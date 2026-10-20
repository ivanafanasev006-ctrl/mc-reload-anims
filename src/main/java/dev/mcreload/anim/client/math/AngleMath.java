package dev.mcreload.anim.client.math;

public final class AngleMath {
    private static final float PI = (float) Math.PI;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    /** Shortest-path interpolation between two angles in radians. */
    public static float lerpAngle(float from, float to, float t) {
        float d = (to - from) % TWO_PI;
        if (d > PI) d -= TWO_PI;
        if (d < -PI) d += TWO_PI;
        return from + d * t;
    }

    public static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /** Smoothstep easing. */
    public static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private AngleMath() {}
}
