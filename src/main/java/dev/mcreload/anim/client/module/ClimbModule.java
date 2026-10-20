package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.util.Mth;

/**
 * Ladders, vines and other climbables: a plain, smooth hand-over-hand climb. Hands move straight
 * up and down the rails one after the other, feet step up the rungs under the body (no splayed
 * knees), the right hand going up together with the left foot. The cycle follows the height
 * actually gained, so everything stops when you stop.
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
        // 1 = hand at its highest, 0 = pulled down to the shoulder. Smooth sine, no snapping.
        float reachR = Mth.sin(p) * 0.5f + 0.5f;
        float reachL = 1f - reachR;
        // Feet step opposite to the hands.
        float stepR = reachL, stepL = reachR;

        float aw = c * m.armsFree.v;
        f.rightArm().override(-2.05f - reachR * 0.60f, 0f, 0.10f, aw);
        f.leftArm().override(-2.05f - reachL * 0.60f, 0f, -0.10f, aw);
        f.rightArm().overrideBend(s.elbow() * (0.25f + 0.95f * (1f - reachR)), aw);
        f.leftArm().overrideBend(s.elbow() * (0.25f + 0.95f * (1f - reachL)), aw);

        // Feet straight up and down the rungs, knees pointing at the ladder.
        f.rightLeg().override(-0.25f - stepR * 0.90f, 0f, 0.03f, c);
        f.leftLeg().override(-0.25f - stepL * 0.90f, 0f, -0.03f, c);
        f.rightLeg().overrideBend(s.knee() * (0.20f + stepR * 1.40f), c);
        f.leftLeg().overrideBend(s.knee() * (0.20f + stepL * 1.40f), c);

        // Close to the ladder, a gentle sway with each pull.
        float shift = reachR - reachL;
        f.body().pz += 0.05f * c;
        f.body().rz += 0.04f * shift * c;
        Rigging.leanTorso(f, s, 0.08f * c);
        f.head().rx -= 0.30f * c;
        f.head().ry += 0.08f * shift * c;
    }
}
