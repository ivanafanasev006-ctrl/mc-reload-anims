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

    /**
     * Rolls a limb about its own long axis: given Euler angles (x, y, z) applied the way
     * ModelPart does (R = Rz * Ry * Rx), returns in place the Euler angles of R * Ry(roll).
     * For an arm this turns the wrist, so a held blade sweeps around the hand.
     */
    public static void rollLocalY(float[] xyz, float roll) {
        double cx = Math.cos(xyz[0]), sx = Math.sin(xyz[0]);
        double cy = Math.cos(xyz[1]), sy = Math.sin(xyz[1]);
        double cz = Math.cos(xyz[2]), sz = Math.sin(xyz[2]);
        // R = Rz Ry Rx (row-major)
        double r00 = cz * cy, r01 = cz * sy * sx - sz * cx, r02 = cz * sy * cx + sz * sx;
        double r10 = sz * cy, r11 = sz * sy * sx + cz * cx, r12 = sz * sy * cx - cz * sx;
        double r20 = -sy,     r21 = cy * sx,                r22 = cy * cx;
        double cr = Math.cos(roll), sr = Math.sin(roll);
        // M = R * Ry(roll); Ry = [[cr,0,sr],[0,1,0],[-sr,0,cr]]
        double m00 = r00 * cr - r02 * sr;
        double m10 = r10 * cr - r12 * sr;
        double m20 = r20 * cr - r22 * sr;
        double m21 = r21;
        double m22 = r20 * sr + r22 * cr;
        double m01 = r01, m11 = r11;
        double sinY = Math.max(-1.0, Math.min(1.0, -m20));
        double y = Math.asin(sinY);
        double x, z;
        if (Math.abs(sinY) < 0.9999) {
            x = Math.atan2(m21, m22);
            z = Math.atan2(m10, m00);
        } else {
            // Gimbal lock: put everything into z.
            x = 0.0;
            z = Math.atan2(-m01, m11);
        }
        // Every rotation has two Euler triples; keep the one nearest the unrolled pose so the
        // limb springs never see a sudden flip.
        double ax = x + Math.PI, ay = Math.PI - y, az = z + Math.PI;
        double d1 = dist(x, xyz[0]) + dist(y, xyz[1]) + dist(z, xyz[2]);
        double d2 = dist(ax, xyz[0]) + dist(ay, xyz[1]) + dist(az, xyz[2]);
        if (d2 < d1) { x = ax; y = ay; z = az; }
        xyz[0] = (float) (xyz[0] + wrap(x - xyz[0]));
        xyz[1] = (float) (xyz[1] + wrap(y - xyz[1]));
        xyz[2] = (float) (xyz[2] + wrap(z - xyz[2]));
    }

    /**
     * Euler angles (x, y, z) such that ModelPart's rotationZYX(z, y, x) reproduces {@code q}.
     */
    public static void toModelEuler(org.joml.Quaternionf q, float[] out) {
        org.joml.Vector3f c0 = q.transform(new org.joml.Vector3f(1f, 0f, 0f));
        org.joml.Vector3f c1 = q.transform(new org.joml.Vector3f(0f, 1f, 0f));
        org.joml.Vector3f c2 = q.transform(new org.joml.Vector3f(0f, 0f, 1f));
        // Columns of R = Rz Ry Rx: R20 = c0.z, R21 = c1.z, R22 = c2.z, R10 = c0.y, R00 = c0.x.
        double sy = Math.max(-1.0, Math.min(1.0, -c0.z()));
        out[1] = (float) Math.asin(sy);
        if (Math.abs(sy) < 0.9999) {
            out[0] = (float) Math.atan2(c1.z(), c2.z());
            out[2] = (float) Math.atan2(c0.y(), c0.x());
        } else {
            out[0] = 0f;
            out[2] = (float) Math.atan2(-c1.x(), c1.y());
        }
    }

    private static double wrap(double d) {
        d %= Math.PI * 2.0;
        if (d > Math.PI) d -= Math.PI * 2.0;
        if (d < -Math.PI) d += Math.PI * 2.0;
        return d;
    }

    private static double dist(double a, double b) {
        return Math.abs(wrap(a - b));
    }

    private AngleMath() {}
}
