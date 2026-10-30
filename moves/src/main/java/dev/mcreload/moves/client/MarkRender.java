package dev.mcreload.moves.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mcreload.moves.KinetrixMoves;
import dev.mcreload.moves.Marks;
import dev.mcreload.moves.Stamina;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;

/**
 * The bow's marks over the marked mobs (Marks; author, 2026-10-30, 23:01: "a red mark in a white
 * square"): over the head, facing the camera, seen through walls; dimmed while the mob is behind
 * cover, where the volley at the marked would not shoot at it.
 */
public final class MarkRender {
    private static final ResourceLocation MARK = new ResourceLocation(KinetrixMoves.MODID, "textures/misc/mark.png");
    /** Half the mark's side and how high over the head it hangs (blocks). */
    private static final float HALF = 0.22f, ABOVE = 0.55f;

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(MarkRender::render);
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui) return;
        Stamina s = Stamina.of(p);
        if (s.marks.isEmpty()) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float pt = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.textSeeThrough(MARK);
        VertexConsumer v = buffers.getBuffer(type);
        for (int id : s.marks.keySet()) {
            if (!(p.level().getEntity(id) instanceof LivingEntity e) || !e.isAlive()) continue;
            double x = Mth.lerp(pt, e.xo, e.getX()) - cam.x, y = Mth.lerp(pt, e.yo, e.getY()) + e.getBbHeight() + ABOVE - cam.y,
                    z = Mth.lerp(pt, e.zo, e.getZ()) - cam.z;
            // a little bigger far off, so it reads at a bow's range
            float half = HALF * (1f + Math.min(1.5f, (float) Math.sqrt(x * x + y * y + z * z) / 32f));
            int alpha = Marks.seen(p, e) ? 255 : 115;
            ps.pushPose();
            ps.translate(x, y, z);
            ps.mulPose(camera.rotation());
            ps.scale(-1f, -1f, 1f);
            Matrix4f m = ps.last().pose();
            quad(v, m, half, alpha, false);
            quad(v, m, half, alpha, true);
            ps.popPose();
        }
        buffers.endBatch(type);
    }

    /** The mark's square, its corners one way round or the other (seen from either side). */
    private static void quad(VertexConsumer v, Matrix4f m, float h, int alpha, boolean back) {
        float[][] corners = {{-h, -h, 0, 0}, {-h, h, 0, 1}, {h, h, 1, 1}, {h, -h, 1, 0}};
        for (int i = 0; i < 4; i++) {
            float[] c = corners[back ? 3 - i : i];
            v.vertex(m, c[0], c[1], 0f).color(255, 255, 255, alpha).uv(c[2], c[3]).uv2(0xF000F0).endVertex();
        }
    }

    private MarkRender() {}
}
