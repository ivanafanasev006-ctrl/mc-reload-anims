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
        return request(p, move, held, -1);
    }

    /** {@code target}: the mob a vault goes over or a finisher is made on (its entity id), -1 for other moves. */
    public static boolean request(ServerPlayer p, Move move, boolean held, int target) {
        Stamina s = Stamina.of(p);
        net.minecraft.world.entity.Entity enemy = move.targeted() ? p.level().getEntity(target) : null;
        if (!allowed(p, s, move) || !validTarget(p, move, enemy)) {
            MovesNet.deny(p, move);
            sync(p, s, true);
            return false;
        }
        s.spend(move.cost(), p);
        Guard.lower(p, s);
        s.start(p, move, held);
        if (move == Move.HOP) Hop.started(p, s, enemy);
        if (move == Move.FINISHER) Strikes.finisherStarted(p, s, enemy);
        if (move == Move.BARRAGE) p.stopUsingItem();     // the volley takes over the drawn bow
        MovesNet.play(p, move);
        sync(p, s, true);
        return true;
    }

    /**
     * The player broke off their move: a slide by a jump or a blow, any move's end (once its push is
     * over) by running or striking. A hop broken off over its landing still gives its Speed (this
     * end may hear of it before its own landing tick, the client being ahead).
     */
    public static void cancel(ServerPlayer p, Move move) {
        Stamina s = Stamina.of(p);
        if (s.move != move) return;
        if (move == Move.HOP && s.moveTick >= Move.HOP_TAKEOFF) Hop.landed(p, s);
        s.end(p);
        MovesNet.stop(p);
    }

    /** The mob a targeted move is made at is one it may be made at (the client named it; the server checks). */
    private static boolean validTarget(ServerPlayer p, Move move, net.minecraft.world.entity.Entity e) {
        if (move == Move.HOP) return Hop.valid(p, e);
        if (move == Move.FINISHER) return e instanceof net.minecraft.world.entity.LivingEntity le && Strikes.finishable(p, le, false);
        return true;
    }

    public static boolean allowed(Player p, Stamina s, Move move) {
        if (!p.isAlive() || p.isSpectator() || p.isPassenger() || p.isSleeping() || p.isFallFlying()
                || p.getAbilities().flying || p.isInWater() || p.isInLava()) return false;
        // Using an item (eating, drawing a bow) rules moves out, except the volley, which needs a full
        // draw, and the moves of the flow and the Legolas stance, made with the bow drawn.
        if (move == Move.BARRAGE ? !Bow.volleyReady(p) : move.drawnBow() ? !Bow.drawing(p) : p.isUsingItem()) return false;
        // One move at a time; the next may start once this one has played out (its fade aside).
        if (s.move != null && s.moveTick < s.move.freeFrom()) return false;
        if (!s.canAfford(move, p)) return false;
        boolean ground = p.onGround() || p.tickCount - lastGround(p) <= GROUND_GRACE;
        boolean sprint = p.isSprinting() || p.tickCount - lastSprint(p) <= GROUND_GRACE;
        if (move == Move.SLIDE) return ground && sprint;
        // The sword moves of the left button: a sword in hand; the dash off a sprint.
        if (move.strike() && !Strikes.sword(p)) return false;
        if (move == Move.DASH) return ground && sprint;
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
