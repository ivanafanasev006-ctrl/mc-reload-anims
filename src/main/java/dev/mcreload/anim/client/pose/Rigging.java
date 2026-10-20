package dev.mcreload.anim.client.pose;

import net.minecraft.util.Mth;

/** Small building blocks shared by the animation modules. */
public final class Rigging {
    /** One leg segment (thigh or shin): 6 model pixels, in blocks, at the player model scale. */
    public static final float LEG_SEGMENT_BLOCKS = 6f / 16f * 0.9375f;
    /** Distance from the spine to the shoulder pivot, model pixels. */
    private static final float SHOULDER = 5f;

    /**
     * Twist the upper body around the spine. Positive = right shoulder goes back (the same
     * direction vanilla uses at the start of a right-hand swing). The arms are moved with the
     * shoulders, because vanilla arms are not children of the torso.
     */
    public static void twistUpper(PoseFrame f, float angle) {
        if (angle == 0f) return;
        float dx = SHOULDER * (1f - Mth.cos(angle));
        float dz = SHOULDER * Mth.sin(angle);
        f.torso().ry += angle;
        PartPose r = f.rightArm();
        r.px += dx; r.pz += dz; r.ry += angle;
        PartPose l = f.leftArm();
        l.px -= dx; l.pz -= dz; l.ry += angle;
    }

    /**
     * Bend both knees while keeping the feet planted: thighs swing forward by {@code theta},
     * shins fold back by 2*theta and the whole body drops by exactly the height that loses.
     */
    public static void crouch(PoseFrame f, Signs s, float theta) {
        if (theta == 0f) return;
        f.rightLeg().rx -= theta;
        f.leftLeg().rx -= theta;
        f.rightLeg().bend += s.knee() * 2f * theta;
        f.leftLeg().bend += s.knee() * 2f * theta;
        f.body().py -= 2f * LEG_SEGMENT_BLOCKS * (1f - Mth.cos(theta));
        f.torso().bend += s.torso() * 0.5f * theta;
    }

    /** Spread arms sideways. Positive = away from the body on both sides. */
    public static void armsOut(PoseFrame f, float amount) {
        f.rightArm().rz += amount;
        f.leftArm().rz -= amount;
    }

    /** Widen the stance. Positive = feet further apart. */
    public static void legsOut(PoseFrame f, float amount) {
        f.rightLeg().rz += amount;
        f.leftLeg().rz -= amount;
    }

    /** Bend both elbows by the same natural amount. */
    public static void elbows(PoseFrame f, Signs s, float amount) {
        f.rightArm().bend += s.elbow() * amount;
        f.leftArm().bend += s.elbow() * amount;
    }

    /** Lean the upper torso (moves arms and head with it). Positive = forward. */
    public static void leanTorso(PoseFrame f, Signs s, float amount) {
        f.torso().bend += s.torso() * amount;
    }

    private Rigging() {}
}
