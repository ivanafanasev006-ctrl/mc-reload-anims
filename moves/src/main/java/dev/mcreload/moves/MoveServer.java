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
    /** W + Space + R: the kick or the dive may start this late in a jump (a vanilla jump is 12 ticks in the air). */
    public static final int JUMP_GRACE = 14;

    /**
     * A player's client asks for a move. Returns whether it is done; if not, their client is told to
     * stop the move it has already started.
     */
    public static boolean request(ServerPlayer p, Move move, boolean held) {
        return request(p, move, held, -1);
    }

    /**
     * {@code target}: the mob a vault, a kick or a finisher is made at, or the player a friend pulls
     * up (an entity id); for the moves on a ledge, which way its wall is (Ledge.id); -1 for the rest.
     */
    public static boolean request(ServerPlayer p, Move move, boolean held, int target) {
        Stamina s = Stamina.of(p);
        net.minecraft.world.entity.Entity enemy = move.targeted() || move.marked() ? p.level().getEntity(target) : null;
        boolean ok = !move.serverStarted() && allowed(p, s, move) && validTarget(p, move, enemy)
                && (!move.ledge() || Ledge.valid(p, s, move, target)) && (!move.marked() || Bow.markedTarget(p, s, enemy));
        if (ok && move == Move.LEDGE_HELP) ok = Ledge.help(p, enemy);
        if (!ok) {
            MovesNet.deny(p, move);
            sync(p, s, true);
            return false;
        }
        s.spend(move.cost(s.move), p);
        if (move.marked()) {
            // the volley at the marked: the step before shoots its arrow if the client went on before
            // this end got to its release; the first step starts the cooldown and pays for the start
            if (s.move != null && s.move.marked()) {
                Bow.markedShot(p, s);
            } else {
                s.markedCooldown = MovesConfig.markedCooldownTicks();
                s.markedShot.clear();
                s.spend(MovesConfig.costMarked(), p);
            }
            s.spend(MovesConfig.costMarkedEach(), p);
        }
        Guard.lower(p, s);
        if (move.ledge() || move == Move.FALL_TUCK) p.stopUsingItem();
        s.start(p, move, held);
        if (move == Move.HOP) Hop.started(p, s, enemy);
        if (move == Move.KICK || move == Move.ROLL_KICK) Kick.started(p, s, enemy);
        if (move == Move.WHIRL) Whirl.started(p, s);
        if (move == Move.SWORD_SPIN) Spin.started(p, s);
        if (move.finisher()) Strikes.finisherStarted(p, s, enemy);
        if (move == Move.DASH) {
            s.dashCooldown = MovesConfig.dashCooldownTicks();
            s.dashHit = false;
        }
        if (move == Move.SLIDE_ATTACK) {
            s.slideAttackCooldown = MovesConfig.slideAttackCooldownTicks();
            s.attackYaw = p.getYRot();
        }
        // Caught by the edge: the fall so far does not hurt (the tuck's own fall does, less: Fall).
        if (move.ledge()) p.fallDistance = 0f;
        if (move.volley() || move.marked()) p.stopUsingItem();     // the volleys take over the drawn bow (no arrow of its own)
        if (move.marked()) {
            s.target = target;
            s.stepShot = false;
        }
        MovesNet.play(p, move, move.ledge() ? s.ledgeDir : -1);
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
        if (move == Move.HOP && s.moveTick >= Move.HOP_SHOVE && !s.hopShoved) Hop.shove(p, s);
        // The dash broken off by running on, its blade still out: it may still find its mob (the
        // client runs a tick or two ahead of this end).
        if (move == Move.DASH && !s.dashHit && s.moveTick >= Strikes.DASH_REACH_FROM) {
            net.minecraft.world.entity.LivingEntity target = Strikes.dashTarget(p);
            if (target != null) {
                s.dashHit = true;
                Strikes.blow(p, target, MovesConfig.dashCrit(), true);
            }
        }
        if ((move == Move.KICK || move == Move.ROLL_KICK) && !s.kickDone && s.moveTick >= Kick.hit(move) - 2
                && p.level().getEntity(s.target) instanceof net.minecraft.world.entity.LivingEntity mob) Kick.kick(p, s, mob);
        s.end(p);
        MovesNet.stop(p);
    }

    /** The mob a targeted move is made at is one it may be made at (the client named it; the server checks). */
    private static boolean validTarget(ServerPlayer p, Move move, net.minecraft.world.entity.Entity e) {
        if (move == Move.HOP) return Hop.valid(p, e);
        if (move == Move.KICK || move == Move.ROLL_KICK) return Kick.valid(p, e);
        if (move == Move.LEDGE_HELP) return e instanceof Player;
        if (move.finisher()) return e instanceof net.minecraft.world.entity.LivingEntity le && Strikes.finishable(p, le, false);
        return true;
    }

    public static boolean allowed(Player p, Stamina s, Move move) {
        if (!p.isAlive() || p.isSpectator() || p.isPassenger() || p.isSleeping() || p.isFallFlying()
                || p.getAbilities().flying || p.isInWater() || p.isInLava()) return false;
        // Using an item (eating, drawing a bow) rules moves out, except the volley, which needs a full
        // draw, and the moves of the flow and the Legolas stance, made with the bow drawn.
        // The ledge and the fall take the hands from whatever they were doing.
        // The bow's G and the volley at the marked take the bow over, drawn or not.
        boolean hands = move.ledge() || move == Move.FALL_TUCK || move == Move.FALL_ROLL || move == Move.PLUNGE_FALL || move == Move.PLUNGE;
        boolean bow = move.volley() || move.marked();
        if (bow && !Bow.volleyReady(p)) return false;
        if (!hands && !bow && (move.drawnBow() ? !Bow.drawing(p) : p.isUsingItem())) return false;
        // One move at a time; the next may start once this one has played out (its fade aside), or at
        // once if it follows from it (the attack out of a slide, the moves on a ledge...).
        if (s.move != null && s.moveTick < s.move.freeFrom() && !move.follows(s.move)) return false;
        if (!s.canAfford(move, p)) return false;
        boolean ground = p.onGround() || p.tickCount - lastGround(p) <= GROUND_GRACE;
        boolean sprint = p.isSprinting() || p.tickCount - lastSprint(p) <= GROUND_GRACE;
        if (move == Move.SLIDE) return ground && sprint;
        // Out of the slide, a sword or an axe in hand, after the cooldown (a client a few ticks ahead is let through).
        if (move == Move.SLIDE_ATTACK) {
            return s.move == Move.SLIDE && Strikes.finisherWeapon(p) && s.slideAttackCooldown <= (p.level().isClientSide ? 0 : 5);
        }
        // In the air: the tuck while falling far enough, the roll out of it; the ledge from a jump or a fall.
        if (move == Move.FALL_TUCK) return p.fallDistance >= Fall.TUCK_FROM - (p.level().isClientSide ? 0f : 1f);
        if (move == Move.FALL_ROLL) return s.move == Move.FALL_TUCK;
        // Falling with a sword or an axe: the blade held for the plunge, and driven in on landing.
        if (move == Move.PLUNGE_FALL) return Strikes.finisherWeapon(p) && p.fallDistance >= MovesConfig.plungeFrom() - (p.level().isClientSide ? 0f : 1f);
        if (move == Move.PLUNGE) return s.move == Move.PLUNGE_FALL;
        // G let go: the turns end (in the air too: the keys walk the sword's turns, a step off an edge)
        if (move == Move.SPIN_END) return s.move == Move.SWORD_SPIN;
        if (move == Move.WHIRL_END) return s.move == Move.WHIRL;
        if (move.ledge()) return move != Move.LEDGE_DOWN || ground;
        if (move == Move.LEDGE_HELP) return ground;
        // W + Space + R: the kick and the dive from the ground or a jump already begun, not out of a fall.
        if (move == Move.KICK || move == Move.DIVE || move == Move.ROLL_KICK) {
            int grace = JUMP_GRACE + (p.level().isClientSide ? 0 : GROUND_GRACE);
            return p.fallDistance < Fall.TUCK_FROM && (ground || p.tickCount - lastGround(p) <= grace);
        }
        // The strikes: the dash with a sword (the left and the right button held); on G the sword's turn
        // with a sword, the whirlwind with an axe; the finisher: the stab with a sword, the chop with an
        // axe (author, 2026-10-30).
        if (move == Move.DASH && !Strikes.sword(p)) return false;
        if (move == Move.WHIRL && !Strikes.axe(p)) return false;
        if (move == Move.SWORD_SPIN && !Strikes.sword(p)) return false;
        if (move == Move.FINISHER && !Strikes.sword(p)) return false;
        if (move == Move.FINISHER_AXE && !Strikes.axe(p)) return false;
        // The volley at the marked: its first step waits out the cooldown and wants the energy for the start.
        if (move.marked() && (s.move == null || !s.move.marked())) {
            if (s.markedCooldown > (p.level().isClientSide ? 0 : 5)) return false;
            if (!s.free() && !p.getAbilities().instabuild && s.energy < MovesConfig.costMarked() + MovesConfig.costMarkedEach()) return false;
        }
        // They wait out their cooldowns (the server lets a client a few ticks ahead of it through).
        if (move == Move.DASH) return ground && s.dashCooldown <= (p.level().isClientSide ? 0 : 5);
        if (move == Move.WHIRL) return ground && s.whirlCooldown <= (p.level().isClientSide ? 0 : 5);
        if (move == Move.SWORD_SPIN) return ground && s.spinCooldown <= (p.level().isClientSide ? 0 : 5);
        return ground || !MovesConfig.dodgeOnlyOnGround();
    }

    // ------------------------------------------------------------------ what the server remembers

    private static final java.util.Map<java.util.UUID, int[]> SEEN = new java.util.concurrent.ConcurrentHashMap<>();
    /** The same for the client's own player (in a single player world both ends run in one game). */
    private static final java.util.Map<java.util.UUID, int[]> SEEN_CLIENT = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, float[]> SENT = new java.util.concurrent.ConcurrentHashMap<>();
    /** Where each player last stood (the edge just gone over: Ledge.justLeft), on each end. */
    private static final java.util.Map<java.util.UUID, net.minecraft.world.phys.Vec3> GROUND_AT = new java.util.concurrent.ConcurrentHashMap<>(),
            GROUND_AT_CLIENT = new java.util.concurrent.ConcurrentHashMap<>();

    /** The tick the player was last on the ground. */
    public static int lastGround(Player p) {
        int[] seen = (p.level().isClientSide ? SEEN_CLIENT : SEEN).get(p.getUUID());
        return seen == null ? Integer.MIN_VALUE / 2 : seen[0];
    }

    /** Where the player's feet last stood on the ground (null: not seen yet). */
    public static net.minecraft.world.phys.Vec3 groundAt(Player p) {
        return (p.level().isClientSide ? GROUND_AT_CLIENT : GROUND_AT).get(p.getUUID());
    }

    private static int lastSprint(Player p) {
        int[] seen = (p.level().isClientSide ? SEEN_CLIENT : SEEN).get(p.getUUID());
        return seen == null ? Integer.MIN_VALUE / 2 : seen[1];
    }

    /** Every server tick after the bars were refilled. */
    static void afterTick(ServerPlayer p, Stamina s) {
        seen(p);
        sync(p, s, false);
    }

    /**
     * When the player was last on the ground and sprinting (every tick, on both ends: the client
     * checks a move as the server will, a jump a tick or two old still counting as from the ground).
     */
    public static void seen(Player p) {
        java.util.Map<java.util.UUID, int[]> map = p.level().isClientSide ? SEEN_CLIENT : SEEN;
        int[] seen = map.computeIfAbsent(p.getUUID(), u -> new int[]{Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2});
        if (p.onGround()) {
            seen[0] = p.tickCount;
            (p.level().isClientSide ? GROUND_AT_CLIENT : GROUND_AT).put(p.getUUID(), p.position());
        }
        if (p.isSprinting()) seen[1] = p.tickCount;
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
        GROUND_AT.remove(event.getEntity().getUUID());
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
