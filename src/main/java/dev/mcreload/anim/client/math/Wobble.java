package dev.mcreload.anim.client.math;

/**
 * Damped oscillator pushed around by a force (usually minus the body's acceleration): the
 * "inertia" of loose limbs. Stays at 0 while nothing changes, swings and settles when it does.
 */
public final class Wobble {
    public float x;
    private float v;
    private final float k, c, limit;

    public Wobble(float stiffness, float damping, float limit) {
        this.k = stiffness;
        this.c = damping;
        this.limit = limit;
    }

    public void step(float dt, float force) {
        if (dt <= 0f) return;
        int n = Math.max(1, (int) Math.ceil(dt / (1f / 240f)));
        float h = dt / n;
        for (int i = 0; i < n; i++) {
            float a = force - k * x - c * v;
            v += a * h;
            x += v * h;
        }
        if (x > limit) { x = limit; v = Math.min(v, 0f); }
        if (x < -limit) { x = -limit; v = Math.max(v, 0f); }
    }
}
