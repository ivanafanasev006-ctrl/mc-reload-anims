package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.pose.PartPose;
import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.util.Mth;

/**
 * Front crawl (replaces vanilla's breaststroke) and treading water.
 *
 * <p>In vanilla's swimming pose the body lies face down, so an arm angle of 0 points to the hips,
 * -PI/2 under the chest and -PI past the head. A crawl stroke is a continuous windmill: reach past
 * the head, pull under the chest to the hip, recover over the back with a bent elbow. Arms are half
 * a turn apart, the body rolls toward the recovering arm, the legs flutter-kick.</p>
 */
final class WaterModule implements AnimModule {
    private static final float DIVE_SECONDS = 1.1f;

    @Override
    public boolean enabled() {
        return Cfg.water;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float gate = m.gate.v;

        float swim = m.swimW.v * gate;
        if (swim > 0.001f) {
            float p = m.swimPhase;
            stroke(f.rightArm(), s, p, 1f, swim);
            stroke(f.leftArm(), s, p + Mth.PI, -1f, swim);
            // Roll toward the arm that is recovering over the water; the head stays steady, and the
            // roll fades out quickly as the swimmer comes up so nothing shakes at the surface.
            float roll = 0.18f * Mth.sin(p) * swim * swim;
            f.body().ry += roll;
            f.head().ry -= roll * 0.8f;

            float kick = Mth.sin(p * 2f);
            kickLeg(f.rightLeg(), s, kick, swim);
            kickLeg(f.leftLeg(), s, -kick, swim);
        }

        float water = m.waterW.v * (1f - m.swimW.v) * gate;
        float paddle = water * m.paddleW.v;
        float tread = water - paddle;
        if (tread > 0.001f) {
            // Treading water on the spot: arms and legs spread, everything slow. The legs paddle
            // like flippers just behind the body, the arms sweep a little forward and back in turn.
            float t = m.time + m.seed;
            float leg = Mth.sin(t * 1.7f), arm = Mth.sin(t * 1.25f);
            f.rightLeg().override(0.12f + 0.22f * leg, 0f, 0.24f, tread);
            f.leftLeg().override(0.12f - 0.22f * leg, 0f, -0.24f, tread);
            f.rightLeg().overrideBend(s.knee() * (0.30f + 0.25f * Math.max(0f, -leg)), tread);
            f.leftLeg().overrideBend(s.knee() * (0.30f + 0.25f * Math.max(0f, leg)), tread);
            float aw = tread * m.armsFree.v;
            f.rightArm().override(-0.35f + 0.22f * arm, 0f, 0.80f, aw);
            f.leftArm().override(-0.35f - 0.22f * arm, 0f, -0.80f, aw);
            f.rightArm().overrideBend(s.elbow() * 0.35f, aw);
            f.leftArm().overrideBend(s.elbow() * 0.35f, aw);
            f.body().py += 0.03f * Mth.sin(t * 1.7f * 2f) * tread;
            f.body().rz += 0.025f * arm * tread;
        }
        if (paddle > 0.001f) {
            // Swimming along at the surface without sprinting: a relaxed dog-paddle. Arms reach
            // forward in turn and pull back under the chest, legs cycle slowly behind, chest low.
            float p = m.paddlePhase;
            float r = Mth.sin(p), l = Mth.sin(p + Mth.PI);
            float aw = paddle * m.armsFree.v;
            f.rightArm().override(-1.35f - 0.45f * r, 0f, 0.25f + 0.10f * Math.max(0f, -r), aw);
            f.leftArm().override(-1.35f - 0.45f * l, 0f, -0.25f - 0.10f * Math.max(0f, -l), aw);
            f.rightArm().overrideBend(s.elbow() * (0.30f + 0.70f * Math.max(0f, -r)), aw);
            f.leftArm().overrideBend(s.elbow() * (0.30f + 0.70f * Math.max(0f, -l)), aw);
            float k = Mth.sin(p * 1.0f + 0.8f);
            f.rightLeg().override(0.25f + 0.30f * k, 0f, 0.10f, paddle);
            f.leftLeg().override(0.25f - 0.30f * k, 0f, -0.10f, paddle);
            f.rightLeg().overrideBend(s.knee() * (0.35f + 0.45f * Math.max(0f, k)), paddle);
            f.leftLeg().overrideBend(s.knee() * (0.35f + 0.45f * Math.max(0f, -k)), paddle);
            f.body().rx -= 0.55f * paddle;
            f.head().rx -= 0.45f * paddle;
            Rigging.twistUpper(f, 0.12f * r * paddle);
            f.body().py += 0.03f * Mth.sin(p * 2f) * paddle;
        }

        diveAir(m, f, s, gate);
        dive(m, f, s, gate);
    }

    /** Falling toward water: arms go overhead, hands together, and the body tips head-first. */
    private static void diveAir(MotionState m, PoseFrame f, Signs s, float gate) {
        float w = m.diveAirW.v * gate * (1f - m.waterW.v);
        if (w <= 0.001f) return;
        float aw = w * m.armsFree.v;
        f.rightArm().override(-3.0f, 0f, -0.10f, aw);
        f.leftArm().override(-3.0f, 0f, 0.10f, aw);
        f.rightArm().overrideBend(0f, aw);
        f.leftArm().overrideBend(0f, aw);
        f.rightLeg().override(0.10f, 0f, 0.02f, w);
        f.leftLeg().override(0.10f, 0f, -0.02f, w);
        f.rightLeg().overrideBend(s.knee() * 0.20f, w);
        f.leftLeg().overrideBend(s.knee() * 0.20f, w);
        // Arching over: the steeper the fall, the more head-first.
        float steep = Mth.clamp(-m.vertical / 0.8f, 0f, 1f);
        f.body().rx -= (0.70f + 0.75f * steep) * w;
        f.head().rx += 0.30f * w;
        Rigging.leanTorso(f, s, 0.10f * w);
    }

    /** Dolphin entry after jumping in from a height: streamlined, head first, then an undulating glide out. */
    private static void dive(MotionState m, PoseFrame f, Signs s, float gate) {
        float t = m.since(m.diveStart) / DIVE_SECONDS;
        if (t < 0f || t >= 1f) return;
        float e = Math.min(1f, t / 0.08f) * (1f - dev.mcreload.anim.client.math.AngleMath.ease((t - 0.55f) / 0.45f)) * gate;
        if (e <= 0.001f) return;
        float wave = Mth.sin(t * Mth.TWO_PI * 2.2f);
        float aw = e * m.armsFree.v;
        // Arms locked overhead, hands together.
        f.rightArm().override(-3.0f, 0f, -0.10f, aw);
        f.leftArm().override(-3.0f, 0f, 0.10f, aw);
        f.rightArm().overrideBend(0f, aw);
        f.leftArm().overrideBend(0f, aw);
        // Legs together doing a dolphin kick: a wave from the hips through the knees.
        f.rightLeg().override(0.20f * wave, 0f, 0.02f, e);
        f.leftLeg().override(0.20f * wave, 0f, -0.02f, e);
        f.rightLeg().overrideBend(s.knee() * (0.15f + 0.45f * Math.max(0f, -wave)), e);
        f.leftLeg().overrideBend(s.knee() * (0.15f + 0.45f * Math.max(0f, -wave)), e);
        // Body pitched head-down into the water, levelling out as the dive ends.
        float pitch = 1.25f * (1f - 0.6f * dev.mcreload.anim.client.math.AngleMath.ease(t / 0.8f));
        f.body().rx -= pitch * e;
        Rigging.leanTorso(f, s, -0.12f * wave * e);
        f.head().rx += 0.35f * e;
    }

    /** @param out +1 right arm, -1 left arm (sign of "away from the body" on the z axis). */
    private static void stroke(PartPose arm, Signs s, float phase, float out, float w) {
        // Increasing angle: -PI (reach) -> 0 (hip, end of pull) -> +PI (over the back) -> wraps to -PI.
        float a = phase % Mth.TWO_PI;
        if (a > Mth.PI) a -= Mth.TWO_PI;
        if (a < -Mth.PI) a += Mth.TWO_PI;
        float recovering = Math.max(0f, Mth.sin(a));    // arm above the back
        float pulling = Math.max(0f, -Mth.sin(a));      // arm under the chest
        arm.override(a, 0f, out * (0.12f + 0.35f * recovering), w);
        arm.overrideBend(s.elbow() * (0.35f * pulling + 1.1f * recovering), w);
    }

    private static void kickLeg(PartPose leg, Signs s, float kick, float w) {
        leg.override(0.30f * kick, 0f, 0f, w);
        leg.overrideBend(s.knee() * (0.15f + 0.35f * Math.max(0f, kick)), w);
    }
}
