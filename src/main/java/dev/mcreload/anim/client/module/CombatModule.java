package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.pose.PartPose;
import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

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
        AbstractClientPlayer p = rig.player;

        swing(p, m, f, s, pt, gate);
        useItems(m, f, s, gate);
    }

    private static void swing(AbstractClientPlayer p, MotionState m, PoseFrame f, Signs s, float pt, float gate) {
        float a = p.getAttackAnim(pt);
        if (a <= 0f) return;

        HumanoidArm arm = p.swingingArm == InteractionHand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
        ItemStack held = p.getItemInHand(p.swingingArm);
        // Full body motion for weapons, a lighter touch when mining or punching.
        float w = (isWeapon(held) ? 1f : 0.35f) * gate;

        float sq = Mth.sqrt(a);
        float twist = Mth.sin(sq * Mth.TWO_PI);   // same curve vanilla uses for the torso
        float follow = Mth.sin(a * Mth.PI);       // 0 -> 1 -> 0 over the swing

        Rigging.twistUpper(f, side * twist * 0.22f * w);
        Rigging.leanTorso(f, s, 0.16f * follow * w);
        f.head().rx -= 0.10f * follow * w;
        f.body().rz -= side * 0.05f * follow * w;

        // Wind-up: the swinging elbow is bent, then snaps straight through the hit.
        PartPose swingArm = side > 0 ? f.rightArm() : f.leftArm();
        swingArm.bend += s.elbow() * 0.5f * Math.max(0f, Mth.cos(sq * Mth.PI)) * w;

        // Short lunge with the opposite foot when standing; fades out while running.
        float still = 1f - 0.7f * m.moveF();
        PartPose lead = side > 0 ? f.leftLeg() : f.rightLeg();
        PartPose rear = side > 0 ? f.rightLeg() : f.leftLeg();
        float lunge = follow * w * still;
        lead.rx -= 0.28f * lunge;
        rear.rx += 0.20f * lunge;
        lead.bend += s.knee() * 0.35f * lunge;
        rear.bend += s.knee() * 0.18f * lunge;
        f.body().py -= 0.025f * lunge;
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

    private static boolean isWeapon(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem;
    }
}
