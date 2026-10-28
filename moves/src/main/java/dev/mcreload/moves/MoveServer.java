package dev.mcreload.moves;

import dev.mcreload.moves.net.MovesNet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The server's side of a move: is it allowed, pay for it, tell everyone who can see the player,
 * and keep the player's client up to date with their bars.
 */
public final class MoveServer {
    /** A request this late after leaving the ground still counts as from the ground (lag). */
    private static final int GROUND_GRACE = 4;

    /**
     * A player's client asks for a move. Returns whether it is done; if not, their client is told to
     * stop the move it has already started.
     */
    public static boolean request(ServerPlayer p, Move move, boolean held) {
        Stamina s = Stamina.of(p);
        if (!allowed(p, s, move)) {
            MovesNet.deny(p, move);
            sync(p, s, true);
            return false;
        }
        s.spend(move.cost(), p);
        Guard.lower(p, s);
        s.start(p, move, held);
        MovesNet.play(p, move);
        sync(p, s, true);
        return true;
    }

    /** The player cancelled their slide (jump or attack). */
    public static void cancel(ServerPlayer p, Move move) {
        Stamina s = Stamina.of(p);
        if (s.move != move) return;
        s.end(p);
        MovesNet.stop(p);
    }

    public static boolean allowed(Player p, Stamina s, Move move) {
        if (!p.isAlive() || p.isSpectator() || p.isPassenger() || p.isSleeping() || p.isFallFlying()
                || p.getAbilities().flying || p.isInWater() || p.isInLava() || p.isUsingItem()) return false;
        // One move at a time; the next may start once this one has played out (its fade aside).
        if (s.move != null && s.moveTick < s.move.length - 3) return false;
        if (!s.canAfford(move, p)) return false;
        boolean ground = p.onGround() || p.tickCount - lastGround(p) <= GROUND_GRACE;
        if (move == Move.SLIDE) return ground && (p.isSprinting() || p.tickCount - lastSprint(p) <= GROUND_GRACE);
        return ground || !MovesConfig.dodgeOnlyOnGround();
    }

    // ------------------------------------------------------------------ what the server remembers

    private static final java.util.Map<java.util.UUID, int[]> SEEN = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, float[]> SENT = new java.util.concurrent.ConcurrentHashMap<>();

    private static int lastGround(Player p) {
        int[] seen = SEEN.get(p.getUUID());
        return seen == null ? Integer.MIN_VALUE / 2 : seen[0];
    }

    private static int lastSprint(Player p) {
        int[] seen = SEEN.get(p.getUUID());
        return seen == null ? Integer.MIN_VALUE / 2 : seen[1];
    }

    /** Every server tick after the bars were refilled. */
    static void afterTick(ServerPlayer p, Stamina s) {
        int[] seen = SEEN.computeIfAbsent(p.getUUID(), u -> new int[]{Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2});
        if (p.onGround()) seen[0] = p.tickCount;
        if (p.isSprinting()) seen[1] = p.tickCount;
        sync(p, s, false);
    }

    /** Sends the bars to the player when they changed enough to show (or {@code now}). */
    static void sync(ServerPlayer p, Stamina s, boolean now) {
        float[] last = SENT.computeIfAbsent(p.getUUID(), u -> new float[]{-1, -1, -1, -1, -1, -1});
        float maxE = MovesConfig.maxEnergy(), maxS = MovesConfig.maxShields();
        // The bar is 71 pixels: a quarter of a pixel is invisible; free time counts by the second.
        boolean changed = Math.abs(last[0] - s.energy) >= maxE / 284f || last[1] != s.shields
                || last[2] != maxE || last[3] != maxS || (int) last[4] != s.freeTicks / 20 || (int) last[5] != s.freeTotal;
        if (!changed && !now) return;
        last[0] = s.energy;
        last[1] = s.shields;
        last[2] = maxE;
        last[3] = maxS;
        last[4] = s.freeTicks / 20;
        last[5] = s.freeTotal;
        MovesNet.stamina(p, s);
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SEEN.remove(event.getEntity().getUUID());
        SENT.remove(event.getEntity().getUUID());
    }

    /** A new player entity (respawn, other dimension): send the bars again. */
    @SubscribeEvent
    public static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    private MoveServer() {}
}
