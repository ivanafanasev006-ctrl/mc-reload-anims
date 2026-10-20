package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.util.Mth;

/** Flutter kick while swimming, arm sculling and bicycle legs while treading water. */
final class WaterModule implements AnimModule {
    @Override
    public boolean enabled() {
        return Cfg.water;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float gate = m.gate.v;

        float swim = m.swimW.v * gate;
        if (swim > 0.001f) {
            float kick = Mth.sin(m.time * 7.5f);
            f.rightLeg().rx += 0.28f * kick * swim;
            f.leftLeg().rx -= 0.28f * kick * swim;
            f.rightLeg().bend += s.knee() * (0.25f + 0.30f * Math.max(0f, kick)) * swim;
            f.leftLeg().bend += s.knee() * (0.25f + 0.30f * Math.max(0f, -kick)) * swim;
        }

        float tread = m.waterW.v * (1f - m.swimW.v) * gate;
        if (tread > 0.001f) {
            float w = Mth.sin(m.time * 3.2f + m.seed), w2 = Mth.cos(m.time * 3.2f + m.seed);
            Rigging.armsOut(f, (0.45f + 0.25f * w) * tread);
            f.rightArm().rx += (-0.25f + 0.20f * w2) * tread;
            f.leftArm().rx += (-0.25f - 0.20f * w2) * tread;
            Rigging.elbows(f, s, 0.40f * tread);
            f.rightLeg().rx += 0.35f * w * tread;
            f.leftLeg().rx -= 0.35f * w * tread;
            f.rightLeg().bend += s.knee() * (0.45f + 0.35f * Math.max(0f, w)) * tread;
            f.leftLeg().bend += s.knee() * (0.45f + 0.35f * Math.max(0f, -w)) * tread;
            f.body().py += 0.03f * Mth.sin(m.time * 6.4f + m.seed) * tread;
        }
    }
}
