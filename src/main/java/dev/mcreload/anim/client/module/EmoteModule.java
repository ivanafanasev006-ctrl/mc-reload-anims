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
     * A "default dance": 8 beats of arms swinging across the chest in turn with a bounce in the
     * knees, then 8 beats of alternating kicks with both arms swinging the other way. ~128 bpm.
     */
    private static void dance(PoseFrame f, Signs s, float e, float aw, float t) {
        float beat = t * 2.13f;
        int bar = (int) (beat / 8f) % 2;
        int b = (int) beat;
        float in = beat - b;
        float sideBeat = (b % 2 == 0) ? 1f : -1f;
        // Smooth hit on each beat.
        float hit = AngleMath.ease(Math.min(1f, in / 0.35f));
        float prev = -sideBeat;
        float sw = Mth.lerp(hit, prev, sideBeat);       // -1..1 swing for this beat

        // Bounce on every beat.
        float bounce = pulse(beat);
        Rigging.crouch(f, s, 0.22f * bounce * e);
        f.head().rx += 0.10f * bounce * e;

        if (bar == 0) {
            // Arms swing across the body diagonally, one up one down, alternating each beat.
            float r = Math.max(0f, sw), l = Math.max(0f, -sw);
            arm(f.rightArm(), s, Mth.lerp(r, -2.35f, -1.05f), Mth.lerp(r, 0.10f, -0.85f), Mth.lerp(r, 0.55f, 0f), 0.25f, aw);
            arm(f.leftArm(), s, Mth.lerp(l, -2.35f, -1.05f), Mth.lerp(l, -0.10f, 0.85f), Mth.lerp(l, -0.55f, 0f), 0.25f, aw);
            f.body().rz += 0.08f * sw * e;
            f.body().px += 0.05f * sw * e;
            Rigging.twistUpper(f, -0.25f * sw * e);
            f.head().ry += 0.20f * sw * e;
        } else {
            // Kicks: one leg flicks out in front on each beat, arms swing together the other way.
            PartPose kick = sideBeat > 0 ? f.rightLeg() : f.leftLeg();
            float k = Mth.sin(Mth.PI * Math.min(1f, in / 0.6f));
            leg(kick, s, -0.95f * k, 0f, 0.9f * (1f - k) * k + 0.1f, e * Math.max(0.2f, k));
            arm(f.rightArm(), s, -1.35f, -0.75f * sw, 0.25f, 0.55f, aw);
            arm(f.leftArm(), s, -1.35f, -0.75f * sw, -0.25f, 0.55f, aw);
            Rigging.twistUpper(f, 0.35f * sw * e);
            f.body().rz -= 0.06f * sw * e;
        }
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
