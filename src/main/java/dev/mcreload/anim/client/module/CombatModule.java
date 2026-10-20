package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.math.AngleMath;
import dev.mcreload.anim.client.pose.PartPose;
import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

/**
 * Whole-body follow-through for vanilla combat: the swing drives the torso, hips and a short
 * lunge; bows twist the shoulders and draw the string with a bending elbow; crossbows, tridents
 * and shields get a braced stance; eating and drinking move the head.
 *
 * With Better Combat installed, its weapons do not use the vanilla swing, so this module simply
 * stays quiet for them and Better Combat's own attack animation is blended by the harmonizer.
 */
final class CombatModule implements AnimModule {
    @Override
    public boolean enabled() {
        return Cfg.combat;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float gate = m.gate.v;
        if (gate <= 0.001f) return;
        swing(rig, m, f, s, gate, pt);
        useItems(m, f, s, gate);
    }

    /**
     * Weapon and fist attacks play our own keyframed wind-up / strike / recovery, chained into a
     * three-hit combo; tools keep vanilla's swing so mining looks normal.
     */
    private static void swing(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float gate, float pt) {
        int kind = m.slashKind;
        if (kind < 0 || kind == MotionState.TOOL) {
            toolSwing(rig.player, m, f, s, gate, pt);
            return;
        }
        float t = m.slashProgress();
        if (t >= 1.3f) return;

        // Envelope: quick blend in, hold, blend out after the recovery.
        float w = AngleMath.ease(t / 0.10f) * (1f - AngleMath.ease((t - 0.85f) / 0.45f)) * gate;
        // Better Combat (or any other mod) animating this arm wins.
        int armIndex = m.slashSide > 0 ? PoseFrame.RIGHT_ARM : PoseFrame.LEFT_ARM;
        w *= 1f - rig.foreign[armIndex];
        if (w <= 0.001f) return;

        float side = m.slashSide;
        SlashLibrary.Key k = SlashLibrary.sample(SlashLibrary.forKind(kind), t);

        PartPose arm = side > 0 ? f.rightArm() : f.leftArm();
        PartPose off = side > 0 ? f.leftArm() : f.rightArm();
        arm.override(k.armX(), side * k.armY(), side * k.armZ(), w);
        arm.overrideBend(s.elbow() * k.elbow(), w);
        off.override(k.offX(), 0f, -side * k.offZ(), w);
        off.overrideBend(s.elbow() * k.offElbow(), w);

        Rigging.twistUpper(f, side * k.twist() * w);
        f.body().rx += k.lean() * w;
        f.head().rx += k.lean() * 0.9f * w;
        f.body().rz -= side * 0.06f * Math.max(0f, -k.lean()) * w;

        // Footwork: step into the strike with the opposite foot, less while running.
        float still = 1f - 0.75f * m.moveF();
        float lw = w * still;
        PartPose lead = side > 0 ? f.leftLeg() : f.rightLeg();
        PartPose rear = side > 0 ? f.rightLeg() : f.leftLeg();
        lead.overrideX(k.leadThigh(), lw);
        lead.overrideBend(s.knee() * k.leadKnee(), lw);
        rear.overrideX(k.rearThigh(), lw);
        rear.overrideBend(s.knee() * k.rearKnee(), lw);
        float drop = Math.max(0f, k.leadKnee() - 0.3f);
        f.body().py -= 0.07f * drop * lw;
    }

    /** Mining / using a tool: vanilla arm swing, plus a little body follow-through. */
    private static void toolSwing(AbstractClientPlayer p, MotionState m, PoseFrame f, Signs s, float gate, float pt) {
        float a = p.getAttackAnim(pt);
        float w = m.toolSwingW.v * gate;
        if (w <= 0.001f || a <= 0f) return;
        float follow = Mth.sin(a * Mth.PI);
        Rigging.twistUpper(f, m.slashSide * Mth.sin(Mth.sqrt(a) * Mth.TWO_PI) * 0.12f * w);
        Rigging.leanTorso(f, s, 0.10f * follow * w);
    }

    private static void useItems(MotionState m, PoseFrame f, Signs s, float gate) {
        float side = m.useSide;
        PartPose useArm = side > 0 ? f.rightArm() : f.leftArm();
        PartPose otherArm = side > 0 ? f.leftArm() : f.rightArm();

        // Bow: bow-arm shoulder forward, string arm draws back with a folding elbow, wide stance.
        float bow = m.bowW.v * gate;
        if (bow > 0.001f) {
            Rigging.twistUpper(f, -side * 0.30f * bow);
            otherArm.bend += s.elbow() * 1.25f * m.usePull * bow;
            Rigging.legsOut(f, 0.10f * bow);
            f.rightLeg().bend += s.knee() * 0.12f * bow;
            f.leftLeg().bend += s.knee() * 0.12f * bow;
            Rigging.leanTorso(f, s, -0.04f * bow * m.usePull);
        }

        // Crossbow charging: hunch over it and crank.
        float xbow = m.crossbowW.v * gate;
        if (xbow > 0.001f) {
            otherArm.bend += s.elbow() * 1.1f * m.usePull * xbow;
            Rigging.leanTorso(f, s, 0.12f * xbow);
            Rigging.crouch(f, s, 0.12f * xbow);
        }

        // Trident wind-up: lean back, opposite foot forward.
        float spear = m.spearW.v * gate;
        if (spear > 0.001f) {
            float pull = m.usePull * spear;
            Rigging.twistUpper(f, side * 0.25f * pull);
            Rigging.leanTorso(f, s, -0.12f * pull);
            PartPose lead = side > 0 ? f.leftLeg() : f.rightLeg();
            lead.rx -= 0.25f * pull;
            lead.bend += s.knee() * 0.25f * pull;
        }

        // Shield: braced, slightly crouched, shield elbow tucked.
        float block = m.blockW.v * gate;
        if (block > 0.001f) {
            Rigging.crouch(f, s, 0.16f * block);
            Rigging.leanTorso(f, s, 0.10f * block);
            f.head().rx -= 0.08f * block;
            Rigging.legsOut(f, 0.06f * block);
            useArm.bend += s.elbow() * 0.40f * block;
        }

        // Food and drink.
        float eat = m.eatW.v * gate;
        if (eat > 0.001f) f.head().rx += (0.12f + 0.07f * Mth.sin(m.time * 14f)) * eat;
        float drink = m.drinkW.v * gate;
        if (drink > 0.001f) {
            f.head().rx -= 0.35f * drink;
            Rigging.leanTorso(f, s, -0.05f * drink);
        }
        float horn = m.hornW.v * gate;
        if (horn > 0.001f) {
            f.head().rx -= 0.25f * horn;
            Rigging.leanTorso(f, s, -0.08f * horn);
        }
    }

}
