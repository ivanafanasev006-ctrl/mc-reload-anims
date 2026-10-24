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
 * turns into the cut, the blade turns in the hand.
 * <ul>
 *   <li>Drawing a sword (switching to it): a twirl of the blade and a step into the guard.</li>
 *   <li>Sword guard: for a couple of seconds after drawing it or after any attack (a click at the
 *       air counts, and so do Better Combat's attacks), standing or walking, like the fists'
 *       guard: side-on and low, arms out wide, blade tipped forward (a shield is held up in
 *       front instead, and can be raised from the guard). Swords only; not axes or tridents.</li>
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
        curMotion = m;
        curFrame = f;
        float hd = m.mainRight ? 1f : -1f;
        PartPose main = m.mainRight ? f.rightArm() : f.leftArm();
        PartPose off = m.mainRight ? f.leftArm() : f.rightArm();
        boolean shield = holdsShield(rig.player);
        float foreignMain = rig.foreign[m.mainRight ? PoseFrame.RIGHT_ARM : PoseFrame.LEFT_ARM];
        float foreignOff = rig.foreign[m.mainRight ? PoseFrame.LEFT_ARM : PoseFrame.RIGHT_ARM];
        // Better Combat playing its attack on the sword arm: that is an attack too (keeps the guard).
        net.minecraft.world.item.ItemStack held = rig.player.getMainHandItem();
        if (foreignMain > 0.5f && dev.mcreload.anim.client.rig.Weapons.isSword(held)
                && dev.mcreload.anim.compat.BetterCombatCompat.handles(held)) {
            m.attackTime = m.time;
        }
        stance(m, f, s, gate, hd, main, off, shield, foreignMain, foreignOff);
        // Switched away from the sword halfway through: the draw stops with it.
        if (dev.mcreload.anim.client.rig.Weapons.isOneHandedSword(held)) draw(m, f, s, gate, hd, main, off, shield, foreignMain, foreignOff);
        strike(rig, m, f, s, gate, hd, main, off, shield);
    }

    /** A shield (anything that can block like one) in the off hand. */
    private static boolean holdsShield(net.minecraft.world.entity.player.Player p) {
        return p.getOffhandItem().canPerformAction(net.minecraftforge.common.ToolActions.SHIELD_BLOCK);
    }

    private static float smoother(float x) {
        x = AngleMath.clamp(x, 0f, 1f);
        return x * x * x * (x * (x * 6f - 15f) + 10f);
    }

    private MotionState curMotion;
    private PoseFrame curFrame;
    /** 0 = guard stance, 1 = strike: each keeps its own Euler history. */
    private int curSlot;

    /**
     * Arm rotation from Mo'Bends-style "out to the side by z, then swung round by y" (degrees).
     * Every rotation has two Euler triples; we keep the one closest to what this arm got last
     * frame, so a swing passing over the shoulder or behind the back never flips the arm round
     * the long way (which looked like the wrist twisting the wrong way).
     */
    private void arm(PartPose arm, float zDeg, float yDeg, float xDeg, float w) {
        Quaternionf q = new Quaternionf().rotationY(deg(yDeg)).mul(new Quaternionf().rotationZ(deg(zDeg)))
                .mul(new Quaternionf().rotationX(deg(xDeg)));
        AngleMath.toModelEuler(q, e);
        if (curMotion != null && curFrame != null) {
            int idx = (arm == curFrame.rightArm() ? 0 : 1) + 2 * curSlot;
            float[] ref = curMotion.armEulerRef[idx];
            if (curMotion.time - curMotion.armEulerTime[idx] > 0.25f) {
                ref[0] = 0f; ref[1] = 0f; ref[2] = 0f;
            }
            AngleMath.nearestEuler(e, ref);
            System.arraycopy(e, 0, ref, 0, 3);
            curMotion.armEulerTime[idx] = curMotion.time;
        }
        arm.override(e[0], e[1], e[2], w);
    }

    // ------------------------------------------------------------------ sword guard and drawing

    /** The shield held across in front of the chest (fitted on the model and in CI). */
    private static final float G_SHIELD_X = -0.70f, G_SHIELD_Y = -0.45f, G_SHIELD_Z = 0.05f, G_SHIELD_ELBOW = 0.90f;

    /**
     * The guard, after Mo'Bends: side-on and low, leaning in, the sword arm out to the side with
     * the blade tipped forward, the free hand out the other way (or the shield up in front).
     * Standing, the feet wide apart and knees bent; walking, the arms stay out and the body turns
     * less, so the feet still go forward.
     */
    private void stance(MotionState m, PoseFrame f, Signs s, float gate, float hd, PartPose main, PartPose off,
                        boolean shield, float foreignMain, float foreignOff) {
        curSlot = 0;
        // The sword-fall pose in the air (AirModule#bladeFall) has the arms then, and drawing the
        // sword has the whole body (it ends in this guard and hands over as it fades out).
        float w = m.readyW.v * gate * (1f - m.airW.v * m.hardDropW.v * m.bladeReadyW.v) * (1f - drawWeight(m));
        if (w <= 0.001f) return;
        // Blocking: the shield arm is the game's (raised), the sword arm stays in the guard.
        float block = shield ? m.blockW.v : 0f;
        float mw = w * Math.max(m.armsFree.v, block) * (1f - foreignMain);
        float ow = w * m.armsFree.v * (1f - block) * (1f - foreignOff);
        float b0 = Mth.sin(m.time * 4f), b1 = Mth.cos(m.time * 3.5f);
        float moving = AngleMath.clamp(m.moveF(), 0f, 1f);
        guardPose(m, f, s, hd, main, off, shield, w, mw, ow, 1f, b0, b1, moving);
        // Standing (and not behind a raised shield, which has its own stance): feet wide apart.
        float still = (1f - moving) * m.groundW.v * (1f - block);
        guardLegs(f, s, w * still);
    }

    /**
     * The guard at {@code k} (0 = arms down by the sides, 1 = the guard): body turned side-on,
     * leaning in, sword arm out to the side with the blade tipped forward, the other arm out the
     * other way or the shield across in front.
     */
    private void guardPose(MotionState m, PoseFrame f, Signs s, float hd, PartPose main, PartPose off, boolean shield,
                           float w, float mw, float ow, float k, float b0, float b1, float moving) {
        float turn = deg(shield ? 10f : 30f) * (1f - 0.6f * moving) * k;
        f.body().ry += hd * turn * w;
        f.head().ry += hd * turn * w;
        Rigging.leanTorso(f, s, deg(20f + 2f * b0) * k * w);
        if (mw > 0.001f) {
            arm(main, hd * (60f + 5f * b0) * k, hd * 5f * b1 * k, 0f, mw);
            main.overrideBend(s.elbow() * deg(20f) * k, mw);
            m.itemTiltTarget = Math.max(m.itemTiltTarget, deg(65f) * k * mw);
        }
        if (ow > 0.001f) {
            if (shield) {
                off.override(G_SHIELD_X * k, -hd * G_SHIELD_Y * k, -hd * G_SHIELD_Z * k, ow);
                off.overrideBend(s.elbow() * G_SHIELD_ELBOW * k, ow);
            } else {
                arm(off, -hd * (60f - 5f * b1) * k, 0f, 0f, ow);
                off.overrideBend(s.elbow() * deg(60f) * k, ow);
            }
        }
    }

    private static void guardLegs(PoseFrame f, Signs s, float w) {
        if (w <= 0.001f) return;
        f.rightLeg().override(deg(-30f), deg(25f), deg(10f), w);
        f.leftLeg().override(deg(-30f), deg(-25f), deg(-10f), w);
        f.rightLeg().overrideBend(s.knee() * deg(30f), w);
        f.leftLeg().overrideBend(s.knee() * deg(30f), w);
        f.body().py -= px(2f) * w;
    }

    /** How much of the body drawing the sword has right now (1 while it plays, fading at its end). */
    private static float drawWeight(MotionState m) {
        float T = m.since(m.drawStart);
        if (T < 0f || T >= MotionState.DRAW_SECONDS) return 0f;
        return 1f - AngleMath.ease((T / MotionState.DRAW_SECONDS - 0.88f) / 0.12f);
    }

    /**
     * Drawing the sword (0.8 s). The body goes into the guard as before: the sword arm rises out
     * and up to the side and comes down into the guard as the feet spread into the stance with a
     * little dip, the other arm (or the shield) coming up with it; the arm stays in one plane, so
     * it never flips round. The sword, in step with it: it comes into the hand turned round, point
     * back (as if from a scabbard behind); makes a half turn to the ordinary grip as the arm rises,
     * the point going outward along the arm (away from the body, never through it); then tips
     * forward to the guard's angle in a tenth of a second. The turn is set exactly in the frame it
     * is drawn (no smoothing, no lag, so the sword never jumps in the hand). The guard itself waits
     * until the draw is over (nothing is added twice).
     */
    private void draw(MotionState m, PoseFrame f, Signs s, float gate, float hd, PartPose main, PartPose off,
                      boolean shield, float foreignMain, float foreignOff) {
        curSlot = 0;
        float T = m.since(m.drawStart);
        if (T < 0f || T >= MotionState.DRAW_SECONDS) return;
        float t = T / MotionState.DRAW_SECONDS;
        float w = gate * drawWeight(m);
        if (w <= 0.001f) return;
        float mw = w * m.armsFree.v * (1f - foreignMain);
        float ow = w * m.armsFree.v * (1f - foreignOff);
        float up = smoother(t / 0.40f);                 // out and up to the side
        float down = smoother((t - 0.45f) / 0.45f);     // into the guard
        float moving = AngleMath.clamp(m.moveF(), 0f, 1f);
        // The rest of the guard comes in with the settle; the sword arm has its own path.
        guardPose(m, f, s, hd, main, off, shield, w, 0f, ow * smoother((t - 0.20f) / 0.55f), down, 0f, 1f, moving);
        if (mw > 0.001f) {
            float raise = Mth.lerp(down, Mth.lerp(up, 0.10f, 2.00f), deg(60f));
            main.override(-0.30f * up * (1f - down), 0f, hd * raise, mw);
            main.overrideBend(s.elbow() * Mth.lerp(down, Mth.lerp(up, 0.15f, 0.60f), deg(20f)), mw);
            // Point back -> (half turn, out along the arm) -> ordinary grip -> tipped forward.
            float half = smoother((t - 0.08f) / 0.50f);
            float tip = smoother((t - 0.58f) / 0.14f);
            float tilt = Mth.PI * (1f - half) + deg(65f) * tip;
            if (t < 0.72f && mw > 0.5f) m.tiltNow(tilt);
            else m.itemTiltTarget = Math.max(m.itemTiltTarget, tilt * mw);
        }
        // Standing: the feet spread into the stance with a little dip.
        float still = (1f - moving) * m.groundW.v;
        guardLegs(f, s, w * still * down);
        f.body().py -= 0.06f * Mth.sin(Mth.PI * AngleMath.clamp((t - 0.45f) / 0.50f, 0f, 1f)) * w * still;
    }

    // ------------------------------------------------------------------ strikes

    private void strike(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float gate, float hd, PartPose main, PartPose off,
                        boolean shield) {
        curSlot = 1;
        int kind = m.slashKind;
        if (!MotionState.isWeapon(kind)) return;
        float t = m.slashProgress();
        if (t >= 1.3f) return;
        float in = m.slashChained ? 1f : AngleMath.ease(t / 0.10f);
        float w = in * (1f - AngleMath.ease((t - 0.8f) / 0.45f)) * gate;
        int armIndex = m.mainRight ? PoseFrame.RIGHT_ARM : PoseFrame.LEFT_ARM;
        w *= 1f - rig.foreign[armIndex];      // Better Combat animating the arm wins
        if (w <= 0.001f) return;
        float aw = w * m.armsFree.v;

        // A short wind-up, then the cut accelerates through and decelerates into the
        // follow-through (smootherstep: no sudden starts or stops), then holds and recovers.
        float a = AngleMath.clamp(t / 0.7f, 0f, 1f);
        float sw = smoother((a - 0.06f) / 0.42f);
        boolean axe = MotionState.isAxe(kind);
        if (axe) sw = smoother((a - 0.14f) / 0.46f);
        boolean still = m.moveF() < 0.3f && m.groundW.v > 0.5f;

        float twist, tilt, lean;
        switch (kind) {
            case MotionState.SLASH_H, MotionState.AXE_SWEEP -> {
                // Inward sweep: wound up behind the shoulder, swept flat across to the other side.
                twist = -70f * sw;
                arm(main, hd * 90f, hd * (60f - 180f * sw + twist), 0f, aw);
                main.overrideBend(s.elbow() * deg(10f), aw);
                // The blade lines up with the arm as it sweeps across, so it never cuts back
                // through the chest at the end of the swing.
                tilt = 40f + 45f * sw;
                lean = 20f - 20f * sw;
            }
            case MotionState.SLASH_BACK -> {
                // Rising backhand: from low across the body at the opposite hip, up and out
                // diagonally to finish high on the sword side, the chest turning with it. One
                // clean diagonal (the old version spun the hanging arm about its own axis).
                twist = -35f + 65f * sw;
                float z = 45f + 95f * sw;
                float y = -150f + 120f * sw;
                arm(main, hd * z, hd * (y + twist), 0f, aw);
                main.overrideBend(s.elbow() * deg(Mth.lerp(sw, 45f, 10f)), aw);
                tilt = Mth.lerp(sw, 60f, 85f);
                lean = Mth.lerp(sw, 12f, -4f);
            }
            case MotionState.SLASH_V, MotionState.AXE_OVER -> {
                // Overhead chop: blade raised high behind the head, brought straight down with
                // the whole body.
                twist = 20f - 30f * sw;
                // The wind-up is animated too: the blade rises from in front up behind the head
                // (instead of popping straight up there), then comes down.
                float rise = smoother(a / (axe ? 0.16f : 0.08f));
                float x = Mth.lerp(sw, Mth.lerp(rise, -60f, -170f), -45f);
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
        if (shield) {
            // A shield stays up in front through the cut.
            off.override(G_SHIELD_X, -hd * G_SHIELD_Y, -hd * G_SHIELD_Z, aw);
            off.overrideBend(s.elbow() * G_SHIELD_ELBOW, aw);
        } else {
            // Free arm out for balance, elbow bent.
            arm(off, -hd * 70f, hd * 10f, 0f, aw);
            off.overrideBend(s.elbow() * deg(60f), aw);
        }
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
