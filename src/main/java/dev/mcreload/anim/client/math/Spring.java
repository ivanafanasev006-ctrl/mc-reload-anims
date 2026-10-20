package dev.mcreload.anim.client.math;

/**
 * Damped spring used for one-shot reactions (landing squash, hurt recoil, spell release).
 * {@link #impulse(float)} with strength 1 produces a peak of roughly 1.0, then settles back to 0
 * with a small natural overshoot.
 */
public final class Spring {
    public float x;
    private float v;
    private final float k, c, gain;

    public Spring(float stiffness, float damping) {
        this.k = stiffness;
        this.c = damping;
        this.gain = 1f / measurePeak(stiffness, damping);
    }

    public void impulse(float strength) {
        v += strength * gain;
    }

    public void step(float dt) {
        if (dt <= 0f) return;
        int n = Math.max(1, (int) Math.ceil(dt / (1f / 240f)));
        float h = dt / n;
        for (int i = 0; i < n; i++) {
            float a = -k * x - c * v;
            v += a * h;
            x += v * h;
        }
        if (Math.abs(x) < 1e-4f && Math.abs(v) < 1e-3f) { x = 0f; v = 0f; }
        x = Math.max(-1.5f, Math.min(1.5f, x));
    }

    private static float measurePeak(float k, float c) {
        float x = 0f, v = 1f, peak = 1e-4f, h = 1f / 1000f;
        for (int i = 0; i < 3000; i++) {
            float a = -k * x - c * v;
            v += a * h;
            x += v * h;
            peak = Math.max(peak, x);
        }
        return peak;
    }
}
