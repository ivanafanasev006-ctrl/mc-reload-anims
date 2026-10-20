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
        McReloadAnim.LOG.info("[mcreload] SELF-TEST MODE");
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
                        McReloadAnim.LOG.info("[mcreload] SELF-TEST FINISHED");
                        mc.stop();
                    }
                }
            }
        } catch (Throwable t) {
            McReloadAnim.LOG.error("[mcreload] SELF-TEST ERROR in phase " + phase, t);
            phase = Phase.DONE;
            timer = 0;
        }
        if (timer > 20 * 60 * 4 && phase != Phase.RUNNING) {
            McReloadAnim.LOG.error("[mcreload] SELF-TEST TIMEOUT in phase " + phase);
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
        SCENARIOS.add(new Scenario("01_idle", 50, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY), (mc, t) -> {},
                new Window(25, 50, 2, 0.6f)));
        SCENARIOS.add(new Scenario("02_walk", 45, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> move(mc, true, false, false, false), new Window(20, 45, 6, 0.07f)));
        SCENARIOS.add(new Scenario("03_sprint", 45, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> move(mc, true, true, false, false), new Window(20, 45, 6, 0.05f)));
        SCENARIOS.add(new Scenario("04_sneak_walk", 60, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> move(mc, true, false, true, false), new Window(25, 60, 6, 0.15f)));
        SCENARIOS.add(new Scenario("05_sneak_idle", 50, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> move(mc, false, false, true, false), new Window(25, 50, 2, 0.4f)));
        // Two sprint jumps in a row: the leading leg must switch.
        SCENARIOS.add(new Scenario("06_sprint_jump_x2", 70, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> move(mc, true, true, false, (t >= 20 && t <= 21) || (t >= 42 && t <= 43)),
                new Window(21, 34, 5, 0.06f), new Window(43, 56, 5, 0.06f)));
        SCENARIOS.add(new Scenario("07_fall_short", 60, sp -> place(sp, 2, Y + 12, 0, -90, ItemStack.EMPTY),
                (mc, t) -> move(mc, false, false, false, false), new Window(8, 45, 6, 0.12f)));
        SCENARIOS.add(new Scenario("07b_skydive", 110, sp -> place(sp, 2, Y + 70, 0, -90, ItemStack.EMPTY),
                (mc, t) -> move(mc, false, false, false, false), new Window(30, 85, 5, 0.3f)));
        SCENARIOS.add(new Scenario("08a_slash_1", 25, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.DIAMOND_SWORD)),
                (mc, t) -> { if (t == 10) mc.player.swing(InteractionHand.MAIN_HAND); }, new Window(10, 25, 6, 0.03f)));
        SCENARIOS.add(new Scenario("08b_slash_2", 16, sp -> {},
                (mc, t) -> { if (t == 4) mc.player.swing(InteractionHand.MAIN_HAND); }, new Window(4, 16, 6, 0.03f)));
        SCENARIOS.add(new Scenario("08c_slash_3", 22, sp -> {},
                (mc, t) -> { if (t == 4) mc.player.swing(InteractionHand.MAIN_HAND); }, new Window(4, 20, 6, 0.03f)));
        SCENARIOS.add(new Scenario("10_punches", 50, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> { if (t == 8 || t == 16 || t == 24 || t == 32) mc.player.swing(InteractionHand.MAIN_HAND); },
                new Window(8, 40, 10, 0.04f)));
        SCENARIOS.add(new Scenario("15_pickaxe", 45, sp -> placeLook(sp, 2, Y, 0, -90, 55, new ItemStack(Items.IRON_PICKAXE)),
                (mc, t) -> { if (t >= 8 && t % 3 == 0) mc.player.swing(InteractionHand.MAIN_HAND); }, new Window(10, 40, 8, 0.05f)));
        SCENARIOS.add(new Scenario("16_shovel", 45, sp -> placeLook(sp, 2, Y, 0, -90, 60, new ItemStack(Items.IRON_SHOVEL)),
                (mc, t) -> { if (t >= 8 && t % 3 == 0) mc.player.swing(InteractionHand.MAIN_HAND); }, new Window(10, 40, 8, 0.05f)));
        SCENARIOS.add(new Scenario("17_hoe", 40, sp -> placeLook(sp, 2, Y, 0, -90, 65, new ItemStack(Items.IRON_HOE)),
                (mc, t) -> { if (t >= 8 && t % 3 == 0) mc.player.swing(InteractionHand.MAIN_HAND); }, new Window(10, 36, 6, 0.05f)));
        SCENARIOS.add(new Scenario("11_swim", 70, sp -> place(sp, POOL_X + 2, Y - 3, 0, -90, ItemStack.EMPTY),
                (mc, t) -> move(mc, t > 5, t > 5, false, false), new Window(40, 70, 6, 0.1f)));
        SCENARIOS.add(new Scenario("13_ladder", 55, sp -> place(sp, LADDER_X + 1, Y, 0, 90, ItemStack.EMPTY),
                (mc, t) -> move(mc, true, false, false, false), new Window(15, 55, 6, 0.15f)));
        SCENARIOS.add(new Scenario("18_boat", 70, SelfTest::boardBoat,
                (mc, t) -> move(mc, t > 15, false, false, false), new Window(35, 70, 6, 0.12f)));
        SCENARIOS.add(new Scenario("19_horse", 80, SelfTest::mountHorse,
                (mc, t) -> move(mc, t > 15, t > 15, false, false), new Window(35, 80, 6, 0.12f)));
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
            McReloadAnim.LOG.info("[mcreload] self-test {} t={} fps={} pos=({}, {}, {}) ground={} sprint={} speed={} sprintW={} airW={} walkSpeed={}",
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
        sp.setItemInHand(InteractionHand.MAIN_HAND, hand.copy());
        sp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        sp.getAbilities().flying = false;
        sp.onUpdateAbilities();
        sp.setDeltaMovement(0, 0, 0);
        sp.teleportTo(sp.serverLevel(), x + 0.5, y, z + 0.5, yaw, 0f);
        sp.setYHeadRot(yaw);
    }

    private static void move(Minecraft mc, boolean forward, boolean sprint, boolean sneak, boolean jump) {
        INPUT.forward = forward;
        INPUT.sneak = sneak;
        INPUT.jump = jump;
        if (mc.player != null) mc.player.setSprinting(sprint && forward);
    }

    private SelfTest() {}
}
