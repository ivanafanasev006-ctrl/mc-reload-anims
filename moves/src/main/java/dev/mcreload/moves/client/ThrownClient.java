package dev.mcreload.moves.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mcreload.moves.Thrown;
import dev.mcreload.moves.net.MovesNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.ThrownTridentRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Thrown weapons on the client (Thrown): what each trident that flies as one is (the server says, MovesNet.ThrownInfo), drawn
 * as the weapon, point first; stuck in a mob it goes about with the mob's own smooth position (the trident's is sent
 * seldom). The pull's target: the local player's weapon stuck in the mob in front within reach.
 */
public final class ThrownClient {
    private static final Map<Integer, MovesNet.ThrownInfo> INFO = new HashMap<>();
    /** The mob the throw's aim is on (its red square), -1: none. */
    static int aimed = -1;

    public static void info(MovesNet.ThrownInfo m) {
        if (m.item().isEmpty()) INFO.remove(m.entity());
        else INFO.put(m.entity(), m);
    }

    static void reset() {
        INFO.clear();
    }

    /** The local player's weapon stuck in {@code looked} (or, without one, in the nearest mob in front) within reach: the trident's id, or -1. */
    static int pullable(LocalPlayer p, Entity looked) {
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        // one whose weapon this client no longer has is forgotten (picked up, gone, out of sight: told again on coming near)
        INFO.keySet().removeIf(id -> p.level().getEntity(id) == null);
        for (MovesNet.ThrownInfo m : INFO.values()) {
            if (m.owner() != p.getId() || m.stuckIn() < 0) continue;
            if (!(p.level().getEntity(m.stuckIn()) instanceof LivingEntity mob) || !mob.isAlive()) continue;
            Vec3 to = new Vec3(mob.getX() - p.getX(), 0, mob.getZ() - p.getZ());
            double d = to.length() - mob.getBbWidth() / 2;
            if (d > Thrown.PULL_REACH || Math.abs(mob.getY() - p.getY()) > 1.5) continue;
            Vec3 look = p.getViewVector(1f);
            look = new Vec3(look.x, 0, look.z);
            boolean ahead = looked == mob || to.lengthSqr() < 1.0E-4
                    || Math.toDegrees(Math.acos(Mth.clamp(look.normalize().dot(to.normalize()), -1, 1))) <= 60;
            if (!ahead || d >= bestDist) continue;
            best = m.entity();
            bestDist = d;
        }
        return best;
    }

    /** The mob the weapon with this id is stuck in, or -1. */
    static int stuckIn(int weapon) {
        MovesNet.ThrownInfo m = INFO.get(weapon);
        return m == null ? -1 : m.stuckIn();
    }

    /** Draws a trident carrying a weapon as the weapon; any other trident as vanilla does. */
    public static final class Renderer extends EntityRenderer<ThrownTrident> {
        private final ThrownTridentRenderer vanilla;
        private final ItemRenderer items;

        public Renderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            vanilla = new ThrownTridentRenderer(ctx);
            items = ctx.getItemRenderer();
        }

        @Override
        public void render(ThrownTrident t, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
            MovesNet.ThrownInfo m = INFO.get(t.getId());
            if (m == null) {
                vanilla.render(t, yaw, partial, pose, buffers, light);
                return;
            }
            pose.pushPose();
            float y, x;
            Entity mob = m.stuckIn() >= 0 ? t.level().getEntity(m.stuckIn()) : null;
            if (mob != null) {
                // on the mob, where it went in, as it moves (smoothly: its own position, not the weapon's)
                Vec3 at = mob.getPosition(partial).add(m.ox(), m.oy(), m.oz()).subtract(t.getPosition(partial));
                pose.translate(at.x, at.y, at.z);
                y = m.yaw();
                x = m.pitch();
            } else {
                y = Mth.lerp(partial, t.yRotO, t.getYRot());
                x = Mth.lerp(partial, t.xRotO, t.getXRot());
            }
            // as an arrow is turned (local +X along the flight), the sprite's point (its top right) along +X
            pose.mulPose(Axis.YP.rotationDegrees(y - 90f));
            pose.mulPose(Axis.ZP.rotationDegrees(x));
            pose.mulPose(Axis.ZP.rotationDegrees(-45f));
            items.renderStatic(m.item(), ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers, t.level(), t.getId());
            pose.popPose();
        }

        @Override
        public ResourceLocation getTextureLocation(ThrownTrident t) {
            return vanilla.getTextureLocation(t);
        }

        @Override
        public boolean shouldRender(ThrownTrident t, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
            // stuck in a mob it is drawn where the mob is
            return INFO.containsKey(t.getId()) || super.shouldRender(t, frustum, x, y, z);
        }
    }

    /** The weapon (its id) is an axe: for the pull's hint. */
    static boolean axe(int weapon) {
        MovesNet.ThrownInfo m = INFO.get(weapon);
        return m != null && m.item().getItem() instanceof net.minecraft.world.item.AxeItem;
    }

    /** Whether the weapon is out of the hand (a throw going on or its weapon in the world): for the hint. */
    static boolean anyOf(LocalPlayer p) {
        for (MovesNet.ThrownInfo m : INFO.values()) if (m.owner() == p.getId()) return true;
        return false;
    }

    private ThrownClient() {}
}
