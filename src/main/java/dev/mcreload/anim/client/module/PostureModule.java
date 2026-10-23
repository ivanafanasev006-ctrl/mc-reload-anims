package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.math.AngleMath;
import dev.mcreload.anim.client.pose.PartPose;
import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.compat.PersonalityCompat;
import dev.mcreload.anim.config.Cfg;
import dev.mcreload.anim.net.Posture;
import net.minecraft.util.Mth;

/**
 * Crawling on land and sitting on the ground.
 *
 * <p><b>Crawl.</b> Not a swim stroke: a low "cave diver" crawl. Lying on the chest, one forearm
 * reaches ahead and plants, then the body is dragged up to it, the elbow digging in and swinging
 * out to the side, while the opposite knee, drawn up sideways along the ground (frog leg),
 * pushes back. Arms and legs alternate, the hips roll a little and the body snakes; the head is
 * held up, looking ahead. Lying still, the body rests on the forearms.</p>
 *
 * <p>In vanilla's crawl pose the body lies face down, so in model space -y is ahead (along the
 * ground), -z is down into the ground and x is sideways. Limb directions are written in those
 * terms (out, ahead / back, down) and turned into rotations here.</p>
 *
 * <p><b>Sit.</b> Two relaxed ways of sitting on the ground, changing now and then: leaning back
 * on the hands with one knee up, and one knee up with an arm resting on it and the other leg
 * folded in front.</p>
 */
final class PostureModule implements AnimModule {
    // Arm keys: {out, ahead, down, elbow}. The pull goes REACH -> MID -> END, the hand planted.
    // MID, END, the resting arms and the drawn-up knee were fitted on a box model of the player
    // over the whole cycle: the forearms no longer fold back into the chest or across the face,
    // and the drawn-up shin stays out from under the belly. The knee is drawn up gently: the
    // thigh stays angled back from the hip (it does not swing out flat beside the body) and the
    // knee stays well below the hip.
    private static final float[] REACH = {0.22f, 0.95f, 0.22f, 0.25f};
    private static final float[] MID = {0.72f, 0.0f, 0.34f, 1.28f};
    private static final float[] END = {0.53f, -0.06f, 0.21f, 0.54f};
    /** Lying still: propped on the forearms, hands beside the head. */
    private static final float[] REST_ARM = {0.63f, 0.17f, 0.71f, 1.38f};
    // Leg keys: {out, back, down, knee}. The push goes DRAWN -> EXTENDED.
    private static final float[] DRAWN = {0.50f, 0.45f, 0.02f, 1.05f};
    private static final float[] EXTENDED = {0.22f, 0.97f, 0.05f, 0.25f};
    private static final float[] REST_LEG = {0.14f, 0.99f, 0.0f, 0.12f};
    /** Part of each arm's cycle spent pulling (the rest is the reach forward). */
    private static final float PULL = 0.6f;

    /** Seconds before changing the way of sitting, and the blend between two ways. */
    private static final float SIT_SWITCH = 24f, SIT_BLEND = 1.6f;

    private final float[] a = new float[4], b = new float[4];

    @Override
    public boolean enabled() {
        return Cfg.postures;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float c = m.crawlW.v;
        if (c > 0.001f) crawl(rig, m, f, s, c, pt);
        float sw = m.sitW.v * m.gate.v;
        if (sw > 0.001f) sit(m, f, s, sw);
    }

    /**
     * How much our arm pose is wanted: it gives way while the hands do something else (a swing,
     * eating, drawing a bow, casting).
     */
    private static float handsFree(MotionState m) {
        float swing = m.slashKind >= 0 ? 1f - AngleMath.ease((m.slashProgress() - 0.8f) / 0.35f) : 0f;
        float use = Math.max(Math.max(m.eatW.v, m.drinkW.v), Math.max(m.bowW.v, Math.max(m.crossbowW.v, m.spearW.v)));
        return m.armsFree.v * (1f - Math.max(swing, use)) * (1f - 0.9f * m.castW.v);
    }

    // ------------------------------------------------------------------ crawl

    private void crawl(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float c, float pt) {
        float mv = m.crawlMoveW.v;
        float aw = c * handsFree(m);
        float u = m.crawlPhase - (float) Math.floor(m.crawlPhase);
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? 1f : -1f;                        // +1 right arm (and left leg)
            float su = u + (i == 0 ? 0f : 0.5f);
            su -= (float) Math.floor(su);
            armKey(su, a);
            mix(REST_ARM, a, mv, a);
            proneArm(side > 0 ? f.rightArm() : f.leftArm(), s, a, side, aw);
            // The opposite knee pushes while this arm pulls (like a real low crawl).
            legKey(su, b);
            mix(REST_LEG, b, mv, b);
            proneLeg(side > 0 ? f.leftLeg() : f.rightLeg(), s, b, -side, c);
        }
        float wave = Mth.sin(u * Mth.TWO_PI);
        // Hips roll away from the knee being drawn up; the body snakes toward the pulling arm.
        f.body().ry += 0.07f * wave * mv * c;
        f.body().rz += 0.05f * Mth.cos(u * Mth.TWO_PI) * mv * c;
        f.torso().overrideBend(0f, c);
        // Head up, looking ahead (a bit higher when you look up), nodding with each pull.
        float pitch = Mth.lerp(pt, rig.player.xRotO, rig.player.getXRot()) * Mth.DEG_TO_RAD;
        float head = AngleMath.clamp(-1.05f + 0.45f * pitch, -1.40f, -0.45f)
                + 0.05f * Mth.sin(u * Mth.TWO_PI * 2f) * mv;
        f.head().overrideX(head, c);
    }

    /** Arm key at this point of its own cycle (0..1): pull with the hand planted, then reach. */
    private static void armKey(float u, float[] out) {
        if (u < PULL) {
            float t = u / PULL;
            if (t < 0.5f) mix(REACH, MID, AngleMath.ease(t / 0.5f), out);
            else mix(MID, END, AngleMath.ease((t - 0.5f) / 0.5f), out);
            return;
        }
        // Reach: the forearm comes up off the ground and slides forward again.
        float t = (u - PULL) / (1f - PULL);
        mix(END, REACH, AngleMath.ease(t), out);
        float lift = Mth.sin(Mth.PI * t);
        out[2] -= 0.45f * lift;
        out[3] += 0.35f * lift;
    }

    /** Leg key at this point of the cycle: push back while the other arm pulls, then draw up. */
    private static void legKey(float u, float[] out) {
        if (u < PULL) {
            mix(DRAWN, EXTENDED, AngleMath.ease(u / PULL), out);
            return;
        }
        float t = (u - PULL) / (1f - PULL);
        mix(EXTENDED, DRAWN, AngleMath.ease(t), out);
        out[2] -= 0.12f * Mth.sin(Mth.PI * t);
    }

    private static void mix(float[] from, float[] to, float t, float[] out) {
        for (int i = 0; i < 4; i++) out[i] = from[i] + (to[i] - from[i]) * t;
    }

    /**
     * Prone arm from a direction (out, ahead, down) and an elbow fold. The elbow folds inward
     * (toward the head), so the forearm lies ahead on the ground.
     * @param side +1 right arm, -1 left arm
     */
    private static void proneArm(PartPose arm, Signs s, float[] k, float side, float w) {
        if (w <= 0.001f) return;
        float dx = -k[0] * side, dy = -k[1], dz = -k[2];
        float n = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= n; dy /= n; dz /= n;
        float cxy = Mth.sqrt(dx * dx + dy * dy);
        // Rotation order Z * Y * X on the arm's rest direction (0, 1, 0), the X turn past 90 degrees.
        float rx = (float) Math.atan2(dz, -cxy);
        float rz = (float) Math.atan2(dx, -dy);
        arm.override(rx, 0f, rz, w);
        arm.overrideBendAxis(side > 0 ? -Mth.HALF_PI : Mth.HALF_PI, w);
        arm.overrideBend(s.elbow() * k[3], w);
        // The shoulder goes with the reach (ahead = -y here) and back with the pull.
        arm.py -= 1.6f * AngleMath.clamp((k[1] - 0.3f) / 0.65f, -0.5f, 1f) * w;
    }

    /**
     * Prone leg from a direction (out, back, down) and a knee fold. The knee folds sideways along
     * the ground, so a leg drawn up sits like a frog's.
     * @param side +1 right leg, -1 left leg
     */
    private static void proneLeg(PartPose leg, Signs s, float[] k, float side, float w) {
        float dx = -k[0] * side, dy = k[1], dz = -k[2];
        float n = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= n; dy /= n; dz /= n;
        float cxy = Mth.sqrt(dx * dx + dy * dy);
        float rx = (float) Math.atan2(dz, cxy);
        float rz = (float) Math.atan2(-dx, dy);
        leg.override(rx, 0f, rz, w);
        leg.overrideBendAxis(side > 0 ? Mth.HALF_PI : -Mth.HALF_PI, w);
        leg.overrideBend(s.knee() * k[3], w);
    }

    // ------------------------------------------------------------------ sit

    private void sit(MotionState m, PoseFrame f, Signs s, float w) {
        float t = m.since(m.sitStart);
        float aw = w * handsFree(m);
        int k = (int) (t / SIT_SWITCH);
        float in = t - k * SIT_SWITCH;
        // Any style value (older clients still send three) maps onto one of the poses.
        int cur = (m.sitStyle + k) % Posture.SIT_POSES;
        float drop;
        if (k > 0 && in < SIT_BLEND) {
            int prev = (m.sitStyle + k - 1) % Posture.SIT_POSES;
            float bl = AngleMath.ease(in / SIT_BLEND);
            drop = style(prev, f, s, w, aw, w * (1f - bl), t);
            drop += style(cur, f, s, w * bl, aw * bl, w * bl, t);
        } else {
            drop = style(cur, f, s, w, aw, w, t);
        }
        // Personality (if it is the one making us sit) already lowers the model.
        if (m.personalitySit) drop -= PersonalityCompat.OWN_DROP * w;
        f.body().py -= drop;

        // Alive: breathing, the head drifting a little around wherever you look.
        float tt = m.time + m.seed;
        Rigging.leanTorso(f, s, 0.015f * Mth.sin(tt * 1.5f) * w);
        f.head().ry += 0.12f * Mth.sin(tt * 0.27f) * w;
        f.head().rx += 0.04f * Mth.sin(tt * 0.19f) * w;
    }

    /**
     * One way of sitting. Limb poses are pulled toward with {@code w} (arms {@code aw}); body tilt,
     * hips and head are added scaled by {@code add}. Returns how far the hips go down (blocks, scaled by add).
     *
     * <p>The hip joints sit in the middle of the torso's depth, at its bottom edge, so a thigh
     * pointing forward or up comes out of the belly and a level thigh hangs two pixels below the
     * seat. Sitting, the legs are therefore drawn up into the hips and a little forward, and the
     * hips go all the way down onto the ground; leaning back tips the whole body from the hips
     * instead of folding the back at the waist. Every pose was checked against a box model of the
     * player (wide and slim arms): no part through another, and hips, feet and planted hands on
     * the ground. The arm angles are relative to the tipped, leaning chest they hang from.</p>
     */
    private static float style(int v, PoseFrame f, Signs s, float w, float aw, float add, float t) {
        float recline = 0f, waist = 0f, forward = 0f, up = 0f, drop = 0f;
        switch (v) {
            case 0 -> {
                // Leaning back on both hands planted behind the hips, right leg out along the
                // ground, left knee up and turned out a little.
                recline = 0.22f; waist = -0.08f; forward = 1.25f; up = 1.7f; drop = 0.68f;
                leg(f.rightLeg(), s, -1.60f + recline, 0.13f, 0.06f, 0.11f, 0f, w);
                leg(f.leftLeg(), s, -2.19f + recline, -0.22f, -0.25f, 1.65f, 0f, w);
                arm(f.rightArm(), s, 0.40f, 0f, 0.31f, 0f, 0f, aw);
                arm(f.leftArm(), s, 0.40f, 0f, -0.31f, 0f, 0f, aw);
                f.head().rx += 0.27f * add;
            }
            default -> {
                // Right knee up with the arm resting over it, left leg folded flat in front, left
                // hand on the ground.
                recline = 0.16f; waist = 0.27f; forward = 1.9f; up = 1.45f; drop = 0.68f;
                leg(f.rightLeg(), s, -2.17f, 0.45f, -0.09f, 1.56f, 0f, w);
                leg(f.leftLeg(), s, -1.41f, -0.30f, -0.08f, 1.60f, -Mth.HALF_PI, w);
                arm(f.rightArm(), s, -1.64f, -0.40f, -0.04f, 0.25f + 0.05f * Mth.sin(t * 0.8f), 0f, aw);
                arm(f.leftArm(), s, -0.14f, 0f, -0.38f, 0.06f, 0f, aw);
                f.head().ry -= 0.15f * add;
            }
        }
        f.body().rx += recline * add;
        Rigging.leanTorso(f, s, waist * add);
        f.rightLeg().py -= up * add;
        f.leftLeg().py -= up * add;
        f.rightLeg().pz -= forward * add;
        f.leftLeg().pz -= forward * add;
        return drop * add;
    }

    private static void leg(PartPose leg, Signs s, float x, float y, float z, float knee, float axis, float w) {
        leg.override(x, y, z, w);
        leg.overrideBendAxis(axis, w);
        leg.overrideBend(s.knee() * knee, w);
    }

    private static void arm(PartPose arm, Signs s, float x, float y, float z, float elbow, float axis, float w) {
        if (w <= 0.001f) return;
        arm.override(x, y, z, w);
        arm.overrideBendAxis(axis, w);
        arm.overrideBend(s.elbow() * elbow, w);
    }
}
