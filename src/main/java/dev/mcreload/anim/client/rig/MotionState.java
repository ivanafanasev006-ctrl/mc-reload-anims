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
    public static final int SLASH_H = 0, SLASH_V = 1, SLASH_BACK = 2, PUNCH = 3, TOOL = 4,
            PICK = 5, SHOVEL = 6, HOE = 7, CHOP = 8;
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
    public float swimPhase;
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
        }
        prevYawRate = yawRate;
        yawRate = Mth.wrapDegrees(player.yBodyRot - player.yBodyRotO);

        boolean ground = player.onGround();
        boolean special = player.isInWater() || player.isFallFlying() || player.onClimbable()
                || player.isPassenger() || player.getAbilities().flying;
        if (!ground) {
            if (wasOnGround && vy > 0.05 && !special) {
                // Take-off: switch the leading leg every jump.
                jumpLeg = !jumpLeg;
                takeoffTick = player.tickCount;
                takeoffRun = Mth.clamp((float) Math.sqrt(vx * vx + vz * vz) / 0.22f, 0f, 1f);
            }
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
        boolean creativeFlying = player.getAbilities().flying;
        boolean gliding = player.isFallFlying();
        boolean riding = player.isPassenger();
        boolean blocked = riding || player.isSleeping() || gliding || player.isAutoSpinAttack() || creativeFlying;
        gate.to(blocked ? 0f : 1f, dt);

        boolean swimming = player.isVisuallySwimming();
        boolean treading = player.isInWater() && !ground && !swimming;
        boolean climbing = player.onClimbable() && !ground && !riding;
        boolean air = !ground && !player.isInWater() && !climbing && !blocked;

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

        speed.to(horizontalSpeed, dt);
        // Front crawl: about one stroke per second per arm, a bit faster when sprint-swimming.
        if (swimming) swimPhase += dt * (2.4f + 2.6f * Math.min(1f, horizontalSpeed / 0.2f));

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

        boolean fists = player.getMainHandItem().isEmpty();
        guardW.to(fists && time - lastPunchTime < 2.5f && !blocked ? 1f : 0f, dt);

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
        ItemStack held = player.getItemInHand(player.swingingArm);
        int kind = classify(held);
        float progress = slashProgress();

        // Mining re-triggers the vanilla swing every few ticks: let each strike finish, then loop.
        if (isTool(kind) && kind == slashKind && progress < 0.92f) return;
        // Weapons: allow chaining once the hit has landed.
        if (isSlash(kind) && isSlash(slashKind) && progress < 0.55f) return;

        slashChained = progress < 1.3f && ((isSlash(kind) && isSlash(slashKind)) || (isTool(kind) && kind == slashKind));
        slashSide = arm == HumanoidArm.RIGHT ? 1f : -1f;
        if (isSlash(kind)) {
            boolean chained = isSlash(slashKind) && time - slashStart < slashDuration + 0.6f;
            combo = chained ? (combo + 1) % 3 : 0;
            slashKind = combo;
            // Slower weapons swing slower: match the attack cooldown (sword ~0.6 s, axe ~0.95 s).
            float cooldown = player.getCurrentItemAttackStrengthDelay() / 20f;
            slashDuration = Mth.clamp(cooldown * 0.95f, 0.5f, 1.0f);
        } else if (kind == PUNCH) {
            punchSide = time - lastPunchTime < 1.2f ? -punchSide : 1f;
            lastPunchTime = time;
            slashKind = PUNCH;
            slashDuration = 0.36f;
        } else {
            slashKind = kind;
            slashDuration = isTool(kind) ? 0.40f : 0.30f;
        }
        slashStart = time;
    }

    private int classify(ItemStack held) {
        if (held.isEmpty()) return PUNCH;
        Item item = held.getItem();
        if (item instanceof PickaxeItem) return PICK;
        if (item instanceof ShovelItem) return SHOVEL;
        if (item instanceof HoeItem) return HOE;
        if (item instanceof AxeItem) return targetingBlock() ? CHOP : SLASH_H;
        if (Weapons.isMeleeWeapon(held)) return SLASH_H;
        return TOOL;
    }

    /** For you: are you hitting a block (chopping a tree) rather than a mob? Others: assume a mob. */
    private boolean targetingBlock() {
        Minecraft mc = Minecraft.getInstance();
        return player == mc.player && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
    }

    public static boolean isSlash(int kind) {
        return kind >= SLASH_H && kind <= SLASH_BACK;
    }

    public static boolean isTool(int kind) {
        return kind >= PICK && kind <= CHOP;
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
