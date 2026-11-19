package dev.mcreload.moves.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mcreload.moves.MobPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraft.world.phys.Vec3;
import dev.mcreload.moves.Stamina;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.GuardAnswers;
import net.minecraftforge.common.MinecraftForge;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The mobs' poses in our moves (MobPose): set by the server for some ticks, eased in over a few. The whole body (a lean
 * from the feet, a turn about, a lift) through the pose stack before the mob is drawn, as StoopFx leans it; the limbs of a
 * humanoid model right after the vanilla set-up (mixin/LivingEntityRendererMixin). A mob dying in a pose keeps it: the
 * vanilla tip onto its side is not drawn.
 */
public final class MobPoses {
    private static final int EASE = 3;
    private static final float D = Mth.DEG_TO_RAD;

    private record On(MobPose pose, long start, int ticks, boolean turned) {}

    private static final Map<Integer, On> ON = new HashMap<>();
    private static final Map<LivingEntity, Integer> PUSHED = new WeakHashMap<>();

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(MobPoses::pre);
        MinecraftForge.EVENT_BUS.addListener(MobPoses::post);
        MinecraftForge.EVENT_BUS.addListener(MobPoses::holderFacing);
    }

    /**
     * Holding the living shield the body turns with the look at once (no vanilla lag of the body behind the head, no jerk
     * when it catches up), so the arms, the blade and the mob on it all go round together (author 19.11: "you look left,
     * your body turns left, and it stays still").
     */
    private static void holderFacing(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        Stamina s = Stamina.of(p);
        if (s.move != Move.HOSTAGE_HOLD && s.move != Move.GUARD_GRAB && s.move != Move.HOSTAGE_KICK) return;
        p.yBodyRotO = p.yRotO;
        p.yBodyRot = p.getYRot();
        p.yHeadRotO = p.yRotO;
        p.yHeadRot = p.getYRot();
    }

    /** The server: this mob takes this pose for {@code ticks} (turned: it faces the other way meanwhile). */
    public static void set(int entity, MobPose pose, int ticks, boolean turned) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        ON.values().removeIf(o -> o.start + o.ticks < now - 40);
        if (pose == MobPose.NONE) ON.remove(entity);
        else ON.put(entity, new On(pose, now, ticks, turned));
    }

    static void clear() {
        ON.clear();
    }

    private static On on(LivingEntity e) {
        if (ON.isEmpty() || e instanceof Player) return null;
        On o = ON.get(e.getId());
        if (o == null) return null;
        long now = e.level().getGameTime();
        return now > o.start + o.ticks ? null : o;
    }

    private static float age(On o, LivingEntity e, float partial) {
        return Math.max(0f, e.level().getGameTime() - o.start + partial);
    }

    /** How much of the pose is on: eased in from its start, out over its last ticks (the lying ones stay). */
    private static float weight(On o, float t) {
        // the time stop's freeze: slowing into the pose over a second (author: "their moves slow down within 1 s")
        float in = Mth.clamp(t / (frozen(o.pose) ? FREEZE : EASE), 0f, 1f);
        boolean stays = o.pose == MobPose.FALL_FACE || o.pose == MobPose.THROWN || o.pose == MobPose.BLASTED
                || o.pose == MobPose.FALL_BACK || o.pose == MobPose.STUMBLE;
        float out = stays ? 1f : Mth.clamp((o.ticks - t) / EASE, 0f, 1f);
        float k = Math.min(in, out);
        return k * k * (3 - 2 * k);
    }

    private static final int FREEZE = 20;

    private static boolean frozen(MobPose p) {
        return p == MobPose.FROZEN_RUN || p == MobPose.FROZEN_LEAP || p == MobPose.FROZEN_POUNCE || p == MobPose.FROZEN_RAGE;
    }

    /** Down from the feet like a plank, faster as it goes (from {@code t0}, over {@code ticks}), to {@code to} degrees. */
    private static float fall(float t, float t0, float ticks, float to) {
        return to * (float) Math.pow(Mth.clamp((t - t0) / ticks, 0f, 1f), 2);
    }

    private static float smooth(float t, float from, float to) {
        float k = Mth.clamp((t - from) / (to - from), 0f, 1f);
        return k * k * (3 - 2 * k);
    }

    // ------------------------------------------------------------------ the whole body

    /** {lean forward (deg, from the feet), turn about (deg), lift (blocks)} at {@code t} ticks of the pose. */
    private static float[] body(On o, float t) {
        float turn = o.turned ? 180f : 0f;
        return switch (o.pose) {
            case CAUGHT -> new float[]{8f, turn, 0f};
            // turned about as the arm goes behind its back (half a second), bent over
            case TWISTED -> new float[]{24f * smooth(t, 2, 14), 180f * smooth(t, 0, 16), 0f};
            // a stumble, then down like a plank, faster as it goes; lying a little off the ground (its chest is thick)
            case FALL_FACE -> {
                float fall = t < 3 ? 12f * t / 3 : 12f + 78f * (float) Math.pow(Mth.clamp((t - 3) / 9f, 0f, 1f), 2);
                yield new float[]{fall, turn, 0.13f * smooth(t, 8, 12)};
            }
            case FLUNG -> new float[]{-28f * smooth(t, 0, 3) * (1f - smooth(t, 12, 20)), turn, 0f};
            // sagging back on the blade, lifted a little by it
            case HOSTAGE -> new float[]{-8f + Mth.sin(t * 0.9f) * 1.2f, turn, 0.06f};
            case THROWN -> new float[]{78f * smooth(t, 0, 4) + 12f * smooth(t, 14, 22), turn, 0.13f * smooth(t, 14, 22)};
            case FROZEN_RUN -> new float[]{12f, turn, 0f};
            // in a deep leap: stretched out forward, high off the ground
            case FROZEN_LEAP -> new float[]{38f, turn, 0.9f};
            // jumping down from two blocks up at the player
            case FROZEN_POUNCE -> new float[]{16f, turn, 1.4f};
            case FROZEN_RAGE -> new float[]{-12f, turn, 0f};
            // blown back high (its flight is the server's), onto its back
            case BLASTED -> new float[]{-30f * smooth(t, 0, 3) - 60f * smooth(t, 6, 18), turn, 0.13f * smooth(t, 14, 18)};
            case FALL_BACK -> new float[]{-8f * smooth(t, 0, 3) + fall(t, 3, 9, -82f), turn, 0.13f * smooth(t, 9, 12)};
            // a stagger, then onto its face
            case STUMBLE -> new float[]{10f * smooth(t, 0, 4) * (1 - smooth(t, 6, 8)) + 4f + fall(t, 8, 10, 86f), turn, 0.13f * smooth(t, 14, 18)};
            default -> new float[]{0f, 0f, 0f};
        };
    }

    private static void pre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity e = event.getEntity();
        On o = on(e);
        if (o == null) return;
        float t = age(o, e, event.getPartialTick()), w = weight(o, t);
        float[] b = body(o, t);
        float lean = b[0] * w, turn = b[1], lift = b[2] * w;
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        if (o.pose == MobPose.HOSTAGE) stuck(e, ps, event.getPartialTick());
        float yaw = Mth.rotLerp(event.getPartialTick(), e.yBodyRotO, e.yBodyRot);
        ps.translate(0, lift, 0);
        // the lean along the way it faces once turned; the turn about its middle (as a growing yaw turns a mob)
        ps.mulPose(Axis.YP.rotationDegrees(-(yaw + turn)));
        ps.mulPose(Axis.XP.rotationDegrees(lean));
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        int death = e.deathTime;
        e.deathTime = 0;
        PUSHED.put(e, death);
    }

    /**
     * The living shield drawn where its holder's blade has it this very frame, not where the server last said (a mob's
     * place comes every few ticks and is eased into: it trailed behind the player, author 19.11: "you become as one").
     */
    private static void stuck(LivingEntity e, PoseStack ps, float partial) {
        Player p = holder(e);
        if (p != null) {
            float yaw = p.getViewYRot(partial);
            Vec3 at = p.getPosition(partial).add(-Mth.sin(yaw * D) * GuardAnswers.SHIELD_AT, 0, Mth.cos(yaw * D) * GuardAnswers.SHIELD_AT);
            Vec3 drawn = e.getPosition(partial);
            ps.translate(at.x - drawn.x, at.y - drawn.y, at.z - drawn.z);
            e.yBodyRot = e.yBodyRotO = yaw;
            e.yHeadRot = e.yHeadRotO = yaw;
        }
    }

    /** The player holding this mob on the blade (the living shield), if any near. */
    private static Player holder(LivingEntity e) {
        for (Player p : e.level().players()) {
            Stamina s = Stamina.of(p);
            boolean holds = s.move == Move.HOSTAGE_HOLD || (s.move == Move.GUARD_GRAB && s.moveTick >= GuardAnswers.GRAB_TURN)
                    || (s.move == Move.HOSTAGE_KICK && s.moveTick < GuardAnswers.THROW_HIT);
            if (holds && p.distanceToSqr(e) <= 16.0) return p;
        }
        return null;
    }

    private static void post(RenderLivingEvent.Post<?, ?> event) {
        Integer death = PUSHED.remove(event.getEntity());
        if (death == null) return;
        event.getEntity().deathTime = death;
        event.getPoseStack().popPose();
    }

    // ------------------------------------------------------------------ the limbs

    /** Right after the model is set up the vanilla way (the mixin): a humanoid's limbs into the pose. */
    public static void limbs(LivingEntity e, EntityModel<?> model, float partial) {
        if (!(model instanceof HumanoidModel<?> h)) return;
        On o = on(e);
        if (o == null) return;
        float t = age(o, e, partial), w = weight(o, t);
        // head, body, right arm, left arm, right leg, left leg: x, y, z each (degrees)
        float[] p = limbs(o, t);
        if (p == null) return;
        set(h.head, p, 0, w);
        set(h.body, p, 3, w);
        set(h.rightArm, p, 6, w);
        set(h.leftArm, p, 9, w);
        set(h.rightLeg, p, 12, w);
        set(h.leftLeg, p, 15, w);
        if (o.pose == MobPose.HOSTAGE) inStep(e, h, partial);
        h.hat.copyFrom(h.head);
    }

    /**
     * The living shield walks with its holder (author 19.11: "in sync", not standing still while the player walks): its
     * legs in step with the player's (the same stride and pace, the other foot first, as it is ahead of him), its arms
     * hanging and swinging a little.
     */
    private static void inStep(LivingEntity e, HumanoidModel<?> h, float partial) {
        Player p = holder(e);
        if (p == null) return;
        float pos = p.walkAnimation.position(partial), speed = Math.min(1f, p.walkAnimation.speed(partial));
        float swing = Mth.cos(pos * 0.6662f) * 1.2f * speed;
        h.rightLeg.xRot += -swing;
        h.leftLeg.xRot += swing;
        h.rightArm.xRot += swing * 0.25f;
        h.leftArm.xRot += -swing * 0.25f;
    }

    private static void set(ModelPart part, float[] p, int i, float w) {
        part.xRot = Mth.lerp(w, part.xRot, p[i] * D);
        part.yRot = Mth.lerp(w, part.yRot, p[i + 1] * D);
        part.zRot = Mth.lerp(w, part.zRot, p[i + 2] * D);
    }

    private static float[] limbs(On o, float t) {
        float s = Mth.sin(t * 0.8f);
        return switch (o.pose) {
            // the right arm held out by the wrist, the other flung out for balance
            case CAUGHT -> new float[]{10, 0, 0, 4, 0, 0, -88, 0, 4, -30, 0, -14, -6, 0, 2, 10, 0, -2};
            // the right arm up behind the back, the left clawing the air, head down
            case TWISTED -> new float[]{22, 0, 0, 10, 0, 0, 42, 0, -38, -35 + s * 8, 0, -22, -12, 0, 3, 12, 0, -3};
            case FALL_FACE -> {
                // a stumble (a leg out to catch it, the arms forward), then lying: arms past the head, cheek on the ground
                float lie = smooth(t, 6, 13);
                yield mix(new float[]{18, 0, 0, 0, 0, 0, -70, 0, 18, -60, 0, -18, -34, 0, 2, 22, 0, -2},
                        new float[]{-25, 70, 0, 0, 0, 0, -165, 0, 16, -165, 0, -16, 0, 0, 4, 0, 0, -4}, lie);
            }
            // flung: arms out in front, legs left behind, the head snapping forward
            case FLUNG -> new float[]{18, 0, 0, 0, 0, 0, -75, 0, 26, -75, 0, -26, -28, 0, 4, -14, 0, -4};
            // on the blade: head thrown back, arms hanging limp a little forward, the feet hardly holding it
            case HOSTAGE -> new float[]{-32, 0, 0, -10, 0, 0, -8 + s * 3, 0, 12, -8 - s * 3, 0, -12, 6, 0, 3, -4, 0, -3};
            // flat through the air head first: arms trailing, legs apart; then lying as it landed
            case THROWN -> mix(new float[]{-50, 0, 0, 0, 0, 0, 25, 0, 34, 25, 0, -34, 10, 0, 12, 10, 0, -12},
                    new float[]{-25, -70, 0, 0, 0, 0, -10, 0, 30, -10, 0, -30, 0, 0, 8, 0, 0, -8}, smooth(t, 14, 22));
            case FROZEN_RUN -> new float[]{-10, 0, 0, 0, 0, 0, -55, 0, 6, 42, 0, -6, 38, 0, 2, -42, 0, -2};
            case FROZEN_LEAP -> new float[]{-38, 0, 0, 0, 0, 0, -150, 0, 14, -150, 0, -14, 28, 0, 4, 48, 0, -4};
            case FROZEN_POUNCE -> new float[]{-22, 0, 0, 0, 0, 0, -172, 0, 22, -168, 0, -22, -72, 0, 4, 24, 0, -4};
            // roaring: the head up, the arms thrown back
            case FROZEN_RAGE -> new float[]{-38, 0, 0, -8, 0, 0, 52, 0, 38, 52, 0, -38, -10, 0, 12, 10, 0, -12};
            case BLASTED -> new float[]{-24, 0, 0, 0, 0, 0, -30, 0, 72, -30, 0, -72, 22, 0, 24, -22, 0, -24};
            case FALL_BACK -> mix(new float[]{20, 0, 0, 0, 0, 0, -65, 0, 22, -65, 0, -22, -24, 0, 4, 10, 0, -4},
                    new float[]{-10, 30, 0, 0, 0, 0, -12, 0, 62, -12, 0, -62, 0, 0, 10, 0, 0, -10}, smooth(t, 6, 12));
            case STUMBLE -> mix(new float[]{16, 0, 0, 0, 0, 0, -40, 0, 30, -70, 0, -20, -40, 0, -6, 30, 0, 8},
                    new float[]{-25, 70, 0, 0, 0, 0, -165, 0, 16, -165, 0, -16, 0, 0, 4, 0, 0, -4}, smooth(t, 9, 16));
            default -> null;
        };
    }

    private static float[] mix(float[] a, float[] b, float k) {
        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) out[i] = Mth.lerp(k, a[i], b[i]);
        return out;
    }

    private MobPoses() {}
}
