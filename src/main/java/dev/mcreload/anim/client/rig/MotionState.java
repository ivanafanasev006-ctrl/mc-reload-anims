package dev.mcreload.anim.client.rig;

import dev.mcreload.anim.client.math.Smooth;
import dev.mcreload.anim.client.math.Spring;
import dev.mcreload.anim.compat.IronsSpellsCompat;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

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

    // ---- per frame ----
    /** Seconds, continuous. */
    public float time;
    /** Seconds since the previous frame for this player (0 when re-rendered in the same frame). */
    public float dt;
    private float lastTime = Float.NaN;

    /** Interpolated vertical speed, blocks/tick (+ = up). */
    public float vertical;
    public float horizontalSpeed;

    public final Smooth forward = new Smooth(10f), strafe = new Smooth(10f), turn = new Smooth(8f), move = new Smooth(8f);
    /** 1 = normal on-foot animation allowed; fades to 0 when riding, sleeping, gliding, riptiding. */
    public final Smooth gate = new Smooth(8f);
    public final Smooth sprintW = new Smooth(6f), sneakW = new Smooth(10f), groundW = new Smooth(14f);
    public final Smooth airW = new Smooth(10f), riseW = new Smooth(9f), fallW = new Smooth(3.5f);
    public final Smooth swimW = new Smooth(5f), waterW = new Smooth(5f), climbW = new Smooth(8f);
    public final Smooth blockW = new Smooth(10f), bowW = new Smooth(9f), crossbowW = new Smooth(9f), spearW = new Smooth(9f);
    public final Smooth eatW = new Smooth(8f), drinkW = new Smooth(8f), hornW = new Smooth(8f);
    public final Smooth castW = new Smooth(6f);

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
     * (using an item, holding a loaded crossbow, swinging a tool, Iron's casting).
     */
    public final Smooth armsFree = new Smooth(10f);
    public final Smooth toolSwingW = new Smooth(14f);

    // ---- melee ----
    public static final int SLASH_H = 0, SLASH_V = 1, SLASH_BACK = 2, PUNCH = 3, TOOL = 4;
    public static final float SLASH_SECONDS = 0.42f;
    public int slashKind = -1;
    public float slashStart = -100f;
    /** +1 right arm, -1 left arm. */
    public float slashSide = 1f;
    private int combo;
    private float lastAttack;

    /** Continuous stroke phase for front crawl, radians. */
    public float swimPhase;

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
        }
        prevYawRate = yawRate;
        yawRate = Mth.wrapDegrees(player.yBodyRot - player.yBodyRotO);

        boolean ground = player.onGround();
        boolean special = player.isInWater() || player.isFallFlying() || player.onClimbable()
                || player.isPassenger() || player.getAbilities().flying;
        if (!ground) {
            airTicks++;
            minAirVy = Math.min(minAirVy, (float) vy);
        } else {
            if (!wasOnGround && airTicks > 2 && !special && Cfg.landing) {
                float impact = -minAirVy;
                if (impact > 0.18f) land.impulse(Mth.clamp((impact - 0.12f) / 0.85f, 0.15f, 1.25f));
            }
            airTicks = 0;
            minAirVy = 0f;
        }
        wasOnGround = ground;

        if (player.hurtTime > lastHurtTime && Cfg.reactions) hurt.impulse(1f);
        lastHurtTime = player.hurtTime;

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
        move.to(Mth.clamp(horizontalSpeed / 0.2f, 0f, 1f), dt);

        boolean ground = player.onGround();
        boolean flying = player.isFallFlying() || player.getAbilities().flying;
        boolean blocked = player.isPassenger() || player.isSleeping() || player.isFallFlying() || player.isAutoSpinAttack();
        gate.to(blocked ? 0f : 1f, dt);

        boolean swimming = player.isVisuallySwimming();
        boolean treading = player.isInWater() && !ground && !swimming;
        boolean climbing = player.onClimbable() && !ground && !player.isPassenger();
        boolean air = !ground && !player.isInWater() && !climbing && !flying && !player.isPassenger();

        sprintW.to(player.isSprinting() && horizontalSpeed > 0.05f ? 1f : 0f, dt);
        sneakW.to(player.isCrouching() ? 1f : 0f, dt);
        groundW.to(ground ? 1f : 0f, dt);
        airW.to(air ? 1f : 0f, dt);
        riseW.to(air && vertical > 0.04f ? 1f : 0f, dt);
        fallW.to(air ? Mth.clamp((-vertical - 0.30f) / 0.55f, 0f, 1f) : 0f, dt);
        swimW.to(swimming ? 1f : 0f, dt);
        waterW.to(treading ? 1f : 0f, dt);
        climbW.to(climbing ? 1f : 0f, dt);
        blockW.to(player.isBlocking() ? 1f : 0f, dt);

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

        speed.to(horizontalSpeed, dt);
        if (swimming) swimPhase += dt * (3.6f + 9f * Math.min(1f, horizontalSpeed / 0.2f));

        // Melee: start our own timeline at every new swing, independent of vanilla's 6-tick swing.
        float attack = player.getAttackAnim(pt);
        if (attack > 0f && (lastAttack <= 0f || attack < lastAttack - 0.3f)) startSwing();
        lastAttack = attack;
        toolSwingW.to(slashKind == TOOL && slashProgress() < 1.1f ? 1f : 0f, dt);

        boolean vanillaArms = player.isUsingItem()
                || CrossbowItem.isCharged(player.getMainHandItem()) || CrossbowItem.isCharged(player.getOffhandItem());
        float free = vanillaArms ? 0f : 1f;
        armsFree.to(free * (1f - 0.9f * castW.v), dt);

        land.step(dt);
        hurt.step(dt);
        release.step(dt);
    }

    private void startSwing() {
        HumanoidArm arm = player.swingingArm == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        slashSide = arm == HumanoidArm.RIGHT ? 1f : -1f;
        ItemStack held = player.getItemInHand(player.swingingArm);
        if (Weapons.isMeleeWeapon(held)) {
            boolean chained = slashKind >= 0 && slashKind <= SLASH_BACK && time - slashStart < SLASH_SECONDS + 0.7f;
            combo = chained ? (combo + 1) % 3 : 0;
            slashKind = combo;
        } else if (held.isEmpty()) {
            slashKind = PUNCH;
        } else {
            slashKind = TOOL;
        }
        slashStart = time;
    }

    /** 0..1 over the swing, keeps growing afterwards (used to blend out). */
    public float slashProgress() {
        return (time - slashStart) / SLASH_SECONDS;
    }

    public float moveF() {
        return move.v;
    }

    public AbstractClientPlayer player() {
        return player;
    }
}
