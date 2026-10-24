package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.util.Mth;

/**
 * Sleeping, a different way each time you lie down (MotionState.sleepStyle): sprawled out like a
 * starfish; face down in the pillow, an arm under it; on the back with the hands behind the head
 * and one knee up. Slow breathing in all of them. The poses were fitted on a box model with the
 * mattress: nothing but the hands under the pillow goes into the bed.
 */
final class SleepModule implements AnimModule {
    @Override
    public boolean enabled() {
        return Cfg.idle;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        // Off at once on waking: vanilla stands the body up in one go, so do the limbs.
        float w = rig.player.isSleeping() ? m.sleepW.v : 0f;
        if (w <= 0.001f) return;
        float breath = Mth.sin(m.time * 1.1f + m.seed);
        switch (m.sleepStyle) {
            case MotionState.SLEEP_FACE_DOWN -> faceDown(f, s, w, breath);
            case MotionState.SLEEP_HANDS_BEHIND -> handsBehind(f, s, w, breath);
            default -> starfish(f, s, w, breath);
        }
    }

    /** Sprawled out: one arm flung over the head, legs apart. */
    private static void starfish(PoseFrame f, Signs s, float w, float breath) {
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

    /**
     * Face down in the pillow: the body rolled over onto the front, one arm up and folded under
     * the pillow, the other along the side, legs straight and a little apart, the head turned a
     * touch to the side.
     */
    private static void faceDown(PoseFrame f, Signs s, float w, float breath) {
        f.body().ry += Mth.PI * w;
        f.rightArm().override(-1.67f, 0.48f, 0.98f, w);
        f.rightArm().overrideBend(s.elbow() * 0.86f, w);
        f.rightArm().overrideBendAxis(0.09f, w);
        f.leftArm().override(0.08f, 0f, -0.16f - 0.02f * breath, w);
        f.leftArm().overrideBend(s.elbow() * 0.12f, w);
        f.rightLeg().override(0f, 0f, 0.08f, w);
        f.leftLeg().override(0.05f, 0f, -0.20f, w);
        f.rightLeg().overrideBend(s.knee() * 0.05f, w);
        f.leftLeg().overrideBend(s.knee() * 0.12f, w);
        Rigging.leanTorso(f, s, -0.03f * breath * w);
        f.head().override(0f, 0.30f, 0f, w);
    }

    /**
     * On the back, the hands behind the head (elbows out to the sides) and the left knee up, the
     * foot flat on the bed beside the other knee.
     */
    private static void handsBehind(PoseFrame f, Signs s, float w, float breath) {
        f.rightArm().override(-2.84f, 1.50f, -0.20f, w);
        f.leftArm().override(-2.84f, -1.50f, 0.20f, w);
        f.rightArm().overrideBend(s.elbow() * (1.08f + 0.03f * breath), w);
        f.leftArm().overrideBend(s.elbow() * (1.08f + 0.03f * breath), w);
        f.rightArm().overrideBendAxis(0.73f, w);
        f.leftArm().overrideBendAxis(-0.73f, w);
        f.rightLeg().override(0f, 0.10f, 0.10f, w);
        f.rightLeg().overrideBend(s.knee() * 0.06f, w);
        f.leftLeg().override(-1.00f, -0.10f, -0.20f, w);
        f.leftLeg().overrideBend(s.knee() * 1.90f, w);
        Rigging.leanTorso(f, s, -0.03f * breath * w);
        f.head().override(0.10f, 0f, 0f, w);
    }
}
