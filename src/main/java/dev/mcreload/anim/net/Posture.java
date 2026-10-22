package dev.mcreload.anim.net;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Crawling and sitting on request (keys C and X), the server side and what both sides share.
 *
 * <p>Crawling is the vanilla crawl pose (the one you get under a trapdoor) forced on: the hitbox is
 * one block high, so the player fits through one-block gaps. The server has to agree, so it is
 * only offered when the server runs Kinetrix (single player and LAN hosts always do).</p>
 *
 * <p>Sitting lowers the hitbox and the eyes; the server keeps the list and passes it on, so
 * everyone sees who is sitting.</p>
 */
public final class Posture {
    /** Number of sitting styles (SIT packet values 1..SIT_STYLES). */
    public static final int SIT_STYLES = 3;
    /** Hitbox height and eye height while sitting on the ground (standing: 1.8 and 1.62). */
    public static final float SIT_HEIGHT = 1.2f, SIT_EYE = 1.0f;

    /** Who is sitting, as the server sees it (value = style 1..3). */
    private static final Map<UUID, Integer> SERVER_SIT = new ConcurrentHashMap<>();
    /** Players this server made crawl on their request. */
    private static final Set<UUID> SERVER_CRAWL = ConcurrentHashMap.newKeySet();
    /**
     * Who is sitting, as this client knows it (only when the server takes part, so the hitbox here
     * matches the server's). Client thread only.
     */
    public static final Set<UUID> CLIENT_SIT = ConcurrentHashMap.newKeySet();

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(Posture::onSize);
        MinecraftForge.EVENT_BUS.addListener(Posture::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(Posture::onLogout);
        MinecraftForge.EVENT_BUS.addListener(Posture::onRespawn);
        MinecraftForge.EVENT_BUS.addListener(Posture::onChangeDimension);
    }

    // ------------------------------------------------------------------ rules

    public static boolean canCrawl(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.isPassenger() && !p.isSleeping() && !p.isFallFlying()
                && !p.getAbilities().flying && !p.isAutoSpinAttack();
    }

    /** Sitting down: standing (not sneaking) on the ground, out of water. */
    public static boolean canSit(Player p) {
        return canKeepSitting(p) && p.onGround() && p.getPose() == Pose.STANDING && !p.isVisuallyCrawling();
    }

    public static boolean canKeepSitting(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.isPassenger() && !p.isSleeping() && !p.isFallFlying()
                && !p.getAbilities().flying && !p.isInWater() && !p.isInLava() && !p.isAutoSpinAttack()
                && (p.onGround() || p.getDeltaMovement().y > -0.2);
    }

    // ------------------------------------------------------------------ requests from clients

    static void requestCrawl(ServerPlayer p, boolean on) {
        if (on && canCrawl(p)) {
            if (SERVER_SIT.containsKey(p.getUUID())) standUp(p, false);
            SERVER_CRAWL.add(p.getUUID());
            p.setForcedPose(Pose.SWIMMING);
        } else {
            boolean was = SERVER_CRAWL.remove(p.getUUID());
            if (was && p.getForcedPose() == Pose.SWIMMING) p.setForcedPose(null);
            if (on) KinetrixNet.toOwner(p, AnimPacket.CRAWL, 0);   // refused
        }
    }

    static void requestSit(ServerPlayer p, int style) {
        if (style > 0) {
            if (!canSit(p) && !SERVER_SIT.containsKey(p.getUUID())) {
                KinetrixNet.toOwner(p, AnimPacket.SIT, 0);          // refused
                return;
            }
            SERVER_SIT.put(p.getUUID(), style);
            p.refreshDimensions();
            KinetrixNet.relay(p, AnimPacket.SIT, style);
        } else if (SERVER_SIT.containsKey(p.getUUID())) {
            standUp(p, false);
        }
    }

    private static void standUp(ServerPlayer p, boolean tellOwner) {
        SERVER_SIT.remove(p.getUUID());
        p.refreshDimensions();
        KinetrixNet.relay(p, AnimPacket.SIT, 0);
        if (tellOwner) KinetrixNet.toOwner(p, AnimPacket.SIT, 0);
    }

    // ------------------------------------------------------------------ events

    /** Smaller hitbox and lower eyes while sitting (both sides, each from its own list). */
    private static void onSize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player p) || event.getPose() != Pose.STANDING) return;
        if (p.level() == null) return;
        UUID id = p.getUUID();
        boolean sitting = p.level().isClientSide ? CLIENT_SIT.contains(id) : SERVER_SIT.containsKey(id);
        if (!sitting) return;
        EntityDimensions d = event.getNewSize();
        event.setNewSize(EntityDimensions.scalable(d.width, Math.min(d.height, SIT_HEIGHT)));
        event.setNewEyeHeight(Math.min(event.getNewEyeHeight(), SIT_EYE));
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        UUID id = p.getUUID();
        if (SERVER_SIT.containsKey(id) && (!canKeepSitting(p) || p.getPose() != Pose.STANDING)) standUp(p, true);
        if (SERVER_CRAWL.contains(id) && !canCrawl(p)) {
            SERVER_CRAWL.remove(id);
            if (p.getForcedPose() == Pose.SWIMMING) p.setForcedPose(null);
            KinetrixNet.toOwner(p, AnimPacket.CRAWL, 0);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity(), false);
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        forget(event.getEntity(), true);
    }

    private static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        forget(event.getEntity(), true);
    }

    /** A new player entity (respawn, other dimension) or gone: nothing carries over. */
    private static void forget(Player p, boolean tell) {
        if (!(p instanceof ServerPlayer sp)) return;
        UUID id = p.getUUID();
        boolean sat = SERVER_SIT.remove(id) != null;
        boolean crawled = SERVER_CRAWL.remove(id);
        if (crawled && sp.getForcedPose() == Pose.SWIMMING) sp.setForcedPose(null);
        if (sat) {
            sp.refreshDimensions();
            KinetrixNet.relay(sp, AnimPacket.SIT, 0);
        }
        if (!tell) return;
        if (sat) KinetrixNet.toOwner(sp, AnimPacket.SIT, 0);
        if (crawled) KinetrixNet.toOwner(sp, AnimPacket.CRAWL, 0);
    }

    private Posture() {}
}
