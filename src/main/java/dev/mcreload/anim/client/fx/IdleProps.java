package dev.mcreload.anim.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.client.rig.RigRegistry;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Props for idle fidgets: the stick a player pulls out of their pocket to fence with the air.
 * Drawn in the empty main hand with the same transform chain the game uses for held items.
 */
public final class IdleProps {
    private static final ItemStack STICK = new ItemStack(Items.STICK);
    private static final float[] ROT = new float[3], POS = new float[3], BODY_ROT = new float[3], BODY_POS = new float[3];

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (!Cfg.enabled || !Cfg.idleActions) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean drew = false;
        for (AbstractClientPlayer player : level.players()) {
            if (player.isInvisible() || !player.getMainHandItem().isEmpty()) continue;
            if (player == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            if (player != mc.player && !Cfg.otherPlayers) continue;
            PlayerRig rig = RigRegistry.get(player);
            if (rig == null) continue;
            MotionState m = rig.motion;
            if (m.idleAction != MotionState.IDLE_SHADOWBOX || m.idleW.v < 0.5f) continue;
            if (!dev.mcreload.anim.client.module.Gestures.stickOut(m.since(m.idleActionStart))) continue;

            boolean left = player.getMainArm() == HumanoidArm.LEFT;
            int armPart = left ? PoseFrame.LEFT_ARM : PoseFrame.RIGHT_ARM;
            if (!rig.finalPose(armPart, 1, ROT) || !rig.finalPose(armPart, 0, POS)) continue;
            boolean hasBodyRot = rig.finalPose(PoseFrame.BODY, 1, BODY_ROT);
            boolean hasBodyPos = rig.finalPose(PoseFrame.BODY, 0, BODY_POS);
            Matrix4f mat = WeaponTrails.handMatrix(player, rig, ROT, POS, hasBodyRot ? BODY_ROT : null,
                    hasBodyPos ? BODY_POS : null, left, pt);

            PoseStack ps = event.getPoseStack();
            ps.pushPose();
            ps.translate(-cam.x, -cam.y, -cam.z);
            ps.mulPoseMatrix(mat);
            int light = LevelRenderer.getLightColor(level, player.blockPosition());
            mc.getItemRenderer().renderStatic(player, STICK,
                    left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    left, ps, buffers, level, light, OverlayTexture.NO_OVERLAY, player.getId());
            ps.popPose();
            drew = true;
        }
        if (drew) buffers.endBatch();
    }

    private IdleProps() {}
}
