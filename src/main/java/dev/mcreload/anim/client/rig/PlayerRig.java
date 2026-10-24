package dev.mcreload.anim.client.rig;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.mcreload.anim.client.math.AngleMath;
import dev.mcreload.anim.client.module.AnimModule;
import dev.mcreload.anim.client.module.Modules;
import dev.mcreload.anim.client.pose.PartPose;
import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * Per-player animation brain. Owns two playerAnimator layers:
 * <ul>
 *   <li>{@link BaseLayer} at the very bottom of the stack: our procedural Mo'Bends-style motion,
 *       added on top of the vanilla pose. Every other mod's animation plays above it.</li>
 *   <li>{@link HarmonizerLayer} at the very top: watches what the layers in between change and
 *       cross-fades every start/stop, so switching between our motion and Better Combat /
 *       Iron's Spells / emotes never jumps.</li>
 * </ul>
 */
public final class PlayerRig {
    public static final int BASE_PRIORITY = -100_000;
    public static final int HARMONIZER_PRIORITY = 100_000;

    static final int T_POS = 0, T_ROT = 1, T_BEND = 2;

    public final AbstractClientPlayer player;
    public final MotionState motion;
    public final PoseFrame frame = new PoseFrame();
    final BaseLayer base;
    final HarmonizerLayer harmonizer;

    /** What the base layer output this frame, per part and transform type. */
    final float[][][] baseOut = new float[PoseFrame.COUNT][3][3];
    final boolean[][] baseOutValid = new boolean[PoseFrame.COUNT][3];

    /** 0..1, smoothed: how much another mod currently animates each part. */
    public final float[] foreign = new float[PoseFrame.COUNT];
    private final float[] yieldFactor = new float[PoseFrame.COUNT];
    private final LimbSprings springs = new LimbSprings();
    private final float[] tmp = new float[3];

    public PlayerRig(AbstractClientPlayer player) {
        this.player = player;
        this.motion = new MotionState(player);
        this.base = new BaseLayer(this);
        this.harmonizer = new HarmonizerLayer(this);
    }

    public BaseLayer baseLayer() {
        return base;
    }

    public HarmonizerLayer harmonizerLayer() {
        return harmonizer;
    }

    /** The dummy in the animation gallery (always animated, it is there to show them). */
    public boolean preview;

    private dev.kosmx.playerAnim.api.layered.IAnimation ironsLayer;
    private boolean ironsLooked;

    /** One of Iron's Spells' animations is playing on this player (a cast, its wind-up or its end). */
    public boolean ironsAnimating() {
        if (!dev.mcreload.anim.compat.ModCompat.ironsSpells) return false;
        if (!ironsLooked) {
            ironsLooked = true;
            if (player instanceof dev.kosmx.playerAnim.impl.IAnimatedPlayer ap) {
                ironsLayer = ap.playerAnimator_getAnimation(
                        new net.minecraft.resources.ResourceLocation(dev.mcreload.anim.compat.IronsSpellsCompat.MODID, "animation"));
            }
        }
        return ironsLayer != null && ironsLayer.isActive();
    }

    public boolean shouldAnimateNow() {
        return shouldAnimate();
    }

    boolean shouldAnimate() {
        if (preview) return true;
        if (!Cfg.enabled) return false;
        if (player.isSpectator() || player.isRemoved()) return false;
        return player.isLocalPlayer() || Cfg.otherPlayers;
    }

    void computeFrame(float partialTick) {
        motion.frame(partialTick);
        // Another mod hanging the head (a nap): how far down it was drawn last frame.
        motion.updateDoze(foreign[PoseFrame.HEAD], finalPose(PoseFrame.HEAD, T_ROT, tmp) ? tmp[0] : 0f);
        frame.reset();
        Signs signs = Cfg.signs;
        for (AnimModule module : Modules.ALL) {
            if (module.enabled()) module.apply(this, motion, frame, signs, partialTick);
        }
        frame.scale(Cfg.intensity);
        tuneSprings();

        float yieldStrength = Cfg.harmonizer ? Cfg.yield : 0f;
        for (int i = 0; i < PoseFrame.COUNT; i++) {
            // Legs never yield: when another mod animates them while we run, the harmonizer
            // decides how much of our stride to keep.
            yieldFactor[i] = PoseFrame.isLeg(i) ? 0f : foreign[i] * yieldStrength;
        }
    }

    /** Stiffness per body part for the secondary-motion springs, depending on what is happening. */
    private void tuneSprings() {
        springs.beginFrame(motion.dt);
        float lv = Cfg.liveliness;
        boolean attacking = motion.slashKind >= 0 && motion.slashKind != MotionState.TOOL && motion.slashProgress() < 1.2f;
        float air = Math.max(motion.airW.v, Math.max(motion.elytraW.v, motion.flyW.v));

        float armW = lerp(26f, 12f, lv), armZ = lerp(0.85f, 0.45f, lv);
        // Strikes: firm but not snapping; a touch of lag keeps them fluid.
        if (attacking) { armW = Math.max(armW, 26f); armZ = 0.72f; }
        // While vanilla poses the arms (using items), follow it tightly.
        float vanilla = 1f - motion.armsFree.v * (1f - motion.toolSwingW.v);
        armW = lerp(armW, 60f, vanilla);
        armZ = lerp(armZ, 1f, vanilla);

        float legW = lerp(lerp(30f, 24f, lv), lerp(24f, 13f, lv), air);
        float legZ = lerp(0.9f, lerp(0.85f, 0.5f, lv), air);
        if (attacking) legW = Math.max(legW, 24f);

        springs.omega[PoseFrame.RIGHT_ARM] = springs.omega[PoseFrame.LEFT_ARM] = armW;
        springs.zeta[PoseFrame.RIGHT_ARM] = springs.zeta[PoseFrame.LEFT_ARM] = armZ;
        springs.omega[PoseFrame.RIGHT_LEG] = springs.omega[PoseFrame.LEFT_LEG] = legW;
        springs.zeta[PoseFrame.RIGHT_LEG] = springs.zeta[PoseFrame.LEFT_LEG] = legZ;
        springs.omega[PoseFrame.TORSO] = attacking ? 22f : lerp(20f, 11f, lv);
        springs.zeta[PoseFrame.TORSO] = lerp(0.85f, 0.55f, lv);
        springs.omega[PoseFrame.BODY] = lerp(18f, 10f, lv);
        springs.zeta[PoseFrame.BODY] = lerp(0.9f, 0.6f, lv);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    Vec3f applyBase(String name, TransformType type, Vec3f v0) {
        int i = PoseFrame.indexOf(name);
        if (i < 0) return v0;
        PartPose p = frame.get(i);
        float k = 1f - yieldFactor[i];
        float x, y, z;
        int t;
        switch (type) {
            case ROTATION -> {
                t = T_ROT;
                x = v0.getX();
                y = v0.getY();
                z = v0.getZ();
                // Targets replace the incoming pose, additive motion goes on top of the result.
                if (p.wx > 0f) x = AngleMath.lerpAngle(x, p.tx, p.wx * k);
                if (p.wy > 0f) y = AngleMath.lerpAngle(y, p.ty, p.wy * k);
                if (p.wz > 0f) z = AngleMath.lerpAngle(z, p.tz, p.wz * k);
                x += p.rx * k;
                y += p.ry * k;
                z += p.rz * k;
                if (Math.abs(p.roll) > 1e-4f) {
                    tmp[0] = x; tmp[1] = y; tmp[2] = z;
                    AngleMath.rollLocalY(tmp, p.roll * k);
                    x = tmp[0]; y = tmp[1]; z = tmp[2];
                }
            }
            case POSITION -> {
                t = T_POS;
                x = v0.getX() + p.px * k;
                y = v0.getY() + p.py * k;
                z = v0.getZ() + p.pz * k;
            }
            case BEND -> {
                t = T_BEND;
                x = v0.getX();
                if (p.wAxis > 0f) x = AngleMath.lerpAngle(x, p.bendAxis, p.wAxis * k);
                y = v0.getY() + p.bend * k;
                z = v0.getZ();
            }
            default -> {
                return v0;
            }
        }
        if (Cfg.liveliness > 0f && i != PoseFrame.HEAD) {
            tmp[0] = x; tmp[1] = y; tmp[2] = z;
            int angles = t == T_ROT ? 0b111 : (t == T_BEND ? 0b001 : 0);
            springs.filter(i, t, tmp, angles);
            x = tmp[0]; y = tmp[1]; z = tmp[2];
        }
        // Another mod animating the whole player its own way (ParCool's parkour moves): hand the
        // pose through untouched, fading out of ours and back in again.
        float pass = motion.parkourW.v;
        if (pass > 0.001f) {
            boolean angleX = t == T_ROT || t == T_BEND;
            x = angleX ? AngleMath.lerpAngle(x, v0.getX(), pass) : x + (v0.getX() - x) * pass;
            y = t == T_ROT ? AngleMath.lerpAngle(y, v0.getY(), pass) : y + (v0.getY() - y) * pass;
            z = t == T_ROT ? AngleMath.lerpAngle(z, v0.getZ(), pass) : z + (v0.getZ() - z) * pass;
        }
        float[] out = baseOut[i][t];
        out[0] = x;
        out[1] = y;
        out[2] = z;
        baseOutValid[i][t] = true;
        return new Vec3f(x, y, z);
    }

    /**
     * The pose that was actually drawn last frame for a part (after every mod's animation),
     * in playerAnimator units. Returns false if nothing was drawn yet.
     * @param type 0 = position, 1 = rotation, 2 = bend
     */
    public boolean finalPose(int part, int type, float[] out) {
        if (harmonizer.isActive() && harmonizer.lastOut(part, type, out)) return true;
        if (!baseOutValid[part][type]) return false;
        System.arraycopy(baseOut[part][type], 0, out, 0, 3);
        return true;
    }

    /** What our own layer output last frame for a part, before the other mods' layers (self-test). */
    public boolean basePose(int part, int type, float[] out) {
        if (!baseOutValid[part][type]) return false;
        System.arraycopy(baseOut[part][type], 0, out, 0, 3);
        return true;
    }

    static int typeIndex(TransformType type) {
        return switch (type) {
            case POSITION -> T_POS;
            case ROTATION -> T_ROT;
            case BEND -> T_BEND;
            default -> -1;
        };
    }
}
