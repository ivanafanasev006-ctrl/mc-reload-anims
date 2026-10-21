package dev.mcreload.anim.client.selftest;

import dev.mcreload.anim.McReloadAnim;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Automated visual test, enabled only with -Dmcreload.selftest=true (used by CI).
 * Creates a world, builds a test area, runs every animation scenario with real player input and
 * saves screenshots showing the world view plus side / front / back views of the player.
 */
public final class SelfTest {
    public static boolean enabled() {
        return Boolean.getBoolean("mcreload.selftest");
    }

    static final int Y = 120;
    private static final int POOL_X = 75, LADDER_X = -6;

    private enum Phase { BOOT, CREATING, WAITING_WORLD, SETUP, RUNNING, DONE }

    private static Phase phase = Phase.BOOT;
    private static int timer;
    private static int scenarioIndex;
    private static int scenarioTick;
    private static final List<Scenario> SCENARIOS = new ArrayList<>();
    static String currentLabel = "";

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(SelfTest::onTick);
        MinecraftForge.EVENT_BUS.addListener(SelfTest::onHud);
        MinecraftForge.EVENT_BUS.addListener(SelfTestOverlay::onRenderGui);
        buildScenarios();
        McReloadAnim.LOG.info("[kinetrix] SELF-TEST MODE");
    }

    private static void onHud(RenderGuiOverlayEvent.Pre event) {
        if (phase == Phase.RUNNING) event.setCanceled(true);
    }

    private static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        timer++;
        try {
            switch (phase) {
                case BOOT -> boot(mc);
                case CREATING -> pressCreate(mc);
                case WAITING_WORLD -> {
                    if (mc.player != null && mc.level != null && mc.screen == null && mc.getSingleplayerServer() != null) {
                        if (timer > 80) {
                            phase = Phase.SETUP;
                            timer = 0;
                        }
                    } else {
                        timer = 0;
                    }
                }
                case SETUP -> setup(mc);
                case RUNNING -> run(mc);
                case DONE -> {
                    if (timer == 40) {
                        McReloadAnim.LOG.info("[kinetrix] SELF-TEST FINISHED");
                        mc.stop();
                    }
                }
            }
        } catch (Throwable t) {
            McReloadAnim.LOG.error("[kinetrix] SELF-TEST ERROR in phase " + phase, t);
            phase = Phase.DONE;
            timer = 0;
        }
        if (timer > 20 * 60 * 4 && phase != Phase.RUNNING) {
            McReloadAnim.LOG.error("[kinetrix] SELF-TEST TIMEOUT in phase " + phase);
            mc.stop();
        }
    }

    private static void boot(Minecraft mc) {
        Screen s = mc.screen;
        if (s == null || timer < 40) return;
        if (!(s instanceof TitleScreen)) {
            mc.options.onboardAccessibility = false;
            mc.setScreen(new TitleScreen());
            return;
        }
        CreateWorldScreen.openFresh(mc, s);
        phase = Phase.CREATING;
        timer = 0;
    }

    private static void pressCreate(Minecraft mc) {
        if (!(mc.screen instanceof CreateWorldScreen screen) || timer < 20) return;
        String create = Component.translatable("selectWorld.create").getString();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button b && b.getMessage().getString().equals(create)) {
                b.onPress();
                phase = Phase.WAITING_WORLD;
                timer = 0;
                return;
            }
        }
    }

    private static void setup(Minecraft mc) {
        if (timer != 1) {
            if (timer > 60) {
                phase = Phase.RUNNING;
                timer = 0;
                scenarioIndex = 0;
                scenarioTick = 0;
            }
            return;
        }
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        mc.options.hideGui = false;
        IntegratedServer srv = mc.getSingleplayerServer();
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayers().get(0);
            ServerLevel lvl = sp.serverLevel();
            sp.setGameMode(GameType.CREATIVE);
            lvl.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, srv);
            lvl.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, srv);
            lvl.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, srv);
            lvl.setDayTime(6000);
            lvl.setWeatherParameters(12000, 0, false, false);

            BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
            BlockState air = Blocks.AIR.defaultBlockState();
            // Running track along +X.
            for (int x = -12; x <= 100; x++) {
                for (int z = -8; z <= 8; z++) {
                    lvl.setBlock(new BlockPos(x, Y - 1, z), floor, 2);
                    for (int y = Y; y < Y + 24; y++) lvl.setBlock(new BlockPos(x, y, z), air, 2);
                }
            }
            // Swimming pool, 3 deep.
            for (int x = POOL_X; x <= POOL_X + 22; x++) {
                for (int z = -4; z <= 4; z++) {
                    for (int y = Y - 4; y <= Y - 1; y++) {
                        boolean wall = x == POOL_X || x == POOL_X + 22 || z == -4 || z == 4 || y == Y - 4;
                        lvl.setBlock(new BlockPos(x, y, z), wall ? floor : Blocks.WATER.defaultBlockState(), 2);
                    }
                }
            }
            // Ladder on a wall.
            BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
            for (int y = Y; y < Y + 20; y++) {
                lvl.setBlock(new BlockPos(LADDER_X, y, 0), floor, 2);
                lvl.setBlock(new BlockPos(LADDER_X + 1, y, 0), ladder, 2);
            }
        });
    }

    // ------------------------------------------------------------------ scenarios

    /** Capture every rendered frame between two ticks (at most maxFrames, at least spacing apart). */
    private record Window(int from, int to, int maxFrames, float spacing) {}

    private record Scenario(String name, int length, Setup setup, Driver driver, Window... windows) {}

    @FunctionalInterface
    private interface Setup { void run(ServerPlayer sp); }

    @FunctionalInterface
    private interface Driver { void tick(Minecraft mc, int t); }

    private static final ScriptedInput INPUT = new ScriptedInput();
    private static int framesInWindow;
    private static int currentWindow = -1;
    private static long lastShotNanos;
    private static int shotCounter;
    private static int fpsLogTimer;

    private static void buildScenarios() {
        ItemStack none = ItemStack.EMPTY;
        SCENARIOS.add(new Scenario("00_stand", 30, sp -> place(sp, 2, Y, 0, -90, none),
                (mc, t) -> {
                    move(mc, false, false, false, false);
                    var r = dev.mcreload.anim.client.rig.RigRegistry.get(mc.player);
                    if (r != null) { r.motion.idleSince = r.motion.time; r.motion.idleAction = -1; }
                }, new Window(15, 30, 1, 0.4f)));
        SCENARIOS.add(new Scenario("03b_sprint_brake", 45, sp -> place(sp, 2, Y, 0, -90, none),
                (mc, t) -> move(mc, t < 25, t < 25, false, false), new Window(25, 38, 4, 0.06f)));
        SCENARIOS.add(new Scenario("08a_slash_1", 25, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.DIAMOND_SWORD)),
                (mc, t) -> { if (t == 10) mc.player.swing(InteractionHand.MAIN_HAND); }, new Window(10, 25, 5, 0.03f)));
        SCENARIOS.add(new Scenario("08b_slash_2", 16, sp -> {},
                (mc, t) -> { if (t == 4) mc.player.swing(InteractionHand.MAIN_HAND); }, new Window(4, 16, 4, 0.03f)));
        SCENARIOS.add(new Scenario("08e_sword_guard", 40, sp -> {},
                (mc, t) -> {}, new Window(20, 40, 2, 0.4f)));
        SCENARIOS.add(new Scenario("09_axe", 40, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.IRON_AXE)),
                (mc, t) -> { if (t == 8) mc.player.swing(InteractionHand.MAIN_HAND); },
                new Window(8, 26, 4, 0.05f), new Window(32, 40, 1, 0.3f)));
        SCENARIOS.add(new Scenario("10b_hand_digging", 40, sp -> placeLook(sp, 2, Y, 0, -90, 60, none),
                (mc, t) -> { if (t >= 8 && t % 3 == 0) mc.player.swing(InteractionHand.MAIN_HAND); }, new Window(10, 36, 6, 0.05f)));
        SCENARIOS.add(new Scenario("16_place_block", 30, sp -> placeLook(sp, 2, Y, 0, -90, 50, new ItemStack(Items.OAK_PLANKS, 16)),
                (mc, t) -> {
                    boolean use = t == 8;
                    mc.options.keyUse.setDown(use);
                    if (use) mc.player.swing(InteractionHand.MAIN_HAND);
                }, new Window(8, 18, 4, 0.04f)));
        SCENARIOS.add(new Scenario("14_bow", 45, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.BOW)),
                (mc, t) -> mc.options.keyUse.setDown(t > 5), new Window(20, 45, 3, 0.3f)));
        SCENARIOS.add(new Scenario("24_shield_walk", 45, sp -> { place(sp, 2, Y, 0, -90, new ItemStack(Items.IRON_SWORD)); sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); },
                (mc, t) -> { mc.options.keyUse.setDown(t > 4); move(mc, t > 15, false, false, false); },
                new Window(10, 15, 1, 0.2f), new Window(22, 45, 4, 0.12f)));
        SCENARIOS.add(new Scenario("28_sneak_toggle", 50, sp -> place(sp, 2, Y, 3, -90, none),
                (mc, t) -> move(mc, t > 4, false, t > 8 && (t / 3) % 2 == 0, false), new Window(9, 50, 10, 0.0f)));
        SCENARIOS.add(new Scenario("29_back_jump", 50, sp -> place(sp, 10, Y, 3, -90, none),
                (mc, t) -> { moveBack(mc, t < 40); INPUT.jump = t >= 8 && t < 10; }, new Window(9, 36, 9, 0.02f)));
        SCENARIOS.add(new Scenario("30_crossbow", 40, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.CROSSBOW)),
                (mc, t) -> move(mc, t > 22, false, false, false), new Window(12, 20, 1, 0.3f), new Window(28, 40, 2, 0.2f)));
        SCENARIOS.add(new Scenario("31_crossbow_loaded", 30, sp -> {
                    ItemStack cb = new ItemStack(Items.CROSSBOW);
                    net.minecraft.world.item.CrossbowItem.setCharged(cb, true);
                    place(sp, 2, Y, 0, -90, cb);
                }, (mc, t) -> move(mc, false, false, false, false), new Window(14, 30, 1, 0.3f)));
        SCENARIOS.add(new Scenario("32_fishing", 75, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.FISHING_ROD)),
                (mc, t) -> mc.options.keyUse.setDown(t == 8 || t == 55),
                new Window(8, 22, 5, 0.02f), new Window(35, 50, 1, 0.3f), new Window(55, 66, 3, 0.03f)));
        SCENARIOS.add(new Scenario("25_eat", 40, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.GOLDEN_APPLE, 8)),
                (mc, t) -> mc.options.keyUse.setDown(t > 5), new Window(15, 38, 3, 0.25f)));
        SCENARIOS.add(new Scenario("07c_sword_landing", 60, sp -> place(sp, 2, Y + 7, 0, -90, new ItemStack(Items.IRON_SWORD)),
                (mc, t) -> move(mc, false, false, false, false), new Window(6, 18, 3, 0.0f), new Window(19, 45, 5, 0.06f)));
        SCENARIOS.add(new Scenario("26_explosion", 45, sp -> place(sp, 2, Y, 0, -90, none),
                (mc, t) -> { if (t == 10) { explode(mc, 4.0, 0.5); blastCue(mc); } }, new Window(11, 34, 4, 0.08f)));
        SCENARIOS.add(new Scenario("26b_explosion_sword", 45, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.IRON_SWORD)),
                (mc, t) -> { if (t == 10) { explode(mc, 4.0, 0.5); blastCue(mc); } }, new Window(11, 34, 4, 0.08f)));
        SCENARIOS.add(new Scenario("27_totem", 50, sp -> place(sp, 2, Y, 0, -90, none),
                (mc, t) -> { if (t == 8) { var r = dev.mcreload.anim.client.rig.RigRegistry.get(mc.player); if (r != null) r.motion.onTotem(); } },
                new Window(14, 50, 3, 0.3f)));
        SCENARIOS.add(new Scenario("19_horse", 90, SelfTest::mountHorse,
                (mc, t) -> {
                    // walk, gallop, hold jump and release (the horse jumps), then get off.
                    move(mc, t > 8 && t < 66, t > 28 && t < 66, t >= 72 && t < 75, t >= 44 && t < 52);
                }, new Window(18, 27, 2, 0.15f), new Window(34, 44, 2, 0.1f), new Window(52, 66, 5, 0.02f),
                new Window(73, 90, 3, 0.05f)));
        SCENARIOS.add(new Scenario("11b_tread", 50, sp -> place(sp, POOL_X + 10, Y - 2, 0, -90, none),
                (mc, t) -> move(mc, false, false, false, t % 10 < 3), new Window(20, 50, 3, 0.3f)));
        SCENARIOS.add(new Scenario("11d_surface_swim", 60, sp -> place(sp, POOL_X + 3, Y - 2, 0, -90, none),
                (mc, t) -> move(mc, t > 5, false, false, t % 8 < 3), new Window(25, 60, 4, 0.2f)));
        SCENARIOS.add(new Scenario("11c_dive_high", 50, sp -> place(sp, POOL_X + 12, Y + 5, 0, -90, none),
                (mc, t) -> move(mc, false, false, false, false), new Window(5, 40, 8, 0.05f)));
        SCENARIOS.add(new Scenario("11f_chill_fall", 70, sp -> place(sp, POOL_X + 14, Y + 14, 0, 90, none),
                (mc, t) -> moveBack(mc, true), new Window(6, 60, 8, 0.12f)));
        SCENARIOS.add(new Scenario("11g_surfacing", 60, sp -> place(sp, POOL_X + 8, Y - 3, 0, -90, none),
                (mc, t) -> move(mc, false, false, false, true), new Window(10, 60, 6, 0.2f)));
        for (int i : new int[]{MotionState_EMOTE_WAVE, MotionState_EMOTE_FACEPALM, MotionState_EMOTE_SALUTE, MotionState_EMOTE_CLAP}) {
            final int emote = i;
            SCENARIOS.add(new Scenario("40_emote_" + dev.mcreload.anim.client.rig.MotionState.EMOTE_KEYS[i], 60,
                    sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                    (mc, t) -> {
                        move(mc, false, false, false, false);
                        if (t == 16) { var r = dev.mcreload.anim.client.rig.RigRegistry.get(mc.player); if (r != null) r.motion.playEmote(emote); }
                    }, new Window(24, 58, 3, 0.35f)));
        }
    }

    private static final int MotionState_EMOTE_WAVE = dev.mcreload.anim.client.rig.MotionState.EMOTE_WAVE,
            MotionState_EMOTE_FACEPALM = dev.mcreload.anim.client.rig.MotionState.EMOTE_FACEPALM,
            MotionState_EMOTE_SALUTE = dev.mcreload.anim.client.rig.MotionState.EMOTE_SALUTE,
            MotionState_EMOTE_CLAP = dev.mcreload.anim.client.rig.MotionState.EMOTE_CLAP;

    private static void startGlide(Minecraft mc) {
        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(
                mc.player, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
    }

    /** Glide in from low enough to touch down a couple of seconds later. */
    private static void launchElytraLow(ServerPlayer sp) {
        place(sp, -8, Y + 9, 4, -90, ItemStack.EMPTY);
        sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        sp.teleportTo(sp.serverLevel(), -7.5, Y + 9, 4.5, -90, 25f);
    }

    private static void swapHands(Minecraft mc) {
        IntegratedServer srv = mc.getSingleplayerServer();
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayers().get(0);
            ItemStack main = sp.getMainHandItem().copy(), off = sp.getOffhandItem().copy();
            sp.setItemInHand(InteractionHand.MAIN_HAND, off);
            sp.setItemInHand(InteractionHand.OFF_HAND, main);
        });
    }

    private static void explode(Minecraft mc, double dx, double dz) {
        IntegratedServer srv = mc.getSingleplayerServer();
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayers().get(0);
            sp.serverLevel().explode(null, sp.getX() + dx, sp.getY() + 0.5, sp.getZ() + dz, 2.0f,
                    net.minecraft.world.level.Level.ExplosionInteraction.NONE);
        });
    }

    private static void launchElytra(ServerPlayer sp) {
        place(sp, 2, Y + 40, 0, -90, ItemStack.EMPTY);
        sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        sp.teleportTo(sp.serverLevel(), 2.5, Y + 40, 0.5, -90, 10f);
    }

    /** No sound card in CI, so the explosion sound cue never fires: trigger the reaction directly. */
    private static void blastCue(Minecraft mc) {
        var rig = dev.mcreload.anim.client.rig.RigRegistry.get(mc.player);
        if (rig != null) rig.motion.onExplosion(4.0);
    }

    private static void shieldUp(ServerPlayer sp) {
        place(sp, 2, Y, 0, -90, new ItemStack(Items.IRON_SWORD));
        sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
    }

    private static void goToBed(ServerPlayer sp) {
        place(sp, 2, Y, 0, -90, ItemStack.EMPTY);
        sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
        ServerLevel lvl = sp.serverLevel();
        lvl.setDayTime(18000);
        BlockPos foot = new BlockPos(6, Y, 3), head = foot.east();
        BlockState bed = Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.EAST);
        lvl.setBlock(foot, bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                net.minecraft.world.level.block.state.properties.BedPart.FOOT), 3);
        lvl.setBlock(head, bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
        sp.teleportTo(lvl, 5.5, Y, 3.5, -90, 0f);
        sp.startSleeping(head);
    }

    private static void placeLook(ServerPlayer sp, double x, double y, double z, float yaw, float pitch, ItemStack hand) {
        place(sp, x, y, z, yaw, hand);
        sp.teleportTo(sp.serverLevel(), x + 0.5, y, z + 0.5, yaw, pitch);
    }

    private static void boardBoat(ServerPlayer sp) {
        sp.stopRiding();
        place(sp, POOL_X + 3, Y - 1, 0, -90, ItemStack.EMPTY);
        net.minecraft.world.entity.vehicle.Boat boat = new net.minecraft.world.entity.vehicle.Boat(sp.serverLevel(), POOL_X + 3.5, Y - 1, 0.5);
        boat.setYRot(-90);
        sp.serverLevel().addFreshEntity(boat);
        sp.startRiding(boat, true);
    }

    private static void mountHorse(ServerPlayer sp) {
        sp.stopRiding();
        place(sp, 2, Y, 0, -90, ItemStack.EMPTY);
        net.minecraft.world.entity.animal.horse.Horse horse =
                net.minecraft.world.entity.EntityType.HORSE.create(sp.serverLevel());
        if (horse == null) return;
        horse.moveTo(2.5, Y, 0.5, -90, 0);
        horse.setTamed(true);
        horse.equipSaddle(null);
        sp.serverLevel().addFreshEntity(horse);
        sp.startRiding(horse, true);
    }

    private static void run(Minecraft mc) {
        if (scenarioIndex >= SCENARIOS.size()) {
            move(mc, false, false, false, false);
            mc.options.keyUse.setDown(false);
            phase = Phase.DONE;
            timer = 0;
            return;
        }
        if (mc.player != null && mc.player.input != INPUT) mc.player.input = INPUT;
        if (++fpsLogTimer % 20 == 0 && mc.player != null) {
            dev.mcreload.anim.client.rig.PlayerRig rig = dev.mcreload.anim.client.rig.RigRegistry.get(mc.player);
            McReloadAnim.LOG.info("[kinetrix] self-test {} t={} fps={} pos=({}, {}, {}) ground={} sprint={} speed={} sprintW={} airW={} walkSpeed={}",
                    currentLabel, scenarioTick, mc.getFps(),
                    String.format("%.2f", mc.player.getX()), String.format("%.2f", mc.player.getY()), String.format("%.2f", mc.player.getZ()),
                    mc.player.onGround(), mc.player.isSprinting(),
                    rig == null ? "-" : String.format("%.3f", rig.motion.speed.v),
                    rig == null ? "-" : String.format("%.2f", rig.motion.sprintW.v),
                    rig == null ? "-" : String.format("%.2f", rig.motion.airW.v),
                    String.format("%.2f", mc.player.walkAnimation.speed()));
        }

        Scenario s = SCENARIOS.get(scenarioIndex);
        currentLabel = s.name();
        if (scenarioTick == 0) {
            mc.options.keyUse.setDown(false);
            mc.options.keyDrop.setDown(false);
            framesInWindow = 0;
            currentWindow = -1;
            shotCounter = 0;
            IntegratedServer srv = mc.getSingleplayerServer();
            srv.execute(() -> s.setup().run(srv.getPlayerList().getPlayers().get(0)));
        }
        if (scenarioTick > 3) s.driver().tick(mc, scenarioTick);
        scenarioTick++;
        if (scenarioTick > s.length()) {
            scenarioIndex++;
            scenarioTick = 0;
        }
    }

    /** Called after every rendered frame (GUI included). */
    static void onFrameRendered(Minecraft mc) {
        if (phase != Phase.RUNNING || scenarioIndex >= SCENARIOS.size()) return;
        Scenario s = SCENARIOS.get(scenarioIndex);
        int t = scenarioTick;
        for (int wi = 0; wi < s.windows().length; wi++) {
            Window w = s.windows()[wi];
            if (t < w.from() || t > w.to()) continue;
            if (wi != currentWindow) {
                currentWindow = wi;
                framesInWindow = 0;
            }
            long now = System.nanoTime();
            if (framesInWindow >= w.maxFrames() || (now - lastShotNanos) / 1e9f < w.spacing()) return;
            framesInWindow++;
            lastShotNanos = now;
            String file = s.name() + "_f" + String.format("%02d", shotCounter++) + "_t" + t + ".png";
            Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), msg -> {});
            return;
        }
    }

    private static void place(ServerPlayer sp, double x, double y, double z, float yaw, ItemStack hand) {
        if (sp.isPassenger()) sp.stopRiding();
        if (sp.isSleeping()) sp.stopSleeping();
        if (sp.isFallFlying()) sp.stopFallFlying();
        sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
        sp.serverLevel().setDayTime(6000);
        sp.setItemInHand(InteractionHand.MAIN_HAND, hand.copy());
        sp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        sp.getAbilities().flying = false;
        sp.onUpdateAbilities();
        sp.setDeltaMovement(0, 0, 0);
        sp.teleportTo(sp.serverLevel(), x + 0.5, y, z + 0.5, yaw, 0f);
        sp.setYHeadRot(yaw);
    }

    private static void moveBack(Minecraft mc, boolean back) {
        INPUT.forward = false;
        INPUT.back = back;
        INPUT.sneak = false;
        INPUT.jump = false;
        if (mc.player != null) mc.player.setSprinting(false);
    }

    private static void move(Minecraft mc, boolean forward, boolean sprint, boolean sneak, boolean jump) {
        INPUT.back = false;
        INPUT.forward = forward;
        INPUT.sneak = sneak;
        INPUT.jump = jump;
        if (mc.player != null) mc.player.setSprinting(sprint && forward);
    }

    private SelfTest() {}
}
