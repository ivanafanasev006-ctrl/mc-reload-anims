package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;

/**
 * Secondary motion in the spirit of Fresh Animations: limbs and torso are loose and lag behind
 * the body. Speeding up throws the arms back, stopping swings them forward, strafing swings them
 * sideways, turning swings them round, and landing drives the legs up into the hips and flops the
 * arms out. Driven by damped oscillators in {@link MotionState}.
 */
final class PhysicsModule implements AnimModule {
    @Override
    public boolean enabled() {
        return Cfg.liveliness > 0f;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float g = m.gate.v * Math.min(1f, Cfg.liveliness / 0.6f);
        if (g <= 0.001f) return;
        float arms = m.armsFree.v * (1f - m.toolSwingW.v) * g;
        float fwd = m.swayF.x, side = m.swayS.x, vert = m.swayV.x, turn = m.swayT.x;

        // Speeding up: arms trail back, chest lags back, head nods. Stopping: the reverse.
        f.rightArm().rx -= 0.90f * fwd * arms;
        f.leftArm().rx -= 0.90f * fwd * arms;
        Rigging.leanTorso(f, s, 0.30f * fwd * g);

        // Strafing: both arms swing the other way.
        f.rightArm().rz += 0.70f * side * arms;
        f.leftArm().rz += 0.70f * side * arms;
        f.body().rz -= 0.10f * side * g;

        // Vertical jolts: arms flop out, legs ride up into the hips, knees give.
        Rigging.armsOut(f, -0.45f * vert * arms);
        Rigging.elbows(f, s, -0.30f * vert * arms);
        float give = Math.max(0f, -vert);
        f.rightLeg().py -= 2.2f * give * g;
        f.leftLeg().py -= 2.2f * give * g;
        f.rightLeg().bend += s.knee() * 0.35f * give * g;
        f.leftLeg().bend += s.knee() * 0.35f * give * g;

        // Turning: arms and shoulders swing round behind the turn.
        f.rightArm().ry += 0.60f * turn * arms;
        f.leftArm().ry += 0.60f * turn * arms;
        Rigging.twistUpper(f, 0.12f * turn * g);
    }
}
