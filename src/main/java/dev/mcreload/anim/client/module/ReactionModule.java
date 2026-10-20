package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;

/** Short recoil when hit: chest and head snap back, arms flinch outward, then settle. */
final class ReactionModule implements AnimModule {
    @Override
    public boolean enabled() {
        return Cfg.reactions;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float h = m.hurt.x * m.gate.v;
        if (Math.abs(h) <= 0.001f) return;
        Rigging.leanTorso(f, s, -0.22f * h);
        f.head().rx -= 0.18f * h;
        f.body().rx += 0.10f * h;
        Rigging.armsOut(f, 0.15f * h);
    }
}
