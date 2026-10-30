package dev.mcreload.moves.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcreload.moves.Ledge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * The edge the hanging player looks at (LedgeInput.aim), outlined along its top where the hands would
 * take it: green within the leap's reach, red out of it (author, 2026-10-30, 23:01: "aim by looking
 * at it, it lights up green or red, then Space and he leaps there").
 */
public final class LedgeAim {
    static void init() {
        MinecraftForge.EVENT_BUS.addListener(LedgeAim::render);
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Ledge.Target aim = LedgeInput.aim;
        if (aim == null) return;
        Vec3 cam = event.getCamera().getPosition();
        AABB box = Ledge.edgeBox(aim.spot()).move(-cam.x, -cam.y, -cam.z);
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        float r = aim.reachable() ? 0.35f : 1f, g = aim.reachable() ? 1f : 0.25f;
        // twice, a hair apart: a line a little bolder than the block outline's
        LevelRenderer.renderLineBox(ps, buffers.getBuffer(RenderType.lines()), box, r, g, 0.3f, 1f);
        LevelRenderer.renderLineBox(ps, buffers.getBuffer(RenderType.lines()), box.inflate(0.01), r, g, 0.3f, 1f);
        buffers.endBatch(RenderType.lines());
    }

    private LedgeAim() {}
}
