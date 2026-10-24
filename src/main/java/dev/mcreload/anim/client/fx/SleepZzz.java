package dev.mcreload.anim.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/** "Z z z" letters (Minecraft font) drifting up from sleeping players' heads (and from players dozing off standing). */
public final class SleepZzz {
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!Cfg.enabled || !Cfg.idle) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        float pt = event.getPartialTick();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        boolean any = false;

        for (AbstractClientPlayer player : level.players()) {
            if (player.isInvisible()) continue;
            boolean sleeping = player.isSleeping();
            if (!sleeping) {
                dev.mcreload.anim.client.rig.PlayerRig rig = dev.mcreload.anim.client.rig.RigRegistry.get(player);
                if (rig == null || !rig.motion.dozing) continue;
            }
            if (player == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            Vec3 head = player.getPosition(pt).add(0, sleeping ? 0.45 : 1.45, 0);
            float time = (player.tickCount + pt) / 20f;
            for (int i = 0; i < 3; i++) {
                float phase = (time * 0.45f + i / 3f) % 1f;
                float alpha = Mth.sin(phase * Mth.PI);
                if (alpha < 0.08f) continue;
                double x = head.x + 0.25 * Mth.sin(time * 1.3f + i * 2.1f) * phase;
                double y = head.y + 0.25 + phase * 1.1;
                double z = head.z + 0.25 * Mth.cos(time * 1.1f + i * 1.7f) * phase;
                float scale = 0.018f + 0.014f * phase;

                ps.pushPose();
                ps.translate(x - cam.x, y - cam.y, z - cam.z);
                ps.mulPose(camera.rotation());
                ps.scale(-scale, -scale, scale);
                String text = i == 0 ? "Z" : "z";
                int a = Math.max(8, (int) (alpha * 255f)) << 24;
                float w = -font.width(text) / 2f;
                font.drawInBatch(text, w, 0f, a | 0xFFFFFF, true, ps.last().pose(), buffers,
                        Font.DisplayMode.NORMAL, 0, 0xF000F0);
                ps.popPose();
                any = true;
            }
        }
        if (any) buffers.endBatch();
    }

    private SleepZzz() {}
}
