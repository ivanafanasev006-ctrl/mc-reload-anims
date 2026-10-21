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

    private static final float PI = Mth.PI;

    private static float deg(float d) {
        return d * Mth.DEG_TO_RAD;
    }

    /** Model pixels to blocks (player model scale). */
    private static float px(float p) {
        return p / 16f * 0.9375f;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        // Vanilla's crouch pose (torso tipped, parts shifted down) switches on and off instantly
        // with isCrouching(). Undo it with exactly that switch, never with the smoothed sneak
        // weight: otherwise, tapping shift, torso, head and legs come apart for a moment while
        // the weight catches up. Our own sneak pose then blends in smoothly on top.
        if (rig.player.isCrouching()) cancelVanillaCrouch(f);

        float ground = m.gate.v * (1f - m.airW.v) * (1f - m.swimW.v) * (1f - m.climbW.v) * (1f - m.waterW.v);
        if (ground <= 0.001f) return;

        float ls = rig.player.walkAnimation.position(pt) * 0.6662f;
        float amt = Math.min(1f, rig.player.walkAnimation.speed(pt));
        float move = AngleMath.clamp(m.speed.v / 0.06f, 0f, 1f);

        float sneak = m.sneakW.v;
        float run = m.sprintW.v * (1f - sneak);
        float walk = (1f - run);
        float arms = m.armsFree.v * (1f - m.toolSwingW.v);

        float standW = ground * (1f - move) * (1f - sneak);
        if (standW > 0.001f) stand(f, s, standW, arms, m);
        float walkW = ground * move * walk;
        if (walkW > 0.001f) walk(f, s, walkW, arms, ls, amt);
        float runW = ground * move * run;
        if (runW > 0.001f) sprint(f, s, runW, arms, ls, amt, m);
        if (sneak > 0.001f) sneak(f, s, ground * sneak, arms, ls, amt, move, m);

        brake(f, s, m, ground, arms);

        // Lean back when backpedalling, into strafes, into turns.
        float back = AngleMath.clamp(-m.forward.v / 0.2f, 0f, 1f);
        f.body().rx += 0.10f * back * ground;
        float strafe = AngleMath.clamp(m.strafe.v / 0.2f, -1f, 1f);
        f.body().rz -= 0.10f * strafe * ground;
        float turn = AngleMath.clamp(m.turn.v / 15f, -1f, 1f);
        f.body().rz -= 0.12f * turn * m.moveF() * (0.4f + 0.6f * m.sprintW.v) * ground;
    }

    /**
     * Standing (after Mo'Bends' stand): weight a little on one leg, toes turned out, soft knees and
     * elbows, arms hanging a little away from the body and breathing, chest rising and falling.
     */
    private static void stand(PoseFrame f, Signs s, float w, float arms, MotionState m) {
        float phase = m.time * 2f;
        float breath = (Mth.cos(phase) - 1f) / 2f;           // 0 .. -1
        // Which leg carries the weight drifts slowly from one side to the other.
        float shift = Mth.sin(m.time * 0.23f + m.seed);
        float rRelax = Math.max(0f, shift), lRelax = Math.max(0f, -shift);
        f.rightLeg().targetX(deg(-2f) - 0.10f * rRelax, w);
        f.rightLeg().targetY(deg(5f), w);
        f.rightLeg().targetZ(deg(3f), w);
        f.leftLeg().targetX(deg(-2f) - 0.10f * lRelax, w);
        f.leftLeg().targetY(deg(-5f), w);
        f.leftLeg().targetZ(deg(-3f), w);
        f.rightLeg().bend += s.knee() * (deg(5f) + 0.22f * rRelax) * w;
        f.leftLeg().bend += s.knee() * (deg(5f) + 0.22f * lRelax) * w;
        // Hips drop toward the relaxed leg and slide over the standing one.
        f.body().rz += 0.025f * shift * w;
        f.body().px += px(0.35f) * -shift * w;

        float aw = w * arms;
        float out = deg(4f) + deg(2.5f) * -breath;
        armPose(f.rightArm(), deg(-3f), out, aw);
        armPose(f.leftArm(), deg(-3f), -out, aw);
        f.rightArm().bend += s.elbow() * deg(10f) * aw;
        f.leftArm().bend += s.elbow() * deg(10f) * aw;
        Rigging.leanTorso(f, s, deg(3f) * -breath * w);
    }

    /** Walk (Mo'Bends): moderate leg swing, the swinging leg's knee folds, shoulders counter-rotate. */
    private static void walk(PoseFrame f, Signs s, float w, float arms, float ls, float amt) {
        float c = Mth.cos(ls), sn = Mth.sin(ls);
        float legSwing = 0.7f * amt;
        legX(f.rightLeg(), deg(-5f) + c * legSwing, w);
        legX(f.leftLeg(), deg(-5f) - c * legSwing, w);
        f.rightLeg().targetZ(deg(2f), w);
        f.leftLeg().targetZ(deg(-2f), w);
        // Knee folds while the leg swings through, straight while it carries the weight.
        float rKnee = AngleMath.clamp(0.5f + 2.5f * sn, 0f, 1f), lKnee = AngleMath.clamp(0.5f - 2.5f * sn, 0f, 1f);
        f.rightLeg().bend += s.knee() * (deg(4f) + deg(45f) * rKnee * Math.min(1f, amt * 1.5f)) * w;
        f.leftLeg().bend += s.knee() * (deg(4f) + deg(45f) * lKnee * Math.min(1f, amt * 1.5f)) * w;

        float aw = w * arms;
        float armSwing = 0.5f * amt + 0.15f * amt;
        armPose(f.rightArm(), -c * armSwing, deg(5f), aw);
        armPose(f.leftArm(), c * armSwing, deg(-5f), aw);
        f.rightArm().bend += s.elbow() * deg(10f + 10f * c) * aw;
        f.leftArm().bend += s.elbow() * deg(10f + 10f * -sn) * aw;

        float k = Math.min(1f, amt * 1.5f);
        Rigging.twistUpper(f, deg(-20f) * c * k * w * arms);
        Rigging.leanTorso(f, s, deg(3f + 5f * Mth.cos(ls * 2f)) * k * w);
        f.body().py += px(0.6f) * Mth.cos(ls * 2f) * k * w;
    }

    /**
     * Sprint (Mo'Bends): long powerful strides with the knees driving through, the torso leaning
     * into it and twisting hard with the arms, elbows pumping, a springy bounce on every step.
     * The head keeps looking ahead.
     */
    private static void sprint(PoseFrame f, Signs s, float w, float arms, float ls0, float amt, MotionState m) {
        float ls = ls0 * 0.8f;
        float c = Mth.cos(ls), sn = Mth.sin(ls);
        float legSwing = 1.26f * amt;
        legX(f.rightLeg(), deg(-5f) + c * legSwing, w);
        legX(f.leftLeg(), deg(-5f) - c * legSwing, w);
        f.rightLeg().targetZ(deg(2f), w);
        f.leftLeg().targetZ(deg(-2f), w);
        float knee = 0.7f * amt;
        f.rightLeg().bend += s.knee() * (deg(40f) + Mth.cos(ls + PI + 1.8f) * knee) * w;
        f.leftLeg().bend += s.knee() * (deg(40f) + Mth.cos(ls + 1.8f) * knee) * w;

        float aw = w * arms;
        boolean blade = m.holdingBlade;
        float armSwing = 1.1f * amt;
        PartPose main = m.mainRight ? f.rightArm() : f.leftArm();
        if (blade) {
            // With a sword: the sword arm swings less and keeps the blade pointing forward-down,
            // the free arm pumps as usual.
            float mc = m.mainRight ? -c : c;
            armPose(main, deg(-10f) + mc * armSwing * 0.45f, (m.mainRight ? 1f : -1f) * deg(10f), aw);
            main.bend += s.elbow() * deg(25f) * aw;
            PartPose off = m.mainRight ? f.leftArm() : f.rightArm();
            float oc = m.mainRight ? c : -c;
            armPose(off, oc * armSwing, (m.mainRight ? -1f : 1f) * deg(5f), aw);
            off.bend += s.elbow() * deg(sn * (m.mainRight ? 1f : -1f) > 0f ? 10f : 45f) * aw;
        } else {
            armPose(f.rightArm(), -c * armSwing, deg(5f), aw);
            armPose(f.leftArm(), c * armSwing, deg(-5f), aw);
            // Elbows: the arm coming forward bends more.
            float rEl = AngleMath.clamp(0.5f + 2.5f * sn, 0f, 1f);
            f.rightArm().bend += s.elbow() * deg(10f + 35f * rEl) * aw;
            f.leftArm().bend += s.elbow() * deg(10f + 35f * (1f - rEl)) * aw;
        }

        Rigging.twistUpper(f, deg(-40f) * c * w * arms);
        // Leaning into the run: the whole body a little, the chest more.
        f.body().rx -= deg(12f) * amt * w;
        f.head().rx -= deg(12f) * amt * w;
        Rigging.leanTorso(f, s, deg(12f + 8f * Mth.cos(ls * 2f)) * w);
        f.body().py += px(1.5f) * Mth.cos(ls * 2f + 0.6f) * w;
        // Into turns with the shoulders.
        float yaw = AngleMath.clamp(m.turn.v * 0.3f, -10f, 10f);
        f.torso().rz -= deg(yaw) * 0.5f * w;
    }

    /**
     * Sneak (Mo'Bends): the upper body leans well forward and bobs with each step, long careful
     * strides with the knees bending in turn, arms a little forward and out to the sides.
     */
    private static void sneak(PoseFrame f, Signs s, float w, float arms, float ls, float amt0, float move, MotionState m) {
        float c = Mth.cos(ls), sn = Mth.sin(ls);

        float legSwing = 1.54f * amt0;
        legX(f.rightLeg(), deg(-5f) + c * legSwing, w);
        legX(f.leftLeg(), deg(-5f) - c * legSwing, w);
        f.rightLeg().targetZ(deg(10f), w);
        f.leftLeg().targetZ(deg(-10f), w);
        float rK = AngleMath.clamp(0.5f + 2.5f * sn, 0f, 1f);
        float rKnee = deg(10f) + deg(35f) * (move > 0.1f ? rK : 0.5f);
        float lKnee = deg(10f) + deg(35f) * (move > 0.1f ? 1f - rK : 0.5f);
        f.rightLeg().bend += s.knee() * rKnee * w;
        f.leftLeg().bend += s.knee() * lKnee * w;
        f.body().py -= px(1.3f) * w;

        float aw = w * arms;
        armPose(f.rightArm(), deg(-20f) + deg(20f) * -c * move, deg(10f), aw);
        armPose(f.leftArm(), deg(-20f) + deg(20f) * c * move, deg(-10f), aw);
        f.rightArm().bend += s.elbow() * deg(15f) * aw;
        f.leftArm().bend += s.elbow() * deg(15f) * aw;

        Rigging.leanTorso(f, s, deg(25f + 5f * Mth.cos(ls * 2f) * move) * w);
        // Nervous look-around while standing still.
        float still = 1f - move;
        f.head().ry += (0.35f * Mth.sin(m.time * 0.9f + m.seed) + 0.06f * Mth.sin(m.time * 2.3f)) * still * w;
    }

    /**
     * Stopping out of a sprint: the body's momentum carries the chest on forward for a moment,
     * then the front foot braces and the body sits back a little to keep its balance, arms coming
     * forward. Short and understated.
     */
    private static void brake(PoseFrame f, Signs s, MotionState m, float ground, float arms) {
        float T = m.since(m.brakeStart);
        float t = T / 0.5f;
        if (t < 0f || t >= 1f) return;
        float b = AngleMath.ease(t / 0.08f) * (1f - AngleMath.ease((t - 0.5f) / 0.5f)) * ground;
        if (b <= 0.001f) return;
        // Momentum: the chest keeps going for ~0.12 s, then the weight goes back onto the heels.
        float carry = Mth.sin(Mth.PI * AngleMath.clamp(T / 0.24f, 0f, 1f));
        float sit = AngleMath.ease((T - 0.08f) / 0.15f);
        Rigging.leanTorso(f, s, (deg(12f) * carry - deg(7f) * sit) * b);
        f.body().rx += deg(6f) * sit * b;
        f.head().rx -= deg(5f) * sit * b;
        // Front foot planted ahead, both knees giving a little.
        float side = m.mainRight ? 1f : -1f;
        PartPose lead = side > 0 ? f.leftLeg() : f.rightLeg();
        PartPose rear = side > 0 ? f.rightLeg() : f.leftLeg();
        lead.override(deg(-22f) - deg(6f) * sit, 0f, deg(-4f) * side, b);
        rear.override(deg(8f), 0f, deg(4f) * side, b);
        lead.overrideBend(s.knee() * deg(14f), b);
        rear.overrideBend(s.knee() * deg(26f), b);
        f.body().py -= px(1.2f) * sit * b;
        // Arms swing forward with the momentum and stay a little out for balance.
        float aw = b * arms;
        f.rightArm().override(deg(-30f) - deg(10f) * carry, 0f, deg(14f), aw);
        f.leftArm().override(deg(-30f) - deg(10f) * carry, 0f, deg(-14f), aw);
        f.rightArm().overrideBend(s.elbow() * deg(25f), aw);
        f.leftArm().overrideBend(s.elbow() * deg(25f), aw);
    }

    /** Exactly undo HumanoidModel's crouch offsets (we bend at the waist instead). */
    private static void cancelVanillaCrouch(PoseFrame f) {
        f.torso().overrideX(0f, 1f);
        f.torso().py -= 3.2f;
        f.head().py -= 4.2f;
        f.rightArm().py -= 3.2f;
        f.leftArm().py -= 3.2f;
        f.rightLeg().pz -= 4.0f;
        f.leftLeg().pz -= 4.0f;
        f.rightLeg().py -= 0.2f;
        f.leftLeg().py -= 0.2f;
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
