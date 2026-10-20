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
        legX(f.rightLeg(), -0.05f + 0.62f * a * cos, w);
        legX(f.leftLeg(), -0.05f - 0.62f * a * cos, w);
        f.rightLeg().bend += s.knee() * (0.10f + 0.12f * a + 0.85f * a * Math.max(0f, sin)) * w;
        f.leftLeg().bend += s.knee() * (0.10f + 0.12f * a + 0.85f * a * Math.max(0f, -sin)) * w;

        float aw = w * arms;
        armPose(f.rightArm(), -0.60f * a * cos, 0.06f, aw);
        armPose(f.leftArm(), 0.60f * a * cos, -0.06f, aw);
        f.rightArm().bend += s.elbow() * (0.22f + 0.40f * a * Math.max(0f, -sin)) * aw;
        f.leftArm().bend += s.elbow() * (0.22f + 0.40f * a * Math.max(0f, sin)) * aw;

        Rigging.twistUpper(f, -cos * 0.12f * a * w);
        f.body().rx -= 0.07f * a * w;
        f.head().rx -= 0.06f * a * w;
        // Walking: highest over the planted foot, lowest when the legs are spread.
        f.body().py -= 0.045f * a * Math.abs(cos) * w;
        f.body().rz += 0.025f * a * sin * w;
    }

    private static void run(PoseFrame f, Signs s, float w, float arms, float sin, float cos, float a) {
        // Thigh drives far forward, pushes back less; the knee folds tight during the forward swing.
        legX(f.rightLeg(), -0.30f * a + 0.95f * a * cos, w);
        legX(f.leftLeg(), -0.30f * a - 0.95f * a * cos, w);
        float sw = Mth.sin(0.25f);
        float cw = Mth.cos(0.25f);
        float rSwing = sin * cw + cos * sw;       // sin(phase + 0.25)
        float lSwing = -rSwing;
        f.rightLeg().bend += s.knee() * (0.35f + 1.45f * Math.max(0f, rSwing)) * a * w;
        f.leftLeg().bend += s.knee() * (0.35f + 1.45f * Math.max(0f, lSwing)) * a * w;

        float aw = w * arms;
        armPose(f.rightArm(), -0.25f * a - 0.95f * a * cos, -0.04f, aw);
        armPose(f.leftArm(), -0.25f * a + 0.95f * a * cos, 0.04f, aw);
        f.rightArm().bend += s.elbow() * (1.45f + 0.25f * Math.max(0f, -sin)) * a * aw;
        f.leftArm().bend += s.elbow() * (1.45f + 0.25f * Math.max(0f, sin)) * a * aw;

        Rigging.twistUpper(f, -cos * 0.20f * a * w);
        // Pitched forward ~18 degrees, head keeps looking ahead.
        f.body().rx -= 0.32f * a * w;
        Rigging.leanTorso(f, s, 0.10f * a * w);
        f.head().rx -= 0.36f * a * w;
        // Running: lowest on the planted foot, highest in the flight phase.
        f.body().py -= (0.075f * Math.abs(sin) - 0.02f) * a * w;
        f.body().rz += 0.035f * a * sin * w;
    }

    /** Cautious creep: low on bent legs, slow high steps, hands ready in front, eyes scanning. */
    private static void sneak(PoseFrame f, Signs s, float w, float arms, float sin, float cos, float a, MotionState m) {
        float crouch = 0.55f;
        legX(f.rightLeg(), -crouch + 0.42f * a * cos - 0.25f * a * Math.max(0f, sin), w);
        legX(f.leftLeg(), -crouch - 0.42f * a * cos - 0.25f * a * Math.max(0f, -sin), w);
        f.rightLeg().bend += s.knee() * (2f * crouch + 0.75f * a * Math.max(0f, sin)) * w;
        f.leftLeg().bend += s.knee() * (2f * crouch + 0.75f * a * Math.max(0f, -sin)) * w;
        f.body().py -= 2f * Rigging.LEG_SEGMENT_BLOCKS * (1f - Mth.cos(crouch)) * w;
        f.body().py -= 0.02f * a * Math.abs(sin) * w;

        float aw = w * arms;
        armPose(f.rightArm(), -0.85f + 0.18f * a * cos, 0.22f, aw);
        armPose(f.leftArm(), -0.85f - 0.18f * a * cos, -0.22f, aw);
        f.rightArm().bend += s.elbow() * 1.35f * aw;
        f.leftArm().bend += s.elbow() * 1.35f * aw;

        // Weight transfers from foot to foot, shoulders slightly hunched.
        f.body().rz += 0.07f * a * sin * w;
        Rigging.twistUpper(f, -cos * 0.08f * a * w);
        Rigging.leanTorso(f, s, 0.08f * w);
        // Nervous look-around while standing still.
        float still = 1f - a;
        f.head().ry += (0.32f * Mth.sin(m.time * 0.9f + m.seed) + 0.08f * Mth.sin(m.time * 2.3f)) * still * w;
        f.head().rx += 0.05f * Mth.sin(m.time * 0.6f) * still * w;
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
