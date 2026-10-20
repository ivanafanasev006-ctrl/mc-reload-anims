package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.util.Mth;

/**
 * Ladders, vines and other climbables, climbed like a rock wall: each hand reaches high and wide
 * for the next hold and hauls the body up with a bending elbow, the legs splay out frog-style and
 * push with deep knee bends, and the whole body shifts its weight from side to side with every
 * pull. The cycle follows the height actually gained, so everything freezes when you stop.
 */
final class ClimbModule implements AnimModule {
    @Override
    public boolean enabled() {
        return Cfg.climbing;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float c = m.climbW.v * m.gate.v;
        if (c <= 0.001f) return;

        float p = m.climbPhase;
        // 0 = hand at the chest after the pull, 1 = fully reaching up for the next hold.
        float reachR = Mth.sin(p) * 0.5f + 0.5f;
        float reachL = 1f - reachR;
        float pullR = Mth.sin(p - 0.5f) * 0.5f + 0.5f;
        float pullL = 1f - pullR;
        // Legs push opposite to the arms (right hand reaches while the left foot pushes).
        float stepR = reachL, stepL = reachR;

        float aw = c * m.armsFree.v;
        f.rightArm().targetX(-1.25f - reachR * 1.65f, aw);
        f.leftArm().targetX(-1.25f - reachL * 1.65f, aw);
        f.rightArm().targetY(0f, aw);
        f.leftArm().targetY(0f, aw);
        f.rightArm().targetZ(0.20f + 0.25f * reachR, aw);
        f.leftArm().targetZ(-0.20f - 0.25f * reachL, aw);
        f.rightArm().bend += s.elbow() * (0.25f + 1.45f * (1f - pullR)) * aw;
        f.leftArm().bend += s.elbow() * (0.25f + 1.45f * (1f - pullL)) * aw;

        // Frog legs: knees wide, one foot high on the next rung, the other straightening to push.
        f.rightLeg().targetX(-0.55f - stepR * 0.95f, c);
        f.leftLeg().targetX(-0.55f - stepL * 0.95f, c);
        f.rightLeg().targetZ(0.38f, c);
        f.leftLeg().targetZ(-0.38f, c);
        f.rightLeg().bend += s.knee() * (0.45f + stepR * 1.55f) * c;
        f.leftLeg().bend += s.knee() * (0.45f + stepL * 1.55f) * c;
        f.body().pz += 0.12f * c;

        // Weight shifts toward the hand that is pulling, chest rocks into the wall on each pull.
        float shift = reachR - reachL;
        f.body().rz += 0.14f * shift * c;
        f.body().px -= 0.05f * shift * c;
        Rigging.leanTorso(f, s, (0.12f + 0.12f * Math.abs(Mth.sin(p))) * c);
        f.head().rx -= 0.35f * c;
        f.head().ry += 0.20f * shift * c;
    }
}
