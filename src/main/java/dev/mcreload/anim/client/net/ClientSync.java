package dev.mcreload.anim.client.net;

import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.client.rig.RigRegistry;
import dev.mcreload.anim.config.Cfg;
import dev.mcreload.anim.net.AnimPacket;
import dev.mcreload.anim.net.KinetrixNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;

/**
 * Client end of the multiplayer sync: tells the server what only this client knows about you
 * (keys held, aiming at a block, emote / fidget / landing choices), and applies what it hears
 * about other players to their rigs.
 */
public final class ClientSync {
    private static int lastBits = -1;
    private static int lastSendTick;
    private static ClientPacketListener lastConnection;

    private static boolean canSend() {
        if (!Cfg.sync || !Cfg.enabled) return false;
        return serverHasKinetrix();
    }

    /** The server we are on runs Kinetrix (always true in single player and for a LAN host). */
    public static boolean serverHasKinetrix() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener conn = mc.getConnection();
        if (conn == null || mc.player == null) return false;
        if (conn != lastConnection) {
            lastConnection = conn;
            lastBits = -1;
            dev.mcreload.anim.net.Posture.CLIENT_SIT.clear();
        }
        try {
            return KinetrixNet.CHANNEL.isRemotePresent(conn.getConnection());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Once per client tick: send the held keys when they change. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !canSend()) return;
        int bits = 0;
        if (p.input != null) {
            if (p.input.forwardImpulse > 0.1f) bits |= AnimPacket.BIT_FORWARD;
            if (p.input.forwardImpulse < -0.1f) bits |= AnimPacket.BIT_BACK;
            if (p.input.leftImpulse > 0.1f) bits |= AnimPacket.BIT_LEFT;
            if (p.input.leftImpulse < -0.1f) bits |= AnimPacket.BIT_RIGHT;
            if (p.input.jumping) bits |= AnimPacket.BIT_JUMP;
            if (p.input.shiftKeyDown) bits |= AnimPacket.BIT_SNEAK;
        }
        if (mc.screen == null) {
            if (mc.options.keyUse.isDown()) bits |= AnimPacket.BIT_USE;
            if (mc.options.keyAttack.isDown()) bits |= AnimPacket.BIT_ATTACK;
        }
        bits |= dev.mcreload.anim.client.ScreenPoses.of(mc.screen) << AnimPacket.SCREEN_SHIFT;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) bits |= AnimPacket.BIT_TARGET_BLOCK;
        // On change, and now and then anyway (cheap) so nothing stays stale.
        if (bits != lastBits || p.tickCount - lastSendTick > 100) {
            lastBits = bits;
            lastSendTick = p.tickCount;
            KinetrixNet.CHANNEL.sendToServer(new AnimPacket(0, AnimPacket.STATE, bits));
        }
    }

    /** Crawl / sit requests: sent whenever the server can take them, even with sync turned off. */
    public static void posture(byte type, int value) {
        if (!serverHasKinetrix()) return;
        KinetrixNet.CHANNEL.sendToServer(new AnimPacket(0, type, value));
    }

    /** A one-off choice of your own player (emote, fidget, landing) for the others to see. */
    public static void event(AbstractClientPlayer player, byte type, int value) {
        if (player != Minecraft.getInstance().player || !canSend()) return;
        KinetrixNet.CHANNEL.sendToServer(new AnimPacket(0, type, value));
    }

    public static void receive(AnimPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !p.valid()) return;
        Entity e = mc.level.getEntity(p.entityId());
        if (!(e instanceof AbstractClientPlayer player)) return;
        if (player == mc.player) {
            // The server ended our crawl or stood us up (or refused).
            if (p.type() == AnimPacket.CRAWL || p.type() == AnimPacket.SIT) dev.mcreload.anim.client.PostureControl.serverReset(p.type());
            return;
        }
        if (p.type() == AnimPacket.SIT) {
            boolean changed = p.value() > 0 ? dev.mcreload.anim.net.Posture.CLIENT_SIT.add(player.getUUID())
                    : dev.mcreload.anim.net.Posture.CLIENT_SIT.remove(player.getUUID());
            if (changed) player.refreshDimensions();
        }
        PlayerRig rig = RigRegistry.get(player);
        if (rig != null) rig.motion.onRemote(p.type(), p.value());
    }

    private ClientSync() {}
}
