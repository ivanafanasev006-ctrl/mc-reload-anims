package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.util.Mth;

/** Standing still: breathing, relaxed elbows and a slow shift of weight from foot to foot. */
final class IdleModule implements AnimModule {
    @Override
    public boolean enabled() {
        return Cfg.idle;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float still = (1f - m.moveF()) * m.groundW.v * m.gate.v * (1f - m.swimW.v) * (1f - m.waterW.v);
        if (still <= 0.001f) return;

        float breath = Mth.sin(m.time * 1.7f + m.seed);
        Rigging.leanTorso(f, s, (0.012f + 0.012f * breath) * still);
        Rigging.armsOut(f, (0.025f + 0.015f * breath) * still);
        Rigging.elbows(f, s, 0.10f * still);
        f.head().rx += 0.01f * breath * still;

        float shift = Mth.sin(m.time * 0.37f + m.seed * 1.7f);
        f.body().rz += 0.015f * shift * still;
        f.rightLeg().bend += s.knee() * (0.05f + 0.06f * Math.max(0f, shift)) * still;
        f.leftLeg().bend += s.knee() * (0.05f + 0.06f * Math.max(0f, -shift)) * still;
    }
}
