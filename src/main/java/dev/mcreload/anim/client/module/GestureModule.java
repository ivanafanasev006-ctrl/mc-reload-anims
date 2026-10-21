package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.math.AngleMath;
import dev.mcreload.anim.client.pose.PartPose;
import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.util.Mth;

/**
 * Little things people do.
 * <ul>
 *   <li>Swapping items between hands: both hands meet in front of the chest and part again.</li>
 *   <li>Idle fidgets after standing still for a while (each 3-4 s, every 5-10 s, can be turned
 *       off with {@code modules.idleActions}): a stretch, scratching the head then shading the
 *       eyes to look into the distance, pulling a stick out of the pocket and fencing with the air,
 *       hands on hips looking around.</li>
 * </ul>
 */
final class GestureModule implements AnimModule {
    static final float SWAP_SECONDS = 0.5f;

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float gate = m.gate.v;
        if (gate <= 0.001f) return;
        swap(m, f, s, gate);
        if (m.idleAction >= 0 && Cfg.idleActions) {
            float w = m.idleW.v * gate;
            if (w > 0.001f) idle(m, f, s, w, m.since(m.idleActionStart));
        }
    }

    private static void swap(MotionState m, PoseFrame f, Signs s, float gate) {
        float t = m.since(m.swapStart) / SWAP_SECONDS;
        if (t < 0f || t >= 1f) return;
        float e = Mth.sin(Mth.PI * t) * gate * m.armsFree.v;
        // Hands come together in front of the chest, then go back to where they were.
        f.rightArm().override(-1.05f, -0.55f, 0f, e);
        f.leftArm().override(-1.05f, 0.55f, 0f, e);
        f.rightArm().overrideBend(s.elbow() * 1.0f, e);
        f.leftArm().overrideBend(s.elbow() * 1.0f, e);
        f.head().rx += 0.35f * e;
        Rigging.leanTorso(f, s, 0.06f * e);
    }

    // ------------------------------------------------------------------ idle fidgets

    private static void idle(MotionState m, PoseFrame f, Signs s, float w, float t) {
        float dur = MotionState.idleDuration(m.idleAction);
        float env = AngleMath.ease(t / 0.35f) * (1f - AngleMath.ease((t - (dur - 0.45f)) / 0.45f)) * w;
        if (env <= 0.001f) return;
        float side = m.mainRight ? 1f : -1f;
        PartPose main = side > 0 ? f.rightArm() : f.leftArm();
        PartPose off = side > 0 ? f.leftArm() : f.rightArm();
        float aw = env * m.armsFree.v;
        switch (m.idleAction) {
            case MotionState.IDLE_STRETCH -> stretch(f, s, env, aw, t);
            case MotionState.IDLE_SCRATCH -> scratch(f, s, env, aw, t, main, off, side);
            case MotionState.IDLE_SHADOWBOX -> shadowbox(f, s, env, aw, t, main, off, side);
            case MotionState.IDLE_YAWN -> yawn(f, s, env, aw, t, main, off, side);
            case MotionState.IDLE_HANDS -> lookAtHands(f, s, env, aw, t);
            case MotionState.IDLE_NECK -> neck(f, s, env, aw, t);
            case MotionState.IDLE_IMPATIENT -> impatient(f, s, env, aw, t, side);
            case MotionState.IDLE_KICK -> kickPebble(f, s, env, aw, t, side);
            case MotionState.IDLE_SQUATS -> squats(f, s, env, aw, t);
            case MotionState.IDLE_ITEM_SPIN -> itemSpin(m, f, s, env, aw, t, main, off, side);
            case MotionState.IDLE_ITEM_INSPECT -> itemInspect(m, f, s, env, aw, t, main, off, side);
            case MotionState.IDLE_ITEM_TOSS -> itemToss(m, f, s, env, aw, t, main, off, side);
            case MotionState.IDLE_ITEM_PRACTICE -> itemPractice(m, f, s, env, aw, t, main, off, side);
            case MotionState.IDLE_ITEM_SHOULDER -> itemShoulder(m, f, s, env, aw, t, main, off, side);
            case MotionState.IDLE_ITEM_LEAN -> itemLean(m, f, s, env, aw, t, main, off, side);
            case MotionState.IDLE_FLINT -> flint(m, f, s, env, aw, t, main, off, side);
            case MotionState.IDLE_BUCKET -> bucket(m, f, s, env, aw, t, main, off, side);
            case MotionState.IDLE_ROD -> rod(m, f, s, env, aw, t, main, off, side);
            case MotionState.IDLE_LANTERN -> lantern(m, f, s, env, aw, t, main, off, side);
            case MotionState.IDLE_BLOCK -> blockInspect(m, f, s, env, aw, t, main, off, side);
            default -> lookAround(f, s, env, aw, t, m);
        }
    }

    // ------------------------------------------------------------------ playing with the held item

    /**
     * A flourish: the arm comes out to the side, the weapon / tool twirls round twice in the
     * fingers with the wrist circling, then it is swept down and brought back to rest, the eyes
     * following it.
     */
    private static void itemSpin(MotionState m, PoseFrame f, Signs s, float e, float aw, float t,
                                 PartPose main, PartPose off, float side) {
        float out = seg(t, 0f, 0.4f) * (1f - seg(t, 2.3f, 2.9f));
        float spin = seg(t, 0.35f, 1.9f);
        float circle = Mth.sin(t * 10f) * spin * (1f - spin) * 4f;
        main.override(-1.15f + 0.15f * circle, side * (0.55f + 0.10f * Mth.cos(t * 10f) * spin * (1f - spin) * 4f),
                side * 0.30f, aw * out);
        main.overrideBend(s.elbow() * 0.45f, aw * out);
        m.itemTiltTarget = 2f * Mth.TWO_PI * spin * e;
        Rigging.twistUpper(f, side * 0.15f * out * e);
        f.head().ry -= side * 0.35f * out * e;
        f.head().rx += 0.10f * out * e;
    }

    /**
     * Looking it over: brought up in front of the face and turned this way and that, the other
     * hand running along it from below; then lowered with a satisfied nod.
     */
    private static void itemInspect(MotionState m, PoseFrame f, Signs s, float e, float aw, float t,
                                    PartPose main, PartPose off, float side) {
        float up = seg(t, 0f, 0.5f) * (1f - seg(t, 3.0f, 3.5f));
        main.override(-1.55f, -side * 0.35f, side * 0.10f, aw * up);
        main.overrideBend(s.elbow() * 1.05f, aw * up);
        main.overrideRoll(side * 0.85f * Mth.sin(t * 1.4f) * seg(t, 0.4f, 0.9f), aw * up);
        // The free hand strokes along it, from the hilt outward, twice.
        float stroke = (t * 0.9f) % 1f;
        float sw = seg(t, 0.8f, 1.1f) * (1f - seg(t, 2.6f, 2.9f));
        off.override(-1.20f - 0.45f * stroke, side * 0.50f, -side * 0.05f, aw * sw);
        off.overrideBend(s.elbow() * (1.20f - 0.60f * stroke), aw * sw);
        f.head().rx += (0.20f + 0.10f * seg(t, 2.9f, 3.2f)) * up * e;
        f.head().ry -= side * 0.15f * up * e;
    }

    /**
     * Tossing it up and catching it: a dip, a flick of the wrist, the item flipping over once in
     * the air (in the fingers), the eyes following it up, a little give in the knees on the catch.
     */
    private static void itemToss(MotionState m, PoseFrame f, Signs s, float e, float aw, float t,
                                 PartPose main, PartPose off, float side) {
        float dip = Mth.sin(Mth.PI * Mth.clamp(t / 0.4f, 0f, 1f));
        float flick = seg(t, 0.35f, 0.55f) * (1f - seg(t, 1.05f, 1.35f));
        float flip = seg(t, 0.40f, 1.15f);
        float catchDip = Mth.sin(Mth.PI * Mth.clamp((t - 1.15f) / 0.45f, 0f, 1f));
        float hold = seg(t, 0f, 0.3f) * (1f - seg(t, 1.9f, 2.3f));
        main.override(-0.95f - 1.25f * flick + 0.25f * dip + 0.20f * catchDip, -side * 0.15f, side * 0.10f, aw * hold);
        main.overrideBend(s.elbow() * (0.90f - 0.55f * flick), aw * hold);
        m.itemTiltTarget = Mth.TWO_PI * flip * e;
        f.head().rx -= (0.45f * flick - 0.15f * catchDip) * e;
        Rigging.crouch(f, s, (0.10f * dip + 0.15f * catchDip) * e);
    }

    /** A couple of practice swings at the air, the body turning into each one. */
    private static void itemPractice(MotionState m, PoseFrame f, Signs s, float e, float aw, float t,
                                     PartPose main, PartPose off, float side) {
        float hold = seg(t, 0f, 0.3f) * (1f - seg(t, 2.5f, 2.9f));
        float sw1 = seg(t, 0.45f, 0.75f), sw2 = seg(t, 1.45f, 1.75f);
        float wind = seg(t, 0.15f, 0.45f) * (1f - sw1) + seg(t, 1.15f, 1.45f) * (1f - sw2) * sw1;
        float through = Math.max(sw1 * (1f - seg(t, 0.9f, 1.15f)), sw2 * (1f - seg(t, 1.9f, 2.4f)));
        main.override(-1.0f - 1.6f * wind + 0.5f * through, side * (0.45f * wind - 0.55f * through),
                side * 0.20f, aw * hold);
        main.overrideBend(s.elbow() * (0.6f + 0.6f * wind - 0.4f * through), aw * hold);
        m.itemTiltTarget = 1.0f * (wind + through) * e;
        Rigging.twistUpper(f, side * (0.35f * wind - 0.40f * through) * e);
        Rigging.leanTorso(f, s, 0.15f * through * e);
        PartPose lead = side > 0 ? f.leftLeg() : f.rightLeg();
        lead.rx -= 0.25f * hold * e;
        lead.bend += s.knee() * 0.25f * hold * e;
    }

    /** Resting it on the shoulder, the other hand on the hip, weight on one leg, looking about. */
    private static void itemShoulder(MotionState m, PoseFrame f, Signs s, float e, float aw, float t,
                                     PartPose main, PartPose off, float side) {
        float up = seg(t, 0f, 0.6f) * (1f - seg(t, 3.4f, 4.0f));
        main.override(-2.25f, -side * 0.15f, side * 0.20f, aw * up);
        main.overrideBend(s.elbow() * 1.90f, aw * up);
        m.itemTiltTarget = 2.30f * up * e;
        off.override(0.25f, 0f, -side * 0.55f, aw * up);
        off.overrideBend(s.elbow() * -1.30f, aw * up);
        f.body().rz -= side * 0.04f * up * e;
        PartPose rest = side > 0 ? f.leftLeg() : f.rightLeg();
        rest.bend += s.knee() * 0.30f * up * e;
        rest.rz -= side * 0.08f * up * e;
        f.head().ry += 0.45f * Mth.sin(t * 0.9f) * up * e;
    }

    /** Planting it on the ground in front and leaning on it with both hands on top. */
    private static void itemLean(MotionState m, PoseFrame f, Signs s, float e, float aw, float t,
                                 PartPose main, PartPose off, float side) {
        float lean = seg(t, 0f, 0.7f) * (1f - seg(t, 3.8f, 4.5f));
        main.override(-0.80f, -side * 0.25f, 0f, aw * lean);
        main.overrideBend(s.elbow() * 0.35f, aw * lean);
        m.itemTiltTarget = Mth.PI * lean * e;
        off.override(-0.85f, side * 0.45f, 0f, aw * lean);
        off.overrideBend(s.elbow() * 0.45f, aw * lean);
        Rigging.leanTorso(f, s, 0.18f * lean * e);
        PartPose cross = side > 0 ? f.rightLeg() : f.leftLeg();
        cross.rz += side * 0.10f * lean * e;
        cross.bend += s.knee() * 0.25f * lean * e;
        f.head().ry += 0.30f * Mth.sin(t * 0.8f) * lean * e;
    }

    /** Clicking the flint and steel together twice, looking at the sparks. */
    private static void flint(MotionState m, PoseFrame f, Signs s, float e, float aw, float t,
                              PartPose main, PartPose off, float side) {
        float up = seg(t, 0f, 0.4f) * (1f - seg(t, 2.1f, 2.6f));
        float click = Math.max(Mth.sin(Mth.PI * Mth.clamp((t - 0.8f) / 0.25f, 0f, 1f)),
                Mth.sin(Mth.PI * Mth.clamp((t - 1.4f) / 0.25f, 0f, 1f)));
        main.override(-1.15f + 0.35f * click, -side * 0.40f, 0f, aw * up);
        main.overrideBend(s.elbow() * 1.15f, aw * up);
        off.override(-1.10f, side * 0.45f, 0f, aw * up);
        off.overrideBend(s.elbow() * 1.25f, aw * up);
        f.head().rx += 0.30f * up * e;
    }

    /** Peering into the bucket, tipping it, then swinging it idly at the side. */
    private static void bucket(MotionState m, PoseFrame f, Signs s, float e, float aw, float t,
                               PartPose main, PartPose off, float side) {
        float peek = seg(t, 0f, 0.5f) * (1f - seg(t, 1.6f, 2.0f));
        main.override(-1.65f, -side * 0.30f, 0f, aw * peek);
        main.overrideBend(s.elbow() * 1.35f, aw * peek);
        main.overrideRoll(side * 0.55f * seg(t, 0.5f, 1.0f), aw * peek);
        f.head().rx += 0.35f * peek * e;
        float swing = seg(t, 1.7f, 2.0f) * (1f - seg(t, 2.7f, 3.2f));
        main.override(0.45f * Mth.sin((t - 1.7f) * 5f), 0f, side * 0.12f, aw * swing);
        main.overrideBend(0f, aw * swing);
    }

    /** Checking the rod: the free hand pulls at the line, then a test flick. */
    private static void rod(MotionState m, PoseFrame f, Signs s, float e, float aw, float t,
                            PartPose main, PartPose off, float side) {
        float hold = seg(t, 0f, 0.4f) * (1f - seg(t, 2.5f, 3.0f));
        float flick = Mth.sin(Mth.PI * Mth.clamp((t - 1.7f) / 0.35f, 0f, 1f));
        main.override(-1.20f - 0.50f * flick, -side * 0.10f, side * 0.10f, aw * hold);
        main.overrideBend(s.elbow() * (0.90f - 0.40f * flick), aw * hold);
        float pull = seg(t, 0.4f, 0.7f) * (1f - seg(t, 1.4f, 1.6f));
        off.override(-2.00f + 0.25f * Mth.sin(t * 6f) * pull, side * 0.30f, 0f, aw * pull);
        off.overrideBend(s.elbow() * 0.60f, aw * pull);
        f.head().rx -= 0.20f * pull * e;
    }

    /** Holding the lantern up at eye level, giving it a shake, looking around by its light. */
    private static void lantern(MotionState m, PoseFrame f, Signs s, float e, float aw, float t,
                                PartPose main, PartPose off, float side) {
        float up = seg(t, 0f, 0.5f) * (1f - seg(t, 2.7f, 3.2f));
        float shake = Mth.sin(t * 15f) * Mth.sin(Mth.PI * Mth.clamp((t - 0.6f) / 0.8f, 0f, 1f));
        main.override(-2.00f + 0.10f * shake, -side * 0.15f, side * 0.05f * shake, aw * up);
        main.overrideBend(s.elbow() * 0.85f, aw * up);
        f.head().ry += 0.55f * Mth.sin((t - 1.4f) * 1.6f) * seg(t, 1.4f, 1.8f) * up * e;
        f.head().rx -= 0.10f * up * e;
    }

    /** Turning a block over in the hand, looking at each side, the other hand steadying it. */
    private static void blockInspect(MotionState m, PoseFrame f, Signs s, float e, float aw, float t,
                                     PartPose main, PartPose off, float side) {
        float up = seg(t, 0f, 0.5f) * (1f - seg(t, 2.9f, 3.4f));
        main.override(-1.30f, -side * 0.35f, 0f, aw * up);
        main.overrideBend(s.elbow() * 1.10f, aw * up);
        main.overrideRoll(side * 1.0f * Mth.sin(t * 1.3f) * seg(t, 0.4f, 0.8f), aw * up);
        m.itemTiltTarget = 0.6f * Mth.sin(t * 0.9f) * seg(t, 0.4f, 0.8f) * up * e;
        float touch = seg(t, 1.0f, 1.3f) * (1f - seg(t, 2.2f, 2.5f));
        off.override(-1.20f, side * 0.50f, 0f, aw * touch);
        off.overrideBend(s.elbow() * 1.10f, aw * touch);
        f.head().rx += 0.25f * up * e;
    }

    /** Arms up overhead, lean to each side, twist the shoulders round, arms back down. */
    private static void stretch(PoseFrame f, Signs s, float e, float aw, float t) {
        // Keys: arms up (0.3-0.9), lean right (1.4), lean left (2.0), arms out and twist (2.3-3.0).
        float up = seg(t, 0.2f, 0.9f) * (1f - seg(t, 2.1f, 2.4f));
        float out = seg(t, 2.1f, 2.4f) * (1f - seg(t, 3.0f, 3.5f));
        float lean = seg(t, 0.9f, 1.4f) - 2f * seg(t, 1.4f, 2.0f) + seg(t, 2.0f, 2.3f);
        float twist = seg(t, 2.3f, 2.6f) - 2f * seg(t, 2.6f, 2.95f) + seg(t, 2.95f, 3.2f);

        float armX = -3.05f * up + (-1.45f) * out;
        float armZ = 0.18f * up + 1.25f * out;
        float wArm = aw * Math.max(up, out);
        f.rightArm().override(armX, 0f, armZ, wArm);
        f.leftArm().override(armX, 0f, -armZ, wArm);
        f.rightArm().overrideBend(s.elbow() * 0.05f, wArm);
        f.leftArm().overrideBend(s.elbow() * 0.05f, wArm);
        // Arch the back and rise onto the toes at the top of the stretch.
        Rigging.leanTorso(f, s, -0.18f * up * e);
        f.head().rx -= 0.30f * up * e;
        f.body().py += 0.03f * up * e;
        f.body().rz += 0.22f * lean * e;
        f.head().rz += 0.10f * lean * e;
        Rigging.twistUpper(f, 0.50f * twist * e);
        f.head().ry -= 0.25f * twist * e;
    }

    /** Scratch the top of the head, then shade the eyes with the hand and gaze into the distance. */
    private static void scratch(PoseFrame f, Signs s, float e, float aw, float t, PartPose main, PartPose off, float side) {
        float scratching = 1f - seg(t, 1.5f, 1.9f);
        float shading = seg(t, 1.5f, 1.9f);
        float rub = Mth.sin(t * 26f) * seg(t, 0.4f, 0.6f) * (1f - seg(t, 1.3f, 1.5f));
        main.override(Mth.lerp(shading, -2.75f, -2.25f), side * Mth.lerp(shading, -0.15f, -0.55f),
                side * (Mth.lerp(shading, 0.30f, 0.05f) + 0.07f * rub), aw);
        main.overrideBend(s.elbow() * (Mth.lerp(shading, 1.55f, 2.0f) + 0.12f * rub), aw);
        // Other hand on the hip.
        off.override(0.15f, 0f, -side * 0.50f, aw);
        off.overrideBend(s.elbow() * 1.45f, aw);
        // Head tilted, a little puzzled, then up and scanning the horizon.
        f.head().rz += side * 0.16f * scratching * e;
        f.head().rx += 0.15f * scratching * e;
        f.head().rx -= 0.18f * shading * e;
        f.head().ry += 0.55f * Mth.sin((t - 1.7f) * 1.6f) * shading * e;
        f.body().rz -= side * 0.04f * e;
    }

    /**
     * Pulls a stick out of the pocket and fences with the air: an overhead cut, a sweep, a lunge,
     * then puts it back. (The stick itself is drawn by {@code IdleProps}.)
     */
    private static void shadowbox(PoseFrame f, Signs s, float e, float aw, float t, PartPose main, PartPose off, float side) {
        float k = Mth.clamp(t, 0f, SHADOW[SHADOW.length - 1][0]);
        int i = 0;
        while (i < SHADOW.length - 2 && k > SHADOW[i + 1][0]) i++;
        float[] a = SHADOW[i], b = SHADOW[i + 1];
        float u = AngleMath.ease((k - a[0]) / (b[0] - a[0]));
        float armX = Mth.lerp(u, a[1], b[1]), armY = Mth.lerp(u, a[2], b[2]), armZ = Mth.lerp(u, a[3], b[3]);
        float elbow = Mth.lerp(u, a[4], b[4]), twist = Mth.lerp(u, a[5], b[5]), lean = Mth.lerp(u, a[6], b[6]);
        float lunge = Mth.lerp(u, a[7], b[7]);
        main.override(armX, side * armY, side * armZ, aw);
        main.overrideBend(s.elbow() * elbow, aw);
        // Free hand up behind for balance like a fencer.
        float fence = seg(t, 0.6f, 0.9f) * (1f - seg(t, 2.1f, 2.5f));
        off.override(Mth.lerp(fence, 0f, -2.3f), 0f, -side * Mth.lerp(fence, 0.1f, 0.9f), aw);
        off.overrideBend(s.elbow() * Mth.lerp(fence, 0.2f, 1.4f), aw);
        Rigging.twistUpper(f, side * twist * e);
        f.body().rx += lean * e;
        f.head().rx += lean * 0.8f * e;
        f.head().rx += 0.30f * (1f - seg(t, 0.4f, 0.6f)) * e;   // glance at the pocket
        PartPose lead = side > 0 ? f.rightLeg() : f.leftLeg();
        PartPose rear = side > 0 ? f.leftLeg() : f.rightLeg();
        lead.overrideX(-0.75f * lunge, e * lunge);
        lead.overrideBend(s.knee() * 0.9f * lunge, e * lunge);
        rear.overrideX(0.45f * lunge, e * lunge);
        rear.overrideBend(s.knee() * 0.2f * lunge, e * lunge);
        f.body().py -= 0.10f * lunge * e;
    }

    /** t, armX, armY, armZ, elbow, twist, lean, lunge (right-handed). */
    private static final float[][] SHADOW = {
            {0.00f, 0.00f, 0.00f, 0.10f, 0.10f, 0.00f, 0.00f, 0f},
            {0.40f, 0.30f, 0.00f, 0.12f, 0.70f, 0.10f, 0.00f, 0f},   // hand in the pocket
            {0.85f, -2.70f, 0.20f, 0.25f, 1.30f, 0.30f, 0.06f, 0f},  // stick raised overhead
            {1.10f, -0.70f, -0.40f, 0.00f, 0.10f, -0.35f, -0.20f, 0.6f}, // cut down
            {1.40f, -1.35f, 1.30f, 0.60f, 0.60f, 0.70f, 0.00f, 0.2f},  // wind up wide
            {1.65f, -1.40f, -1.20f, -0.20f, 0.05f, -0.70f, -0.15f, 0.5f}, // sweep across
            {2.00f, -1.57f, 0.00f, 0.05f, 0.00f, 0.25f, -0.18f, 1.0f},  // lunge
            {2.20f, -1.57f, 0.00f, 0.05f, 0.00f, 0.25f, -0.18f, 1.0f},
            {2.60f, 0.30f, 0.00f, 0.12f, 0.70f, 0.10f, 0.00f, 0f},     // back in the pocket
            {3.20f, 0.00f, 0.00f, 0.10f, 0.10f, 0.00f, 0.00f, 0f},
    };

    /** While the stick is out of the pocket (seconds into the shadow-boxing fidget). */
    static boolean stickOut(float t) {
        return t > 0.55f && t < 2.5f;
    }

    /** Hands on hips, shifting weight, looking slowly left and right. */
    private static void lookAround(PoseFrame f, Signs s, float e, float aw, float t, MotionState m) {
        f.rightArm().override(0.20f, 0f, 0.55f, aw);
        f.leftArm().override(0.20f, 0f, -0.55f, aw);
        f.rightArm().overrideBend(s.elbow() * 1.50f, aw);
        f.leftArm().overrideBend(s.elbow() * 1.50f, aw);
        float look = Mth.sin(t * 1.9f);
        f.head().ry += 0.75f * look * e;
        f.head().rx -= 0.08f * e;
        Rigging.twistUpper(f, -0.12f * look * e);
        f.body().rz += 0.05f * Mth.sin(t * 0.95f) * e;
        f.body().px += 0.03f * Mth.sin(t * 0.95f) * e;
        Rigging.leanTorso(f, s, -0.05f * e);
    }

    /** Big yawn: one arm stretches up, the other hand covers the mouth, head tipped back. */
    private static void yawn(PoseFrame f, Signs s, float e, float aw, float t, PartPose main, PartPose off, float side) {
        float open = seg(t, 0.3f, 1.0f) * (1f - seg(t, 2.0f, 2.6f));
        main.override(-1.70f, side * -0.70f, 0f, aw * open);
        main.overrideBend(s.elbow() * 2.0f, aw * open);
        off.override(-2.90f, 0f, -side * 0.30f, aw * open);
        off.overrideBend(s.elbow() * 0.40f, aw * open);
        f.head().rx -= 0.35f * open * e;
        Rigging.leanTorso(f, s, -0.12f * open * e);
        f.body().py += 0.02f * open * e;
        // A shiver at the end.
        f.body().rz += 0.03f * Mth.sin(t * 30f) * seg(t, 2.0f, 2.2f) * (1f - seg(t, 2.4f, 2.6f)) * e;
    }

    /** Holds the hands up and inspects them, turning them over, glancing from one to the other. */
    private static void lookAtHands(PoseFrame f, Signs s, float e, float aw, float t) {
        float turn = Mth.sin(t * 2.4f);
        f.rightArm().override(-1.25f, -0.25f, 0.05f, aw);
        f.leftArm().override(-1.25f, 0.25f, -0.05f, aw);
        f.rightArm().overrideBend(s.elbow() * 1.3f, aw);
        f.leftArm().overrideBend(s.elbow() * 1.3f, aw);
        f.rightArm().overrideRoll(0.9f * turn, aw);
        f.leftArm().overrideRoll(-0.9f * turn, aw);
        f.head().rx += 0.55f * e;
        f.head().ry += 0.25f * Mth.sin(t * 1.3f) * e;
    }

    /** Tilts the head to each side to crack the neck, then rolls the shoulders. */
    private static void neck(PoseFrame f, Signs s, float e, float aw, float t) {
        float tilt = seg(t, 0.2f, 0.6f) - 2f * seg(t, 0.8f, 1.2f) + seg(t, 1.4f, 1.6f);
        f.head().rz += 0.45f * tilt * e;
        f.head().rx += 0.10f * Math.abs(tilt) * e;
        float roll = seg(t, 1.5f, 1.6f) * (1f - seg(t, 2.3f, 2.5f));
        float c = Mth.sin((t - 1.5f) * 9f) * roll;
        f.rightArm().rx += 0.25f * c * aw;
        f.leftArm().rx += 0.25f * c * aw;
        f.rightArm().rz += 0.15f * roll * aw;
        f.leftArm().rz -= 0.15f * roll * aw;
        f.torso().py -= 0.6f * Math.max(0f, c) * e;
    }

    /** Arms folded across the chest, tapping a foot, sighing. */
    private static void impatient(PoseFrame f, Signs s, float e, float aw, float t, float side) {
        f.rightArm().override(-0.80f, -0.75f, 0.05f, aw);
        f.leftArm().override(-0.70f, 0.75f, -0.05f, aw);
        f.rightArm().overrideBend(s.elbow() * 1.85f, aw);
        f.leftArm().overrideBend(s.elbow() * 1.85f, aw);
        PartPose foot = side > 0 ? f.rightLeg() : f.leftLeg();
        float tap = Math.max(0f, Mth.sin(t * 11f)) * seg(t, 0.5f, 0.7f) * (1f - seg(t, 2.8f, 3.0f));
        foot.overrideX(-0.15f, e);
        foot.overrideBend(s.knee() * (0.15f + 0.25f * tap), e);
        foot.py -= 0.8f * tap * e;
        float sigh = Mth.sin(Mth.PI * AngleMath.clamp((t - 1.6f) / 0.9f, 0f, 1f));
        f.head().rx -= 0.25f * sigh * e;
        Rigging.leanTorso(f, s, -0.08f * sigh * e);
        f.body().rz += side * 0.04f * e;
    }

    /** Looks down and kicks a pebble away. */
    private static void kickPebble(PoseFrame f, Signs s, float e, float aw, float t, float side) {
        PartPose foot = side > 0 ? f.rightLeg() : f.leftLeg();
        float back = seg(t, 0.4f, 0.9f) * (1f - seg(t, 0.9f, 1.05f));
        float kick = seg(t, 0.9f, 1.05f) * (1f - seg(t, 1.4f, 1.9f));
        foot.overrideX(0.45f * back - 0.85f * kick, e * Math.max(back, kick));
        foot.overrideBend(s.knee() * (0.9f * back + 0.1f * kick), e * Math.max(back, kick));
        f.rightArm().rz += 0.15f * kick * aw;
        f.leftArm().rz -= 0.15f * kick * aw;
        f.rightArm().rx += 0.25f * kick * side * aw;
        f.leftArm().rx -= 0.25f * kick * side * aw;
        // Eyes on the ground, then following the pebble away.
        f.head().rx += (0.55f - 0.35f * seg(t, 1.0f, 1.6f)) * e;
        f.body().rx += 0.06f * kick * e;
    }

    /** Two quick squats, arms straight out in front. */
    private static void squats(PoseFrame f, Signs s, float e, float aw, float t) {
        float down = Math.max(0f, Mth.sin((t - 0.4f) / 1.3f * Mth.TWO_PI - Mth.HALF_PI) * 0.5f + 0.5f)
                * seg(t, 0.3f, 0.5f) * (1f - seg(t, 2.9f, 3.2f));
        f.rightArm().override(-1.55f, 0f, 0.05f, aw);
        f.leftArm().override(-1.55f, 0f, -0.05f, aw);
        f.rightArm().overrideBend(0f, aw);
        f.leftArm().overrideBend(0f, aw);
        Rigging.crouch(f, s, 0.95f * down * e);
        Rigging.leanTorso(f, s, 0.35f * down * e);
        f.head().rx -= 0.30f * down * e;
        Rigging.legsOut(f, 0.12f * e);
    }

    private static float seg(float t, float a, float b) {
        return AngleMath.ease((t - a) / (b - a));
    }
}
