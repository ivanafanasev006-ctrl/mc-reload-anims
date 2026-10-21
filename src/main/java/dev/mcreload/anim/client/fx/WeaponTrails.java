package dev.mcreload.anim.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.client.rig.RigRegistry;
import dev.mcreload.anim.client.rig.Weapons;
import dev.mcreload.anim.client.selftest.SelfTest;
import dev.mcreload.anim.compat.ModCompat;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * White wind streak behind a swung blade.
 *
 * <p>Every frame during a strike we rebuild the exact transform chain the game uses to draw the held
 * item (entity position, body yaw, playerAnimator's whole-body transform, model flip and scale, the
 * arm's final animated pose, hand offset, the item model's third-person transform) and record two
 * points on the blade. The recent samples are drawn as a fading additive ribbon.</p>
 *
 * <p>Our own slashes emit during their strike window. Animations from other mods (Better Combat)
 * emit whenever the weapon tip moves fast while that mod animates the arm.</p>
 */
public final class WeaponTrails {
    private static final float BASE_U = 0.42f, TIP_U = 0.97f;
    private static final float FAST_TIP_SPEED = 7.0f; // blocks per second

    private record Sample(long nanos, Vec3 base, Vec3 tip) {}

    private static final class Trail {
        final ArrayDeque<Sample> samples = new ArrayDeque<>();
        Vec3 lastTip;
        long lastTipNanos;
    }

    private static final Map<Integer, Trail> TRAILS = new HashMap<>();
    private static final float[] ROT = new float[3], POS = new float[3], BODY_ROT = new float[3], BODY_POS = new float[3];

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            TRAILS.clear();
            return;
        }
        if (!Cfg.enabled || !Cfg.trails) {
            TRAILS.clear();
            return;
        }

        long now = System.nanoTime();
        // Self-test renders at a few FPS: keep the streak long so its placement can be checked.
        long life = (long) ((SelfTest.enabled() ? 1.5f : Cfg.trailSeconds) * 1e9);
        float pt = event.getPartialTick();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();

        for (AbstractClientPlayer player : level.players()) {
            PlayerRig rig = RigRegistry.get(player);
            if (rig == null || player.isInvisible()) continue;
            if (player == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            if (player != mc.player && !Cfg.otherPlayers) continue;
            sample(player, rig, pt, now);
        }

        // Drop expired samples and trails.
        Iterator<Map.Entry<Integer, Trail>> it = TRAILS.entrySet().iterator();
        while (it.hasNext()) {
            Trail trail = it.next().getValue();
            while (!trail.samples.isEmpty() && now - trail.samples.peekFirst().nanos() > life) trail.samples.pollFirst();
            if (trail.samples.isEmpty() && (trail.lastTip == null || now - trail.lastTipNanos > 1_000_000_000L)) it.remove();
        }

        render(event.getPoseStack(), cam, now, life);
    }

    private static void sample(AbstractClientPlayer player, PlayerRig rig, float pt, long now) {
        MotionState m = rig.motion;
        // Which hand is swinging, and is it a weapon?
        HumanoidArm arm = m.slashSide > 0 ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
        InteractionHand hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = player.getItemInHand(hand);
        int armPart = arm == HumanoidArm.RIGHT ? PoseFrame.RIGHT_ARM : PoseFrame.LEFT_ARM;

        boolean ours = MotionState.isWeapon(m.slashKind);
        float t = m.slashProgress();
        float from = SelfTest.enabled() ? 0.05f : 0.34f, to = SelfTest.enabled() ? 1.0f : 0.74f;
        boolean ourStrike = ours && t >= from && t <= to && rig.foreign[armPart] < 0.5f;
        boolean foreign = ModCompat.betterCombat && rig.foreign[armPart] > 0.5f;
        if (!ourStrike && !foreign) {
            // Better Combat swings the main hand; check that one too.
            if (!ModCompat.betterCombat) return;
            arm = player.getMainArm();
            hand = InteractionHand.MAIN_HAND;
            stack = player.getMainHandItem();
            armPart = arm == HumanoidArm.RIGHT ? PoseFrame.RIGHT_ARM : PoseFrame.LEFT_ARM;
            if (rig.foreign[armPart] <= 0.5f) return;
            foreign = true;
        }
        if (!Weapons.isMeleeWeapon(stack)) return;

        Vec3[] points = bladePoints(player, rig, armPart, arm == HumanoidArm.LEFT, stack, pt);
        if (points == null) return;

        Trail trail = TRAILS.computeIfAbsent(player.getId(), id -> new Trail());
        boolean emit = ourStrike;
        if (!emit && trail.lastTip != null) {
            float secs = (now - trail.lastTipNanos) / 1e9f;
            if (secs > 0f && secs < 0.2f) emit = points[1].distanceTo(trail.lastTip) / secs > FAST_TIP_SPEED;
        }
        trail.lastTip = points[1];
        trail.lastTipNanos = now;
        if (emit) {
            Sample last = trail.samples.peekLast();
            if (last == null || last.tip().distanceToSqr(points[1]) > 1e-6) {
                trail.samples.addLast(new Sample(now, points[0], points[1]));
            }
        }
    }

    /** World-space (relative to nothing; absolute) points on the blade: [base, tip]. */
    private static Vec3[] bladePoints(AbstractClientPlayer player, PlayerRig rig, int armPart, boolean left, ItemStack stack, float pt) {
        if (!rig.finalPose(armPart, 1, ROT) || !rig.finalPose(armPart, 0, POS)) return null;
        boolean hasBodyRot = rig.finalPose(PoseFrame.BODY, 1, BODY_ROT);
        boolean hasBodyPos = rig.finalPose(PoseFrame.BODY, 0, BODY_POS);

        Matrix4f mat = handMatrix(player, rig, ROT, POS, hasBodyRot ? BODY_ROT : null, hasBodyPos ? BODY_POS : null, left, pt);

        // Item model's third-person transform (ItemTransform.apply), then ItemRenderer's centering.
        ItemDisplayContext ctx = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, player.level(), player, 0);
        ItemTransform tr = model.getTransforms().getTransform(ctx);
        float rx = tr.rotation.x(), ry = tr.rotation.y(), rz = tr.rotation.z();
        if (left) { ry = -ry; rz = -rz; }
        mat.translate((left ? -1f : 1f) * tr.translation.x(), tr.translation.y(), tr.translation.z());
        mat.rotate(new Quaternionf().rotationXYZ(rx * Mth.DEG_TO_RAD, ry * Mth.DEG_TO_RAD, rz * Mth.DEG_TO_RAD));
        mat.scale(tr.scale.x(), tr.scale.y(), tr.scale.z());
        mat.translate(-0.5f, -0.5f, -0.5f);

        // A held sword's sprite runs diagonally from the handle (bottom-left) to the tip (top-right).
        Vector3f base = mat.transformPosition(new Vector3f(BASE_U, BASE_U, 0.5f));
        Vector3f tip = mat.transformPosition(new Vector3f(TIP_U, TIP_U, 0.5f));
        return new Vec3[]{new Vec3(base.x(), base.y(), base.z()), new Vec3(tip.x(), tip.y(), tip.z())};
    }

    /**
     * World-space transform of an item held in the hand, as built by ItemInHandLayer just before
     * the item renderer takes over (entity position, body yaw, playerAnimator's whole-body
     * transform, model flip and scale, the arm's animated pose, the hand offset).
     */
    static Matrix4f handMatrix(AbstractClientPlayer player, float[] rot, float[] armPos, float[] bodyRot, float[] bodyPos,
                               boolean left, float pt) {
        return handMatrix(player, null, rot, armPos, bodyRot, bodyPos, left, pt);
    }

    private static final float[] T_ROT = new float[3], T_POS = new float[3], T_BEND = new float[3], A_BEND = new float[3];

    /** Same as above, with our elbow and waist joints taken into account when the rig is known. */
    static Matrix4f handMatrix(AbstractClientPlayer player, PlayerRig rig, float[] rot, float[] armPos, float[] bodyRot,
                               float[] bodyPos, boolean left, float pt) {
        Matrix4f mat = new Matrix4f();
        Vec3 pos = player.getPosition(pt);
        mat.translate((float) pos.x, (float) pos.y, (float) pos.z);
        if (player.isCrouching()) mat.translate(0f, -0.125f, 0f);
        float bodyYaw = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot);
        mat.rotate(Axis.YP.rotationDegrees(180f - bodyYaw));

        // playerAnimator's whole-body transform (PlayerRenderer.setupRotations, at RETURN).
        float bx = bodyPos != null ? bodyPos[0] : 0f, by = bodyPos != null ? bodyPos[1] : 0f, bz = bodyPos != null ? bodyPos[2] : 0f;
        mat.translate(bx, by + 0.7f, bz);
        if (bodyRot != null) {
            mat.rotate(Axis.ZP.rotation(bodyRot[2]));
            mat.rotate(Axis.YP.rotation(bodyRot[1]));
            mat.rotate(Axis.XP.rotation(bodyRot[0]));
        }
        mat.translate(0f, -0.7f, 0f);

        // LivingEntityRenderer: flip, player scale, model offset.
        mat.scale(-1f, -1f, 1f);
        mat.scale(0.9375f, 0.9375f, 0.9375f);
        mat.translate(0f, -1.501f, 0f);

        boolean joints = rig != null && dev.mcreload.anim.client.joint.Joints.active();
        // Waist joint: the arm rides on the bent upper body.
        if (joints && rig.finalPose(PoseFrame.TORSO, 2, T_BEND) && Math.abs(T_BEND[1]) > 1e-4f) {
            boolean hasT = rig.finalPose(PoseFrame.TORSO, 1, T_ROT);
            boolean hasP = rig.finalPose(PoseFrame.TORSO, 0, T_POS);
            Quaternionf q = hasT ? new Quaternionf().rotationZYX(T_ROT[2], T_ROT[1], T_ROT[0]) : new Quaternionf();
            float tx = hasP ? T_POS[0] : 0f, ty = hasP ? T_POS[1] : 0f, tz = hasP ? T_POS[2] : 0f;
            mat.translate(tx / 16f, ty / 16f, tz / 16f);
            mat.rotate(q);
            dev.mcreload.anim.client.joint.Joint.hinge(mat, 6f, 0f, 0f, 4f, 2f, true, T_BEND[0], T_BEND[1]);
            mat.rotate(q.conjugate());
            mat.translate(-tx / 16f, -ty / 16f, -tz / 16f);
        }

        // The arm, exactly as ModelPart.translateAndRotate.
        mat.translate(armPos[0] / 16f, armPos[1] / 16f, armPos[2] / 16f);
        mat.rotate(new Quaternionf().rotationZYX(rot[2], rot[1], rot[0]));

        // Elbow joint.
        if (joints && rig.finalPose(left ? PoseFrame.LEFT_ARM : PoseFrame.RIGHT_ARM, 2, A_BEND)) {
            boolean slim = "slim".equals(player.getModelName());
            float cx = (left ? 1f : -1f) * (slim ? 0.5f : 1f);
            dev.mcreload.anim.client.joint.Joint.hinge(mat, 4f, cx, 0f, slim ? 1.5f : 2f, 2f, false, A_BEND[0], A_BEND[1]);
        }
        // Weapon turned in the fist.
        if (joints && left != rig.motion.mainRight && Math.abs(rig.motion.itemTilt.v) > 1e-3f) {
            mat.translate(0f, 10f / 16f, -1f / 16f);
            mat.rotate(new Quaternionf().rotationX(-rig.motion.itemTilt.v));
            mat.translate(0f, -10f / 16f, 1f / 16f);
        }

        // ItemInHandLayer.renderArmWithItem
        mat.rotate(Axis.XP.rotationDegrees(-90f));
        mat.rotate(Axis.YP.rotationDegrees(180f));
        mat.translate((left ? -1f : 1f) / 16f, 0.125f, -0.625f);
        return mat;
    }

    private static void render(PoseStack poseStack, Vec3 cam, long now, long life) {
        if (TRAILS.isEmpty()) return;
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f pose = poseStack.last().pose();

        int rgb = Cfg.trailColor;
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float maxAlpha = Cfg.trailOpacity;

        boolean any = false;
        for (Trail trail : TRAILS.values()) {
            if (trail.samples.size() < 2) continue;
            Sample[] s = trail.samples.toArray(new Sample[0]);
            // Catmull-Rom subdivision for a smooth arc even at low frame rates.
            int sub = 4;
            Vec3 prevBase = null, prevTip = null;
            float prevAge = 0f;
            for (int i = 0; i < s.length - 1; i++) {
                Sample p0 = s[Math.max(0, i - 1)], p1 = s[i], p2 = s[i + 1], p3 = s[Math.min(s.length - 1, i + 2)];
                for (int k = (i == 0 ? 0 : 1); k <= sub; k++) {
                    float u = k / (float) sub;
                    Vec3 base = catmull(p0.base(), p1.base(), p2.base(), p3.base(), u);
                    Vec3 tip = catmull(p0.tip(), p1.tip(), p2.tip(), p3.tip(), u);
                    float age = (now - (p1.nanos() + (long) ((p2.nanos() - p1.nanos()) * u))) / (float) life;
                    if (prevBase != null) {
                        float a0 = alpha(prevAge) * maxAlpha, a1 = alpha(age) * maxAlpha;
                        quad(vc, pose, cam, prevBase, prevTip, tip, base, r, g, b, a0, a1);
                        any = true;
                    }
                    prevBase = base;
                    prevTip = tip;
                    prevAge = age;
                }
            }
        }
        if (any) buffers.endBatch(RenderType.lightning());
    }

    private static float alpha(float age) {
        float a = 1f - Mth.clamp(age, 0f, 1f);
        return a * a;
    }

    /** Ribbon segment, drawn from both sides. Base edge is faint, tip edge bright. */
    private static void quad(VertexConsumer vc, Matrix4f pose, Vec3 cam, Vec3 b0, Vec3 t0, Vec3 t1, Vec3 b1,
                             float r, float g, float b, float a0, float a1) {
        float edge = 0.12f;
        v(vc, pose, cam, b0, r, g, b, a0 * edge);
        v(vc, pose, cam, t0, r, g, b, a0);
        v(vc, pose, cam, t1, r, g, b, a1);
        v(vc, pose, cam, b1, r, g, b, a1 * edge);

        v(vc, pose, cam, b1, r, g, b, a1 * edge);
        v(vc, pose, cam, t1, r, g, b, a1);
        v(vc, pose, cam, t0, r, g, b, a0);
        v(vc, pose, cam, b0, r, g, b, a0 * edge);
    }

    private static void v(VertexConsumer vc, Matrix4f pose, Vec3 cam, Vec3 p, float r, float g, float b, float a) {
        vc.vertex(pose, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).color(r, g, b, a).endVertex();
    }

    private static Vec3 catmull(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
        float t2 = t * t, t3 = t2 * t;
        double x = 0.5 * ((2 * p1.x) + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3);
        double y = 0.5 * ((2 * p1.y) + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3);
        double z = 0.5 * ((2 * p1.z) + (-p0.z + p2.z) * t + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3);
        return new Vec3(x, y, z);
    }

    private WeaponTrails() {}
}
