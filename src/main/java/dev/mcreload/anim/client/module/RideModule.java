package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.pose.PartPose;
import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import dev.mcreload.anim.client.math.AngleMath;
import net.minecraft.world.entity.vehicle.Boat;

/**
 * Riding. Vanilla only sits the player down; here:
 * <ul>
 *   <li>Boat: real rowing, driven by the boat's own paddle phase. Each arm pulls its oar, elbows
 *       bend on the recovery, the torso rocks forward and back with the stroke.</li>
 *   <li>Horse and other mounts: the rider moves with the mount's gait, rising and settling in the
 *       saddle, leaning into speed, knees gripping, hands holding the reins and bobbing.</li>
 * </ul>
 */
final class RideModule implements AnimModule {
    @Override
    public boolean enabled() {
        return Cfg.locomotion;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float w = m.rideW.v;
        if (w <= 0.001f) return;
        Entity vehicle = rig.player.getVehicle();
        if (vehicle instanceof Boat boat) {
            boat(boat, m, f, s, w, pt);
        } else if (vehicle instanceof LivingEntity mount) {
            mount(mount, m, f, s, w, pt);
        } else if (vehicle != null) {
            // Minecart and other seats: relaxed, hands on knees, swaying with movement.
            float arms = w * m.armsFree.v;
            f.rightArm().targetX(-0.65f, arms);
            f.leftArm().targetX(-0.65f, arms);
            Rigging.elbows(f, s, 0.45f * arms);
            f.body().rz += 0.04f * Mth.sin(m.time * 2.1f + m.seed) * w;
        }
    }

    private static void boat(Boat boat, MotionState m, PoseFrame f, Signs s, float w, float pt) {
        float arms = w * m.armsFree.v;
        float lean = 0f;
        for (int side = 0; side < 2; side++) {
            PartPose arm = side == 0 ? f.leftArm() : f.rightArm();
            float out = side == 0 ? -1f : 1f;
            if (boat.getPaddleState(side)) {
                float r = boat.getRowingTime(side, pt);
                // Same phase vanilla uses to swing the oar model.
                float pull = (Mth.sin(-r) + 1f) * 0.5f;          // 0 = catch (arms forward), 1 = finish
                float sweep = (Mth.sin(-r + 1f) + 1f) * 0.5f;
                arm.targetX(-1.35f + 0.85f * pull, arms);
                arm.targetY(out * (0.25f - 0.35f * sweep), arms);
                arm.targetZ(out * 0.30f, arms);
                arm.bend += s.elbow() * (0.25f + 0.95f * pull) * arms;
                lean += (0.5f - pull) * 0.5f;
            } else {
                // Resting on the oar handle.
                arm.targetX(-0.85f, arms);
                arm.targetY(0f, arms);
                arm.targetZ(out * 0.30f, arms);
                arm.bend += s.elbow() * 0.55f * arms;
            }
        }
        // Reach forward at the catch, lean back at the finish.
        Rigging.leanTorso(f, s, lean * 0.35f * w);
        f.head().rx -= lean * 0.20f * w;
    }

    /**
     * In the saddle, moving with the mount's gait:
     * <ul>
     *   <li>standing: a relaxed seat, hands resting low on the reins, looking about, and every so
     *       often a pat on the neck;</li>
     *   <li>walk: the hips follow the horse's back side to side, hands soft;</li>
     *   <li>trot: posting, rising out of the saddle every other beat;</li>
     *   <li>gallop: up in a jockey's two-point seat, leaning well forward, knees folded, hands
     *       forward along the neck, rocking with the stride;</li>
     *   <li>the horse jumping: folding forward over the neck on take-off with the hands following
     *       the mouth, staying with it in the air, then sitting down deep and absorbing the
     *       landing;</li>
     *   <li>leaning into turns; leaning forward onto the neck when the horse rears; giving the
     *       reins when it puts its head down to graze.</li>
     * </ul>
     */
    private static void mount(LivingEntity mount, MotionState m, PoseFrame f, Signs s, float w, float pt) {
        float speed = Math.min(mount.walkAnimation.speed(pt), 1.2f);
        float phase = mount.walkAnimation.position(pt) * 0.6662f;
        float moving = Mth.clamp(speed / 0.12f, 0f, 1f);
        float trot = Mth.clamp((speed - 0.30f) / 0.20f, 0f, 1f) * (1f - Mth.clamp((speed - 0.65f) / 0.20f, 0f, 1f));
        float gallop = Mth.clamp((speed - 0.65f) / 0.20f, 0f, 1f);
        float walk = moving * (1f - trot) * (1f - gallop);
        float still = 1f - moving;

        // The mount in the air (jumping / dropping) and touching down.
        float vy = m.mountVy.v;
        float rise = Mth.clamp(vy / 0.35f, 0f, 1f);
        float drop = Mth.clamp(-vy / 0.45f, 0f, 1f);
        float jump = Math.max(rise, drop * 0.8f);
        float landing = Math.max(0f, m.mountLand.x);
        float rear = 0f, graze = 0f;
        if (mount instanceof AbstractHorse horse) {
            rear = horse.getStandAnim(pt);
            graze = horse.getEatAnim(pt) * still;
        }
        float turn = Mth.clamp(m.mountTurn.v / 12f, -1f, 1f) * moving;
        float arms = w * m.armsFree.v;

        // ---- legs: astride, the knees folding up into the stirrups at speed
        float fold = 0.15f * trot + 0.45f * gallop + 0.40f * jump;
        float grip = 0.05f * Math.abs(Mth.sin(phase)) * moving;
        float thigh = -1.00f + 0.20f * gallop + 0.15f * jump;
        f.rightLeg().override(thigh, 0.25f, 0.42f - grip, w);
        f.leftLeg().override(thigh, -0.25f, -0.42f + grip, w);
        f.rightLeg().overrideBend(s.knee() * (1.00f + fold), w);
        f.leftLeg().overrideBend(s.knee() * (1.00f + fold), w);

        // ---- body
        float post = Math.max(0f, Mth.sin(phase));                 // trot: up every other beat
        float lift = 0.08f * post * trot + 0.06f * gallop + 0.10f * rise - 0.07f * landing;
        f.body().py += lift * w;
        // Hips swinging with the walk.
        f.body().rz += 0.05f * Mth.sin(phase) * walk * w;
        f.body().ry += 0.04f * Mth.sin(phase) * walk * w;
        // Rocking-horse motion at the gallop.
        f.body().rx += (0.07f * Mth.sin(phase * 2f) * gallop + 0.03f * Mth.sin(phase * 2f) * trot) * w;
        // Leaning: into speed, folding over the neck on a jump, onto the neck when rearing,
        // sitting back for the landing.
        float forward = 0.04f * walk + 0.12f * trot + 0.30f * gallop + 0.45f * rise + 0.25f * drop + 0.55f * rear
                - 0.18f * landing + 0.08f * still * graze;
        f.body().rx -= forward * 0.45f * w;
        Rigging.leanTorso(f, s, forward * 0.70f * w);
        f.head().rx -= forward * 0.55f * w;
        // Into turns.
        f.body().rz -= 0.14f * turn * w;
        f.head().rz += 0.06f * turn * w;

        // ---- standing still: look about now and then
        float look = Mth.sin(m.time * 0.45f + m.seed) * Mth.clamp(Mth.sin(m.time * 0.21f + m.seed * 2f) * 2f, 0f, 1f);
        f.head().ry += 0.45f * look * still * w;
        Rigging.twistUpper(f, 0.10f * look * still * w);

        // ---- hands on the reins, following the horse's mouth
        float give = 0.10f * Mth.sin(phase * 2f + 0.6f) * (walk + trot) + 0.12f * gallop * Mth.sin(phase * 2f);
        float reach = 0.30f * gallop + 0.55f * rise + 0.35f * drop + 0.50f * rear + 0.40f * graze;
        float armX = -0.70f - reach + give;
        float elbow = Mth.clamp(0.95f - 0.45f * gallop - 0.6f * Math.max(rise, rear) - 0.4f * graze, 0.15f, 1.2f);
        // Every eight seconds or so, standing, the free hand pats the neck.
        float patT = (m.time + m.seed * 3f) % 8f;
        float pat = still * (1f - graze) * AngleMath.ease((patT - 5.6f) / 0.3f) * (1f - AngleMath.ease((patT - 7.2f) / 0.3f));
        float tap = pat * Math.abs(Mth.sin(patT * 9f));
        f.rightArm().override(armX, -0.30f, 0.02f, arms);
        f.leftArm().override(Mth.lerp(pat, armX, -1.05f + 0.15f * tap), Mth.lerp(pat, 0.30f, 0.15f), -0.02f, arms);
        f.rightArm().overrideBend(s.elbow() * elbow, arms);
        f.leftArm().overrideBend(s.elbow() * Mth.lerp(pat, elbow, 0.35f), arms);
        Rigging.leanTorso(f, s, 0.10f * pat * w);
    }
}
