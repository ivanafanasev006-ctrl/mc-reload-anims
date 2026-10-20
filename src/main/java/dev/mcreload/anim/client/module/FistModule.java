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
 * Bare-handed fighting (after Mo'Bends' punch): once you start punching you raise a boxing guard,
 * fists by the chin, feet staggered. Punches ALTERNATE left and right: the punching arm snaps out
 * straight toward where you look while the shoulders rotate into it, then it comes back to guard.
 * The guard relaxes a couple of seconds after the last punch.
 */
final class FistModule implements AnimModule {
    @Override
    public boolean enabled() {
        return Cfg.combat;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float gate = m.gate.v * m.armsFree.v;
        float guard = m.guardW.v * gate;
        boolean punching = m.slashKind == MotionState.PUNCH && m.slashProgress() < 1.2f;
        if (guard <= 0.001f && !punching) return;
        float w = Math.max(guard, punching ? gate : 0f);

        // Guard: fists up by the chin, elbows tucked.
        guardArm(f.rightArm(), s, 1f, w);
        guardArm(f.leftArm(), s, -1f, w);

        // Staggered stance when standing still: lead foot opposite the main hand.
        float still = (1f - m.moveF()) * m.groundW.v;
        float lw = w * still;
        float main = m.slashSide;
        PartPose lead = main > 0 ? f.leftLeg() : f.rightLeg();
        PartPose rear = main > 0 ? f.rightLeg() : f.leftLeg();
        lead.targetX(-0.35f, lw);
        lead.bend += s.knee() * 0.45f * lw;
        rear.targetX(0.20f, lw);
        rear.bend += s.knee() * 0.35f * lw;
        Rigging.legsOut(f, 0.08f * lw);
        f.body().py -= 0.04f * lw;
        Rigging.twistUpper(f, main * 0.15f * w);

        if (!punching) return;
        float t = m.slashProgress();
        float e;
        if (t < 0.28f) e = AngleMath.ease(t / 0.28f);
        else if (t < 0.50f) e = 1f;
        else e = 1f - AngleMath.ease((t - 0.50f) / 0.50f);
        float pw = e * gate;
        if (pw <= 0.001f) return;

        // Which fist: alternates every punch.
        float armSide = m.punchSide > 0 ? main : -main;
        PartPose arm = armSide > 0 ? f.rightArm() : f.leftArm();
        float pitch = rig.player.getXRot() * Mth.DEG_TO_RAD;
        arm.override(-1.57f + pitch, -0.12f * armSide, 0f, pw);
        arm.overrideBend(0f, pw);

        // Shoulders drive the punch: punching shoulder forward, weight onto the lead foot.
        Rigging.twistUpper(f, -armSide * 0.42f * pw);
        f.body().rx -= 0.08f * pw;
        lead.bend += s.knee() * 0.15f * pw * still;
        f.body().py -= 0.02f * pw * still;
    }

    /** @param side +1 right arm, -1 left arm. */
    private static void guardArm(PartPose arm, Signs s, float side, float w) {
        // Override, so the guard holds even while walking (no averaging with the walk arm swing).
        arm.override(-1.35f, -0.35f * side, -0.10f * side, w);
        arm.overrideBend(s.elbow() * 1.75f, w);
    }
}
