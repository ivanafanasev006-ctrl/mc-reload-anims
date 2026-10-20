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

    private static void mount(LivingEntity mount, MotionState m, PoseFrame f, Signs s, float w, float pt) {
        float speed = Math.min(mount.walkAnimation.speed(pt), 1f);
        float phase = mount.walkAnimation.position(pt) * 0.6662f;
        // Rising and settling twice per stride, rocking front to back.
        float bounce = Math.abs(Mth.sin(phase));
        f.body().py += (0.05f * bounce - 0.02f) * speed * w;
        f.body().rx += (-0.10f * speed + 0.07f * Mth.sin(phase * 2f) * speed) * w;
        Rigging.leanTorso(f, s, (0.08f + 0.10f * speed) * w);
        f.head().rx -= (0.12f + 0.12f * speed) * w;
        f.body().rz += 0.03f * Mth.sin(phase) * speed * w;

        // Hands on the reins, bobbing with the gait.
        float arms = w * m.armsFree.v;
        float bob = 0.12f * Mth.sin(phase * 2f + 0.6f) * speed;
        f.rightArm().targetX(-0.80f + bob, arms);
        f.leftArm().targetX(-0.80f + bob, arms);
        f.rightArm().targetY(-0.25f, arms);
        f.leftArm().targetY(0.25f, arms);
        f.rightArm().targetZ(0.05f, arms);
        f.leftArm().targetZ(-0.05f, arms);
        Rigging.elbows(f, s, 1.05f * arms);

        // Knees grip the saddle (on top of vanilla's riding legs).
        f.rightLeg().bend += s.knee() * (0.55f + 0.10f * bounce * speed) * w;
        f.leftLeg().bend += s.knee() * (0.55f + 0.10f * bounce * speed) * w;
    }
}
