package dev.mcreload.anim.client.pose;

/**
 * What our procedural layer wants to do to one body part this frame.
 * Rotations/positions are added on top of the incoming (vanilla) pose; targets pull an axis
 * toward an absolute angle with a weight, for poses vanilla does not have (e.g. ladder reach).
 */
public final class PartPose {
    /** Additive rotation, radians (model space: -x = forward/up for arms and legs). */
    public float rx, ry, rz;
    /** Additive position. Model pixels for limbs, blocks for the whole "body". */
    public float px, py, pz;
    /** Additive bend amount, radians, in the forward/back plane. */
    public float bend;
    /** Absolute rotation targets and their weights (0 = unused). */
    public float tx, ty, tz, wx, wy, wz;

    public void reset() {
        rx = ry = rz = 0f;
        px = py = pz = 0f;
        bend = 0f;
        tx = ty = tz = 0f;
        wx = wy = wz = 0f;
    }

    public void targetX(float value, float weight) {
        if (weight <= 0f) return;
        tx = wx <= 0f ? value : (tx * wx + value * weight) / (wx + weight);
        wx = Math.min(1f, wx + weight);
    }

    public void scale(float s) {
        rx *= s; ry *= s; rz *= s;
        px *= s; py *= s; pz *= s;
        bend *= s;
        float ws = Math.min(1f, s);
        wx *= ws; wy *= ws; wz *= ws;
    }
}
