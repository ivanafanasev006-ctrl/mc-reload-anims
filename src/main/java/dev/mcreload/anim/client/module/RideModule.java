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

    /**
     * In the saddle: thighs along the horse's flanks, shins hanging straight down to the stirrups,
     * a straight back that rises and settles with the gait (posting at a trot, leaning forward
     * and moving with the neck at a gallop), hands low and together on the reins, giving with
     * the horse's head.
     */
    private static void mount(LivingEntity mount, MotionState m, PoseFrame f, Signs s, float w, float pt) {
        float speed = Math.min(mount.walkAnimation.speed(pt), 1f);
        float phase = mount.walkAnimation.position(pt) * 0.6662f;
        float gallop = Mth.clamp((speed - 0.55f) / 0.35f, 0f, 1f);

        // Legs: astride, knees down, a little grip.
        float grip = 0.06f * Math.abs(Mth.sin(phase)) * speed;
        f.rightLeg().override(-1.00f, 0.25f, 0.42f - grip, w);
        f.leftLeg().override(-1.00f, -0.25f, -0.42f + grip, w);
        f.rightLeg().overrideBend(s.knee() * (1.00f + 0.10f * speed), w);
        f.leftLeg().overrideBend(s.knee() * (1.00f + 0.10f * speed), w);

        // Body: rising and settling with the stride, leaning into speed.
        float post = Math.abs(Mth.sin(phase));
        f.body().py += (0.06f * post - 0.02f) * speed * w;
        f.body().rx -= (0.06f * speed + 0.20f * gallop) * w;
        f.body().rx += 0.05f * Mth.sin(phase * 2f) * speed * w;
        Rigging.leanTorso(f, s, (0.05f + 0.10f * speed + 0.15f * gallop) * w);
        f.head().rx -= (0.05f * speed + 0.25f * gallop) * w;
        f.body().rz += 0.03f * Mth.sin(phase) * speed * w;

        // Hands low and together on the reins, following the horse's head.
        float arms = w * m.armsFree.v;
        float give = 0.10f * Mth.sin(phase * 2f + 0.6f) * speed + 0.15f * gallop * Mth.sin(phase * 2f);
        f.rightArm().override(-0.75f + give, -0.30f, 0.02f, arms);
        f.leftArm().override(-0.75f + give, 0.30f, -0.02f, arms);
        f.rightArm().overrideBend(s.elbow() * (0.95f - 0.25f * gallop), arms);
        f.leftArm().overrideBend(s.elbow() * (0.95f - 0.25f * gallop), arms);
    }
}
