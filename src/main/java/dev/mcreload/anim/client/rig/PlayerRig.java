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

    boolean shouldAnimate() {
        if (!Cfg.enabled) return false;
        if (player.isSpectator() || player.isRemoved()) return false;
        return player.isLocalPlayer() || Cfg.otherPlayers;
    }

    void computeFrame(float partialTick) {
        motion.frame(partialTick);
        frame.reset();
        Signs signs = Cfg.signs;
        for (AnimModule module : Modules.ALL) {
            if (module.enabled()) module.apply(this, motion, frame, signs, partialTick);
        }
        frame.scale(Cfg.intensity);

        float yieldStrength = Cfg.harmonizer ? Cfg.yield : 0f;
        for (int i = 0; i < PoseFrame.COUNT; i++) {
            // Legs never yield: when another mod animates them while we run, the harmonizer
            // decides how much of our stride to keep.
            yieldFactor[i] = PoseFrame.isLeg(i) ? 0f : foreign[i] * yieldStrength;
        }
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
                x = v0.getX() + p.rx * k;
                y = v0.getY() + p.ry * k;
                z = v0.getZ() + p.rz * k;
                if (p.wx > 0f) x = AngleMath.lerpAngle(x, p.tx, p.wx * k);
                if (p.wy > 0f) y = AngleMath.lerpAngle(y, p.ty, p.wy * k);
                if (p.wz > 0f) z = AngleMath.lerpAngle(z, p.tz, p.wz * k);
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
                y = v0.getY() + p.bend * k;
                z = v0.getZ();
            }
            default -> {
                return v0;
            }
        }
        float[] out = baseOut[i][t];
        out[0] = x;
        out[1] = y;
        out[2] = z;
        baseOutValid[i][t] = true;
        return new Vec3f(x, y, z);
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
