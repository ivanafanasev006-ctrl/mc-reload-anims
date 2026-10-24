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
    private static int groundTicks;
    private static int windupShot = -1;

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(SelfTest::onTick);
        MinecraftForge.EVENT_BUS.addListener(SelfTest::onHud);
        MinecraftForge.EVENT_BUS.addListener(SelfTestOverlay::onRenderGui);
        buildScenarios();
        // -Dmcreload.selftest.only=<regex>: run just those scenarios (e.g. the compatibility run).
        String only = System.getProperty("mcreload.selftest.only", "");
        if (!only.isBlank()) {
            java.util.regex.Pattern pat = java.util.regex.Pattern.compile(only);
            SCENARIOS.removeIf(sc -> !pat.matcher(sc.name()).find());
            McReloadAnim.LOG.info("[kinetrix] self-test: {} scenarios match '{}'", SCENARIOS.size(), only);
        }
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
            // Start only once the server has actually built the test track (slow CI machines).
            if (timer > 60 && worldReady) {
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
            // A long straight railway for the minecart.
            for (int x = -10; x <= 60; x++) lvl.setBlock(new BlockPos(x, Y, 7), Blocks.RAIL.defaultBlockState(), 2);
            worldReady = true;
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
    /** Full-screen world view (no side/front/top panels) for the current scenario. */
    static volatile boolean noPanels;
    /** Shots are taken by an open screen once it has drawn itself (the gallery), not by the HUD. */
    static volatile boolean captureFromScreen;

    /** Called by our screens at the end of drawing. */
    public static void screenRendered(Minecraft mc) {
        if (captureFromScreen) onFrameRendered(mc);
    }
    private static volatile boolean worldReady;
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
        // A sword taken in hand: switched to at t=8, it spins once round the forearm in front.
        SCENARIOS.add(new Scenario("08f_sword_draw", 50, sp -> place(sp, 2, Y, 0, -90, none),
                (mc, t) -> { if (t == 8) giveMain(mc, new ItemStack(Items.DIAMOND_SWORD)); },
                new Window(9, 24, 6, 0.0f), new Window(30, 50, 2, 0.3f)));
        // The draw held still at points along it (CI renders too few frames to catch the turn; the
        // motion clock is the player's ticks plus the partial tick, so this is within half a tick):
        // point back, the half turn out along the raised arm, the ordinary grip, tipped into the guard.
        float[] drawPhases = {0.06f, 0.22f, 0.30f, 0.36f, 0.44f, 0.56f, 0.66f, 0.80f};
        Window[] drawHolds = new Window[drawPhases.length];
        for (int i = 0; i < drawPhases.length; i++) drawHolds[i] = new Window(20 + 10 * i + 4, 20 + 10 * i + 9, 1, 0f);
        SCENARIOS.add(new Scenario("08j_sword_draw_phases", 20 + 10 * drawPhases.length, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.DIAMOND_SWORD)),
                (mc, t) -> {
                    int i = (t - 20) / 10;
                    var m = localMotion(mc);
                    if (m != null && t >= 20 && i < drawPhases.length)
                        m.drawStart = (mc.player.tickCount + 0.5f) / 20f - drawPhases[i] * dev.mcreload.anim.client.rig.MotionState.DRAW_SECONDS;
                }, drawHolds));
        // A sword put into the other hand (at t=8, the main hand already holding one): it spins there.
        SCENARIOS.add(new Scenario("08k_off_hand_draw", 30, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.IRON_SWORD)),
                (mc, t) -> { if (t == 8) onServer(mc, sp -> sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND_SWORD))); },
                new Window(9, 26, 6, 0.0f)));
        // Sprinting with a sword, then with one in each hand: the sword arms held out to the side
        // and back, the blades toward the ground.
        SCENARIOS.add(new Scenario("02s_sword_sprint", 40, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.DIAMOND_SWORD)),
                (mc, t) -> move(mc, t < 38, t < 38, false, false), new Window(18, 36, 4, 0.08f)));
        // A jump on the run: the swords stay held out through it.
        SCENARIOS.add(new Scenario("02u_sword_sprint_jump", 45, sp -> { place(sp, 2, Y, 0, -90, new ItemStack(Items.DIAMOND_SWORD)); sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD)); },
                (mc, t) -> move(mc, t < 43, t < 43, false, t >= 20 && t < 22), new Window(16, 19, 1, 0f), new Window(21, 34, 4, 0.05f)));
        SCENARIOS.add(new Scenario("02t_dual_sword_sprint", 40, sp -> { place(sp, 2, Y, 0, -90, new ItemStack(Items.DIAMOND_SWORD)); sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD)); },
                (mc, t) -> move(mc, t < 38, t < 38, false, false), new Window(18, 36, 4, 0.08f)));
        // Menus: thinking in the inventory and behind the pause menu (the game is paused there:
        // the animation clock runs on), rummaging in a chest, a tap and a talk with a villager.
        SCENARIOS.add(new Scenario("77a_screen_inventory", 50, sp -> place(sp, 2, Y, 0, -90, none),
                (mc, t) -> {
                    if (t == 10) mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                    if (t == 50) mc.setScreen(null);
                }, new Window(18, 48, 3, 0.3f)));
        SCENARIOS.add(new Scenario("77b_screen_pause", 50, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.IRON_SWORD)),
                (mc, t) -> {
                    if (t == 10) mc.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));
                    if (t == 50) mc.setScreen(null);
                }, new Window(18, 48, 3, 0.3f)));
        SCENARIOS.add(new Scenario("77c_screen_chest", 60, sp -> { place(sp, 2, Y, 0, -90, none); sp.serverLevel().setBlock(new BlockPos(4, Y, 0), Blocks.CHEST.defaultBlockState(), 3); },
                (mc, t) -> {
                    BlockPos chest = new BlockPos(4, Y, 0);
                    if (t == 10) mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(chest), Direction.WEST, chest, false));
                    if (t == 58) mc.player.closeContainer();
                    if (t == 60) onServer(mc, sp -> sp.serverLevel().setBlock(chest, Blocks.AIR.defaultBlockState(), 3));
                }, new Window(18, 56, 4, 0.25f)));
        SCENARIOS.add(new Scenario("77d_screen_trade", 150, SelfTest::villagerToTrade,
                (mc, t) -> {
                    if (t == 10) {
                        var v = mc.level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, mc.player.getBoundingBox().inflate(6));
                        if (!v.isEmpty()) mc.gameMode.interact(mc.player, v.get(0), InteractionHand.MAIN_HAND);
                    }
                    if (t == 148) mc.player.closeContainer();
                    if (t == 150) onServer(mc, sp -> sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,
                            sp.getBoundingBox().inflate(8)).forEach(net.minecraft.world.entity.Entity::discard));
                }, new Window(11, 30, 4, 0.12f), new Window(34, 146, 8, 0.6f)));
        // Dozing off standing after a minute of nothing (here forced, with a sword in hand): the
        // fidget that was playing stops, the head nods, Z z z fly up one at a time.
        SCENARIOS.add(new Scenario("78d_doze", 80, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.IRON_SWORD)),
                (mc, t) -> {
                    var m = localMotion(mc);
                    if (m == null) return;
                    if (t == 8) m.forceIdle(dev.mcreload.anim.client.rig.MotionState.IDLE_ITEM_SPIN);
                    if (t == 14) m.forceDoze();
                }, new Window(12, 13, 1, 0f), new Window(30, 78, 6, 0.3f)));
        // Sleeping: each of the three ways (a new one is picked every time you lie down).
        String[] sleeps = {"starfish", "face_down", "hands_behind"};
        for (int k = 0; k < sleeps.length; k++) {
            final int style = k;
            SCENARIOS.add(new Scenario("78" + (char) ('a' + k) + "_sleep_" + sleeps[k], 60, SelfTest::goToBed,
                    (mc, t) -> {
                        var m = localMotion(mc);
                        if (m != null && t >= 6) m.sleepStyle = style;
                    }, new Window(30, 58, 2, 0.4f)));
        }
        // The animation gallery, opened with the command: a few entries from different categories
        // on the preview dummy.
        String[][] galleryShots = {{"sword_draw", "8"}, {"sprint", "40"}, {"sleep_face_down", "65"}, {"sleep_hands_behind", "90"},
                {"sit_back", "115"}, {"doze", "140"}, {"dual_run", "170"}, {"swim", "205"}};
        Window[] galleryWindows = new Window[galleryShots.length];
        for (int i = 0; i < galleryShots.length; i++) {
            int at = Integer.parseInt(galleryShots[i][1]);
            galleryWindows[i] = new Window(at + 8, at + 22, 2, 0.25f);
        }
        SCENARIOS.add(new Scenario("79_gallery", 236, sp -> place(sp, 2, Y, 0, -90, none),
                (mc, t) -> {
                    if (t == 4) {
                        captureFromScreen = true;
                        net.minecraftforge.client.ClientCommandHandler.runCommand("kinetrix menu");
                    }
                    if (t == 7 && !(mc.screen instanceof dev.mcreload.anim.client.gallery.GalleryScreen)) {
                        McReloadAnim.LOG.warn("[kinetrix] self-test: /kinetrix menu did not open the gallery");
                        mc.setScreen(new dev.mcreload.anim.client.gallery.GalleryScreen(null));
                    }
                    for (String[] shot : galleryShots) {
                        if (t == Integer.parseInt(shot[1]) && mc.screen instanceof dev.mcreload.anim.client.gallery.GalleryScreen gs) {
                            gs.select(dev.mcreload.anim.client.gallery.Demos.byId(shot[0]));
                        }
                    }
                    if (t == 236) {
                        captureFromScreen = false;
                        mc.setScreen(null);
                    }
                }, galleryWindows));
        // One swing at the air in a jump, then walking: the guard is held on the move, then fades.
        SCENARIOS.add(new Scenario("08g_sword_guard_walk", 110, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.IRON_SWORD)),
                (mc, t) -> {
                    move(mc, t >= 74 && t < 84, false, false, t >= 74 && t < 76);
                    if (t == 77) mc.player.swing(InteractionHand.MAIN_HAND);
                    if (t >= 86 && t < 104) move(mc, true, false, false, false);
                }, new Window(74, 84, 3, 0.05f), new Window(88, 104, 4, 0.1f)));
        // Sword and shield: a swing, the guard with the shield across in front, the shield raised
        // from the guard (the sword stays ready), another swing with the shield kept in front.
        SCENARIOS.add(new Scenario("08h_sword_shield_guard", 60, sp -> { place(sp, 2, Y, 0, -90, new ItemStack(Items.IRON_SWORD)); sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); },
                (mc, t) -> {
                    if (t == 8 || t == 44) mc.player.swing(InteractionHand.MAIN_HAND);
                    mc.options.keyUse.setDown(t >= 24 && t < 38);
                }, new Window(10, 22, 2, 0.2f), new Window(28, 38, 2, 0.2f), new Window(44, 56, 3, 0.05f)));
        // Attacking the air with the attack key (as a player clicks; with Better Combat it plays its
        // own attack): the guard comes up after each attack, standing and walking.
        SCENARIOS.add(new Scenario("08i_sword_guard_click", 90, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.IRON_SWORD)),
                (mc, t) -> {
                    if (t == 66 || t == 78) net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey());
                    mc.options.keyAttack.setDown((t >= 66 && t < 68) || (t >= 78 && t < 80));
                    move(mc, t >= 82 && t < 90, false, false, false);
                }, new Window(66, 76, 3, 0.05f), new Window(80, 90, 3, 0.1f)));
        SCENARIOS.add(new Scenario("09_axe", 40, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.IRON_AXE)),
                (mc, t) -> { if (t == 8) mc.player.swing(InteractionHand.MAIN_HAND); },
                new Window(8, 26, 4, 0.05f), new Window(32, 40, 1, 0.3f)));
        SCENARIOS.add(new Scenario("10b_hand_digging", 40, sp -> placeLook(sp, 2, Y, 0, -90, 60, none),
                (mc, t) -> { if (t >= 8 && t % 3 == 0) mc.player.swing(InteractionHand.MAIN_HAND); }, new Window(10, 36, 6, 0.05f)));
        SCENARIOS.add(new Scenario("10c_shovel", 36, sp -> placeLook(sp, 2, Y, 0, -90, 60, new ItemStack(Items.IRON_SHOVEL)),
                (mc, t) -> { if (t >= 6 && t % 3 == 0) mc.player.swing(InteractionHand.MAIN_HAND); }, new Window(10, 34, 6, 0.05f)));
        SCENARIOS.add(new Scenario("10d_dig_shield", 36, sp -> { placeLook(sp, 2, Y, 0, -90, 60, none); sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); },
                (mc, t) -> { if (t >= 6 && t % 3 == 0) mc.player.swing(InteractionHand.MAIN_HAND); }, new Window(10, 34, 5, 0.05f)));
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
        SCENARIOS.add(new Scenario("29b_back_drop", 50, sp -> place(sp, 10, Y + 4, 3, -90, none),
                (mc, t) -> moveBack(mc, t < 40), new Window(2, 30, 9, 0.02f)));
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
        SCENARIOS.add(new Scenario("24b_shield_sneak", 40, sp -> { place(sp, 2, Y, 0, -90, new ItemStack(Items.IRON_SWORD)); sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); },
                (mc, t) -> { mc.options.keyUse.setDown(t > 4); move(mc, t > 22, false, t > 8, false); },
                new Window(16, 22, 1, 0.2f), new Window(26, 40, 2, 0.2f)));
        SCENARIOS.add(new Scenario("14b_bow_turn", 50, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.BOW)),
                (mc, t) -> {
                    mc.options.keyUse.setDown(t > 5);
                    // Swing the view round while drawing: the head turns ahead of the body.
                    if (t > 12 && t < 40) mc.player.setYRot(mc.player.getYRot() + (t < 26 ? 4f : -4f));
                }, new Window(18, 40, 4, 0.2f)));
        SCENARIOS.add(new Scenario("33_trident_throw", 50, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.TRIDENT)),
                (mc, t) -> mc.options.keyUse.setDown(t > 5 && t < 30), new Window(14, 29, 2, 0.2f), new Window(30, 42, 4, 0.03f)));
        SCENARIOS.add(new Scenario("34_trident_stab", 30, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.TRIDENT)),
                (mc, t) -> { if (t == 8) mc.player.swing(InteractionHand.MAIN_HAND); }, new Window(8, 24, 6, 0.0f)));
        SCENARIOS.add(new Scenario("25_eat", 40, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.GOLDEN_APPLE, 8)),
                (mc, t) -> mc.options.keyUse.setDown(t > 5), new Window(15, 38, 3, 0.25f)));
        SCENARIOS.add(new Scenario("07c_sword_landing", 60, sp -> place(sp, 2, Y + 7, 0, -90, new ItemStack(Items.IRON_SWORD)),
                (mc, t) -> move(mc, false, false, false, false), new Window(8, 18, 5, 0.0f), new Window(19, 45, 5, 0.06f)));
        SCENARIOS.add(new Scenario("07d_sword_landing_totem", 50, sp -> { place(sp, 2, Y + 7, 0, -90, new ItemStack(Items.IRON_SWORD)); sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING)); },
                (mc, t) -> move(mc, false, false, false, false), new Window(10, 40, 6, 0.06f)));
        // The same drop holding W: an ordinary fall and a forward roll, sword in hand or not.
        SCENARIOS.add(new Scenario("07e_sword_fall_forward", 55, sp -> place(sp, 2, Y + 7, 0, -90, new ItemStack(Items.IRON_SWORD)),
                (mc, t) -> move(mc, t < 45, false, false, false), new Window(8, 18, 3, 0.0f), new Window(19, 42, 6, 0.04f)));
        // Holding S (or A / D, or nothing): the sword goes up and is planted on landing.
        // Landing with the sword planted, then walking off at once: the kneel lets go within 0.15 s.
        SCENARIOS.add(new Scenario("07g_sword_land_walk_off", 50, sp -> { place(sp, 2, Y + 7, 0, -90, new ItemStack(Items.IRON_SWORD)); groundTicks = 0; },
                (mc, t) -> { groundTicks = mc.player.onGround() ? groundTicks + 1 : 0; move(mc, groundTicks >= 5 && t < 45, false, false, false); },
                new Window(8, 18, 3, 0.0f), new Window(19, 40, 7, 0.03f)));
        // A sword in each hand: both raised point down, wound up and driven into the ground.
        SCENARIOS.add(new Scenario("07h_dual_sword_landing", 50, sp -> { place(sp, 2, Y + 7, 0, -90, new ItemStack(Items.IRON_SWORD)); sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND_SWORD)); },
                (mc, t) -> move(mc, false, false, false, false), new Window(6, 18, 4, 0.0f), new Window(19, 40, 5, 0.04f)));
        SCENARIOS.add(new Scenario("07f_sword_fall_back", 60, sp -> place(sp, 2, Y + 7, 0, -90, new ItemStack(Items.IRON_SWORD)),
                (mc, t) -> moveBack(mc, t < 50), new Window(8, 18, 3, 0.0f), new Window(19, 45, 5, 0.06f)));
        SCENARIOS.add(new Scenario("26_explosion", 45, sp -> place(sp, 2, Y, 0, -90, none),
                (mc, t) -> { if (t == 10) { explode(mc, 4.0, 0.5); blastCue(mc); } }, new Window(11, 34, 4, 0.08f)));
        SCENARIOS.add(new Scenario("26b_explosion_sword", 45, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.IRON_SWORD)),
                (mc, t) -> { if (t == 10) { explode(mc, 4.0, 0.5); blastCue(mc); } }, new Window(11, 40, 7, 0.05f)));
        SCENARIOS.add(new Scenario("27_totem", 50, sp -> place(sp, 2, Y, 0, -90, none),
                (mc, t) -> { if (t == 8) { var r = dev.mcreload.anim.client.rig.RigRegistry.get(mc.player); if (r != null) r.motion.onTotem(); } },
                new Window(14, 50, 3, 0.3f)));
        SCENARIOS.add(new Scenario("19_horse", 90, SelfTest::mountHorse,
                (mc, t) -> {
                    // walk, gallop, hold jump and release (the horse jumps), then get off.
                    move(mc, t > 8 && t < 66, t > 28 && t < 66, t >= 72 && t < 75, t >= 44 && t < 52);
                }, new Window(18, 27, 2, 0.15f), new Window(34, 44, 2, 0.1f), new Window(52, 66, 5, 0.02f),
                new Window(73, 90, 3, 0.05f)));
        // A big, wide horse (compat.wideMounts, as for DragN's Livestock Overhaul): the legs spread
        // round the barrel, the seat lower, the hands forward on the reins. Shown on a vanilla horse.
        SCENARIOS.add(new Scenario("19b_horse_wide", 70, SelfTest::mountHorse,
                (mc, t) -> {
                    if (t == 4) {
                        savedWideMounts = dev.mcreload.anim.config.Cfg.wideMounts;
                        dev.mcreload.anim.config.Cfg.wideMounts = java.util.Set.of("minecraft:horse");
                    }
                    move(mc, t > 30 && t < 62, t > 44 && t < 62, false, false);
                    if (t == 68 && savedWideMounts != null) dev.mcreload.anim.config.Cfg.wideMounts = savedWideMounts;
                }, new Window(14, 28, 2, 0.3f), new Window(34, 44, 2, 0.1f), new Window(50, 62, 3, 0.05f)));
        // Two on one horse: someone (a villager here) in the saddle, the player right-clicks the
        // horse and climbs on behind (TwoRiders), holds on to the rider, then sneaks to get off.
        SCENARIOS.add(new Scenario("19c_horse_two_riders", 60, SelfTest::mountBehind,
                (mc, t) -> move(mc, false, false, t >= 50 && t < 54, false),
                new Window(8, 46, 3, 0.3f), new Window(54, 60, 1, 0.1f)));
        SCENARIOS.add(new Scenario("11b_tread", 50, sp -> place(sp, POOL_X + 10, Y - 2, 0, -90, none),
                (mc, t) -> move(mc, false, false, false, t % 10 < 3), new Window(20, 50, 3, 0.3f)));
        SCENARIOS.add(new Scenario("11d_surface_swim", 60, sp -> place(sp, POOL_X + 3, Y - 2, 0, -90, none),
                (mc, t) -> move(mc, t > 5, false, false, t % 8 < 3), new Window(25, 60, 4, 0.2f)));
        SCENARIOS.add(new Scenario("11c_dive_high", 50, sp -> place(sp, POOL_X + 12, Y + 5, 0, -90, none),
                (mc, t) -> move(mc, false, false, false, false), new Window(5, 40, 8, 0.05f)));
        SCENARIOS.add(new Scenario("11f_chill_fall", 70, sp -> place(sp, POOL_X + 14, Y + 14, 0, 90, none),
                (mc, t) -> moveBack(mc, true), new Window(6, 60, 8, 0.12f)));
        SCENARIOS.add(new Scenario("11h_exit_water", 90, sp -> { place(sp, POOL_X + 8, Y - 3, 0, 90, none); sp.setXRot(-12f); },
                (mc, t) -> { move(mc, t > 3, t > 3, false, t > 10); mc.player.setXRot(-12f); },
                new Window(30, 90, 12, 0.05f)));
        SCENARIOS.add(new Scenario("11g_surfacing", 60, sp -> place(sp, POOL_X + 8, Y - 3, 0, -90, none),
                (mc, t) -> move(mc, false, false, false, true), new Window(10, 60, 6, 0.2f)));
        SCENARIOS.add(new Scenario("05_fist_guard", 40, sp -> place(sp, 2, Y, 0, -90, none),
                (mc, t) -> { if (t == 5 || t == 12) mc.player.swing(InteractionHand.MAIN_HAND); },
                new Window(22, 40, 2, 0.3f)));
        // Playing with the item in hand (fidgets forced on).
        int[][] fidgets = {
                {dev.mcreload.anim.client.rig.MotionState.IDLE_ITEM_SPIN, 0},
                {dev.mcreload.anim.client.rig.MotionState.IDLE_ITEM_INSPECT, 0},
                {dev.mcreload.anim.client.rig.MotionState.IDLE_ITEM_TOSS, 1},
                {dev.mcreload.anim.client.rig.MotionState.IDLE_ITEM_SHOULDER, 2},
                {dev.mcreload.anim.client.rig.MotionState.IDLE_ITEM_LEAN, 3},
                {dev.mcreload.anim.client.rig.MotionState.IDLE_LANTERN, 4},
                {dev.mcreload.anim.client.rig.MotionState.IDLE_BLOCK, 5},
                {dev.mcreload.anim.client.rig.MotionState.IDLE_BUCKET, 6},
        };
        net.minecraft.world.item.Item[] fidgetItems = {Items.DIAMOND_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE,
                Items.IRON_SHOVEL, Items.LANTERN, Items.GRASS_BLOCK, Items.WATER_BUCKET};
        for (int[] fd : fidgets) {
            final int action = fd[0];
            final net.minecraft.world.item.Item item = fidgetItems[fd[1]];
            SCENARIOS.add(new Scenario("45_fidget_" + action, 75, sp -> place(sp, 2, Y, 0, -90, new ItemStack(item)),
                    (mc, t) -> {
                        move(mc, false, false, false, false);
                        if (t == 8) { var r = dev.mcreload.anim.client.rig.RigRegistry.get(mc.player); if (r != null) r.motion.forceIdle(action); }
                    }, new Window(16, 72, 4, 0.25f)));
        }
        // Riding a minecart down the line, then sitting about once it has stopped.
        SCENARIOS.add(new Scenario("47_minecart", 330, SelfTest::boardMinecart,
                (mc, t) -> move(mc, false, false, false, false),
                new Window(8, 60, 4, 0.2f), new Window(215, 330, 6, 0.5f)));
        SCENARIOS.add(new Scenario("48_boat_idle", 200, SelfTest::boardBoat,
                (mc, t) -> move(mc, false, false, false, false), new Window(60, 200, 6, 0.6f)));
        for (int i : new int[]{MotionState_EMOTE_WAVE, MotionState_EMOTE_FACEPALM, 0, 15, 16, 17, 18, 19}) {
            final int emote = i;
            SCENARIOS.add(new Scenario("40_emote_" + dev.mcreload.anim.client.rig.MotionState.EMOTE_KEYS[i], 60,
                    sp -> place(sp, 2, Y, 0, -90, ItemStack.EMPTY),
                    (mc, t) -> {
                        move(mc, false, false, false, false);
                        if (t == 16) { var r = dev.mcreload.anim.client.rig.RigRegistry.get(mc.player); if (r != null) r.motion.playEmote(emote); }
                    }, new Window(24, 58, 3, 0.35f)));
        }
        buildCompatScenarios();
    }

    private static void armor(ServerPlayer sp) {
        sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
    }

    private static dev.mcreload.anim.client.rig.MotionState localMotion(Minecraft mc) {
        var r = dev.mcreload.anim.client.rig.RigRegistry.get(mc.player);
        return r == null ? null : r.motion;
    }

    /** Armour, empty-hand fidgets (also with a torch in the other hand), Better Combat, mining, a friend. */
    private static void buildCompatScenarios() {
        ItemStack none = ItemStack.EMPTY;
        SCENARIOS.add(new Scenario("60_armor_walk", 64, sp -> { place(sp, 2, Y, 0, -90, none); armor(sp); },
                (mc, t) -> move(mc, t > 10 && t < 40, false, t >= 44, false),
                new Window(14, 38, 3, 0.15f), new Window(48, 64, 2, 0.2f)));
        SCENARIOS.add(new Scenario("61_armor_flex", 80, sp -> { place(sp, 2, Y, 0, -90, none); armor(sp); },
                (mc, t) -> {
                    move(mc, false, false, false, false);
                    if (t == 10) { var m = localMotion(mc); if (m != null) m.playEmote(dev.mcreload.anim.client.rig.MotionState.EMOTE_FLEX); }
                }, new Window(30, 80, 3, 0.5f)));
        SCENARIOS.add(new Scenario("62_attack_key", 44, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.DIAMOND_SWORD)),
                (mc, t) -> { if (t == 10 || t == 30) net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey()); },
                new Window(10, 50, 10, 0.2f)));
        // Swinging a stack of planks, then a chestplate, at the air: our own swat, never vanilla's swing.
        SCENARIOS.add(new Scenario("62b_item_swat", 50, sp -> place(sp, 2, Y, 0, -90, new ItemStack(Items.OAK_PLANKS, 16)),
                (mc, t) -> {
                    if (t == 8 || t == 34) net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey());
                    if (t == 24) {
                        IntegratedServer srv = mc.getSingleplayerServer();
                        srv.execute(() -> srv.getPlayerList().getPlayers().get(0)
                                .setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_CHESTPLATE)));
                    }
                }, new Window(8, 20, 5, 0.03f), new Window(34, 46, 5, 0.03f)));
        // Mining an obsidian block (survival, so it does not break) with the attack key held.
        SCENARIOS.add(new Scenario("63_pick_mining", 50, sp -> {
                    place(sp, 2, Y, 0, -90, new ItemStack(Items.IRON_PICKAXE));
                    sp.setGameMode(GameType.SURVIVAL);
                    sp.serverLevel().setBlock(new BlockPos(4, Y + 1, 0), Blocks.OBSIDIAN.defaultBlockState(), 3);
                    sp.serverLevel().setBlock(new BlockPos(4, Y, 0), Blocks.OBSIDIAN.defaultBlockState(), 3);
                    sp.teleportTo(sp.serverLevel(), 2.5, Y, 0.5, -90, 10f);
                },
                (mc, t) -> mc.options.keyAttack.setDown(t > 8 && t < 45), new Window(12, 45, 6, 0.25f)));
        // Empty-hand fidgets, then the same with a torch in the other hand.
        int[] idles = {dev.mcreload.anim.client.rig.MotionState.IDLE_STRETCH, dev.mcreload.anim.client.rig.MotionState.IDLE_SCRATCH,
                dev.mcreload.anim.client.rig.MotionState.IDLE_SHADOWBOX, dev.mcreload.anim.client.rig.MotionState.IDLE_YAWN,
                dev.mcreload.anim.client.rig.MotionState.IDLE_HANDS, dev.mcreload.anim.client.rig.MotionState.IDLE_SQUATS};
        for (int torch = 0; torch < 2; torch++) {
            for (int action : idles) {
                final int a = action;
                final boolean withTorch = torch == 1;
                SCENARIOS.add(new Scenario("64_idle" + (withTorch ? "_torch_" : "_") + action, 80,
                        sp -> { place(sp, 2, Y, 0, -90, none); if (withTorch) sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH)); },
                        (mc, t) -> {
                            move(mc, false, false, false, false);
                            if (t == 8) { var m = localMotion(mc); if (m != null) m.forceIdle(a); }
                        }, new Window(12, 76, 6, 0.55f)));
            }
        }
        SCENARIOS.add(new Scenario("65_friend", 300, sp -> place(sp, 2, Y, -1, -90, none),
                (mc, t) -> {
                    move(mc, false, false, false, false);
                    noPanels = true;
                    friend(mc, t);
                }, new Window(14, 40, 4, 0.12f), new Window(62, 88, 2, 0.3f), new Window(96, 126, 4, 0.12f),
                new Window(130, 148, 3, 0.05f), new Window(158, 185, 2, 0.4f), new Window(196, 240, 4, 0.4f),
                new Window(258, 290, 8, 0.03f)));
        // Crawl (key C held) under a one-block glass ceiling and out again, then stand up (let go of C).
        SCENARIOS.add(new Scenario("70_crawl", 125, sp -> {
                    place(sp, 1, Y, -6, -90, none);
                    for (int x = 3; x <= 4; x++) sp.serverLevel().setBlock(new BlockPos(x, Y + 1, -6), Blocks.GLASS.defaultBlockState(), 3);
                },
                (mc, t) -> {
                    dev.mcreload.anim.client.PostureControl.testHoldCrawl(t >= 6 && t < 104);
                    move(mc, t > 30 && t < 100, false, false, false);
                }, new Window(14, 30, 2, 0.3f), new Window(36, 98, 8, 0.12f), new Window(106, 125, 2, 0.3f)));
        // Sitting (key X) in each of the three ways, then standing up again by walking off.
        for (int style = 0; style < dev.mcreload.anim.net.Posture.SIT_POSES; style++) {
            final int st = style;
            SCENARIOS.add(new Scenario("71_sit_" + style, 70, sp -> place(sp, 2, Y, 0, -90, none),
                    (mc, t) -> {
                        move(mc, t >= 62, false, false, false);
                        if (t == 6) dev.mcreload.anim.client.PostureControl.testPressSit();
                        if (t >= 8 && t <= 20) { var m = localMotion(mc); if (m != null && m.sitting) m.sitStyle = st; }
                    }, new Window(6, 18, 3, 0.1f), new Window(24, 60, 2, 0.6f), new Window(62, 70, 2, 0.15f)));
        }
        // Two swords: drawn from behind the back into a wary crouch; twirled one after the other.
        for (int action : new int[]{dev.mcreload.anim.client.rig.MotionState.IDLE_DUAL_DRAW, dev.mcreload.anim.client.rig.MotionState.IDLE_DUAL_SPIN}) {
            final int a = action;
            // Started once the guard from getting the swords (a draw) has gone, as idling would.
            SCENARIOS.add(new Scenario("64b_idle_dual_" + action, 180,
                    sp -> { place(sp, 2, Y, 0, -90, new ItemStack(Items.IRON_SWORD)); sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND_SWORD)); },
                    (mc, t) -> {
                        move(mc, false, false, false, false);
                        if (t == 84) { var m = localMotion(mc); if (m != null) m.forceIdle(a); }
                    }, new Window(84, 180, 16, 0.2f)));
        }
        // The friend crawls past and sits down (as their client would tell us).
        SCENARIOS.add(new Scenario("72_friend_posture", 150, sp -> place(sp, 2, Y, -1, -90, none),
                (mc, t) -> {
                    move(mc, false, false, false, false);
                    noPanels = true;
                    friendPosture(mc, t);
                }, new Window(14, 60, 5, 0.25f), new Window(80, 140, 3, 0.5f)));
        // Iron's Spells casts (through its /cast command), each with a different arm animation of
        // Iron's: the arms must go where Iron's sends them (forward for a spray), whatever else
        // Kinetrix is doing (a sword stance, walking, a staff in hand).
        if (dev.mcreload.anim.compat.ModCompat.ironsSpells) {
            String[][] casts = {{"a", "cone_of_cold", "none"}, {"b", "firebolt", "none"}, {"c", "fireball", "none"},
                    {"d", "lightning_lance", "none"}, {"e", "heal", "none"}, {"f", "cone_of_cold", "sword"},
                    {"g", "cone_of_cold", "staff"}, {"h", "cone_of_cold", "walk"}, {"i", "electrocute", "turn"}};
            for (String[] c : casts) {
                String spell = c[1], how = c[2];
                SCENARIOS.add(new Scenario("98" + c[0] + "_irons_" + spell + (how.equals("none") ? "" : "_" + how), 80,
                        sp -> {
                            place(sp, 2, Y, 0, -90, how.equals("sword") ? new ItemStack(Items.DIAMOND_SWORD)
                                    : how.equals("staff") ? ironsItem("graybeard_staff") : ItemStack.EMPTY);
                            if (how.equals("staff")) sp.setItemInHand(InteractionHand.OFF_HAND, ironsItem("ice_staff"));
                        },
                        (mc, t) -> {
                            move(mc, how.equals("walk") && t > 6 && t < 70, false, false, false);
                            // Looking 60 degrees off to the side while casting: the arms follow the look.
                            if (how.equals("turn") && mc.player != null && t >= 20) {
                                float yaw = -90f + Math.min(60f, (t - 20) * 4f);
                                mc.player.setYRot(yaw);
                                mc.player.setYHeadRot(yaw);
                            }
                            if (t == 10) onServer(mc, sp -> sp.getServer().getCommands().performPrefixedCommand(
                                    sp.createCommandSourceStack().withPermission(4), "cast @s irons_spellbooks:" + spell + " 3"));
                        }, new Window(11, 22, 4, 0.1f), new Window(24, 78, 5, 0.4f)));
            }
        }
        // Last: hand the player model to Entity Model Features (Fresh Moves) to check nothing breaks.
        if (dev.mcreload.anim.compat.EmfCompat.LOADED) SCENARIOS.add(new Scenario("99_emf_player", 140, sp -> { place(sp, 2, Y, 0, -90, none); armor(sp); },
                (mc, t) -> {
                    move(mc, t > 90 && t < 120, false, false, false);
                    if (t == 6) {
                        dev.mcreload.anim.config.ClientConfig.PLAYER_OVER_EMF.set(false);
                        dev.mcreload.anim.config.Cfg.refresh();
                        mc.reloadResourcePacks();
                    }
                }, new Window(40, 88, 2, 0.5f), new Window(94, 120, 3, 0.15f), new Window(124, 140, 1, 0.3f)));
    }

    private static net.minecraftforge.common.util.FakePlayer friend;
    private static java.util.Set<String> savedWideMounts;

    /** A second player, driven from the server, with Kinetrix sync messages sent for it. */
    private static void friend(Minecraft mc, int t) {
        IntegratedServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        srv.execute(() -> {
            ServerPlayer me = srv.getPlayerList().getPlayers().get(0);
            ServerLevel lvl = me.serverLevel();
            if (t == 5) {
                com.mojang.authlib.GameProfile prof = new com.mojang.authlib.GameProfile(
                        java.util.UUID.fromString("0f6c4b32-7c2a-4d4e-9a77-1b1e5d0c0a01"), "Friend");
                friend = net.minecraftforge.common.util.FakePlayerFactory.get(lvl, prof);
                friend.moveTo(-6.5, Y, 1.5, -90f, 0f);
                friend.setYHeadRot(-90f);
                friend.yBodyRot = -90f;
                friend.setOnGround(true);
                srv.getPlayerList().broadcastAll(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
                        .createPlayerInitializing(java.util.List.of(friend)));
                lvl.addNewPlayer(friend);
                return;
            }
            net.minecraftforge.common.util.FakePlayer f = friend;
            if (f == null) return;
            if (t >= 10 && t < 40) {                       // sprints up past us
                f.setSprinting(true);
                step(f, 0.28, 0);
            } else if (t == 40) {
                f.setSprinting(false);
            } else if (t >= 60 && t < 90) {               // sneaks
                f.setShiftKeyDown(true);
                f.setPose(net.minecraft.world.entity.Pose.CROUCHING);
                if (t >= 70 && t < 80) step(f, 0.06, 0);
            } else if (t == 90) {
                f.setShiftKeyDown(false);
                f.setPose(net.minecraft.world.entity.Pose.STANDING);
                sync(me, f, dev.mcreload.anim.net.AnimPacket.STATE, dev.mcreload.anim.net.AnimPacket.BIT_TARGET_BLOCK);
            } else if (t > 95 && t < 126) {               // digs with bare hands (aiming at a block)
                if (t % 4 == 0) f.swing(InteractionHand.MAIN_HAND, true);
            } else if (t == 128) {
                sync(me, f, dev.mcreload.anim.net.AnimPacket.STATE, 0);
            } else if (t == 131 || t == 139) {           // punches someone
                f.swing(InteractionHand.MAIN_HAND, true);
            } else if (t == 152) {                        // eats
                f.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 4));
                showHand(lvl, f);
                f.startUsingItem(InteractionHand.MAIN_HAND);
            } else if (t == 186) {
                f.stopUsingItem();
                f.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                showHand(lvl, f);
            } else if (t == 190) {                        // emote chosen on their screen
                sync(me, f, dev.mcreload.anim.net.AnimPacket.EMOTE, dev.mcreload.anim.client.rig.MotionState.EMOTE_RUNNING_MAN);
            } else if (t == 244) {
                sync(me, f, dev.mcreload.anim.net.AnimPacket.EMOTE, -1);
                sync(me, f, dev.mcreload.anim.net.AnimPacket.STATE, dev.mcreload.anim.net.AnimPacket.BIT_FORWARD);
            } else if (t > 246 && t <= 258) {             // up onto a ledge...
                f.setOnGround(false);
                f.moveTo(f.getX() - 0.05, f.getY() + 0.42, f.getZ(), -90f, 0f);
            } else if (t > 258 && t <= 264) {             // ...and a hard drop, rolling out
                f.moveTo(f.getX() + 0.05, Math.max(Y, f.getY() - 0.85), f.getZ(), -90f, 0f);
                if (t == 263) sync(me, f, dev.mcreload.anim.net.AnimPacket.LAND, dev.mcreload.anim.client.rig.MotionState.LAND_ROLL);
                if (f.getY() <= Y + 1e-3) f.setOnGround(true);
            } else if (t == 296) {
                sync(me, f, dev.mcreload.anim.net.AnimPacket.STATE, 0);
                srv.getPlayerList().broadcastAll(new net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket(
                        java.util.List.of(f.getUUID())));
                lvl.removePlayerImmediately(f, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                friend = null;
            }
        });
    }

    /** The friend crawling along and then sitting down (style from their client). */
    private static void friendPosture(Minecraft mc, int t) {
        IntegratedServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        srv.execute(() -> {
            ServerPlayer me = srv.getPlayerList().getPlayers().get(0);
            ServerLevel lvl = me.serverLevel();
            if (t == 5) {
                com.mojang.authlib.GameProfile prof = new com.mojang.authlib.GameProfile(
                        java.util.UUID.fromString("0f6c4b32-7c2a-4d4e-9a77-1b1e5d0c0a02"), "Friend");
                friend = net.minecraftforge.common.util.FakePlayerFactory.get(lvl, prof);
                friend.moveTo(-5.5, Y, 1.5, -90f, 0f);
                friend.setYHeadRot(-90f);
                friend.yBodyRot = -90f;
                friend.setOnGround(true);
                friend.setPose(net.minecraft.world.entity.Pose.SWIMMING);
                srv.getPlayerList().broadcastAll(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
                        .createPlayerInitializing(java.util.List.of(friend)));
                lvl.addNewPlayer(friend);
                return;
            }
            net.minecraftforge.common.util.FakePlayer f = friend;
            if (f == null) return;
            if (t >= 10 && t < 60) {
                f.setPose(net.minecraft.world.entity.Pose.SWIMMING);
                if (t >= 20) step(f, 0.05, 0);
            } else if (t == 64) {
                f.setPose(net.minecraft.world.entity.Pose.STANDING);
            } else if (t == 72) {
                sync(me, f, dev.mcreload.anim.net.AnimPacket.SIT, 3);
            } else if (t == 144) {
                sync(me, f, dev.mcreload.anim.net.AnimPacket.SIT, 0);
                srv.getPlayerList().broadcastAll(new net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket(
                        java.util.List.of(f.getUUID())));
                lvl.removePlayerImmediately(f, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                friend = null;
            }
        });
    }

    /** Fake players never tick, so their held item has to be sent by hand. */
    private static void showHand(ServerLevel lvl, ServerPlayer f) {
        lvl.getChunkSource().broadcast(f, new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(f.getId(),
                java.util.List.of(com.mojang.datafixers.util.Pair.of(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                        f.getMainHandItem().copy()))));
    }

    private static void step(ServerPlayer f, double dx, double dz) {
        f.moveTo(f.getX() + dx, f.getY(), f.getZ() + dz, -90f, 0f);
        f.setYHeadRot(-90f);
        f.yBodyRot = -90f;
        f.setOnGround(true);
    }

    private static void sync(ServerPlayer to, ServerPlayer about, byte type, int value) {
        dev.mcreload.anim.net.KinetrixNet.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> to),
                new dev.mcreload.anim.net.AnimPacket(about.getId(), type, value));
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

    private static void boardMinecart(ServerPlayer sp) {
        sp.stopRiding();
        place(sp, 0, Y, 7, -90, ItemStack.EMPTY);
        net.minecraft.world.entity.vehicle.Minecart cart =
                new net.minecraft.world.entity.vehicle.Minecart(sp.serverLevel(), 0.5, Y, 7.5);
        sp.serverLevel().addFreshEntity(cart);
        sp.startRiding(cart, true);
        cart.setDeltaMovement(0.6, 0, 0);
    }

    private static void boardBoat(ServerPlayer sp) {
        sp.stopRiding();
        place(sp, POOL_X + 3, Y - 1, 0, -90, ItemStack.EMPTY);
        net.minecraft.world.entity.vehicle.Boat boat = new net.minecraft.world.entity.vehicle.Boat(sp.serverLevel(), POOL_X + 3.5, Y - 0.3, 0.5);
        boat.setYRot(-90);
        sp.serverLevel().addFreshEntity(boat);
        sp.startRiding(boat, true);
    }

    private static String f2(float v) {
        return String.format("%.2f", v);
    }

    /** An item of Iron's Spells by name (a stick if it is not there). */
    private static ItemStack ironsItem(String name) {
        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                new net.minecraft.resources.ResourceLocation("irons_spellbooks", name));
        return item == null || item == Items.AIR ? new ItemStack(Items.STICK) : new ItemStack(item);
    }

    /** Run something on the server with the test player. */
    private static void onServer(Minecraft mc, java.util.function.Consumer<ServerPlayer> action) {
        IntegratedServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        srv.execute(() -> action.accept(srv.getPlayerList().getPlayers().get(0)));
    }

    /** A farmer (so it has trades) standing still two blocks in front of the player. */
    private static void villagerToTrade(ServerPlayer sp) {
        place(sp, 2, Y, 0, -90, ItemStack.EMPTY);
        ServerLevel level = sp.serverLevel();
        level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, sp.getBoundingBox().inflate(8))
                .forEach(net.minecraft.world.entity.Entity::discard);
        net.minecraft.world.entity.npc.Villager v = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (v == null) return;
        v.setVillagerData(v.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER).setLevel(1));
        v.moveTo(4.5, Y, 0.5, 90, 0);
        v.setYHeadRot(90);
        v.setYBodyRot(90);
        v.setNoAi(true);
        level.addFreshEntity(v);
    }

    /** Put an item in the player's main hand on the server (as picking it in the hotbar would). */
    private static void giveMain(Minecraft mc, ItemStack stack) {
        IntegratedServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        srv.execute(() -> srv.getPlayerList().getPlayers().get(0).setItemInHand(InteractionHand.MAIN_HAND, stack.copy()));
    }

    private static void mountBehind(ServerPlayer sp) {
        sp.stopRiding();
        place(sp, 2, Y, 0, -90, ItemStack.EMPTY);
        net.minecraft.server.level.ServerLevel level = sp.serverLevel();
        net.minecraft.world.phys.AABB area = sp.getBoundingBox().inflate(8);
        for (var old : level.getEntitiesOfClass(net.minecraft.world.entity.animal.horse.AbstractHorse.class, area)) {
            old.ejectPassengers();
            old.discard();
        }
        level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, area).forEach(net.minecraft.world.entity.Entity::discard);
        net.minecraft.world.entity.animal.horse.Horse horse = net.minecraft.world.entity.EntityType.HORSE.create(level);
        net.minecraft.world.entity.npc.Villager rider = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (horse == null || rider == null) return;
        horse.moveTo(2.5, Y, 0.5, -90, 0);
        horse.setYBodyRot(-90);
        horse.setYHeadRot(-90);
        horse.setTamed(true);
        horse.equipSaddle(null);
        level.addFreshEntity(horse);
        rider.moveTo(2.5, Y, 0.5, -90, 0);
        rider.setNoAi(true);
        level.addFreshEntity(rider);
        rider.startRiding(horse, true);
        // As a right-click does it (Forge's entity-interact event first, then the horse).
        net.minecraft.world.InteractionResult r = sp.interactOn(horse, InteractionHand.MAIN_HAND);
        // A player always takes the front seat from a mob (vanilla Entity#addPassenger); between
        // two players the one who climbs on second sits behind. Put the villager in front here so
        // the player is the one behind, as with a friend in the saddle.
        if (horse.getFirstPassenger() == sp) {
            try {
                java.lang.reflect.Field f = net.minecraft.world.entity.Entity.class.getDeclaredField("passengers");
                f.setAccessible(true);
                f.set(horse, com.google.common.collect.ImmutableList.of(rider, sp));
                level.getChunkSource().broadcast(horse, new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(horse));
            } catch (ReflectiveOperationException e) {
                McReloadAnim.LOG.warn("[kinetrix] self-test: could not swap the riders", e);
            }
        }
        McReloadAnim.LOG.info("[kinetrix] self-test two riders: interact={} passengers={} behind={}", r,
                horse.getPassengers().size(), dev.mcreload.anim.ride.TwoRiders.isBehind(sp));
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
            mc.options.keyAttack.setDown(false);
            phase = Phase.DONE;
            timer = 0;
            return;
        }
        if (mc.player != null && mc.player.input != INPUT) mc.player.input = INPUT;
        // Drops with a sword: the weights behind the air pose, every tick in the air.
        if (mc.player != null && currentLabel != null && currentLabel.startsWith("07") && !mc.player.onGround()) {
            dev.mcreload.anim.client.rig.PlayerRig rig = dev.mcreload.anim.client.rig.RigRegistry.get(mc.player);
            if (rig != null) {
                var m = rig.motion;
                McReloadAnim.LOG.info("[kinetrix] self-test {} t={} air vy={} airW={} fallW={} bladeReadyW={} hardDropW={} impactIn={} blade={} armsFree={} toolSwingW={} gate={} tilt={}",
                        currentLabel, scenarioTick, String.format("%.3f", m.vertical), String.format("%.2f", m.airW.v),
                        String.format("%.2f", m.fallW.v), String.format("%.2f", m.bladeReadyW.v), String.format("%.2f", m.hardDropW.v), String.format("%.2f", m.impactIn), m.holdingBlade,
                        String.format("%.2f", m.armsFree.v), String.format("%.2f", m.toolSwingW.v),
                        String.format("%.2f", m.gate.v), String.format("%.2f", m.itemTilt.v));
            }
        }
        // Menus and beds: the arm and head angles as finally drawn (to compare with the box model).
        if (mc.player != null && currentLabel != null && (currentLabel.startsWith("77") || currentLabel.startsWith("78")) && scenarioTick % 10 == 5) {
            dev.mcreload.anim.client.rig.PlayerRig rig = dev.mcreload.anim.client.rig.RigRegistry.get(mc.player);
            if (rig != null) {
                float[] r = new float[3], l = new float[3], rb = new float[3], lb = new float[3], h = new float[3], rp = new float[3];
                rig.finalPose(dev.mcreload.anim.client.pose.PoseFrame.RIGHT_ARM, 1, r);
                rig.finalPose(dev.mcreload.anim.client.pose.PoseFrame.LEFT_ARM, 1, l);
                rig.finalPose(dev.mcreload.anim.client.pose.PoseFrame.RIGHT_ARM, 2, rb);
                rig.finalPose(dev.mcreload.anim.client.pose.PoseFrame.LEFT_ARM, 2, lb);
                rig.finalPose(dev.mcreload.anim.client.pose.PoseFrame.HEAD, 1, h);
                rig.finalPose(dev.mcreload.anim.client.pose.PoseFrame.RIGHT_ARM, 0, rp);
                McReloadAnim.LOG.info("[kinetrix] self-test {} t={} pose R=({}, {}, {}) bend({}, {}) pos({}, {}, {}) L=({}, {}, {}) bend({}, {}) head=({}, {}, {}) w think={} rummage={} trade={} sleep={}",
                        currentLabel, scenarioTick, f2(r[0]), f2(r[1]), f2(r[2]), f2(rb[0]), f2(rb[1]), f2(rp[0]), f2(rp[1]), f2(rp[2]),
                        f2(l[0]), f2(l[1]), f2(l[2]), f2(lb[0]), f2(lb[1]), f2(h[0]), f2(h[1]), f2(h[2]),
                        f2(rig.motion.thinkW.v), f2(rig.motion.rummageW.v), f2(rig.motion.tradeW.v), f2(rig.motion.sleepW.v));
            }
        }
        // Iron's casts: what we put out for the arms and what was finally drawn, per frame of interest.
        if (mc.player != null && currentLabel != null && currentLabel.startsWith("98") && scenarioTick % 4 == 0) {
            dev.mcreload.anim.client.rig.PlayerRig rig = dev.mcreload.anim.client.rig.RigRegistry.get(mc.player);
            if (rig != null) {
                int ra = dev.mcreload.anim.client.pose.PoseFrame.RIGHT_ARM, la = dev.mcreload.anim.client.pose.PoseFrame.LEFT_ARM;
                int to = dev.mcreload.anim.client.pose.PoseFrame.TORSO, bo = dev.mcreload.anim.client.pose.PoseFrame.BODY;
                float[] r = new float[3], l = new float[3], br = new float[3], bl = new float[3], rb = new float[3], lb = new float[3],
                        rp = new float[3], brp = new float[3], tr = new float[3], tb = new float[3], bd = new float[3];
                rig.finalPose(ra, 1, r);
                rig.finalPose(la, 1, l);
                rig.basePose(ra, 1, br);
                rig.basePose(la, 1, bl);
                rig.finalPose(ra, 2, rb);
                rig.finalPose(la, 2, lb);
                rig.finalPose(ra, 0, rp);
                rig.basePose(ra, 0, brp);
                rig.finalPose(to, 1, tr);
                rig.finalPose(to, 2, tb);
                rig.finalPose(bo, 1, bd);
                var m = rig.motion;
                McReloadAnim.LOG.info("[kinetrix] self-test {} t={} irons R=({}, {}, {}) ours({}, {}, {}) bend({}, {}) pos({}, {}, {}) ours({}, {}, {})"
                                + " L=({}, {}, {}) ours({}, {}, {}) bend({}, {}) torso=({}, {}, {}) bend({}, {}) body=({}, {}, {})"
                                + " foreign R={} L={} T={} cast={} {} ironsAnim={} bow={} using={} anim={} free={} yaw head-body={}",
                        currentLabel, scenarioTick, f2(r[0]), f2(r[1]), f2(r[2]), f2(br[0]), f2(br[1]), f2(br[2]), f2(rb[0]), f2(rb[1]),
                        f2(rp[0]), f2(rp[1]), f2(rp[2]), f2(brp[0]), f2(brp[1]), f2(brp[2]),
                        f2(l[0]), f2(l[1]), f2(l[2]), f2(bl[0]), f2(bl[1]), f2(bl[2]), f2(lb[0]), f2(lb[1]),
                        f2(tr[0]), f2(tr[1]), f2(tr[2]), f2(tb[0]), f2(tb[1]), f2(bd[0]), f2(bd[1]), f2(bd[2]),
                        f2(rig.foreign[ra]), f2(rig.foreign[la]), f2(rig.foreign[to]), f2(m.castW.v), m.cast.spellId(), rig.ironsAnimating(), f2(m.bowW.v),
                        mc.player.isUsingItem(), mc.player.getUseItem().getUseAnimation(), f2(m.armsFree.v),
                        f2(net.minecraft.util.Mth.wrapDegrees(mc.player.yHeadRot - mc.player.yBodyRot)));
            }
        }
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
            if (mc.player.getVehicle() instanceof net.minecraft.world.entity.LivingEntity v) {
                McReloadAnim.LOG.info("[kinetrix] self-test {} mount pos=({}, {}) onGround={} walkSpeed={} vy={} zza={}",
                        currentLabel, String.format("%.2f", v.getX()), String.format("%.2f", v.getY()), v.onGround(),
                        String.format("%.2f", v.walkAnimation.speed()),
                        rig == null ? "-" : String.format("%.3f", rig.motion.mountVy.v), String.format("%.2f", mc.player.zza));
            }
        }

        Scenario s = SCENARIOS.get(scenarioIndex);
        currentLabel = s.name();
        // A resource reload (loading overlay) pauses the script until it is done.
        if (mc.getOverlay() != null) return;
        if (scenarioTick == 0) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.options.keyUse.setDown(false);
            mc.options.keyAttack.setDown(false);
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
        // The sword wind-up lasts only the last 0.28 s of a drop: one extra shot whenever a frame
        // happens to fall inside it.
        if (s.name().startsWith("07") && mc.player != null && !mc.player.onGround()) {
            dev.mcreload.anim.client.rig.PlayerRig rig = dev.mcreload.anim.client.rig.RigRegistry.get(mc.player);
            if (rig != null && rig.motion.impactIn < 0.2f && windupShot != scenarioIndex) {
                windupShot = scenarioIndex;
                Screenshot.grab(mc.gameDirectory, s.name() + "_windup_t" + t + "_in" + Math.round(rig.motion.impactIn * 100) + ".png",
                        mc.getMainRenderTarget(), msg -> {});
            }
        }
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
        noPanels = false;
        if (sp.isPassenger()) sp.stopRiding();
        if (sp.isSleeping()) sp.stopSleeping();
        if (sp.isFallFlying()) sp.stopFallFlying();
        sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
        sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
        sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, ItemStack.EMPTY);
        sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, ItemStack.EMPTY);
        sp.serverLevel().setDayTime(6000);
        sp.setItemInHand(InteractionHand.MAIN_HAND, hand.copy());
        sp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        if (!sp.isCreative()) sp.setGameMode(GameType.CREATIVE);
        // The obsidian target of the mining test.
        if (sp.serverLevel().getBlockState(new BlockPos(4, Y, 0)).is(Blocks.OBSIDIAN)) {
            sp.serverLevel().setBlock(new BlockPos(4, Y, 0), Blocks.AIR.defaultBlockState(), 3);
            sp.serverLevel().setBlock(new BlockPos(4, Y + 1, 0), Blocks.AIR.defaultBlockState(), 3);
        }
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
