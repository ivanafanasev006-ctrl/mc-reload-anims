package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.math.AngleMath;
import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.util.Mth;

/**
 * Standing still: breathing, relaxed elbows and a slow shift of weight from foot to foot. After a
 * minute of doing nothing at all, dozing off on your feet (whatever is in hand).
 */
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
        float arms = m.armsFree.v;
        Rigging.armsOut(f, (0.025f + 0.015f * breath) * still * arms);
        Rigging.elbows(f, s, 0.10f * still * arms);
        f.head().rx += 0.01f * breath * still;

        float shift = Mth.sin(m.time * 0.37f + m.seed * 1.7f);
        f.body().rz += 0.015f * shift * still;
        f.rightLeg().bend += s.knee() * (0.05f + 0.06f * Math.max(0f, shift)) * still;
        f.leftLeg().bend += s.knee() * (0.05f + 0.06f * Math.max(0f, -shift)) * still;

        doze(m, f, s, m.dozeW.v * still);
    }

    /**
     * Nodding off: the head sinks slowly onto the chest, jerks back up a little and sinks again;
     * the shoulders drop, the arms hang loose (whatever they hold), the knees sag and the whole
     * body sways a touch with slow, deep breaths. "Z z z" rise from the head (SleepZzz).
     */
    private static void doze(MotionState m, PoseFrame f, Signs s, float w) {
        if (w <= 0.001f) return;
        float t = m.since(m.dozeStart);
        float u = (t / 4.5f) % 1f;
        float sink = u < 0.85f ? 0.3f + 0.7f * AngleMath.ease(u / 0.85f) : 1f - 0.7f * AngleMath.ease((u - 0.85f) / 0.15f);
        float breath = Mth.sin(t * 1.4f);
        f.head().override(0.20f + 0.55f * sink, 0.10f * Mth.sin(t * 0.21f + m.seed), 0.10f * sink, w);
        Rigging.leanTorso(f, s, (0.07f + 0.04f * sink + 0.02f * breath) * w);
        float aw = w * m.armsFree.v;
        f.rightArm().override(0.04f, 0f, 0.05f, aw);
        f.leftArm().override(0.04f, 0f, -0.05f, aw);
        f.rightArm().overrideBend(s.elbow() * 0.12f, aw);
        f.leftArm().overrideBend(s.elbow() * 0.12f, aw);
        f.rightLeg().bend += s.knee() * 0.08f * w;
        f.leftLeg().bend += s.knee() * 0.08f * w;
        f.body().py -= 0.015f * w;
        f.body().rz += 0.02f * Mth.sin(t * 0.5f + m.seed) * w;
    }
}
