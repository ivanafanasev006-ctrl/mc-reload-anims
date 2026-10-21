package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.math.AngleMath;
import dev.mcreload.anim.client.pose.PartPose;
import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * Swords and axes, after Mo'Bends: the arm swings out from the shoulder and the whole upper body
 * turns into the cut, the blade turns in the hand, and after a fight the fighter stays in a low,
 * side-on guard for a few seconds. Holding a weapon without fighting changes nothing.
 * <ul>
 *   <li>Sword combo: inward sweep, rising cut, overhead chop, spinning slash.</li>
 *   <li>Axe: heavy overhead chop, heavy sweep.</li>
 * </ul>
 */
final class BladeModule implements AnimModule {
    private final float[] e = new float[3];

    @Override
    public boolean enabled() {
        return Cfg.combat;
    }

    private static float deg(float d) {
        return d * Mth.DEG_TO_RAD;
    }

    private static float px(float p) {
        return p / 16f * 0.9375f;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float gate = m.gate.v;
        if (gate <= 0.001f) return;
        float hd = m.mainRight ? 1f : -1f;
        PartPose main = m.mainRight ? f.rightArm() : f.leftArm();
        PartPose off = m.mainRight ? f.leftArm() : f.rightArm();
        stance(m, f, s, gate, hd, main, off);
        strike(rig, m, f, s, gate, hd, main, off);
    }

    /** Arm rotation from Mo'Bends-style "out to the side by z, then swung round by y" (degrees). */
    private void arm(PartPose arm, float zDeg, float yDeg, float xDeg, float w) {
        Quaternionf q = new Quaternionf().rotationY(deg(yDeg)).mul(new Quaternionf().rotationZ(deg(zDeg)))
                .mul(new Quaternionf().rotationX(deg(xDeg)));
        AngleMath.toModelEuler(q, e);
        arm.override(e[0], e[1], e[2], w);
    }

    // ------------------------------------------------------------------ guard after a fight

    private void stance(MotionState m, PoseFrame f, Signs s, float gate, float hd, PartPose main, PartPose off) {
        float w = m.readyW.v * gate;
        if (w <= 0.001f) return;
        float aw = w * m.armsFree.v;
        float b0 = Mth.sin(m.time * 4f), b1 = Mth.cos(m.time * 3.5f);
        // Side-on, low, leaning in.
        f.body().ry += hd * deg(30f) * w;
        f.head().ry += hd * deg(30f) * w;
        Rigging.leanTorso(f, s, deg(20f + 2f * b0) * w);
        f.rightLeg().override(deg(-30f), deg(25f), deg(10f), w);
        f.leftLeg().override(deg(-30f), deg(-25f), deg(-10f), w);
        f.rightLeg().overrideBend(s.knee() * deg(30f), w);
        f.leftLeg().overrideBend(s.knee() * deg(30f), w);
        f.body().py -= px(2f) * w;
        // Sword arm out to the side, blade tipped forward; guard hand out the other way.
        arm(main, hd * (60f + 5f * b0), hd * 5f * b1, 0f, aw);
        main.overrideBend(s.elbow() * deg(20f), aw);
        arm(off, -hd * (60f - 5f * b1), 0f, 0f, aw);
        off.overrideBend(s.elbow() * deg(60f), aw);
        m.itemTiltTarget = Math.max(m.itemTiltTarget, deg(65f) * aw);
    }

    // ------------------------------------------------------------------ strikes

    private void strike(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float gate, float hd, PartPose main, PartPose off) {
        int kind = m.slashKind;
        if (!MotionState.isWeapon(kind)) return;
        float t = m.slashProgress();
        if (t >= 1.3f) return;
        float in = m.slashChained ? 1f : AngleMath.ease(t / 0.06f);
        float w = in * (1f - AngleMath.ease((t - 0.8f) / 0.4f)) * gate;
        int armIndex = m.mainRight ? PoseFrame.RIGHT_ARM : PoseFrame.LEFT_ARM;
        w *= 1f - rig.foreign[armIndex];      // Better Combat animating the arm wins
        if (w <= 0.001f) return;
        float aw = w * m.armsFree.v;

        // Mo'Bends timing: the cut snaps through in the first third, then holds and recovers.
        float a = AngleMath.clamp(t / 0.6f, 0f, 1f);
        float sw = AngleMath.clamp(a * 3f, 0f, 1f);
        boolean axe = MotionState.isAxe(kind);
        if (axe) sw = AngleMath.ease(AngleMath.clamp((a - 0.12f) * 2.4f, 0f, 1f));
        boolean still = m.moveF() < 0.3f && m.groundW.v > 0.5f;

        float twist, tilt, lean;
        switch (kind) {
            case MotionState.SLASH_H, MotionState.AXE_SWEEP -> {
                // Inward sweep: wound up behind the shoulder, swept flat across to the other side.
                twist = -70f * sw;
                arm(main, hd * 90f, hd * (60f - 180f * sw + twist), 0f, aw);
                main.overrideBend(s.elbow() * deg(10f), aw);
                tilt = 50f;
                lean = 20f - 20f * sw;
            }
            case MotionState.SLASH_BACK -> {
                // Rising cut: from low across the body up and out the other side.
                twist = 40f - 90f * sw;
                arm(main, hd * (20f + 110f * sw), hd * (60f - 150f * sw + twist), 0f, aw);
                main.overrideBend(s.elbow() * deg(20f), aw);
                tilt = 90f;
                lean = 10f - 10f * sw;
            }
            case MotionState.SLASH_V, MotionState.AXE_OVER -> {
                // Overhead chop: blade raised high behind the head, brought straight down with
                // the whole body.
                twist = 20f - 30f * sw;
                float x = Mth.lerp(sw, -170f, -45f);
                arm(main, hd * 10f, hd * twist, x, aw);
                main.overrideBend(s.elbow() * deg(Mth.lerp(sw, 50f, 5f)), aw);
                tilt = Mth.lerp(sw, 20f, 70f);
                lean = Mth.lerp(sw, -5f, 30f);
                f.body().pz -= px(3f) * sw * w;
            }
            default -> {
                // Whirl: arm out flat, a full turn of the whole body.
                float v5 = AngleMath.clamp(a * 1.6f, 0f, 1f);
                float sw2 = AngleMath.clamp(a * 2f, 0f, 1f);
                twist = 20f * a;
                arm(main, hd * (10f + 120f * v5), hd * (-20f + 70f * sw2 + twist), 0f, aw);
                main.overrideBend(s.elbow() * deg(20f), aw);
                tilt = 90f * a;
                lean = 20f - 20f * a;
                f.body().ry += hd * Mth.TWO_PI * (v5 % 1f) * w;
            }
        }
        Rigging.twistShoulders(f, hd * deg(twist) * w);
        Rigging.leanTorso(f, s, deg(lean) * w);
        // Free arm out for balance, elbow bent.
        arm(off, -hd * 70f, hd * 10f, 0f, aw);
        off.overrideBend(s.elbow() * deg(60f), aw);
        m.itemTiltTarget = Math.max(m.itemTiltTarget, deg(tilt) * aw);

        // Footwork: a low split stance when standing, stepping into the cut.
        if (still) {
            f.rightLeg().override(deg(-25f), deg(15f), deg(6f), w);
            f.leftLeg().override(deg(-15f), deg(-15f), deg(-6f), w);
            PartPose lead = m.mainRight ? f.leftLeg() : f.rightLeg();
            lead.overrideX(deg(-40f) * sw, w * sw);
            f.rightLeg().overrideBend(s.knee() * deg(30f), w);
            f.leftLeg().overrideBend(s.knee() * deg(25f), w);
            f.body().py -= px(1.5f) * w;
        }
    }
}
