package dev.mcreload.anim.net;

import dev.mcreload.anim.McReloadAnim;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multiplayer sync. Animations are computed on every client from what the game shows, but some
 * things only the player's own client knows: keys held, whether you are mining or fighting, which
 * emote or fidget you chose, how you landed. Clients send those here; the server passes them on
 * to everyone who can see that player.
 *
 * <p>Optional on both ends: a server without Kinetrix simply never hears from us (clients check
 * that the channel is present before sending) and a client without it is never sent anything.</p>
 */
public final class KinetrixNet {
    public static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(McReloadAnim.MODID, "sync"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    /** Latest STATE and EMOTE of every player, so players who come into view get them too. */
    private static final Map<UUID, int[]> LAST = new ConcurrentHashMap<>();
    /** Simple per-player flood guard: packets allowed per second. */
    private static final int BUDGET = 40;
    private static final Map<UUID, long[]> RATE = new ConcurrentHashMap<>();

    public static void init() {
        CHANNEL.messageBuilder(AnimPacket.class, 0)
                .encoder(AnimPacket::encode)
                .decoder(AnimPacket::decode)
                .consumerMainThread(AnimPacket::handle)
                .add();
        MinecraftForge.EVENT_BUS.addListener(KinetrixNet::onStartTracking);
        MinecraftForge.EVENT_BUS.addListener(KinetrixNet::onLogout);
    }

    static void relay(ServerPlayer sender, AnimPacket p) {
        if (sender == null || !p.valid() || !allowed(sender)) return;
        AnimPacket out = new AnimPacket(sender.getId(), p.type(), p.value());
        if (p.type() == AnimPacket.STATE || p.type() == AnimPacket.EMOTE) {
            int[] last = LAST.computeIfAbsent(sender.getUUID(), u -> new int[]{0, -1});
            last[p.type() == AnimPacket.STATE ? 0 : 1] = p.value();
        }
        // Everyone near enough to see this player, who has Kinetrix (others never get our packets).
        double range = Math.max(64.0, sender.server.getPlayerList().getViewDistance() * 16.0);
        for (ServerPlayer viewer : sender.serverLevel().players()) {
            if (viewer == sender || viewer.distanceToSqr(sender) > range * range || !hasKinetrix(viewer)) continue;
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), out);
        }
    }

    /** A real, connected player whose client has Kinetrix (fake players from other mods have no client). */
    private static boolean hasKinetrix(ServerPlayer p) {
        if (p instanceof net.minecraftforge.common.util.FakePlayer || p.connection == null) return false;
        try {
            return CHANNEL.isRemotePresent(p.connection.connection);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean allowed(ServerPlayer p) {
        long now = System.currentTimeMillis() / 1000L;
        long[] r = RATE.computeIfAbsent(p.getUUID(), u -> new long[2]);
        if (r[0] != now) {
            r[0] = now;
            r[1] = 0;
        }
        return ++r[1] <= BUDGET;
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        Entity target = event.getTarget();
        if (!(target instanceof ServerPlayer tracked) || !(event.getEntity() instanceof ServerPlayer viewer)) return;
        int[] last = LAST.get(tracked.getUUID());
        if (last == null || !hasKinetrix(viewer)) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new AnimPacket(tracked.getId(), AnimPacket.STATE, last[0]));
        if (last[1] >= 0) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new AnimPacket(tracked.getId(), AnimPacket.EMOTE, last[1]));
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
        RATE.remove(event.getEntity().getUUID());
    }

    private KinetrixNet() {}
}
