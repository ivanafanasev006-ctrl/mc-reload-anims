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
        SCENARIOS.add(new Scenario("02_walk", 40, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> move(mc, true, false, false, false), new Window(20, 40, 4, 0.08f)));
        SCENARIOS.add(new Scenario("03_sprint", 40, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> move(mc, true, true, false, false), new Window(20, 40, 4, 0.06f)));
        SCENARIOS.add(new Scenario("03b_sprint_brake", 45, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> move(mc, t < 25, t < 25, false, false), new Window(25, 40, 4, 0.08f)));
        SCENARIOS.add(new Scenario("04_ninja_sneak", 55, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> move(mc, true, false, true, false), new Window(25, 55, 4, 0.15f)));
        SCENARIOS.add(new Scenario("06_sprint_jump_x2", 70, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> move(mc, true, true, false, (t >= 20 && t <= 21) || (t >= 42 && t <= 43)),
                new Window(21, 34, 3, 0.08f), new Window(43, 56, 3, 0.08f)));
        SCENARIOS.add(new Scenario("07_high_fall_roll", 60, sp -> place(sp, 2, Y + 7, 0, -90, ItemStack.EMPTY),
                (mc, t) -> move(mc, t > 5, false, false, false), new Window(22, 45, 6, 0.06f)));
        SCENARIOS.add(new Scenario("07c_sword_landing", 60, sp -> place(sp, 2, Y + 7, 0, -90, new ItemStack(Items.IRON_SWORD)),
                (mc, t) -> move(mc, false, false, false, false), new Window(22, 45, 5, 0.08f)));
        SCENARIOS.add(new Scenario("07b_skydive", 100, sp -> place(sp, 2, Y + 70, 0, -90, ItemStack.EMPTY),
                (mc, t) -> move(mc, false, false, false, false), new Window(50, 80, 2, 0.4f)));
        SCENARIOS.add(new Scenario("20_elytra", 70, SelfTest::launchElytra,
                (mc, t) -> move(mc, false, false, false, false), new Window(25, 60, 4, 0.2f)));
        SCENARIOS.add(new Scenario("21_sword_stance", 35, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.DIAMOND_SWORD)),
                (mc, t) -> move(mc, false, false, false, false), new Window(25, 35, 1, 0.3f)));
        SCENARIOS.add(new Scenario("08a_slash_1", 25, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.DIAMOND_SWORD)),
                (mc, t) -> { if (t == 10) mc.player.swing(InteractionHand.MAIN_HAND); }, new Window(10, 25, 5, 0.03f)));
        SCENARIOS.add(new Scenario("08b_slash_2", 16, sp -> {},
                (mc, t) -> { if (t == 4) mc.player.swing(InteractionHand.MAIN_HAND); }, new Window(4, 16, 4, 0.03f)));
        SCENARIOS.add(new Scenario("08c_slash_3", 16, sp -> {},
                (mc, t) -> { if (t == 4) mc.player.swing(InteractionHand.MAIN_HAND); }, new Window(4, 16, 4, 0.03f)));
        SCENARIOS.add(new Scenario("08d_spin", 25, sp -> {},
                (mc, t) -> { if (t == 4) mc.player.swing(InteractionHand.MAIN_HAND); }, new Window(4, 22, 6, 0.03f)));
        SCENARIOS.add(new Scenario("09_axe", 50, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.IRON_AXE)),
                (mc, t) -> { if (t == 8 || t == 30) mc.player.swing(InteractionHand.MAIN_HAND); },
                new Window(8, 26, 4, 0.05f), new Window(30, 48, 4, 0.05f)));
        SCENARIOS.add(new Scenario("10_punches", 40, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> { if (t == 8 || t == 16 || t == 24) mc.player.swing(InteractionHand.MAIN_HAND); },
                new Window(8, 32, 6, 0.05f)));
        SCENARIOS.add(new Scenario("10b_fists_on_block", 40, sp -> placeLook(sp, 2, Y, 0, -90, 60, ItemStack.EMPTY),
                (mc, t) -> { if (t >= 8 && t % 3 == 0) mc.player.swing(InteractionHand.MAIN_HAND); }, new Window(10, 36, 5, 0.05f)));
        SCENARIOS.add(new Scenario("22_throw", 40, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.COBBLESTONE, 16)),
                (mc, t) -> {
                    boolean throwNow = t == 8 || t == 22;
                    mc.options.keyDrop.setDown(throwNow);
                    if (throwNow) mc.player.swing(InteractionHand.MAIN_HAND);
                }, new Window(8, 18, 3, 0.04f), new Window(22, 32, 3, 0.04f)));
        SCENARIOS.add(new Scenario("15_pickaxe", 35, sp -> placeLook(sp, 2, Y, 0, -90, 55, new ItemStack(Items.IRON_PICKAXE)),
                (mc, t) -> { if (t >= 8 && t % 3 == 0) mc.player.swing(InteractionHand.MAIN_HAND); }, new Window(10, 33, 4, 0.06f)));
        SCENARIOS.add(new Scenario("14_bow", 50, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.BOW)),
                (mc, t) -> mc.options.keyUse.setDown((t > 5 && t < 28) || t > 32),
                new Window(18, 27, 2, 0.3f), new Window(42, 50, 2, 0.3f)));
        SCENARIOS.add(new Scenario("23_crossbow_carry", 25, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.CROSSBOW)),
                (mc, t) -> mc.options.keyUse.setDown(false), new Window(18, 25, 1, 0.3f)));
        SCENARIOS.add(new Scenario("23b_crossbow_load_aim", 60, sp -> {},
                (mc, t) -> mc.options.keyUse.setDown(t > 4 && t < 40), new Window(15, 35, 2, 0.4f), new Window(48, 60, 1, 0.3f)));
        SCENARIOS.add(new Scenario("24_shield", 40, SelfTest::shieldUp,
                (mc, t) -> mc.options.keyUse.setDown(t > 4), new Window(20, 40, 2, 0.4f)));
        SCENARIOS.add(new Scenario("25_eat", 45, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.GOLDEN_APPLE, 8)),
                (mc, t) -> mc.options.keyUse.setDown(t > 5), new Window(15, 40, 3, 0.25f)));
        SCENARIOS.add(new Scenario("26_explosion", 50, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> { if (t == 10) explode(mc, 6.5, 0.5); }, new Window(11, 30, 4, 0.08f)));
        SCENARIOS.add(new Scenario("26b_explosion_sword", 50, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.IRON_SWORD)),
                (mc, t) -> { if (t == 10) explode(mc, 6.5, 0.5); }, new Window(11, 30, 3, 0.1f)));
        SCENARIOS.add(new Scenario("27_totem", 60, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> { if (t == 8) { var r = dev.mcreload.anim.client.rig.RigRegistry.get(mc.player); if (r != null) r.motion.onTotem(); } },
                new Window(14, 50, 4, 0.25f)));
        SCENARIOS.add(new Scenario("28_sleep", 70, SelfTest::goToBed,
                (mc, t) -> {}, new Window(40, 70, 2, 0.5f)));
        SCENARIOS.add(new Scenario("11_swim", 60, sp -> place(sp, POOL_X + 2, Y - 3, 0, -90, ItemStack.EMPTY),
                (mc, t) -> move(mc, t > 5, t > 5, false, false), new Window(40, 60, 3, 0.15f)));
        SCENARIOS.add(new Scenario("13_ladder", 55, sp -> place(sp, LADDER_X + 1, Y, 0, 90, ItemStack.EMPTY),
                (mc, t) -> move(mc, true, false, false, false), new Window(15, 55, 4, 0.2f)));
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
        sp.setDeltaMovement(1.0, 0, 0);
        sp.hurtMarked = true;
        sp.startFallFlying();
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
        sp.startSleepInBed(head).ifLeft(problem -> McReloadAnim.LOG.warn("[mcreload] self-test bed: {}", problem));
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

    private static void move(Minecraft mc, boolean forward, boolean sprint, boolean sneak, boolean jump) {
        INPUT.forward = forward;
        INPUT.sneak = sneak;
        INPUT.jump = jump;
        if (mc.player != null) mc.player.setSprinting(sprint && forward);
    }

    private SelfTest() {}
}
