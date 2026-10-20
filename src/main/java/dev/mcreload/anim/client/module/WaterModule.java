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
            // Roll toward the arm that is recovering over the water.
            f.body().ry += 0.30f * Mth.sin(p) * swim;

            float kick = Mth.sin(p * 3f);
            kickLeg(f.rightLeg(), s, kick, swim);
            kickLeg(f.leftLeg(), s, -kick, swim);
        }

        float tread = m.waterW.v * (1f - m.swimW.v) * gate;
        if (tread > 0.001f) {
            float w = Mth.sin(m.time * 3.2f + m.seed), w2 = Mth.cos(m.time * 3.2f + m.seed);
            Rigging.armsOut(f, (0.55f + 0.30f * w) * tread);
            f.rightArm().rx += (-0.30f + 0.25f * w2) * tread;
            f.leftArm().rx += (-0.30f - 0.25f * w2) * tread;
            Rigging.elbows(f, s, 0.50f * tread);
            f.rightLeg().targetX(-0.35f + 0.45f * w, tread);
            f.leftLeg().targetX(-0.35f - 0.45f * w, tread);
            f.rightLeg().bend += s.knee() * (0.55f + 0.55f * Math.max(0f, w)) * tread;
            f.leftLeg().bend += s.knee() * (0.55f + 0.55f * Math.max(0f, -w)) * tread;
            f.body().py += 0.035f * Mth.sin(m.time * 6.4f + m.seed) * tread;
        }
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
