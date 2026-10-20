package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.math.AngleMath;
import dev.mcreload.anim.client.pose.PartPose;
import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.client.rig.Weapons;
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
        boolean drag = Weapons.isMeleeWeapon(rig.player.getMainHandItem()) && !(MotionState.isWeapon(m.slashKind) && m.slashProgress() < 1.05f);
        if (run > 0.001f) run(f, s, ground * run, arms, sin, cos, AngleMath.clamp(spd / 0.28f, 0f, 1.1f), m, drag);
        if (sneak > 0.001f) sneak(f, s, ground * sneak, arms, AngleMath.clamp(spd / 0.06f, 0f, 1f), m);

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

    /**
     * Sprint, "in a hurry": only a slight forward lean, but a big loose bounce on every stride,
     * shoulders and arms thrown around, and small imperfect wobbles so no two strides look alike.
     */
    private static void run(PoseFrame f, Signs s, float w, float arms, float sin, float cos, float a, MotionState m, boolean drag) {
        float t = m.time + m.seed;
        // Stride-to-stride irregularity: amplitude and timing wander a little.
        float vary = 1f + 0.18f * wobble(t * 0.9f, 0f);
        legX(f.rightLeg(), -0.18f * a + 0.95f * a * vary * cos, w);
        legX(f.leftLeg(), -0.18f * a - 0.95f * a * vary * cos, w);
        // Knee: almost straight at push-off behind, folded tight as the leg drives forward.
        float shift = 0.5f;
        float rSwing = sin * Mth.cos(shift) + cos * Mth.sin(shift);   // sin(phase + shift)
        float lSwing = -rSwing;
        f.rightLeg().bend += s.knee() * (0.15f + 1.75f * Math.max(0f, rSwing)) * a * w;
        f.leftLeg().bend += s.knee() * (0.15f + 1.75f * Math.max(0f, lSwing)) * a * w;
        // Feet land slightly apart, not on a line.
        f.rightLeg().targetZ(0.06f, w);
        f.leftLeg().targetZ(-0.06f, w);

        float aw = w * arms;
        float swing = 1.30f * a * vary;
        if (drag) {
            // Sword trailing behind in the main hand, the free arm pumping on its own.
            PartPose main = m.mainRight ? f.rightArm() : f.leftArm();
            PartPose off = m.mainRight ? f.leftArm() : f.rightArm();
            float side = m.mainRight ? 1f : -1f;
            float offPhase = m.mainRight ? -cos : cos;
            main.override(0.30f + 0.10f * a * cos * side, 0f, side * 0.22f, aw);
            main.overrideBend(s.elbow() * 0.20f, aw);
            main.overrideRoll(Mth.PI, aw);
            off.override(-0.25f * a + swing * 1.1f * offPhase, 0f, -side * 0.10f, aw);
            off.overrideBend(s.elbow() * 1.45f * a, aw);
        } else {
            armPose(f.rightArm(), -0.20f * a - swing * cos, -0.08f, aw);
            armPose(f.leftArm(), -0.20f * a + swing * cos, 0.08f, aw);
            f.rightArm().ry -= 0.28f * a * Math.max(0f, -cos) * aw;
            f.leftArm().ry += 0.28f * a * Math.max(0f, cos) * aw;
            f.rightArm().bend += s.elbow() * (1.35f + 0.35f * Math.max(0f, -sin)) * a * aw;
            f.leftArm().bend += s.elbow() * (1.35f + 0.35f * Math.max(0f, sin)) * a * aw;
            // Hands flop a little out of rhythm.
            f.rightArm().rz += 0.10f * wobble(t * 2.3f, 1f) * a * aw;
            f.leftArm().rz -= 0.10f * wobble(t * 2.1f, 2f) * a * aw;
        }

        // Shoulders thrown round hard against the hips.
        float twist = -cos * 0.42f * a * w;
        Rigging.twistUpper(f, twist);
        f.head().ry -= twist * 0.7f;
        // Leaning just enough to hurry, head up looking where it's going.
        f.body().rx -= 0.20f * a * w;
        Rigging.leanTorso(f, s, 0.06f * a * w);
        f.head().rx -= 0.20f * a * w;
        // Loose bounce: low as the legs pass each other, launched up in the flight phase,
        // a bit higher on some strides than others.
        float bounce = (Math.abs(cos) - 0.45f) * (0.13f + 0.04f * wobble(t * 0.7f, 3f));
        f.body().py += bounce * a * w;
        // The upper body and head are jolted by each landing and rock side to side.
        float jolt = Math.max(0f, 0.55f - Math.abs(cos));
        Rigging.leanTorso(f, s, 0.10f * jolt * a * w);
        f.head().rx += 0.12f * jolt * a * w;
        f.body().rz += (0.07f * sin + 0.03f * wobble(t * 1.3f, 4f)) * a * w;
        f.body().px += 0.025f * sin * a * w;
        f.head().rz += 0.05f * wobble(t * 1.9f, 5f) * a * w;
        f.head().ry += 0.06f * wobble(t * 1.1f, 6f) * a * w;
    }

    /** Smooth pseudo-random wobble in about [-1, 1]. */
    static float wobble(float t, float seed) {
        return 0.6f * Mth.sin(t * 1.7f + seed * 2.1f) + 0.4f * Mth.sin(t * 2.9f + seed * 3.7f);
    }

    /**
     * Ninja creep: body a little side-on, low on bent legs, each foot clearly lifted and placed
     * in turn, lead hand low in front, rear hand guarding, eyes scanning when standing still.
     */
    private static void sneak(PoseFrame f, Signs s, float w, float arms, float a, MotionState m) {
        float sin = Mth.sin(m.sneakPhase), cos = Mth.cos(m.sneakPhase);
        float turn = 0.30f * w;
        f.body().ry += turn;
        f.head().ry += turn;
        f.rightArm().ry += turn * 0.5f;
        f.leftArm().ry += turn * 0.5f;

        float base = 0.50f;
        // Thighs reach forward and back in turn...
        legX(f.rightLeg(), -base + 0.55f * a * cos, w);
        legX(f.leftLeg(), -base - 0.55f * a * cos, w);
        f.rightLeg().targetZ(0.14f, w);
        f.leftLeg().targetZ(-0.14f, w);
        // ...and the swinging foot is lifted high, the planted one carries the weight.
        float rLift = Math.max(0f, sin);
        float lLift = Math.max(0f, -sin);
        f.rightLeg().bend += s.knee() * (2f * base + 0.95f * rLift * a) * w;
        f.leftLeg().bend += s.knee() * (2f * base + 0.95f * lLift * a) * w;
        f.body().py -= 2f * Rigging.LEG_SEGMENT_BLOCKS * (1f - Mth.cos(base)) * w;
        // Each step dips a little as the weight is transferred.
        f.body().py -= 0.03f * Math.abs(cos) * a * w;
        f.torso().targetX(0.32f, w);

        float aw = w * arms;
        // Lead hand low and forward, rear hand up by the face; both move with the steps.
        f.rightArm().targetX(-1.10f + 0.15f * a * cos, aw);
        f.rightArm().targetY(0f, aw);
        f.rightArm().targetZ(0.15f, aw);
        f.rightArm().bend += s.elbow() * 0.50f * aw;
        f.leftArm().targetX(-1.60f - 0.10f * a * cos, aw);
        f.leftArm().targetY(0.45f, aw);
        f.leftArm().targetZ(0f, aw);
        f.leftArm().bend += s.elbow() * 1.60f * aw;

        // Weight shifts from foot to foot.
        f.body().rz += 0.06f * a * sin * w;
        f.body().px += 0.035f * a * sin * w;
        Rigging.twistUpper(f, 0.08f * a * cos * w);
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
