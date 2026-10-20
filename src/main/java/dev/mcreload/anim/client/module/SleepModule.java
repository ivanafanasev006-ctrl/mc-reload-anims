package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.util.Mth;

/** Sleeping sprawled out like a starfish: one arm flung over the head, legs apart, slow breathing. */
final class SleepModule implements AnimModule {
    @Override
    public boolean enabled() {
        return Cfg.idle;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float w = m.sleepW.v;
        if (w <= 0.001f) return;
        float breath = Mth.sin(m.time * 1.1f + m.seed);
        f.rightArm().override(-0.35f, 0f, 1.25f + 0.04f * breath, w);
        f.rightArm().overrideBend(s.elbow() * 0.35f, w);
        f.leftArm().override(-2.70f, 0f, -0.45f, w);
        f.leftArm().overrideBend(s.elbow() * 1.00f, w);
        f.rightLeg().override(0f, 0.15f, 0.42f, w);
        f.leftLeg().override(0f, -0.15f, -0.48f, w);
        f.rightLeg().overrideBend(s.knee() * 0.25f, w);
        f.leftLeg().overrideBend(s.knee() * 0.10f, w);
        Rigging.leanTorso(f, s, -0.03f * breath * w);
        f.head().ry += 0.25f * w;
        f.head().rz += 0.15f * w;
    }
}
