package dev.mcreload.anim.client.rig;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.mcreload.anim.client.math.AngleMath;
import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.config.Cfg;
import org.jetbrains.annotations.NotNull;

/**
 * Top-of-stack layer that makes other animation mods blend with ours.
 *
 * <p>It receives the pose after every other layer has been applied and compares it with what our
 * base layer produced. When they differ on a body part, some other mod (Better Combat attack or
 * weapon pose, Iron's Spells cast, an emote...) is animating that part. Whenever that starts or
 * stops, the part is cross-faded from the pose shown on the previous frame to the new one, which
 * removes the snapping you otherwise see when an animation begins or ends abruptly.</p>
 *
 * <p>It also lets our stride win on the legs while you are moving, so attacking or casting on the
 * run keeps the legs running instead of freezing them in a standing pose.</p>
 */
public final class HarmonizerLayer implements IAnimation {
    /** Frames without a difference before a part is considered released. */
    private static final int RELEASE_FRAMES = 3;
    private static final float EPS = 1e-4f;

    private final PlayerRig rig;

    private long frameCounter;
    private long nowNanos;
    private final boolean[] control = new boolean[PoseFrame.COUNT];
    private final long[] lastDiffFrame = new long[PoseFrame.COUNT];

    private final boolean[] fading = new boolean[PoseFrame.COUNT];
    private final long[] fadeStart = new long[PoseFrame.COUNT];
    private final float[] fadeSeconds = new float[PoseFrame.COUNT];
    private final float[][][] snapshot = new float[PoseFrame.COUNT][3][3];
    private final boolean[][] snapshotValid = new boolean[PoseFrame.COUNT][3];

    private final float[][][] lastOut = new float[PoseFrame.COUNT][3][3];
    private final boolean[][] lastOutValid = new boolean[PoseFrame.COUNT][3];

    HarmonizerLayer(PlayerRig rig) {
        this.rig = rig;
    }

    @Override
    public boolean isActive() {
        return Cfg.harmonizer && rig.shouldAnimate();
    }

    @Override
    public void setupAnim(float tickDelta) {
        if (!isActive()) return;
        frameCounter++;
        nowNanos = System.nanoTime();
        float dt = rig.motion.dt;
        for (int i = 0; i < PoseFrame.COUNT; i++) {
            if (control[i] && frameCounter - lastDiffFrame[i] > RELEASE_FRAMES) {
                control[i] = false;
                startFade(i, Cfg.fadeOut);
            }
            float target = control[i] ? 1f : 0f;
            float rate = control[i] ? 14f : 6f;
            if (dt > 0f) rig.foreign[i] += (target - rig.foreign[i]) * (1f - (float) Math.exp(-rate * dt));
        }
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float tickDelta, @NotNull Vec3f value0) {
        if ((type == TransformType.ROTATION || type == TransformType.POSITION) && stillBow(modelName)) return new Vec3f(0f, 0f, 0f);
        int i = PoseFrame.indexOf(modelName);
        int t = PlayerRig.typeIndex(type);
        if (i < 0 || t < 0) return value0;

        float x = value0.getX(), y = value0.getY(), z = value0.getZ();

        boolean hasBase = rig.baseOutValid[i][t];
        float[] base = rig.baseOut[i][t];
        if (hasBase && differs(x, y, z, base, t)) {
            lastDiffFrame[i] = frameCounter;
            if (!control[i]) {
                control[i] = true;
                startFade(i, Cfg.fadeIn);
            }
        }

        // Keep our locomotion on the legs while moving, keep the head looking where you look.
        if (control[i] && hasBase) {
            float keep = 0f;
            if (PoseFrame.isLeg(i)) keep = Cfg.keepLegs * rig.motion.moveF() * rig.motion.gate.v;
            else if (i == PoseFrame.HEAD && t == PlayerRig.T_ROT) keep = Cfg.keepHead;
            // A whole-body move of another mod (Kinetrix Moves' dodges): it moves the player itself.
            if (keep > 0f && dev.mcreload.anim.api.KinetrixApi.wholeBody(rig.player.getUUID())) keep = 0f;
            if (keep > 0f) {
                x = mix(x, base[0], keep, t, 0);
                y = mix(y, base[1], keep, t, 1);
                z = mix(z, base[2], keep, t, 2);
            }
        }

        if (fading[i]) {
            float secs = fadeSeconds[i];
            float progress = secs <= 0f ? 1f : (nowNanos - fadeStart[i]) / 1e9f / secs;
            if (progress >= 1f) {
                fading[i] = false;
            } else if (snapshotValid[i][t]) {
                float e = AngleMath.ease(progress);
                float[] s = snapshot[i][t];
                x = mix(s[0], x, e, t, 0);
                y = mix(s[1], y, e, t, 1);
                z = mix(s[2], z, e, t, 2);
            }
        }

        float[] out = lastOut[i][t];
        out[0] = x;
        out[1] = y;
        out[2] = z;
        lastOutValid[i][t] = true;
        return new Vec3f(x, y, z);
    }

    /**
     * The item of this hand is a bow held in the hand (not drawn, not in a whole-body move of
     * Kinetrix Moves, whose bow moves turn it on purpose): other mods' turns of it are dropped, so
     * a sword's swing (Better Combat) swings the arm and the bow stays as it is held.
     */
    private boolean stillBow(String part) {
        boolean right = "rightItem".equals(part);
        if (!right && !"leftItem".equals(part)) return false;
        net.minecraft.world.entity.player.Player p = rig.player;
        net.minecraft.world.item.ItemStack stack = right == rig.motion.mainRight ? p.getMainHandItem() : p.getOffhandItem();
        return Weapons.isBow(stack) && !p.isUsingItem() && !dev.mcreload.anim.api.KinetrixApi.wholeBody(p.getUUID());
    }

    boolean lastOut(int part, int type, float[] out) {
        if (!lastOutValid[part][type]) return false;
        System.arraycopy(lastOut[part][type], 0, out, 0, 3);
        return true;
    }

    private void startFade(int i, float seconds) {
        if (seconds <= 0f) {
            fading[i] = false;
            return;
        }
        for (int t = 0; t < 3; t++) {
            snapshotValid[i][t] = lastOutValid[i][t];
            if (lastOutValid[i][t]) System.arraycopy(lastOut[i][t], 0, snapshot[i][t], 0, 3);
        }
        fading[i] = true;
        fadeStart[i] = nowNanos;
        fadeSeconds[i] = seconds;
    }

    private static boolean differs(float x, float y, float z, float[] base, int t) {
        if (t == PlayerRig.T_ROT) {
            return Math.abs(angleDiff(x, base[0])) > EPS || Math.abs(angleDiff(y, base[1])) > EPS || Math.abs(angleDiff(z, base[2])) > EPS;
        }
        return Math.abs(x - base[0]) > EPS || Math.abs(y - base[1]) > EPS || Math.abs(z - base[2]) > EPS;
    }

    private static float angleDiff(float a, float b) {
        return AngleMath.lerpAngle(b, a, 1f) - b;
    }

    /** Interpolate one component; angles take the short way round. Bend: x is the bend plane angle. */
    private static float mix(float from, float to, float w, int type, int component) {
        boolean angle = type == PlayerRig.T_ROT || (type == PlayerRig.T_BEND && component == 0);
        return angle ? AngleMath.lerpAngle(from, to, w) : AngleMath.lerp(from, to, w);
    }
}
