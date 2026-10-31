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
    /** The move at the last look (a scenario's own record). */
    private static Move lastMove;
    /** The arrows left at the last look (a scenario's own count). */
    private static int arrowsWere = -1;
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
            // The forward roll with an axe, seen from the front (the author's video of 2026-10-30, 23:01).
            SCENARIOS.add(new Scenario("42_frames_roll_axe", 32, sp -> place(sp, 0, new ItemStack(Items.IRON_AXE)), (mc, t) -> {
                camera(mc, CameraType.THIRD_PERSON_FRONT);
                if (t == 8) press(mc, MovesClient.DODGE, mc.options.keyUp);
                if (t == 10) release(mc);
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
        whirlwind("92a_whirlwind", CameraType.THIRD_PERSON_BACK, false);
        whirlwind("92b_whirlwind_front", CameraType.THIRD_PERSON_FRONT, false);
        whirlwind("92c_whirlwind_held", CameraType.THIRD_PERSON_BACK, true);
        fastRun("16_fast_run");
        // the author's message of 2026-10-30: G (the sword's turn, the whirlwind), the tray, the splash
        swordSpin("88_sword_spin", CameraType.THIRD_PERSON_BACK, false);
        swordSpin("89_sword_spin_first_person", CameraType.FIRST_PERSON, false);
        swordSpin("88b_sword_spin_held", CameraType.THIRD_PERSON_BACK, true);
        tray("17_tray");
        plungeSplash("99b_plunge_splash");
        // celebrate is Kinetrix's now: its emote (played as the emote menu plays it) and its music, then broken off
        SCENARIOS.add(new Scenario("45_kinetrix_celebrate", 205, sp -> place(sp, 0, ItemStack.EMPTY), (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_FRONT);
            if (t == 4) note("celebrate (Kinetrix): started " + kinetrixEmote(mc, 20));
            if (t == 8) note("celebrate (Kinetrix): the music playing " + musicPlaying(mc));
            if (t == 125) note("celebrate (Kinetrix): at 6 s the emote " + kinetrixEmote(mc, -2) + ", the music playing " + musicPlaying(mc));
            if (t == 197) note("celebrate (Kinetrix): after 9.6 s the emote " + kinetrixEmote(mc, -2) + ", the music playing " + musicPlaying(mc));
        }, new int[]{4, 14, 45, 77, 80, 86, 101, 125, 168, 175, 192}, false));
        SCENARIOS.add(new Scenario("46_kinetrix_celebrate_broken_off", 70, sp -> place(sp, 0, ItemStack.EMPTY), (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            if (t == 4) kinetrixEmote(mc, 20);
            if (t == 40) note("celebrate broken off: before, the emote " + kinetrixEmote(mc, -2) + ", the music playing " + musicPlaying(mc));
            mc.options.keyUp.setDown(t >= 44 && t < 50);
            if (t == 60) note("celebrate broken off: after walking, the emote " + kinetrixEmote(mc, -2) + ", the music playing " + musicPlaying(mc));
        }, new int[]{}, false));
        ledgeCorner("87_ledge_corner");
        slideAttack("96_slide_attack", CameraType.THIRD_PERSON_BACK);
        slideAttack("97_slide_attack_first_person", CameraType.FIRST_PERSON);
        slideAttack("96b_slide_attack_bow", CameraType.THIRD_PERSON_BACK, Items.BOW);
        slideShot("96c_slide_shot", CameraType.THIRD_PERSON_BACK);
        fallRoll("98_fall_roll", CameraType.THIRD_PERSON_BACK);
        ledgeJump("80_ledge_jump_hops_leap_climb", CameraType.THIRD_PERSON_BACK);
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

        // ---- the bow: G (the volley; author, 2026-10-30, 23:01: on G now, its arrows 0.55 s apart); the
        // right button stays held from before it
        SCENARIOS.add(new Scenario("80_bow_volley", 70, sp -> {
            place(sp, 0, new ItemStack(Items.BOW));
            Stamina.of(sp).bowCooldown = 0;
            sp.getInventory().add(new ItemStack(Items.ARROW, 10));
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            if (t == 1) Stamina.of(mc.player).bowCooldown = 0;     // a G of a scenario before waits on
            if (t == 2) {
                mc.options.keyUse.setDown(true);
                KeyMapping.click(mc.options.keyUse.getKey());
            }
            if (t == 26) {
                startPos = mc.player.position();
                note("volley: drawn " + mc.player.getTicksUsingItem() + " ticks");
                KeyMapping.click(MovesClient.ABILITY.getKey());
            }
            if (t == 28) note("volley: move " + Stamina.of(mc.player).move + " (G tapped), G waits " + Stamina.of(mc.player).bowCooldown / 20 + " s");
            if (t == 64) KeyMapping.click(MovesClient.ABILITY.getKey());
            if (t == 66) note("volley: G again 1.9 s later: move " + Stamina.of(mc.player).move + ", G waits " + Stamina.of(mc.player).bowCooldown / 20 + " s");
            if (t >= 28 && t <= 66) {
                int left = mc.player.getInventory().countItem(Items.ARROW);
                if (left != arrowsWere && arrowsWere >= 0) note("volley: an arrow left at tick " + (t - 28) + " of the volley");
                arrowsWere = left;
            }
            if (t == 55) {
                Vec3 d = mc.player.position().subtract(startPos);
                note(String.format(Locale.ROOT, "volley: stepped back %.2f blocks, arrows left %d of 10, drawing again %s",
                        -d.z, mc.player.getInventory().countItem(Items.ARROW), mc.player.isUsingItem()));
            }
            if (t == 66) mc.options.keyUse.setDown(false);
        }, new int[]{26, 29, 33, 39, 44, 50, 55, 60}, true));

        // ---- after the volley, the right button still held: on one knee, creeping and aiming; the left
        // button does nothing there now (author, 2026-10-31: the old flow is gone, the Legolas stance took
        // its place); letting go shoots the arrow and stands up
        SCENARIOS.add(new Scenario("81_bow_kneel_after_g", 130, sp -> {
            place(sp, 0, new ItemStack(Items.BOW));
            Stamina.of(sp).bowCooldown = 0;
            sp.getInventory().add(new ItemStack(Items.ARROW, 16));
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            if (t == 1) Stamina.of(mc.player).bowCooldown = 0;     // a G of a scenario before waits on
            if (t == 2) {
                mc.options.keyUse.setDown(true);
                KeyMapping.click(mc.options.keyUse.getKey());
            }
            if (t == 26) KeyMapping.click(MovesClient.ABILITY.getKey());
            if (t == 68) {
                note("kneel after G: kneeling " + BowStance.kneeling(mc.player) + ", drawing " + mc.player.isUsingItem());
                startPos = mc.player.position();
            }
            mc.options.keyUp.setDown(t >= 56 && t < 76);
            if (t == 76) {
                note(String.format(Locale.ROOT, "kneel after G: crept %.2f blocks in 1 s on the knee (walking with a drawn bow: about 0.9)",
                        mc.player.position().subtract(startPos).horizontalDistance()));
            }
            if (t == 80 || t == 90 || t == 100) mc.options.keyAttack.setDown(true);
            if (t == 81 || t == 91 || t == 101) mc.options.keyAttack.setDown(false);
            if (t == 79 || t == 84 || t == 94 || t == 104) {
                Move m = Stamina.of(mc.player).move;
                note(String.format(Locale.ROOT, "kneel after G t=%d (left clicks at 80, 90, 100): arrows %d, move %s, kneeling %s", t,
                        mc.player.getInventory().countItem(Items.ARROW), m, BowStance.kneeling(mc.player)));
            }
            if (t == 110) mc.options.keyUse.setDown(false);
            if (t == 125) note(String.format(Locale.ROOT, "kneel after G: let go, arrows %d, kneeling %s",
                    mc.player.getInventory().countItem(Items.ARROW), BowStance.kneeling(mc.player)));
        }, new int[]{40, 60, 84, 94, 114, 124}, true));

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
            if (t == 146) note("legolas: let go, its wait " + (Stamina.of(mc.player).legolasCooldown + 19) / 20 + " s (want 12), stance "
                    + BowStance.inLegolas());
        }, new int[]{24, 28, 32, 36, 44, 50, 76, 80, 100, 104, 128, 146}, true));

        // ---- the bow in close combat: three blows at a husk (with Better Combat its strikes: from the
        // side, overhead, a thrust; without it vanilla's swing)
        // ---- G held: the hail, five to ten arrows spread about the look, the slide a block longer
        SCENARIOS.add(new Scenario("85_bow_hail", 70, sp -> {
            place(sp, 0, new ItemStack(Items.BOW));
            Stamina.of(sp).bowCooldown = 0;
            sp.getInventory().add(new ItemStack(Items.ARROW, 20));
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            if (t == 1) Stamina.of(mc.player).bowCooldown = 0;     // a G of a scenario before waits on
            if (t == 6) {
                startPos = mc.player.position();
                KeyMapping.click(MovesClient.ABILITY.getKey());
            }
            MovesClient.ABILITY.setDown(t >= 6 && t < 44);
            if (t == 12) note("hail: move " + Stamina.of(mc.player).move + " (G held)");
            if (t == 66) note(String.format(Locale.ROOT, "hail: G held 1.9 s, %d arrows, slid back %.2f blocks (the volley: 1.85)",
                    20 - mc.player.getInventory().countItem(Items.ARROW), -mc.player.position().subtract(startPos).z));
        }, new int[]{10, 14, 18, 24, 30, 38, 46, 56}, true));

        // ---- the marks (the right button held, the left one on a mob), then the right button let go: the
        // volley at the marked; three zombies, the middle one behind a pillar
        SCENARIOS.add(new Scenario("86_bow_marks_volley", 100, sp -> {
            place(sp, 0, new ItemStack(Items.BOW));
            sp.getInventory().add(new ItemStack(Items.ARROW, 20));
            Stamina.of(sp).markedCooldown = 0;
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, -4, 9, 20f);
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, 0, 11, 20f);
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, 5, 8, 20f);
            for (int y = Y + 1; y <= Y + 3; y++) sp.serverLevel().setBlock(BlockPos.containing(sp.getX(), y, sp.getZ() + 8), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            LocalPlayer p = mc.player;
            if (t == 1) Stamina.of(p).markedCooldown = 0;
            if (t == 2) {
                mc.options.keyUse.setDown(true);
                KeyMapping.click(mc.options.keyUse.getKey());
            }
            List<net.minecraft.world.entity.Entity> mobs = dummies(mc);
            for (int k = 0; k < 3; k++) {
                if (k < mobs.size() && t == 8 + 6 * k) lookAt(p, mobs.get(k).position().add(0, 1.0, 0));
                if (k < mobs.size() && t == 10 + 6 * k) mc.options.keyAttack.setDown(true);
                if (t == 11 + 6 * k) mc.options.keyAttack.setDown(false);
            }
            if (t == 27) {
                lookAt(p, p.getEyePosition().add(0, 0, 10));
                note(String.format(Locale.ROOT, "marks: %d marked, %d of them in sight, the bow drawn %d ticks", Stamina.of(p).marks.size(),
                        dev.mcreload.moves.Marks.inSight(p, Stamina.of(p)).size(), p.getTicksUsingItem()));
                arrowsWere = p.getInventory().countItem(Items.ARROW);
            }
            if (t == 30) mc.options.keyUse.setDown(false);
            if (t == 32) note("marked volley: " + Stamina.of(p).move + ", the volley's wait " + Stamina.of(p).markedCooldown / 20 + " s");
            if (t >= 30 && t <= 50) {
                Stamina st = Stamina.of(p);
                if (st.move != lastMove) note("marked volley t=" + t + ": " + st.move + (st.move == null ? "" : " at tick " + st.moveTick)
                        + ", yaw " + Math.round(p.getYRot()));
                lastMove = st.move;
            }
            if (t == 70) {
                StringBuilder hurt = new StringBuilder();
                for (net.minecraft.world.entity.Entity e : dummies(mc)) {
                    hurt.append(String.format(Locale.ROOT, " %.0f", ((net.minecraft.world.entity.LivingEntity) e).getHealth()));
                }
                note(String.format(Locale.ROOT, "marked volley: %d arrows, the zombies' health (left, middle behind the pillar, right):%s",
                        arrowsWere - p.getInventory().countItem(Items.ARROW), hurt));
                dummyReport(mc, "marked volley", true);
            }
        }, new int[]{20, 26, 31, 33, 35, 37, 39, 41, 44, 60}, true));

        // ---- G at two marked mobs: the volley's arrows at them in turn, the player turned to each
        SCENARIOS.add(new Scenario("87_bow_g_marked", 70, sp -> {
            place(sp, 0, new ItemStack(Items.BOW));
            Stamina.of(sp).bowCooldown = 0;
            sp.getInventory().add(new ItemStack(Items.ARROW, 20));
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, -4, 9, 20f);
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, 5, 8, 20f);
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            if (t == 1) Stamina.of(mc.player).bowCooldown = 0;     // a G of a scenario before waits on
            LocalPlayer p = mc.player;
            if (t == 2) {
                mc.options.keyUse.setDown(true);
                KeyMapping.click(mc.options.keyUse.getKey());
            }
            List<net.minecraft.world.entity.Entity> mobs = dummies(mc);
            for (int k = 0; k < 2; k++) {
                if (k < mobs.size() && t == 6 + 4 * k) lookAt(p, mobs.get(k).position().add(0, 1.0, 0));
                if (k < mobs.size() && t == 8 + 4 * k) mc.options.keyAttack.setDown(true);
                if (t == 9 + 4 * k) mc.options.keyAttack.setDown(false);
            }
            if (t == 16) {
                mc.options.keyUse.setDown(false);
                lookAt(p, p.getEyePosition().add(0, 0, 10));
            }
            if (t == 18) KeyMapping.click(MovesClient.ABILITY.getKey());
            if (t == 66) {
                StringBuilder hurt = new StringBuilder();
                for (net.minecraft.world.entity.Entity e : dummies(mc)) {
                    hurt.append(String.format(Locale.ROOT, " %.0f", ((net.minecraft.world.entity.LivingEntity) e).getHealth()));
                }
                note("G at two marked: the zombies' health (left, right):" + hurt + " (1 and 3 into the first, 2 into the second)");
                dummyReport(mc, "G at the marked", true);
            }
        }, new int[]{12, 30, 40, 52}, true));

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
        // ---- the menu (/kinetrix_moves): the attacks and the mechanics, their keys and switches
        SCENARIOS.add(new Scenario("51_menu", 22, sp -> place(sp, 0, new ItemStack(Items.BOW)), (mc, t) -> {
            camera(mc, CameraType.FIRST_PERSON);
            if (t == 2) net.minecraftforge.client.ClientCommandHandler.runCommand("kinetrix_moves");
            if (t == 4) note("menu: /kinetrix_moves opened " + (mc.screen == null ? "nothing" : mc.screen.getClass().getSimpleName()));
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
            // author, 2026-10-30: the left and the right button held together.
            // The buttons go down without a click: a real press reaches the moves' tick handler before
            // vanilla's keys, here it would come after them and vanilla would swing first.
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

    /**
     * An axe, G: the whirlwind among three zombies; its reach, its run, its cooldown. {@code held}: G held,
     * five turns, the camera turned to the right meanwhile (the run follows it) and A, S, D held (they do nothing).
     */
    private static void whirlwind(String name, CameraType camera, boolean held) {
        SCENARIOS.add(new Scenario(name, held ? 90 : 70, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_AXE));
            Stamina.of(sp).whirlCooldown = 0;
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, 0, 2.2, 20f);
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, 1.8, 3.6, 20f);
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, -2.0, 1.0, 20f);
        }, (mc, t) -> {
            camera(mc, camera);
            if (t == 1) Stamina.of(mc.player).whirlCooldown = 0;
            if (t == 8) {
                startPos = mc.player.position();
                KeyMapping.click(MovesClient.ABILITY.getKey());
            }
            if (held) {
                MovesClient.ABILITY.setDown(t >= 8 && t < 70);
                mc.options.keyLeft.setDown(t >= 20 && t < 30);
                mc.options.keyDown.setDown(t >= 30 && t < 40);
                mc.options.keyRight.setDown(t >= 40 && t < 50);
                // the camera turns 90 degrees to the right over the third and fourth turns
                if (t >= 30 && t < 48) mc.player.setYRot(mc.player.getYRot() + 5f);
            }
            if (t == 9) note(name + ": move " + Stamina.of(mc.player).move);
            if (t == 30) note(name + ": still turning " + Stamina.of(mc.player).move);
            if (t == (held ? 80 : 54)) {
                Vec3 d = mc.player.position().subtract(startPos);
                note(String.format(Locale.ROOT, "%s: went %.2f blocks (x %.2f, z %.2f), cooldown %d s, energy %.1f, %s", name,
                        d.horizontalDistance(), d.x, d.z, Stamina.of(mc.player).whirlCooldown / 20, Stamina.of(mc.player).energy,
                        Stamina.of(mc.player).move));
                dummyReport(mc, name, true);
            }
        }, held ? new int[]{10, 20, 30, 40, 50, 56, 60, 64, 68, 72} : new int[]{8, 10, 12, 14, 16, 18, 20, 23, 26, 30, 34, 38, 42, 46, 50},
                camera != CameraType.FIRST_PERSON));
    }

    /**
     * A sword, G: the quick turn among three zombies (one behind), walking forward through it (no stun,
     * author 2026-10-30); {@code held}: G held, three turns, walking to the left. Who it hits, how far the
     * player went, the cooldown.
     */
    private static void swordSpin(String name, CameraType camera, boolean held) {
        SCENARIOS.add(new Scenario(name, 50, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            Stamina.of(sp).spinCooldown = 0;
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, 0, 2.0, 20f);
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, 1.8, 0.4, 20f);
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, -0.6, -1.8, 20f);
        }, (mc, t) -> {
            // first person only at the start: the attack takes the camera behind (Cutscene) and gives it back
            if (t <= 1) camera(mc, camera);
            if (t == 12 || t == 40) note(name + ": the camera at tick " + t + ": " + mc.options.getCameraType());
            if (t == 1) Stamina.of(mc.player).spinCooldown = 0;
            if (t == 8) {
                startPos = mc.player.position();
                KeyMapping.click(MovesClient.ABILITY.getKey());
            }
            MovesClient.ABILITY.setDown(held && t >= 8 && t < 30);
            (held ? mc.options.keyLeft : mc.options.keyUp).setDown(t >= 8 && t < 30);
            if (t == 9) note(name + ": move " + Stamina.of(mc.player).move);
            if (t == 20) note(name + ": at 0.6 s " + Stamina.of(mc.player).move + " tick " + Stamina.of(mc.player).moveTick);
            if (t == 40) {
                note(String.format(Locale.ROOT, "%s: walked %.2f blocks meanwhile, cooldown %d s, energy %.1f, %s", name,
                        mc.player.position().subtract(startPos).horizontalDistance(), Stamina.of(mc.player).spinCooldown / 20,
                        Stamina.of(mc.player).energy, Stamina.of(mc.player).move));
                dummyReport(mc, name, true);
            }
        }, held ? new int[]{9, 11, 13, 15, 17, 19, 21, 23, 26, 30, 34} : new int[]{8, 9, 10, 11, 12, 13, 14, 15, 17, 20, 26},
                camera != CameraType.FIRST_PERSON));
    }

    /** The tray: a sword (the turn waiting, the dash waiting, the slide's attack ready), then an axe (the whirlwind ready), pressing G while it waits. */
    private static void tray(String name) {
        SCENARIOS.add(new Scenario(name, 60, sp -> {
            place(sp, 0, new ItemStack(Items.DIAMOND_SWORD));
            Stamina s = Stamina.of(sp);
            s.spinCooldown = 12 * 20;
            s.dashCooldown = 25 * 20;
            s.slideAttackCooldown = 0;
            s.whirlCooldown = 0;
        }, (mc, t) -> {
            camera(mc, CameraType.FIRST_PERSON);
            Stamina s = Stamina.of(mc.player);
            if (t == 1) {
                s.spinCooldown = 12 * 20;
                s.dashCooldown = 25 * 20;
                s.slideAttackCooldown = 0;
            }
            if (t == 20) KeyMapping.click(MovesClient.ABILITY.getKey());
            if (t == 22) note("tray: G while the turn waits: " + s.move + ", the turn " + s.spinCooldown / 20 + " s, the dash " + s.dashCooldown / 20 + " s");
            if (t == 30) mc.player.getInventory().setItem(mc.player.getInventory().selected, new ItemStack(Items.IRON_AXE));
        }, new int[]{10, 21, 40}, false));
    }

    /** Falling with a sword among four zombies: the three on the blocks round the landing lose 1 HP, the one two blocks off none. */
    private static void plungeSplash(String name) {
        SCENARIOS.add(new Scenario(name, 70, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, 0, 1.0, 20f);
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, 1.0, -1.0, 20f);
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, -1.0, 0, 20f);
            dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, 0, -2.5, 20f);
            sp.teleportTo(sp.getX(), sp.getY() + 8, sp.getZ());
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            if (t == 60) dummyReport(mc, name, true);
        }, new int[]{20, 26, 30}, true));
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
            // the back roll after the landing: away from the mob (author, 2026-10-30)
            if (t == 7 + dev.mcreload.moves.Kick.RK_ROLL_FROM || t == 7 + dev.mcreload.moves.Kick.RK_ROLL_TO + 2) {
                net.minecraft.world.entity.Entity mob = nearestDummy(mc);
                note(String.format(Locale.ROOT, "%s: %s the roll, %.2f blocks from the mob", name,
                        t == 7 + dev.mcreload.moves.Kick.RK_ROLL_FROM ? "before" : "after", mob == null ? -1 : mob.distanceTo(mc.player)));
            }
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
        slideAttack(name, camera, Items.IRON_SWORD);
    }

    private static void slideAttack(String name, CameraType camera, net.minecraft.world.item.Item weapon) {
        SCENARIOS.add(new Scenario(name, 60, sp -> {
            place(sp, 0, new ItemStack(weapon));
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

    /**
     * The shot out of a slide (author, 2026-10-31): sprinting, C tapped, the right button as the slide
     * goes: a whole turn down onto one knee, the bow drawn; the camera turned a little and W held on the
     * knee (it creeps); let go: three arrows at once, side by side, at three zombies ahead; up, and
     * walking on. Then the tray (the bow's rows: G(G+), C+LMB, C+RMB, the marked, RMB+R) with the
     * enchanted golden apple eaten: every wait half as long.
     */
    private static void slideShot(String name, CameraType camera) {
        SCENARIOS.add(new Scenario(name, 110, sp -> {
            place(sp, 0, new ItemStack(Items.BOW));
            sp.getInventory().add(new ItemStack(Items.ARROW, 16));
            sp.teleportTo(sp.serverLevel(), 0.5, Y + 1, -8.5, 0f, 0f);
            for (int i = -1; i <= 1; i++) dummy(sp, net.minecraft.world.entity.EntityType.ZOMBIE, i * 1.4, 14, -1f);
            Stamina st = Stamina.of(sp);
            st.slideShotCooldown = 0;
            st.legolasCooldown = 0;
            st.hasteTicks = 0;
        }, (mc, t) -> {
            camera(mc, camera);
            Stamina s = Stamina.of(mc.player);
            if (t == 1) s.slideShotCooldown = 0;
            KeyMapping slide = slideKey(mc);
            mc.options.keyUp.setDown((t >= 2 && t < 14) || (t >= 34 && t < 44));
            mc.options.keySprint.setDown(t >= 2 && t < 14);
            if (t == 12) slide.setDown(true);
            if (t == 13) slide.setDown(false);
            if (t == 16) {
                startPos = mc.player.position();
                mc.options.keyUse.setDown(true);
                KeyMapping.click(mc.options.keyUse.getKey());
            }
            if (t == 17 || t == 24 || t == 30) note(String.format(Locale.ROOT, "%s t=%d: move %s, tick %d, drawing %s, yaw %.0f", name, t,
                    s.move, s.moveTick, mc.player.isUsingItem(), mc.player.getYRot()));
            if (t == 30) note(String.format(Locale.ROOT, "%s: slid on %.2f blocks into the knee", name,
                    mc.player.position().subtract(startPos).horizontalDistance()));
            // the camera turned a little and back (it is free on the knee); the arrows go the way it looks
            if (t >= 30 && t < 34) mc.player.setYRot(mc.player.getYRot() + 2f);
            if (t >= 40 && t < 44) mc.player.setYRot(mc.player.getYRot() - 2f);
            if (t == 34) startPos = mc.player.position();
            if (t == 44) note(String.format(Locale.ROOT, "%s: crept %.2f blocks in 0.5 s on the knee, move %s", name,
                    mc.player.position().subtract(startPos).horizontalDistance(), s.move));
            if (t == 50) mc.options.keyUse.setDown(false);
            if (t == 51 || t == 54) note(String.format(Locale.ROOT, "%s t=%d: let go, move %s, arrows %d", name, t, s.move,
                    mc.player.getInventory().countItem(Items.ARROW)));
            if (t == 80) {
                note(String.format(Locale.ROOT, "%s: arrows left %d (16 - 3), move %s, wait %d s", name,
                        mc.player.getInventory().countItem(Items.ARROW), s.move, (s.slideShotCooldown + 19) / 20));
                dummyReport(mc, name, true);
            }
            // the tray with the enchanted golden apple: the waits started now are half as long
            if (t == 84) mc.getSingleplayerServer().execute(() -> {
                Stamina st = Stamina.of(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));
                st.ateGoldenApple(true);
                st.legolasCooldown = st.cooled(dev.mcreload.moves.MovesConfig.legolasCooldownTicks());
                st.markedCooldown = st.cooled(dev.mcreload.moves.MovesConfig.markedCooldownTicks());
                st.bowCooldown = st.cooled(dev.mcreload.moves.MovesConfig.hailCooldownTicks());
            });
            if (t == 100) note(String.format(Locale.ROOT, "%s: enchanted apple, free %d s, waits: G %d s (40 halved), C+RMB %d s (was halved at the apple),"
                            + " marked %d s (25 halved), RMB+R %d s (12 halved)", name, s.freeTicks / 20, (s.bowCooldown + 19) / 20,
                    (s.slideShotCooldown + 19) / 20, (s.markedCooldown + 19) / 20, (s.legolasCooldown + 19) / 20));
            // the apple and the waits do not carry over into the scenarios after
            if (t == 106) mc.getSingleplayerServer().execute(() -> {
                Stamina st = Stamina.of(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));
                st.hasteTicks = 0;
                st.freeTicks = 0;
                st.setCooldowns(new int[Stamina.of(mc.player).cooldowns().length]);
            });
        }, new int[]{14, 17, 19, 21, 23, 26, 30, 44, 51, 53, 56, 60, 66, 72, 104}, camera != CameraType.FIRST_PERSON));
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

    /**
     * Running at the wall, a jump with the right button: caught; D held: the hops one after another
     * along it; a pillar a block higher across a gap looked at (green), Space: the leap
     * onto its edge; a pillar too far looked at (red); W + Space: up onto the pillar, a carpet on its top
     * (author, 2026-10-30, 23:01).
     */
    private static void ledgeJump(String name, CameraType camera) {
        SCENARIOS.add(new Scenario(name, 150, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            ledgeWall(sp);
            ServerLevel level = sp.serverLevel();
            for (int y = Y + 1; y <= Y + 4; y++) {
                for (int z = 3; z <= 4; z++) {
                    level.setBlock(new BlockPos(LEDGE_X - 5, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                    level.setBlock(new BlockPos(LEDGE_X - 14, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                }
            }
            level.setBlock(new BlockPos(LEDGE_X - 5, Y + 5, 3), Blocks.WHITE_CARPET.defaultBlockState(), 2);
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
            // D held: the hops one after another (no creeping along the edge any more)
            mc.options.keyRight.setDown(t >= 18 && t < 44);
            if (t == 24) note(name + ": D held: " + Stamina.of(p).move);
            if (t == 58) note(String.format(Locale.ROOT, "%s: D held 1.3 s, the hops went %.2f blocks, now %s at x %.2f", name,
                    p.position().subtract(startPos).horizontalDistance(), Stamina.of(p).move, p.getX()));
            // the pillar's edge across the gap: looked at, lit green; Space: the leap
            if (t >= 60 && t < 70) lookAt(p, new Vec3(LEDGE_X - 4.6, Y + 4.85, 3.0));
            if (t == 66) note(name + ": the pillar's edge looked at: " + aimNote());
            if (t == 70) {
                startPos = p.position();
                KeyMapping.click(mc.options.keyJump.getKey());
                mc.options.keyJump.setDown(true);
            }
            if (t == 71) {
                mc.options.keyJump.setDown(false);
                note(name + ": Space: " + Stamina.of(p).move);
            }
            if (t == 92) note(String.format(Locale.ROOT, "%s: after the leap %s, %.2f blocks across, the edge %.2f higher, feet %.2f below it",
                    name, Stamina.of(p).move, p.position().subtract(startPos).horizontalDistance(), Stamina.of(p).ledgeTop - (Y + 4),
                    Stamina.of(p).ledgeTop - p.getY()));
            // a pillar too far: lit red
            if (t >= 94 && t < 102) lookAt(p, new Vec3(LEDGE_X - 13.5, Y + 4.85, 3.0));
            if (t == 100) note(name + ": the far pillar looked at: " + aimNote());
            if (t == 102) p.setYRot(0f);
            if (t == 106) {
                mc.options.keyUp.setDown(true);
                mc.options.keyJump.setDown(true);
                minHeight = p.getHealth();
            }
            if (t == 108) {
                mc.options.keyUp.setDown(false);
                mc.options.keyJump.setDown(false);
                note(name + ": climbing " + Stamina.of(p).move);
            }
            if (t > 106 && t < 145) minHeight = Math.min(minHeight, p.getHealth());
            if (t == 145) note(String.format(Locale.ROOT, "%s: lowest health on the climb %.1f; after it feet %.3f above the pillar's top "
                    + "(the carpet: 0.062), on the ground %s, %s", name, minHeight, p.getY() - (Y + 5), p.onGround(), Stamina.of(p).move));
        }, new int[]{8, 12, 16, 22, 28, 34, 40, 50, 66, 72, 74, 76, 78, 82, 92, 100, 110, 114, 118, 122, 130}, camera != CameraType.FIRST_PERSON));
    }

    /** The player looking at a point (the camera with them). */
    private static void lookAt(LocalPlayer p, Vec3 at) {
        float[] look = dev.mcreload.moves.Marks.look(at.subtract(p.getEyePosition()));
        p.setYRot(look[0]);
        p.setXRot(look[1]);
        p.setYHeadRot(look[0]);
    }

    /** What the edge looked at from the hang is: none, or green (in reach) or red, where. */
    private static String aimNote() {
        dev.mcreload.moves.Ledge.Target aim = LedgeInput.aim;
        if (aim == null) return "none";
        return String.format(Locale.ROOT, "%s, the edge's top %.2f, x %.2f", aim.reachable() ? "green (in reach)" : "red (too far)",
                aim.spot().top(), aim.spot().at().x);
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
    /** The nearest of the self-test's mobs to the player, on this client. */
    /** The self-test's mobs on this client, from the player's left to their right (facing +Z: from +X to -X). */
    private static List<net.minecraft.world.entity.Entity> dummies(Minecraft mc) {
        List<net.minecraft.world.entity.Entity> out = new ArrayList<>(mc.level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                mc.player.getBoundingBox().inflate(16)));
        out.sort((a, b) -> Double.compare(b.getX(), a.getX()));
        return out;
    }

    private static net.minecraft.world.entity.Entity nearestDummy(Minecraft mc) {
        net.minecraft.world.entity.Entity best = null;
        for (var e : mc.level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, mc.player.getBoundingBox().inflate(12))) {
            if (best == null || e.distanceTo(mc.player) < best.distanceTo(mc.player)) best = e;
        }
        return best;
    }

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

    private static final net.minecraft.resources.ResourceLocation KINETRIX_CELEBRATE = new net.minecraft.resources.ResourceLocation("kinetrix", "celebrate");

    /**
     * Kinetrix's emote of this player (by reflection: Kinetrix is not compiled against): {@code play} >= 0
     * starts that emote as its menu does; returns the emote playing now (-1: none, null: no Kinetrix).
     */
    private static Integer kinetrixEmote(Minecraft mc, int play) {
        try {
            Object rig = Class.forName("dev.mcreload.anim.client.rig.RigRegistry")
                    .getMethod("get", net.minecraft.client.player.AbstractClientPlayer.class).invoke(null, mc.player);
            Object motion = rig.getClass().getField("motion").get(rig);
            if (play >= 0) motion.getClass().getMethod("playEmote", int.class).invoke(motion, play);
            return motion.getClass().getField("emote").getInt(motion);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Kinetrix's celebrate music is playing on this client (the sound engine's playing instances; the development game's names). */
    private static boolean musicPlaying(Minecraft mc) {
        try {
            java.lang.reflect.Field f = mc.getSoundManager().getClass().getDeclaredField("soundEngine");
            f.setAccessible(true);
            Object engine = f.get(mc.getSoundManager());
            java.lang.reflect.Field g = engine.getClass().getDeclaredField("instanceToChannel");
            g.setAccessible(true);
            for (Object o : ((java.util.Map<?, ?>) g.get(engine)).keySet()) {
                if (o instanceof net.minecraft.client.resources.sounds.SoundInstance si && si.getLocation().equals(KINETRIX_CELEBRATE)) return true;
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
