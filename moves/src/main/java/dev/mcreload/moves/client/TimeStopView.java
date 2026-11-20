package dev.mcreload.moves.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mcreload.moves.GuardAnswers;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.Stamina;
import dev.mcreload.moves.TimeStop;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The time stop as the players see it (TimeStop on the server), and the block's finisher, which borrows its camera.
 *
 * <ul>
 *   <li>The maker's camera: third person, staying behind the place he began from while the server puts him at each
 *   enemy in turn; the mouse turns it all round (the look is free, the body faces each blow's mob); first person again
 *   at the end, if it was that. (The block's finisher has the chain's camera instead, Cutscene: behind him, turned
 *   with him to the mob as he lands behind it, author 19.11.)</li>
 *   <li>The after-image (author 19.11: "before each one your reflection, gone in a fraction of a second"): where he was a
 *   moment ago his body is seen again, pale and fading out in 0.4 s.</li>
 *   <li>The slashes (19.11: "as the sweep_attack's white line, but turned each its own way: upright, 30, 45, 127
 *   degrees", "not gone at once: in 1-2 s more and more see-through"): a white crescent where each blow cut, turned to
 *   the camera at its own angle.</li>
 * </ul>
 */
public final class TimeStopView {
    /** How long an after-image stays (ticks), and how far behind the camera stands (blocks). */
    private static final int GHOST_TICKS = 8;
    private static final double BACK = 4.5;
    /** A slash: how long it is seen (ticks), the part of it at full strength. */
    private static final int SLASH_TICKS = 34, SLASH_FULL = 4;

    private record Ghost(int entity, Vec3 at, float yaw, long born) {}

    private record Slash(Vec3 at, float roll, float size, long born) {}

    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final List<Slash> SLASHES = new ArrayList<>();
    private static boolean active;
    private static Vec3 anchor;
    private static float anchorYaw;
    private static CameraType before;
    private static boolean drawingGhost;
    private static int gap;
    /** Where our own player was last tick (the finisher's jump behind the mob leaves an after-image there). */
    private static Vec3 lastSelf;
    private static float lastSelfYaw;

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(TimeStopView::tick);
        MinecraftForge.EVENT_BUS.addListener(TimeStopView::camera);
        MinecraftForge.EVENT_BUS.addListener(TimeStopView::facing);
        MinecraftForge.EVENT_BUS.addListener(TimeStopView::draw);
    }

    /** Ours: a move with this view (the time stop, the block's finisher). */
    private static boolean viewed(Move m) {
        return m != null && (m.timeStop() || m == Move.GUARD_FINISH);
    }

    /** A part of the time stop (or the finisher) starts on {@code p}: an after-image where they stand now; for us, the camera. */
    static void step(Player p, Move move, int target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if ((move.timeStopBlow() && !move.asStrike()) || move == Move.TS_BRAKE || move == Move.AS_HIDDEN) GHOSTS.add(new Ghost(p.getId(), p.position(), p.yBodyRot, mc.level.getGameTime()));
        if (p == mc.player && (move == Move.TS_STANCE || move == Move.CR_LEAP || move == Move.AS_SPIN || move == Move.AXC_OPEN || move == Move.BW_RAIN)
                && !active) {
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
            SLASHES.clear();
            RINGS.clear();
            active = false;
            lastSelf = null;
            return;
        }
        long now = mc.level.getGameTime();
        GHOSTS.removeIf(g -> now - g.born > GHOST_TICKS || now < g.born);
        SLASHES.removeIf(s -> now - s.born > SLASH_TICKS || now < s.born);
        // the slashes where the blows cut (each client sees every player's)
        RINGS.removeIf(r -> now - r.born > RING_TICKS || now < r.born);
        for (Player p : mc.level.players()) {
            Stamina s = Stamina.of(p);
            if (s.move == null) continue;
            heavyFx(p, s, now);
            boolean blow = (s.move.timeStopBlow() && s.moveTick == TimeStop.hitTick(s.move)) || (s.move.crLeap() && s.moveTick == TimeStop.CR_STAB);
            boolean stab = s.move == Move.GUARD_FINISH && s.moveTick == GuardAnswers.FIN_STAB;
            if (!blow && !stab) continue;
            Entity mob = mc.level.getEntity(s.target);
            if (mob == null) continue;
            Vec3 at = mob.position().add(0, mob.getBbHeight() * 0.55, 0);
            float jitter = (float) ((p.getRandom().nextDouble() - 0.5) * 24.0);
            SLASHES.add(new Slash(at, roll(s.move) + jitter, stab ? 0.9f : 1.25f, now));
            if (s.move == Move.TS_SPIN) SLASHES.add(new Slash(at.add(0, -0.25, 0), roll(s.move) + 180f - jitter, 1.4f, now));
        }
        LocalPlayer self = mc.player;
        Stamina mine = self == null ? null : Stamina.of(self);
        boolean on = mine != null && viewed(mine.move);
        // the finisher's jump: an after-image where we were
        if (self != null && mine.move == Move.GUARD_FINISH && lastSelf != null && lastSelf.distanceToSqr(self.position()) > 1.0) {
            GHOSTS.add(new Ghost(self.getId(), lastSelf, lastSelfYaw, now));
            // the camera behind him turns with him to the mob (as in the chain of finishers: Cutscene has it behind)
            Entity mob = mc.level.getEntity(mine.target);
            if (mob != null) SmoothCamera.turnTo(self, TimeStop.facing(self.position(), mob.position()));
        }
        lastSelf = self == null ? null : self.position();
        lastSelfYaw = self == null ? 0f : self.yBodyRot;
        // between its parts this end may finish one a tick or two before the server starts the next: a few ticks' grace
        gap = on ? 0 : gap + 1;
        if (active && gap > 6) {
            active = false;
            if (before != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(before);
        }
    }

    /** Each blow's slash at its own angle (0 level, 90 upright), the way the blade went. */
    private static float roll(Move m) {
        return switch (m) {
            case TS_SLASH -> 127f;
            case TS_LEAP -> 90f;
            case TS_THRUST -> 30f;
            case TS_SPIN, CR_BLINK_B -> 172f;
            case CR_BLINK_A -> 127f;
            case CR_BLINK_C -> 8f;
            case CR_BLINK_D -> 30f;
            case CR_LEAP, CR_HOP -> 62f;
            case AS_CHOP -> 90f;
            case AS_SIDE -> 4f;
            case AS_HOOK -> 125f;
            case TS_BACK -> 8f;
            case TS_BELLY -> 45f;
            default -> 18f;
        };
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

    /** The body faces the blow's mob (the look is the camera's); in the stance, the way he
     * began; in the brake, the way he skids (the animation turns him side-on to it). */
    private static void facing(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        Stamina s = Stamina.of(p);
        // the axe's shadow: gone, not drawn (his after-images are, they are drawn on purpose)
        if (s.move == Move.AS_HIDDEN && !drawingGhost) {
            event.setCanceled(true);
            return;
        }
        if (!viewed(s.move)) return;
        float yaw;
        boolean finisherBehind = s.move == Move.GUARD_FINISH && s.moveTick >= GuardAnswers.FIN_JUMP;
        Entity mob = s.move.timeStopBlow() || s.move.crLeap() || finisherBehind ? p.level().getEntity(s.target) : null;
        if (mob != null) yaw = TimeStop.facing(p.position(), mob.position());
        else if (s.move == Move.GUARD_FINISH) return;
        else if (s.move == Move.TS_BRAKE) yaw = s.target;
        else if (p == Minecraft.getInstance().player && anchor != null) yaw = anchorYaw;
        else return;
        p.yBodyRot = p.yBodyRotO = yaw;
        p.yHeadRot = p.yHeadRotO = yaw;
    }

    private static void draw(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) ghosts(event);
        else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            slashes(event);
            rings(event);
        }
    }

    // ------------------------------------------------------------------ the moves of 2026-11-20 (HeavyMoves)

    private record Ring(Vec3 at, float reach, long born) {}

    private static final List<Ring> RINGS = new ArrayList<>();
    /** A shock ring: how long it runs out and fades (ticks). */
    private static final int RING_TICKS = 12;

    /** Their slashes and shock rings, at their hits, seen by every client. */
    private static void heavyFx(Player p, Stamina s, long now) {
        Move m = s.move;
        int t = s.moveTick;
        Vec3 look = p.getViewVector(1f);
        look = new Vec3(look.x, 0, look.z).normalize();
        if (m == Move.AX_SLAM && t == dev.mcreload.moves.HeavyMoves.SLAM_HIT) {
            RINGS.add(new Ring(p.position(), dev.mcreload.moves.MovesConfig.slamRadius(), now));
            RINGS.add(new Ring(p.position(), 2.5f, now));
        }
        if (m == Move.AX_HEAVY) {
            // the three big ones (author: "the first along from above, the second from the side, the last from below upwards")
            float[] rolls = {127f, 6f, 53f};
            for (int i = 0; i < 3; i++) {
                if (t != dev.mcreload.moves.HeavyMoves.HEAVY_HITS[i]) continue;
                SLASHES.add(new Slash(p.getEyePosition().add(look.scale(1.9)).subtract(0, 0.35, 0), rolls[i], 2.3f, now));
            }
        }
        Entity mob = s.target >= 0 ? p.level().getEntity(s.target) : null;
        if (m.axeChain() && t == dev.mcreload.moves.HeavyMoves.chainHit(m) && mob != null) {
            float roll = switch (m) {
                case AXC_SPIN -> 172f;
                case AXC_UPPER -> 70f;
                case AXC_KICK -> 20f;
                default -> 95f;
            };
            SLASHES.add(new Slash(mob.position().add(0, mob.getBbHeight() * 0.6, 0), roll, 1.5f, now));
            RINGS.add(new Ring(mob.position(), 2.6f, now));
        }
        if (m == Move.SW_KNEE && t == dev.mcreload.moves.HeavyMoves.KNEE_HIT && mob != null) {
            Vec3 head = mob.position().add(0, mob.getBbHeight() * 0.85, 0);
            SLASHES.add(new Slash(head, 90f, 1.8f, now));
            SLASHES.add(new Slash(head, 30f, 1.5f, now));
            SLASHES.add(new Slash(head, 150f, 1.5f, now));
            RINGS.add(new Ring(mob.position(), 3.5f, now));
        }
        if (m == Move.SW_FLING && t == dev.mcreload.moves.HeavyMoves.FLING_HIT && mob != null) {
            SLASHES.add(new Slash(mob.position().add(0, mob.getBbHeight() * 0.5, 0), 40f, 1.4f, now));
        }
        if (m == Move.XB_BELLY && t == dev.mcreload.moves.HeavyMoves.BELLY_HIT) RINGS.add(new Ring(p.position(), 2.2f, now));
        if (m == Move.BW_RAIN && t == dev.mcreload.moves.HeavyMoves.RAIN_LAND) RINGS.add(new Ring(p.position(), 4f, now));
    }

    /** The shock rings: a ring of pixel squares on the ground running out and fading, in the slashes' look. */
    private static void rings(RenderLevelStageEvent event) {
        if (RINGS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        org.joml.Matrix4f m = event.getPoseStack().last().pose();
        for (Ring r : RINGS) {
            float f = Math.min(1f, (now - r.born + pt) / RING_TICKS);
            float reach = r.reach * (1f - (1f - f) * (1f - f));
            int alpha = Math.round(200 * (1f - f));
            if (alpha <= 2 || reach < 0.2f) continue;
            float px = 0.16f;
            int n = Math.max(12, (int) (2 * Math.PI * reach / 0.32f));
            float y = (float) (r.at.y - cam.y) + 0.06f;
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                // every pixel on the ring, and a ragged second row inside it now and then
                for (int row = 0; row < 2; row++) {
                    if (row == 1 && (i * 7 + (int) (f * 10)) % 3 != 0) continue;
                    float rr = reach - row * px * 1.4f;
                    float x = (float) (r.at.x + Math.cos(a) * rr - cam.x), z = (float) (r.at.z + Math.sin(a) * rr - cam.z);
                    int c = row == 0 ? 255 : 190;
                    vc.vertex(m, x - px, y, z - px).color(c, c, c, alpha).endVertex();
                    vc.vertex(m, x - px, y, z + px).color(c, c, c, alpha).endVertex();
                    vc.vertex(m, x + px, y, z + px).color(c, c, c, alpha).endVertex();
                    vc.vertex(m, x + px, y, z - px).color(c, c, c, alpha).endVertex();
                    vc.vertex(m, x + px, y, z - px).color(c, c, c, alpha).endVertex();
                    vc.vertex(m, x + px, y, z + px).color(c, c, c, alpha).endVertex();
                    vc.vertex(m, x - px, y, z + px).color(c, c, c, alpha).endVertex();
                    vc.vertex(m, x - px, y, z - px).color(c, c, c, alpha).endVertex();
                }
            }
        }
        buffers.endBatch(RenderType.lightning());
    }

    // ------------------------------------------------------------------ the after-images

    /** The after-images: the player's body drawn again where they were, pale, fading out. */
    private static void ghosts(RenderLevelStageEvent event) {
        if (GHOSTS.isEmpty() || drawingGhost) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        drawingGhost = true;
        try {
            for (Ghost g : GHOSTS) {
                float age = now - g.born + pt;
                float alpha = 0.6f * Math.max(0f, 1f - age / GHOST_TICKS);
                if (alpha <= 0.01f || !(mc.level.getEntity(g.entity) instanceof AbstractClientPlayer p)) continue;
                float bodyWas = p.yBodyRot, bodyWasO = p.yBodyRotO;
                p.yBodyRot = p.yBodyRotO = g.yaw;
                mc.getEntityRenderDispatcher().render(p, g.at.x - cam.x, g.at.y - cam.y, g.at.z - cam.z, g.yaw, pt, ps,
                        new Pale(buffers, p.getSkinTextureLocation().toString(), alpha), 0xF000F0);
                p.yBodyRot = bodyWas;
                p.yBodyRotO = bodyWasO;
            }
            buffers.endBatch();
        } finally {
            drawingGhost = false;
        }
    }

    /**
     * The body and the blade in hand only (no armour, cape or glint), see-through and cold white: the skin's layers into
     * the translucent entity type, the item atlas into the translucent item sheet, the alpha taken down.
     */
    private record Pale(MultiBufferSource to, String skin, float alpha) implements MultiBufferSource {
        private static final Map<RenderType, Integer> KIND = new IdentityHashMap<>();

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            int kind = KIND.computeIfAbsent(type, t -> {
                String s = t.toString();
                if (s.contains("glint")) return 0;
                if (s.contains("textures/atlas/blocks.png")) return 2;
                return s.contains("skins/") || s.contains("skin") ? 1 : 0;
            });
            if (kind == 0) return NOTHING;
            if (kind == 1 && !type.toString().contains(skin)) return NOTHING;
            RenderType into = kind == 1 ? RenderType.entityTranslucent(new net.minecraft.resources.ResourceLocation(skin)) : Sheets.translucentItemSheet();
            return new Fade(to.getBuffer(into), alpha);
        }
    }

    private static final VertexConsumer NOTHING = new VertexConsumer() {
        public VertexConsumer vertex(double x, double y, double z) { return this; }
        public VertexConsumer color(int r, int g, int b, int a) { return this; }
        public VertexConsumer uv(float u, float v) { return this; }
        public VertexConsumer overlayCoords(int u, int v) { return this; }
        public VertexConsumer uv2(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void endVertex() {}
        public void defaultColor(int r, int g, int b, int a) {}
        public void unsetDefaultColor() {}
    };

    /** A vertex consumer passing everything on, the colour paled to a cold white and its alpha taken down. */
    private record Fade(VertexConsumer to, float alpha) implements VertexConsumer {
        public VertexConsumer vertex(double x, double y, double z) {
            to.vertex(x, y, z);
            return this;
        }

        public VertexConsumer color(int r, int g, int b, int a) {
            to.color(150 + r * 105 / 255, 170 + g * 85 / 255, 255, Math.round(a * alpha));
            return this;
        }

        public VertexConsumer uv(float u, float v) {
            to.uv(u, v);
            return this;
        }

        public VertexConsumer overlayCoords(int u, int v) {
            to.overlayCoords(OverlayTexture.NO_OVERLAY);
            return this;
        }

        public VertexConsumer uv2(int u, int v) {
            to.uv2(0xF0, 0xF0);
            return this;
        }

        public VertexConsumer normal(float x, float y, float z) {
            to.normal(x, y, z);
            return this;
        }

        public void endVertex() {
            to.endVertex();
        }

        public void defaultColor(int r, int g, int b, int a) {
            to.defaultColor(r, g, b, a);
        }

        public void unsetDefaultColor() {
            to.unsetDefaultColor();
        }
    }

    // ------------------------------------------------------------------ the slashes

    /** The slash's sprite: a pixel crescent, 4 frames of it falling apart (32 x 16 each, one under the other). */
    private static final net.minecraft.resources.ResourceLocation SLASH =
            new net.minecraft.resources.ResourceLocation(dev.mcreload.moves.KinetrixMoves.MODID, "textures/effect/ts_slash.png");
    private static final int SLASH_FRAMES = 4;

    /**
     * The slashes: the pixel crescent facing the camera, turned its own way, in Minecraft's own look (author 19.11: "pixelly,
     * as the campfire's smoke or the sword's sweep", not smooth): whole at first, then falling apart pixel by pixel as it
     * fades out.
     */
    private static void slashes(RenderLevelStageEvent event) {
        if (SLASHES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        float pt = event.getPartialTick();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucentEmissive(SLASH);
        VertexConsumer vc = buffers.getBuffer(type);
        PoseStack.Pose pose = event.getPoseStack().last();
        for (Slash s : SLASHES) {
            float age = now - s.born + pt;
            float life = Math.min(1f, Math.max(0f, (age - SLASH_FULL) / (SLASH_TICKS - SLASH_FULL)));
            int frame = Math.min(SLASH_FRAMES - 1, (int) (life * SLASH_FRAMES));
            // the alpha in steps too, as a particle's
            float a = Math.round((1f - life * 0.75f) * 4f) / 4f;
            if (a <= 0.01f) continue;
            double r = Math.toRadians(s.roll);
            Vector3f u = new Vector3f(left).mul((float) -Math.cos(r)).add(new Vector3f(up).mul((float) Math.sin(r)));
            Vector3f v = new Vector3f(left).mul((float) Math.sin(r)).add(new Vector3f(up).mul((float) Math.cos(r)));
            float w = 1.25f * s.size, h = w / 2f;
            float cx = (float) (s.at.x - cam.x), cy = (float) (s.at.y - cam.y), cz = (float) (s.at.z - cam.z);
            float v0 = frame / (float) SLASH_FRAMES, v1 = (frame + 1) / (float) SLASH_FRAMES;
            int alpha = Math.round(255 * a);
            corner(vc, pose, cx - u.x * w - v.x * h, cy - u.y * w - v.y * h, cz - u.z * w - v.z * h, 0f, v1, alpha);
            corner(vc, pose, cx + u.x * w - v.x * h, cy + u.y * w - v.y * h, cz + u.z * w - v.z * h, 1f, v1, alpha);
            corner(vc, pose, cx + u.x * w + v.x * h, cy + u.y * w + v.y * h, cz + u.z * w + v.z * h, 1f, v0, alpha);
            corner(vc, pose, cx - u.x * w + v.x * h, cy - u.y * w + v.y * h, cz - u.z * w + v.z * h, 0f, v0, alpha);
        }
        buffers.endBatch(type);
    }

    private static void corner(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int alpha) {
        vc.vertex(pose.pose(), x, y, z).color(255, 255, 255, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(pose.normal(), 0f, 1f, 0f).endVertex();
    }

    private TimeStopView() {}
}
