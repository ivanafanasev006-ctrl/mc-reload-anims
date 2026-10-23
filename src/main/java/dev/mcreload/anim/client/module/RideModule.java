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
            boolean paddling = boat.getPaddleState(0) || boat.getPaddleState(1);
            idleInSeat(m, f, s, w, paddling || m.horizontalSpeed > 0.02f, true);
        } else if (vehicle instanceof net.minecraft.world.entity.vehicle.AbstractMinecart cart) {
            minecart(cart, m, f, s, w, pt);
            idleInSeat(m, f, s, w, m.horizontalSpeed > 0.01f, false);
        } else if (vehicle instanceof LivingEntity mount) {
            mount(mount, m, f, s, w, pt, dev.mcreload.anim.ride.TwoRiders.isBehind(rig.player));
        } else if (vehicle != null) {
            // Minecart and other seats: relaxed, hands on knees, swaying with movement.
            float arms = w * m.armsFree.v;
            f.rightArm().targetX(-0.65f, arms);
            f.leftArm().targetX(-0.65f, arms);
            Rigging.elbows(f, s, 0.45f * arms);
            f.body().rz += 0.04f * Mth.sin(m.time * 2.1f + m.seed) * w;
        }
    }

    /**
     * Riding a minecart: hands on the rim, rocking with every bump, leaning into the sideways
     * pull and against speeding up or braking; at full speed, both arms thrown up in the air.
     */
    private static void minecart(net.minecraft.world.entity.vehicle.AbstractMinecart cart, MotionState m, PoseFrame f,
                                 Signs s, float w, float pt) {
        float arms = w * m.armsFree.v;
        float v = m.horizontalSpeed;
        float moving = Mth.clamp(v / 0.05f, 0f, 1f);
        float fast = Mth.clamp((v - 0.30f) / 0.10f, 0f, 1f);
        // Hands on the rim either side.
        f.rightArm().override(-0.40f, 0f, 0.62f, arms);
        f.leftArm().override(-0.40f, 0f, -0.62f, arms);
        f.rightArm().overrideBend(s.elbow() * 0.45f, arms);
        f.leftArm().overrideBend(s.elbow() * 0.45f, arms);
        // Rattling along: little jolts and a rock from side to side.
        float rattle = Mth.sin(m.time * 23f + m.seed) * Mth.sin(m.time * 7.1f) * moving;
        f.body().rz += (0.03f * rattle + 0.04f * Mth.sin(m.time * 2.3f + m.seed) * moving) * w;
        f.head().rz -= 0.02f * rattle * w;
        // Pulled sideways on the bends, thrown back when it speeds up, forward when it brakes.
        f.body().rz -= Mth.clamp(m.strafe.v * 2.5f, -0.20f, 0.20f) * w;
        Rigging.leanTorso(f, s, (-0.35f * m.swayF.x - 0.10f * moving) * w);
        f.head().rx += 0.15f * m.swayF.x * w;
        // Full speed: arms up, whooping.
        if (fast > 0.001f) {
            float wave = Mth.sin(m.time * 6f);
            f.rightArm().override(-2.85f + 0.10f * wave, 0f, 0.35f, arms * fast);
            f.leftArm().override(-2.85f - 0.10f * wave, 0f, -0.35f, arms * fast);
            f.rightArm().overrideBend(s.elbow() * 0.15f, arms * fast);
            f.leftArm().overrideBend(s.elbow() * 0.15f, arms * fast);
            f.head().rx -= 0.20f * fast * w;
            Rigging.leanTorso(f, s, -0.12f * fast * w);
        }
    }

    // ------------------------------------------------------------------ sitting about in a boat or cart

    private static final int SEAT_REST = 0, SEAT_LEAN_BACK = 1, SEAT_LOOK = 2, SEAT_STRETCH = 3, SEAT_SIDE = 4,
            SEAT_DRUM = 5, SEAT_COUNT = 6;
    private static final float SEAT_BLOCK = 5.5f;

    /**
     * Sitting still in a boat or minecart: every few seconds a different thing to do, cross-faded:
     * resting, leaning back with the hands behind the head, looking about shading the eyes,
     * stretching, leaning over the side (a hand trailing in the water from a boat), drumming the
     * fingers on the rim.
     */
    private static void idleInSeat(MotionState m, PoseFrame f, Signs s, float w, boolean busy, boolean boat) {
        if (busy) {
            m.rideIdleStart = m.time;
            return;
        }
        float T = m.since(m.rideIdleStart) - 1.5f;
        if (T <= 0f) return;
        int k = (int) (T / SEAT_BLOCK);
        float in = T - k * SEAT_BLOCK;
        int cur = seatPick(k, m.seed), prev = k == 0 ? SEAT_REST : seatPick(k - 1, m.seed);
        float blend = dev.mcreload.anim.client.math.AngleMath.ease(in / 0.8f);
        float aw = w * m.armsFree.v;
        float start = dev.mcreload.anim.client.math.AngleMath.ease(T / 0.8f);
        seatPose(f, s, m, prev, w * (1f - blend) * start, aw * (1f - blend) * start, in + SEAT_BLOCK, boat);
        seatPose(f, s, m, cur, w * blend * start, aw * blend * start, in, boat);
    }

    private static int seatPick(int k, float seed) {
        int h = (int) ((k * 2654435761L + (long) (seed * 1000f)) >>> 9);
        int p = Math.floorMod(h, SEAT_COUNT);
        if (k > 0) {
            int q = Math.floorMod((int) (((k - 1) * 2654435761L + (long) (seed * 1000f)) >>> 9), SEAT_COUNT);
            if (p == q) p = (p + 1) % SEAT_COUNT;
        }
        return p;
    }

    private static void seatPose(PoseFrame f, Signs s, MotionState m, int v, float w, float aw, float t, boolean boat) {
        if (w <= 0.001f) return;
        float side = m.mainRight ? 1f : -1f;
        PartPose main = side > 0 ? f.rightArm() : f.leftArm();
        PartPose off = side > 0 ? f.leftArm() : f.rightArm();
        switch (v) {
            case SEAT_LEAN_BACK -> {
                f.rightArm().override(-2.85f, -0.25f, 0.55f, aw);
                f.leftArm().override(-2.85f, 0.25f, -0.55f, aw);
                f.rightArm().overrideBend(s.elbow() * 1.75f, aw);
                f.leftArm().overrideBend(s.elbow() * 1.75f, aw);
                Rigging.leanTorso(f, s, -0.30f * w);
                f.head().rx -= 0.15f * w;
            }
            case SEAT_LOOK -> {
                float look = Mth.sin(t * 0.9f);
                main.override(-2.30f, -side * 0.45f, side * 0.25f, aw);
                main.overrideBend(s.elbow() * 1.50f, aw);
                f.head().ry += 0.70f * look * w;
                f.head().rx -= 0.10f * w;
                Rigging.twistUpper(f, -0.15f * look * w);
            }
            case SEAT_STRETCH -> {
                float up = Mth.sin(Mth.PI * Mth.clamp(t / 3.5f, 0f, 1f));
                f.rightArm().override(-2.95f, 0f, 0.30f, aw * up);
                f.leftArm().override(-2.95f, 0f, -0.30f, aw * up);
                f.rightArm().overrideBend(0f, aw * up);
                f.leftArm().overrideBend(0f, aw * up);
                Rigging.leanTorso(f, s, -0.20f * up * w);
                f.body().rz += 0.10f * Mth.sin(t * 1.6f) * up * w;
                f.head().rx -= 0.25f * up * w;
            }
            case SEAT_SIDE -> {
                // Lean over the side; from a boat a hand trails in the water.
                f.body().rz += side * 0.22f * w;
                f.head().rz -= side * 0.10f * w;
                f.head().rx += 0.35f * w;
                main.override(boat ? 0.10f : -0.30f, 0f, side * (boat ? 0.95f : 0.70f), aw);
                main.overrideBend(s.elbow() * (boat ? 0.10f + 0.10f * Mth.sin(t * 2f) : 0.40f), aw);
                off.override(-0.40f, 0f, -side * 0.60f, aw);
                off.overrideBend(s.elbow() * 0.45f, aw);
            }
            case SEAT_DRUM -> {
                // Bored: fingers drumming on the rim, the head bobbing to a tune.
                float tap = Math.abs(Mth.sin(t * 9f));
                main.override(-0.55f + 0.10f * tap, 0f, side * 0.55f, aw);
                main.overrideBend(s.elbow() * (0.60f + 0.15f * tap), aw);
                off.override(-0.40f, 0f, -side * 0.62f, aw);
                off.overrideBend(s.elbow() * 0.45f, aw);
                f.head().rx += 0.06f * Mth.sin(t * 4.5f) * w;
                f.head().rz += 0.05f * Mth.sin(t * 2.25f) * w;
            }
            default -> {
                // Resting: just breathing, the odd glance around.
                f.head().ry += 0.30f * Mth.sin(t * 0.5f) * w;
                f.body().rz += 0.02f * Mth.sin(t * 1.4f) * w;
            }
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
     *   <li>gallop: up out of the saddle, leaning well forward, the thighs swinging back with the
     *       chest (the knees stay off the belly), hands forward along the neck, rocking with the
     *       stride;</li>
     *   <li>the horse jumping: folding forward over the neck on take-off with the hands following
     *       the mouth, staying with it in the air, then sitting down deep and absorbing the
     *       landing;</li>
     *   <li>leaning into turns; leaning forward onto the neck when the horse rears; giving the
     *       reins when it puts its head down to graze;</li>
     *   <li>riding behind someone (two on one horse): the same seat, hands round their waist.</li>
     * </ul>
     */
    private static void mount(LivingEntity mount, MotionState m, PoseFrame f, Signs s, float w, float pt, boolean behind) {
        // Gait from how fast we really go (the mount's leg animation saturates at a horse's
        // normal speed, so it can't tell a trot from a gallop): blocks per tick.
        float v = m.speed.v;
        float phase = mount.walkAnimation.position(pt) * 0.6662f;
        float moving = Mth.clamp(v / 0.04f, 0f, 1f);
        float gallop = Mth.clamp((v - 0.26f) / 0.10f, 0f, 1f);
        float trot = Mth.clamp((v - 0.11f) / 0.08f, 0f, 1f) * (1f - gallop);
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

        // A heavy, wide-bodied mount (DragN's Livestock Overhaul...): see compat.wideMounts.
        float wide = isWideMount(mount) ? 1f : 0f;

        // Leaning: into speed, folding over the neck on a jump, onto the neck when rearing,
        // sitting back for the landing.
        float forward = 0.05f * walk + 0.15f * trot + 0.39f * gallop + 0.34f * rise + 0.21f * drop + 0.55f * rear
                - 0.18f * landing + 0.08f * still * graze;

        // ---- legs: astride, down round the horse's sides and a little forward, the knees well
        // clear of the belly (at least 2 px in every gait, checked on the model): the thighs swing
        // back as the chest leans forward instead of the knees folding up under it, and a bigger
        // horse's flanks take a little of the legs (they may sink into it a few pixels).
        float fold = 0.10f * trot + 0.05f * gallop;
        float grip = 0.05f * Math.abs(Mth.sin(phase)) * moving;
        float thigh = -0.75f + 0.20f * gallop + 0.15f * jump + 0.50f * forward;
        float splay = Mth.lerp(wide, 0.52f, 0.72f), turnOut = Mth.lerp(wide, 0.25f, 0.45f);
        f.rightLeg().override(thigh, turnOut, splay - grip, w);
        f.leftLeg().override(thigh, -turnOut, -splay + grip, w);
        f.rightLeg().overrideBend(s.knee() * (0.70f + fold), w);
        f.leftLeg().overrideBend(s.knee() * (0.70f + fold), w);
        if (wide > 0f) {
            // The seat comes down onto the saddle, the hips a little wider apart.
            f.rightLeg().px -= 0.8f * wide * w;
            f.leftLeg().px += 0.8f * wide * w;
            f.body().py -= 0.09f * wide * w;
        }

        // ---- body
        float post = Math.max(0f, Mth.sin(phase));                 // trot: up every other beat
        float lift = 0.08f * post * trot + 0.10f * gallop + 0.10f * rise - 0.07f * landing;
        f.body().py += lift * w;
        // Hips swinging with the walk.
        f.body().rz += 0.05f * Mth.sin(phase) * walk * w;
        f.body().ry += 0.04f * Mth.sin(phase) * walk * w;
        // Rocking-horse motion at the gallop.
        f.body().rx += (0.07f * Mth.sin(phase * 2f) * gallop + 0.03f * Mth.sin(phase * 2f) * trot) * w;
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
        // A big horse: the hands hold the reins low in front, wider apart, the upper arms down by
        // the sides and the elbows bent (the reins run from its mouth down to the saddle).
        float armX = Mth.lerp(wide, -0.70f, -0.42f) - reach + give;
        float elbow = Mth.clamp(Mth.lerp(wide, 0.95f, 0.85f) - 0.45f * gallop - 0.6f * Math.max(rise, rear) - 0.4f * graze,
                0.15f, 1.2f);
        float armIn = Mth.lerp(wide, 0.30f, 0.20f), armOut = Mth.lerp(wide, 0.02f, 0.04f);
        // Every eight seconds or so, standing, the free hand pats the neck.
        float patT = (m.time + m.seed * 3f) % 8f;
        float pat = behind ? 0f : still * (1f - graze) * AngleMath.ease((patT - 5.6f) / 0.3f) * (1f - AngleMath.ease((patT - 7.2f) / 0.3f));
        float tap = pat * Math.abs(Mth.sin(patT * 9f));
        f.rightArm().override(armX, -armIn, armOut, arms);
        f.leftArm().override(Mth.lerp(pat, armX, Mth.lerp(wide, -1.05f, -1.25f) + 0.15f * tap), Mth.lerp(pat, armIn, 0.15f),
                -armOut, arms);
        f.rightArm().overrideBend(s.elbow() * elbow, arms);
        f.leftArm().overrideBend(s.elbow() * Mth.lerp(pat, elbow, 0.35f), arms);
        Rigging.leanTorso(f, s, 0.10f * pat * w);

        // Riding behind someone (two on one horse, see TwoRiders): both hands hold on to the
        // rider's waist (fitted on the model: the fists at its sides, the elbows nearly straight).
        if (behind) {
            f.rightArm().override(-0.98f, -0.19f, 0.06f, arms);
            f.leftArm().override(-0.98f, 0.19f, -0.06f, arms);
            f.rightArm().overrideBend(s.elbow() * 0.17f, arms);
            f.leftArm().overrideBend(s.elbow() * 0.17f, arms);
            return;
        }

        // Pig / strider steered with a carrot or fungus on a stick: that arm holds it out in
        // front of the mount's nose, dangling the bait; the other hand on the saddle.
        boolean stickR = isBaitStick(m, true), stickL = isBaitStick(m, false);
        if (stickR || stickL) {
            PartPose stickArm = stickR ? f.rightArm() : f.leftArm();
            PartPose other = stickR ? f.leftArm() : f.rightArm();
            float out = stickR ? 1f : -1f;
            stickArm.override(-1.25f + 0.06f * Mth.sin(m.time * 3f), -out * 0.10f, out * 0.05f, arms);
            stickArm.overrideBend(s.elbow() * 0.25f, arms);
            other.override(-0.35f, 0f, -out * 0.25f, arms);
            other.overrideBend(s.elbow() * 0.60f, arms);
        }
    }

    /** A mount listed in compat.wideMounts, by its entity id or its mod id. */
    private static boolean isWideMount(Entity mount) {
        java.util.Set<String> list = Cfg.wideMounts;
        if (list.isEmpty()) return false;
        net.minecraft.resources.ResourceLocation id = net.minecraft.world.entity.EntityType.getKey(mount.getType());
        return list.contains(id.getNamespace()) || list.contains(id.toString());
    }

    private static boolean isBaitStick(MotionState m, boolean right) {
        net.minecraft.world.entity.player.Player p = m.playerRef();
        net.minecraft.world.entity.HumanoidArm mainArm = p.getMainArm();
        boolean mainIsRight = mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT;
        net.minecraft.world.item.ItemStack st = (right == mainIsRight) ? p.getMainHandItem() : p.getOffhandItem();
        return st.getItem() instanceof net.minecraft.world.item.FoodOnAStickItem<?>;
    }
}
