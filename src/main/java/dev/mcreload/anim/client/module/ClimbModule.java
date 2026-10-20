package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.util.Mth;

/**
 * Ladders, vines and other climbables (after Mo'Bends' ladder climb): hand-over-hand reaching with
 * big arm travel and pulling elbows, high alternating knee lifts, the chest rocking toward the
 * ladder on every pull. The cycle follows the height actually gained, so it freezes when you stop.
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
        float armR = Mth.sin(p) * 0.5f + 0.5f;
        float armL = Mth.sin(p + Mth.PI) * 0.5f + 0.5f;
        float armR2 = Mth.sin(p - 0.3f) * 0.5f + 0.5f;
        float armL2 = Mth.sin(p + Mth.PI - 0.3f) * 0.5f + 0.5f;
        float twice = Mth.sin(p * 2f) * 0.5f + 0.5f;
        float legR = Mth.sin(p + Mth.PI) * 0.5f + 0.5f;
        float legL = Mth.sin(p) * 0.5f + 0.5f;
        float legR2 = Mth.sin(p + Mth.PI + 0.3f) * 0.5f + 0.5f;
        float legL2 = Mth.sin(p + 0.3f) * 0.5f + 0.5f;

        float aw = c * m.armsFree.v;
        // Arms reach from overhead (-2.36) down to the chest (-1.14), elbows folding as they pull.
        f.rightArm().targetX(-2.36f + armR * 1.22f, aw);
        f.leftArm().targetX(-2.36f + armL * 1.22f, aw);
        f.rightArm().targetY(0f, aw);
        f.leftArm().targetY(0f, aw);
        f.rightArm().targetZ(-0.08f, aw);
        f.leftArm().targetZ(0.08f, aw);
        f.rightArm().bend += s.elbow() * armR2 * 1.40f * aw;
        f.leftArm().bend += s.elbow() * armL2 * 1.40f * aw;

        // High alternating knee lifts.
        f.rightLeg().targetX(-0.79f - legR * 0.87f, c);
        f.leftLeg().targetX(-0.79f - legL * 0.87f, c);
        f.rightLeg().bend += s.knee() * (0.35f + legR2 * 1.57f) * c;
        f.leftLeg().bend += s.knee() * (0.35f + legL2 * 1.57f) * c;
        // Climbing pose sits the hips back from the ladder so the knees fit.
        f.body().pz += 0.10f * c;

        // Chest rocks into the ladder on each pull; body sways side to side.
        Rigging.leanTorso(f, s, twice * 0.17f * c);
        f.body().rz += 0.05f * Mth.sin(p) * c;
        f.head().rx -= 0.25f * c;
    }
}
