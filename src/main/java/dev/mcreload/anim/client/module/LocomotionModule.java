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
 * Walking, sprinting and sneaking. Rides on vanilla's own limb swing phase so the feet stay in
 * sync with footsteps, and adds what vanilla lacks: knees and elbows that bend, an upper body
 * that counter-rotates against the hips, a bob, sprint lean, and leaning into strafes and turns.
 */
final class LocomotionModule implements AnimModule {
    @Override
    public boolean enabled() {
        return Cfg.locomotion;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float gate = m.gate.v * (1f - m.swimW.v) * (1f - m.climbW.v) * (1f - m.waterW.v);
        if (gate <= 0.001f) return;

        float speed = Math.min(rig.player.walkAnimation.speed(pt), 1.2f);
        float phase = rig.player.walkAnimation.position(pt) * 0.6662f;
        float sinP = Mth.sin(phase), cosP = Mth.cos(phase);
        float sprint = m.sprintW.v;
        // In the air the vanilla stride keeps running; tone ours down there.
        float amp = speed * gate * (1f - 0.65f * m.airW.v);
        // Iron's Spells animates the arms while casting: keep our arm swing out of its way.
        float armFree = 1f - 0.8f * m.castW.v;

        // Knees: fold while the leg swings forward, stay soft on the way back.
        float kneeSwing = 0.65f + 0.45f * sprint;
        float rKnee = 0.12f + kneeSwing * Math.max(0f, sinP);
        float lKnee = 0.12f + kneeSwing * Math.max(0f, -sinP);
        f.rightLeg().bend += s.knee() * rKnee * amp;
        f.leftLeg().bend += s.knee() * lKnee * amp;
        // Higher knee lift when sprinting.
        f.rightLeg().rx -= 0.25f * sprint * Math.max(0f, sinP) * amp;
        f.leftLeg().rx -= 0.25f * sprint * Math.max(0f, -sinP) * amp;

        // Elbows: always a little soft, tucked tight when sprinting, more bend on the forward swing.
        float elbowBase = 0.18f + 0.75f * sprint;
        f.rightArm().bend += s.elbow() * (elbowBase + 0.30f * Math.max(0f, -sinP)) * amp * armFree;
        f.leftArm().bend += s.elbow() * (elbowBase + 0.30f * Math.max(0f, sinP)) * amp * armFree;
        // Sprint arm pump on top of vanilla's swing.
        f.rightArm().rx += -cosP * 0.30f * sprint * amp * armFree;
        f.leftArm().rx += cosP * 0.30f * sprint * amp * armFree;

        // Shoulders counter-rotate against the hips.
        Rigging.twistUpper(f, -cosP * 0.07f * amp * (1f + 0.8f * sprint) * armFree);

        // Bob: lowest when the legs are furthest apart.
        f.body().py -= Math.abs(cosP) * 0.035f * amp * (1f + 0.6f * sprint);

        // Sprint lean: whole body forward, upper torso a bit more, head keeps looking ahead.
        float lean = sprint * m.moveF() * gate;
        f.body().rx -= 0.13f * lean;
        Rigging.leanTorso(f, s, 0.12f * lean);
        f.head().rx -= 0.21f * lean;

        // Lean back a little when walking backwards.
        float back = AngleMath.clamp(-m.forward.v / 0.2f, 0f, 1f);
        f.body().rx += 0.06f * back * gate;

        // Lean into strafes and turns (negative body roll = lean right).
        float strafe = AngleMath.clamp(m.strafe.v / 0.2f, -1f, 1f);
        f.body().rz -= 0.09f * strafe * gate;
        float turn = AngleMath.clamp(m.turn.v / 15f, -1f, 1f);
        f.body().rz -= 0.10f * turn * m.moveF() * (0.5f + 0.5f * sprint) * gate;

        // Sneaking: real bent-knee crouch on top of vanilla's.
        float sneak = m.sneakW.v * m.groundW.v * gate;
        if (sneak > 0.001f) {
            Rigging.crouch(f, s, 0.18f * sneak);
            Rigging.leanTorso(f, s, 0.05f * sneak);
        }
    }
}
