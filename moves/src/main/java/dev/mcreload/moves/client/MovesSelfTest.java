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

        // ---- the hop over a zombie set on the player, and an arrow knocked aside by a dodge
        SCENARIOS.add(new Scenario("70_hop_over_zombie", 60, sp -> {
            place(sp, 0, new ItemStack(Items.IRON_SWORD));
            net.minecraft.world.entity.monster.Zombie z = net.minecraft.world.entity.EntityType.ZOMBIE.create(sp.serverLevel());
            z.moveTo(sp.getX(), sp.getY(), sp.getZ() + 2, 180f, 0f);
            z.setYHeadRot(180f);
            z.setNoAi(true);
            z.setAggressive(true);
            z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));    // not burning in the sun
            z.addTag("kinetrix_moves.selftest");
            sp.serverLevel().addFreshEntity(z);
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            if (t == 8) {
                startPos = mc.player.position();
                press(mc, MovesClient.DODGE, mc.options.keyUp);
            }
            if (t == 10) release(mc);
            if (t == 11) note("hop: move " + Stamina.of(mc.player).move);
            if (t == 52) {
                Vec3 d = mc.player.position().subtract(startPos);
                note(String.format(Locale.ROOT, "hop: went %.2f blocks along z (zombie at +2), now facing yaw %.0f, health %.1f",
                        d.z, mc.player.getYRot(), mc.player.getHealth()));
                IntegratedServer srv = mc.getSingleplayerServer();
                srv.execute(() -> {
                    ServerPlayer sp = srv.getPlayerList().getPlayers().get(0);
                    note("hop: Speed after landing " + sp.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED));
                    for (var e : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class,
                            sp.getBoundingBox().inflate(8))) if (e.getTags().contains("kinetrix_moves.selftest")) e.discard();
                });
            }
        }, new int[]{8, 12, 15, 19, 23, 27, 31, 36, 46}, true));
        SCENARIOS.add(new Scenario("71_deflect_arrow", 30, sp -> place(sp, 0, new ItemStack(Items.IRON_SWORD)), (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            if (t == 8) {
                IntegratedServer srv = mc.getSingleplayerServer();
                srv.execute(() -> {
                    ServerPlayer sp = srv.getPlayerList().getPlayers().get(0);
                    net.minecraft.world.entity.projectile.Arrow a = new net.minecraft.world.entity.projectile.Arrow(sp.serverLevel(),
                            sp.getX(), sp.getY() + 1.3, sp.getZ() + 5);
                    a.shoot(0, 0, -1, 1.6f, 0f);
                    sp.serverLevel().addFreshEntity(a);
                });
                press(mc, MovesClient.DODGE, mc.options.keyLeft);
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

        // ---- after the volley, the right button still held: on one knee, creeping and aiming; the
        // left button shoots early and the next arrow is drawn at once; letting go shoots and stands up
        SCENARIOS.add(new Scenario("81_bow_kneel", 112, sp -> {
            place(sp, 0, new ItemStack(Items.BOW));
            sp.getInventory().add(new ItemStack(Items.ARROW, 10));
        }, (mc, t) -> {
            camera(mc, CameraType.THIRD_PERSON_BACK);
            if (t == 2) {
                mc.options.keyUse.setDown(true);
                KeyMapping.click(mc.options.keyUse.getKey());
            }
            if (t == 26) mc.options.keyAttack.setDown(true);
            if (t == 27) mc.options.keyAttack.setDown(false);
            if (t == 56) {
                note("kneel: kneeling " + BowStance.kneeling(mc.player) + ", drawing " + mc.player.isUsingItem());
                startPos = mc.player.position();
                mc.player.setXRot(-20f);
            }
            mc.options.keyUp.setDown(t >= 56 && t < 76);
            if (t == 76) {
                Vec3 d = mc.player.position().subtract(startPos);
                note(String.format(Locale.ROOT, "kneel: crept %.2f blocks in 1 s (walking with a drawn bow: about 0.9)", d.horizontalDistance()));
            }
            if (t == 80) {
                note("kneel: arrows left " + mc.player.getInventory().countItem(Items.ARROW) + ", drawn " + mc.player.getTicksUsingItem() + " ticks");
                mc.options.keyAttack.setDown(true);
            }
            if (t == 81) mc.options.keyAttack.setDown(false);
            if (t == 84) {
                note("kneel: after the early shot arrows left " + mc.player.getInventory().countItem(Items.ARROW)
                        + ", kneeling " + BowStance.kneeling(mc.player) + ", drawing again " + mc.player.isUsingItem());
            }
            if (t == 96) mc.options.keyUse.setDown(false);
            if (t == 102) {
                note("kneel: let go, arrows left " + mc.player.getInventory().countItem(Items.ARROW)
                        + ", kneeling " + BowStance.kneeling(mc.player));
            }
        }, new int[]{50, 58, 70, 80, 82, 90, 98, 108}, true));

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

    private static void writeReport(Minecraft mc) {
        Path out = mc.gameDirectory.toPath().resolve("moves-selftest.txt");
        try {
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
