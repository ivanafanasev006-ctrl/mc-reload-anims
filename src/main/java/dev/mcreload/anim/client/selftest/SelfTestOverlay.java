package dev.mcreload.anim.client.selftest;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RenderGuiEvent;
import org.joml.Quaternionf;

/** Draws the local player from the side, front and from above, like the Mo'Bends showcase panels. */
final class SelfTestOverlay {
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || SelfTest.currentLabel.isEmpty()) return;
        GuiGraphics g = event.getGuiGraphics();
        float pt = event.getPartialTick();
        int w = g.guiWidth(), h = g.guiHeight();

        int panelW = w / 4;
        int top = 4, bottom = h - 4;
        String[] names = {"SIDE", "FRONT", "TOP"};
        float[] views = {90f, 0f, 0f};
        float[] pitches = {0f, 0f, 90f};
        int[] scales = {Math.max(20, h / 5), Math.max(16, h / 7), Math.max(16, h / 7)};
        int[] x0 = {w - panelW * 2, w - panelW, w - panelW};
        int[][] y = {{top, bottom}, {top, (top + bottom) / 2}, {(top + bottom) / 2, bottom}};

        for (int i = 0; i < 3; i++) {
            int left = x0[i], right = (i == 0) ? w - panelW : w;
            int y0 = y[i][0], y1 = y[i][1];
            g.fill(left + 2, y0, right - 2, y1, 0xC0303040);
            g.drawString(mc.font, names[i], left + 6, y0 + 4, 0xFFFFFF);
            int cx = (left + right) / 2;
            int cy = i == 2 ? (y0 + y1) / 2 : y1 - (y1 - y0) / 6;
            drawPlayer(g, cx, cy, scales[i], views[i], pitches[i], player, pt);
        }
        g.drawString(mc.font, SelfTest.currentLabel, 6, 6, 0xFFFF55);
        g.flush();
        SelfTest.onFrameRendered(mc);
    }

    private static void drawPlayer(GuiGraphics g, int x, int y, int scale, float viewDeg, float pitchDeg, LocalPlayer player, float pt) {
        float bodyYaw = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot);
        // Top view: tip the model so we look down on the head, feet at the panel centre.
        Quaternionf pose = new Quaternionf().rotateZ(Mth.PI).rotateX(pitchDeg * Mth.DEG_TO_RAD)
                .rotateY((viewDeg + bodyYaw - 180f) * Mth.DEG_TO_RAD);

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 150);
        ps.mulPoseMatrix(new org.joml.Matrix4f().scaling(scale, scale, -scale));
        ps.mulPose(pose);
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderSystem.runAsFancy(() -> dispatcher.render(player, 0, 0, 0, 0f, pt, ps, buffers, 0xF000F0));
        buffers.endBatch();
        dispatcher.setRenderShadow(true);
        ps.popPose();
        Lighting.setupFor3DItems();
    }

    private SelfTestOverlay() {}
}
