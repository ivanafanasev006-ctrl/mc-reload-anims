package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.math.AngleMath;
import dev.mcreload.anim.client.pose.PartPose;
import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import net.minecraft.util.Mth;

/**
 * Emotes from the emote menu: dancing, waving, clapping, facepalm, shrug, salute, bow, sitting
 * down, thumbs up, victory, pointing, laughing, crying, the floss and push-ups. Looping ones play
 * until you move; the rest end on their own. Any movement, attack or hit cancels them.
 */
final class EmoteModule implements AnimModule {
    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        if (m.emote < 0 && m.emoteW.v < 0.01f) return;
        float w = m.emoteW.v * m.gate.v;
        if (w <= 0.001f || m.emote < 0) return;
        float t = m.since(m.emoteStart);
        float d = MotionState.emoteDuration(m.emote);
        float env = d > 0f ? w * (1f - AngleMath.ease((t - (d - 0.35f)) / 0.35f)) : w;
        float aw = env * m.armsFree.v;
        float side = m.mainRight ? 1f : -1f;
        PartPose main = side > 0 ? f.rightArm() : f.leftArm();
        PartPose off = side > 0 ? f.leftArm() : f.rightArm();
        switch (m.emote) {
            case MotionState.EMOTE_DANCE -> dance(f, s, env, aw, t);
            case MotionState.EMOTE_WAVE -> wave(f, s, env, aw, t, main, side);
            case MotionState.EMOTE_CLAP -> clap(f, s, env, aw, t);
            case MotionState.EMOTE_FACEPALM -> facepalm(f, s, env, aw, t, main, side);
            case MotionState.EMOTE_SHRUG -> shrug(f, s, env, aw, t);
            case MotionState.EMOTE_SALUTE -> salute(f, s, env, aw, t, main, side);
            case MotionState.EMOTE_BOW -> bow(f, s, env, aw, t, main, off, side);
            case MotionState.EMOTE_SIT -> sit(f, s, env, aw, t);
            case MotionState.EMOTE_THUMBS -> thumbs(f, s, env, aw, t, main, side);
            case MotionState.EMOTE_VICTORY -> victory(f, s, env, aw, t);
            case MotionState.EMOTE_POINT -> point(f, s, env, aw, t, main, side);
            case MotionState.EMOTE_LAUGH -> laugh(f, s, env, aw, t, main, off, side);
            case MotionState.EMOTE_CRY -> cry(f, s, env, aw, t);
            case MotionState.EMOTE_FLOSS -> floss(f, s, env, aw, t);
            case MotionState.EMOTE_PUSHUPS -> pushups(f, s, env, aw, t);
            case MotionState.EMOTE_RUNNING_MAN -> runningMan(f, s, env, aw, t);
            case MotionState.EMOTE_ROBOT -> robot(f, s, env, aw, t);
            case MotionState.EMOTE_GUITAR -> guitar(f, s, env, aw, t, main, off, side);
            case MotionState.EMOTE_FLEX -> flex(f, s, env, aw, t);
            case MotionState.EMOTE_MEDITATE -> meditate(f, s, env, aw, t);
            default -> { }
        }
    }

    private static void arm(PartPose arm, Signs s, float x, float y, float z, float elbow, float w) {
        arm.override(x, y, z, w);
        arm.overrideBend(s.elbow() * elbow, w);
    }

    private static void leg(PartPose leg, Signs s, float x, float z, float knee, float w) {
        leg.override(x, 0f, z, w);
        leg.overrideBend(s.knee() * knee, w);
    }

    private static float seg(float t, float a, float b) {
        return AngleMath.ease((t - a) / (b - a));
    }

    /** 0..1..0 smooth pulse every beat; {@code beat} counts beats. */
    private static float pulse(float beat) {
        return 0.5f - 0.5f * Mth.cos(beat * Mth.TWO_PI);
    }

    // ------------------------------------------------------------------ dances

    /**
     * The "default dance", in three 8-beat parts at ~128 bpm, looping:
     * <ol>
     *   <li>bouncing on the knees, fists in front of the chest, forearms swinging across the body
     *       one up and one down, alternating every beat, the hips and head going with them;</li>
     *   <li>the kicks: a little hop on one leg while the other kicks out forward, both arms swung
     *       low to the other side, alternating every beat;</li>
     *   <li>feet wide and knees bent, shoulders rolling side to side while both fists pump up from
     *       the shoulders and down again, head nodding to the beat.</li>
     * </ol>
     */
    private static void dance(PoseFrame f, Signs s, float e, float aw, float t) {
        float beat = t * 2.13f;
        int part = (int) (beat / 8f) % 3;
        int b = (int) beat;
        float in = beat - b;
        float sideBeat = (b % 2 == 0) ? 1f : -1f;
        // Each beat snaps over in the first third, then holds: crisp but not jerky.
        float hit = AngleMath.ease(Math.min(1f, in / 0.35f));
        float sw = Mth.lerp(hit, -sideBeat, sideBeat);       // -1..1 for this beat
        float bounce = pulse(beat);

        switch (part) {
            case 0 -> {
                Rigging.crouch(f, s, (0.10f + 0.20f * bounce) * e);
                f.head().rx += 0.10f * bounce * e;
                float r = Math.max(0f, sw), l = Math.max(0f, -sw);
                arm(f.rightArm(), s, Mth.lerp(r, -2.35f, -1.05f), Mth.lerp(r, 0.10f, -0.85f), Mth.lerp(r, 0.55f, 0f), 0.30f, aw);
                arm(f.leftArm(), s, Mth.lerp(l, -2.35f, -1.05f), Mth.lerp(l, -0.10f, 0.85f), Mth.lerp(l, -0.55f, 0f), 0.30f, aw);
                f.body().rz += 0.08f * sw * e;
                f.body().px += 0.05f * sw * e;
                Rigging.twistUpper(f, -0.25f * sw * e);
                f.head().ry += 0.20f * sw * e;
            }
            case 1 -> {
                PartPose kick = sideBeat > 0 ? f.rightLeg() : f.leftLeg();
                PartPose stand = sideBeat > 0 ? f.leftLeg() : f.rightLeg();
                float k = Mth.sin(Mth.PI * Math.min(1f, in / 0.6f));
                leg(kick, s, -1.00f * k, sideBeat * 0.12f * k, 0.9f * (1f - k) * k + 0.1f, e * Math.max(0.25f, k));
                stand.bend += s.knee() * 0.25f * (1f - k) * e;
                f.body().py += 0.08f * k * e;                      // the hop
                arm(f.rightArm(), s, -0.55f, -0.70f * sw, 0.30f, 0.45f, aw);
                arm(f.leftArm(), s, -0.55f, -0.70f * sw, -0.30f, 0.45f, aw);
                Rigging.twistUpper(f, 0.35f * sw * e);
                f.body().rz -= 0.06f * sw * e;
                Rigging.leanTorso(f, s, 0.10f * k * e);
            }
            default -> {
                Rigging.legsOut(f, 0.22f * e);
                Rigging.crouch(f, s, (0.22f + 0.12f * bounce) * e);
                float pump = Math.abs(Mth.sin(beat * Mth.PI));    // up on every beat
                arm(f.rightArm(), s, -0.40f - 2.30f * pump, 0f, 0.55f - 0.25f * pump, 1.60f - 1.30f * pump, aw);
                arm(f.leftArm(), s, -0.40f - 2.30f * pump, 0f, -0.55f + 0.25f * pump, 1.60f - 1.30f * pump, aw);
                float roll = Mth.sin(beat * Mth.PI * 0.5f);
                Rigging.twistUpper(f, 0.30f * roll * e);
                f.body().rz += 0.06f * roll * e;
                f.head().rx += 0.15f * bounce * e;
            }
        }
    }

    /**
     * The running man: one knee comes up high while the other foot slides back along the floor,
     * then they swap, the body bobbing down on each step; fists pumping at the chest.
     */
    private static void runningMan(PoseFrame f, Signs s, float e, float aw, float t) {
        float beat = t * 2.2f;
        float ph = beat * Mth.PI;
        float c = Mth.cos(ph), sn = Mth.sin(ph);
        float r = Math.max(0f, c), l = Math.max(0f, -c);
        // Up knee: thigh forward and knee bent; the other leg back and straightening as it slides.
        leg(f.rightLeg(), s, -1.20f * r + 0.45f * l, 0.05f, 1.40f * r + 0.15f, e);
        leg(f.leftLeg(), s, -1.20f * l + 0.45f * r, -0.05f, 1.40f * l + 0.15f, e);
        f.body().py -= 0.06f * Math.abs(sn) * e;
        f.body().py += 0.04f * e;
        Rigging.leanTorso(f, s, 0.12f * e);
        float pump = Math.abs(sn);
        arm(f.rightArm(), s, -1.10f + 0.35f * c, -0.30f, 0.15f, 1.50f, aw);
        arm(f.leftArm(), s, -1.10f - 0.35f * c, 0.30f, -0.15f, 1.50f, aw);
        f.head().rx += 0.10f * pump * e;
        f.body().rz += 0.04f * sn * e;
    }

    /**
     * The robot: stiff, mechanical poses held and then jerked to the next one twice a beat, the
     * head ticking round in steps; arms bent at right angles.
     */
    private static void robot(PoseFrame f, Signs s, float e, float aw, float t) {
        float step = t * 3.2f;
        int i = (int) step;
        float in = step - i;
        float k = AngleMath.ease(Math.min(1f, in / 0.18f));         // quick move, then hold
        float[][] poses = {
                // rArm x, z, elbow, lArm x, z, elbow, head yaw, twist, crouch
                {-1.57f, 0.10f, 1.57f, 0f, -0.15f, 1.57f, 0.00f, 0.00f, 0.10f},
                {-1.57f, 0.10f, 0.00f, -1.57f, -0.10f, 1.57f, 0.50f, 0.20f, 0.10f},
                {0f, 0.15f, 1.57f, -1.57f, -0.10f, 0.00f, -0.50f, -0.20f, 0.20f},
                {-3.00f, 0.10f, 1.57f, -1.57f, -0.10f, 1.57f, 0.00f, 0.00f, 0.20f},
                {-1.57f, 0.90f, 1.57f, -1.57f, -0.90f, 1.57f, 0.40f, 0.15f, 0.10f},
                {-1.57f, 0.10f, 1.57f, -3.00f, -0.10f, 1.57f, -0.40f, -0.15f, 0.25f},
        };
        float[] a = poses[(i + poses.length - 1) % poses.length], b = poses[i % poses.length];
        float[] p = new float[a.length];
        for (int j = 0; j < a.length; j++) p[j] = Mth.lerp(k, a[j], b[j]);
        arm(f.rightArm(), s, p[0], 0f, p[1], p[2], aw);
        arm(f.leftArm(), s, p[3], 0f, p[4], p[5], aw);
        f.head().ry += p[6] * e;
        Rigging.twistUpper(f, p[7] * e);
        Rigging.crouch(f, s, p[8] * e);
    }

    /** Air guitar: leaning back, one hand strumming at the hip, the other up the neck, head banging. */
    private static void guitar(PoseFrame f, Signs s, float e, float aw, float t, PartPose main, PartPose off, float side) {
        float strum = Mth.sin(t * 14f);
        float bang = pulse(t * 2.4f);
        arm(main, s, -0.75f + 0.18f * strum, -side * 0.55f, side * 0.20f, 1.20f, aw);
        arm(off, s, -1.25f, side * 0.45f, -side * 0.25f, 0.45f + 0.10f * Mth.sin(t * 3f), aw);
        Rigging.leanTorso(f, s, (-0.18f + 0.35f * bang) * e);
        f.head().rx += (0.45f * bang - 0.15f) * e;
        Rigging.legsOut(f, 0.18f * e);
        Rigging.crouch(f, s, (0.20f + 0.08f * bang) * e);
        Rigging.twistUpper(f, side * 0.25f * e);
    }

    /** Flexing: a double biceps from the front, then a side chest each way. */
    private static void flex(PoseFrame f, Signs s, float e, float aw, float t) {
        float a = seg(t, 0f, 0.35f) * (1f - seg(t, 1.30f, 1.55f));
        float b = seg(t, 1.40f, 1.70f) * (1f - seg(t, 2.50f, 2.75f));
        float c = seg(t, 2.65f, 2.95f);
        float shake = 0.03f * Mth.sin(t * 40f);
        // Double biceps: upper arms out level to the sides, forearms straight up (the arm is
        // raised forward first so its elbow folds upward, then swung out to the side).
        arm(f.rightArm(), s, -1.57f + shake, 1.35f, 0f, 1.95f, aw * a);
        arm(f.leftArm(), s, -1.57f - shake, -1.35f, 0f, 1.95f, aw * a);
        Rigging.legsOut(f, 0.15f * a * e);
        f.head().rx -= 0.10f * a * e;
        // Side chest, right then left: hands clasped in front of the belly, chest turned.
        for (int i = 0; i < 2; i++) {
            float w = i == 0 ? b : c;
            if (w <= 0.001f) continue;
            float d = i == 0 ? 1f : -1f;
            // Forearms well out in front of the belly, one fist a little above the other, so
            // nothing lies flat against the body or the other hand.
            arm(f.rightArm(), s, -0.95f + shake, -0.42f, 0.18f, 1.15f, aw * w);
            arm(f.leftArm(), s, -1.10f - shake, 0.42f, -0.18f, 1.05f, aw * w);
            Rigging.twistUpper(f, d * 0.45f * w * e);
            f.head().ry -= d * 0.45f * w * e;
            PartPose front = d > 0 ? f.leftLeg() : f.rightLeg();
            front.rx -= 0.20f * w * e;
            front.bend += s.knee() * 0.35f * w * e;
        }
        Rigging.leanTorso(f, s, -0.05f * e);
    }

    private static final float[] EUL = new float[3];

    /** @param side +1 right leg, -1 left leg. */
    private static void crossLeg(PartPose leg, Signs s, float side, float w) {
        org.joml.Quaternionf q = new org.joml.Quaternionf().rotationY(side * 0.55f)
                .rotateX(-1.45f).rotateY(side * 1.45f);
        AngleMath.toModelEuler(q, EUL);
        leg.override(EUL[0], EUL[1], EUL[2], w);
        leg.overrideBend(s.knee() * (side > 0 ? 2.25f : 2.10f), w);
    }

    /** Meditating: sitting cross-legged in mid-air, hands on the knees, slowly rising and sinking. */
    private static void meditate(PoseFrame f, Signs s, float e, float aw, float t) {
        float sit = seg(t, 0f, 0.8f);
        float w = e * sit;
        float breathe = Mth.sin(t * 1.4f);
        // Hips down to the ground, then floating a little above it.
        f.body().py -= (12f / 16f * 0.9375f - 0.18f - 0.06f * breathe) * w;
        // Sitting with the knees wide and the soles together in front (butterfly): thighs
        // forward and spread, knees folding sideways so each shin comes in toward the middle;
        // one leg a touch higher so the shins never lie flat against each other.
        f.rightLeg().override(-1.42f, 0.70f, 0f, w);
        f.leftLeg().override(-1.52f, -0.70f, 0f, w);
        f.rightLeg().overrideBendAxis(Mth.HALF_PI, w);
        f.leftLeg().overrideBendAxis(-Mth.HALF_PI, w);
        f.rightLeg().overrideBend(s.knee() * 1.45f, w);
        f.leftLeg().overrideBend(s.knee() * 1.45f, w);
        arm(f.rightArm(), s, -0.85f, -0.25f, 0.20f, 0.45f, aw * sit);
        arm(f.leftArm(), s, -0.85f, 0.25f, -0.20f, 0.45f, aw * sit);
        Rigging.leanTorso(f, s, 0.02f * breathe * w);
        f.head().rx += (0.12f + 0.03f * breathe) * w;
    }

    /** The floss: straight arms swing side to side, one in front of the hips and one behind, hips the other way. */
    private static void floss(PoseFrame f, Signs s, float e, float aw, float t) {
        float beat = t * 2.6f;
        float sw = Mth.sin(beat * Mth.PI);                 // -1..1 side to side
        boolean front = ((int) Math.floor(beat)) % 2 == 0;  // which arm goes in front swaps every swing
        float fr = front ? -0.30f : 0.30f;
        arm(f.rightArm(), s, fr, 0f, -0.55f * sw, 0f, aw);
        arm(f.leftArm(), s, -fr, 0f, -0.55f * sw, 0f, aw);
        f.body().px += 0.10f * sw * e;
        f.body().rz += 0.10f * sw * e;
        f.head().rz -= 0.08f * sw * e;
        Rigging.crouch(f, s, 0.10f * e);
    }

    // ------------------------------------------------------------------ gestures

    private static void wave(PoseFrame f, Signs s, float e, float aw, float t, PartPose main, float side) {
        float up = seg(t, 0f, 0.35f);
        float wv = Mth.sin(t * 9f) * up;
        arm(main, s, -2.75f, 0f, side * (0.35f + 0.30f * wv), 0.45f + 0.25f * wv, aw * up);
        f.head().rz -= side * 0.10f * e;
        f.body().rz -= side * 0.03f * e;
        Rigging.leanTorso(f, s, -0.06f * up * e);
        Rigging.crouch(f, s, 0.05f * Math.abs(wv) * e);
    }

    private static void clap(PoseFrame f, Signs s, float e, float aw, float t) {
        float c = Math.abs(Mth.sin(t * 6.5f));
        arm(f.rightArm(), s, -1.20f, -0.55f - 0.20f * (1f - c) + 0.20f, 0f, 1.00f, aw);
        arm(f.leftArm(), s, -1.20f, 0.55f + 0.20f * (1f - c) - 0.20f, 0f, 1.00f, aw);
        Rigging.crouch(f, s, 0.06f * c * e);
        f.head().rx += 0.05f * c * e;
        f.body().py += 0.015f * c * e;
    }

    private static void facepalm(PoseFrame f, Signs s, float e, float aw, float t, PartPose main, float side) {
        float up = seg(t, 0.1f, 0.5f);
        arm(main, s, -1.60f, side * -0.70f, 0f, 1.45f, aw * up);
        f.head().rx += 0.45f * up * e;
        f.head().ry += 0.20f * Mth.sin(t * 5f) * seg(t, 0.6f, 0.8f) * e;
        Rigging.leanTorso(f, s, 0.12f * up * e);
        f.body().py -= 0.02f * up * e;
    }

    private static void shrug(PoseFrame f, Signs s, float e, float aw, float t) {
        float up = Mth.sin(Mth.PI * AngleMath.clamp(t / 1.5f, 0f, 1f));
        arm(f.rightArm(), s, -0.55f, 0.35f, 0.55f, 1.20f, aw * up);
        arm(f.leftArm(), s, -0.55f, -0.35f, -0.55f, 1.20f, aw * up);
        f.rightArm().py -= 1.5f * up * aw;
        f.leftArm().py -= 1.5f * up * aw;
        f.head().rz += 0.18f * up * e;
        f.head().rx -= 0.05f * up * e;
    }

    private static void salute(PoseFrame f, Signs s, float e, float aw, float t, PartPose main, float side) {
        float up = seg(t, 0.15f, 0.45f);
        arm(main, s, -1.90f, side * 0.30f, side * 0.20f, 1.10f, aw * up);
        // Standing to attention.
        f.rightLeg().override(0f, 0f, 0f, e);
        f.leftLeg().override(0f, 0f, 0f, e);
        Rigging.leanTorso(f, s, -0.05f * e);
        f.head().rx -= 0.05f * e;
    }

    private static void bow(PoseFrame f, Signs s, float e, float aw, float t, PartPose main, PartPose off, float side) {
        float down = Mth.sin(Mth.PI * AngleMath.clamp((t - 0.2f) / 1.8f, 0f, 1f));
        arm(main, s, -0.50f, side * -1.10f, side * 0.60f, 1.10f, aw);  // hand across the stomach
        arm(off, s, 0.55f, 0f, -side * 0.15f, 0.20f, aw);          // other arm swept back
        // Bend at the hips: the body tips forward around the hip joint, the legs stay upright.
        f.body().rx -= 0.80f * down * e;
        Rigging.leanTorso(f, s, 0.15f * down * e);
        f.head().rx += 0.20f * down * e;
        PartPose lead = side > 0 ? f.rightLeg() : f.leftLeg();
        PartPose rear = side > 0 ? f.leftLeg() : f.rightLeg();
        lead.overrideX(-0.80f * down - 0.20f * down, e);
        lead.overrideBend(s.knee() * 0.30f * down, e);
        rear.overrideX(-0.80f * down, e);
        rear.overrideBend(0f, e);
    }

    /** Sits down on the ground, leaning back on the hands. */
    private static void sit(PoseFrame f, Signs s, float e, float aw, float t) {
        float sitW = seg(t, 0f, 0.6f) * e;
        f.body().py -= 0.62f * sitW;
        leg(f.rightLeg(), s, -1.45f, 0.18f, 0.25f, sitW);
        leg(f.leftLeg(), s, -1.35f, -0.18f, 0.55f, sitW);
        arm(f.rightArm(), s, 0.55f, 0f, 0.30f, 0.10f, aw * seg(t, 0.2f, 0.7f));
        arm(f.leftArm(), s, 0.55f, 0f, -0.30f, 0.10f, aw * seg(t, 0.2f, 0.7f));
        Rigging.leanTorso(f, s, -0.15f * sitW);
        f.head().ry += 0.35f * Mth.sin(t * 0.5f) * sitW;
        f.head().rx += 0.04f * Mth.sin(t * 1.7f) * sitW;
    }

    private static void thumbs(PoseFrame f, Signs s, float e, float aw, float t, PartPose main, float side) {
        float up = seg(t, 0.1f, 0.4f);
        arm(main, s, -1.35f, side * -0.20f, side * 0.10f, 1.60f, aw * up);
        main.overrideRoll(side * -1.4f, aw * up);
        f.head().rz += side * 0.10f * e;
        f.head().rx -= 0.05f * up * e;
    }

    private static void victory(PoseFrame f, Signs s, float e, float aw, float t) {
        float hop = Math.max(0f, Mth.sin(t * 7f)) * seg(t, 0.2f, 0.4f) * (1f - seg(t, 2.0f, 2.4f));
        arm(f.rightArm(), s, -2.85f, 0f, 0.45f + 0.10f * hop, 0.35f, aw);
        arm(f.leftArm(), s, -2.85f, 0f, -0.45f - 0.10f * hop, 0.35f, aw);
        f.body().py += 0.18f * hop * e;
        // Crouch before each little jump, legs tucking in the air.
        float load = Math.max(0f, -Mth.sin(t * 7f)) * seg(t, 0.2f, 0.4f) * (1f - seg(t, 2.0f, 2.4f));
        Rigging.crouch(f, s, 0.35f * load * e);
        f.rightLeg().bend += s.knee() * 0.70f * hop * e;
        f.leftLeg().bend += s.knee() * 0.70f * hop * e;
        f.head().rx -= 0.30f * e;
        Rigging.leanTorso(f, s, -0.10f * e);
    }

    private static void point(PoseFrame f, Signs s, float e, float aw, float t, PartPose main, float side) {
        float up = seg(t, 0.1f, 0.35f);
        arm(main, s, -1.70f, side * 0.10f, 0f, 0f, aw * up);
        Rigging.twistUpper(f, -side * 0.20f * up * e);
        f.body().rx -= 0.05f * up * e;
    }

    private static void laugh(PoseFrame f, Signs s, float e, float aw, float t, PartPose main, PartPose off, float side) {
        float shake = Mth.sin(t * 22f) * 0.5f + 0.5f;
        float bend = 0.5f + 0.5f * Mth.sin(t * 2.2f);
        arm(main, s, -0.50f, side * -1.10f, side * 0.60f, 1.10f, aw);  // hand on the belly
        arm(off, s, -0.25f + 0.10f * shake, 0f, -side * 0.25f, 0.40f, aw);
        Rigging.leanTorso(f, s, (0.10f + 0.25f * bend) * e);
        f.head().rx -= (0.30f - 0.40f * bend) * e;
        f.body().py += 0.02f * shake * e;
        f.torso().ry += 0.03f * shake * e;
    }

    private static void cry(PoseFrame f, Signs s, float e, float aw, float t) {
        float sob = Math.max(0f, Mth.sin(t * 5.5f));
        arm(f.rightArm(), s, -1.40f, -0.75f, 0f, 1.35f, aw);
        arm(f.leftArm(), s, -1.40f, 0.75f, 0f, 1.35f, aw);
        f.head().rx += (0.45f + 0.08f * sob) * e;
        Rigging.leanTorso(f, s, (0.20f + 0.06f * sob) * e);
        f.body().py -= 0.02f * sob * e;
        f.rightLeg().bend += s.knee() * 0.25f * e;
        f.leftLeg().bend += s.knee() * 0.25f * e;
    }

    /** Push-ups: a straight plank pivoting at the feet, arms bending as the chest goes down. */
    private static void pushups(PoseFrame f, Signs s, float e, float aw, float t) {
        float lie = seg(t, 0f, 0.7f) * e;
        float down = (0.5f - 0.5f * Mth.cos(Math.max(0f, t - 0.7f) * 3.2f)) * seg(t, 0.6f, 0.9f);
        // Angle of the body from vertical: shoulders ~0.6 blocks up with straight arms, ~0.3 down.
        float theta = Mth.lerp(down, 1.13f, 1.35f);
        float hip = 0.70f;
        f.body().rx -= theta * lie;
        // Keep the feet where they were: hips drop and move forward as the body tips over.
        f.body().py += (hip * Mth.cos(theta) - hip) * lie;
        f.body().pz -= hip * Mth.sin(theta) * lie;
        arm(f.rightArm(), s, Mth.lerp(down, -theta, -0.95f), 0f, 0.22f, 1.55f * down, aw * lie);
        arm(f.leftArm(), s, Mth.lerp(down, -theta, -0.95f), 0f, -0.22f, 1.55f * down, aw * lie);
        leg(f.rightLeg(), s, 0f, 0.04f, 0f, lie);
        leg(f.leftLeg(), s, 0f, -0.04f, 0f, lie);
        f.head().rx -= 0.8f * theta * lie;
    }
}
