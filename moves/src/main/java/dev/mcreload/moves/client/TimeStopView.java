package dev.mcreload.moves.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.Stamina;
import dev.mcreload.moves.TimeStop;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The time stop as the players see it (TimeStop on the server).
 *
 * <ul>
 *   <li>Its maker's camera: third person, staying behind the place he began from while the server puts him at each
 *   enemy in turn; the mouse turns it all round (the look is free, the body faces each blow's mob); first person again
 *   at the end, if it was that.</li>
 *   <li>The flicker (author: "as in films and anime, the body flickering in different places"): where he was a moment
 *   ago his body is seen again for a few ticks, on and off, as he appears at the next mob.</li>
 * </ul>
 */
public final class TimeStopView {
    /** How long an after-image stays (ticks), and how far behind the camera stands (blocks). */
    private static final int GHOST_TICKS = 5;
    private static final double BACK = 4.5;

    private record Ghost(int entity, Vec3 at, float yaw, long born) {}

    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static boolean active;
    private static Vec3 anchor;
    private static float anchorYaw;
    private static CameraType before;
    private static boolean drawingGhost;
    private static int gap;

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(TimeStopView::tick);
        MinecraftForge.EVENT_BUS.addListener(TimeStopView::camera);
        MinecraftForge.EVENT_BUS.addListener(TimeStopView::facing);
        MinecraftForge.EVENT_BUS.addListener(TimeStopView::ghosts);
    }

    /** A part of the time stop starts on {@code p}: an after-image where they stand now; for us, the camera. */
    static void step(Player p, Move move, int target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (move != Move.TS_STANCE) GHOSTS.add(new Ghost(p.getId(), p.position(), p.yBodyRot, mc.level.getGameTime()));
        if (p == mc.player && move == Move.TS_STANCE && !active) {
            active = true;
            gap = 0;
            anchor = p.getEyePosition();
            anchorYaw = p.getYRot();
            before = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            GHOSTS.clear();
            active = false;
            return;
        }
        long now = mc.level.getGameTime();
        GHOSTS.removeIf(g -> now - g.born > GHOST_TICKS || now < g.born);
        boolean on = mc.player != null && Stamina.of(mc.player).move != null && Stamina.of(mc.player).move.timeStop();
        // between its parts this end may finish one a tick or two before the server starts the next: a few ticks' grace
        gap = on ? 0 : gap + 1;
        if (active && gap > 6) {
            active = false;
            if (before != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(before);
        }
    }

    /** The camera behind where he began, turned by the mouse, not into a wall. */
    private static void camera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || anchor == null || mc.player == null || mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) return;
        Vec3 look = Vec3.directionFromRotation(event.getPitch(), event.getYaw());
        Vec3 want = anchor.subtract(look.scale(BACK));
        var hit = mc.level.clip(new ClipContext(anchor, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        Vec3 at = hit.getType() == HitResult.Type.MISS ? want : hit.getLocation().add(look.scale(0.3));
        SmoothCamera.place(event.getCamera(), at);
    }

    /** The body faces the blow's mob (the look is the camera's); in the stance and the yawn, the way he began. */
    private static void facing(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        Stamina s = Stamina.of(p);
        if (s.move == null || !s.move.timeStop()) return;
        float yaw;
        Entity mob = s.move.timeStopBlow() ? p.level().getEntity(s.target) : null;
        if (mob != null) yaw = TimeStop.facing(p.position(), mob.position());
        else if (p == Minecraft.getInstance().player && anchor != null) yaw = anchorYaw;
        else return;
        p.yBodyRot = p.yBodyRotO = yaw;
        p.yHeadRot = p.yHeadRotO = yaw;
    }

    /** The after-images: the player's body drawn again where they were, flickering out. */
    private static void ghosts(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || GHOSTS.isEmpty() || drawingGhost) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        drawingGhost = true;
        try {
            for (Ghost g : GHOSTS) {
                long age = now - g.born;
                // on, off, on, off: the flicker
                if (age % 2 == 1) continue;
                if (!(mc.level.getEntity(g.entity) instanceof AbstractClientPlayer p)) continue;
                float bodyWas = p.yBodyRot, bodyWasO = p.yBodyRotO;
                p.yBodyRot = p.yBodyRotO = g.yaw;
                mc.getEntityRenderDispatcher().render(p, g.at.x - cam.x, g.at.y - cam.y, g.at.z - cam.z, g.yaw,
                        event.getPartialTick(), ps, buffers, mc.getEntityRenderDispatcher().getPackedLightCoords(p, event.getPartialTick()));
                p.yBodyRot = bodyWas;
                p.yBodyRotO = bodyWasO;
            }
            buffers.endBatch();
        } finally {
            drawingGhost = false;
        }
    }

    private TimeStopView() {}
}
