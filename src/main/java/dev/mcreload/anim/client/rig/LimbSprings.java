package dev.mcreload.anim.client.rig;

import dev.mcreload.anim.client.pose.PoseFrame;

/**
 * Secondary motion: every body part follows its target pose through a damped spring instead of
 * snapping to it. Limbs gain weight and inertia, overshoot a little and settle (follow-through),
 * and quick pose changes ripple through the body like a soft ragdoll. Head is left alone so the
 * view direction never lags.
 */
final class LimbSprings {
    private final float[][][] pos = new float[PoseFrame.COUNT][3][3];
    private final float[][][] vel = new float[PoseFrame.COUNT][3][3];
    private final boolean[][] init = new boolean[PoseFrame.COUNT][3];
    private final long[][] stamp = new long[PoseFrame.COUNT][3];
    final float[] omega = new float[PoseFrame.COUNT];
    final float[] zeta = new float[PoseFrame.COUNT];

    private long frame;
    private float dt;

    void beginFrame(float dt) {
        if (dt > 0f) frame++;
        this.dt = dt;
    }

    void reset() {
        for (boolean[] b : init) java.util.Arrays.fill(b, false);
    }

    /**
     * Filters one transform in place.
     * @param angleMask bit i set = component i is an angle (shortest-path error).
     */
    void filter(int part, int type, float[] v, int angleMask) {
        float[] p = pos[part][type];
        float[] vl = vel[part][type];
        if (!init[part][type]) {
            System.arraycopy(v, 0, p, 0, 3);
            vl[0] = vl[1] = vl[2] = 0f;
            init[part][type] = true;
            stamp[part][type] = frame;
            return;
        }
        if (stamp[part][type] != frame && dt > 0f) {
            stamp[part][type] = frame;
            float w = omega[part], z = zeta[part];
            int steps = Math.max(1, (int) Math.ceil(dt / (1f / 240f)));
            float h = dt / steps;
            for (int c = 0; c < 3; c++) {
                boolean angle = (angleMask & (1 << c)) != 0;
                for (int k = 0; k < steps; k++) {
                    float err = v[c] - p[c];
                    if (angle) err = wrap(err);
                    float a = w * w * err - 2f * z * w * vl[c];
                    vl[c] += a * h;
                    p[c] += vl[c] * h;
                }
                // Guard against runaway after long pauses.
                float err = v[c] - p[c];
                if (angle) err = wrap(err);
                if (Math.abs(err) > 3f) { p[c] = v[c]; vl[c] = 0f; }
            }
        }
        // Output the spring state, expressed near the requested angle.
        for (int c = 0; c < 3; c++) {
            boolean angle = (angleMask & (1 << c)) != 0;
            v[c] = angle ? v[c] - wrap(v[c] - p[c]) : p[c];
        }
    }

    private static float wrap(float a) {
        final float pi = (float) Math.PI, tau = pi * 2f;
        a %= tau;
        if (a > pi) a -= tau;
        if (a < -pi) a += tau;
        return a;
    }
}
