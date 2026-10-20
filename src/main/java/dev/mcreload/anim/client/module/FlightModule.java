package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.util.Mth;

/**
 * Elytra gliding and creative flight. Gliding keeps vanilla's streamlined pose but lets the limbs
 * ride the wind: arms swept back and trembling, legs trailing with a slow wave that runs from hip to
 * knee. Creative hover: legs dangling and slowly kicking, arms loose, a gentle bob.
 */
final class FlightModule implements AnimModule {
    @Override
    public boolean enabled() {
        return Cfg.airborne;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float t = m.time;

        float glide = m.elytraW.v;
        if (glide > 0.001f) {
            float speed = Math.min(1f, (float) rig.player.getDeltaMovement().length() / 1.5f);
            float wave = Mth.sin(t * 6f + m.seed), wave2 = Mth.sin(t * 6f + m.seed - 0.9f);
            float tremble = Mth.sin(t * 23f) * 0.03f * speed;
            Rigging.armsOut(f, (0.10f + 0.05f * wave + tremble) * glide);
            f.rightArm().rx += (0.10f + 0.05f * wave2) * glide;
            f.leftArm().rx += (0.10f - 0.05f * wave2) * glide;
            Rigging.elbows(f, s, (0.20f + 0.10f * speed) * glide);
            // Legs trail; the wave runs down the leg (hip first, knee a moment later).
            f.rightLeg().rx += 0.10f * wave * glide;
            f.leftLeg().rx -= 0.10f * wave * glide;
            f.rightLeg().bend += s.knee() * (0.15f + 0.20f * Math.max(0f, wave2)) * glide;
            f.leftLeg().bend += s.knee() * (0.15f + 0.20f * Math.max(0f, -wave2)) * glide;
        }

        float fly = m.flyW.v;
        if (fly > 0.001f) {
            float a = Mth.sin(t * 2.2f + m.seed), b = Mth.sin(t * 2.2f + m.seed + 1.4f);
            float move = m.moveF();
            f.rightLeg().targetX(0.10f + 0.25f * a + 0.20f * move, fly);
            f.leftLeg().targetX(0.10f - 0.25f * a + 0.20f * move, fly);
            f.rightLeg().bend += s.knee() * (0.45f + 0.30f * Math.max(0f, b)) * fly;
            f.leftLeg().bend += s.knee() * (0.45f + 0.30f * Math.max(0f, -b)) * fly;
            Rigging.legsOut(f, 0.06f * fly);
            float arms = fly * m.armsFree.v;
            f.rightArm().targetX(-0.20f + 0.10f * b - 0.30f * move, arms);
            f.leftArm().targetX(-0.20f - 0.10f * b - 0.30f * move, arms);
            Rigging.armsOut(f, 0.25f * fly);
            Rigging.elbows(f, s, 0.35f * fly);
            f.body().rx -= 0.25f * move * fly;
            f.body().py += 0.03f * Mth.sin(t * 1.6f + m.seed) * fly;
        }
    }
}
