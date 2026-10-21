package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.pose.PartPose;
import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;
import dev.mcreload.anim.client.math.AngleMath;
import net.minecraft.util.Mth;

/**
 * Front crawl (replaces vanilla's breaststroke) and treading water.
 *
 * <p>In vanilla's swimming pose the body lies face down, so an arm angle of 0 points to the hips,
 * -PI/2 under the chest and -PI past the head. A crawl stroke is a continuous windmill: reach past
 * the head, pull under the chest to the hip, recover over the back with a bent elbow. Arms are half
 * a turn apart, the body rolls toward the recovering arm, the legs flutter-kick.</p>
 */
final class WaterModule implements AnimModule {
    private static final float DIVE_SECONDS = 0.85f;

    @Override
    public boolean enabled() {
        return Cfg.water;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float gate = m.gate.v;

        float swim = m.swimW.v * gate;
        if (swim > 0.001f) {
            float p = m.swimPhase;
            stroke(f.rightArm(), s, p, 1f, swim);
            stroke(f.leftArm(), s, p + Mth.PI, -1f, swim);
            // Roll toward the arm that is recovering over the water; the head stays steady, and the
            // roll fades out quickly as the swimmer comes up so nothing shakes at the surface.
            float roll = 0.18f * Mth.sin(p) * swim * swim;
            f.body().ry += roll;
            f.head().ry -= roll * 0.8f;

            float kick = Mth.sin(p * 2f);
            kickLeg(f.rightLeg(), s, kick, swim);
            kickLeg(f.leftLeg(), s, -kick, swim);
        }

        float water = m.waterW.v * (1f - m.swimW.v) * gate;
        if (water > 0.001f) surface(m, f, s, water);

        diveAir(m, f, s, gate);
        dive(m, f, s, gate);
        chill(m, f, s, gate);
    }

    /**
     * At the surface without sprinting: treading water like a real swimmer. The body stays
     * vertical with the shoulders above the water; the arms are held out to the sides, forearms
     * forward, palms flat, sculling in and out in quick figure-eights; the legs do the eggbeater
     * kick: knees bent and slightly apart, each foot drawing circles, the two legs half a turn
     * apart. Swimming along, the body only leans in slightly and the arms sweep further.
     */
    private static void surface(MotionState m, PoseFrame f, Signs s, float w) {
        float mv = m.paddleW.v;
        float t = m.time + m.seed;
        float p = mv > 0.05f ? m.paddlePhase : t * 3.0f;      // leg circles
        float sc = t * 4.2f;                                    // sculling

        // Float higher: lift the model so the shoulders clear the surface, bobbing with each kick.
        f.body().py += AngleMath.clamp(m.surfaceDepth + 0.15f, 0f, 0.45f) * w;
        f.body().py += 0.02f * Mth.sin(p * 2f) * w;
        f.body().rx -= 0.12f * mv * w;
        Rigging.leanTorso(f, s, 0.06f * mv * w);

        // Eggbeater legs: thigh forward a little and out, the foot drawing a circle below the knee.
        eggbeater(f.rightLeg(), s, p, 1f, w);
        eggbeater(f.leftLeg(), s, p + Mth.PI, -1f, w);

        // Sculling arms: upper arms out to the sides and a little forward, forearms forward,
        // the hands sweeping out and in just under the surface.
        float aw = w * m.armsFree.v;
        float sr = Mth.sin(sc), cr = Mth.cos(sc);
        float reach = 0.25f * mv;
        f.rightArm().override(-0.55f - reach - 0.06f * cr, 0.35f * sr, 0.62f + 0.10f * cr, aw);
        f.leftArm().override(-0.55f - reach - 0.06f * cr, -0.35f * sr, -0.62f - 0.10f * cr, aw);
        f.rightArm().overrideBend(s.elbow() * (0.85f + 0.10f * sr), aw);
        f.leftArm().overrideBend(s.elbow() * (0.85f + 0.10f * sr), aw);
        Rigging.twistUpper(f, 0.04f * Mth.sin(p) * w);
    }

    /** @param out +1 right leg, -1 left leg. */
    private static void eggbeater(PartPose leg, Signs s, float phase, float out, float w) {
        float sn = Mth.sin(phase), cs = Mth.cos(phase);
        leg.override(-0.30f + 0.18f * sn, 0f, out * (0.16f + 0.10f * cs), w);
        leg.overrideBend(s.knee() * (0.75f + 0.35f * cs), w);
    }

    /** Falling toward water: arms go overhead, hands together, and the body tips head-first. */
    private static void diveAir(MotionState m, PoseFrame f, Signs s, float gate) {
        float w = m.diveAirW.v * gate * (1f - m.waterW.v);
        if (w <= 0.001f) return;
        streamline(f, s, w, w * m.armsFree.v);
        // Tipping over head-first (body.rx -PI/2 is lying flat, -PI is upside down): the steeper
        // and longer the fall, the closer to straight down.
        float steep = AngleMath.clamp(-m.vertical / 0.7f, 0f, 1f);
        f.body().rx -= (1.15f + 1.50f * steep) * w;
        f.head().rx += 0.25f * w;
    }

    /** Entering the water head-first, then curving round under the surface and levelling out. */
    private static void dive(MotionState m, PoseFrame f, Signs s, float gate) {
        float t = m.since(m.diveStart) / DIVE_SECONDS;
        if (t < 0f || t >= 1f) return;
        float e = Math.min(1f, t / 0.05f) * (1f - AngleMath.ease((t - 0.55f) / 0.45f)) * gate * (1f - m.swimW.v);
        if (e <= 0.001f) return;
        streamline(f, s, e, e * m.armsFree.v);
        // Head-first, steeply down at first (PI = straight down), then the dolphin curve back up
        // to level under the surface.
        float pitch = Mth.lerp(AngleMath.ease((t - 0.20f) / 0.65f), 2.65f, 1.50f);
        f.body().rx -= pitch * e;
        float wave = Mth.sin(t * Mth.TWO_PI * 1.8f);
        f.rightLeg().overrideX(0.25f * wave, e);
        f.leftLeg().overrideX(0.25f * wave, e);
        Rigging.leanTorso(f, s, -0.15f * wave * e);
        f.head().rx += 0.30f * e;
    }

    private static void streamline(PoseFrame f, Signs s, float w, float aw) {
        f.rightArm().override(-3.05f, 0f, -0.12f, aw);
        f.leftArm().override(-3.05f, 0f, 0.12f, aw);
        f.rightArm().overrideBend(0f, aw);
        f.leftArm().overrideBend(0f, aw);
        f.rightLeg().override(0.05f, 0f, 0.02f, w);
        f.leftLeg().override(0.05f, 0f, -0.02f, w);
        f.rightLeg().overrideBend(s.knee() * 0.10f, w);
        f.leftLeg().overrideBend(s.knee() * 0.10f, w);
        f.torso().overrideBend(0f, w);
    }

    /**
     * Jumping or falling backwards (holding S): turned over onto the back, hands clasped behind
     * the head, one knee up, completely relaxed. On land this ends in a back roll
     * (ReactionModule); in water, a splash pose for a moment after hitting the surface.
     */
    private static void chill(MotionState m, PoseFrame f, Signs s, float gate) {
        float air = m.chillW.v * gate * (1f - m.waterW.v);
        float t = m.since(m.chillSplashStart) / 0.8f;
        float splash = t >= 0f && t < 1f ? (1f - AngleMath.ease((t - 0.3f) / 0.7f)) * gate : 0f;
        float w = Math.max(air, splash);
        if (w <= 0.001f) return;
        float aw = w * m.armsFree.v;
        // On the back.
        f.body().rx += 1.35f * w;
        f.head().rx -= 0.20f * w;
        // Hands behind the head.
        f.rightArm().override(-2.90f, -0.30f, 0.45f, aw);
        f.leftArm().override(-2.90f, 0.30f, -0.45f, aw);
        f.rightArm().overrideBend(s.elbow() * 1.6f, aw);
        f.leftArm().overrideBend(s.elbow() * 1.6f, aw);
        // One leg straight, the other knee up.
        f.rightLeg().override(-0.75f, 0f, 0.08f, w);
        f.rightLeg().overrideBend(s.knee() * 1.25f, w);
        f.leftLeg().override(-0.10f, 0f, -0.05f, w);
        f.leftLeg().overrideBend(s.knee() * 0.10f, w);
        f.torso().overrideBend(0f, w);
    }

    /** @param out +1 right arm, -1 left arm (sign of "away from the body" on the z axis). */
    private static void stroke(PartPose arm, Signs s, float phase, float out, float w) {
        // Increasing angle: -PI (reach) -> 0 (hip, end of pull) -> +PI (over the back) -> wraps to -PI.
        float a = phase % Mth.TWO_PI;
        if (a > Mth.PI) a -= Mth.TWO_PI;
        if (a < -Mth.PI) a += Mth.TWO_PI;
        float recovering = Math.max(0f, Mth.sin(a));    // arm above the back
        float pulling = Math.max(0f, -Mth.sin(a));      // arm under the chest
        arm.override(a, 0f, out * (0.12f + 0.35f * recovering), w);
        arm.overrideBend(s.elbow() * (0.35f * pulling + 1.1f * recovering), w);
    }

    private static void kickLeg(PartPose leg, Signs s, float kick, float w) {
        leg.override(0.30f * kick, 0f, 0f, w);
        leg.overrideBend(s.knee() * (0.15f + 0.35f * Math.max(0f, kick)), w);
    }
}
