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
 * Airborne poses: a running leap (lead knee driven up, rear leg trailing, opposite arm reaching)
 * or a standing tuck jump, a hang at the apex, windmilling arms and pedalling legs on long falls,
 * and a spring-loaded landing crouch.
 */
final class AirModule implements AnimModule {
    @Override
    public boolean enabled() {
        return Cfg.airborne || Cfg.landing;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float gate = m.gate.v;
        if (gate <= 0.001f) return;
        float arms = m.armsFree.v * (1f - m.toolSwingW.v);

        if (Cfg.airborne) {
            float air = m.airW.v * gate;
            if (air > 0.001f) {
                float rise = m.riseW.v;
                float fall = m.fallW.v * (1f - rise);
                float hang = Math.max(0f, 1f - rise - fall);
                float running = AngleMath.clamp(m.speed.v / 0.2f, 0f, 1f);

                // --- rise: blend standing tuck <-> running leap
                float wr = air * rise;
                if (wr > 0.001f) {
                    float lead = Mth.lerp(running, -0.75f, -1.20f);
                    float rear = Mth.lerp(running, -0.55f, 0.45f);
                    leg(f.rightLeg(), s, lead, Mth.lerp(running, 1.25f, 1.45f), wr);
                    leg(f.leftLeg(), s, rear, Mth.lerp(running, 1.05f, 0.75f), wr);
                    arm(f.rightArm(), s, Mth.lerp(running, -0.95f, 0.55f), 0.35f * (1f - running), 0.5f, wr * arms);
                    arm(f.leftArm(), s, Mth.lerp(running, -0.95f, -1.45f), -0.35f * (1f - running), 0.5f, wr * arms);
                    f.body().rx -= 0.14f * running * wr;
                    f.head().rx -= 0.12f * running * wr;
                }

                // --- apex hang: knees up, arms floating out
                float wh = air * hang;
                if (wh > 0.001f) {
                    leg(f.rightLeg(), s, -0.45f, 0.95f, wh);
                    leg(f.leftLeg(), s, -0.30f, 0.80f, wh);
                    arm(f.rightArm(), s, -0.55f, 0.45f, 0.4f, wh * arms);
                    arm(f.leftArm(), s, -0.55f, -0.45f, 0.4f, wh * arms);
                }

                // --- long fall: arms windmilling overhead, legs pedalling
                float wf = air * fall;
                if (wf > 0.001f) {
                    float t = m.time;
                    float a = Mth.sin(t * 8f + m.seed), b = Mth.cos(t * 8.7f + m.seed), c = Mth.sin(t * 7f + m.seed);
                    arm(f.rightArm(), s, -2.25f + 0.45f * a, 0.75f + 0.15f * b, 0.45f, wf * arms);
                    arm(f.leftArm(), s, -2.25f + 0.45f * b, -0.75f - 0.15f * a, 0.45f, wf * arms);
                    leg(f.rightLeg(), s, -0.35f + 0.50f * c, 0.55f + 0.55f * Math.max(0f, c), wf);
                    leg(f.leftLeg(), s, -0.35f - 0.50f * c, 0.55f + 0.55f * Math.max(0f, -c), wf);
                    f.body().rx += 0.12f * wf;
                    f.head().rx += 0.10f * wf;
                }
            }
        }

        if (Cfg.landing) {
            float land = Math.max(0f, m.land.x) * gate;
            if (land > 0.001f) {
                Rigging.crouch(f, s, 0.70f * land);
                Rigging.leanTorso(f, s, 0.25f * land);
                f.body().rx -= 0.18f * land;
                f.head().rx -= 0.35f * land;
                f.rightArm().targetX(-0.75f, Math.min(1f, land) * arms);
                f.leftArm().targetX(-0.75f, Math.min(1f, land) * arms);
                Rigging.armsOut(f, 0.25f * land);
            }
        }
    }

    private static void leg(PartPose leg, Signs s, float x, float knee, float w) {
        leg.targetX(x, w);
        leg.targetY(0f, w);
        leg.bend += s.knee() * knee * w;
    }

    private static void arm(PartPose arm, Signs s, float x, float z, float elbow, float w) {
        if (w <= 0.001f) return;
        arm.targetX(x, w);
        arm.targetY(0f, w);
        arm.targetZ(z, w);
        arm.bend += s.elbow() * elbow * w;
    }
}
