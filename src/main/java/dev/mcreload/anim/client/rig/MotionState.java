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
    /** Held item tipped forward in the hand (radians), smoothed; modules raise the target. */
    public final Smooth itemTilt = new Smooth(16f);
    public float itemTiltTarget;
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
    public final Smooth blockW = new Smooth(10f), bowW = new Smooth(9f), crossbowW = new Smooth(9f), spearW = new Smooth(9f);
    public final Smooth eatW = new Smooth(8f), drinkW = new Smooth(8f), hornW = new Smooth(8f);
    public final Smooth castW = new Smooth(6f);
    public final Smooth rideW = new Smooth(6f), flyW = new Smooth(5f), elytraW = new Smooth(6f);
    public final Smooth guardW = new Smooth(6f);
    public final Smooth sleepW = new Smooth(4f);
    /** Holding a melee weapon and ready to fight (standing / walking, recently attacked). */
    public final Smooth readyW = new Smooth(4f);
    /** Weapon in the main hand and nothing in the other: two-handed (samurai) grip. */
    public final Smooth twoHandW = new Smooth(5f);
    public final Smooth crossCarryW = new Smooth(6f), crossAimW = new Smooth(8f);

    // ---- one-shot reactions
    public final Spring brake = new Spring(40f, 8.5f);
    public final Spring shieldHit = new Spring(160f, 14f);
    public final Spring hitFront = new Spring(120f, 12f);
    public final Spring hitBack = new Spring(120f, 12f);
    public static final int LAND_NONE = 0, LAND_ROLL = 1, LAND_SWORD = 2;
    public int landKind = LAND_NONE;
    public float landStart = -100f;
    public float explodeStart = -100f, explodeStrength;
    public float totemStart = -100f;
    private int hurtPending;
    private float prevTickSpeed;
    private boolean wasSprinting;
    private int lastGlideTick = -1000;
    private boolean wasInWater;
    private int lastWaterTick = -1000;
    private boolean rollEarly;
    private int lastSprintTick = -1000;
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
            PICK = 6, SHOVEL = 7, HOE = 8, CHOP = 9, AXE_OVER = 10, AXE_SWEEP = 11, THROW = 12, FIST_BLOCK = 13, PLACE = 14;
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
    public final Smooth chillW = new Smooth(4f);
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
        }
        prevYawRate = yawRate;
        yawRate = Mth.wrapDegrees(player.yBodyRot - player.yBodyRotO);

        boolean ground = player.onGround();
        boolean special = player.isInWater() || player.isFallFlying() || player.onClimbable()
                || player.isPassenger() || player.getAbilities().flying;
        float tickSpeed = (float) Math.sqrt(vx * vx + vz * vz);
        if (ground && wasOnGround && !special && player.tickCount - lastSprintTick <= 3 && prevTickSpeed > 0.2f && tickSpeed < prevTickSpeed * 0.6f && Cfg.locomotion) {
            // Sprint released: skid to a stop.
            if (time - brakeStart > 0.6f) brakeStart = time;
        }
        prevTickSpeed = tickSpeed;
        wasSprinting = player.isSprinting();
        if (wasSprinting) lastSprintTick = player.tickCount;
        if (player.isFallFlying()) lastGlideTick = player.tickCount;

        // Diving into water from a height: streamlined dolphin entry; never "land" on the bottom.
        boolean inWater = player.isInWater();
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

        if (!ground) {
            if (wasOnGround && vy > 0.05 && !special) {
                // Take-off: switch the leading leg every jump.
                jumpLeg = !jumpLeg;
                takeoffTick = player.tickCount;
                takeoffRun = Mth.clamp((float) Math.sqrt(vx * vx + vz * vz) / 0.22f, 0f, 1f);
            }
            airTicks++;
            minAirVy = Math.min(minAirVy, (float) vy);
            // About to hit the ground hard while running on: start the roll at the moment of
            // touchdown instead of a tick or two after it.
            if (!special && !rollEarly && Cfg.landing && -minAirVy > 0.62f && movingForward()
                    && !(Weapons.isMeleeWeapon(player.getMainHandItem()) && player.getOffhandItem().isEmpty())) {
                double gap = groundGap();
                if (gap >= 0 && gap < -vy * 1.1) {
                    landKind = LAND_ROLL;
                    landStart = time;
                    rollEarly = true;
                }
            }
        } else {
            if (!wasOnGround && player.tickCount - lastGlideTick < 4) {
                // Coming in on an elytra: just absorb it.
                if (Cfg.landing) land.impulse(0.8f);
            } else if (rollEarly) {
                // Already rolling.
            } else if (!wasOnGround && airTicks > 2 && !special && Cfg.landing) {
                float impact = -minAirVy;
                ItemStack main = player.getMainHandItem();
                boolean swordOnly = Weapons.isMeleeWeapon(main) && player.getOffhandItem().isEmpty();
                if (impact > 0.62f && swordOnly) {
                    // High drop with only a blade in hand: plant it in the ground.
                    landKind = LAND_SWORD;
                    landStart = time;
                    land.impulse(0.6f);
                } else if (impact > 0.62f && movingForward()) {
                    // High drop while running on: break the fall with a forward roll.
                    landKind = LAND_ROLL;
                    landStart = time;
                } else if (impact > 0.62f) {
                    // High drop standing still: just a deeper crouch.
                    land.impulse(1.25f);
                } else if (impact > 0.18f) {
                    land.impulse(Mth.clamp((impact - 0.12f) / 0.85f, 0.15f, 1.25f));
                }
            }
            airTicks = 0;
            minAirVy = 0f;
            rollEarly = false;
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
        lastMain = main.copy();
        lastOff = off.copy();

        cast = Cfg.ironsSpells ? IronsSpellsCompat.query(player) : IronsSpellsCompat.CastInfo.NONE;
        if (wasCasting && !cast.casting()) release.impulse(1f);
        wasCasting = cast.casting();
    }

    /** Called every rendered frame before the modules run. */
    public void frame(float pt) {
        float now = (player.tickCount + pt) / 20f;
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
        boolean blocked = riding || player.isSleeping() || gliding || player.isAutoSpinAttack() || creativeFlying;
        gate.to(blocked ? 0f : 1f, dt);

        boolean swimming = player.isVisuallySwimming();
        // Bobbing at the surface flips in and out of the water every few ticks: treat the last
        // moments in the water as still in it, so the pose doesn't flicker between air and water.
        boolean wet = player.isInWater() || player.tickCount - lastWaterTick < 10;
        boolean treading = wet && !ground && !swimming;
        boolean climbing = player.onClimbable() && !ground && !riding;
        boolean air = !ground && !wet && !climbing && !blocked;

        sprintW.to(player.isSprinting() && horizontalSpeed > 0.05f ? 1f : 0f, dt);
        sneakW.to(player.isCrouching() ? 1f : 0f, dt);
        groundW.to(ground ? 1f : 0f, dt);
        airW.to(air ? 1f : 0f, dt);
        fallW.to(air ? Mth.clamp((-vertical - 0.40f) / 0.5f, 0f, 1f) : 0f, dt);
        skydiveW.to(air ? Mth.clamp((-vertical - 1.4f) / 0.8f, 0f, 1f) : 0f, dt);
        swimW.to(swimming ? 1f : 0f, dt);
        waterW.to(treading ? 1f : 0f, dt);
        climbW.to(climbing ? 1f : 0f, dt);
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
        itemTilt.to(itemTiltTarget, dt);
        itemTiltTarget = 0f;
        holdingBlade = Weapons.isMeleeWeapon(player.getMainHandItem());
        speed.to(horizontalSpeed, dt);
        // Sneaking: our own step cycle, fast enough that the feet really step instead of gliding.
        if (player.isCrouching()) sneakPhase += dt * 88f * Math.min(horizontalSpeed, 0.12f);
        // Front crawl: about one stroke per second per arm, a bit faster when sprint-swimming.
        if (swimming) swimPhase += dt * (2.4f + 2.6f * Math.min(1f, horizontalSpeed / 0.2f));
        // Paddling along at the surface without sprinting.
        paddleW.to(treading ? Mth.clamp(horizontalSpeed / 0.07f, 0f, 1f) : 0f, dt);
        if (treading) paddlePhase += dt * (2.2f + 10f * Math.min(horizontalSpeed, 0.15f));
        // About to drop into water: start the dolphin dive in the air.
        boolean overWater = air && diveEligible && vertical < -0.25f && fallHeight >= 1.0f && waterBelow();
        boolean backwards = forward.v < -0.02f || isLocalPressingBack();
        chillW.to(overWater && backwards && fallHeight >= 4f ? 1f : 0f, dt);
        diveAirW.to(overWater && chillW.v < 0.5f ? 1f : 0f, dt);
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
        boolean weapon = Weapons.isMeleeWeapon(mainItem);
        boolean recentlyFought = time - slashStart < 3.0f;
        // Guard only for a few seconds after a fight, standing still; otherwise a weapon is just held.
        readyW.to(weapon && ground && !blocked && !swimming && sprintW.v < 0.5f
                && recentlyFought && time - slashStart > slashDuration * 0.9f
                && moveF() < 0.3f && !player.isUsingItem() ? 1f : 0f, dt);
        twoHandW.to(Cfg.twoHanded && weapon && offItem.isEmpty() ? 1f : 0f, dt);
        boolean crossInHand = mainItem.getItem() instanceof CrossbowItem || offItem.getItem() instanceof CrossbowItem;
        boolean charged = CrossbowItem.isCharged(mainItem) || CrossbowItem.isCharged(offItem);
        crossCarryW.to(crossInHand && !charged && !player.isUsingItem() && !blocked ? 1f : 0f, dt);
        crossAimW.to(charged && !blocked ? 1f : 0f, dt);
        sleepW.to(player.isSleeping() ? 1f : 0f, dt);

        boolean drawing = use == UseAnim.BOW;
        if (drawing && !wasDrawingBow) bowStyle ^= 1;
        wasDrawingBow = drawing;

        updateIdle(ground && !blocked && !swimming && !player.isInWater() && !climbing && !player.isUsingItem()
                && !player.isCrouching() && horizontalSpeed < 0.01f && time - slashStart > 2f
                && time - swapStart > 1f && player.hurtTime == 0, dt);
        updateEmote(dt);

        boolean fists = mainItem.isEmpty();
        guardW.to(fists && time - lastPunchTime < 1.3f && !blocked ? 1f : 0f, dt);

        // Eating and drinking are ours (food goes to the mouth); everything else in use is vanilla's.
        boolean eating = use == UseAnim.EAT || use == UseAnim.DRINK;
        boolean vanillaArms = (player.isUsingItem() && !eating) || charged;
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
            IDLE_COUNT = 10;

    // ---- emotes (played from the emote menu; client side)
    public static final int EMOTE_DANCE = 0, EMOTE_WAVE = 1, EMOTE_CLAP = 2, EMOTE_FACEPALM = 3, EMOTE_SHRUG = 4,
            EMOTE_SALUTE = 5, EMOTE_BOW = 6, EMOTE_SIT = 7, EMOTE_THUMBS = 8, EMOTE_VICTORY = 9, EMOTE_POINT = 10,
            EMOTE_LAUGH = 11, EMOTE_CRY = 12, EMOTE_FLOSS = 13, EMOTE_PUSHUPS = 14, EMOTE_COUNT = 15;
    public static final String[] EMOTE_KEYS = {"dance", "wave", "clap", "facepalm", "shrug", "salute", "bow", "sit",
            "thumbs", "victory", "point", "laugh", "cry", "floss", "pushups"};
    public int emote = -1;
    public float emoteStart = -100f;
    public final Smooth emoteW = new Smooth(6f);
    private int emoteMovingTicks;

    /** Seconds; 0 = loops until you move. */
    public static float emoteDuration(int e) {
        return switch (e) {
            case EMOTE_DANCE, EMOTE_SIT, EMOTE_FLOSS, EMOTE_PUSHUPS -> 0f;
            case EMOTE_WAVE, EMOTE_SALUTE, EMOTE_BOW -> 2.4f;
            case EMOTE_SHRUG -> 1.8f;
            case EMOTE_THUMBS, EMOTE_POINT -> 2.0f;
            case EMOTE_CRY -> 3.5f;
            default -> 2.8f;
        };
    }

    public void playEmote(int e) {
        emote = e;
        emoteStart = time;
        emoteMovingTicks = 0;
    }

    private void updateEmote(float dt) {
        if (emote >= 0) {
            float d = emoteDuration(emote);
            boolean busy = horizontalSpeed > 0.03f || player.swinging || player.isUsingItem() || player.hurtTime > 0
                    || !player.onGround() || player.isPassenger() || player.isSleeping() || player.isInWater();
            if (busy) emoteMovingTicks++; else emoteMovingTicks = 0;
            if ((d > 0f && time - emoteStart > d) || emoteMovingTicks > 2) emote = -1;
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
        if (idleAction < 0 && emote < 0 && time - idleSince > nextIdleAt) {
            int pick = rng.nextInt(IDLE_COUNT);
            if (pick == lastIdleAction) pick = (pick + 1) % IDLE_COUNT;
            idleAction = lastIdleAction = pick;
            idleActionStart = time;
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

    public static float idleDuration(int action) {
        return switch (action) {
            case IDLE_STRETCH -> 4.0f;
            case IDLE_SCRATCH, IDLE_IMPATIENT, IDLE_SQUATS -> 3.5f;
            case IDLE_SHADOWBOX -> 3.2f;
            case IDLE_NECK, IDLE_KICK -> 2.6f;
            default -> 3.0f;
        };
    }

    /** Height of the feet above the first solid surface below (up to 3 blocks), or -1. */
    private double groundGap() {
        net.minecraft.world.level.Level level = player.level();
        double y = player.getY();
        net.minecraft.core.BlockPos.MutableBlockPos pos = player.blockPosition().mutable();
        for (int i = 0; i < 4; i++) {
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                double top = pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
                if (top <= y + 1e-3) return y - top;
            }
            pos.move(net.minecraft.core.Direction.DOWN);
        }
        return -1;
    }

    private boolean isLocalPressingBack() {
        return player instanceof net.minecraft.client.player.LocalPlayer lp && lp.input != null && lp.input.forwardImpulse < -0.1f;
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
    private boolean movingForward() {
        if (player instanceof net.minecraft.client.player.LocalPlayer lp && lp.input != null) {
            return lp.input.forwardImpulse > 0.1f;
        }
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

    /** Seconds since an event started (large if it never did). */
    public float since(float start) {
        return time - start;
    }

    private void startSwing() {
        HumanoidArm arm = player.swingingArm == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        ItemStack held = player.getItemInHand(player.swingingArm);
        int kind = classify(held);
        float progress = slashProgress();

        // Mining re-triggers the vanilla swing every few ticks: let each strike finish, then loop.
        if (isLooping(kind) && kind == slashKind && progress < 0.92f) return;
        // Weapons: allow chaining once the hit has landed.
        if (isWeapon(kind) && isWeapon(slashKind) && progress < 0.55f) return;

        slashChained = progress < 1.3f && ((isWeapon(kind) && isWeapon(slashKind)) || (isLooping(kind) && kind == slashKind));
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
            // Digging with bare hands: one fist after the other.
            fistSide = -fistSide;
            slashSide *= fistSide;
            slashKind = kind;
            slashDuration = 0.42f;
            slashChained = false;
        } else if (kind == PLACE) {
            slashKind = kind;
            slashDuration = 0.38f;
            slashChained = false;
        } else {
            slashKind = kind;
            slashDuration = isLooping(kind) ? 0.40f : 0.30f;
        }
        slashStart = time;
    }

    private int classify(ItemStack held) {
        if (isLocalDropping()) return THROW;
        // Right-click on a block (placing, opening, using an item on it): a press of the hand.
        if (isLocalUsing() || (player != Minecraft.getInstance().player && held.getItem() instanceof net.minecraft.world.item.BlockItem)) {
            return PLACE;
        }
        if (held.isEmpty()) return targetingBlock() ? FIST_BLOCK : PUNCH;
        Item item = held.getItem();
        if (item instanceof PickaxeItem) return PICK;
        if (item instanceof ShovelItem) return SHOVEL;
        if (item instanceof HoeItem) return HOE;
        if (item instanceof AxeItem) return targetingBlock() ? CHOP : AXE_OVER;
        if (Weapons.isMeleeWeapon(held)) return SLASH_H;
        return TOOL;
    }

    private boolean isLocalUsing() {
        Minecraft mc = Minecraft.getInstance();
        return player == mc.player && mc.options.keyUse.isDown();
    }

    /** Vanilla swings the arm when you drop an item (Q): detect it for your own player. */
    private boolean isLocalDropping() {
        Minecraft mc = Minecraft.getInstance();
        return player == mc.player && mc.options.keyDrop.isDown();
    }

    /** For you: are you hitting a block (chopping a tree) rather than a mob? Others: assume a mob. */
    private boolean targetingBlock() {
        Minecraft mc = Minecraft.getInstance();
        return player == mc.player && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
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
