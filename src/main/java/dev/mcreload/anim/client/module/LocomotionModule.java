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
 * Full-body gait cycles that REPLACE vanilla's stiff limb swing:
 * <ul>
 *   <li>walk: heel-to-toe stride with real knee flex, relaxed bent elbows, shoulder counter-swing;</li>
 *   <li>run: body pitched forward, high knee drive, rear leg folding up behind, arms bent ~90 degrees pumping;</li>
 *   <li>sneak: low cautious creep on bent legs, slow high steps, hands up in front, looking around.</li>
 * </ul>
 * All three are driven by vanilla's limb-swing phase so footsteps stay in sync, and blended by
 * smoothed weights so switching between them never snaps.
 */
final class LocomotionModule implements AnimModule {
    @Override
    public boolean enabled() {
        return Cfg.locomotion;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float ground = m.gate.v * (1f - m.airW.v) * (1f - m.swimW.v) * (1f - m.climbW.v) * (1f - m.waterW.v);
        if (ground <= 0.001f) return;

        float phase = rig.player.walkAnimation.position(pt) * 0.6662f;
        float sin = Mth.sin(phase), cos = Mth.cos(phase);
        float spd = m.speed.v;

        float sneak = m.sneakW.v;
        float run = m.sprintW.v * (1f - sneak);
        float walk = (1f - sneak) * (1f - run);
        float arms = m.armsFree.v * (1f - m.toolSwingW.v);

        if (walk > 0.001f) walk(f, s, ground * walk, arms, sin, cos, AngleMath.clamp(spd / 0.215f, 0f, 1f), m);
        if (run > 0.001f) run(f, s, ground * run, arms, sin, cos, AngleMath.clamp(spd / 0.28f, 0f, 1.1f));
        if (sneak > 0.001f) sneak(f, s, ground * sneak, arms, sin, cos, AngleMath.clamp(spd / 0.065f, 0f, 1f), m);

        brake(f, s, m, ground, arms);

        // Lean back when backpedalling, into strafes, into turns.
        float back = AngleMath.clamp(-m.forward.v / 0.2f, 0f, 1f);
        f.body().rx += 0.10f * back * ground;
        float strafe = AngleMath.clamp(m.strafe.v / 0.2f, -1f, 1f);
        f.body().rz -= 0.12f * strafe * ground;
        float turn = AngleMath.clamp(m.turn.v / 15f, -1f, 1f);
        f.body().rz -= 0.16f * turn * m.moveF() * (0.4f + 0.6f * m.sprintW.v) * ground;
    }

    /** Walk. a = 0 gives a relaxed standing pose (idle). */
    private static void walk(PoseFrame f, Signs s, float w, float arms, float sin, float cos, float a, MotionState m) {
        legX(f.rightLeg(), -0.05f + 0.66f * a * cos, w);
        legX(f.leftLeg(), -0.05f - 0.66f * a * cos, w);
        // Toes turn out a little on the swing.
        f.rightLeg().ry += 0.08f * a * Math.max(0f, sin) * w;
        f.leftLeg().ry -= 0.08f * a * Math.max(0f, -sin) * w;
        // Soft knee at heel strike, deep fold on the swing.
        f.rightLeg().bend += s.knee() * (0.10f + 0.15f * a + 0.95f * a * Math.max(0f, sin)) * w;
        f.leftLeg().bend += s.knee() * (0.10f + 0.15f * a + 0.95f * a * Math.max(0f, -sin)) * w;

        float aw = w * arms;
        // Arms swing wider and cross a little in front of the body on the way forward.
        armPose(f.rightArm(), -0.74f * a * cos, 0.07f, aw);
        armPose(f.leftArm(), 0.74f * a * cos, -0.07f, aw);
        f.rightArm().ry -= 0.14f * a * Math.max(0f, -cos) * aw;
        f.leftArm().ry += 0.14f * a * Math.max(0f, cos) * aw;
        f.rightArm().bend += s.elbow() * (0.25f + 0.50f * a * Math.max(0f, -sin)) * aw;
        f.leftArm().bend += s.elbow() * (0.25f + 0.50f * a * Math.max(0f, sin)) * aw;

        // Shoulders counter-rotate against the hips, head stays steady.
        float twist = -cos * 0.18f * a * w;
        Rigging.twistUpper(f, twist);
        f.head().ry -= twist * 0.5f;
        f.body().rx -= 0.08f * a * w;
        f.head().rx -= 0.07f * a * w;
        // Walking: highest over the planted foot, lowest when the legs are spread;
        // hips sway over the standing leg.
        f.body().py -= 0.05f * a * Math.abs(cos) * w;
        f.body().rz += 0.045f * a * sin * w;
        f.body().px += 0.03f * a * sin * w;
    }

    /** Flat-out sprint: pitched far forward, legs driving and pushing off, arms pumping hard. */
    private static void run(PoseFrame f, Signs s, float w, float arms, float sin, float cos, float a) {
        legX(f.rightLeg(), -0.30f * a + 0.92f * a * cos, w);
        legX(f.leftLeg(), -0.30f * a - 0.92f * a * cos, w);
        // Knee: almost straight at push-off behind, folded tight as the leg drives forward.
        float shift = 0.5f;
        float rSwing = sin * Mth.cos(shift) + cos * Mth.sin(shift);   // sin(phase + shift)
        float lSwing = -rSwing;
        f.rightLeg().bend += s.knee() * (0.12f + 1.85f * Math.max(0f, rSwing)) * a * w;
        f.leftLeg().bend += s.knee() * (0.12f + 1.85f * Math.max(0f, lSwing)) * a * w;

        float aw = w * arms;
        armPose(f.rightArm(), -0.30f * a - 1.20f * a * cos, -0.05f, aw);
        armPose(f.leftArm(), -0.30f * a + 1.20f * a * cos, 0.05f, aw);
        f.rightArm().ry -= 0.22f * a * Math.max(0f, -cos) * aw;
        f.leftArm().ry += 0.22f * a * Math.max(0f, cos) * aw;
        f.rightArm().bend += s.elbow() * (1.50f + 0.25f * Math.max(0f, -sin)) * a * aw;
        f.leftArm().bend += s.elbow() * (1.50f + 0.25f * Math.max(0f, sin)) * a * aw;

        float twist = -cos * 0.32f * a * w;
        Rigging.twistUpper(f, twist);
        f.head().ry -= twist * 0.6f;
        // Pitched hard forward ("running for your life"), head still looking ahead.
        f.body().rx -= 0.48f * a * w;
        Rigging.leanTorso(f, s, 0.18f * a * w);
        f.head().rx -= 0.60f * a * w;
        // Lowest on the planted foot, launched up in the flight phase.
        f.body().py -= (0.10f * Math.abs(sin) - 0.03f) * a * w;
        f.body().rz += 0.05f * a * sin * w;
    }

    /**
     * Ninja creep: body turned side-on, very low and wide, feet stepping one after the other
     * without ever crossing, lead hand reaching low in front, rear hand guarding the face,
     * eyes scanning when standing still.
     */
    private static void sneak(PoseFrame f, Signs s, float w, float arms, float sin, float cos, float a, MotionState m) {
        float turn = 0.55f * w;
        f.body().ry += turn;
        f.head().ry += turn;

        float base = 0.55f;
        legX(f.rightLeg(), -base + 0.32f * a * cos, w);
        legX(f.leftLeg(), -base - 0.32f * a * cos, w);
        f.rightLeg().targetZ(0.34f, w);
        f.leftLeg().targetZ(-0.34f, w);
        float rLift = AngleMath.clamp(0.5f + 2f * sin, 0f, 1f);
        float lLift = AngleMath.clamp(0.5f - 2f * sin, 0f, 1f);
        f.rightLeg().bend += s.knee() * (2f * base + 0.55f * rLift * a) * w;
        f.leftLeg().bend += s.knee() * (2f * base + 0.55f * lLift * a) * w;
        f.body().py -= 2f * Rigging.LEG_SEGMENT_BLOCKS * (1f - Mth.cos(base)) * w;
        f.torso().targetX(0.38f, w);

        float aw = w * arms;
        // Lead hand low and forward, rear hand up by the face.
        f.rightArm().targetX(-1.15f + 0.10f * a * cos, aw);
        f.rightArm().targetY(0f, aw);
        f.rightArm().targetZ(0.15f, aw);
        f.rightArm().bend += s.elbow() * 0.50f * aw;
        f.leftArm().targetX(-1.70f, aw);
        f.leftArm().targetY(0.50f, aw);
        f.leftArm().targetZ(0f, aw);
        f.leftArm().bend += s.elbow() * 1.70f * aw;

        // Weight shifts from foot to foot.
        f.body().rz += 0.08f * a * sin * w;
        f.body().px += 0.04f * a * sin * w;
        // Nervous look-around while standing still.
        float still = 1f - a;
        f.head().ry += (0.40f * Mth.sin(m.time * 0.9f + m.seed) + 0.08f * Mth.sin(m.time * 2.3f)) * still * w;
        f.head().rx += 0.05f * Mth.sin(m.time * 0.6f) * still * w;
    }

    /** Skidding to a stop after a sprint: heels dug in, body thrown back, arms out for balance. */
    private static void brake(PoseFrame f, Signs s, MotionState m, float ground, float arms) {
        float b = Math.max(0f, m.brake.x) * ground;
        if (b <= 0.001f) return;
        float bw = Math.min(1f, b);
        f.rightLeg().overrideX(-0.75f, bw);
        f.rightLeg().overrideBend(s.knee() * 0.25f, bw);
        f.leftLeg().overrideX(0.25f, bw);
        f.leftLeg().overrideBend(s.knee() * 1.05f, bw);
        f.body().rx += 0.35f * b;
        Rigging.leanTorso(f, s, -0.15f * b);
        f.head().rx -= 0.30f * b;
        f.body().py -= 0.10f * b;
        float aw = bw * arms;
        f.rightArm().override(-1.10f, 0f, 0.70f, aw);
        f.leftArm().override(-1.10f, 0f, -0.70f, aw);
        f.rightArm().overrideBend(s.elbow() * 0.25f, aw);
        f.leftArm().overrideBend(s.elbow() * 0.25f, aw);
    }

    private static void legX(PartPose leg, float x, float w) {
        leg.targetX(x, w);
        leg.targetY(0f, w);
    }

    /** @param out sideways angle, positive = away from the body for the right arm. */
    private static void armPose(PartPose arm, float x, float out, float w) {
        if (w <= 0.001f) return;
        arm.targetX(x, w);
        arm.targetY(0f, w);
        arm.targetZ(out, w);
    }
}
