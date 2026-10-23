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
        swing(rig, m, f, s, gate, pt);
        useItems(rig, m, f, s, gate, pt);
        javelin(rig, m, f, s, gate);
        fishing(rig, m, f, s, gate);
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
        // Swords and axes: BladeModule. Fists: FistModule.
        boolean keyed = MotionState.isLooping(kind) || kind == MotionState.THROW || kind == MotionState.PLACE
                || kind == MotionState.STAB || kind == MotionState.SWAT;
        if (!keyed) return;
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
        // Tools aim at the block you look at, a swat at whatever you look at: lower strikes when
        // looking down.
        boolean aims = MotionState.isLooping(kind) || kind == MotionState.PLACE || kind == MotionState.SWAT;
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
        if (MotionState.isTool(kind)) {
            // Tools are worked with one hand: the free arm hangs relaxed and steady.
            off.override(-0.30f, 0f, -side * 0.12f, w);
            off.overrideBend(s.elbow() * 0.35f, w);
        } else {
            off.override(offX, offY, offZ, w);
            off.overrideBend(s.elbow() * Mth.lerp(two, k.offElbow(), k.elbow() + 0.35f), w);
        }

        Rigging.twistUpper(f, side * k.twist() * w);
        // Weapons pitch the whole body into the strike; working a block in front of the feet
        // bends at the waist and knees instead, so the feet stay planted.
        boolean weapon = MotionState.isWeapon(kind) || kind == MotionState.STAB;
        float bodyLean = weapon ? k.lean() : k.lean() * 0.25f;
        f.body().rx += bodyLean * w;
        f.head().rx += bodyLean * 0.9f * w;
        f.body().rz -= side * 0.06f * Math.max(0f, -bodyLean) * w;
        if (!weapon) {
            float waist = -k.lean() * 1.6f;
            Rigging.leanTorso(f, s, waist * w);
            f.head().rx -= waist * 0.6f * w;
            Rigging.crouch(f, s, waist * 0.5f * w);
        }
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
        if (kind == MotionState.STAB) {
            // The spear lines up with the arm for the thrust.
            m.itemTiltTarget = Math.max(m.itemTiltTarget, Mth.HALF_PI * w);
        }
        if (weapon) {
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

    private static void useItems(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float gate, float pt) {
        float side = m.useSide;
        PartPose useArm = side > 0 ? f.rightArm() : f.leftArm();
        PartPose otherArm = side > 0 ? f.leftArm() : f.rightArm();
        float pitch = rig.player.getXRot() * Mth.DEG_TO_RAD;

        // Bow: archer's side-on stance. Turning the whole body puts the bow shoulder toward the
        // target; head and (vanilla-aimed) arms are turned back so the aim stays on target.
        float bow = m.bowW.v * gate;
        if (bow > 0.001f) {
            // The arms aim exactly as vanilla points them (along the camera), so nothing turns
            // them or the chest: the bow arm stays straight, the drawing elbow folds back toward
            // the face as the string comes back. The feet take an archer's stance.
            useArm.overrideBend(0f, bow);
            // The drawing hand stays close behind the bow hand: the arm swings in a little
            // further than vanilla and the elbow folds sideways (bend axis turned a quarter
            // turn), so the string comes straight back a little instead of the forearm tipping up.
            otherArm.ry += side * 0.30f * bow;
            // Vanilla points both arms where the head looks, up to ~50 degrees off the body:
            // turn the chest and shoulders with the head, so the arms stay in front of the chest
            // instead of the drawing arm crossing through it when the camera swings round.
            AbstractClientPlayer pl = rig.player;
            float headYaw = Mth.wrapDegrees(Mth.rotLerp(pt, pl.yHeadRotO, pl.yHeadRot)
                    - Mth.rotLerp(pt, pl.yBodyRotO, pl.yBodyRot)) * Mth.DEG_TO_RAD;
            Rigging.twistShoulders(f, headYaw * bow);
            otherArm.overrideBendAxis(side * Mth.HALF_PI, bow);
            otherArm.overrideBend(s.elbow() * (0.12f + 0.30f * m.usePull), bow);
            f.torso().overrideBend(0f, bow);
            if (m.bowStyle == 1) {
                // Horizontal bow: the bow arm turned flat (about its own axis, the aim is kept).
                useArm.overrideRoll(side * 1.15f, bow);
            }
            float still = 1f - m.moveF();
            PartPose lead = side > 0 ? f.rightLeg() : f.leftLeg();
            PartPose rear = side > 0 ? f.leftLeg() : f.rightLeg();
            lead.overrideX(-0.25f, bow * still);
            rear.overrideX(0.20f, bow * still);
            lead.bend += s.knee() * 0.25f * bow;
            rear.bend += s.knee() * 0.15f * bow;
            Rigging.legsOut(f, 0.08f * bow * still);
        }

        // Crossbow: both hands on it, held at the body like a shotgun: the trigger hand at the
        // hip, the other hand forward under the front, the shoulders turned a little behind it
        // (arm angles solved for the crossbow's grip and front). Loaded, it comes up a little,
        // level and ready, and the feet set when standing.
        float carry = m.crossCarryW.v * gate;
        float aimC = m.crossAimW.v * gate;
        float hold = Math.max(carry, aimC);
        if (hold > 0.001f) {
            float cs = crossbowSide(rig);
            PartPose grip = cs > 0 ? f.rightArm() : f.leftArm();
            PartPose fore = cs > 0 ? f.leftArm() : f.rightArm();
            float up = aimC / hold;
            // Held still: no breathing or swinging on the arms (they would rattle the crossbow).
            grip.override(Mth.lerp(up, -0.78f, -0.94f), cs * Mth.lerp(up, -0.63f, -0.56f),
                    cs * Mth.lerp(up, 0.43f, 0.33f), hold);
            grip.overrideBend(s.elbow() * Mth.lerp(up, 0.84f, 0.77f), hold);
            fore.override(Mth.lerp(up, -0.92f, -1.40f), cs * Mth.lerp(up, 0.35f, 0.61f),
                    cs * Mth.lerp(up, 0.46f, -0.24f), hold);
            fore.overrideBend(s.elbow() * 0.10f, hold);
            Rigging.twistShoulders(f, cs * 0.40f * hold);
            float still = (1f - m.moveF()) * aimC;
            PartPose lead = cs > 0 ? f.leftLeg() : f.rightLeg();
            PartPose rear = cs > 0 ? f.rightLeg() : f.leftLeg();
            lead.overrideX(-0.25f, still);
            rear.overrideX(0.18f, still);
            lead.bend += s.knee() * 0.25f * still;
            rear.bend += s.knee() * 0.20f * still;
            Rigging.legsOut(f, 0.08f * still);
        }
        // Loading: hold it down and pull the string back by hand.
        float xbow = m.crossbowW.v * gate;
        if (xbow > 0.001f) {
            otherArm.bend += s.elbow() * (0.4f + 1.4f * m.usePull) * xbow;
            otherArm.rx += 0.35f * m.usePull * xbow;
            Rigging.leanTorso(f, s, 0.18f * xbow);
            Rigging.crouch(f, s, 0.15f * xbow);
        }


        // Shield: staggered half-squat behind the shield, recoil on each blocked hit.
        float block = m.blockW.v * gate;
        if (block > 0.001f) {
            // Behind the shield: knees bent and the weight low, but the legs keep walking (or
            // creeping, when sneaking); standing still, a staggered guard stance.
            float still = 1f - m.moveF();
            PartPose lead = side > 0 ? f.rightLeg() : f.leftLeg();   // shield-side foot forward
            PartPose rear = side > 0 ? f.leftLeg() : f.rightLeg();
            float sneak = m.sneakW.v;
            lead.overrideX(-0.35f, block * still * (1f - sneak));
            rear.overrideX(0.30f, block * still * (1f - sneak));
            // Already low when sneaking: no extra squat or lean on top.
            Rigging.crouch(f, s, 0.28f * block * (1f - sneak));
            Rigging.legsOut(f, 0.10f * block * still * (1f - sneak));
            Rigging.leanTorso(f, s, 0.18f * block * (1f - sneak));
            useArm.bend += s.elbow() * 0.35f * block;
            // Keep the shield upright in front of the chest however far the chest leans (the
            // arms ride on the torso, so a forward lean tips the shield down into the legs);
            // vanilla also drops the arms 0.4 rad when crouching.
            float chestLean = f.torso().bend * s.torso();
            useArm.rx -= (chestLean + (rig.player.isCrouching() ? 0.4f : 0f)) * block;
        }
        float hit = m.shieldHit.x;
        if (Math.abs(hit) > 0.001f) {
            useArm.rx += 0.40f * hit;
            Rigging.leanTorso(f, s, -0.22f * hit);
            f.body().pz += 0.06f * hit;
            f.head().rx -= 0.12f * hit;
            f.body().py -= 0.03f * hit;
        }

        // Food and drink: the FOOD goes to the mouth, not the hand. A held item sits about five
        // pixels in front of the fist, so the fist stops below the chin with the forearm up and
        // the food right at the lips (arm solved with the jointed arm for the food's centre, as
        // a function of where the head is pointing).
        float eat = m.eatW.v * gate;
        float drink = m.drinkW.v * gate;
        float mouth = Math.max(eat, drink);
        if (mouth > 0.001f) {
            float headPitch = AngleMath.clamp(pitch + (0.12f * eat - 0.35f * drink) / mouth, -0.9f, 1.0f);
            float bob = eat * 0.05f * Mth.sin(m.time * 14f);
            useArm.override(-1.20f + 1.19f * headPitch + bob, -side * (0.76f + 0.27f * headPitch),
                    side * Math.max(0f, 0.21f - 0.40f * headPitch), mouth);
            useArm.overrideBend(s.elbow() * (0.60f + 0.29f * headPitch), mouth);
        }
        if (eat > 0.001f) f.head().rx += (0.12f + 0.07f * Mth.sin(m.time * 14f)) * eat;
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

    // ------------------------------------------------------------------ trident

    /**
     * Trident throw, like a javelin. Wind-up: the throwing arm drawn back high over the
     * shoulder, elbow cocked, chest turned side-on and leaning back, the free arm pointing at the
     * target, feet in a long stride. Release: whipped over the top and down, the body following
     * through onto the front foot.
     */
    private static void javelin(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float gate) {
        float spear = m.spearW.v * gate;
        float ts = m.since(m.spearThrowStart) / 0.55f;
        float thr = ts >= 0f && ts < 1f ? (1f - AngleMath.ease((ts - 0.5f) / 0.5f)) * gate : 0f;
        float w = Math.max(spear, thr);
        if (w <= 0.001f) return;
        float side = m.useSide;
        PartPose arm = side > 0 ? f.rightArm() : f.leftArm();
        PartPose free = side > 0 ? f.leftArm() : f.rightArm();
        PartPose lead = side > 0 ? f.leftLeg() : f.rightLeg();
        PartPose rear = side > 0 ? f.rightLeg() : f.leftLeg();
        float pull = spear > thr ? AngleMath.ease(m.usePull) : 1f;
        float whip = thr > spear ? AngleMath.ease(ts / 0.28f) : 0f;   // 0 = cocked, 1 = thrown

        arm.override(Mth.lerp(whip, -2.45f - 0.40f * pull, -0.70f), side * Mth.lerp(whip, 0.12f, -0.25f),
                side * Mth.lerp(whip, 0.22f, 0.05f), w);
        arm.overrideBend(s.elbow() * Mth.lerp(whip, 0.30f + 0.40f * pull, 0.05f), w);
        free.override(Mth.lerp(whip, -1.45f, -0.30f), -side * Mth.lerp(whip, 0.30f, 0.0f),
                -side * Mth.lerp(whip, 0.08f, 0.40f), w);
        free.overrideBend(s.elbow() * Mth.lerp(whip, 0.15f, 0.65f), w);
        Rigging.twistShoulders(f, side * Mth.lerp(whip, 0.55f * pull, -0.45f) * w);
        Rigging.leanTorso(f, s, Mth.lerp(whip, -0.18f * pull, 0.32f) * w);
        f.head().rx += Mth.lerp(whip, 0.10f * pull, -0.15f) * w;

        float still = (1f - m.moveF()) * m.groundW.v;
        lead.overrideX(Mth.lerp(whip, -0.45f * pull, -0.35f), w * still);
        rear.overrideX(Mth.lerp(whip, 0.35f * pull, 0.05f), w * still);
        lead.bend += s.knee() * Mth.lerp(whip, 0.25f, 0.50f) * w * still;
        rear.bend += s.knee() * Mth.lerp(whip, 0.30f, 0.15f) * w * still;
        f.body().py -= 0.03f * pull * w * still;
    }

    // ------------------------------------------------------------------ fishing

    /**
     * Fishing rod: a real cast (rod whipped back over the shoulder, then flicked forward with a
     * step and the body following through), a relaxed hold while the bobber is out (rod tip up,
     * weight on one leg, the tip twitching now and then), and a sharp strike back on reeling in.
     */
    private static void fishing(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float gate) {
        float side = m.rodSide;
        PartPose rodArm = side > 0 ? f.rightArm() : f.leftArm();
        PartPose free = side > 0 ? f.leftArm() : f.rightArm();
        PartPose lead = side > 0 ? f.leftLeg() : f.rightLeg();
        PartPose rear = side > 0 ? f.rightLeg() : f.leftLeg();
        float hold = m.fishingW.v * gate;

        float tc = m.since(m.fishCastStart) / 0.6f;
        float cast = tc >= 0f && tc < 1f ? (1f - AngleMath.ease((tc - 0.6f) / 0.4f)) * gate : 0f;
        float tr = m.since(m.fishReelStart) / 0.5f;
        float reel = tr >= 0f && tr < 1f ? (1f - AngleMath.ease((tr - 0.45f) / 0.55f)) * gate : 0f;
        float w = Math.max(hold, Math.max(cast, reel));
        if (w <= 0.001f) return;

        // Base: the waiting hold.
        float t = m.time + m.seed;
        float twitch = Mth.sin(t * 9f) * Math.max(0f, Mth.sin(t * 0.7f) - 0.85f) * 6f;
        float x = -0.60f + 0.03f * Mth.sin(t * 1.3f) - 0.10f * twitch, bend = 0.95f, twist = 0.10f, lean = -0.04f;
        float step = 0f;
        if (cast > 0.001f) {
            // Back over the shoulder, then whipped forward, then settling.
            float back = AngleMath.ease(tc / 0.2f) * (1f - AngleMath.ease((tc - 0.2f) / 0.25f));
            float flick = AngleMath.ease((tc - 0.2f) / 0.25f) * (1f - AngleMath.ease((tc - 0.55f) / 0.45f));
            x = Mth.lerp(back, x, -2.75f);
            x = Mth.lerp(flick, x, -1.35f);
            bend = Mth.lerp(back, bend, 0.70f);
            bend = Mth.lerp(flick, bend, 0.15f);
            twist = twist + 0.45f * back - 0.35f * flick;
            lean = lean - 0.12f * back + 0.22f * flick;
            step = Math.max(back * 0.5f, flick);
        }
        if (reel > 0.001f) {
            // Strike: rod snapped up and back, leaning back against the fish.
            float jerk = AngleMath.ease(tr / 0.15f) * (1f - AngleMath.ease((tr - 0.25f) / 0.5f));
            x = Mth.lerp(jerk, x, -1.80f);
            bend = Mth.lerp(jerk, bend, 0.55f);
            lean -= 0.18f * jerk;
            twist += 0.15f * jerk;
        }
        float aw = w;
        rodArm.override(x, -side * 0.18f, side * 0.10f, aw);
        rodArm.overrideBend(s.elbow() * bend, aw);
        // The free hand rests on the belly while waiting, out for balance when casting.
        free.override(Mth.lerp(step, -0.55f, -0.25f), side * Mth.lerp(step, 0.45f, 0f), -side * Mth.lerp(step, 0.05f, 0.45f), aw);
        free.overrideBend(s.elbow() * Mth.lerp(step, 1.25f, 0.50f), aw);
        Rigging.twistUpper(f, side * twist * w);
        Rigging.leanTorso(f, s, lean * w);
        // Weight on the back leg while waiting; a step into the cast.
        float still = (1f - m.moveF()) * m.groundW.v;
        lead.overrideX(Mth.lerp(step, -0.12f, -0.40f), w * still);
        rear.overrideX(Mth.lerp(step, 0.10f, 0.15f), w * still);
        lead.bend += s.knee() * (0.10f + 0.25f * step) * w * still;
        rear.bend += s.knee() * 0.15f * w * still;
        f.body().rz -= side * 0.03f * w * still * (1f - step);
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
