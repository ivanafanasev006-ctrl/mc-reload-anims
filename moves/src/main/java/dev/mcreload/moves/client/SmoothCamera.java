package dev.mcreload.moves.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Method;

/**
 * The camera eased (author, 2026-11-17: "why does the camera always jerk so, in third person and in first; the change
 * could be softer").
 *
 * <p>The turns the mod gives the player (facing the wall as a ledge is caught, round its corner in a hop, to the mob in a
 * kick or a combo, about in the slide's attack) went into the player's own turn at once or within a tick or two, and the
 * view turned with it in a jump. They go through {@link #turn} and {@link #turnTo} now: added to the turn a little every
 * frame, as a hand on the mouse turns it, nearly all of it within a fifth of a second. The body still faces the move's way
 * at once; only the view follows it.</p>
 *
 * <p>The cutscene's camera (Cutscene: taken from first person behind the player for the kicks, the chain, the axe's
 * combo, the turns in the air) was cut to behind and back. Now it pulls out from the eyes to its place behind and, as the
 * move ends, in to the eyes again before first person comes back; close to the eyes the player's own body is not drawn
 * (the camera is inside it).</p>
 */
public final class SmoothCamera {
    /** The eased turn's pace (s): about 95 % of it is made in three times this. */
    private static final float TAU = 0.07f;
    /** The camera's way out behind and in again (s). */
    private static final float OUT = 0.35f, IN = 0.25f;
    private static float pending;
    private static long lastFrame;
    /** The cutscene's camera: how far out it is (0 at the eyes, 1 at its place behind), and where it goes. */
    private static float pull = 1f, pullTo = 1f;
    private static boolean pulling;
    private static final Method SET_POSITION = setPosition();

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(SmoothCamera::frame);
        MinecraftForge.EVENT_BUS.addListener(SmoothCamera::camera);
        MinecraftForge.EVENT_BUS.addListener(SmoothCamera::hideSelf);
    }

    /** The player turned this much by a move (degrees, to the right): eased into the view over the next frames. */
    public static void turn(float degrees) {
        pending += degrees;
    }

    /** The player turned by a move to face this way (degrees): eased the same. */
    public static void turnTo(LocalPlayer p, float yaw) {
        pending += Mth.wrapDegrees(yaw - (p.getYRot() + pending));
    }

    /** Where the player's turn is going: their yaw with what is still owed of it. */
    public static float yaw(LocalPlayer p) {
        return p.getYRot() + pending;
    }

    static void reset() {
        pending = 0f;
        pulling = false;
        pull = pullTo = 1f;
    }

    /** The cutscene took the camera behind: from the eyes out (or, coming back in, out again from where it is). */
    static void pullOut(boolean fromEyes) {
        if (fromEyes) pull = 0f;
        pullTo = 1f;
        pulling = true;
    }

    /** The cutscene ends: the camera back in to the eyes; true once it is there (first person may come back). */
    static boolean pullIn() {
        pullTo = 0f;
        pulling = true;
        return pull <= 0.001f;
    }

    /** The cutscene gave the camera back (or lost it to the player's own F5). */
    static void pullDone() {
        pulling = false;
        pull = pullTo = 1f;
    }

    /** Every frame: a part of the turn still owed, as the mouse turns the player (the turn and the one before it). */
    private static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1.0E9f);
        lastFrame = now;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            reset();
            return;
        }
        if (Math.abs(pending) < 0.01f) {
            pending = 0f;
        } else {
            float step = pending * (1f - (float) Math.exp(-dt / TAU));
            p.setYRot(p.getYRot() + step);
            p.yRotO += step;
            pending -= step;
        }
        if (pulling && pull != pullTo) {
            float speed = dt / (pullTo > pull ? OUT : IN);
            pull = pullTo > pull ? Math.min(pullTo, pull + speed) : Math.max(pullTo, pull - speed);
        }
    }

    /** The camera behind, moved in towards the eyes as far as the pull has not got yet. */
    private static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (!pulling || pull >= 0.999f || SET_POSITION == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK || mc.player == null) return;
        Camera cam = event.getCamera();
        Vec3 eye = mc.player.getEyePosition((float) event.getPartialTick());
        float k = pull * pull * (3f - 2f * pull);
        try {
            SET_POSITION.invoke(cam, eye.add(cam.getPosition().subtract(eye).scale(k)));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // the camera stays where vanilla put it
        }
    }

    /** Close to the eyes the camera is inside the player: their own body is not drawn then. */
    private static void hideSelf(RenderPlayerEvent.Pre event) {
        if (pulling && pull < 0.35f && event.getEntity() == Minecraft.getInstance().player
                && Minecraft.getInstance().options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
            event.setCanceled(true);
        }
    }

    /** Puts the camera here (for the time stop's view, TimeStopView); false if it cannot. */
    static boolean place(Camera cam, Vec3 at) {
        if (SET_POSITION == null) return false;
        try {
            SET_POSITION.invoke(cam, at);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static Method setPosition() {
        try {
            return ObfuscationReflectionHelper.findMethod(Camera.class, "m_90581_", Vec3.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private SmoothCamera() {}
}
