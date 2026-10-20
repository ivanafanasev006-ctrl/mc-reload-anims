package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.util.Mth;

/**
 * Ladders, vines and scaffolding-like climbables: hand-over-hand reaching and alternating
 * knee lifts, phased by height so the hands match the climbing speed and freeze when you stop.
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

        double y = Mth.lerp(pt, rig.player.yo, rig.player.getY());
        float phase = (float) (y * 4.5);
        float a = Mth.sin(phase);

        f.rightArm().targetX(-2.5f + 0.35f * a, 0.9f * c);
        f.leftArm().targetX(-2.5f - 0.35f * a, 0.9f * c);
        Rigging.armsOut(f, -0.10f * c);
        f.rightArm().bend += s.elbow() * (0.35f + 0.35f * Math.max(0f, -a)) * c;
        f.leftArm().bend += s.elbow() * (0.35f + 0.35f * Math.max(0f, a)) * c;

        f.rightLeg().rx -= 0.45f * Math.max(0f, a) * c;
        f.leftLeg().rx -= 0.45f * Math.max(0f, -a) * c;
        f.rightLeg().bend += s.knee() * (0.25f + 0.80f * Math.max(0f, a)) * c;
        f.leftLeg().bend += s.knee() * (0.25f + 0.80f * Math.max(0f, -a)) * c;

        Rigging.leanTorso(f, s, 0.06f * c);
        f.head().rx -= 0.15f * c;
    }
}
