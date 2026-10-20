package dev.mcreload.anim.client.module;

/** Public view of gesture timings for renderers. */
public final class Gestures {
    public static boolean stickOut(float secondsIntoFidget) {
        return GestureModule.stickOut(secondsIntoFidget);
    }

    private Gestures() {}
}
