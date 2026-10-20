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
        if (timer > 20 * 60 * 6 && phase != Phase.RUNNING) {
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

    private record Scenario(String name, int length, Setup setup, Driver driver, int[] shots) {}

    @FunctionalInterface
    private interface Setup { void run(ServerPlayer sp); }

    @FunctionalInterface
    private interface Driver { void tick(Minecraft mc, int t); }

    private static void buildScenarios() {
        int[] cycle = {20, 22, 24, 26, 28, 30};
        SCENARIOS.add(new Scenario("01_idle", 50, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY), (mc, t) -> {}, new int[]{30, 45}));
        SCENARIOS.add(new Scenario("02_walk", 40, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> keys(mc, true, false, false, false), cycle));
        SCENARIOS.add(new Scenario("03_sprint", 40, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> keys(mc, true, true, false, false), cycle));
        SCENARIOS.add(new Scenario("04_sneak_walk", 50, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> keys(mc, true, false, true, false), new int[]{24, 28, 32, 36, 40}));
        SCENARIOS.add(new Scenario("05_sneak_idle", 60, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> keys(mc, false, false, true, false), new int[]{30, 45, 58}));
        SCENARIOS.add(new Scenario("06_sprint_jump", 45, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> keys(mc, true, true, false, t >= 20 && t <= 21), new int[]{19, 22, 25, 28, 31, 34}));
        SCENARIOS.add(new Scenario("07_fall_and_land", 70, sp -> place(sp, 2, Y + 18, 0, -90, ItemStack.EMPTY),
                (mc, t) -> {}, new int[]{12, 20, 26, 30, 34, 40}));
        SCENARIOS.add(new Scenario("08_sword_combo", 60, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.DIAMOND_SWORD)),
                (mc, t) -> { if (t == 10 || t == 22 || t == 34) mc.player.swing(InteractionHand.MAIN_HAND); },
                new int[]{12, 13, 14, 15, 16, 24, 25, 26, 27, 36, 37, 38, 39, 42}));
        SCENARIOS.add(new Scenario("09_sword_run", 40, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.IRON_SWORD)),
                (mc, t) -> { keys(mc, true, true, false, false); if (t == 20) mc.player.swing(InteractionHand.MAIN_HAND); },
                new int[]{22, 24, 26}));
        SCENARIOS.add(new Scenario("10_punch", 30, sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                (mc, t) -> { if (t == 10) mc.player.swing(InteractionHand.MAIN_HAND); }, new int[]{12, 14, 16, 20}));
        SCENARIOS.add(new Scenario("11_swim", 60, sp -> place(sp, POOL_X + 2, Y - 3, 0, -90, ItemStack.EMPTY),
                (mc, t) -> keys(mc, t > 5, t > 5, false, false), new int[]{40, 43, 46, 49, 52, 55}));
        SCENARIOS.add(new Scenario("12_tread_water", 45, sp -> place(sp, POOL_X + 11, Y - 1, 0, -90, ItemStack.EMPTY),
                (mc, t) -> {}, new int[]{30, 36, 42}));
        SCENARIOS.add(new Scenario("13_ladder", 50, sp -> place(sp, LADDER_X + 1, Y, 0, 90, ItemStack.EMPTY),
                (mc, t) -> keys(mc, true, false, false, false), new int[]{25, 30, 35, 40}));
        SCENARIOS.add(new Scenario("14_bow", 50, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.BOW)),
                (mc, t) -> mc.options.keyUse.setDown(t > 5), new int[]{15, 30}));
    }

    private static void run(Minecraft mc) {
        if (scenarioIndex >= SCENARIOS.size()) {
            keys(mc, false, false, false, false);
            mc.options.keyUse.setDown(false);
            phase = Phase.DONE;
            timer = 0;
            return;
        }
        Scenario s = SCENARIOS.get(scenarioIndex);
        currentLabel = s.name();
        if (scenarioTick == 0) {
            keys(mc, false, false, false, false);
            mc.options.keyUse.setDown(false);
            IntegratedServer srv = mc.getSingleplayerServer();
            srv.execute(() -> s.setup().run(srv.getPlayerList().getPlayers().get(0)));
        } else if (scenarioTick > 4) {
            s.driver().tick(mc, scenarioTick);
        }
        for (int shot : s.shots()) {
            if (shot == scenarioTick) {
                String file = s.name() + "_t" + String.format("%03d", scenarioTick) + ".png";
                Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), msg -> {});
            }
        }
        scenarioTick++;
        if (scenarioTick > s.length()) {
            scenarioIndex++;
            scenarioTick = 0;
        }
    }

    private static void place(ServerPlayer sp, double x, double y, double z, float yaw, ItemStack hand) {
        sp.setItemInHand(InteractionHand.MAIN_HAND, hand.copy());
        sp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        sp.getAbilities().flying = false;
        sp.onUpdateAbilities();
        sp.setDeltaMovement(0, 0, 0);
        sp.teleportTo(sp.serverLevel(), x + 0.5, y, z + 0.5, yaw, 0f);
        sp.setYHeadRot(yaw);
    }

    private static void keys(Minecraft mc, boolean forward, boolean sprint, boolean sneak, boolean jump) {
        mc.options.keyUp.setDown(forward);
        mc.options.keySprint.setDown(sprint);
        mc.options.keyShift.setDown(sneak);
        mc.options.keyJump.setDown(jump);
        if (mc.player != null && sprint && forward) mc.player.setSprinting(true);
    }

    private SelfTest() {}
}
