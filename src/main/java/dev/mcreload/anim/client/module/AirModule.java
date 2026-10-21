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

/**
 * Airborne motion.
 * <ul>
 *   <li>Jump: the leading leg ALTERNATES every jump (Mo'Bends style). The lead knee starts folded
 *       and extends, the trailing shin folds up behind as the leap goes on; arms counter-swing;
 *       the chest leans in as you start to come down. Standing jumps tuck both knees instead.</li>
 *   <li>Falling: arms thrown up in a Y and flailing, legs kicking back underneath (never forward).</li>
 *   <li>Long fast fall: skydiver spread, body pitched down, limbs fluttering in the wind.</li>
 *   <li>Landing: spring-loaded crouch that keeps the feet planted.</li>
 * </ul>
 */
final class AirModule implements AnimModule {
    @Override
    public boolean enabled() {
        return Cfg.airborne || Cfg.landing;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float gate = m.gate.v;
        if (gate <= 0.001f) return;
        float arms = m.armsFree.v * (1f - m.toolSwingW.v);

        if (Cfg.airborne) {
            float air = m.airW.v * gate;
            if (air > 0.001f) {
                float fall = m.fallW.v;
                float dive = m.skydiveW.v;
                float jumpW = air * (1f - fall);
                float fallW = air * fall * (1f - dive);
                float diveW = air * fall * dive;
                if (jumpW > 0.001f) jump(m, f, s, jumpW, arms, pt);
                // A real drop (faster than any ordinary jump comes down) with only a blade in
                // hand: both hands take the sword up overhead, ready to plant it on landing.
                boolean bladeOnly = m.holdingBlade;
                float bladeW = bladeOnly ? air * AngleMath.clamp((-m.vertical - 0.50f) / 0.25f, 0f, 1f) : 0f;
                float plainFall = fallW * (1f - bladeW);
                if (plainFall > 0.001f) fall(m, f, s, plainFall, arms);
                if (bladeW > 0.001f) bladeFall(m, f, s, bladeW, arms, m.mainRight ? 1f : -1f,
                        rig.player.getOffhandItem().isEmpty());
                if (diveW > 0.001f) skydive(m, f, s, diveW, arms);
            }
        }

        if (Cfg.landing) {
            float land = Math.max(0f, m.land.x) * gate;
            if (land > 0.001f) {
                Rigging.crouch(f, s, 0.70f * land);
                // Legs absorb the impact, driven up into the hips.
                f.rightLeg().py -= 1.4f * land;
                f.leftLeg().py -= 1.4f * land;
                Rigging.leanTorso(f, s, 0.25f * land);
                f.body().rx -= 0.18f * land;
                f.head().rx -= 0.35f * land;
                f.rightArm().targetX(-0.75f, Math.min(1f, land) * arms);
                f.leftArm().targetX(-0.75f, Math.min(1f, land) * arms);
                Rigging.armsOut(f, 0.25f * land);
            }
        }
    }

    private static void jump(MotionState m, PoseFrame f, Signs s, float w, float arms, float pt) {
        float t = m.airTime(pt);
        boolean fresh = t < 1.2f;
        // 0 -> 1 over the first half second of the leap (eased like Mo'Bends' relax curve).
        float relax = fresh ? (float) Math.sqrt(Math.sqrt(AngleMath.clamp(t / 0.5f, 0f, 1f))) : 1f;
        float run = fresh ? m.takeoffRun : 0.4f;

        boolean rightLeads = m.jumpLeg;
        PartPose lead = rightLeads ? f.rightLeg() : f.leftLeg();
        PartPose trail = rightLeads ? f.leftLeg() : f.rightLeg();
        PartPose leadArm = rightLeads ? f.leftArm() : f.rightArm();   // opposite arm reaches forward
        PartPose trailArm = rightLeads ? f.rightArm() : f.leftArm();
        float side = rightLeads ? 1f : -1f;

        // Running leap.
        float leadThigh = Mth.lerp(run, -0.60f, -0.80f);
        float trailThigh = Mth.lerp(run, -0.35f, 0.80f);
        float leadKnee = Mth.lerp(run, 1.10f, 1.40f) * (1f - relax) + Mth.lerp(run, 0.55f, 0.25f);
        float trailKnee = Mth.lerp(run, 0.80f, 1.22f) * relax + 0.10f;
        // Jumping on the spot: no lead leg, both knees tuck up under the body, feet a little apart.
        float still = AngleMath.clamp(1f - run / 0.35f, 0f, 1f);
        float tuck = Mth.lerp(relax, 1.15f, 0.55f);
        leadThigh = Mth.lerp(still, leadThigh, -0.22f);
        trailThigh = Mth.lerp(still, trailThigh, -0.22f);
        leadKnee = Mth.lerp(still, leadKnee, tuck);
        trailKnee = Mth.lerp(still, trailKnee, tuck);
        leg(lead, s, leadThigh, leadKnee, w);
        leg(trail, s, trailThigh, trailKnee, w);
        Rigging.legsOut(f, (0.09f + 0.12f * still) * w);
        // Legs draw up into the hips as the knees tuck.
        f.rightLeg().py -= 1.0f * still * (1f - relax) * w;
        f.leftLeg().py -= 1.0f * still * (1f - relax) * w;

        float aw = w * arms;
        arm(leadArm, s, Mth.lerp(run, -0.60f, -0.87f), leadArm == f.rightArm() ? 0.17f : -0.17f, 0.45f, aw);
        arm(trailArm, s, Mth.lerp(run, -0.40f, 0.87f), trailArm == f.rightArm() ? 0.25f : -0.25f, 0.40f, aw);

        // Upright on the way up, chest leaning in on the way down.
        float lean = (AngleMath.clamp(m.vertical, -0.2f, 0.2f) * -100f + 20f) * Mth.DEG_TO_RAD;
        Rigging.leanTorso(f, s, 0.55f * lean * w);
        f.head().rx -= 0.45f * lean * w;
        // Counter-rotation: the lead leg's shoulder swings back.
        Rigging.twistUpper(f, side * 0.30f * run * w);
    }

    private static void fall(MotionState m, PoseFrame f, Signs s, float w, float arms) {
        float t = m.time;
        float a = Mth.sin(t * 7.0f + m.seed), b = Mth.sin(t * 7.7f + m.seed + 1.7f);
        float c = Mth.sin(t * 5.5f + m.seed);
        float aw = w * arms;
        // Arms thrown up in a Y, flailing.
        arm(f.rightArm(), s, -2.70f + 0.25f * a, 0.55f + 0.15f * b, 0.35f + 0.25f * Math.max(0f, b), aw);
        arm(f.leftArm(), s, -2.70f + 0.25f * b, -0.55f - 0.15f * a, 0.35f + 0.25f * Math.max(0f, a), aw);
        // Legs kick BACK underneath the body, knees bent.
        leg(f.rightLeg(), s, 0.15f + 0.38f * c, 0.55f + 0.45f * Math.max(0f, c), w);
        leg(f.leftLeg(), s, 0.15f - 0.38f * c, 0.55f + 0.45f * Math.max(0f, -c), w);
        Rigging.legsOut(f, 0.12f * w);
        Rigging.leanTorso(f, s, -0.08f * w);
    }

    /**
     * Dropping from a height with a sword: both hands on the hilt raised over the head, the blade
     * pointing up, knees drawn up and the back rounded over it, ready to drive it into the
     * ground on landing (see ReactionModule#plantBlade, which starts from this pose).
     */
    private static void bladeFall(MotionState m, PoseFrame f, Signs s, float w, float arms, float side, boolean two) {
        float t = m.time;
        float a = Mth.sin(t * 6.0f + m.seed), c = Mth.sin(t * 4.5f + m.seed + 1f);
        float aw = w * arms;
        PartPose main = side > 0 ? f.rightArm() : f.leftArm();
        PartPose off = side > 0 ? f.leftArm() : f.rightArm();
        main.override(-2.55f + 0.05f * a, -side * 0.30f, side * 0.10f, aw);
        if (two) {
            off.override(-2.45f + 0.05f * a, side * 0.62f, -side * 0.10f, aw);
        } else {
            off.override(-1.30f + 0.10f * a, 0f, -side * (0.95f + 0.10f * a), aw);
        }
        main.overrideBend(s.elbow() * 0.35f, aw);
        off.overrideBend(s.elbow() * 0.45f, aw);
        // Blade straight up over the head (turned in the fist, see ReactionModule#plantBlade).
        m.itemTiltTarget = Math.max(m.itemTiltTarget, 0.95f * aw);
        m.armsHeld = Math.max(m.armsHeld, aw);
        // Knees drawn up, one ahead of the other, ready to take the landing on one knee.
        PartPose lead = side > 0 ? f.leftLeg() : f.rightLeg();
        PartPose rear = side > 0 ? f.rightLeg() : f.leftLeg();
        lead.override(-1.05f + 0.08f * c, 0f, -side * 0.12f, w);
        lead.overrideBend(s.knee() * 1.35f, w);
        rear.override(-0.20f - 0.08f * c, 0f, side * 0.12f, w);
        rear.overrideBend(s.knee() * 1.10f, w);
        // Rounded over the blade, head down watching the landing spot.
        Rigging.leanTorso(f, s, 0.22f * w);
        f.head().rx += 0.35f * w;
        f.body().rx += 0.05f * a * w;
    }

    private static void skydive(MotionState m, PoseFrame f, Signs s, float w, float arms) {
        float t = m.time;
        float flutter = Mth.sin(t * 13f + m.seed), flutter2 = Mth.sin(t * 11f + m.seed + 2f);
        // Skydiver's arch: belly to the ground, arms spread wide and up, knees bent, shins raised.
        f.body().rx -= 1.15f * w;
        f.head().rx -= 1.0f * w;
        Rigging.leanTorso(f, s, -0.25f * w);
        float aw = w * arms;
        arm(f.rightArm(), s, -0.45f + 0.06f * flutter, 1.75f + 0.06f * flutter2, 0.30f, aw);
        arm(f.leftArm(), s, -0.45f + 0.06f * flutter2, -1.75f - 0.06f * flutter, 0.30f, aw);
        leg(f.rightLeg(), s, 0.15f + 0.05f * flutter2, 1.25f, w);
        leg(f.leftLeg(), s, 0.15f + 0.05f * flutter, 1.25f, w);
        Rigging.legsOut(f, 0.24f * w);
    }

    private static void leg(PartPose leg, Signs s, float x, float knee, float w) {
        leg.targetX(x, w);
        leg.targetY(0f, w);
        leg.bend += s.knee() * knee * w;
    }

    private static void arm(PartPose arm, Signs s, float x, float z, float elbow, float w) {
        if (w <= 0.001f) return;
        arm.targetX(x, w);
        arm.targetY(0f, w);
        arm.targetZ(z, w);
        arm.bend += s.elbow() * elbow * w;
    }
}
