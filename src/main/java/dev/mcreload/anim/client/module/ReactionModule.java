package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.math.AngleMath;
import dev.mcreload.anim.client.pose.PartPose;
import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.client.rig.Weapons;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * One-shot reactions to what happens to the player.
 * <ul>
 *   <li>Hit from the front: knocked back, arms flung forward. Hit from behind: shoved forward,
 *       arms thrown back.</li>
 *   <li>High fall: forward roll. High fall holding only a blade: plants it in the ground and drops
 *       into a crouch.</li>
 *   <li>Explosion: flinches and shields the face with both arms, as if flash-banged; holding a
 *       blade instead drives it into the ground and braces against the blast.</li>
 *   <li>Totem of Undying: stares at their own hands, looking from one to the other.</li>
 * </ul>
 */
final class ReactionModule implements AnimModule {
    private static final float ROLL_SECONDS = 0.38f, BACKROLL_SECONDS = 0.38f, SWORD_LAND_SECONDS = 1.2f,
            BLAST_SECONDS = 1.4f, TOTEM_SECONDS = 2.6f;

    @Override
    public boolean enabled() {
        return Cfg.reactions || Cfg.landing;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float gate = m.gate.v;
        float arms = m.armsFree.v;
        float side = rig.player.getMainArm() == HumanoidArm.RIGHT ? 1f : -1f;
        PartPose main = side > 0 ? f.rightArm() : f.leftArm();
        PartPose off = side > 0 ? f.leftArm() : f.rightArm();

        if (Cfg.reactions && gate > 0.001f) {
            hits(m, f, s, gate, arms);
            blast(rig, m, f, s, gate, arms, main, off, side);
            totem(m, f, s, gate, arms);
        }
        if (Cfg.landing) {
            // Rolling out of an elytra landing starts while the glide gate is still opening.
            boolean free = !rig.player.isFallFlying() && !rig.player.isPassenger() && !rig.player.isSleeping();
            if (m.landKind == MotionState.LAND_ROLL) roll(m, f, s, free ? 1f : gate, free ? 1f : arms);
            if (m.landKind == MotionState.LAND_BACKROLL && gate > 0.001f) backRoll(m, f, s, gate, arms);
            if (m.landKind == MotionState.LAND_SWORD && gate > 0.001f) {
                swordLanding(m, f, s, gate, main, off, side, rig.player.getOffhandItem().isEmpty());
            }
        }
    }

    private static void hits(MotionState m, PoseFrame f, Signs s, float gate, float arms) {
        float front = m.hitFront.x * gate;
        if (Math.abs(front) > 0.001f) {
            Rigging.leanTorso(f, s, -0.32f * front);
            f.body().rx += 0.18f * front;
            f.head().rx -= 0.22f * front;
            float aw = AngleMath.clamp(front, 0f, 1f) * arms;
            f.rightArm().override(-1.0f, 0f, 0.25f, aw);
            f.leftArm().override(-1.0f, 0f, -0.25f, aw);
        }
        float back = m.hitBack.x * gate;
        if (Math.abs(back) > 0.001f) {
            Rigging.leanTorso(f, s, 0.35f * back);
            f.body().rx -= 0.22f * back;
            f.head().rx += 0.15f * back;
            float aw = AngleMath.clamp(back, 0f, 1f) * arms;
            f.rightArm().override(0.65f, 0f, 0.35f, aw);
            f.leftArm().override(0.65f, 0f, -0.35f, aw);
        }
        float h = m.hurt.x * gate;
        if (Math.abs(h) > 0.001f) Rigging.armsOut(f, 0.12f * h);
    }

    private static void roll(MotionState m, PoseFrame f, Signs s, float gate, float arms) {
        float t = m.since(m.landStart) / ROLL_SECONDS;
        if (t >= 1f || t < 0f) return;
        float spin = AngleMath.ease(t);
        float tuck = Mth.sin(Mth.PI * Math.min(1f, t * 1.15f)) * gate;
        // Over the head and back onto the feet.
        f.body().rx -= Mth.TWO_PI * spin * gate;
        // Lift the pivot a little so the tucked head clears the ground upside down.
        f.body().py += 0.18f * Mth.sin(Mth.PI * t) * gate;
        // Curled into a ball.
        f.rightLeg().override(-2.0f, 0f, 0.05f, tuck);
        f.leftLeg().override(-2.0f, 0f, -0.05f, tuck);
        f.rightLeg().overrideBend(s.knee() * 2.3f, tuck);
        f.leftLeg().overrideBend(s.knee() * 2.3f, tuck);
        f.rightArm().override(-1.0f, -0.30f, 0f, tuck * arms);
        f.leftArm().override(-1.0f, 0.30f, 0f, tuck * arms);
        f.rightArm().overrideBend(s.elbow() * 1.6f, tuck * arms);
        f.leftArm().overrideBend(s.elbow() * 1.6f, tuck * arms);
        Rigging.leanTorso(f, s, 0.6f * tuck);
        f.head().rx += 0.5f * tuck;
    }

    /**
     * Landing from a backwards fall (lying back, hands behind the head): down onto the back and
     * rolled straight on over the shoulders, back up onto the feet.
     */
    private static void backRoll(MotionState m, PoseFrame f, Signs s, float gate, float arms) {
        float t = m.since(m.landStart) / BACKROLL_SECONDS;
        if (t >= 1f || t < 0f) return;
        float spin = AngleMath.ease(t);
        float tuck = Mth.sin(Mth.PI * Math.min(1f, t * 1.1f)) * gate;
        // Keep turning backwards from however far we were already lying back, to a full turn.
        f.body().rx += Mth.lerp(spin, m.backRollFrom, Mth.TWO_PI) * gate;
        // Down on the back at first, the hips lifting as we go over the shoulders.
        f.body().py += (-0.42f * (1f - AngleMath.ease(t / 0.75f)) + 0.12f * Mth.sin(Mth.PI * t)) * gate;
        // Balled up, hands by the ears pushing off the ground.
        f.rightLeg().override(-2.0f, 0f, 0.08f, tuck);
        f.leftLeg().override(-2.0f, 0f, -0.08f, tuck);
        f.rightLeg().overrideBend(s.knee() * 2.2f, tuck);
        f.leftLeg().overrideBend(s.knee() * 2.2f, tuck);
        f.rightArm().override(-2.75f, 0f, 0.35f, tuck * arms);
        f.leftArm().override(-2.75f, 0f, -0.35f, tuck * arms);
        f.rightArm().overrideBend(s.elbow() * 1.9f, tuck * arms);
        f.leftArm().overrideBend(s.elbow() * 1.9f, tuck * arms);
        Rigging.leanTorso(f, s, 0.55f * tuck);
        f.head().rx += 0.55f * tuck;
    }

    /**
     * A hero landing with a sword. In the air both hands already hold it high, blade up (see
     * AirModule#bladeFall). Touching down, the legs give and the body sinks into a kneel over
     * ~0.15 s while both hands drive the blade straight down, point-first, into the ground in
     * front of the front knee; the shock comes up through the shoulders with a damped bounce,
     * the head comes up to look ahead, breathing; then the sword is pulled out and the fighter
     * stands up. Every part blends in and out smoothly - nothing pops.
     */
    private static void swordLanding(MotionState m, PoseFrame f, Signs s, float gate, PartPose main, PartPose off,
                                     float side, boolean two) {
        float T = m.since(m.landStart);
        float t = T / SWORD_LAND_SECONDS;
        if (t >= 1f || t < 0f) return;
        // Blend in over the first 0.12 s, out over the last 40 %.
        float e = AngleMath.ease(T / 0.12f) * (1f - AngleMath.ease((t - 0.60f) / 0.40f)) * gate;
        if (e <= 0.001f) return;
        float stab = AngleMath.ease(T / 0.16f);
        // Sinking into the landing and a damped rebound.
        float slam = T < 0.14f ? AngleMath.ease(T / 0.14f)
                : (float) Math.exp(-(T - 0.14f) * 6f) * Mth.cos((T - 0.14f) * 11f);
        float breathe = Mth.sin(T * 8f) * (float) Math.exp(-T * 2.2f);
        float lean = 0.40f + 0.30f * Math.max(0f, slam) + 0.04f * breathe;
        float headUp = AngleMath.ease((T - 0.22f) / 0.30f);
        // Getting up: the sword comes out as the body rises.
        float rise = AngleMath.ease((t - 0.58f) / 0.32f);
        plantBlade(m, f, s, e, main, off, side, stab * (1f - rise), lean, 0.05f * slam, headUp, two);
    }

    private static void blast(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float gate, float arms,
                              PartPose main, PartPose off, float side) {
        float T = m.since(m.explodeStart);
        float t = T / BLAST_SECONDS;
        if (t >= 1f || t < 0f) return;
        boolean blade = Weapons.isMeleeWeapon(rig.player.getMainHandItem());
        if (blade) {
            bladeBrace(m, f, s, gate, main, off, side, T, rig.player.getOffhandItem().isEmpty());
            return;
        }
        float e = AngleMath.ease(t / 0.04f) * (1f - AngleMath.ease((t - 0.58f) / 0.42f)) * m.explodeStrength * gate;
        if (e <= 0.001f) return;
        // Flash-banged: curled up behind both forearms crossed over the face, back rounded,
        // knees bent, staggered back a step by the blast.
        float flinch = Mth.sin(Mth.PI * AngleMath.clamp(t / 0.25f, 0f, 1f));
        f.rightArm().override(-1.60f, -0.70f, 0f, e * arms);
        f.leftArm().override(-1.75f, 0.70f, 0f, e * arms);
        f.rightArm().overrideBend(s.elbow() * 1.55f, e * arms);
        f.leftArm().overrideBend(s.elbow() * 1.45f, e * arms);
        f.head().rx += 0.40f * e;
        Rigging.leanTorso(f, s, (0.50f + 0.15f * flinch) * e);
        Rigging.crouch(f, s, 0.42f * e);
        PartPose rearLeg = side > 0 ? f.rightLeg() : f.leftLeg();
        rearLeg.rx += 0.35f * flinch * e;
        f.body().pz += (0.10f + 0.08f * flinch) * e;
        f.body().rx += 0.10f * flinch * e;
    }

    /**
     * Blown back holding a blade: rocked back off balance by the blast (chest and head thrown
     * back, the sword coming up in both hands), then down onto one knee driving the blade
     * straight down into the ground in front with both hands, braced behind the hilt with the
     * head ducked while the blast shoves the body back a little; then up again, pulling the
     * sword out. The same clean vertical plant as the hero landing.
     */
    private static void bladeBrace(MotionState m, PoseFrame f, Signs s, float g, PartPose main, PartPose off,
                                   float side, float T, boolean two) {
        float t = T / BLAST_SECONDS;
        // Rocked back: in over 0.08 s, gone by ~0.3 s.
        float rock = AngleMath.ease(T / 0.08f) * (1f - AngleMath.ease((T - 0.12f) / 0.20f)) * g;
        // Braced on one knee behind the planted sword: in from 0.12 s, out over the last 40 %.
        float e = AngleMath.ease((T - 0.10f) / 0.18f) * (1f - AngleMath.ease((t - 0.60f) / 0.40f)) * g;
        if (rock > 0.001f) {
            Rigging.leanTorso(f, s, -0.40f * rock);
            f.head().rx -= 0.35f * rock;
            f.body().rx += 0.10f * rock;
            float aw = rock * m.armsFree.v;
            main.override(-2.30f, -side * 0.28f, side * 0.10f, aw);
            if (two) {
                off.override(-2.20f, side * 0.62f, -side * 0.10f, aw);
                off.overrideBend(s.elbow() * 0.35f, aw);
            } else {
                off.override(-1.40f, 0f, -side * 0.90f, aw);
                off.overrideBend(s.elbow() * 0.40f, aw);
            }
            main.overrideBend(s.elbow() * 0.30f, aw);
            m.itemTiltTarget = Math.max(m.itemTiltTarget, 0.95f * rock);
        }
        if (e > 0.001f) {
            float stab = AngleMath.ease((T - 0.14f) / 0.14f);
            float rise = AngleMath.ease((t - 0.58f) / 0.32f);
            float shove = Mth.sin(Mth.PI * AngleMath.clamp((T - 0.15f) / 0.5f, 0f, 1f));
            plantBlade(m, f, s, e, main, off, side, stab * (1f - rise), 0.38f + 0.10f * shove, 0.03f * shove, 0.3f, two);
        }
        // Shoved back by the blast, sliding to a stop.
        f.body().pz += 0.28f * AngleMath.ease(T / 0.5f) * Math.max(rock, e);
    }

    /**
     * Down on one knee with the sword driven point-first into the ground in front: both hands on
     * the hilt, raised high first and then thrust down.
     *
     * @param stab   0 = raised high (blade up), 1 = driven into the ground
     * @param lean   forward lean of the chest (radians); the arms compensate so the blade stays upright
     * @param drop   extra drop of the hips below the kneel (blocks)
     * @param headUp 0 = looking down at the blade, 1 = looking ahead
     */
    private static void plantBlade(MotionState m, PoseFrame f, Signs s, float e, PartPose main, PartPose off, float side,
                                   float stab, float lean, float drop, float headUp, boolean two) {
        // Kneel: free-side foot planted forward, sword-side knee on the ground.
        PartPose lead = side > 0 ? f.leftLeg() : f.rightLeg();
        PartPose rear = side > 0 ? f.rightLeg() : f.leftLeg();
        lead.override(-1.50f, 0f, -side * 0.10f, e);
        lead.overrideBend(s.knee() * 1.50f, e);
        rear.override(0.05f, 0f, side * 0.10f, e);
        rear.overrideBend(s.knee() * 1.60f, e);
        // Hips come down to the height of the front shin (about 6 pixels), and further on impact.
        f.body().py -= (6f / 16f * 0.9375f + drop) * e;
        Rigging.leanTorso(f, s, lean * e);
        f.head().rx += Mth.lerp(headUp, 0.45f, -0.25f) * e;
        // Both hands on the hilt in front of the body: raised high with the blade pointing up,
        // then driven down until the fists are low in front of the front knee (the arms ride on
        // the leaning torso, so the lean is taken off their angle), while the blade turns over in
        // the fists (item tilt: a rotation about the fist's own axis, so it can never flip through
        // the body) to stand straight up and down, its point driven well into the ground.
        float x = Mth.lerp(stab, -2.60f, -0.70f - lean);
        main.override(x, -side * 0.28f, side * 0.05f, e);
        main.overrideBend(s.elbow() * Mth.lerp(stab, 0.20f, 0.10f), e);
        if (two) {
            off.override(x + 0.10f, side * 0.62f, -side * 0.05f, e);
            off.overrideBend(s.elbow() * Mth.lerp(stab, 0.20f, 0.30f), e);
        } else {
            // Something in the other hand: it rests on the front knee instead.
            off.override(-0.75f - lean, side * 0.10f, -side * 0.15f, e);
            off.overrideBend(s.elbow() * 0.55f, e);
        }
        m.itemTiltTarget = Math.max(m.itemTiltTarget, Mth.lerp(stab, 0.95f, Mth.PI - 1.10f) * e);
        m.armsHeld = Math.max(m.armsHeld, e);
        f.body().py += 0.05f * (1f - stab) * e;
    }

    private static void totem(MotionState m, PoseFrame f, Signs s, float gate, float arms) {
        float t = m.since(m.totemStart) / TOTEM_SECONDS;
        if (t >= 1f || t < 0f) return;
        float e = AngleMath.ease(t / 0.12f) * (1f - AngleMath.ease((t - 0.75f) / 0.25f)) * gate;
        if (e <= 0.001f) return;
        float look = Mth.sin(t * Mth.TWO_PI * 1.3f);
        float aw = e * arms;
        // Hands held up in front, palms up, turned over and over.
        f.rightArm().override(-1.15f - 0.10f * look, -0.15f, 0.10f + 0.15f * Mth.sin(t * 17f), aw);
        f.leftArm().override(-1.15f + 0.10f * look, 0.15f, -0.10f - 0.15f * Mth.sin(t * 17f + 1f), aw);
        f.rightArm().overrideBend(s.elbow() * 1.20f, aw);
        f.leftArm().overrideBend(s.elbow() * 1.20f, aw);
        // Looking from one hand to the other, bewildered.
        f.head().rx += 0.50f * e;
        f.head().ry += 0.45f * look * e;
        f.body().rz += 0.04f * Mth.sin(t * 9f) * e;
        // Dazed: hunched over, knees soft and wobbling, swaying on the spot.
        Rigging.leanTorso(f, s, (0.30f + 0.08f * Mth.sin(t * 7f)) * e);
        Rigging.crouch(f, s, (0.18f + 0.08f * Mth.sin(t * 11f)) * e);
        f.body().px += 0.04f * Mth.sin(t * 5f) * e;
        f.rightLeg().bend += s.knee() * 0.25f * e;
        f.leftLeg().bend += s.knee() * 0.25f * e;
    }
}
