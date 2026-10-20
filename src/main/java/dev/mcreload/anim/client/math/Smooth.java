package dev.mcreload.anim.client.math;

/** Frame-rate independent exponential smoothing toward a target. */
public final class Smooth {
    public float v;
    private final float rate;

    /** @param rate how fast to follow, in 1/seconds (higher = snappier). */
    public Smooth(float rate) {
        this.rate = rate;
    }

    public float to(float target, float dt) {
        if (dt <= 0f) return v;
        v += (target - v) * (1f - (float) Math.exp(-rate * dt));
        return v;
    }
}
