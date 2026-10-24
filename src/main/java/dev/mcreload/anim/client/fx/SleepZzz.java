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

/**
 * "Z z z" (Minecraft font) from sleeping players' heads and from players dozing off standing:
 * one letter at a time, flying up and off to the side and fading out.
 */
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
            dev.mcreload.anim.client.rig.PlayerRig rig = dev.mcreload.anim.client.rig.RigRegistry.get(player);
            if (!sleeping && (rig == null || !rig.motion.dozing)) continue;
            if (player == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            float time = rig != null ? rig.motion.time : (player.tickCount + pt) / 20f;
            // Dozing off: the first one comes a moment after the head starts to droop.
            float from = !sleeping && rig.motion.ownDoze ? rig.motion.dozeStart + 0.8f : -1e9f;
            float yaw = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
            Vec3 head = sleeping ? player.getPosition(pt).add(0, 0.45, 0)
                    : player.getPosition(pt).add(fx * 0.18, 1.55, fz * 0.18);
            // One letter at a time: each flies up and off to the side, swaying, growing, and fades out.
            int newest = Mth.floor(time / ZZZ_EVERY);
            for (int k = newest - 2; k <= newest; k++) {
                float born = k * ZZZ_EVERY;
                float age = time - born;
                if (born < from || age < 0f || age >= ZZZ_LIFE) continue;
                float u = age / ZZZ_LIFE;
                float alpha = Math.min(1f, u / 0.12f) * (1f - smooth((u - 0.45f) / 0.55f));
                if (alpha < 0.03f) continue;
                float side = (k & 1) == 0 ? 1f : -1f;
                double rise = 0.10 + 0.95 * u * (2f - u);
                double out = side * (0.08 + 0.35 * u) + 0.06 * Mth.sin(age * 5f + k);
                double x = head.x + rx * out + fx * 0.15 * u;
                double y = head.y + rise;
                double z = head.z + rz * out + fz * 0.15 * u;
                float scale = 0.011f + 0.021f * u;

                ps.pushPose();
                ps.translate(x - cam.x, y - cam.y, z - cam.z);
                ps.mulPose(camera.rotation());
                ps.mulPose(com.mojang.math.Axis.ZP.rotation(0.25f * side * u));
                ps.scale(-scale, -scale, scale);
                String text = Math.floorMod(k, 3) == 0 ? "Z" : "z";
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

    /** A new letter every this many seconds, each lasting this long (so at most two at once). */
    private static final float ZZZ_EVERY = 1.0f, ZZZ_LIFE = 1.8f;

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    private SleepZzz() {}
}
