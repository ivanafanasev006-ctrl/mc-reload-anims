package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.math.AngleMath;
import dev.mcreload.anim.client.rig.MotionState;

/**
 * Keyframed strikes, authored for a right-handed swing (mirrored for the left hand).
 *
 * <p>Arm angles are ModelPart rotations: x -1.57 = arm straight forward, x -2.9 = overhead,
 * y + = arm swung out to its own side (behind the shoulder when large), y - = across the body,
 * z + = away from the body. Twist + = swinging shoulder back. Lean - = whole body pitched forward.
 * spin = whole-body turns (1 = full 360 degrees).</p>
 */
final class SlashLibrary {
    /** One pose of the attack. */
    record Key(float t,
               float armX, float armY, float armZ, float elbow,
               float twist, float lean,
               float offX, float offZ, float offElbow,
               float leadThigh, float leadKnee, float rearThigh, float rearKnee,
               float spin) {
        Key(float t, float armX, float armY, float armZ, float elbow, float twist, float lean,
            float offX, float offZ, float offElbow, float leadThigh, float leadKnee, float rearThigh, float rearKnee) {
            this(t, armX, armY, armZ, elbow, twist, lean, offX, offZ, offElbow, leadThigh, leadKnee, rearThigh, rearKnee, 0f);
        }
    }

    private static final Key READY = new Key(0f, -0.70f, -0.20f, 0.10f, 0.70f, 0f, 0f, -0.50f, 0.15f, 0.70f, -0.20f, 0.35f, 0.10f, 0.30f);

    // ---------------------------------------------------------------- sword combo

    /** Forehand: blade drawn far back behind the shoulder, then swept flat across the body. */
    static final Key[] HORIZONTAL = {
            READY,
            new Key(0.40f, -2.25f, 0.85f, 0.55f, 1.25f, 0.70f, 0.06f, -0.70f, 0.35f, 0.90f, -0.15f, 0.30f, 0.20f, 0.45f),
            new Key(0.60f, -1.20f, -0.95f, -0.10f, 0.10f, -0.85f, -0.38f, 0.45f, 0.40f, 0.30f, -0.85f, 1.10f, 0.50f, 0.40f),
            new Key(1.00f, -0.85f, -0.45f, 0.00f, 0.45f, -0.25f, -0.08f, -0.30f, 0.20f, 0.60f, -0.25f, 0.45f, 0.15f, 0.30f),
    };

    /** Backhand: wound up across the chest, ripped out wide behind. */
    static final Key[] BACKHAND = {
            READY,
            new Key(0.40f, -1.75f, -1.05f, -0.35f, 1.45f, -0.75f, 0.00f, 0.10f, 0.45f, 0.60f, -0.25f, 0.40f, 0.15f, 0.30f),
            new Key(0.60f, -1.35f, 0.95f, 0.75f, 0.05f, 0.80f, -0.32f, -0.80f, 0.20f, 0.90f, -0.80f, 1.05f, 0.45f, 0.45f),
            new Key(1.00f, -0.80f, 0.35f, 0.25f, 0.45f, 0.20f, -0.06f, -0.40f, 0.20f, 0.60f, -0.25f, 0.45f, 0.15f, 0.30f),
    };

    /** Overhead chop: blade raised high behind the head, brought straight down with the body. */
    static final Key[] VERTICAL = {
            READY,
            new Key(0.40f, -3.00f, 0.20f, 0.25f, 1.45f, 0.40f, 0.15f, -0.40f, 0.40f, 0.70f, -0.20f, 0.30f, 0.20f, 0.30f),
            new Key(0.60f, -0.55f, -0.10f, -0.05f, 0.05f, -0.35f, -0.55f, 0.50f, 0.35f, 0.30f, -0.95f, 1.30f, 0.55f, 0.55f),
            new Key(1.00f, -0.70f, -0.15f, 0.00f, 0.40f, -0.10f, -0.12f, -0.35f, 0.15f, 0.60f, -0.30f, 0.50f, 0.15f, 0.30f),
    };

    /** Spinning finisher: wind up, turn a full circle with the blade held out flat. */
    static final Key[] SPIN = {
            READY,
            new Key(0.25f, -1.45f, 1.30f, 0.70f, 0.60f, 0.70f, 0.05f, -0.80f, 0.40f, 0.70f, -0.30f, 0.45f, 0.20f, 0.40f, 0f),
            new Key(0.70f, -1.55f, 0.25f, 1.20f, 0.05f, -0.30f, -0.15f, -0.40f, 0.90f, 0.30f, -0.45f, 0.70f, 0.30f, 0.50f, 1f),
            new Key(1.00f, -0.80f, -0.50f, 0.10f, 0.45f, -0.15f, -0.06f, -0.40f, 0.20f, 0.60f, -0.25f, 0.45f, 0.15f, 0.30f, 1f),
    };

    // ---------------------------------------------------------------- axe (heavy, whole body)

    /** Heavy diagonal: axe hoisted high over the shoulder, body thrown down into the cut. */
    static final Key[] AXE_OVER = {
            READY,
            new Key(0.48f, -2.75f, 0.65f, 0.40f, 1.35f, 0.75f, 0.18f, -0.50f, 0.30f, 0.70f, -0.20f, 0.35f, 0.20f, 0.35f),
            new Key(0.68f, -0.45f, -0.70f, -0.15f, 0.05f, -0.70f, -0.55f, 0.45f, 0.30f, 0.70f, -0.90f, 1.25f, 0.55f, 0.60f),
            new Key(1.00f, -0.70f, -0.30f, 0.05f, 0.50f, -0.20f, -0.15f, -0.40f, 0.30f, 0.70f, -0.35f, 0.55f, 0.15f, 0.35f),
    };

    /** Heavy horizontal: hips and shoulders wound right round, axe swept through at waist height. */
    static final Key[] AXE_SWEEP = {
            READY,
            new Key(0.48f, -1.20f, 1.60f, 0.90f, 0.80f, 1.05f, 0.08f, -0.40f, 0.30f, 0.70f, -0.20f, 0.40f, 0.25f, 0.50f),
            new Key(0.68f, -1.10f, -1.45f, -0.35f, 0.05f, -1.00f, -0.30f, 0.45f, 0.30f, 0.70f, -0.75f, 1.05f, 0.45f, 0.50f),
            new Key(1.00f, -0.75f, -0.55f, 0.00f, 0.45f, -0.30f, -0.08f, -0.40f, 0.30f, 0.70f, -0.30f, 0.50f, 0.15f, 0.30f),
    };

    // ---------------------------------------------------------------- tools (looping while mining)

    /** Pickaxe: raise it high, hammer it down into the block. */
    static final Key[] PICK = {
            new Key(0f, -1.00f, 0.10f, 0.10f, 0.60f, 0f, 0f, -0.30f, 0.15f, 0.60f, -0.15f, 0.30f, 0.10f, 0.30f),
            new Key(0.45f, -2.55f, 0.15f, 0.15f, 1.25f, 0.25f, 0.06f, -0.30f, 0.15f, 0.60f, -0.20f, 0.35f, 0.15f, 0.30f),
            new Key(0.68f, -0.75f, -0.05f, 0.00f, 0.15f, -0.20f, -0.20f, -0.30f, 0.15f, 0.60f, -0.35f, 0.60f, 0.20f, 0.40f),
            new Key(1.00f, -1.00f, 0.10f, 0.10f, 0.60f, 0f, -0.05f, -0.30f, 0.15f, 0.60f, -0.15f, 0.30f, 0.10f, 0.30f),
    };

    /** Shovel: bend over, push the blade in, lever the earth up. */
    static final Key[] SHOVEL = {
            new Key(0f, -0.80f, 0.00f, 0.05f, 0.50f, 0f, -0.05f, -0.80f, 0.10f, 0.80f, -0.30f, 0.50f, 0.20f, 0.40f),
            new Key(0.35f, -0.45f, 0.00f, 0.05f, 0.60f, 0.15f, -0.25f, -0.90f, 0.10f, 0.90f, -0.45f, 0.80f, 0.25f, 0.60f),
            new Key(0.60f, -0.75f, 0.00f, 0.00f, 0.05f, -0.10f, -0.35f, -0.70f, 0.10f, 0.60f, -0.50f, 0.90f, 0.25f, 0.60f),
            new Key(0.82f, -1.55f, 0.05f, 0.05f, 0.70f, 0.05f, -0.10f, -1.10f, 0.10f, 0.90f, -0.35f, 0.60f, 0.20f, 0.45f),
            new Key(1.00f, -0.80f, 0.00f, 0.05f, 0.50f, 0f, -0.05f, -0.80f, 0.10f, 0.80f, -0.30f, 0.50f, 0.20f, 0.40f),
    };

    /** Hoe: chop down at the ground in front of the feet. */
    static final Key[] HOE = {
            new Key(0f, -0.90f, 0.05f, 0.05f, 0.50f, 0f, -0.05f, -0.30f, 0.15f, 0.60f, -0.25f, 0.45f, 0.15f, 0.35f),
            new Key(0.42f, -2.10f, 0.10f, 0.10f, 1.00f, 0.20f, 0.02f, -0.30f, 0.15f, 0.60f, -0.25f, 0.45f, 0.15f, 0.35f),
            new Key(0.66f, -0.35f, -0.05f, 0.00f, 0.10f, -0.15f, -0.35f, -0.30f, 0.15f, 0.60f, -0.45f, 0.80f, 0.25f, 0.55f),
            new Key(1.00f, -0.90f, 0.05f, 0.05f, 0.50f, 0f, -0.10f, -0.30f, 0.15f, 0.60f, -0.25f, 0.45f, 0.15f, 0.35f),
    };

    /** Axe on wood: diagonal chop from over the shoulder. */
    static final Key[] CHOP = {
            new Key(0f, -0.90f, 0.20f, 0.15f, 0.55f, 0f, 0f, -0.30f, 0.15f, 0.60f, -0.20f, 0.35f, 0.15f, 0.30f),
            new Key(0.42f, -2.20f, 0.70f, 0.50f, 1.10f, 0.50f, 0.04f, -0.30f, 0.15f, 0.60f, -0.25f, 0.40f, 0.15f, 0.30f),
            new Key(0.66f, -1.20f, -0.50f, -0.10f, 0.10f, -0.40f, -0.15f, -0.30f, 0.15f, 0.60f, -0.45f, 0.75f, 0.25f, 0.45f),
            new Key(1.00f, -0.90f, 0.20f, 0.15f, 0.55f, 0f, 0f, -0.30f, 0.15f, 0.60f, -0.20f, 0.35f, 0.15f, 0.30f),
    };

    /** Bare hands on a block: hammer-fist straight down, the whole torso pumping into it. */
    static final Key[] FIST_BLOCK = {
            new Key(0f, -1.10f, -0.15f, 0.05f, 1.30f, 0f, -0.05f, -0.45f, 0.15f, 1.00f, -0.25f, 0.45f, 0.15f, 0.35f),
            new Key(0.40f, -2.30f, -0.05f, 0.10f, 1.35f, 0.30f, 0.06f, -0.40f, 0.15f, 1.00f, -0.25f, 0.45f, 0.15f, 0.35f),
            new Key(0.62f, -0.90f, -0.20f, 0.00f, 0.25f, -0.30f, -0.30f, -0.30f, 0.20f, 0.90f, -0.40f, 0.70f, 0.20f, 0.45f),
            new Key(1.00f, -1.10f, -0.15f, 0.05f, 1.30f, 0f, -0.05f, -0.45f, 0.15f, 1.00f, -0.25f, 0.45f, 0.15f, 0.35f),
    };

    /** Throwing an item away (Q): arm cocked back, flung out forward, follow-through. */
    static final Key[] THROW = {
            new Key(0f, -0.40f, 0.10f, 0.10f, 0.40f, 0f, 0f, -0.40f, 0.10f, 0.40f, -0.10f, 0.25f, 0.10f, 0.25f),
            new Key(0.30f, 0.55f, 0.35f, 0.25f, 1.20f, 0.45f, 0.06f, -0.70f, 0.15f, 0.60f, -0.15f, 0.30f, 0.20f, 0.40f),
            new Key(0.55f, -1.85f, -0.10f, 0.05f, 0.00f, -0.35f, -0.18f, -0.20f, 0.20f, 0.40f, -0.45f, 0.70f, 0.30f, 0.40f),
            new Key(1.00f, -0.50f, 0.00f, 0.10f, 0.40f, -0.05f, -0.03f, -0.40f, 0.10f, 0.40f, -0.15f, 0.30f, 0.10f, 0.25f),
    };

    static Key[] forKind(int kind) {
        return switch (kind) {
            case MotionState.SLASH_H -> HORIZONTAL;
            case MotionState.SLASH_V -> VERTICAL;
            case MotionState.SLASH_BACK -> BACKHAND;
            case MotionState.SPIN -> SPIN;
            case MotionState.AXE_OVER -> AXE_OVER;
            case MotionState.AXE_SWEEP -> AXE_SWEEP;
            case MotionState.PICK -> PICK;
            case MotionState.SHOVEL -> SHOVEL;
            case MotionState.HOE -> HOE;
            case MotionState.CHOP -> CHOP;
            case MotionState.FIST_BLOCK -> FIST_BLOCK;
            case MotionState.THROW -> THROW;
            default -> HORIZONTAL;
        };
    }

    /** Sample the attack at progress t (0..1, clamped), easing between keys. */
    static Key sample(Key[] keys, float t) {
        t = AngleMath.clamp(t, 0f, 1f);
        int i = 0;
        while (i < keys.length - 2 && t > keys[i + 1].t()) i++;
        Key a = keys[i], b = keys[i + 1];
        float span = b.t() - a.t();
        float u = span <= 0f ? 1f : AngleMath.clamp((t - a.t()) / span, 0f, 1f);
        u = AngleMath.ease(u);
        return new Key(t,
                mix(a.armX(), b.armX(), u), mix(a.armY(), b.armY(), u), mix(a.armZ(), b.armZ(), u), mix(a.elbow(), b.elbow(), u),
                mix(a.twist(), b.twist(), u), mix(a.lean(), b.lean(), u),
                mix(a.offX(), b.offX(), u), mix(a.offZ(), b.offZ(), u), mix(a.offElbow(), b.offElbow(), u),
                mix(a.leadThigh(), b.leadThigh(), u), mix(a.leadKnee(), b.leadKnee(), u),
                mix(a.rearThigh(), b.rearThigh(), u), mix(a.rearKnee(), b.rearKnee(), u),
                mix(a.spin(), b.spin(), u));
    }

    /*
     * Wrist roll of the weapon hand, (t, roll) pairs. 0 = blade straight out of the fist as in
     * vanilla; + turns the blade toward the outside of the arm, - toward the inside. A forehand
     * cocks the blade back behind the arm on the wind-up and snaps it through flat on the cut.
     */
    private static final float[][] ROLL_H = {{0f, 0f}, {0.40f, 1.05f}, {0.52f, 0.1f}, {0.60f, -1.20f}, {1.00f, -0.30f}};
    private static final float[][] ROLL_BACK = {{0f, 0f}, {0.40f, -1.30f}, {0.52f, -0.1f}, {0.60f, 1.25f}, {1.00f, 0.30f}};
    private static final float[][] ROLL_V = {{0f, 0f}, {0.42f, 0.35f}, {0.62f, 0f}, {1.00f, 0f}};
    private static final float[][] ROLL_SPIN = {{0f, 0f}, {0.25f, 1.20f}, {0.70f, 1.45f}, {1.00f, 0.10f}};
    private static final float[][] ROLL_AXE_SWEEP = {{0f, 0f}, {0.48f, 1.10f}, {0.68f, -1.10f}, {1.00f, -0.20f}};
    private static final float[][] ROLL_NONE = {{0f, 0f}, {1f, 0f}};

    static float roll(int kind, float t) {
        float[][] k = switch (kind) {
            case MotionState.SLASH_H -> ROLL_H;
            case MotionState.SLASH_BACK -> ROLL_BACK;
            case MotionState.SLASH_V -> ROLL_V;
            case MotionState.SPIN -> ROLL_SPIN;
            case MotionState.AXE_SWEEP -> ROLL_AXE_SWEEP;
            default -> ROLL_NONE;
        };
        t = AngleMath.clamp(t, 0f, 1f);
        int i = 0;
        while (i < k.length - 2 && t > k[i + 1][0]) i++;
        float span = k[i + 1][0] - k[i][0];
        float u = span <= 0f ? 1f : AngleMath.ease((t - k[i][0]) / span);
        return mix(k[i][1], k[i + 1][1], u);
    }

    private static float mix(float a, float b, float u) {
        return a + (b - a) * u;
    }

    private SlashLibrary() {}
}
