package dev.mcreload.anim.client.rig;

import dev.mcreload.anim.client.math.Smooth;
import dev.mcreload.anim.client.math.Spring;
import dev.mcreload.anim.compat.IronsSpellsCompat;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.HitResult;

/**
 * Everything the animation modules need to know about one player, smoothed so that every
 * state change (start sprinting, take off, start casting...) is a blend, never a snap.
 *
 * Raw movement is sampled once per game tick; weights and springs advance every rendered frame.
 */
public final class MotionState {
    private final AbstractClientPlayer player;
    /** Per-player phase offset so idle motion is not in lock-step across players. */
    public final float seed;

    // ---- per tick ----
    private double vx, vy, vz, pvx, pvy, pvz;
    /** Our own position history: playerAnimator ticks us before the entity moves, so xo == x then. */
    private double lastX = Double.NaN, lastY, lastZ;
    private float yawRate, prevYawRate;
    private boolean wasOnGround = true;
    /** Ticks of "still treading water" left (hysteresis so the pose never flickers at the edge). */
    private int treadHold;
    private int groundTicks;
    private int lastRideTick = -1000;
    private int airTicks;
    private float minAirVy;
    private int lastHurtTime;
    private boolean wasCasting;
    public IronsSpellsCompat.CastInfo cast = IronsSpellsCompat.CastInfo.NONE;

    // ---- jumping ----
    /** Alternates every jump, like Mo'Bends: which leg leads the leap. */
    public boolean jumpLeg;
    private int takeoffTick = -1000;
    /** How fast we were moving when we left the ground, 0..1. */
    public float takeoffRun;

    // ---- per frame ----
    /** Seconds, continuous. */
    public float time;
    /** Seconds since the previous frame for this player (0 when re-rendered in the same frame). */
    public float dt;
    private float lastTime = Float.NaN;

    /** Interpolated vertical speed, blocks/tick (+ = up). */
    public float vertical;
    public float horizontalSpeed;
    public float sneakPhase;
    /** Last skid after a sprint. */
    public float brakeStart = -100f;
    /** A melee weapon in the main hand. */
    public boolean holdingBlade;
    /** Falling with a blade in hand and W not held: the sword goes up, to be planted on landing. */
    public final Smooth bladeReadyW = new Smooth(8f);
    /**
     * This drop ends in a hard landing (the speed we will hit the ground at, see #predictedImpact,
     * is past the sword-plant / roll threshold): known from the top of the drop, not only once
     * we are already falling fast, so the sword can be held point down for the whole fall.
     */
    public final Smooth hardDropW = new Smooth(12f);
    private boolean hardDropAhead;
    /**
     * Seconds until this drop touches down (predicted with the hard landing, see
     * #predictedImpact; 99 when unknown or on the ground): the sword is wound up over the last
     * moments before it.
     */
    public float impactIn = 99f;
    private int impactTicks = 99;
    /** ParCool is animating one of its parkour moves on this player: our whole layer steps aside. */
    public boolean parkour;
    public final Smooth parkourW = new Smooth(12f);
    /** Held item tipped forward in the hand (radians), smoothed; modules raise the target. */
    public final Smooth itemTilt = new Smooth(24f);
    public float itemTiltTarget;
    /** The same for the item in the other hand (a second sword). */
    public final Smooth itemTiltOff = new Smooth(24f);
    public float itemTiltOffTarget;
    /**
     * Set by a module to drive the turn exactly this frame, any sign, no smoothing (a twirl
     * computed along a smooth curve: smoothing it only lags and, with the half-turn wrap, can
     * jump); NaN when unused. Main hand and other hand.
     */
    public float itemTiltDirect = Float.NaN, itemTiltOffDirect = Float.NaN;
    /** The main-hand weapon takes both hands (Better Combat two-handed): see Weapons#isTwoHanded. */
    public boolean twoHandedWeapon;
    /** A one-handed sword in the main hand. */
    public boolean oneHandSword;
    /** A one-handed sword in each hand. */
    public boolean dualSwords;
    /** 0..1 this frame: both hands are fixed on something (set by modules, cleared every frame). */
    public float armsHeld;
    /** Loose-limb inertia: forward, sideways and vertical (from body accelerations), and turning. */
    public final dev.mcreload.anim.client.math.Wobble swayF = new dev.mcreload.anim.client.math.Wobble(60f, 7f, 1.2f),
            swayS = new dev.mcreload.anim.client.math.Wobble(60f, 7f, 1.2f),
            swayV = new dev.mcreload.anim.client.math.Wobble(80f, 8f, 1.2f),
            swayT = new dev.mcreload.anim.client.math.Wobble(50f, 6f, 1.2f);
    private float lastF, lastS, lastV, lastT;
    /** Main hand is the right hand. */
    public boolean mainRight = true;

    public final Smooth forward = new Smooth(10f), strafe = new Smooth(10f), turn = new Smooth(8f), move = new Smooth(8f);
    /** 1 = normal on-foot animation allowed; fades to 0 when riding, sleeping, gliding, riptiding. */
    public final Smooth gate = new Smooth(8f);
    public final Smooth sprintW = new Smooth(6f), sneakW = new Smooth(9f), groundW = new Smooth(14f);
    public final Smooth airW = new Smooth(12f), fallW = new Smooth(3f), skydiveW = new Smooth(1.5f);
    public final Smooth swimW = new Smooth(5f), waterW = new Smooth(5f), climbW = new Smooth(8f);
    /** Crawling on land (vanilla crawl pose: our key, Personality's, or squeezed under a block). */
    public final Smooth crawlW = new Smooth(6f), crawlMoveW = new Smooth(5f);
    /** Sitting on the ground (our key, or Personality's). */
    public final Smooth sitW = new Smooth(4f);
    public boolean crawling, sitting;
    /** Crawl cycle, in cycles (one cycle = a pull with each arm), driven by the distance crawled. */
    public float crawlPhase;
    /** Sitting style 0..2 we sat down in, when, and whether Personality already lowers the model. */
    public int sitStyle;
    public float sitStart = -100f;
    public boolean personalitySit;
    /** Another player's sitting style as their client reported it (0 = not sitting). */
    private int remoteSit;
    public final Smooth blockW = new Smooth(10f), bowW = new Smooth(9f), crossbowW = new Smooth(9f), spearW = new Smooth(9f);
    public final Smooth eatW = new Smooth(8f), drinkW = new Smooth(8f), hornW = new Smooth(8f);
    public final Smooth castW = new Smooth(6f);
    public final Smooth rideW = new Smooth(6f), flyW = new Smooth(5f), elytraW = new Smooth(6f);
    public final Smooth guardW = new Smooth(6f);
    public final Smooth sleepW = new Smooth(4f);
    /** How this player sleeps this time (SLEEP_*), chosen anew every time they lie down. */
    public int sleepStyle = -1;
    private boolean wasSleeping;
    /** The sleep style their own client chose, or -1 (then everyone works out the same one). */
    private int remoteSleep = -1;
    public static final int SLEEP_STARFISH = 0, SLEEP_FACE_DOWN = 1, SLEEP_HANDS_BEHIND = 2, SLEEP_STYLES = 3;

    /**
     * In a menu: thinking it over (inventory, crafting table, pause menu and other menus),
     * rummaging (chests, barrels, furnaces, any other container), talking (trading with a villager).
     */
    public static final int SCREEN_NONE = 0, SCREEN_THINK = 1, SCREEN_RUMMAGE = 2, SCREEN_TRADE = 3;
    public int screenPose;
    /** When the current menu pose started. */
    public float screenStart = -100f;
    public final Smooth thinkW = new Smooth(5f), rummageW = new Smooth(5f), tradeW = new Smooth(5f);
    /**
     * Seconds the game spent paused (single player menus): the animation clock keeps running
     * through it, so you can see yourself think behind the pause menu.
     */
    private float pausedFor;
    private long pauseNanos;
    /** Holding a melee weapon and ready to fight (standing / walking, recently attacked). */
    /**
     * Sword guard: a sword in hand, for a couple of seconds after drawing it or after any swing
     * (at a mob or at the air), standing or walking; see BladeModule#stance.
     */
    public final Smooth readyW = new Smooth(6f);
    /**
     * When a sword came into the main hand (switched to, swapped in) and into the other hand:
     * it spins once in the hand, then the guard comes up (BladeModule#spin).
     */
    public float drawStart = -100f, offDrawStart = -100f;
    /** How long the spin of a sword taken in hand takes, and how long the guard lasts after it / a swing. */
    public static final float DRAW_SECONDS = 0.7f, GUARD_HOLD = 2.5f;
    private boolean wasSword, wasOffSword, drawArmed;
    /**
     * Running with a sword (or two), held out to the side: kept through jumps and short drops,
     * dropped when you stop, get hit or really fall (BladeModule#swordRun).
     */
    public final Smooth swordRunW = new Smooth(10f);
    private boolean swordRun;
    /**
     * Dozing off standing (another mod's AFK nap): its head hanging, its snore, or an animation
     * called so. Our fidgets wait, "Z z z" rise over the head (SleepZzz).
     */
    public boolean dozing;
    private float dozeSeen, snoreUntil = -100f, dozeAnimAt = -100f, foreignHead;
    /** Our own nap: a minute without moving, looking round or doing anything (IdleModule#doze). */
    public static final float DOZE_AFTER = 60f;
    public final Smooth dozeW = new Smooth(4f);
    public boolean ownDoze;
    /** When the player last did anything, and when the nap started. */
    public float activeAt, dozeStart = -100f;
    private float lastLookYaw = Float.NaN, lastLookPitch;
    /**
     * Last attack with a sword in hand: a swing of ours, one Better Combat plays (BladeModule
     * sees its animation on the arm), or simply the attack key pressed: the guard runs from it.
     */
    public float attackTime = -100f;
    /** Weapon in the main hand and nothing in the other: two-handed (samurai) grip. */
    public final Smooth twoHandW = new Smooth(5f);
    public final Smooth crossCarryW = new Smooth(6f), crossAimW = new Smooth(8f);

    // ---- one-shot reactions
    public final Spring brake = new Spring(40f, 8.5f);
    public final Spring shieldHit = new Spring(160f, 14f);
    public final Spring hitFront = new Spring(120f, 12f);
    public final Spring hitBack = new Spring(120f, 12f);
    public static final int LAND_NONE = 0, LAND_ROLL = 1, LAND_SWORD = 2, LAND_BACKROLL = 3;
    /** How long the sword landing lasts (strike, kneel, getting up), unless we walk off sooner. */
    public static final float SWORD_LAND_SECONDS = 0.5f;
    /** Landing faster than this (blocks per tick) is a hard landing: a roll with W, a sword plant without. */
    static final float HARD_LANDING = 0.62f;
    public int landKind = LAND_NONE;
    /** Riding: when the vehicle last moved / was paddled (seat idles start after that). */
    public float rideIdleStart;
    /** How far back (radians) the body was already lying when the back roll started. */
    public float backRollFrom;
    /** How far back the body lies right now in a backwards fall (radians, set by WaterModule#chill). */
    public float backLie;
    /** Trident thrown (release after a wind-up). */
    public float spearThrowStart = -100f;
    private boolean wasSpearUse;
    /**
     * Last arm Euler angles produced for each arm (0 = right, 1 = left) by composed rotations,
     * so the next frame can pick the equivalent solution closest to it (no flips).
     */
    public final float[][] armEulerRef = new float[4][3];
    public final float[] armEulerTime = {-100f, -100f, -100f, -100f};
    public float landStart = -100f;
    /** When we started moving off during a sword landing (it then lets go at once), or -100. */
    public float landRelease = -100f;
    public float explodeStart = -100f, explodeStrength;
    public float totemStart = -100f;
    private int hurtPending;
    private float prevTickSpeed;
    private boolean wasSprinting;
    private int lastGlideTick = -1000;
    private boolean wasInWater;
    private int lastWaterTick = -1000;
    private int teleportTick = -1000;
    private int lastVisSwimTick = -1000;
    /** Speed (blocks/tick) of the last tick in the air, for judging how hard a landing was. */
    private float lastAirSpeed;
    private boolean rollEarly;
    private int lastSprintTick = -1000;
    // ---- fishing
    /** Casting the rod out / reeling it in (times), and how much we are fishing right now. */
    public float fishCastStart = -100f, fishReelStart = -100f;
    public final Smooth fishingW = new Smooth(5f);
    /** +1 if the rod is in the right hand, -1 if left. */
    public float rodSide = 1f;

    // ---- riding a mount
    /** The mount's vertical speed (blocks/tick, smoothed) and turn rate (deg/tick, smoothed). */
    public final Smooth mountVy = new Smooth(10f), mountTurn = new Smooth(6f);
    /** The mount touching down after a jump or a drop. */
    public final Spring mountLand = new Spring(70f, 9f);
    private int mountAirTicks;
    private float mountMinVy;
    private double lastMountY = Double.NaN;
    private float mountVyTick, mountTurnTick;

    /** Diving in from a height: streamlined "dolphin" entry. */
    public float diveStart = -100f;
    /** Swapping items between hands (F): hands meet in the middle and part again. */
    public float swapStart = -100f;
    private ItemStack lastMain = ItemStack.EMPTY, lastOff = ItemStack.EMPTY;
    // ---- idle fidgets
    public float idleSince;
    public int idleAction = -1;
    public float idleActionStart = -100f;
    private float nextIdleAt = 8f;
    private final java.util.Random rng = new java.util.Random();

    // ---- archery / throwing
    /** Alternates every bow draw: 0 = classic vertical bow, 1 = horizontal bow drawn from above. */
    public int bowStyle;
    private boolean wasDrawingBow;
    /** Alternates every throw: +1 main arm, -1 off arm. */
    public float throwSide = -1f;
    private float lastThrowTime = -100f;

    public final Spring land = new Spring(95f, 13f);
    public final Spring hurt = new Spring(140f, 13f);
    public final Spring release = new Spring(85f, 10f);

    /** Draw progress of the bow/crossbow/trident currently or last in use, 0..1. */
    public float usePull;
    /** +1 if the item in use is in the right hand, -1 if left. */
    public float useSide = 1f;

    /** Smoothed horizontal speed, blocks/tick. Walk ~0.215, sprint ~0.28, sneak ~0.065. */
    public final Smooth speed = new Smooth(7f);
    /**
     * 1 = our arm poses may replace vanilla's. Drops to 0 whenever vanilla's arm pose matters
     * (using an item, holding a loaded crossbow, swinging an unknown item, Iron's casting).
     */
    public final Smooth armsFree = new Smooth(10f);
    public final Smooth toolSwingW = new Smooth(14f);

    // ---- melee / tools ----
    public static final int SLASH_H = 0, SLASH_V = 1, SLASH_BACK = 2, SPIN = 3, PUNCH = 4, TOOL = 5,
            PICK = 6, SHOVEL = 7, HOE = 8, CHOP = 9, AXE_OVER = 10, AXE_SWEEP = 11, THROW = 12, FIST_BLOCK = 13, PLACE = 14,
            FISH = 15, STAB = 16,
            /** Anything else in the hand (a block, armour, a part from a mod...) swung at the air or a mob. */
            SWAT = 17,
            /** An attack animated by another mod (Better Combat): nothing of ours plays. */
            FOREIGN_ATTACK = 99;
    /** Sword combo order: forehand, backhand, overhead, spinning finisher. */
    private static final int[] SWORD_COMBO = {SLASH_H, SLASH_BACK, SLASH_V, SPIN};
    public int slashKind = -1;
    public float slashStart = -100f;
    public float slashDuration = 0.6f;
    /** True when this swing directly continues the previous one (combo / mining loop): no blend-in dip. */
    public boolean slashChained;
    /** +1 right arm, -1 left arm. */
    public float slashSide = 1f;
    /** Fists alternate: +1 = the swinging (main) arm side, -1 = the other one. */
    public float punchSide = -1f;
    private float lastPunchTime = -100f;
    private int combo;
    private boolean lastSwinging;
    private int lastSwingTime;

    /** Continuous stroke phase for front crawl, radians. */
    public float swimPhase, paddlePhase;
    private double airStartY;
    private boolean diveEligible;
    private float fallHeight;
    /** Falling backwards into water from high up: lying back with the hands behind the head. */
    public final Smooth chillW = new Smooth(7f);
    public float chillSplashStart = -100f;
    /** How far the shoulders sit below the water surface (blocks), for floating higher. */
    public float surfaceDepth;
    private float fistSide = 1f;
    public final Smooth paddleW = new Smooth(4f), diveAirW = new Smooth(10f);
    /** Continuous climbing cycle, radians, driven by height gained. */
    public float climbPhase;
    private double lastClimbY = Double.NaN;

    public MotionState(AbstractClientPlayer player) {
        this.player = player;
        this.seed = (player.getUUID().hashCode() & 1023) / 1023f * Mth.TWO_PI;
    }

    /** Called once per game tick by playerAnimator. */
    public void tick() {
        pvx = vx; pvy = vy; pvz = vz;
        double x = player.getX(), y = player.getY(), z = player.getZ();
        if (Double.isNaN(lastX)) { lastX = x; lastY = y; lastZ = z; }
        vx = x - lastX;
        vy = y - lastY;
        vz = z - lastZ;
        lastX = x; lastY = y; lastZ = z;
        if (vx * vx + vy * vy + vz * vz > 9.0) { // teleport / respawn
            vx = vy = vz = 0;
            pvx = pvy = pvz = 0;
            airStartY = y;
            lastWaterTick = -1000;
            teleportTick = player.tickCount;
            // A teleport is a cut: drop the water poses at once instead of fading them out.
            if (!player.isInWater()) { waterW.v = 0f; swimW.v = 0f; paddleW.v = 0f; treadHold = 0; }
        }
        float lookYaw = player.getYHeadRot(), lookPitch = player.getXRot();
        if (Float.isNaN(lastLookYaw) || Math.abs(Mth.wrapDegrees(lookYaw - lastLookYaw)) + Math.abs(lookPitch - lastLookPitch) > 0.5f) {
            activeAt = time;
        }
        lastLookYaw = lookYaw;
        lastLookPitch = lookPitch;
        prevYawRate = yawRate;
        yawRate = Mth.wrapDegrees(player.yBodyRot - player.yBodyRotO);

        boolean ground = player.onGround();
        boolean special = player.isInWater() || player.isFallFlying() || player.onClimbable()
                || player.isPassenger() || player.getAbilities().flying || player.isVisuallyCrawling();
        float tickSpeed = (float) Math.sqrt(vx * vx + vz * vz);
        if (ground && wasOnGround && !special && player.tickCount - lastSprintTick <= 3 && prevTickSpeed > 0.2f && tickSpeed < prevTickSpeed * 0.6f && Cfg.locomotion) {
            // Sprint released: skid to a stop.
            if (time - brakeStart > 0.6f) brakeStart = time;
        }
        prevTickSpeed = tickSpeed;
        if (!ground) lastAirSpeed = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (player.isVisuallySwimming()) lastVisSwimTick = player.tickCount;
        wasSprinting = player.isSprinting();
        if (wasSprinting) lastSprintTick = player.tickCount;
        if (player.isFallFlying()) lastGlideTick = player.tickCount;

        // Diving into water from a height: streamlined dolphin entry; never "land" on the bottom.
        // Right after a teleport the fluid state is still the old one for a tick or two.
        boolean inWater = player.isInWater() && player.tickCount - teleportTick > 2;
        // Height fallen since the feet last left solid ground (not water).
        if (ground || player.onClimbable()) airStartY = player.getY();
        float fallen = (float) (airStartY - player.getY());
        // A dive only when jumping or falling in from dry land at least ~1.5 blocks above the
        // water, never when bobbing back in after surfacing.
        boolean fromLand = player.tickCount - lastWaterTick > 20;
        if (inWater && !wasInWater && fromLand && fallen >= 1.4f && Cfg.water) {
            if (chillW.v > 0.5f) chillSplashStart = time; else diveStart = time;
        }
        diveEligible = !inWater && fromLand;
        fallHeight = fallen;
        if (inWater) { minAirVy = 0f; airTicks = 0; lastWaterTick = player.tickCount; }
        wasInWater = inWater;

        // Treading water only in water deep enough to float in (not wading along the bottom or
        // hopping in the shallows), held for a few ticks so bobbing at the surface or brushing the
        // bottom never flips the pose; dropped at once when standing on something for two ticks
        // or when jumping up and out of the water.
        groundTicks = ground ? groundTicks + 1 : 0;
        float fluid = inWater ? (float) player.getFluidHeight(net.minecraft.tags.FluidTags.WATER) : 0f;
        if (inWater && fluid >= 0.9f && !ground) treadHold = 6;
        else if (groundTicks >= 2) treadHold = 0;
        else if (!inWater && vy > 0.08) treadHold = 0;
        else if (treadHold > 0 && !(inWater && fluid >= 0.6f)) treadHold--;

        // Riding: the rider's own "air time" means nothing, and getting off is not a landing.
        if (player.isPassenger()) {
            lastRideTick = player.tickCount;
            airTicks = 0;
            minAirVy = 0f;
            airStartY = player.getY();
            net.minecraft.world.entity.Entity mount = player.getVehicle();
            if (mount != null) {
                double my = mount.getY();
                mountVyTick = Double.isNaN(lastMountY) ? 0f : (float) Mth.clamp(my - lastMountY, -2.0, 2.0);
                lastMountY = my;
                mountTurnTick = Mth.wrapDegrees(mount.getYRot() - mount.yRotO);
                if (!mount.onGround()) {
                    mountAirTicks++;
                    mountMinVy = Math.min(mountMinVy, mountVyTick);
                } else {
                    if (mountAirTicks > 3) mountLand.impulse(Mth.clamp(-mountMinVy * 2.2f, 0.35f, 1.2f));
                    mountAirTicks = 0;
                    mountMinVy = 0f;
                }
            }
        } else {
            lastMountY = Double.NaN;
            mountVyTick = 0f;
            mountTurnTick = 0f;
            mountAirTicks = 0;
        }

        if (!ground) {
            if (wasOnGround && vy > 0.05 && !special) {
                // Take-off: switch the leading leg every jump.
                jumpLeg = !jumpLeg;
                takeoffTick = player.tickCount;
                takeoffRun = Mth.clamp((float) Math.sqrt(vx * vx + vz * vz) / 0.22f, 0f, 1f);
            }
            airTicks++;
            minAirVy = Math.min(minAirVy, (float) vy);
            // The item itself, not holdingBlade: that is only refreshed per frame, and a sword can
            // come into the hand on the very tick the drop starts.
            hardDropAhead = !special && Weapons.isMeleeWeapon(player.getMainHandItem()) && predictedImpact() > HARD_LANDING;
            // Falling backwards lying back: start the back roll the moment we touch down, like
            // the forward roll.
            if (!special && !rollEarly && Cfg.landing && chillW.v > 0.45f && -vy > 0.2
                    && player.tickCount - lastRideTick >= 10 && !remoteDriven()) {
                double gap = groundGap();
                if (gap >= 0 && gap < -vy * 1.1) {
                    landKind = LAND_BACKROLL;
                    landStart = time;
                    backRollFrom = backLie;
                    chillW.v = 0f;
                    rollEarly = true;
                    sendLand();
                }
            }
            // About to hit the ground hard while running on (W held, with or without a blade in
            // hand): start the roll at the moment of touchdown instead of a tick or two after it.
            if (!special && !rollEarly && Cfg.landing && -minAirVy > HARD_LANDING && movingForward() && chillW.v < 0.45f
                    && player.tickCount - lastRideTick >= 10 && !remoteDriven()) {
                double gap = groundGap();
                if (gap >= 0 && gap < -vy * 1.1) {
                    landKind = LAND_ROLL;
                    landStart = time;
                    rollEarly = true;
                    sendLand();
                }
            }
        } else {
            if (!wasOnGround && player.tickCount - lastGlideTick < 4) {
                // Coming in on an elytra. A gentle touchdown is just absorbed; slamming in fast
                // (diving into the ground) ends in a forward roll when W is held, otherwise a
                // hard, deep landing.
                if (Cfg.landing) {
                    if (remoteDriven() && takePendingLand()) {
                        // Their client rolled.
                    } else if (lastAirSpeed > 0.65f && movingForward() && !remoteDriven()) {
                        landKind = LAND_ROLL;
                        landStart = time;
                        sendLand();
                    } else if (lastAirSpeed > 0.65f) {
                        land.impulse(1.35f);
                    } else {
                        land.impulse(0.8f);
                    }
                }
            } else if (rollEarly) {
                // Already rolling.
            } else if (player.tickCount - lastRideTick < 10) {
                // Just got off a horse / out of a boat: step down, no landing reaction.
            } else if (!wasOnGround && airTicks > 2 && !special && Cfg.landing) {
                float impact = -minAirVy;
                ItemStack main = player.getMainHandItem();
                boolean swordOnly = Weapons.isMeleeWeapon(main);
                if (remoteDriven()) {
                    // Rolls and sword plants: whatever their own client chose (it knows the keys).
                    if (!takePendingLand()) {
                        if (impact > HARD_LANDING) land.impulse(1.25f);
                        else if (impact > 0.18f) land.impulse(Mth.clamp((impact - 0.12f) / 0.85f, 0.15f, 1.25f));
                    }
                } else if (impact > 0.25f && chillW.v > 0.45f) {
                    // Fell backwards lying back (jumped back holding S): roll out over the shoulder.
                    landKind = LAND_BACKROLL;
                    landStart = time;
                    backRollFrom = backLie;
                    chillW.v = 0f;
                    sendLand();
                } else if (impact > HARD_LANDING && movingForward()) {
                    // High drop while running on (W held): break the fall with a forward roll,
                    // blade in hand or not.
                    landKind = LAND_ROLL;
                    landStart = time;
                    sendLand();
                } else if (impact > HARD_LANDING && swordOnly) {
                    // High drop with a blade in hand and no W (no keys, or A / S / D): plant it in
                    // the ground.
                    landKind = LAND_SWORD;
                    landStart = time;
                    sendLand();
                } else if (impact > HARD_LANDING) {
                    // High drop standing still: just a deeper crouch.
                    land.impulse(1.25f);
                } else if (impact > 0.18f) {
                    land.impulse(Mth.clamp((impact - 0.12f) / 0.85f, 0.15f, 1.25f));
                }
            }
            airTicks = 0;
            minAirVy = 0f;
            rollEarly = false;
            hardDropAhead = false;
            impactTicks = 99;
        }
        wasOnGround = ground;

        if (player.hurtTime > lastHurtTime && Cfg.reactions) hurtPending = 3;
        lastHurtTime = player.hurtTime;
        if (hurtPending > 0) {
            // Which way did the knockback push us? Pushed backwards = hit from the front.
            hurtPending--;
            double h = Math.sqrt(vx * vx + vz * vz);
            float yawR = player.yBodyRot * Mth.DEG_TO_RAD;
            double along = vx * -Mth.sin(yawR) + vz * Mth.cos(yawR);
            if (h > 0.04 || hurtPending == 0) {
                if (h > 0.04 && along > 0.0) hitBack.impulse(1f); else hitFront.impulse(1f);
                hurt.impulse(0.4f);
                hurtPending = 0;
            }
        }

        // Items swapped between hands (works for everyone you can see).
        ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
        if (!(main.isEmpty() && off.isEmpty())
                && ItemStack.matches(main, lastOff) && ItemStack.matches(off, lastMain)
                && !ItemStack.matches(main, off)) {
            swapStart = time;
        }
        // Drawing a sword: switching to one (or swapping one into the main hand) brings it up into
        // a fighting guard. Not on the very first tick (joining with a sword already in hand).
        // One-handed swords only: a two-handed weapon stays in both hands.
        boolean sword = Weapons.isOneHandedSword(main);
        if (sword && drawArmed && (!wasSword || !ItemStack.isSameItem(main, lastMain))) drawStart = time;
        wasSword = sword;
        boolean offSword = Weapons.isOneHandedSword(off);
        if (offSword && drawArmed && (!wasOffSword || !ItemStack.isSameItem(off, lastOff))) offDrawStart = time;
        wasOffSword = offSword;
        drawArmed = true;
        lastMain = main.copy();
        lastOff = off.copy();

        cast = Cfg.ironsSpells ? IronsSpellsCompat.query(player) : IronsSpellsCompat.CastInfo.NONE;
        if (wasCasting && !cast.casting()) release.impulse(1f);
        wasCasting = cast.casting();
    }

    /** Called every rendered frame before the modules run. */
    public void frame(float pt) {
        if (isLocal() && net.minecraft.client.Minecraft.getInstance().isPaused()) {
            long n = System.nanoTime();
            if (pauseNanos != 0L) pausedFor += Math.min((n - pauseNanos) / 1e9f, 0.1f);
            pauseNanos = n;
        } else {
            pauseNanos = 0L;
        }
        float now = (player.tickCount + pt) / 20f + pausedFor;
        if (Float.isNaN(lastTime) || now < lastTime - 1f) {
            dt = 0f;
            lastTime = now;
        } else if (now > lastTime) {
            dt = Math.min(now - lastTime, 0.1f);
            lastTime = now;
        } else {
            dt = 0f;
        }
        time = now;

        double ivx = Mth.lerp(pt, pvx, vx);
        double ivz = Mth.lerp(pt, pvz, vz);
        vertical = (float) Mth.lerp(pt, pvy, vy);
        horizontalSpeed = (float) Math.sqrt(ivx * ivx + ivz * ivz);

        float yaw = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        float fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        float rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        forward.to((float) (ivx * fx + ivz * fz), dt);
        strafe.to((float) (ivx * rx + ivz * rz), dt);
        turn.to(Mth.lerp(pt, prevYawRate, yawRate), dt);
        if (dt > 0f && dt < 0.25f) {
            // Accelerations in blocks/tick per second; limbs lag behind them.
            float aF = (forward.v - lastF) / dt, aS = (strafe.v - lastS) / dt;
            float aV = (vertical - lastV) / dt, aT = (turn.v - lastT) / dt;
            swayF.step(dt, -Mth.clamp(aF, -4f, 4f) * 12f);
            swayS.step(dt, -Mth.clamp(aS, -4f, 4f) * 12f);
            swayV.step(dt, -Mth.clamp(aV, -6f, 6f) * 10f);
            swayT.step(dt, -Mth.clamp(aT, -200f, 200f) * 0.12f);
        }
        lastF = forward.v; lastS = strafe.v; lastV = vertical; lastT = turn.v;
        move.to(Mth.clamp(horizontalSpeed / 0.2f, 0f, 1f), dt);

        boolean ground = player.onGround();
        boolean creativeFlying = player.getAbilities().flying;
        boolean gliding = player.isFallFlying();
        boolean riding = player.isPassenger();
        parkour = dev.mcreload.anim.compat.ParCoolCompat.animating(player);
        parkourW.to(parkour ? 1f : 0f, dt);
        boolean blocked = riding || player.isSleeping() || gliding || player.isAutoSpinAttack() || creativeFlying || parkour;
        gate.to(blocked ? 0f : 1f, dt);

        // The vanilla crawl pose on land is our crawl; in water it is swimming.
        crawling = player.isVisuallyCrawling() && !blocked && Cfg.postures;
        boolean swimming = player.isVisuallySwimming() && !crawling;
        // Treading water (see tick(): deep water only, with hysteresis).
        boolean treading = treadHold > 0 && !swimming && !riding;
        boolean wet = treading || swimming;
        boolean climbing = player.onClimbable() && !ground && !riding && !crawling;
        boolean air = !ground && !wet && !climbing && !blocked && !crawling;
        updateSit(blocked || crawling || wet);

        sprintW.to(player.isSprinting() && horizontalSpeed > 0.05f ? 1f : 0f, dt);
        sneakW.to(player.isCrouching() ? 1f : 0f, dt);
        // On the ground on our feet (crawling and sitting have their own module).
        groundW.to(ground && !crawling && !sitting ? 1f : 0f, dt);
        airW.to(air ? 1f : 0f, dt);
        fallW.to(air ? Mth.clamp((-vertical - 0.40f) / 0.5f, 0f, 1f) : 0f, dt);
        skydiveW.to(air ? Mth.clamp((-vertical - 1.4f) / 0.8f, 0f, 1f) : 0f, dt);
        swimW.to(swimming ? 1f : 0f, dt);
        waterW.to(treading ? 1f : 0f, dt);
        climbW.to(climbing ? 1f : 0f, dt);
        crawlW.to(crawling ? 1f : 0f, dt);
        crawlMoveW.to(crawling ? Mth.clamp(horizontalSpeed / 0.03f, 0f, 1f) : 0f, dt);
        sitW.to(sitting ? 1f : 0f, dt);
        blockW.to(player.isBlocking() ? 1f : 0f, dt);
        rideW.to(riding && !player.isSleeping() ? 1f : 0f, dt);
        flyW.to(creativeFlying && !ground ? 1f : 0f, dt);
        elytraW.to(gliding ? 1f : 0f, dt);

        UseAnim use = UseAnim.NONE;
        if (player.isUsingItem()) {
            ItemStack stack = player.getUseItem();
            use = stack.getUseAnimation();
            HumanoidArm arm = player.getUsedItemHand() == InteractionHand.MAIN_HAND
                    ? player.getMainArm() : player.getMainArm().getOpposite();
            useSide = arm == HumanoidArm.RIGHT ? 1f : -1f;
            float used = stack.getUseDuration() - player.getUseItemRemainingTicks() + pt;
            switch (use) {
                case BOW, SPEAR -> usePull = Mth.clamp(used / 20f, 0f, 1f);
                case CROSSBOW -> usePull = Mth.clamp(used / Math.max(1, CrossbowItem.getChargeDuration(stack)), 0f, 1f);
                default -> { }
            }
        }
        bowW.to(use == UseAnim.BOW ? 1f : 0f, dt);
        crossbowW.to(use == UseAnim.CROSSBOW ? 1f : 0f, dt);
        spearW.to(use == UseAnim.SPEAR ? 1f : 0f, dt);
        eatW.to(use == UseAnim.EAT ? 1f : 0f, dt);
        drinkW.to(use == UseAnim.DRINK ? 1f : 0f, dt);
        hornW.to(use == UseAnim.TOOT_HORN ? 1f : 0f, dt);

        castW.to(cast.casting() ? 1f : 0f, dt);

        mainRight = player.getMainArm() == HumanoidArm.RIGHT;
        mountVy.to(mountVyTick, dt);
        mountTurn.to(mountTurnTick, dt);
        mountLand.step(dt);
        // Fishing: rod in hand and the bobber out.
        ItemStack rodMain = player.getMainHandItem();
        boolean rodInMain = rodMain.getItem() instanceof net.minecraft.world.item.FishingRodItem;
        boolean rodInOff = player.getOffhandItem().getItem() instanceof net.minecraft.world.item.FishingRodItem;
        if (rodInMain || rodInOff) {
            HumanoidArm rodArm = rodInMain ? player.getMainArm() : player.getMainArm().getOpposite();
            rodSide = rodArm == HumanoidArm.RIGHT ? 1f : -1f;
        }
        fishingW.to((rodInMain || rodInOff) && player.fishing != null && !blocked ? 1f : 0f, dt);
        // The item's turn in the hand only matters modulo a full turn: after a spin, take the
        // shortest way back instead of unwinding every turn.
        stepTilt(itemTilt, itemTiltTarget, itemTiltDirect, dt);
        stepTilt(itemTiltOff, itemTiltOffTarget, itemTiltOffDirect, dt);
        itemTiltTarget = 0f;
        itemTiltOffTarget = 0f;
        itemTiltDirect = Float.NaN;
        itemTiltOffDirect = Float.NaN;
        twoHandedWeapon = Weapons.isTwoHanded(player.getMainHandItem());
        oneHandSword = Weapons.isOneHandedSword(player.getMainHandItem());
        dualSwords = oneHandSword && Weapons.isOneHandedSword(player.getOffhandItem());
        armsHeld = 0f;
        holdingBlade = Weapons.isMeleeWeapon(player.getMainHandItem());
        speed.to(horizontalSpeed, dt);
        // Sneaking: our own step cycle, fast enough that the feet really step instead of gliding.
        if (player.isCrouching()) sneakPhase += dt * 88f * Math.min(horizontalSpeed, 0.12f);
        // Front crawl: about one stroke per second per arm, a bit faster when sprint-swimming.
        if (swimming) swimPhase += dt * (2.4f + 2.6f * Math.min(1f, horizontalSpeed / 0.2f));
        // Crawling: the cycle follows the ground covered (backwards when backing out), so the
        // hands and knees grip instead of sliding. One cycle = about one block.
        if (crawling) crawlPhase += (forward.v < -0.01f ? -1f : 1f) * horizontalSpeed * 20f * dt / CRAWL_CYCLE_BLOCKS;
        // Paddling along at the surface without sprinting.
        paddleW.to(treading ? Mth.clamp(horizontalSpeed / 0.07f, 0f, 1f) : 0f, dt);
        if (treading) paddlePhase += dt * (2.2f + 10f * Math.min(horizontalSpeed, 0.15f));
        // Jumping or falling backwards (holding S, or clearly moving backwards): lie back in the
        // air with the hands behind the head; landing, roll out backwards (or float, in water).
        boolean backwards = isLocalPressingBack()
                || (!isLocal() && !remoteDriven() && forward.v < -0.05f);
        // Heading for water from dry land with enough drop: the dolphin dive starts from the top
        // of the jump (or as soon as we go over the edge) and lasts until we hit the water.
        float gap = air && diveEligible && vertical < 0.12f ? waterGap() : -1f;
        boolean overWater = gap >= 0f && fallHeight + gap >= 1.4f;
        // Only a real drop (well below where we took off), never an ordinary jump on flat ground.
        // With a blade in hand over dry land, S means the sword landing instead (see bladeReadyW).
        chillW.to(air && backwards && airTicks >= 3 && fallHeight >= 1.2f && (!holdingBlade || overWater) ? 1f : 0f, dt);
        if (ground) chillW.v = Math.max(0f, chillW.v - dt * 5f);   // short hop: straight back up
        diveAirW.to(overWater && chillW.v < 0.5f ? 1f : 0f, dt);
        // Falling with a blade: sword raised to plant it, unless W is held (that lands in a roll).
        // Pressing W lets go of it three times faster than it comes up, so stepping off an edge
        // with W from a standstill never starts turning the blade over before the roll.
        boolean bladeReady = holdingBlade && !movingForward();
        bladeReadyW.to(bladeReady ? 1f : 0f, bladeReady ? dt : 3f * dt);
        impactIn = air && impactTicks < 99 ? Math.max(0f, (impactTicks - pt) / 20f) : 99f;
        // Sword landing: walking off (or jumping) once the blade is in lets go of the kneel at
        // once instead of waiting for it to finish.
        if (landKind == LAND_SWORD && landRelease < landStart && since(landStart) > 0.12f && wantsToMove()) {
            landRelease = time;
        }
        hardDropW.to(air ? Math.max(hardDropAhead ? 1f : 0f, Mth.clamp((-vertical - 0.50f) / 0.12f, 0f, 1f)) : 0f, dt);
        surfaceDepth = treading ? surfaceDepth() : 0f;

        // Climbing: the cycle follows the height actually gained, so hands match the speed.
        double py = Mth.lerp(pt, player.yo, player.getY());
        if (climbing && !Double.isNaN(lastClimbY)) climbPhase += (float) (py - lastClimbY) * 3.2f;
        lastClimbY = py;

        // Melee: start our own timeline the moment a swing begins.
        boolean swinging = player.swinging;
        int swingTime = player.swingTime;
        if (swinging && (!lastSwinging || swingTime < lastSwingTime)) startSwing();
        lastSwinging = swinging;
        lastSwingTime = swingTime;
        toolSwingW.to(slashKind == TOOL && slashProgress() < 1.1f ? 1f : 0f, dt);

        ItemStack mainItem = player.getMainHandItem(), offItem = player.getOffhandItem();
        boolean weapon = Weapons.isMeleeWeapon(mainItem) && !(mainItem.getItem() instanceof net.minecraft.world.item.TridentItem);
        // The sword guard: for a couple of seconds after drawing the sword or after any swing of it
        // (at a mob or at the air), standing or walking (like the fists' guard), and still while
        // blocking with a shield in the other hand. Swords only: an axe or a trident is just held.
        // One-handed swords only: a two-handed weapon stays in both hands (Better Combat's hold).
        boolean swordInHand = Weapons.isOneHandedSword(mainItem);
        if (swordInHand && isLocal() && net.minecraft.client.Minecraft.getInstance().screen == null
                && net.minecraft.client.Minecraft.getInstance().options.keyAttack.isDown()) {
            attackTime = time;
        }
        boolean afterSwing = time - attackTime < 0.6f + GUARD_HOLD;
        boolean afterDraw = time - Math.max(drawStart, offDrawStart) < DRAW_SECONDS + GUARD_HOLD;
        readyW.to(swordInHand && (afterSwing || afterDraw) && !ownDoze && !blocked && !swimming && !crawling && !sitting
                && sprintW.v < 0.5f && (!player.isUsingItem() || player.isBlocking()) && screenPose == SCREEN_NONE ? 1f : 0f, dt);
        // Running with the sword: starts sprinting on the ground; a jump or a step down of a block
        // or two keeps it (the sprint flag stays on in the air); stopping, a hit, a real fall
        // (over ~3 blocks: falling fast, or the landing ahead is a hard one), water, ladders end it.
        if (swordInHand && player.isSprinting() && ground && horizontalSpeed > 0.10f) swordRun = true;
        if (!swordInHand || player.hurtTime > 0 || blocked || swimming || player.isInWater() || climbing || crawling
                || sitting || (ground && (!player.isSprinting() || horizontalSpeed < 0.06f)) || -vertical > 0.68f
                || player.fallDistance > 3.0f || hardDropW.v > 0.3f || (player.isUsingItem() && !player.isBlocking())) {
            swordRun = false;
        }
        swordRunW.to(swordRun ? 1f : 0f, dt);
        twoHandW.to(Cfg.twoHanded && weapon && offItem.isEmpty() ? 1f : 0f, dt);
        boolean crossInHand = mainItem.getItem() instanceof CrossbowItem || offItem.getItem() instanceof CrossbowItem;
        boolean charged = CrossbowItem.isCharged(mainItem) || CrossbowItem.isCharged(offItem);
        crossCarryW.to(crossInHand && !charged && !player.isUsingItem() && !blocked ? 1f : 0f, dt);
        crossAimW.to(charged && !blocked ? 1f : 0f, dt);
        sleepW.to(player.isSleeping() ? 1f : 0f, dt);
        updateSleepStyle();
        updateScreenPose(blocked || swimming || crawling || sitting || climbing || !ground, dt);

        boolean spearUse = use == UseAnim.SPEAR;
        if (wasSpearUse && !spearUse && usePull >= 0.45f) spearThrowStart = time;
        wasSpearUse = spearUse;
        boolean drawing = use == UseAnim.BOW;
        if (drawing && !wasDrawingBow) bowStyle ^= 1;
        wasDrawingBow = drawing;

        // A minute of nothing at all: dozing off on your feet, whatever is in hand.
        boolean restful = ground && !blocked && !swimming && !crawling && !sitting && !player.isInWater() && !climbing
                && !player.isUsingItem() && !player.isCrouching() && horizontalSpeed < 0.01f && player.hurtTime == 0
                && !player.swinging && time - slashStart > 1f && screenPose == SCREEN_NONE && emote < 0;
        if (!restful) activeAt = time;
        boolean doze = Cfg.idleActions && time - activeAt > DOZE_AFTER;
        if (doze && !ownDoze) dozeStart = time;
        ownDoze = doze;
        dozeW.to(doze ? 1f : 0f, dt);
        updateIdle(ground && !blocked && !swimming && !crawling && !sitting && !player.isInWater() && !climbing && !player.isUsingItem()
                && !player.isCrouching() && horizontalSpeed < 0.01f && time - slashStart > 2f
                && time - swapStart > 1f && player.hurtTime == 0 && readyW.v < 0.05f
                && time - drawStart > DRAW_SECONDS + 1f && time - offDrawStart > DRAW_SECONDS + 1f
                && screenPose == SCREEN_NONE && !dozing && foreignHead < 0.5f, dt);
        updateEmote(dt);

        boolean fists = mainItem.isEmpty();
        guardW.to(fists && time - lastPunchTime < 1.3f && !blocked ? 1f : 0f, dt);

        // Eating and drinking are ours (food goes to the mouth); everything else in use is vanilla's.
        boolean eating = use == UseAnim.EAT || use == UseAnim.DRINK;
        // A crossbow in hand: both hands stay on it (CombatModule), nothing else swings the arms.
        boolean vanillaArms = (player.isUsingItem() && !eating) || charged || crossInHand;
        float free = vanillaArms ? 0f : 1f;
        armsFree.to(free * (1f - 0.9f * castW.v), dt);

        land.step(dt);
        hurt.step(dt);
        release.step(dt);
        brake.step(dt);
        shieldHit.step(dt);
        hitFront.step(dt);
        hitBack.step(dt);
    }

    public static final int IDLE_STRETCH = 0, IDLE_SCRATCH = 1, IDLE_SHADOWBOX = 2, IDLE_LOOK = 3,
            IDLE_YAWN = 4, IDLE_HANDS = 5, IDLE_NECK = 6, IDLE_IMPATIENT = 7, IDLE_KICK = 8, IDLE_SQUATS = 9,
            IDLE_COUNT = 10,
            // Fidgets with whatever is in the main hand.
            IDLE_ITEM_SPIN = 10, IDLE_ITEM_INSPECT = 11, IDLE_ITEM_TOSS = 12, IDLE_ITEM_PRACTICE = 13,
            IDLE_ITEM_SHOULDER = 14, IDLE_ITEM_LEAN = 15, IDLE_FLINT = 16, IDLE_BUCKET = 17, IDLE_ROD = 18,
            IDLE_LANTERN = 19, IDLE_BLOCK = 20,
            // Two swords: drawn from behind the back into a wary stance; twirled one after the other.
            IDLE_DUAL_DRAW = 21, IDLE_DUAL_SPIN = 22;
    /** With a one-handed sword in each hand (sorted ascending). */
    private static final int[] DUAL_SWORD_IDLES = {IDLE_DUAL_DRAW, IDLE_DUAL_SPIN};
    /**
     * The empty-hand fidgets that are played: only the polished ones that keep the hands off the
     * body (no hand through the head or chest, nothing silly).
     */
    private static final int[] EMPTY_HAND_IDLES = {IDLE_STRETCH, IDLE_LOOK, IDLE_NECK, IDLE_KICK};
    /** Generic fidgets that leave the main hand alone (fine with something in it). */
    private static final int[] HANDS_BUSY_IDLES = {IDLE_LOOK, IDLE_NECK, IDLE_KICK};

    // ---- multiplayer sync (see ClientSync / KinetrixNet)
    /** Keys this player holds, as their own client reported them (AnimPacket.BIT_*). */
    private int remoteBits;
    /** Their client runs Kinetrix and talks to us through the server. */
    private boolean remoteSynced;
    /** A special landing their client chose, waiting for them to touch down on our screen. */
    private int pendingLand = -1;
    private float pendingLandTime = -100f;

    private boolean isLocal() {
        return player instanceof net.minecraft.client.player.LocalPlayer;
    }

    /** Another player whose own client tells us what they do (instead of us guessing). */
    public boolean remoteDriven() {
        return remoteSynced && !isLocal();
    }

    private boolean remoteBit(int bit) {
        return remoteDriven() && (remoteBits & bit) != 0;
    }

    /**
     * One of our own full-body moves is playing (an emote, a roll, a sword landing): other
     * mods' player animations wait until it is over.
     */
    public boolean fullBodyMove() {
        if (emote >= 0) return true;
        float t = since(landStart);
        if (t < 0f) return false;
        return switch (landKind) {
            case LAND_ROLL, LAND_BACKROLL -> t < 0.45f;
            case LAND_SWORD -> t < SWORD_LAND_SECONDS && !(landRelease >= landStart && since(landRelease) > 0.15f);
            default -> false;
        };
    }

    /** Sync data about this (other) player, from their client through the server. */
    public void onRemote(byte type, int value) {
        if (isLocal()) return;
        switch (type) {
            case dev.mcreload.anim.net.AnimPacket.STATE -> {
                remoteBits = value;
                remoteSynced = true;
            }
            case dev.mcreload.anim.net.AnimPacket.EMOTE -> {
                remoteSynced = true;
                if (value >= 0 && value < EMOTE_COUNT) startEmote(value);
                else emote = -1;
            }
            case dev.mcreload.anim.net.AnimPacket.IDLE -> {
                remoteSynced = true;
                if (value >= 0) {
                    idleAction = lastIdleAction = value;
                    idleActionStart = time;
                } else {
                    idleAction = -1;
                }
            }
            case dev.mcreload.anim.net.AnimPacket.SIT -> {
                remoteSynced = true;
                remoteSit = value;
            }
            case dev.mcreload.anim.net.AnimPacket.SLEEP -> {
                remoteSynced = true;
                remoteSleep = value;
                if (player.isSleeping()) sleepStyle = Math.floorMod(value, SLEEP_STYLES);
            }
            case dev.mcreload.anim.net.AnimPacket.LAND -> {
                remoteSynced = true;
                pendingLand = value;
                pendingLandTime = time;
                // Arrived after they already touched down here: roll now.
                if (player.onGround() && groundTicks > 0 && groundTicks < 12) takePendingLand();
            }
            default -> {}
        }
    }

    private boolean takePendingLand() {
        if (pendingLand < 0 || time - pendingLandTime > 2f) {
            pendingLand = -1;
            return false;
        }
        landKind = pendingLand;
        landStart = time;
        if (landKind == LAND_BACKROLL) {
            backRollFrom = backLie;
            chillW.v = 0f;
        }
        pendingLand = -1;
        return true;
    }

    /**
     * A new way of sleeping every time you lie down: your own client picks one (never the same
     * twice running) and tells the others; a player whose client does not say gets one worked
     * out from who they are and where the bed is, the same on every screen.
     */
    private void updateSleepStyle() {
        boolean sleeping = player.isSleeping();
        if (sleeping && !wasSleeping) {
            if (isLocal()) {
                if (sleepStyle < 0) {
                    sleepStyle = rng.nextInt(SLEEP_STYLES);
                } else {
                    int pick = rng.nextInt(SLEEP_STYLES - 1);
                    sleepStyle = pick >= sleepStyle ? pick + 1 : pick;
                }
                dev.mcreload.anim.client.net.ClientSync.event(player, dev.mcreload.anim.net.AnimPacket.SLEEP, sleepStyle);
            } else if (remoteSleep >= 0) {
                sleepStyle = Math.floorMod(remoteSleep, SLEEP_STYLES);
            } else {
                long h = player.getUUID().getLeastSignificantBits()
                        ^ player.getSleepingPos().map(net.minecraft.core.BlockPos::asLong).orElse(0L) * 0x9E3779B97F4A7C15L;
                sleepStyle = (int) Math.floorMod(h ^ (h >>> 29), (long) SLEEP_STYLES);
            }
        }
        wasSleeping = sleeping;
    }

    /**
     * Once per frame after the pose was drawn: how much another mod moves the head, and how far
     * down it hangs. Standing still with the head hung by another mod for over a second, a snore
     * heard at the player, or an animation of another mod named like a nap: dozing.
     */
    public void updateDoze(float foreignHeadW, float headPitch) {
        foreignHead = foreignHeadW;
        boolean still = groundW.v > 0.5f && horizontalSpeed < 0.02f && !player.isSleeping() && !player.isPassenger();
        dozeSeen = still && foreignHeadW > 0.5f && headPitch > 0.45f ? dozeSeen + dt : 0f;
        dozing = ownDoze || still && (dozeSeen > 1.2f || time < snoreUntil || time - dozeAnimAt < 0.5f);
    }

    /**
     * After the player's tick, while Iron's Spells casts (or plays one of its animations): face the
     * way you look. Iron's turns the arms by how far the head looks off the body (up to 50 degrees
     * standing still), so with the chest left where it was they swung out sideways and the far arm
     * went through the chest; with the body turned to the look they go straight forward, to where
     * the spell flies. The same thing vanilla does to the body during a swing, eased over a few ticks.
     */
    public void faceLookWhileCasting(boolean ironsAnimating) {
        if (!Cfg.ironsSpells || !(cast.casting() || ironsAnimating)) return;
        if (player.isPassenger() || player.isSleeping() || player.isFallFlying() || player.isVisuallySwimming()) return;
        player.yBodyRot += Mth.wrapDegrees(player.getYHeadRot() - player.yBodyRot) * 0.5f;
    }

    /** Fall asleep on the spot (self-test). */
    public void forceDoze() {
        activeAt = time - DOZE_AFTER - 0.1f;
    }

    /** A snore was heard where this player stands. */
    public void onSnore() {
        snoreUntil = time + 3f;
    }

    /** Another mod is playing a nap / AFK animation on this player right now. */
    public void onDozeAnimation() {
        dozeAnimAt = time;
    }

    /** Which menu pose: your own open menu, or another player's as their client reports it. */
    private void updateScreenPose(boolean busy, float dt) {
        int pose;
        if (isLocal()) pose = dev.mcreload.anim.client.ScreenPoses.of(net.minecraft.client.Minecraft.getInstance().screen);
        else pose = remoteDriven() ? dev.mcreload.anim.net.AnimPacket.screenPose(remoteBits) : SCREEN_NONE;
        if (busy || !Cfg.idle) pose = SCREEN_NONE;
        if (pose != screenPose) {
            screenPose = pose;
            screenStart = time;
        }
        thinkW.to(pose == SCREEN_THINK ? 1f : 0f, dt);
        rummageW.to(pose == SCREEN_RUMMAGE ? 1f : 0f, dt);
        tradeW.to(pose == SCREEN_TRADE ? 1f : 0f, dt);
    }

    /** Your own landing choice, for the others to see. */
    private void sendLand() {
        if (isLocal()) dev.mcreload.anim.client.net.ClientSync.event(player, dev.mcreload.anim.net.AnimPacket.LAND, landKind);
    }

    /** Blocks crawled per crawl cycle (a pull with each arm). */
    private static final float CRAWL_CYCLE_BLOCKS = 1.0f;

    /** Sitting: your own (key X), another player's (from their client), or Personality's. */
    private void updateSit(boolean blocked) {
        int style;
        if (isLocal()) style = dev.mcreload.anim.client.PostureControl.localSit();
        else style = remoteSit;
        personalitySit = style == 0 && dev.mcreload.anim.compat.PersonalityCompat.isSitting(player);
        if (personalitySit) style = 1 + (int) (seed * 7f) % dev.mcreload.anim.net.Posture.SIT_POSES;
        boolean now = style > 0 && !blocked && Cfg.postures;
        if (now && !sitting) {
            sitStyle = style - 1;
            sitStart = time;
        }
        sitting = now;
    }

    // ---- emotes (played from the emote menu; client side)
    public static final int EMOTE_DANCE = 0, EMOTE_WAVE = 1, EMOTE_CLAP = 2, EMOTE_FACEPALM = 3, EMOTE_SHRUG = 4,
            EMOTE_SALUTE = 5, EMOTE_BOW = 6, EMOTE_SIT = 7, EMOTE_THUMBS = 8, EMOTE_VICTORY = 9, EMOTE_POINT = 10,
            EMOTE_LAUGH = 11, EMOTE_CRY = 12, EMOTE_FLOSS = 13, EMOTE_PUSHUPS = 14, EMOTE_RUNNING_MAN = 15,
            EMOTE_ROBOT = 16, EMOTE_GUITAR = 17, EMOTE_FLEX = 18, EMOTE_MEDITATE = 19, EMOTE_COUNT = 20;
    public static final String[] EMOTE_KEYS = {"dance", "wave", "clap", "facepalm", "shrug", "salute", "bow", "sit",
            "thumbs", "victory", "point", "laugh", "cry", "floss", "pushups", "running_man", "robot", "guitar",
            "flex", "meditate"};
    public int emote = -1;
    public float emoteStart = -100f;
    public final Smooth emoteW = new Smooth(6f);
    private int emoteMovingTicks;

    /** Seconds; 0 = loops until you move. */
    public static float emoteDuration(int e) {
        return switch (e) {
            case EMOTE_DANCE, EMOTE_SIT, EMOTE_FLOSS, EMOTE_PUSHUPS, EMOTE_RUNNING_MAN, EMOTE_ROBOT,
                 EMOTE_GUITAR, EMOTE_MEDITATE -> 0f;
            case EMOTE_FLEX -> 4.2f;
            case EMOTE_WAVE, EMOTE_SALUTE, EMOTE_BOW -> 2.4f;
            case EMOTE_SHRUG -> 1.8f;
            case EMOTE_THUMBS, EMOTE_POINT -> 2.0f;
            case EMOTE_CRY -> 3.5f;
            default -> 2.8f;
        };
    }

    public void playEmote(int e) {
        startEmote(e);
        if (isLocal()) dev.mcreload.anim.client.net.ClientSync.event(player, dev.mcreload.anim.net.AnimPacket.EMOTE, e);
    }

    private void startEmote(int e) {
        emote = e;
        emoteStart = time;
        emoteMovingTicks = 0;
    }

    private void updateEmote(float dt) {
        if (emote >= 0) {
            float d = emoteDuration(emote);
            boolean busy = horizontalSpeed > 0.03f || player.swinging || player.isUsingItem() || player.hurtTime > 0
                    || !player.onGround() || player.isPassenger() || player.isSleeping() || player.isInWater()
                    || crawling || sitting;
            if (busy) emoteMovingTicks++; else emoteMovingTicks = 0;
            if ((d > 0f && time - emoteStart > d) || emoteMovingTicks > 2) {
                emote = -1;
                if (isLocal()) dev.mcreload.anim.client.net.ClientSync.event(player, dev.mcreload.anim.net.AnimPacket.EMOTE, -1);
            }
        }
        emoteW.to(emote >= 0 ? 1f : 0f, dt);
        if (emote >= 0) idleSince = time;
    }

    /** Weight of the current idle fidget; drops at once when the player does anything. */
    public final Smooth idleW = new Smooth(7f);

    private void updateIdle(boolean still, float dt) {
        if (!still || !Cfg.idleActions) {
            idleSince = time;
            nextIdleAt = 8f + rng.nextFloat() * 4f;
            idleW.to(0f, dt);
            if (idleW.v < 0.01f) idleAction = -1;
            return;
        }
        if (idleAction >= 0 && time - idleActionStart > idleDuration(idleAction)) {
            idleAction = -1;
            idleSince = time;
            nextIdleAt = 8f + rng.nextFloat() * 4f;
        }
        if (idleAction < 0 && emote < 0 && time - idleSince > nextIdleAt && !remoteDriven()) {
            int pick;
            ItemStack held = player.getMainHandItem();
            if (!held.isEmpty()) {
                // Something in hand: mostly play with it, now and then a fidget that leaves it be.
                // Two swords: their own pair of fidgets. A two-handed weapon: only fidgets that
                // leave the arms alone (GestureModule keeps both hands on it).
                int[] own = dualSwords ? DUAL_SWORD_IDLES : twoHandedWeapon ? HANDS_BUSY_IDLES : itemIdles(held);
                int[] pool = rng.nextFloat() < 0.75f ? own : HANDS_BUSY_IDLES;
                pick = pool[rng.nextInt(pool.length)];
                if (pick == lastIdleAction && pool.length > 1) pick = pool[(java.util.Arrays.binarySearch(pool, pick) + 1 + pool.length) % pool.length];
            } else {
                int i = rng.nextInt(EMPTY_HAND_IDLES.length);
                pick = EMPTY_HAND_IDLES[i];
                if (pick == lastIdleAction) pick = EMPTY_HAND_IDLES[(i + 1) % EMPTY_HAND_IDLES.length];
            }
            idleAction = lastIdleAction = pick;
            idleActionStart = time;
            if (isLocal()) dev.mcreload.anim.client.net.ClientSync.event(player, dev.mcreload.anim.net.AnimPacket.IDLE, pick);
        }
        idleW.to(idleAction >= 0 ? 1f : 0f, dt);
    }

    private int lastIdleAction = -1;

    /** Start a given fidget now (self-test). */
    public void forceIdle(int action) {
        idleAction = lastIdleAction = action;
        idleActionStart = time;
        idleW.v = 1f;
    }

    /** Which fidgets suit the item (sorted ascending). */
    public static int[] itemIdles(ItemStack stack) {
        Item it = stack.getItem();
        if (it instanceof net.minecraft.world.item.SwordItem)
            return new int[]{IDLE_ITEM_SPIN, IDLE_ITEM_INSPECT, IDLE_ITEM_PRACTICE};
        if (it instanceof AxeItem) return new int[]{IDLE_ITEM_INSPECT, IDLE_ITEM_PRACTICE, IDLE_ITEM_SHOULDER};
        if (it instanceof PickaxeItem) return new int[]{IDLE_ITEM_PRACTICE, IDLE_ITEM_SHOULDER};
        if (it instanceof ShovelItem || it instanceof HoeItem) return new int[]{IDLE_ITEM_SPIN, IDLE_ITEM_SHOULDER, IDLE_ITEM_LEAN};
        if (it instanceof net.minecraft.world.item.TridentItem) return new int[]{IDLE_ITEM_SPIN, IDLE_ITEM_PRACTICE, IDLE_ITEM_LEAN};
        if (it instanceof net.minecraft.world.item.FlintAndSteelItem) return new int[]{IDLE_FLINT};
        if (it instanceof net.minecraft.world.item.BucketItem || it instanceof net.minecraft.world.item.MilkBucketItem
                || it instanceof net.minecraft.world.item.SolidBucketItem) return new int[]{IDLE_BUCKET};
        if (it instanceof net.minecraft.world.item.FishingRodItem) return new int[]{IDLE_ITEM_SPIN, IDLE_ROD};
        if (it instanceof net.minecraft.world.item.BlockItem bi) {
            if (bi.getBlock() instanceof net.minecraft.world.level.block.LanternBlock) return new int[]{IDLE_LANTERN};
            return new int[]{IDLE_BLOCK};
        }
        if (Weapons.isMeleeWeapon(stack)) return new int[]{IDLE_ITEM_SPIN, IDLE_ITEM_INSPECT, IDLE_ITEM_PRACTICE};
        return new int[]{IDLE_ITEM_INSPECT};
    }

    public static float idleDuration(int action) {
        return switch (action) {
            case IDLE_ITEM_SPIN, IDLE_ITEM_PRACTICE, IDLE_ROD -> 3.0f;
            case IDLE_DUAL_DRAW -> 4.6f;
            case IDLE_DUAL_SPIN -> 3.6f;
            case IDLE_ITEM_INSPECT -> 3.6f;
            case IDLE_ITEM_TOSS -> 2.4f;
            case IDLE_ITEM_SHOULDER -> 4.0f;
            case IDLE_ITEM_LEAN -> 4.5f;
            case IDLE_FLINT -> 2.6f;
            case IDLE_BUCKET, IDLE_LANTERN -> 3.2f;
            case IDLE_BLOCK -> 3.4f;
            case IDLE_STRETCH -> 4.0f;
            case IDLE_SCRATCH, IDLE_IMPATIENT, IDLE_SQUATS -> 3.5f;
            case IDLE_SHADOWBOX -> 3.2f;
            case IDLE_NECK, IDLE_KICK -> 2.6f;
            default -> 3.0f;
        };
    }

    /** Height of the feet above the first solid surface below (up to 3 blocks), or -1. */
    private double groundGap() {
        return groundGap(4, false);
    }

    /**
     * Height of the feet above the first solid block top below, within {@code depth} blocks; -1 if
     * none, or (with {@code fluidStops}) if water or lava comes first.
     */
    private double groundGap(int depth, boolean fluidStops) {
        net.minecraft.world.level.Level level = player.level();
        double y = player.getY();
        net.minecraft.core.BlockPos.MutableBlockPos pos = player.blockPosition().mutable();
        for (int i = 0; i < depth; i++) {
            if (fluidStops && !level.getFluidState(pos).isEmpty()) return -1;
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                double top = pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
                if (top <= y + 1e-3) return y - top;
            }
            pos.move(net.minecraft.core.Direction.DOWN);
        }
        return -1;
    }

    /**
     * The speed (blocks per tick, downwards) this fall will hit the ground at, as the landing
     * measures it (the fastest tick of the fall), dropping straight down from here with vanilla
     * gravity and drag onto the ground below. With no ground within 32 blocks (or water first: no
     * hard landing there), the speed so far. Also counts the ticks until touchdown (#impactIn).
     */
    private float predictedImpact() {
        double v = -vy, hit = Math.max(0.0, -minAirVy);
        double gap = groundGap(32, true);
        impactTicks = 99;
        if (gap < 0) return (float) Math.max(hit, v);
        double d = 0;
        for (int i = 0; i < 200; i++) {
            v = (v + 0.08) * 0.98;
            if (d + v >= gap) {
                impactTicks = i + 1;
                return (float) Math.max(hit, gap - d);
            }
            d += v;
            hit = Math.max(hit, v);
        }
        return (float) hit;
    }

    private boolean isLocalPressingBack() {
        if (player instanceof net.minecraft.client.player.LocalPlayer lp) return lp.input != null && lp.input.forwardImpulse < -0.1f;
        return remoteBit(dev.mcreload.anim.net.AnimPacket.BIT_BACK);
    }

    /** Distance from the shoulders (1.3 blocks up) down to... i.e. how deep the shoulders are under the surface. */
    private float surfaceDepth() {
        net.minecraft.world.level.Level level = player.level();
        net.minecraft.core.BlockPos.MutableBlockPos pos = player.blockPosition().mutable();
        double top = -1;
        for (int i = 0; i < 4; i++) {
            net.minecraft.world.level.material.FluidState fs = level.getFluidState(pos);
            if (fs.isEmpty() || !fs.is(net.minecraft.tags.FluidTags.WATER)) break;
            top = pos.getY() + fs.getHeight(level, pos);
            pos.move(net.minecraft.core.Direction.UP);
        }
        if (top < 0) return 0f;
        return (float) (top - (player.getY() + 1.3));
    }

    /** Height from the feet down to a water surface below (nothing solid in between), or -1. */
    private float waterGap() {
        net.minecraft.world.level.Level level = player.level();
        net.minecraft.core.BlockPos.MutableBlockPos pos = player.blockPosition().mutable();
        for (int i = 0; i < 16; i++) {
            net.minecraft.world.level.material.FluidState fs = level.getFluidState(pos);
            if (!fs.isEmpty()) {
                if (!fs.is(net.minecraft.tags.FluidTags.WATER)) return -1f;
                return (float) Math.max(0.0, player.getY() - (pos.getY() + fs.getHeight(level, pos)));
            }
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return -1f;
            pos.move(net.minecraft.core.Direction.DOWN);
        }
        return -1f;
    }

    public AbstractClientPlayer playerRef() {
        return player;
    }

    /** Vanilla is still fading out of the swimming pose (left the water or stopped swimming). */
    public boolean swimFading() {
        return player.tickCount - lastVisSwimTick < 20;
    }

    public boolean wasInWaterRecently() {
        return player.tickCount - lastWaterTick < 20;
    }

    /** Water within a few blocks below the feet, with nothing solid in between. */
    private boolean waterBelow() {
        net.minecraft.core.BlockPos.MutableBlockPos pos = player.blockPosition().mutable();
        for (int i = 0; i < 14; i++) {
            if (!player.level().getFluidState(pos).isEmpty()) return player.level().getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER);
            if (!player.level().getBlockState(pos).getCollisionShape(player.level(), pos).isEmpty()) return false;
            pos.move(net.minecraft.core.Direction.DOWN);
        }
        return false;
    }

    /** Forward input held (your own player) or clearly running on (others). */
    /** Trying to walk (any direction) or jump: from the keys for us, from the speed for others. */
    private boolean wantsToMove() {
        if (player instanceof net.minecraft.client.player.LocalPlayer lp && lp.input != null) {
            return Math.abs(lp.input.forwardImpulse) > 0.1f || Math.abs(lp.input.leftImpulse) > 0.1f || lp.input.jumping;
        }
        return horizontalSpeed > 0.05f;
    }

    private boolean movingForward() {
        if (player instanceof net.minecraft.client.player.LocalPlayer lp && lp.input != null) {
            return lp.input.forwardImpulse > 0.1f;
        }
        if (remoteDriven()) return remoteBit(dev.mcreload.anim.net.AnimPacket.BIT_FORWARD);
        return Math.sqrt(vx * vx + vz * vz) > 0.12;
    }

    // ---- events from sounds (see SoundCues)

    public void onShieldBlock() {
        shieldHit.impulse(1f);
    }

    public void onExplosion(double distance) {
        if (distance > 10.0) return;
        explodeStart = time;
        explodeStrength = (float) Mth.clamp(1.3 - distance / 10.0, 0.6, 1.0);
    }

    public void onTotem() {
        totemStart = time;
    }

    /**
     * Turn the main-hand item to exactly {@code v} right now: this frame is drawn with it (the
     * model reads the turn after the modules run) and the next frame keeps it unless set again.
     */
    public void tiltNow(float v) {
        itemTiltDirect = v;
        itemTilt.v = v;
    }

    /** The same for the item in the other hand. */
    public void offTiltNow(float v) {
        itemTiltOffDirect = v;
        itemTiltOff.v = v;
    }

    /** The turn of an item in the hand: exactly as set (twirls), or smoothed toward the target. */
    private static void stepTilt(Smooth tilt, float target, float direct, float dt) {
        if (!Float.isNaN(direct)) {
            tilt.v = direct;
            return;
        }
        float err = tilt.v - target;
        if (Math.abs(err) > Mth.PI) tilt.v -= Mth.TWO_PI * Math.round(err / Mth.TWO_PI);
        tilt.to(target, dt);
    }

    /** Seconds since an event started (large if it never did). */
    public float since(float start) {
        return time - start;
    }

    private void startSwing() {
        HumanoidArm arm = player.swingingArm == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        ItemStack held = player.getItemInHand(player.swingingArm);
        // Any swing of a sword (ours, chained or not, or one Better Combat animates) keeps the guard up.
        if (Weapons.isSword(held)) attackTime = time;
        int kind = classify(held);
        float progress = slashProgress();

        // Mining re-triggers the vanilla swing every few ticks: let each strike finish, then loop.
        if (isLooping(kind) && kind == slashKind && progress < 0.92f) return;
        // Weapons: allow chaining once the hit has landed.
        if (isWeapon(kind) && isWeapon(slashKind) && progress < 0.55f) return;
        // Clicking away with an item: each swing gets through its strike before the next one.
        if (kind == SWAT && slashKind == SWAT && progress < 0.45f) return;

        // Better Combat owns the attacks of its weapons: its animation plays, ours stays out of it.
        if ((isSlash(kind) || kind == AXE_OVER || kind == STAB) && dev.mcreload.anim.compat.BetterCombatCompat.handles(held)) {
            slashKind = FOREIGN_ATTACK;
            slashStart = time;
            slashDuration = 0.6f;
            slashChained = false;
            return;
        }
        slashChained = progress < 1.3f && ((isWeapon(kind) && isWeapon(slashKind)) || (isLooping(kind) && kind == slashKind)
                || (kind == SWAT && slashKind == SWAT));
        slashSide = arm == HumanoidArm.RIGHT ? 1f : -1f;
        float cooldown = player.getCurrentItemAttackStrengthDelay() / 20f;
        if (isSlash(kind)) {
            boolean chained = isSlash(slashKind) && time - slashStart < slashDuration + 0.6f;
            combo = chained ? (combo + 1) % SWORD_COMBO.length : 0;
            slashKind = SWORD_COMBO[combo];
            // Slower weapons swing slower: match the attack cooldown (sword ~0.6 s).
            slashDuration = Mth.clamp(cooldown * 0.95f, 0.5f, 1.0f) * (slashKind == SPIN ? 1.25f : 1f);
        } else if (isAxe(kind)) {
            boolean chained = isAxe(slashKind) && time - slashStart < slashDuration + 0.8f;
            slashKind = chained && slashKind == AXE_OVER ? AXE_SWEEP : AXE_OVER;
            slashDuration = Mth.clamp(cooldown * 0.95f, 0.75f, 1.1f);
        } else if (kind == PUNCH) {
            punchSide = time - lastPunchTime < 1.2f ? -punchSide : 1f;
            lastPunchTime = time;
            slashKind = PUNCH;
            slashDuration = 0.32f;
        } else if (kind == THROW) {
            throwSide = time - lastThrowTime < 2.0f ? -throwSide : 1f;
            lastThrowTime = time;
            slashKind = THROW;
            slashDuration = 0.65f;
        } else if (kind == FIST_BLOCK) {
            // Digging with bare hands: one fist after the other (only the free hand if the other
            // one holds something, like a shield or a totem).
            boolean otherFree = player.getItemInHand(player.swingingArm == InteractionHand.MAIN_HAND
                    ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND).isEmpty();
            fistSide = otherFree ? -fistSide : 1f;
            slashSide *= fistSide;
            slashKind = kind;
            slashDuration = 0.42f;
            slashChained = false;
        } else if (kind == PLACE) {
            slashKind = kind;
            slashDuration = 0.38f;
            slashChained = false;
        } else if (kind == STAB) {
            slashKind = kind;
            slashDuration = Mth.clamp(cooldown * 0.9f, 0.5f, 0.9f);
            slashChained = false;
        } else if (kind == SWAT) {
            slashKind = kind;
            slashDuration = 0.42f;
        } else if (kind == FISH) {
            // Casting out, or reeling in if the bobber is already out.
            if (player.fishing == null) fishCastStart = time; else fishReelStart = time;
            slashKind = kind;
            slashDuration = 0.5f;
            slashChained = false;
        } else {
            slashKind = kind;
            slashDuration = isLooping(kind) ? 0.40f : 0.30f;
        }
        slashStart = time;
    }

    private int classify(ItemStack held) {
        if (isLocalDropping()) return THROW;
        if (held.getItem() instanceof net.minecraft.world.item.FishingRodItem) return FISH;
        if (held.getItem() instanceof net.minecraft.world.item.TridentItem && !isLocalUsing()) return STAB;
        // Right-click on a block (placing, opening, using an item on it): a press of the hand.
        if (isLocalUsing() || (!isLocal() && !remoteDriven() && held.getItem() instanceof net.minecraft.world.item.BlockItem)) {
            return PLACE;
        }
        if (held.isEmpty()) return targetingBlock() ? FIST_BLOCK : PUNCH;
        boolean blade = Weapons.isMeleeWeapon(held);
        boolean tool = held.getItem() instanceof net.minecraft.world.item.DiggerItem;
        // Anything else in the hand (a block, a stick...) hitting a block: a one-handed knock.
        if (!blade && !tool && targetingBlock()) return PICK;
        Item item = held.getItem();
        if (item instanceof PickaxeItem) return PICK;
        if (item instanceof ShovelItem) return SHOVEL;
        if (item instanceof HoeItem) return HOE;
        if (item instanceof AxeItem) return targetingBlock() ? CHOP : AXE_OVER;
        if (Weapons.isMeleeWeapon(held)) return SLASH_H;
        // Anything else (a block, armour, food, a part from a mod...) swung at the air or a mob:
        // our own one-handed swat, never vanilla's arm swing.
        return SWAT;
    }

    private boolean isLocalUsing() {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) return mc.options.keyUse.isDown();
        return remoteBit(dev.mcreload.anim.net.AnimPacket.BIT_USE);
    }

    /** Vanilla swings the arm when you drop an item (Q): detect it for your own player. */
    private boolean isLocalDropping() {
        Minecraft mc = Minecraft.getInstance();
        return player == mc.player && mc.options.keyDrop.isDown();
    }

    /** For you: are you hitting a block (chopping a tree) rather than a mob? Others: assume a mob. */
    private boolean targetingBlock() {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) return mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
        return remoteBit(dev.mcreload.anim.net.AnimPacket.BIT_TARGET_BLOCK);
    }

    /** Sword combo strikes. */
    public static boolean isSlash(int kind) {
        return kind >= SLASH_H && kind <= SPIN;
    }

    public static boolean isAxe(int kind) {
        return kind == AXE_OVER || kind == AXE_SWEEP;
    }

    /** Any keyframed weapon strike (sword or axe): these leave blade trails. */
    public static boolean isWeapon(int kind) {
        return isSlash(kind) || isAxe(kind);
    }

    /** Tool strikes that loop while you keep mining. */
    public static boolean isTool(int kind) {
        return kind >= PICK && kind <= CHOP;
    }

    public static boolean isLooping(int kind) {
        return isTool(kind) || kind == FIST_BLOCK;
    }

    /** 0..1 over the swing, keeps growing afterwards (used to blend out). */
    public float slashProgress() {
        return (time - slashStart) / slashDuration;
    }

    /** Seconds since the last take-off (large if not airborne after a jump). */
    public float airTime(float pt) {
        return (player.tickCount - takeoffTick + pt) / 20f;
    }

    public float moveF() {
        return move.v;
    }

    public AbstractClientPlayer player() {
        return player;
    }
}
