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
    private static final float ROLL_SECONDS = 0.38f, SWORD_LAND_SECONDS = 0.9f, BLAST_SECONDS = 1.4f, TOTEM_SECONDS = 2.6f;

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
            if (m.landKind == MotionState.LAND_SWORD && gate > 0.001f) swordLanding(m, f, s, gate, main, off, side);
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

    private static void swordLanding(MotionState m, PoseFrame f, Signs s, float gate, PartPose main, PartPose off, float side) {
        float t = m.since(m.landStart) / SWORD_LAND_SECONDS;
        if (t >= 1f || t < 0f) return;
        float e = AngleMath.ease(t / 0.12f) * (1f - AngleMath.ease((t - 0.6f) / 0.4f)) * gate;
        m.itemTiltTarget = 0f;
        m.itemTilt.v *= 1f - e;
        plantBlade(f, s, e, main, off, side, 0.75f, AngleMath.ease(t * SWORD_LAND_SECONDS / 0.16f));
        // The impact: thrown down hard onto the knee, then pushing up.
        f.body().py -= 0.10f * Mth.sin(Mth.PI * AngleMath.clamp(t / 0.35f, 0f, 1f)) * e;
        Rigging.leanTorso(f, s, 0.20f * Mth.sin(Mth.PI * AngleMath.clamp(t / 0.4f, 0f, 1f)) * e);
    }

    private static void blast(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float gate, float arms,
                              PartPose main, PartPose off, float side) {
        float t = m.since(m.explodeStart) / BLAST_SECONDS;
        if (t >= 1f || t < 0f) return;
        float e = AngleMath.ease(t / 0.06f) * (1f - AngleMath.ease((t - 0.55f) / 0.45f)) * m.explodeStrength * gate;
        if (e <= 0.001f) return;
        boolean blade = Weapons.isMeleeWeapon(rig.player.getMainHandItem());
        if (blade) {
            // Drive the blade into the ground and brace, sliding back with the blast.
            m.itemTiltTarget = 0f;
            m.itemTilt.v *= 1f - e;
            plantBlade(f, s, e, main, off, side, 0.60f, AngleMath.ease((t * BLAST_SECONDS - 0.05f) / 0.18f));
            // Pushed back by the blast, the blade ploughing through the ground.
            f.body().pz += 0.30f * AngleMath.ease(t / 0.35f) * e;
        } else {
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
    }

    /**
     * Down on one knee with the sword driven point-first into the ground in front: both hands on
     * the hilt, raised high first and then thrust down, the body leaning over it.
     */
    private static void plantBlade(PoseFrame f, Signs s, float e, PartPose main, PartPose off, float side, float depth, float stab) {
        // Kneel: free-side foot planted forward, sword-side knee on the ground.
        PartPose lead = side > 0 ? f.leftLeg() : f.rightLeg();
        PartPose rear = side > 0 ? f.rightLeg() : f.leftLeg();
        lead.override(-1.50f, 0f, -side * 0.10f, e);
        lead.overrideBend(s.knee() * 1.50f, e);
        rear.override(0.05f, 0f, side * 0.10f, e);
        rear.overrideBend(s.knee() * 1.60f, e);
        // Hips come down to the height of the front shin (about 6 pixels).
        f.body().py -= (6f / 16f * 0.9375f) * e;
        float lean = 0.35f;
        Rigging.leanTorso(f, s, lean * e);
        f.head().rx += 0.20f * e;
        // Both hands on the hilt well in front of the body: raised high, then driven down until
        // the arms are about level in the world (the arms ride on the leaning torso, and the
        // sword sits tilted back in the fist), so the turned-over blade goes into the ground
        // point-first in front of the front knee, on the sword side, never back through the legs.
        float x = Mth.lerp(stab, -2.60f, -Mth.HALF_PI - lean - 0.35f);
        main.override(x, -side * 0.28f, side * 0.05f, e);
        off.override(x + 0.10f, side * 0.62f, -side * 0.05f, e);
        main.overrideBend(s.elbow() * Mth.lerp(stab, 0.20f, 0.10f), e);
        off.overrideBend(s.elbow() * Mth.lerp(stab, 0.20f, 0.30f), e);
        // Blade turned over in the fists to point down into the ground.
        main.overrideRoll(side * Mth.PI, e);
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
