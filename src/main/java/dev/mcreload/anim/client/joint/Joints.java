package dev.mcreload.anim.client.joint;

import dev.mcreload.anim.McReloadAnim;

/**
 * Global switches for the jointed model.
 *
 * <p>playerAnimator only bends limbs on Forge when bendy-lib is installed. Without it every elbow,
 * knee and torso bend is silently dropped. We draw the joints ourselves instead; if bendy-lib is
 * present we step aside and let it do the bending, so nothing bends twice.</p>
 */
public final class Joints {
    private static final boolean BENDY = hasClass("io.github.kosmx.bendylib.impl.ICuboid");
    /** Set while the first-person hand is drawn: arms stay straight there. */
    public static boolean firstPerson;

    static {
        McReloadAnim.LOG.info("[mcreload] Joints: {}", BENDY
                ? "bendy-lib found, it will bend the limbs"
                : "drawing elbows, knees and waist ourselves");
    }

    /** Whether models should be cut into jointed halves at all. */
    public static boolean enabled() {
        return !BENDY;
    }

    public static boolean active() {
        return !BENDY;
    }

    private static boolean hasClass(String name) {
        try {
            Class.forName(name, false, Joints.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private Joints() {}
}
