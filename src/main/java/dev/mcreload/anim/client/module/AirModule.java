package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.util.Mth;

/** Jump tuck, hang at the apex, flailing on long falls, and the squash when landing. */
final class AirModule implements AnimModule {
    @Override
    public boolean enabled() {
        return Cfg.airborne || Cfg.landing;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float gate = m.gate.v;
        if (gate <= 0.001f) return;

        if (Cfg.airborne) {
            float air = m.airW.v * gate;
            if (air > 0.001f) {
                float rise = m.riseW.v * air;
                float fall = m.fallW.v * air;
                float hang = air * (1f - m.riseW.v) * (1f - m.fallW.v);

                // Take-off: one knee up, arms lift.
                f.rightLeg().rx -= 0.30f * rise;
                f.leftLeg().rx += 0.18f * rise;
                f.rightLeg().bend += s.knee() * 0.85f * rise;
                f.leftLeg().bend += s.knee() * 0.35f * rise;
                f.rightArm().rx -= 0.25f * rise;
                f.leftArm().rx -= 0.25f * rise;
                Rigging.armsOut(f, 0.12f * rise);

                // Apex: soft knees.
                f.rightLeg().bend += s.knee() * 0.30f * hang;
                f.leftLeg().bend += s.knee() * 0.30f * hang;

                // Long fall: arms out for balance, legs paddling.
                if (fall > 0.001f) {
                    float a = Mth.sin(m.time * 9f + m.seed), b = Mth.cos(m.time * 7.3f + m.seed);
                    f.rightArm().rz += (0.85f + 0.12f * a) * fall;
                    f.leftArm().rz -= (0.85f + 0.12f * b) * fall;
                    f.rightArm().rx += (-0.35f + 0.10f * b) * fall;
                    f.leftArm().rx += (-0.35f + 0.10f * a) * fall;
                    Rigging.elbows(f, s, 0.35f * fall);
                    f.rightLeg().rx += 0.25f * a * fall;
                    f.leftLeg().rx -= 0.25f * a * fall;
                    f.rightLeg().bend += s.knee() * (0.35f + 0.20f * Math.max(0f, a)) * fall;
                    f.leftLeg().bend += s.knee() * (0.35f + 0.20f * Math.max(0f, -a)) * fall;
                    f.body().rx -= 0.08f * fall;
                    f.head().rx += 0.12f * fall;
                }
            }
        }

        if (Cfg.landing) {
            float land = Math.max(0f, m.land.x) * gate;
            if (land > 0.001f) {
                Rigging.crouch(f, s, 0.55f * land);
                Rigging.leanTorso(f, s, 0.25f * land);
                f.head().rx -= 0.25f * land;
                f.rightArm().rx -= 0.35f * land;
                f.leftArm().rx -= 0.35f * land;
                Rigging.armsOut(f, 0.15f * land);
            }
        }
    }
}
