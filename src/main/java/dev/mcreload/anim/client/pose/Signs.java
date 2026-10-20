package dev.mcreload.anim.client.pose;

/**
 * Bend direction conventions, kept in one place so they can be flipped from the config.
 * A positive value from the modules always means the anatomically natural direction:
 * knees fold backwards, elbows fold forwards, the upper torso leans forwards.
 */
public record Signs(float knee, float elbow, float torso) {
    public static final Signs DEFAULT = new Signs(1f, -1f, 1f);

    public static Signs of(boolean invertKnee, boolean invertElbow, boolean invertTorso) {
        return new Signs(
                DEFAULT.knee * (invertKnee ? -1f : 1f),
                DEFAULT.elbow * (invertElbow ? -1f : 1f),
                DEFAULT.torso * (invertTorso ? -1f : 1f));
    }
}
