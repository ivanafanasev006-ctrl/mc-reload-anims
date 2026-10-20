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
 * Weapons, tools and held items.
 * <ul>
 *   <li>Fighting stance with a melee weapon; two-handed samurai grip when the other hand is empty.</li>
 *   <li>Keyframed strikes: sword combo (forehand, backhand, overhead, spinning finisher), heavy axe
 *       chops, pickaxe / shovel / hoe / tree-chopping loops, fists on blocks, item throws.</li>
 *   <li>Bow: side-on archer's stance, alternating classic and horizontal bow. Crossbow: carried at
 *       the chest, shouldered like a rifle when loaded, string pulled by hand when loading.</li>
 *   <li>Shield: staggered half-squat, recoil on every blocked hit. Food raised to the mouth.</li>
 * </ul>
 * With Better Combat installed, its weapons do not use the vanilla swing, so this module stays
 * quiet for them and Better Combat's animation is blended by the harmonizer.
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
        stance(rig, m, f, s, gate);
        swing(rig, m, f, s, gate, pt);
        useItems(rig, m, f, s, gate);
    }

    // ------------------------------------------------------------------ fighting stance

    private static void stance(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float gate) {
        float w = m.readyW.v * gate * m.armsFree.v;
        if (w <= 0.001f) return;
        float side = rig.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1f : -1f;
        PartPose main = side > 0 ? f.rightArm() : f.leftArm();
        PartPose off = side > 0 ? f.leftArm() : f.rightArm();
        float two = m.twoHandW.v;
        float breathe = 0.04f * Mth.sin(m.time * 1.8f + m.seed);

        // Blade held up and across the body, point toward the enemy (overrides the walk arms).
        main.override(Mth.lerp(two, -0.85f, -0.95f) + breathe, side * Mth.lerp(two, -0.35f, -0.42f), side * 0.08f, w);
        main.overrideBend(s.elbow() * Mth.lerp(two, 1.00f, 1.05f), w);
        // Free hand: guarding (one-handed) or on the hilt under the main hand (two-handed).
        off.override(Mth.lerp(two, -0.55f, -1.00f) + breathe, side * Mth.lerp(two, 0f, 0.58f),
                -side * Mth.lerp(two, 0.20f, 0.05f), w);
        off.overrideBend(s.elbow() * Mth.lerp(two, 0.80f, 1.15f), w);

        // Staggered feet, weight low, shoulders turned a little.
        float still = (1f - m.moveF()) * m.groundW.v;
        float lw = w * still;
        PartPose lead = side > 0 ? f.leftLeg() : f.rightLeg();
        PartPose rear = side > 0 ? f.rightLeg() : f.leftLeg();
        lead.targetX(-0.28f, lw);
        lead.bend += s.knee() * 0.45f * lw;
        rear.targetX(0.22f, lw);
        rear.bend += s.knee() * 0.40f * lw;
        Rigging.legsOut(f, 0.10f * lw);
        f.body().py -= 0.035f * lw;
        Rigging.twistUpper(f, side * 0.18f * w);
    }

    // ------------------------------------------------------------------ strikes

    private static void swing(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float gate, float pt) {
        int kind = m.slashKind;
        if (kind == MotionState.TOOL) {
            toolSwing(rig.player, m, f, s, gate, pt);
            return;
        }
        boolean keyed = MotionState.isWeapon(kind) || MotionState.isLooping(kind) || kind == MotionState.THROW;
        if (!keyed) return;  // fists: FistModule
        float t = m.slashProgress();
        if (t >= 1.3f) return;

        // Envelope: quick blend in, hold, blend out after the recovery.
        float in = m.slashChained ? 1f : AngleMath.ease(t / 0.10f);
        float w = in * (1f - AngleMath.ease((t - 0.85f) / 0.45f)) * gate;
        float side = kind == MotionState.THROW ? m.slashSide * m.throwSide : m.slashSide;
        // Better Combat (or any other mod) animating this arm wins.
        int armIndex = side > 0 ? PoseFrame.RIGHT_ARM : PoseFrame.LEFT_ARM;
        w *= 1f - rig.foreign[armIndex];
        if (w <= 0.001f) return;

        SlashLibrary.Key k = SlashLibrary.sample(SlashLibrary.forKind(kind), t);

        PartPose arm = side > 0 ? f.rightArm() : f.leftArm();
        PartPose off = side > 0 ? f.leftArm() : f.rightArm();
        // Tools aim at the block you look at: lower strikes when looking down.
        boolean aims = MotionState.isLooping(kind);
        float aim = aims ? rig.player.getXRot() * Mth.DEG_TO_RAD * 0.5f : 0f;
        arm.override(k.armX() + aim, side * k.armY(), side * k.armZ(), w);
        arm.overrideBend(s.elbow() * k.elbow(), w);
        // The blade moves in the hand too: cocked back on the wind-up, snapped through on the cut.
        arm.overrideRoll(side * SlashLibrary.roll(kind, t), w);

        // Two-handed weapons: the free hand rides on the hilt under the main hand.
        float two = MotionState.isWeapon(kind) ? m.twoHandW.v : 0f;
        float offX = Mth.lerp(two, k.offX(), k.armX() - 0.05f);
        float offY = Mth.lerp(two, 0f, side * k.armY() + side * 0.55f);
        float offZ = Mth.lerp(two, -side * k.offZ(), -side * k.armZ() * 0.3f);
        off.override(offX, offY, offZ, w);
        off.overrideBend(s.elbow() * Mth.lerp(two, k.offElbow(), k.elbow() + 0.35f), w);

        Rigging.twistUpper(f, side * k.twist() * w);
        f.body().rx += k.lean() * w;
        f.head().rx += k.lean() * 0.9f * w;
        f.body().rz -= side * 0.06f * Math.max(0f, -k.lean()) * w;
        if (k.spin() > 0f) {
            // Full turn; wraps to 0 at the end so it never unwinds.
            float turn = (k.spin() % 1f) * Mth.TWO_PI;
            f.body().ry += side * turn * w;
        }

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
        // Weapons: the whole body drives forward into the strike (lunge), not just the arm.
        if (MotionState.isWeapon(kind)) {
            float lunge = Math.max(0f, -k.lean());
            f.body().pz -= 0.38f * lunge * w;
            Rigging.leanTorso(f, s, 0.25f * lunge * w);
        }
    }

    /** Other items: vanilla arm swing, plus a little body follow-through. */
    private static void toolSwing(AbstractClientPlayer p, MotionState m, PoseFrame f, Signs s, float gate, float pt) {
        float a = p.getAttackAnim(pt);
        float w = m.toolSwingW.v * gate;
        if (w <= 0.001f || a <= 0f) return;
        float follow = Mth.sin(a * Mth.PI);
        Rigging.twistUpper(f, m.slashSide * Mth.sin(Mth.sqrt(a) * Mth.TWO_PI) * 0.12f * w);
        Rigging.leanTorso(f, s, 0.10f * follow * w);
    }

    // ------------------------------------------------------------------ held / used items

    private static void useItems(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float gate) {
        float side = m.useSide;
        PartPose useArm = side > 0 ? f.rightArm() : f.leftArm();
        PartPose otherArm = side > 0 ? f.leftArm() : f.rightArm();
        float pitch = rig.player.getXRot() * Mth.DEG_TO_RAD;

        // Bow: archer's side-on stance. Turning the whole body puts the bow shoulder toward the
        // target; head and (vanilla-aimed) arms are turned back so the aim stays on target.
        float bow = m.bowW.v * gate;
        if (bow > 0.001f) {
            // Turned side-on, but not so far that the drawing arm has to go through the chest;
            // less while walking, and the hips stay facing the way the feet are going.
            float moving = m.moveF();
            // Turns into the stance as the string comes back, not in one snap.
            float turn = side * (0.62f - 0.25f * moving) * bow * (0.4f + 0.6f * AngleMath.ease(m.usePull * 2f));
            f.body().ry += turn;
            f.head().ry += turn;
            useArm.ry += turn;
            // Drawing arm: only part of the turn, elbow raised out to the side so the forearm
            // runs past the face instead of through the body.
            otherArm.ry += turn * 0.45f;
            otherArm.rz -= side * (0.20f + 0.35f * m.usePull) * bow;
            otherArm.bend += s.elbow() * (0.5f + 1.1f * m.usePull) * bow;
            // Something (a shield) in the drawing hand: turn it edge-on so it doesn't cover the face.
            net.minecraft.world.InteractionHand used = rig.player.getUsedItemHand();
            boolean offHasItem = !rig.player.getItemInHand(used == net.minecraft.world.InteractionHand.MAIN_HAND
                    ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND).isEmpty();
            if (offHasItem) otherArm.overrideRoll(-side * 1.45f, bow);
            if (m.bowStyle == 1) {
                // Horizontal bow drawn from above: roll the bow arm flat.
                useArm.overrideRoll(side * 1.15f, bow);
                otherArm.rx -= 0.15f * bow;
            }
            // Legs keep stepping straight ahead: undo the body turn below the hips.
            f.rightLeg().ry -= turn;
            f.leftLeg().ry -= turn;
            f.rightLeg().rz += 0.10f * bow;
            f.leftLeg().rz -= 0.10f * bow;
            f.rightLeg().bend += s.knee() * 0.15f * bow;
            f.leftLeg().bend += s.knee() * 0.15f * bow;
            Rigging.leanTorso(f, s, -0.04f * bow * m.usePull);
        }

        // Crossbow: carried across the chest with both hands while unloaded.
        float carry = m.crossCarryW.v * gate * m.armsFree.v;
        if (carry > 0.001f) {
            float cs = crossbowSide(rig);
            PartPose holdArm = cs > 0 ? f.rightArm() : f.leftArm();
            PartPose support = cs > 0 ? f.leftArm() : f.rightArm();
            holdArm.override(-0.75f, -cs * 0.55f, 0f, carry);
            holdArm.overrideBend(s.elbow() * 1.15f, carry);
            support.override(-1.05f, cs * 0.65f, 0f, carry);
            support.overrideBend(s.elbow() * 1.45f, carry);
        }
        // Loaded: shouldered like a rifle, body bladed, feet planted wide.
        float aimC = m.crossAimW.v * gate;
        if (aimC > 0.001f) {
            float cs = crossbowSide(rig);
            sideOn(f, cs * 0.45f * aimC);
            Rigging.legsOut(f, 0.18f * aimC);
            PartPose lead = cs > 0 ? f.leftLeg() : f.rightLeg();
            lead.rx -= 0.25f * aimC;
            lead.bend += s.knee() * 0.30f * aimC;
            f.head().rz += cs * 0.12f * aimC;              // cheek down on the stock
        }
        // Loading: hold it down and pull the string back by hand.
        float xbow = m.crossbowW.v * gate;
        if (xbow > 0.001f) {
            otherArm.bend += s.elbow() * (0.4f + 1.4f * m.usePull) * xbow;
            otherArm.rx += 0.35f * m.usePull * xbow;
            Rigging.leanTorso(f, s, 0.18f * xbow);
            Rigging.crouch(f, s, 0.15f * xbow);
        }

        // Trident wind-up: lean back, opposite foot forward.
        float spear = m.spearW.v * gate;
        if (spear > 0.001f) {
            float pull = m.usePull * spear;
            Rigging.twistUpper(f, side * 0.35f * pull);
            Rigging.leanTorso(f, s, -0.15f * pull);
            PartPose lead = side > 0 ? f.leftLeg() : f.rightLeg();
            lead.rx -= 0.30f * pull;
            lead.bend += s.knee() * 0.30f * pull;
        }

        // Shield: staggered half-squat behind the shield, recoil on each blocked hit.
        float block = m.blockW.v * gate;
        if (block > 0.001f) {
            PartPose lead = side > 0 ? f.rightLeg() : f.leftLeg();   // shield-side foot forward
            PartPose rear = side > 0 ? f.leftLeg() : f.rightLeg();
            lead.targetX(-0.35f, block);
            lead.bend += s.knee() * 0.70f * block;
            rear.targetX(0.35f, block);
            rear.bend += s.knee() * 0.60f * block;
            Rigging.legsOut(f, 0.12f * block);
            f.body().py -= 0.09f * block;
            Rigging.leanTorso(f, s, 0.12f * block);
            f.head().rx -= 0.10f * block;
            useArm.bend += s.elbow() * 0.35f * block;
        }
        float hit = m.shieldHit.x;
        if (Math.abs(hit) > 0.001f) {
            useArm.rx += 0.40f * hit;
            Rigging.leanTorso(f, s, -0.22f * hit);
            f.body().pz += 0.06f * hit;
            f.head().rx -= 0.12f * hit;
            f.body().py -= 0.03f * hit;
        }

        // Food and drink: brought right up to the mouth by the hand holding it.
        float eat = m.eatW.v * gate;
        float drink = m.drinkW.v * gate;
        float mouth = Math.max(eat, drink);
        if (mouth > 0.001f) {
            float bob = eat * 0.12f * Mth.sin(m.time * 14f);
            useArm.override(-1.65f + pitch * 0.5f + bob, -side * 0.55f, 0f, mouth);
            useArm.overrideBend(s.elbow() * 1.85f, mouth);
        }
        if (eat > 0.001f) f.head().rx += (0.12f + 0.07f * Mth.sin(m.time * 14f)) * eat;
        if (drink > 0.001f) {
            f.head().rx -= 0.35f * drink;
            Rigging.leanTorso(f, s, -0.05f * drink);
            useArm.rx -= 0.35f * drink;
        }
        float horn = m.hornW.v * gate;
        if (horn > 0.001f) {
            f.head().rx -= 0.25f * horn;
            Rigging.leanTorso(f, s, -0.08f * horn);
        }
    }

    /** Turn the whole body about its vertical axis, keeping head and arms aimed where they were. */
    private static void sideOn(PoseFrame f, float turn) {
        f.body().ry += turn;
        f.head().ry += turn;
        f.rightArm().ry += turn;
        f.leftArm().ry += turn;
    }

    /** +1 if the crossbow is in the right hand, -1 if left. */
    private static float crossbowSide(PlayerRig rig) {
        AbstractClientPlayer p = rig.player;
        boolean inMain = p.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem;
        net.minecraft.world.entity.HumanoidArm arm = inMain ? p.getMainArm() : p.getMainArm().getOpposite();
        return arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1f : -1f;
    }
}
