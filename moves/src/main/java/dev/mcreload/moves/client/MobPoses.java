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
        float in = Mth.clamp(t / EASE, 0f, 1f);
        boolean stays = o.pose == MobPose.FALL_FACE || o.pose == MobPose.THROWN;
        float out = stays ? 1f : Mth.clamp((o.ticks - t) / EASE, 0f, 1f);
        float k = Math.min(in, out);
        return k * k * (3 - 2 * k);
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
        h.hat.copyFrom(h.head);
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
