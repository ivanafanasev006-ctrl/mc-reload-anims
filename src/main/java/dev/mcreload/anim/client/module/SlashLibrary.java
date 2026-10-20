package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.math.AngleMath;

/**
 * Keyframed melee attacks, authored for a right-handed swing (mirrored for the left hand).
 *
 * <p>Arm angles are ModelPart rotations: x -1.57 = arm straight forward, x -2.9 = overhead,
 * y + = arm swung out to its own side, y - = across the body, z + = away from the body.
 * Twist + = swinging shoulder back. Lean - = whole body pitched forward.</p>
 */
final class SlashLibrary {
    /** One pose of the attack. */
    record Key(float t,
               float armX, float armY, float armZ, float elbow,
               float twist, float lean,
               float offX, float offZ, float offElbow,
               float leadThigh, float leadKnee, float rearThigh, float rearKnee) {
    }

    private static final Key READY = new Key(0f, -0.55f, 0.05f, 0.10f, 0.45f, 0f, 0f, -0.40f, 0.10f, 0.60f, -0.10f, 0.25f, 0.05f, 0.25f);

    /** Forehand: wind up out to the side, sweep flat across the body. */
    static final Key[] HORIZONTAL = {
            READY,
            new Key(0.40f, -1.45f, 0.95f, 0.55f, 1.10f, 0.55f, 0.06f, -0.90f, 0.25f, 0.90f, -0.15f, 0.30f, 0.10f, 0.30f),
            new Key(0.62f, -1.35f, -1.00f, -0.25f, 0.05f, -0.60f, -0.22f, -0.20f, 0.40f, 0.50f, -0.60f, 0.95f, 0.40f, 0.45f),
            new Key(1.00f, -0.75f, -0.45f, 0.00f, 0.40f, -0.20f, -0.06f, -0.40f, 0.15f, 0.60f, -0.20f, 0.40f, 0.10f, 0.30f),
    };

    /** Overhead chop: weapon raised high behind the head, brought straight down. */
    static final Key[] VERTICAL = {
            READY,
            new Key(0.42f, -2.85f, 0.20f, 0.20f, 1.35f, 0.30f, 0.12f, -2.10f, -0.15f, 1.00f, -0.20f, 0.30f, 0.15f, 0.30f),
            new Key(0.62f, -0.45f, -0.05f, -0.10f, 0.00f, -0.30f, -0.38f, -0.60f, 0.00f, 0.40f, -0.70f, 1.10f, 0.45f, 0.50f),
            new Key(1.00f, -0.60f, 0.00f, 0.00f, 0.35f, -0.10f, -0.10f, -0.40f, 0.10f, 0.60f, -0.25f, 0.45f, 0.10f, 0.30f),
    };

    /** Backhand: wind up across the chest, rip back out to the side. */
    static final Key[] BACKHAND = {
            READY,
            new Key(0.40f, -1.30f, -1.05f, -0.45f, 1.35f, -0.60f, -0.05f, -0.50f, 0.30f, 0.80f, -0.25f, 0.35f, 0.15f, 0.30f),
            new Key(0.62f, -1.55f, 0.95f, 0.65f, 0.05f, 0.55f, -0.20f, -0.30f, 0.50f, 0.60f, -0.55f, 0.90f, 0.35f, 0.45f),
            new Key(1.00f, -0.70f, 0.40f, 0.20f, 0.40f, 0.15f, -0.05f, -0.40f, 0.15f, 0.60f, -0.20f, 0.40f, 0.10f, 0.30f),
    };

    private static final Key GUARD = new Key(0f, -1.00f, 0.15f, 0.10f, 1.90f, 0f, 0f, -1.10f, 0.10f, 2.00f, -0.10f, 0.30f, 0.15f, 0.30f);

    /** Jab from a boxing guard. */
    static final Key[] PUNCH = {
            GUARD,
            new Key(0.30f, -0.85f, 0.35f, 0.25f, 2.10f, 0.35f, 0.03f, -1.10f, 0.10f, 2.00f, -0.10f, 0.30f, 0.15f, 0.30f),
            new Key(0.55f, -1.62f, -0.12f, -0.05f, 0.00f, -0.45f, -0.15f, -1.15f, 0.10f, 2.00f, -0.35f, 0.60f, 0.25f, 0.35f),
            new Key(1.00f, -1.00f, 0.15f, 0.10f, 1.90f, 0f, 0f, -1.10f, 0.10f, 2.00f, -0.10f, 0.30f, 0.15f, 0.30f),
    };

    static Key[] forKind(int kind) {
        return switch (kind) {
            case 0 -> HORIZONTAL;
            case 1 -> VERTICAL;
            case 2 -> BACKHAND;
            default -> PUNCH;
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
                mix(a.rearThigh(), b.rearThigh(), u), mix(a.rearKnee(), b.rearKnee(), u));
    }

    private static float mix(float a, float b, float u) {
        return a + (b - a) * u;
    }

    private SlashLibrary() {}
}
