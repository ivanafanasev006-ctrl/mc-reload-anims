package dev.mcreload.anim.client.pose;

/** The full set of per-part deltas our procedural layer produces for one frame. */
public final class PoseFrame {
    public static final int HEAD = 0, TORSO = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5, BODY = 6;
    public static final int COUNT = 7;

    /** playerAnimator part names. "torso" is the body cube, "body" is the whole player. */
    public static final String[] NAMES = {"head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg", "body"};

    private final PartPose[] parts = new PartPose[COUNT];

    public PoseFrame() {
        for (int i = 0; i < COUNT; i++) parts[i] = new PartPose();
    }

    public PartPose get(int index) {
        return parts[index];
    }

    public PartPose head() { return parts[HEAD]; }
    public PartPose torso() { return parts[TORSO]; }
    public PartPose rightArm() { return parts[RIGHT_ARM]; }
    public PartPose leftArm() { return parts[LEFT_ARM]; }
    public PartPose rightLeg() { return parts[RIGHT_LEG]; }
    public PartPose leftLeg() { return parts[LEFT_LEG]; }
    public PartPose body() { return parts[BODY]; }

    public void reset() {
        for (PartPose p : parts) p.reset();
    }

    public void scale(float s) {
        for (PartPose p : parts) p.scale(s);
    }

    public static int indexOf(String name) {
        return switch (name) {
            case "head" -> HEAD;
            case "torso" -> TORSO;
            case "rightArm" -> RIGHT_ARM;
            case "leftArm" -> LEFT_ARM;
            case "rightLeg" -> RIGHT_LEG;
            case "leftLeg" -> LEFT_LEG;
            case "body" -> BODY;
            default -> -1;
        };
    }

    public static boolean isLeg(int index) {
        return index == RIGHT_LEG || index == LEFT_LEG;
    }
}
