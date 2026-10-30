package dev.mcreload.moves.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcreload.moves.HudConfig;
import dev.mcreload.moves.KinetrixMoves;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.Stamina;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.joml.Quaternionf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The moves and the bars in a real game: ./gradlew -p moves runClient -Pselftest. Makes a world,
 * builds a floor (and a one-block-high tunnel), plays every move with real key presses, measures
 * how far it went and how high the hitbox was, and takes screenshots: the bars in every style, and
 * each move from behind with a side view of the player. The numbers go to run/moves-selftest.txt,
 * the pictures to run/screenshots; then the game quits.
 */
public final class MovesSelfTest {
    public static boolean enabled() {
        return Boolean.getBoolean("kinetrix_moves.selftest");
    }

    private static final int Y = 100;
    private static final int TUNNEL_X = 20;

    private enum Phase { BOOT, CREATING, WAITING, SETUP, RUNNING, DONE }

    private record Scenario(String name, int ticks, Consumer<ServerPlayer> setup, BiConsumer<Minecraft, Integer> script,
                            int[] shots, boolean panel) {}

    private static Phase phase = Phase.BOOT;
    private static int timer, index, tick;
    private static final List<Scenario> SCENARIOS = new ArrayList<>();
    private static final List<String> REPORT = new ArrayList<>();
    private static String label = "", pendingShot;
    private static boolean panel;
    /** Measured in the current scenario: where the move started, lowest hitbox, health. */
    private static Vec3 startPos;
    /** The deflect scenario's arrow is in the air. */
    private static volatile boolean deflectShot;
    private static float minHeight;
    private static int moveStart = -1;
    /**
     * The server has done what was asked of it (built the floor, set up the scenario). The game's
     * own server can fall seconds behind on a slow machine; the scenario waits for it.
     */
    private static final java.util.concurrent.atomic.AtomicBoolean SERVER_READY = new java.util.concurrent.atomic.AtomicBoolean();

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(MovesSelfTest::onTick);
        MinecraftForge.EVENT_BUS.addListener(MovesSelfTest::onGui);
        MinecraftForge.EVENT_BUS.addListener(MovesSelfTest::onScreen);
        if (Boolean.getBoolean("kinetrix_moves.selftest.probe")) MinecraftForge.EVENT_BUS.addListener(MovesSelfTest::probe);
        // the moves' own sounds as the game starts them (the celebrate emote's music)
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.sound.PlaySoundEvent e) -> {
            if (e.getSound() != null && KinetrixMoves.MODID.equals(e.getSound().getLocation().getNamespace())) {
                note("sound " + e.getSound().getLocation() + " (" + e.getSound().getSource() + ") started at t " + tick);
            }
        });
        build();
        String only = System.getProperty("kinetrix_moves.selftest.only", "");
        if (!only.isBlank()) SCENARIOS.removeIf(s -> !s.name.matches(".*(" + only + ").*"));
        KinetrixMoves.LOG.info("[kinetrix_moves] SELF-TEST MODE, {} scenarios", SCENARIOS.size());
    }

    // ------------------------------------------------------------------ scenarios

    private static void build() {
        // ---- the bars
        hud("10_hud_full", HudConfig.Style.CLOCK, HudConfig.Shape.HEATER, false, 100f, 10f, false);
        for (HudConfig.Style style : HudConfig.Style.values()) {
            for (HudConfig.Shape shape : HudConfig.Shape.values()) {
                hud("11_hud_" + style.name().toLowerCase(Locale.ROOT) + "_" + shape.name().toLowerCase(Locale.ROOT),
                        style, shape, false, 60f, 6.5f, false);
            }
        }
        hud("12_hud_swapped", HudConfig.Style.CLOCK, HudConfig.Shape.HEATER, true, 35f, 3f, false);
        hud("13_hud_golden_apple", HudConfig.Style.CLOCK, HudConfig.Shape.HEATER, false, 80f, 10f, true);
        SCENARIOS.add(new Scenario("14_arrow_counter", 12, sp -> {
            place(sp, 0, new ItemStack(Items.BOW));
            sp.getInventory().add(new ItemStack(Items.ARROW, 37));
        }, (mc, t) -> camera(mc, CameraType.FIRST_PERSON), new int[]{10}, false));
        SCENARIOS.add(new Scenario("15_no_energy", 24, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            Stamina.of(sp).energy = 5f;
            sp.getFoodData().setFoodLevel(0);     // no refill: the dodge must stay unaffordable
        }, (mc, t) -> {
            camera(mc, CameraType.FIRST_PERSON);
            if (t == 6) press(mc, MovesClient.DODGE, mc.options.keyLeft);
            if (t == 9) release(mc);
            if (t == 12) note("refused dodge without energy: move " + Stamina.of(mc.player).move);
        }, new int[]{8}, false));

        // ---- the moves
        dodge("20_dodge_left", Move.DODGE_LEFT, mc -> mc.options.keyLeft);
        dodge("21_dodge_right", Move.DODGE_RIGHT, mc -> mc.options.keyRight);
        dodge("22_dodge_back", Move.DODGE_BACK, mc -> mc.options.keyDown);
        if (Boolean.getBoolean("kinetrix_moves.selftest.frames")) {
            // Every tick of a roll and a slide, to compare with the Blockbench preview.
            SCENARIOS.add(new Scenario("40_frames_roll", 32, sp -> place(sp, 0, new ItemStack(Items.IRON_SWORD)), (mc, t) -> {
                camera(mc, CameraType.THIRD_PERSON_BACK);
                if (t == 8) press(mc, MovesClient.DODGE, mc.options.keyDown);
                if (t == 10) release(mc);
            }, java.util.stream.IntStream.rangeClosed(8, 26).toArray(), true));
            // The same animation alone: no push, no low hitbox, nothing but the layer.
            SCENARIOS.add(new Scenario("41_frames_anim_only", 32, sp -> place(sp, 0, new ItemStack(Items.IRON_SWORD)), (mc, t) -> {
                camera(mc, CameraType.THIRD_PERSON_BACK);
                if (t == 8) MoveAnimator.play(mc.player, Move.DODGE_BACK);
            }, java.util.stream.IntStream.rangeClosed(8, 26).toArray(), true));
        }
        dodge("23_dodge_front", Move.DODGE_FRONT, mc -> mc.options.keyUp);
        slide("30_slide_tap", false, false);
        slide("31_slide_held", true, false);
        slide("32_slide_tunnel", false, true);

        // ---- the sword block: right mouse button held; a zombie's blow on it in the middle
        guard("60_guard_one_sword", false, CameraType.THIRD_PERSON_BACK);
        guard("61_guard_two_swords", true, CameraType.THIRD_PERSON_BACK);
        guard("62_guard_first_person", false, CameraType.FIRST_PERSON);
        guard("63_guard_two_swords_front", true, CameraType.THIRD_PERSON_FRONT);
        guard("64_guard_two_swords_walking", true, CameraType.THIRD_PERSON_FRONT);

        // ---- the vault: running at a mob, R + W 2.4 blocks before it: over a zombie, over a pig; a
        // chicken is too small (R + W rolls); then an arrow knocked aside by a dodge
        vault("70_vault_zombie", net.minecraft.world.entity.EntityType.ZOMBIE, Move.HOP);
        vault("72_vault_pig", net.minecraft.world.entity.EntityType.PIG, Move.HOP);
        vault("73_vault_chicken_rolls", net.minecraft.world.entity.EntityType.CHICKEN, Move.DODGE_FRONT);
        vault("74_vault_first_person", net.minecraft.world.entity.EntityType.ZOMBIE, Move.HOP, CameraType.FIRST_PERSON);

        // ---- the sword strikes: the dash off a run into a zombie 7 blocks ahead; the finisher on a
        // weakened zombie at point blank (the hint, then the left button held)
        // no run-up any more (S and the left button, the right one): the zombie within the lunge's reach
        dash("90_dash_strike", 4.5, CameraType.THIRD_PERSON_BACK);
        dash("93_dash_mob_near", 2.2, CameraType.THIRD_PERSON_BACK);
        dash("94_dash_first_person", 4.5, CameraType.FIRST_PERSON);
        finisher("91_finisher", Items.IRON_SWORD, CameraType.THIRD_PERSON_BACK);
        finisher("92_finisher_axe", Items.IRON_AXE, CameraType.THIRD_PERSON_BACK);
        finisher("95_finisher_first_person", Items.IRON_SWORD, CameraType.FIRST_PERSON);

        // ---- the author's second message of 2026-10-29: the kick, the attack out of a slide, the
        // roll out of a fall, the ledge
        kick("75_kick_zombie_cutscene", CameraType.FIRST_PERSON);
        kick("76_kick_zombie_side", CameraType.THIRD_PERSON_BACK);
        dive("77_dive", CameraType.THIRD_PERSON_BACK);
        // the author's fifth message of 2026-10-29
        diveOverBlock("78_dive_over_block");
        rollKick("79_roll_kick");
        plunge("99a_plunge", CameraType.THIRD_PERSON_BACK);
        whirlwind("92a_whirlwind", CameraType.THIRD_PERSON_BACK);
        whirlwind("92b_whirlwind_front", CameraType.THIRD_PERSON_FRONT);
        fastRun("16_fast_run");
        // the shots: the squat, waiting, deeper on the riser, the leap, the top, the scream, the hang, the fall, the landing, the stance
        SCENARIOS.add(new Scenario("45_celebrate", 205, sp -> place(sp, 0, ItemStack.EMPTY), (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_FRONT);
            if (t == 4) KeyMapping.click(MovesClient.CELEBRATE.getKey());
            if (t == 6) note("celebrate: move " + Stamina.of(mc.player).move + "; its music in the sounds: "
                    + (mc.getSoundManager().getSoundEvent(dev.mcreload.moves.Celebrate.MUSIC_ID) != null));
            if (t == 125) note("celebrate: still " + Stamina.of(mc.player).move + " at 6 s (hanging), the music playing " + musicPlaying(mc));
            if (t == 175) note("celebrate: landing at 8.5 s, still " + Stamina.of(mc.player).move);
            if (t == 197) note("celebrate: after 9.6 s " + Stamina.of(mc.player).move + ", the music playing " + musicPlaying(mc));
        }, new int[]{4, 14, 45, 77, 80, 83, 86, 101, 125, 155, 168, 172, 175, 182, 192}, false));
        // broken off by walking at 2 s: the emote and its music stop
        SCENARIOS.add(new Scenario("46_celebrate_broken_off", 70, sp -> place(sp, 0, ItemStack.EMPTY), (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            if (t == 4) KeyMapping.click(MovesClient.CELEBRATE.getKey());
            if (t == 40) note("celebrate broken off: before, " + Stamina.of(mc.player).move + ", the music playing " + musicPlaying(mc));
            mc.options.keyUp.setDown(t >= 44 && t < 50);
            if (t == 60) note("celebrate broken off: after walking, " + Stamina.of(mc.player).move + ", the music playing " + musicPlaying(mc));
        }, new int[]{}, false));
        ledgeCorner("87_ledge_corner");
        slideAttack("96_slide_attack", CameraType.THIRD_PERSON_BACK);
        slideAttack("97_slide_attack_first_person", CameraType.FIRST_PERSON);
        fallRoll("98_fall_roll", CameraType.THIRD_PERSON_BACK);
        ledgeJump("80_ledge_jump_catch_shimmy_climb", CameraType.THIRD_PERSON_BACK);
        ledgeJump("84_ledge_first_person", CameraType.FIRST_PERSON);
        ledgeFall("81_ledge_catch_falling");
        ledgeDown("82_ledge_auto_hang_and_hops");
        ledgeTooFar("83_ledge_too_far_from_the_edge");
        ledgeSlideOff("85_ledge_slide_off_catch");
        ledgeClimbRun("86_ledge_climb_running_on");


        SCENARIOS.add(new Scenario("71_deflect_arrow", 30, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            deflectShot = false;
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            if (t == 8) press(mc, MovesClient.DODGE, mc.options.keyLeft);
            // The arrow leaves once the server has the dodge (a busy test server may hear of it a tick
            // or two late): it arrives in the deflect frames, 0-0.21 s.
            if (t >= 9 && t <= 16) {
                IntegratedServer srv = mc.getSingleplayerServer();
                srv.execute(() -> {
                    ServerPlayer sp = srv.getPlayerList().getPlayers().get(0);
                    if (deflectShot || Stamina.of(sp).move != Move.DODGE_LEFT) return;
                    deflectShot = true;
                    net.minecraft.world.entity.projectile.Arrow a = new net.minecraft.world.entity.projectile.Arrow(sp.serverLevel(),
                            sp.getX(), sp.getY() + 1.3, sp.getZ() + 4);
                    a.shoot(0, 0, -1, 1.6f, 0f);
                    sp.serverLevel().addFreshEntity(a);
                });
            }
            if (t == 12) release(mc);
            if (t == 24) {
                IntegratedServer srv = mc.getSingleplayerServer();
                srv.execute(() -> {
                    ServerPlayer sp = srv.getPlayerList().getPlayers().get(0);
                    note(String.format(Locale.ROOT, "deflect: health %.1f, shields %.1f", sp.getHealth(), Stamina.of(sp).shields));
                });
            }
        }, new int[]{8, 10, 12, 16}, true));

        // ---- the bow: a full draw, then the left button (the volley); the right button stays held
        SCENARIOS.add(new Scenario("80_bow_volley", 70, sp -> {
            place(sp, 0, new ItemStack(Items.BOW));
            sp.getInventory().add(new ItemStack(Items.ARROW, 10));
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            if (t == 2) {
                mc.options.keyUse.setDown(true);
                KeyMapping.click(mc.options.keyUse.getKey());
            }
            if (t == 26) {
                startPos = mc.player.position();
                note("volley: drawn " + mc.player.getTicksUsingItem() + " ticks");
                mc.options.keyAttack.setDown(true);
            }
            if (t == 27) {
                mc.options.keyAttack.setDown(false);
                note("volley: move " + Stamina.of(mc.player).move);
            }
            if (t == 55) {
                Vec3 d = mc.player.position().subtract(startPos);
                note(String.format(Locale.ROOT, "volley: stepped back %.2f blocks, arrows left %d of 10, drawing again %s",
                        -d.z, mc.player.getInventory().countItem(Items.ARROW), mc.player.isUsingItem()));
            }
            if (t == 66) mc.options.keyUse.setDown(false);
        }, new int[]{26, 29, 33, 37, 41, 46, 52, 60}, true));

        // ---- after the volley, the right button still held: on one knee, creeping and aiming; then
        // the flow: each left click a dodge (none: a spin in place; A: a roll to the left; S: a hop
        // back) and the round's next pose (crouched, standing, on the knee, crouched, standing);
        // letting go shoots the last arrow and stands up
        SCENARIOS.add(new Scenario("81_bow_flow", 300, sp -> {
            place(sp, 0, new ItemStack(Items.BOW));
            sp.getInventory().add(new ItemStack(Items.ARROW, 16));
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            if (t == 2) {
                mc.options.keyUse.setDown(true);
                KeyMapping.click(mc.options.keyUse.getKey());
            }
            if (t == 26 || t == 80 || t == 125 || t == 170 || t == 201 || t == 246) mc.options.keyAttack.setDown(true);
            if (t == 27 || t == 81 || t == 126 || t == 171 || t == 202 || t == 247) mc.options.keyAttack.setDown(false);
            if (t == 56) {
                note("flow: kneeling " + BowStance.kneeling(mc.player) + ", drawing " + mc.player.isUsingItem() + ", in the flow " + BowStance.inFlow());
                startPos = mc.player.position();
            }
            mc.options.keyUp.setDown(t >= 56 && t < 76);
            if (t == 76) {
                note(String.format(Locale.ROOT, "flow: crept %.2f blocks in 1 s on the knee (walking with a drawn bow: about 0.9)",
                        mc.player.position().subtract(startPos).horizontalDistance()));
                startPos = mc.player.position();
            }
            if (t == 79 || t == 86 || t == 100 || t == 122 || t == 131 || t == 150 || t == 167 || t == 178 || t == 197
                    || t == 206 || t == 222 || t == 242 || t == 250 || t == 268 || t == 288 || t == 298) {
                Move m = Stamina.of(mc.player).move;
                note(String.format(Locale.ROOT, "flow t=%d: arrows %d, move %s, kneeling %s, energy %.0f", t,
                        mc.player.getInventory().countItem(Items.ARROW), m, BowStance.kneeling(mc.player), Stamina.of(mc.player).energy));
            }
            if (t == 197) note(String.format(Locale.ROOT, "flow: three spins in place moved the player %.2f blocks",
                    mc.player.position().subtract(startPos).horizontalDistance()));
            if (t == 200) {
                startPos = mc.player.position();
                mc.options.keyLeft.setDown(true);
            }
            if (t == 206) mc.options.keyLeft.setDown(false);
            if (t == 242) note(String.format(Locale.ROOT, "flow: rolled %.2f blocks to the left", mc.player.position().subtract(startPos).horizontalDistance()));
            if (t == 245) {
                startPos = mc.player.position();
                mc.options.keyDown.setDown(true);
            }
            if (t == 251) mc.options.keyDown.setDown(false);
            if (t == 288) note(String.format(Locale.ROOT, "flow: hopped back %.2f blocks", mc.player.position().subtract(startPos).horizontalDistance()));
            if (t == 292) mc.options.keyUse.setDown(false);
        }, new int[]{60, 86, 100, 108, 131, 150, 178, 187, 206, 222, 250, 268, 296}, true));

        // ---- the Legolas stance: the bow fully drawn, R once; the arrow leaves by itself at every full
        // draw, then a dodge by the keys: A, then S + A (back and to the left), then D, then none
        // (almost in place, the other way from the last one)
        SCENARIOS.add(new Scenario("83_bow_legolas", 150, sp -> {
            place(sp, 0, new ItemStack(Items.BOW));
            sp.getInventory().add(new ItemStack(Items.ARROW, 16));
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            if (t == 2) {
                mc.options.keyUse.setDown(true);
                KeyMapping.click(mc.options.keyUse.getKey());
            }
            if (t == 23) {
                startPos = mc.player.position();
                mc.options.keyLeft.setDown(true);
            }
            if (t == 24) KeyMapping.click(MovesClient.DODGE.getKey());
            if (t == 72) mc.options.keyDown.setDown(true);
            if (t == 96) {
                mc.options.keyLeft.setDown(false);
                mc.options.keyDown.setDown(false);
                mc.options.keyRight.setDown(true);
            }
            if (t == 118) mc.options.keyRight.setDown(false);
            if (t == 30 || t == 51 || t == 72 || t == 96 || t == 118 || t == 138) {
                Vec3 d = mc.player.position().subtract(startPos);
                float r = mc.player.getYRot() * Mth.DEG_TO_RAD;
                // Along the look and to the left of it (Minecraft's strafe: left = (cos, sin)).
                double fwd = -d.x * Mth.sin(r) + d.z * Mth.cos(r), left = d.x * Mth.cos(r) + d.z * Mth.sin(r);
                note(String.format(Locale.ROOT, "legolas t=%d: forward %.2f, left %.2f, arrows %d, move %s, energy %.0f, stance %s", t,
                        fwd, left, mc.player.getInventory().countItem(Items.ARROW), Stamina.of(mc.player).move,
                        Stamina.of(mc.player).energy, BowStance.inLegolas()));
            }
            if (t == 140) mc.options.keyUse.setDown(false);
        }, new int[]{24, 28, 32, 36, 44, 50, 76, 80, 100, 104, 128, 146}, true));

        // ---- the bow in close combat: three blows at a husk (with Better Combat its strikes: from the
        // side, overhead, a thrust; without it vanilla's swing)
        SCENARIOS.add(new Scenario("84_bow_strikes", 56, sp -> {
            place(sp, 0, new ItemStack(Items.BOW));
            net.minecraft.world.entity.monster.Husk z = net.minecraft.world.entity.EntityType.HUSK.create(sp.serverLevel());
            z.moveTo(sp.getX(), sp.getY(), sp.getZ() + 2, 180f, 0f);
            z.setYHeadRot(180f);
            z.setNoAi(true);
            z.addTag("kinetrix_moves.selftest");
            sp.serverLevel().addFreshEntity(z);
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            if (t == 4 || t == 20 || t == 36) {
                mc.options.keyAttack.setDown(true);
                KeyMapping.click(mc.options.keyAttack.getKey());
            }
            if (t == 5 || t == 21 || t == 37) mc.options.keyAttack.setDown(false);
            if (t == 18 || t == 34 || t == 52) {
                IntegratedServer srv = mc.getSingleplayerServer();
                srv.execute(() -> {
                    ServerPlayer sp = srv.getPlayerList().getPlayers().get(0);
                    for (var e : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class, sp.getBoundingBox().inflate(8))) {
                        if (!e.getTags().contains("kinetrix_moves.selftest")) continue;
                        note(String.format(Locale.ROOT, "strikes t=%d: husk health %.1f of %.1f, Better Combat %s", t, e.getHealth(), e.getMaxHealth(),
                                net.minecraftforge.fml.ModList.get().isLoaded("bettercombat")));
                        if (t == 52) e.discard();
                    }
                });
            }
        }, new int[]{6, 9, 12, 15, 22, 25, 28, 38, 41, 44}, true));

        // ---- the settings screen, at the usual GUI scale and at the largest (a small window's worth of room)
        SCENARIOS.add(new Scenario("50_settings", 22, sp -> place(sp, 0, new ItemStack(Items.BOW)), (mc, t) -> {
            camera(mc, CameraType.FIRST_PERSON);
            if (t == 2) mc.setScreen(new MovesSettingsScreen(null));
            if (t == 10) {
                mc.options.guiScale().set(4);
                mc.resizeDisplay();
            }
            if (t == 20) {
                mc.options.guiScale().set(0);
                mc.resizeDisplay();
                mc.setScreen(null);
            }
        }, new int[]{8, 16}, false));
        // ---- the moves' page: every move switched on or off for this player
        SCENARIOS.add(new Scenario("51_settings_moves", 22, sp -> place(sp, 0, new ItemStack(Items.BOW)), (mc, t) -> {
            camera(mc, CameraType.FIRST_PERSON);
            if (t == 2) mc.setScreen(new MovesToggleScreen(new MovesSettingsScreen(null)));
            if (t == 10) {
                mc.options.guiScale().set(4);
                mc.resizeDisplay();
            }
            if (t == 20) {
                mc.options.guiScale().set(0);
                mc.resizeDisplay();
                mc.setScreen(null);
            }
        }, new int[]{8, 16}, false));
    }

    private static void hud(String name, HudConfig.Style style, HudConfig.Shape shape, boolean swap, float energy, float shields, boolean apple) {
        SCENARIOS.add(new Scenario(name, 12, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            sp.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            sp.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            Stamina s = Stamina.of(sp);
            s.energy = energy;
            s.shields = shields;
            if (apple) s.ateGoldenApple(false);
        }, (mc, t) -> {
            camera(mc, CameraType.FIRST_PERSON);
            if (t == 1) {
                HudConfig.STYLE.set(style);
                HudConfig.SHAPE.set(shape);
                HudConfig.SWAP_SIDES.set(swap);
            }
        }, new int[]{10}, false));
    }

    /** The finisher on a weakened zombie at point blank: the hint, then the left button held; with this weapon. */
    private static void finisher(String name, net.minecraft.world.item.Item weapon, CameraType camera) {
        SCENARIOS.add(new Scenario(name, 50, sp -> {
            place(sp, 0, new ItemStack(weapon));
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, 1.0, 3f);    // point blank: the two nearly touch
        }, (mc, t) -> {
            camera(mc, t < 8 ? CameraType.FIRST_PERSON : camera);
            if (t == 6) note("finisher: hint shown " + (StrikeInput.finisherTarget() != null));
            if (t == 8) mc.options.keyAttack.setDown(true);    // no click, as in the dash
            if (t == 10) note("finisher: move " + Stamina.of(mc.player).move);
            if (t == 30) mc.options.keyAttack.setDown(false);
            if (t == 14 || t == 34) dummyReport(mc, "finisher", t == 34);
        }, new int[]{6, 9, 12, 15, 18, 21, 24, 27, 31}, camera != CameraType.FIRST_PERSON));
    }

    /**
     * Sprinting at a zombie {@code dz} blocks ahead, the left button: the dash strike. Near (2.2), it
     * must still run up to the zombie's side, not stand where it started.
     */
    private static void dash(String name, double dz, CameraType camera) {
        SCENARIOS.add(new Scenario(name, 50, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, dz, 20f);
        }, (mc, t) -> {
            camera(mc, camera);
            if (t == 1) Stamina.of(mc.player).dashCooldown = 0;
            // author, 2026-10-29, fifth message: S and the left button held, the right button once.
            // The buttons go down without a click: a real press reaches the moves' tick handler before
            // vanilla's keys, here it would come after them and vanilla would swing first.
            mc.options.keyDown.setDown(t >= 6 && t < 12);
            mc.options.keyAttack.setDown(t >= 7 && t < 12);
            if (t == 10) {
                startPos = mc.player.position();
                mc.options.keyUse.setDown(true);
            }
            if (t == 11) {
                mc.options.keyUse.setDown(false);
                note("dash: move " + Stamina.of(mc.player).move + ", cooldown " + Stamina.of(mc.player).dashCooldown / 20 + " s");
            }
            if (t == 22) note(String.format(Locale.ROOT, "dash: went %.2f blocks to the lunge",
                    mc.player.position().subtract(startPos).horizontalDistance()));
            if (t == 24 || t == 46) dummyReport(mc, "dash", t == 46);
        }, new int[]{10, 13, 16, 19, 21, 24, 28, 34}, camera != CameraType.FIRST_PERSON));
    }

    /**
     * A mob standing still 4.5 blocks ahead; the player runs at it and presses R + W at 2.4 blocks:
     * {@code expect} is the move that should start (the vault, or a roll for a mob too small).
     * Measures how high the player went, where they landed, the mob's shove and the facing.
     */
    private static void vault(String name, net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type, Move expect) {
        vault(name, type, expect, CameraType.THIRD_PERSON_BACK);
    }

    private static void vault(String name, net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type, Move expect,
                              CameraType camera) {
        double[] top = new double[1];
        SCENARIOS.add(new Scenario(name, 56, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            dummy(sp, type, 4.5, -1f);
        }, (mc, t) -> {
            camera(mc, camera);
            mc.options.keyUp.setDown(t >= 2 && t < 40);
            mc.options.keySprint.setDown(t >= 2 && t < 40);
            LocalPlayer p = mc.player;
            if (t >= 2 && Stamina.of(p).move == null && startPos == null) {
                net.minecraft.world.entity.Entity mob = mc.level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                        p.getBoundingBox().inflate(8)).stream().findFirst().orElse(null);
                if (mob != null && mob.position().subtract(p.position()).horizontalDistance() - mob.getBbWidth() / 2 <= 2.4) {
                    startPos = p.position();
                    top[0] = p.getY();
                    KeyMapping.click(MovesClient.DODGE.getKey());
                    note(String.format(Locale.ROOT, "%s: R + W at z %.2f", name, p.getZ()));
                }
            }
            if (startPos != null) top[0] = Math.max(top[0], p.getY());
            if (t == 1) startPos = null;
            if (startPos != null && Stamina.of(p).move != null && Stamina.of(p).moveTick == 1) note(name + ": move " + Stamina.of(p).move + " (expected " + expect + ")");
            if (t == 44 && startPos != null) {
                note(String.format(Locale.ROOT, "%s: highest %.2f blocks up, ran on to z %.2f (mob was at z 5), facing yaw %.0f",
                        name, top[0] - startPos.y, p.getZ(), p.getYRot()));
                dummyReport(mc, name, true);
            }
        }, new int[]{8, 10, 12, 14, 16, 18, 20, 22, 24, 27, 32}, camera != CameraType.FIRST_PERSON));
    }

    /**
     * W held, Space pressed (the player is already in the air), R a tick later at a zombie 2.3 blocks
     * ahead: the vault with a kick, the turn to face the zombie, the spinning kick. From first person
     * the cutscene takes the camera behind the player and gives it back after.
     */
    private static void kick(String name, CameraType camera) {
        SCENARIOS.add(new Scenario(name, 80, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, 2.3, -1f);
        }, (mc, t) -> {
            if (t <= 1) camera(mc, camera);
            mc.options.keyUp.setDown(t >= 4 && t < 12);
            mc.options.keyJump.setDown(t >= 5 && t < 12);
            if (t == 6) {
                startPos = mc.player.position();
                note(name + ": R in the air " + !mc.player.onGround());
                KeyMapping.click(MovesClient.DODGE.getKey());
            }
            if (t == 9) note(name + ": move " + Stamina.of(mc.player).move + ", camera " + mc.options.getCameraType());
            if (t == 6 + dev.mcreload.moves.Kick.TURN_TO + 2) note(String.format(Locale.ROOT, "%s: after the turn yaw %.0f (started 0)",
                    name, mc.player.getYRot()));
            if (t == 7 + dev.mcreload.moves.Kick.HIT) note(String.format(Locale.ROOT, "%s: at the kick %.2f blocks up, %.2f on",
                    name, mc.player.getY() - startPos.y, mc.player.position().subtract(startPos).horizontalDistance()));
            if (t == 70) {
                note(String.format(Locale.ROOT, "%s: camera after %s, health %.1f, facing yaw %.0f, %s", name, mc.options.getCameraType(),
                        mc.player.getHealth(), mc.player.getYRot(), Stamina.of(mc.player).move));
                dummyReport(mc, name, true);
            }
        }, new int[]{8, 12, 16, 20, 22, 24, 26, 28, 30, 32, 34, 36, 40, 46}, camera != CameraType.FIRST_PERSON));
    }

    /** Running, Space and R with no mob near: the deep jump, the hands on the ground, the roll; how far and how high. */
    /** The deep jump at a block a block high, 1.5 blocks ahead (author, 2026-10-29, fifth message: at least over that). */
    private static void diveOverBlock(String name) {
        SCENARIOS.add(new Scenario(name, 60, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            for (int x = -1; x <= 1; x++) sp.serverLevel().setBlock(new BlockPos(x, Y + 1, 2), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            mc.options.keyUp.setDown(t >= 2 && t < 6);
            mc.options.keyJump.setDown(t == 5);
            if (t == 5) {
                startPos = mc.player.position();
                minHeight = 0f;
                KeyMapping.click(MovesClient.DODGE.getKey());
            }
            if (t > 5 && startPos != null) minHeight = Math.max(minHeight, (float) (mc.player.getY() - startPos.y));
            if (t == 7) note(name + ": move " + Stamina.of(mc.player).move);
            if (t == 45) note(String.format(Locale.ROOT, "%s: at z %.2f (the block is z 2-3), %.2f up at most, on the floor %s, %s", name,
                    mc.player.getZ(), minHeight, Math.abs(mc.player.getY() - (Y + 1)) < 0.01, Stamina.of(mc.player).move));
            if (t == 58) mc.getSingleplayerServer().execute(() -> {
                ServerLevel level = mc.getSingleplayerServer().overworld();
                for (int x = -1; x <= 1; x++) level.setBlock(new BlockPos(x, Y + 1, 2), Blocks.AIR.defaultBlockState(), 2);
            });
        }, new int[]{6, 8, 10, 12, 14, 16, 18, 22, 26}, true));
    }

    /** Dropped from 15 blocks with a sword, R not held: the blade held point down, driven into the ground, up; the fall's damage. */
    private static void plunge(String name, CameraType camera) {
        SCENARIOS.add(new Scenario(name, 70, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            sp.teleportTo(sp.serverLevel(), 0.5, Y + 16, 0.5, 0f, 10f);
        }, (mc, t) -> {
            camera(mc, camera);
            if (t == 12) note(name + ": move " + Stamina.of(mc.player).move + ", fallen " + mc.player.fallDistance);
            if (mc.player.onGround() && startPos == null && t > 0) {
                startPos = mc.player.position();
                moveStart = t;
                note(name + ": landed at t " + t + ", move " + Stamina.of(mc.player).move);
            }
            if (t == 1) startPos = null;
            if (startPos != null && t == moveStart + 3) note(String.format(Locale.ROOT, "%s: health %.1f after a fall of 15 (bare: 8; tucked: 16)",
                    name, mc.player.getHealth()));
            if (startPos != null && Stamina.of(mc.player).move == Move.PLUNGE && Stamina.of(mc.player).moveTick == Move.PLUNGE.recoverFrom()) {
                note(String.format(Locale.ROOT, "%s: the stun ends %.2f s after the landing", name, (t - moveStart) / 20f));
            }
            if (t == 60) note(String.format(Locale.ROOT, "%s: %s, pose %s", name, Stamina.of(mc.player).move, mc.player.getPose()));
        }, new int[]{6, 12, 15, 17, 19, 21, 23, 26, 29, 32, 36, 40}, camera != CameraType.FIRST_PERSON));
    }

    /** An axe, S and the left button held, then the right one held: the whirlwind among three zombies; its reach, its run, its cooldown. */
    private static void whirlwind(String name, CameraType camera) {
        SCENARIOS.add(new Scenario(name, 70, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_AXE));
            Stamina.of(sp).whirlCooldown = 0;
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, 0, 2.2, 20f);
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, 1.8, 3.6, 20f);
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, -2.0, 1.0, 20f);
        }, (mc, t) -> {
            camera(mc, camera);
            if (t == 1) Stamina.of(mc.player).whirlCooldown = 0;
            mc.options.keyDown.setDown(t >= 4 && t < 9);
            mc.options.keyAttack.setDown(t >= 5 && t < 60);
            if (t == 8) startPos = mc.player.position();
            mc.options.keyUse.setDown(t >= 8 && t < 60);
            if (t == 9) note(name + ": move " + Stamina.of(mc.player).move);
            if (t == 30) note(name + ": still turning " + Stamina.of(mc.player).move);
            if (t == 54) {
                note(String.format(Locale.ROOT, "%s: went %.2f blocks, cooldown %d s, %s", name,
                        mc.player.position().subtract(startPos).horizontalDistance(), Stamina.of(mc.player).whirlCooldown / 20, Stamina.of(mc.player).move));
                dummyReport(mc, name, true);
            }
        }, new int[]{8, 10, 12, 14, 16, 18, 20, 23, 26, 30, 34, 38, 42, 46, 50}, camera != CameraType.FIRST_PERSON));
    }

    /** Ctrl, W and Space, R at a zombie: the roll kick, a flip over it, the kick, the landing facing it, the roll. */
    private static void rollKick(String name) {
        SCENARIOS.add(new Scenario(name, 80, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, 2.8, -1f);
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            mc.options.keyUp.setDown(t >= 2 && t < 12);
            mc.options.keySprint.setDown(t >= 2 && t < 12);
            mc.options.keyJump.setDown(t >= 5 && t < 12);
            if (t == 6) {
                startPos = mc.player.position();
                minHeight = 0f;
                KeyMapping.click(MovesClient.DODGE.getKey());
            }
            if (t > 6 && startPos != null) minHeight = Math.max(minHeight, (float) (mc.player.getY() - startPos.y));
            if (t == 8) note(name + ": move " + Stamina.of(mc.player).move);
            if (t == 7 + dev.mcreload.moves.Kick.RK_TURN_TO + 1) note(String.format(Locale.ROOT, "%s: after the turn yaw %.0f (started 0)",
                    name, mc.player.getYRot()));
            if (t == 60) {
                note(String.format(Locale.ROOT, "%s: %.2f up at most, went %.2f, facing yaw %.0f, health %.1f, %s", name, minHeight,
                        mc.player.position().subtract(startPos).horizontalDistance(), mc.player.getYRot(), mc.player.getHealth(),
                        Stamina.of(mc.player).move));
                dummyReport(mc, name, true);
            }
        }, new int[]{7, 9, 11, 13, 15, 17, 19, 21, 23, 25, 27, 29, 32, 36, 42}, true));
    }

    /** Ctrl and W held: the fast run, its speed and what it eats. */
    private static void fastRun(String name) {
        SCENARIOS.add(new Scenario(name, 70, sp -> {
            place(sp, -10, ItemStack.EMPTY);
            Stamina.of(sp).energy = 60f;
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            mc.options.keyUp.setDown(t >= 2 && t < 60);
            mc.options.keySprint.setDown(t >= 2 && t < 60);
            if (t == 30) {
                startPos = mc.player.position();
                minHeight = Stamina.of(mc.player).energy;
            }
            if (t == 50) note(String.format(Locale.ROOT, "%s: %.2f blocks a second, energy %.2f -> %.2f in a second", name,
                    mc.player.position().subtract(startPos).horizontalDistance(), minHeight, Stamina.of(mc.player).energy));
        }, new int[]{20, 40}, true));
    }

    /** A pillar a block wide: caught on its face, then the hop mode and D held: round its corners. */
    private static void ledgeCorner(String name) {
        SCENARIOS.add(new Scenario(name, 120, sp -> {
            place(sp, 0, ItemStack.EMPTY);
            ServerLevel level = sp.serverLevel();
            for (int y = Y + 1; y <= Y + 3; y++) level.setBlock(new BlockPos(LEDGE_X, y, 3), Blocks.STONE_BRICKS.defaultBlockState(), 2);
            sp.teleportTo(level, LEDGE_X + 0.5, Y + 10, 2.6, 0f, 0f);
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            LocalPlayer p = mc.player;
            mc.options.keyUse.setDown(t >= 0 && t < 20);
            if (t == 20) note(String.format(Locale.ROOT, "%s: %s, wall way %d", name, Stamina.of(p).move, Stamina.of(p).ledgeDir));
            if (t == 22) KeyMapping.click(MovesClient.DODGE.getKey());
            mc.options.keyRight.setDown(t >= 26 && t < 90);
            if (t >= 24 && t <= 100 && (Boolean.getBoolean("kinetrix_moves.selftest.probe") || t % 25 == 20))
                note(String.format(Locale.ROOT, "%s: t %d %s@%d, wall way %d, at x %.2f z %.2f, feet %.2f below the top, yaw %.0f",
                        name, t, Stamina.of(p).move, Stamina.of(p).moveTick, Stamina.of(p).ledgeDir, p.getX(), p.getZ(), Y + 4 - p.getY(), p.getYRot()));
            if (t == 110) mc.getSingleplayerServer().execute(() -> {
                ServerLevel level = mc.getSingleplayerServer().overworld();
                for (int y = Y + 1; y <= Y + 3; y++) level.setBlock(new BlockPos(LEDGE_X, y, 3), Blocks.AIR.defaultBlockState(), 2);
            });
        }, new int[]{20, 30, 34, 38, 42, 46, 50, 58, 66, 74, 82, 90}, true));
    }

    private static void dive(String name, CameraType camera) {
        SCENARIOS.add(new Scenario(name, 60, sp -> place(sp, 0, new ItemStack(Items.IRON_SWORD)), (mc, t) -> {
            camera(mc, camera);
            mc.options.keyUp.setDown(t >= 2 && t < 10);
            mc.options.keySprint.setDown(t >= 2 && t < 10);
            mc.options.keyJump.setDown(t == 6);
            if (t == 6) {
                startPos = mc.player.position();
                minHeight = 0f;
                KeyMapping.click(MovesClient.DODGE.getKey());
            }
            if (t > 6 && startPos != null) minHeight = Math.max(minHeight, (float) (mc.player.getY() - startPos.y));
            if (t == 8) note(name + ": move " + Stamina.of(mc.player).move);
            if (t == 50) note(String.format(Locale.ROOT, "%s: went %.2f blocks, %.2f up at most, %s, health %.1f", name,
                    mc.player.position().subtract(startPos).horizontalDistance(), minHeight, Stamina.of(mc.player).move,
                    mc.player.getHealth()));
        }, new int[]{7, 10, 13, 16, 18, 20, 23, 26, 30, 34}, true));
    }

    /** Sprinting, C tapped, a zombie a step to the side of the way: the left button as the slide passes it. */
    private static void slideAttack(String name, CameraType camera) {
        SCENARIOS.add(new Scenario(name, 60, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            sp.teleportTo(sp.serverLevel(), 0.5, Y + 1, -6.5, 0f, 0f);
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, 0.9, 5.5, -1f);
            Stamina.of(sp).slideAttackCooldown = 0;
        }, (mc, t) -> {
            camera(mc, camera);
            if (t == 1) Stamina.of(mc.player).slideAttackCooldown = 0;
            KeyMapping slide = slideKey(mc);
            mc.options.keyUp.setDown(t >= 2 && t < 14);
            mc.options.keySprint.setDown(t >= 2 && t < 14);
            if (t == 12) slide.setDown(true);
            if (t == 13) slide.setDown(false);
            if (t == 16) {
                startPos = mc.player.position();
                mc.options.keyAttack.setDown(true);
            }
            if (t == 17) {
                mc.options.keyAttack.setDown(false);
                note(name + ": move " + Stamina.of(mc.player).move + ", yaw " + Math.round(mc.player.getYRot()));
            }
            if (t == 40) {
                note(String.format(Locale.ROOT, "%s: facing yaw %.0f (was 0), went %.2f blocks on, pose %s",
                        name, mc.player.getYRot(), mc.player.position().subtract(startPos).horizontalDistance(), mc.player.getPose()));
                dummyReport(mc, name, true);
            }
        }, new int[]{12, 15, 17, 18, 19, 20, 22, 24, 28, 32, 38}, camera != CameraType.FIRST_PERSON));
    }

    /** Dropped from 11 blocks up, R held: tucked, the roll on landing, the knee; the fall's damage. */
    private static void fallRoll(String name, CameraType camera) {
        SCENARIOS.add(new Scenario(name, 70, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            sp.teleportTo(sp.serverLevel(), 0.5, Y + 16, 0.5, 0f, 10f);
        }, (mc, t) -> {
            camera(mc, camera);
            MovesClient.DODGE.setDown(t >= 0 && t < 30);
            if (t == 6) note(name + ": move " + Stamina.of(mc.player).move + ", fallen " + mc.player.fallDistance);
            if (mc.player.onGround() && startPos == null && t > 0) {
                startPos = mc.player.position();
                moveStart = t;
                note(name + ": landed at t " + t + ", move " + Stamina.of(mc.player).move);
            }
            if (t == 1) startPos = null;
            // the health just after the landing (before food heals it)
            if (startPos != null && t == moveStart + 3) note(String.format(Locale.ROOT, "%s: health %.1f after a fall of 15 (untucked: 8)",
                    name, mc.player.getHealth()));
            if (startPos != null && Stamina.of(mc.player).move == Move.FALL_ROLL && Stamina.of(mc.player).moveTick == Move.FALL_ROLL.recoverFrom()) {
                note(String.format(Locale.ROOT, "%s: the stun ends %.2f s after the landing", name, (t - moveStart) / 20f));
            }
            if (t == 60) note(String.format(Locale.ROOT, "%s: rolled %.2f blocks on, %s, pose %s", name,
                    mc.player.position().subtract(startPos).horizontalDistance(), Stamina.of(mc.player).move, mc.player.getPose()));
        }, new int[]{4, 10, 14, 16, 18, 20, 23, 26, 32, 40, 50, 56}, camera != CameraType.FIRST_PERSON));
    }

    /** The ledge test wall: three blocks high, two deep, across x 24..30, its face at z 3, top at Y + 4. */
    private static final int LEDGE_X = 27;

    private static void ledgeWall(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        for (int x = LEDGE_X - 3; x <= LEDGE_X + 3; x++) {
            for (int z = 3; z <= 4; z++) {
                for (int y = Y + 1; y <= Y + 3; y++) level.setBlock(new BlockPos(x, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
            }
        }
    }

    /** Running at the wall, a jump with the right button: caught; D along it; R and D: a hop; W + Space: up. */
    private static void ledgeJump(String name, CameraType camera) {
        SCENARIOS.add(new Scenario(name, 160, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            ledgeWall(sp);
            // a short run-up: the jump starts half a block from the wall (the hand reaches only the edge right in front)
            sp.teleportTo(sp.serverLevel(), LEDGE_X + 0.5, Y + 1, 2.0, 0f, 0f);
        }, (mc, t) -> {
            camera(mc, camera);
            LocalPlayer p = mc.player;
            mc.options.keyUp.setDown(t >= 2 && t < 12);
            if (t == 4) {
                mc.options.keyJump.setDown(true);
                mc.options.keyUse.setDown(true);
            }
            if (t == 5) mc.options.keyJump.setDown(false);
            if (t == 14) {
                mc.options.keyUse.setDown(false);
                note(String.format(Locale.ROOT, "%s: move %s, feet %.2f below the top, %.3f from the wall", name,
                        Stamina.of(p).move, Y + 4 - p.getY(), 3 - (p.getZ() + 0.3)));
                startPos = p.position();
            }
            // D: along the edge by hand, then (in the hop mode) the hops one after another (two blocks of wall are left)
            mc.options.keyRight.setDown((t >= 20 && t < 40) || (t >= 48 && t < 74));
            if (t == 30) note(name + ": along the edge " + Stamina.of(p).move);
            if (t == 44) note(String.format(Locale.ROOT, "%s: went %.2f blocks along the edge in 1 s, still %s", name,
                    p.position().subtract(startPos).horizontalDistance(), Stamina.of(p).move));
            if (t == 46) KeyMapping.click(MovesClient.DODGE.getKey());
            if (t == 48) {
                note(name + ": " + Stamina.of(p).move + " after R (the hop mode)");
                startPos = p.position();
            }
            if (t == 52) note(name + ": hop " + Stamina.of(p).move);
            if (t == 92) note(String.format(Locale.ROOT, "%s: D held 1.3 s, the hops went %.2f blocks, now %s", name,
                    p.position().subtract(startPos).horizontalDistance(), Stamina.of(p).move));
            if (t == 94) {
                mc.options.keyUp.setDown(true);
                mc.options.keyJump.setDown(true);
            }
            if (t == 96) {
                mc.options.keyUp.setDown(false);
                mc.options.keyJump.setDown(false);
                note(name + ": climbing " + Stamina.of(p).move);
            }
            if (t == 94) minHeight = p.getHealth();
            if (t > 94 && t < 150) minHeight = Math.min(minHeight, p.getHealth());
            if (t == 150) note(String.format(Locale.ROOT, "%s: lowest health on the climb %.1f", name, minHeight));
            if (t == 150) note(String.format(Locale.ROOT, "%s: after the climb feet %.2f above the top, %.2f onto it, on the ground %s, %s",
                    name, p.getY() - (Y + 4), p.getZ() - 3, p.onGround(), Stamina.of(p).move));
        }, new int[]{8, 12, 16, 24, 30, 36, 52, 58, 64, 70, 76, 80, 100, 104, 108, 112, 118, 130}, camera != CameraType.FIRST_PERSON));
    }

    /**
     * On a wide top, running at its edge, C: the slide goes over it; the right button held catches
     * the edge behind (author, 2026-10-29: sliding off a drop the grab did not work).
     */
    private static void ledgeSlideOff(String name) {
        SCENARIOS.add(new Scenario(name, 70, sp -> {
            place(sp, 0, ItemStack.EMPTY);
            ServerLevel level = sp.serverLevel();
            for (int x = LEDGE_X - 3; x <= LEDGE_X + 3; x++) {
                for (int z = 3; z <= 10; z++) {
                    for (int y = Y + 1; y <= Y + 3; y++) level.setBlock(new BlockPos(x, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                }
            }
            sp.teleportTo(level, LEDGE_X + 0.5, Y + 4, 9.5, 180f, 10f);     // facing the drop to the north, 6.5 blocks from it
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            LocalPlayer p = mc.player;
            KeyMapping slide = slideKey(mc);
            mc.options.keyUp.setDown(t >= 2 && t < 14);
            mc.options.keySprint.setDown(t >= 2 && t < 14);
            if (t == 12) slide.setDown(true);
            if (t == 13) {
                slide.setDown(false);
                note(name + ": move " + Stamina.of(p).move);
                mc.options.keyUse.setDown(true);
                minHeight = p.getHealth();
            }
            if (t == 40) {
                mc.options.keyUse.setDown(false);
                note(String.format(Locale.ROOT, "%s: %s, feet %.2f below the top, %.3f from the wall, facing yaw %.0f, health %.1f",
                        name, Stamina.of(p).move, Y + 4 - p.getY(), 3 - (p.getZ() + 0.3), p.getYRot(), p.getHealth()));
            }
            if (t == 42) {
                mc.options.keyUp.setDown(true);
                mc.options.keyJump.setDown(true);
            }
            if (t == 44) release(mc);
            if (t == 64) note(String.format(Locale.ROOT, "%s: climbed back, feet %.2f above the top, %s", name,
                    p.getY() - (Y + 4), Stamina.of(p).move));
        }, new int[]{12, 15, 17, 19, 21, 23, 26, 30, 38, 46, 50, 56}, true));
    }

    /**
     * Falling from high up beside the wall, caught; then W + Space with W kept down: up and running on
     * at once (author, 2026-10-29: the pull-up turned the player over into a roll; the fall before the
     * catch must not end in Kinetrix's own landing roll on the top).
     */
    private static void ledgeClimbRun(String name) {
        SCENARIOS.add(new Scenario(name, 60, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            ledgeWall(sp);
            sp.teleportTo(sp.serverLevel(), LEDGE_X + 0.5, Y + 12, 2.6, 0f, 0f);
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            LocalPlayer p = mc.player;
            mc.options.keyUse.setDown(t >= 0 && t < 24);
            if (t == 24) note(name + ": " + Stamina.of(p).move);
            mc.options.keyUp.setDown(t >= 26 && t < 56);
            mc.options.keyJump.setDown(t >= 26 && t < 28);
            if (t == 28) note(name + ": " + Stamina.of(p).move);
            if (t == 54) note(String.format(Locale.ROOT, "%s: ran on %.2f blocks onto the top, %s", name, p.getZ() - 3, Stamina.of(p).move));
        }, new int[]{30, 36, 40, 42, 44, 46, 48, 50}, true));
    }

    /** Falling from high up beside the wall, the right button held: caught at the edge (the fall forgiven); S + Space: let go. */
    private static void ledgeFall(String name) {
        SCENARIOS.add(new Scenario(name, 70, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            ledgeWall(sp);
            sp.teleportTo(sp.serverLevel(), LEDGE_X + 0.5, Y + 24, 2.6, 0f, 0f);
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            LocalPlayer p = mc.player;
            mc.options.keyUse.setDown(t >= 0 && t < 40);
            if (t == 30) note(String.format(Locale.ROOT, "%s: move %s, feet %.2f below the top, fall %.1f", name,
                    Stamina.of(p).move, Y + 4 - p.getY(), p.fallDistance));
            if (t == 44) {
                mc.options.keyDown.setDown(true);
                mc.options.keyJump.setDown(true);
            }
            if (t == 46) release(mc);
            if (t == 64) note(String.format(Locale.ROOT, "%s: let go, %s, on the ground %s, health %.1f", name,
                    Stamina.of(p).move, p.onGround(), p.getHealth()));
        }, new int[]{20, 24, 28, 34, 46, 50, 56}, true));
    }

    /** On the top facing the drop: Ctrl + W + C hangs from the edge; then R and A: a hop to the left; C lets go. */
    private static void ledgeDown(String name) {
        SCENARIOS.add(new Scenario(name, 120, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            ledgeWall(sp);
            sp.teleportTo(sp.serverLevel(), LEDGE_X + 0.5, Y + 4, 3.4, 180f, 10f);     // the toes at the edge
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            LocalPlayer p = mc.player;
            KeyMapping slide = slideKey(mc);
            if (t == 4) {
                mc.options.keySprint.setDown(true);
                mc.options.keyUp.setDown(true);
                slide.setDown(true);
            }
            if (t == 6) {
                release(mc);
                slide.setDown(false);
                note(name + ": move " + Stamina.of(p).move);
            }
            if (t >= 8 && t <= 40) minHeight = Math.min(minHeight, p.getHealth());
            if (t == 7) minHeight = p.getHealth();
            if (t == 41) note(String.format(Locale.ROOT, "%s: lowest health on the way down %.1f, pose %s", name, minHeight, p.getPose()));
            if (t == 44) note(String.format(Locale.ROOT, "%s: %s, feet %.2f below the top, facing yaw %.0f, %.3f from the wall", name,
                    Stamina.of(p).move, Y + 4 - p.getY(), p.getYRot(), 3 - (p.getZ() + 0.3)));
            if (t == 46) {
                startPos = p.position();
                mc.options.keyLeft.setDown(true);
                KeyMapping.click(MovesClient.DODGE.getKey());
            }
            if (t == 48) mc.options.keyLeft.setDown(false);
            if (t == 90) note(String.format(Locale.ROOT, "%s: R + A hopped %.2f blocks, now %s", name,
                    p.position().subtract(startPos).horizontalDistance(), Stamina.of(p).move));
            if (t == 94) slide.setDown(true);
            if (t == 96) slide.setDown(false);
            if (t == 112) note(name + ": after C " + Stamina.of(p).move + ", on the ground " + p.onGround() + ", health " + p.getHealth());
        }, new int[]{6, 10, 14, 18, 22, 26, 30, 36, 44, 56, 62, 68, 76, 98, 104}, true));
    }

    /**
     * The hand reaches only the edge right in front (author, 2026-10-29): a jump with the right
     * button a step away from the wall catches nothing; Ctrl + W + C a step back from a drop does not
     * hang either.
     */
    private static void ledgeTooFar(String name) {
        SCENARIOS.add(new Scenario(name, 40, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            ledgeWall(sp);
            sp.teleportTo(sp.serverLevel(), LEDGE_X + 0.5, Y + 1, 2.0, 0f, 0f);     // 0.7 from the wall's face
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            LocalPlayer p = mc.player;
            if (t == 4) {
                mc.options.keyJump.setDown(true);
                mc.options.keyUse.setDown(true);
            }
            if (t == 5) mc.options.keyJump.setDown(false);
            if (t == 16) {
                mc.options.keyUse.setDown(false);
                note(name + ": jump 0.7 from the wall, " + Stamina.of(p).move + " (expected null)");
                IntegratedServer srv = mc.getSingleplayerServer();
                srv.execute(() -> {
                    ServerPlayer sp = srv.getPlayerList().getPlayers().get(0);
                    sp.teleportTo(sp.serverLevel(), LEDGE_X + 0.5, Y + 4, 4.6, 180f, 10f);     // 1.3 from the drop
                });
            }
            KeyMapping slide = slideKey(mc);
            if (t == 26) {
                mc.options.keySprint.setDown(true);
                mc.options.keyUp.setDown(true);
                slide.setDown(true);
            }
            if (t == 27) {
                release(mc);
                slide.setDown(false);
                note(name + ": Ctrl + W + C 1.3 from the drop, " + Stamina.of(p).move + " (no LEDGE_DOWN)");
            }
        }, new int[]{8, 30}, true));
    }

    /** A mob that stands still {@code dz} blocks in front of the player (facing them), with this health (-1: full). */
    private static void dummy(ServerPlayer sp, net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type, double dz, float health) {
        dummy(sp, type, 0, dz, health);
    }

    /** The same, {@code dx} blocks to the side. */
    private static void dummy(ServerPlayer sp, net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type,
                              double dx, double dz, float health) {
        net.minecraft.world.entity.Mob z = type.create(sp.serverLevel());
        z.moveTo(sp.getX() + dx, sp.getY(), sp.getZ() + dz, 180f, 0f);
        z.setYHeadRot(180f);
        z.setYBodyRot(180f);
        // No goals (it stands still) but its own physics: the vault's shove and the dash's knockback move it.
        z.removeFreeWill();
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));    // not burning in the sun
        if (health > 0) z.setHealth(health);
        z.addTag("kinetrix_moves.selftest");
        sp.serverLevel().addFreshEntity(z);
    }

    /** The test mob's health and place, from the server (and it goes away with {@code clear}). */
    private static void dummyReport(Minecraft mc, String what, boolean clear) {
        IntegratedServer srv = mc.getSingleplayerServer();
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayers().get(0);
            boolean any = false;
            for (var e : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, sp.getBoundingBox().inflate(12))) {
                if (!e.getTags().contains("kinetrix_moves.selftest")) continue;
                any = true;
                note(String.format(Locale.ROOT, "%s: mob %s health %.1f of %.1f, alive %s, at z %.2f; player energy %.1f",
                        what, e.getType().toShortString(), e.getHealth(), e.getMaxHealth(), e.isAlive(), e.getZ(), Stamina.of(sp).energy));
                if (clear) e.discard();
            }
            if (!any) note(what + ": the mob is gone (dead)");
        });
    }

    private static void dodge(String name, Move move, java.util.function.Function<Minecraft, KeyMapping> dir) {
        SCENARIOS.add(new Scenario(name, 40, sp -> place(sp, 0, new ItemStack(Items.IRON_SWORD)), (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            if (t == 8) press(mc, MovesClient.DODGE, dir.apply(mc));
            if (t == 12) release(mc);
            measure(mc, move, t, 8);
        }, new int[]{8, 10, 12, 14, 16, 20}, true));
    }

    /** Holds the use key with a sword (two swords); at tick 24 a zombie in front hits for 8. */
    private static void guard(String name, boolean two, CameraType camera) {
        SCENARIOS.add(new Scenario(name, 40, sp -> {
            place(sp, 0, new ItemStack(Items.DIAMOND_SWORD));
            if (two) sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
            sp.serverLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        }, (mc, t) -> {
            camera(mc, camera);
            if (t == 4) {
                mc.options.keyUse.setDown(true);
                KeyMapping.click(mc.options.keyUse.getKey());
            }
            // Walking while guarding: Kinetrix's legs walk, the guard's arms and blades must stay.
            if (name.contains("walking")) mc.options.keyUp.setDown(t >= 8 && t < 22);
            if (t == 24) {
                IntegratedServer srv = mc.getSingleplayerServer();
                srv.execute(() -> {
                    ServerPlayer sp = srv.getPlayerList().getPlayers().get(0);
                    net.minecraft.world.entity.monster.Zombie z = net.minecraft.world.entity.EntityType.ZOMBIE.create(sp.serverLevel());
                    z.moveTo(sp.getX(), sp.getY(), sp.getZ() + 2, 180f, 0f);
                    z.setNoAi(true);
                    sp.serverLevel().addFreshEntity(z);
                    sp.invulnerableTime = 0;
                    sp.hurt(sp.damageSources().mobAttack(z), 8f);
                    Stamina st = Stamina.of(sp);
                    note(String.format(Locale.ROOT, "%s: guard %s, health %.1f after a blow of 8, shields %.1f, sword wear %d",
                            name, st.guard, sp.getHealth(), st.shields, sp.getMainHandItem().getDamageValue()));
                    z.discard();
                });
            }
            if (t == 34) mc.options.keyUse.setDown(false);
            if (t == 38) note(name + ": guard after letting go " + Stamina.of(mc.player).guard);
        }, new int[]{6, 10, 16, 26, 36}, camera != CameraType.FIRST_PERSON));
    }

    /** Runs up, sprinting, then taps or holds the slide key; in the tunnel a one-block gap starts 3 blocks ahead. */
    private static void slide(String name, boolean held, boolean tunnel) {
        SCENARIOS.add(new Scenario(name, 60, sp -> {
            place(sp, tunnel ? TUNNEL_X : 0, new ItemStack(Items.IRON_SWORD));
            sp.teleportTo(sp.serverLevel(), (tunnel ? TUNNEL_X : 0) + 0.5, Y + 1, -6.5, 0f, 0f);
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            KeyMapping slide = slideKey(mc);
            mc.options.keyUp.setDown(t >= 2 && t < 14);
            mc.options.keySprint.setDown(t >= 2 && t < 14);
            if (t == 12) slide.setDown(true);
            if (t == (held ? 40 : 13)) slide.setDown(false);
            measure(mc, Move.SLIDE, t, 12);
            if (t == 50) note(name + ": pose " + mc.player.getPose() + ", health " + mc.player.getHealth()
                    + ", stuck in a block " + mc.player.isInWall());
        }, held ? new int[]{12, 17, 24, 28, 31, 34, 37, 40} : new int[]{12, 14, 17, 20, 24, 30, 40}, true));
    }

    private static KeyMapping slideKey(Minecraft mc) {
        for (KeyMapping k : mc.options.keyMappings) if ("key.kinetrix.crawl".equals(k.getName())) return k;
        for (KeyMapping k : mc.options.keyMappings) if ("key.kinetrix_moves.slide".equals(k.getName())) return k;
        throw new IllegalStateException("no slide key");
    }

    /** From the move's start: how far the player went and how low the hitbox got. */
    private static void measure(Minecraft mc, Move move, int t, int pressTick) {
        LocalPlayer p = mc.player;
        if (t == pressTick) {
            startPos = p.position();
            minHeight = p.getBbHeight();
            moveStart = -1;
        }
        if (t < pressTick) return;
        Stamina s = Stamina.of(p);
        if (s.move == move && moveStart < 0) moveStart = t;
        minHeight = Math.min(minHeight, p.getBbHeight());
        if (t == pressTick + move.length + 6) {
            Vec3 d = p.position().subtract(startPos);
            note(String.format(Locale.ROOT, "%s: started %s, went %.2f blocks (x %.2f, z %.2f), lowest hitbox %.2f, health %.1f",
                    move.name().toLowerCase(Locale.ROOT), moveStart >= 0 ? "yes" : "NO", Math.sqrt(d.x * d.x + d.z * d.z),
                    d.x, d.z, minHeight, p.getHealth()));
        }
    }

    // ------------------------------------------------------------------ input

    private static void press(Minecraft mc, KeyMapping action, KeyMapping direction) {
        direction.setDown(true);
        KeyMapping.click(action.getKey());
    }

    private static void release(Minecraft mc) {
        for (KeyMapping k : new KeyMapping[]{mc.options.keyLeft, mc.options.keyRight, mc.options.keyUp, mc.options.keyDown,
                mc.options.keySprint, mc.options.keyJump}) k.setDown(false);
    }

    private static void camera(Minecraft mc, CameraType type) {
        if (mc.options.getCameraType() != type) mc.options.setCameraType(type);
    }

    private static void note(String line) {
        REPORT.add(label + "  " + line);
        KinetrixMoves.LOG.info("[kinetrix_moves] self-test: {}", line);
    }

    // ------------------------------------------------------------------ the world

    /** Clears the player's things and stands them on the floor at x, facing +Z (south). */
    private static void place(ServerPlayer sp, int x, ItemStack hand) {
        sp.getInventory().clearContent();
        sp.setItemInHand(InteractionHand.MAIN_HAND, hand);
        sp.setGameMode(GameType.SURVIVAL);
        sp.setHealth(sp.getMaxHealth());
        sp.getFoodData().setFoodLevel(20);
        sp.getFoodData().setSaturation(5f);
        Stamina s = Stamina.of(sp);
        s.end(sp);
        s.energy = 100f;
        s.shields = 10f;
        s.freeTicks = 0;
        s.boostTicks = 0;
        s.dashCooldown = 0;
        s.slideAttackCooldown = 0;
        sp.teleportTo(sp.serverLevel(), x + 0.5, Y + 1, 0.5, 0f, 10f);
    }

    private static void setupWorld(Minecraft mc) {
        IntegratedServer srv = mc.getSingleplayerServer();
        SERVER_READY.set(false);
        srv.execute(() -> {
            ServerLevel level = srv.overworld();
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, srv);
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, srv);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, srv);
            // No "advancement made" lines in the chat over the bars in the pictures.
            level.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, srv);
            level.setDayTime(6000);
            for (int x = -12; x <= 32; x++) {
                for (int z = -16; z <= 24; z++) {
                    level.setBlock(new BlockPos(x, Y, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                    for (int y = Y + 1; y <= Y + 6; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            // The tunnel: a one-block-high gap from z = -3 to z = 3, with walls on both sides.
            for (int z = -3; z <= 3; z++) {
                for (int dx = -1; dx <= 1; dx++) level.setBlock(new BlockPos(TUNNEL_X + dx, Y + 2, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                level.setBlock(new BlockPos(TUNNEL_X - 1, Y + 1, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                level.setBlock(new BlockPos(TUNNEL_X + 1, Y + 1, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
            }
            SERVER_READY.set(true);
        });
    }

    // ------------------------------------------------------------------ the run

    private static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && phase == Phase.RUNNING && Boolean.getBoolean("kinetrix_moves.selftest.probe")) probeTick();
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        timer++;
        try {
            switch (phase) {
                case BOOT -> {
                    if (mc.screen instanceof TitleScreen && timer > 40) {
                        CreateWorldScreen.openFresh(mc, mc.screen);
                        phase = Phase.CREATING;
                        timer = 0;
                    } else if (mc.screen != null && !(mc.screen instanceof TitleScreen) && timer > 40) {
                        mc.options.onboardAccessibility = false;
                        mc.setScreen(new TitleScreen());
                    }
                }
                case CREATING -> {
                    if (!(mc.screen instanceof CreateWorldScreen screen) || timer < 20) return;
                    String create = Component.translatable("selectWorld.create").getString();
                    for (GuiEventListener child : screen.children()) {
                        if (child instanceof Button b && b.getMessage().getString().equals(create)) {
                            b.onPress();
                            phase = Phase.WAITING;
                            timer = 0;
                            return;
                        }
                    }
                }
                case WAITING -> {
                    if (mc.player == null || mc.screen != null || mc.getSingleplayerServer() == null) timer = 0;
                    else if (timer > 60) {
                        setupWorld(mc);
                        phase = Phase.SETUP;
                        timer = 0;
                    }
                }
                case SETUP -> {
                    // The floor is built and the player stands on it (the first teleport can lag).
                    if (!SERVER_READY.get()) timer = 0;
                    else if (timer == 1) {
                        IntegratedServer srv = mc.getSingleplayerServer();
                        srv.execute(() -> place(srv.getPlayerList().getPlayers().get(0), 0, ItemStack.EMPTY));
                    } else if (timer > 40 && Math.abs(mc.player.getY() - (Y + 1)) < 0.01 && mc.player.onGround()) {
                        phase = Phase.RUNNING;
                        index = -1;
                        next(mc);
                    }
                }
                case RUNNING -> run(mc);
                case DONE -> {
                    if (timer == 40) {
                        writeReport(mc);
                        KinetrixMoves.LOG.info("[kinetrix_moves] SELF-TEST FINISHED");
                        mc.stop();
                    }
                }
            }
        } catch (Throwable t) {
            KinetrixMoves.LOG.error("[kinetrix_moves] SELF-TEST ERROR in " + phase + " / " + label, t);
            REPORT.add(label + "  ERROR " + t);
            phase = Phase.DONE;
            timer = 0;
        }
        if (timer > 20 * 60 * 3 && phase != Phase.RUNNING && phase != Phase.DONE) {
            KinetrixMoves.LOG.error("[kinetrix_moves] SELF-TEST TIMEOUT in {}", phase);
            mc.stop();
        }
    }

    private static void next(Minecraft mc) {
        release(mc);
        index++;
        if (index >= SCENARIOS.size()) {
            phase = Phase.DONE;
            timer = 0;
            label = "";
            return;
        }
        Scenario sc = SCENARIOS.get(index);
        label = sc.name;
        panel = sc.panel;
        mc.gui.getChat().clearMessages(false);
        tick = -10;     // ten ticks to settle after the setup
        IntegratedServer srv = mc.getSingleplayerServer();
        SERVER_READY.set(false);
        srv.execute(() -> {
            sc.setup.accept(srv.getPlayerList().getPlayers().get(0));
            SERVER_READY.set(true);
        });
    }

    private static void run(Minecraft mc) {
        Scenario sc = SCENARIOS.get(index);
        if (!SERVER_READY.get()) return;
        if (pendingShot != null) KinetrixMoves.LOG.debug("[kinetrix_moves] self-test: frame {} not drawn in time", pendingShot);
        tick++;
        if (tick < 0) return;
        sc.script.accept(mc, tick);
        for (int s : sc.shots) if (s == tick) pendingShot = String.format(Locale.ROOT, "moves_%s_t%02d.png", sc.name, tick);
        if (tick >= sc.ticks) next(mc);
    }

    /**
     * -PselftestProbe: every frame the player is drawn, the turn of the body as it is drawn (all the
     * layers, Kinetrix's too), to run/moves-probe.txt: a turn the pictures are too far apart to show.
     */
    private static final List<String> PROBE = new ArrayList<>();

    private static void probe(net.minecraftforge.client.event.RenderPlayerEvent.Post event) {
        if (phase != Phase.RUNNING || !(event.getEntity() instanceof LocalPlayer p)) return;
        if (!(p instanceof dev.kosmx.playerAnim.impl.IAnimatedPlayer a)) return;
        dev.kosmx.playerAnim.core.util.Vec3f r = a.playerAnimator_getAnimation().get3DTransform("body",
                dev.kosmx.playerAnim.api.TransformType.ROTATION, dev.kosmx.playerAnim.core.util.Vec3f.ZERO);
        Stamina s = Stamina.of(p);
        Object own = dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess.getPlayerAssociatedData(p).get(MoveAnimator.LAYER);
        float ownX = own instanceof dev.kosmx.playerAnim.api.layered.IAnimation a2 && a2.isActive()
                ? a2.get3DTransform("body", dev.kosmx.playerAnim.api.TransformType.ROTATION, event.getPartialTick(),
                dev.kosmx.playerAnim.core.util.Vec3f.ZERO).getX() * Mth.RAD_TO_DEG : Float.NaN;
        PROBE.add(String.format(Locale.ROOT, "%s t%d+%.2f %s@%d body %.0f %.0f %.0f ours %.0f ground %s", label, tick, event.getPartialTick(),
                s.move, s.moveTick, r.getX() * Mth.RAD_TO_DEG, r.getY() * Mth.RAD_TO_DEG, r.getZ() * Mth.RAD_TO_DEG, ownX, p.onGround()));
    }

    /** -PselftestProbe: every tick, what the moves' own layer plays and its body turn at the tick and half way to the next. */
    private static void probeTick() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        Object own = dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess.getPlayerAssociatedData(p).get(MoveAnimator.LAYER);
        if (!(own instanceof dev.kosmx.playerAnim.api.layered.ModifierLayer<?> layer)) return;
        Object inner = layer.getAnimation();
        String what = inner == null ? "none" : inner.getClass().getSimpleName();
        if (inner instanceof dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer k) what += "@" + k.getTick();
        StringBuilder b = new StringBuilder();
        for (float d : new float[]{0f, 0.5f}) {
            layer.setupAnim(d);
            dev.kosmx.playerAnim.core.util.Vec3f r = layer.get3DTransform("body", dev.kosmx.playerAnim.api.TransformType.ROTATION, d,
                    dev.kosmx.playerAnim.core.util.Vec3f.ZERO);
            b.append(String.format(Locale.ROOT, " %.0f", r.getX() * Mth.RAD_TO_DEG));
        }
        if (p instanceof dev.kosmx.playerAnim.impl.IAnimatedPlayer a) {
            b.append(" all");
            for (float d : new float[]{0f, 0.5f}) {
                a.playerAnimator_getAnimation().setTickDelta(d);
                dev.kosmx.playerAnim.core.util.Vec3f r = a.playerAnimator_getAnimation().get3DTransform("body",
                        dev.kosmx.playerAnim.api.TransformType.ROTATION, dev.kosmx.playerAnim.core.util.Vec3f.ZERO);
                b.append(String.format(Locale.ROOT, " %.0f", r.getX() * Mth.RAD_TO_DEG));
            }
        }
        try {
            // Kinetrix's own state (it runs in the development game when published locally): its landing
            Object rig = Class.forName("dev.mcreload.anim.client.rig.RigRegistry")
                    .getMethod("get", net.minecraft.client.player.AbstractClientPlayer.class).invoke(null, p);
            if (rig != null) {
                Object m = rig.getClass().getField("motion").get(rig);
                b.append(" kin");
                for (String f : new String[]{"landKind", "landStart", "time", "airTicks", "minAirVy", "wasOnGround", "rollEarly"}) {
                    java.lang.reflect.Field fd = m.getClass().getDeclaredField(f);
                    fd.setAccessible(true);
                    b.append(' ').append(f).append('=').append(fd.get(m));
                }
                b.append(" whole=").append(Class.forName("dev.mcreload.anim.api.KinetrixApi")
                        .getMethod("wholeBody", java.util.UUID.class).invoke(null, p.getUUID()));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            b.append(" kin?").append(e.getClass().getSimpleName());
        }
        Stamina s = Stamina.of(p);
        PROBE.add(String.format(Locale.ROOT, "%s TICK t%d %s@%d layer %s active %s pitch%s ground %s", label, tick, s.move, s.moveTick, what,
                layer.isActive(), b, p.onGround()));
    }

    /** The celebrate emote's music is playing on this client (the sound engine's playing instances; the development game's names). */
    private static boolean musicPlaying(Minecraft mc) {
        try {
            java.lang.reflect.Field f = mc.getSoundManager().getClass().getDeclaredField("soundEngine");
            f.setAccessible(true);
            Object engine = f.get(mc.getSoundManager());
            java.lang.reflect.Field g = engine.getClass().getDeclaredField("instanceToChannel");
            g.setAccessible(true);
            for (Object o : ((java.util.Map<?, ?>) g.get(engine)).keySet()) {
                if (o instanceof net.minecraft.client.resources.sounds.SoundInstance si && si.getLocation().equals(dev.mcreload.moves.Celebrate.MUSIC_ID)) return true;
            }
            return false;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static void writeReport(Minecraft mc) {
        Path out = mc.gameDirectory.toPath().resolve("moves-selftest.txt");
        try {
            if (!PROBE.isEmpty()) Files.write(mc.gameDirectory.toPath().resolve("moves-probe.txt"), PROBE, StandardCharsets.UTF_8);
            Files.write(out, REPORT, StandardCharsets.UTF_8);
        } catch (IOException e) {
            KinetrixMoves.LOG.error("[kinetrix_moves] cannot write {}", out, e);
        }
    }

    // ------------------------------------------------------------------ pictures

    private static void onGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (phase != Phase.RUNNING || mc.player == null) return;
        GuiGraphics g = event.getGuiGraphics();
        if (panel) sideView(g, mc.player, event.getPartialTick());
        g.drawString(mc.font, label + "  t=" + tick, 6, 6, 0xFFFF55);
        g.flush();
        // With a screen open the picture is taken after the screen is drawn (onScreen).
        if (pendingShot != null && mc.screen == null) {
            Screenshot.grab(mc.gameDirectory, pendingShot, mc.getMainRenderTarget(), msg -> {});
            pendingShot = null;
        }
    }

    private static void onScreen(net.minecraftforge.client.event.ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (phase != Phase.RUNNING || pendingShot == null) return;
        event.getGuiGraphics().flush();
        Screenshot.grab(mc.gameDirectory, pendingShot, mc.getMainRenderTarget(), msg -> {});
        pendingShot = null;
    }

    /** The player seen from their left side, in a panel on the right. */
    private static void sideView(GuiGraphics g, LocalPlayer player, float pt) {
        int w = g.guiWidth(), h = g.guiHeight();
        int left = w - w / 4, top = 20, bottom = h - 40;
        g.fill(left, top, w - 4, bottom, 0xC0303040);
        g.drawString(Minecraft.getInstance().font, "SIDE", left + 4, top + 4, 0xFFFFFF);
        float bodyYaw = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot);
        Quaternionf pose = new Quaternionf().rotateZ(Mth.PI).rotateY((90f + bodyYaw - 180f) * Mth.DEG_TO_RAD);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate((left + w - 4) / 2f, bottom - (bottom - top) / 5f, 150);
        int scale = Math.max(20, h / 6);
        ps.mulPoseMatrix(new org.joml.Matrix4f().scaling(scale, scale, -scale));
        ps.mulPose(pose);
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderSystem.runAsFancy(() -> dispatcher.render(player, 0, 0, 0, 0f, pt, ps, buffers, 0xF000F0));
        buffers.endBatch();
        dispatcher.setRenderShadow(true);
        ps.popPose();
        Lighting.setupFor3DItems();
    }

    private MovesSelfTest() {}
}
