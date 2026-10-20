package dev.mcreload.anim.client.pose;

import dev.mcreload.anim.client.math.AngleMath;

/**
 * What our procedural layer wants to do to one body part this frame.
 *
 * <p>Final rotation = lerp(incoming pose, target, weight) + additive. Targets REPLACE vanilla's
 * pose (that is what makes knees, elbows and arms really move); additive values are layered on top
 * (lean, twist, breathing, recoil).</p>
 */
public final class PartPose {
    /** Additive rotation, radians (model space: -x = forward/up for arms and legs). */
    public float rx, ry, rz;
    /** Additive position. Model pixels for limbs, blocks for the whole "body". */
    public float px, py, pz;
    /** Bend amount, radians, in the forward/back plane (knee / elbow / waist). */
    public float bend;
    /** Absolute rotation targets and their weights (0 = keep incoming pose). */
    public float tx, ty, tz, wx, wy, wz;
    /** Twist about the limb's own long axis (wrist roll for arms), applied after everything else. */
    public float roll;

    public void reset() {
        rx = ry = rz = 0f;
        px = py = pz = 0f;
        bend = 0f;
        tx = ty = tz = 0f;
        wx = wy = wz = 0f;
        roll = 0f;
    }

    /** Pull the limb's own twist toward a value (used by attacks to sweep the blade in the hand). */
    public void overrideRoll(float value, float weight) {
        if (weight <= 0f) return;
        roll += (value - roll) * Math.min(1f, weight);
    }

    /** Blend toward an absolute X angle. Several calls are averaged by weight. */
    public void targetX(float value, float weight) {
        if (weight <= 0f) return;
        tx = wx <= 0f ? value : (tx * wx + value * weight) / (wx + weight);
        wx = Math.min(1f, wx + weight);
    }

    public void targetY(float value, float weight) {
        if (weight <= 0f) return;
        ty = wy <= 0f ? value : (ty * wy + value * weight) / (wy + weight);
        wy = Math.min(1f, wy + weight);
    }

    public void targetZ(float value, float weight) {
        if (weight <= 0f) return;
        tz = wz <= 0f ? value : (tz * wz + value * weight) / (wz + weight);
        wz = Math.min(1f, wz + weight);
    }

    /**
     * Higher-priority pose (attacks): pulls whatever was targeted so far toward this one,
     * instead of averaging with it.
     */
    public void override(float x, float y, float z, float weight) {
        if (weight <= 0f) return;
        if (wx <= 0f) { tx = x; wx = weight; } else { tx = AngleMath.lerpAngle(tx, x, weight); wx += (1f - wx) * weight; }
        if (wy <= 0f) { ty = y; wy = weight; } else { ty = AngleMath.lerpAngle(ty, y, weight); wy += (1f - wy) * weight; }
        if (wz <= 0f) { tz = z; wz = weight; } else { tz = AngleMath.lerpAngle(tz, z, weight); wz += (1f - wz) * weight; }
        rx *= 1f - weight;
        ry *= 1f - weight;
        rz *= 1f - weight;
    }

    public void overrideX(float x, float weight) {
        if (weight <= 0f) return;
        if (wx <= 0f) { tx = x; wx = weight; } else { tx = AngleMath.lerpAngle(tx, x, weight); wx += (1f - wx) * weight; }
        rx *= 1f - weight;
    }

    public void overrideBend(float value, float weight) {
        if (weight <= 0f) return;
        bend += (value - bend) * weight;
    }

    public void scale(float s) {
        rx *= s; ry *= s; rz *= s;
        px *= s; py *= s; pz *= s;
        bend *= s;
        roll *= Math.min(1f, s);
        float ws = Math.min(1f, s);
        wx *= ws; wy *= ws; wz *= ws;
    }
}
