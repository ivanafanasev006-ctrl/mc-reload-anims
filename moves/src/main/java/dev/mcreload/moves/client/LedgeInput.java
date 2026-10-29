package dev.mcreload.moves.client;

import dev.mcreload.moves.Ledge;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.MoveServer;
import dev.mcreload.moves.MovesConfig;
import dev.mcreload.moves.Paths;
import dev.mcreload.moves.Stamina;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The ledge on the player's side (Ledge; author, 2026-10-29).
 *
 * <ul>
 *   <li><b>Catching it.</b> The right button held in a jump or a fall, near a block higher than the
 *   player (the wall in front) or past an edge while falling (any wall around): one hand catches it.
 *   Then the button can be let go. It goes before everything else the right button does (author: the
 *   sword block, placing a block, stripping a log...), in the air.</li>
 *   <li><b>Hanging.</b> A / D: hand over hand along the edge, as far as it goes. R: the hop mode, in
 *   which A / D hop a block at a time (R again: back). W + Space: up onto the top.</li>
 *   <li><b>Letting go.</b> S + R, C, S + Space or the left button.</li>
 *   <li><b>A friend.</b> The right button on a player hanging below the edge one stands on: they are
 *   pulled up.</li>
 *   <li><b>Down onto it.</b> Ctrl + W + C at a drop: the player turns round and lowers themself over
 *   the edge into the hang.</li>
 * </ul>
 *
 * <p>While on the edge the mechanic places the player every tick (after vanilla has moved them,
 * before their position goes to the server), so the hands the animations keep on the edge stay
 * there.</p>
 */
public final class LedgeInput {
    /** Where the hanging player is (the start of the move that carries them: a climb, a hop...). */
    private static Vec3 hangAt;
    /** Where the move started from (the friend pulling, the climb down's walk to the edge) and the facing then. */
    private static Vec3 from;
    private static float fromYaw;
    /** The hop mode (R while hanging): A / D hop instead of going hand over hand. */
    private static boolean hopMode;
    private static boolean useWasDown;

    static void reset() {
        hopMode = false;
    }

    /**
     * Every client tick, before the dodges and the right button's other uses. Returns whether the
     * keys were the ledge's this tick (then nothing else reads them).
     */
    static boolean tick(Minecraft mc, LocalPlayer p, Stamina s, boolean dodgePressed, boolean jumpPressed,
                        boolean slidePressed, boolean attackPressed) {
        boolean useDown = mc.options.keyUse.isDown();
        boolean usePressed = useDown && !useWasDown;
        useWasDown = useDown;
        Move m = s.move;
        if (m != null && m.ledge()) {
            onLedge(mc, p, s, dodgePressed, jumpPressed, slidePressed, attackPressed);
            swallowUse(mc);
            return true;
        }
        if (m == Move.LEDGE_HELP) {
            swallowUse(mc);
            return true;
        }
        if (!MovesConfig.ledgeGrab()) return false;
        // A friend below: the right button on them pulls them up.
        if (usePressed && p.onGround() && m == null && friendBelow(mc, p, s)) {
            swallowUse(mc);
            return true;
        }
        // The automatic hang: Ctrl + W + C at a drop (before the slide, which is sprint + C, and
        // Kinetrix's crawl, which is C).
        boolean sprintW = p.onGround() && (mc.options.keySprint.isDown() || p.isSprinting()) && mc.options.keyUp.isDown();
        Ledge.Spot drop = sprintW && (m == null || m == Move.SLIDE && s.moveTick < 3) ? Ledge.dropAhead(p) : null;
        if (drop != null) dev.mcreload.moves.compat.KinetrixBridge.blockCrawl(250);
        if (slidePressed && drop != null) {
            Ledge.Spot spot = drop;
            {
                if (m == Move.SLIDE) MovesClient.cancel(p, s, 2);
                from = p.position();
                fromYaw = p.getYRot();
                if (start(p, s, Move.LEDGE_DOWN, spot)) return true;
            }
        }
        // Catching the edge: in the air, the right button held.
        if (useDown && !p.onGround() && !p.getAbilities().flying && !p.isInWater() && !p.onClimbable()
                && (m == null || m == Move.FALL_TUCK)) {
            boolean falling = p.getDeltaMovement().y < -0.1 || p.fallDistance > 0.6f;
            Ledge.Spot spot = Ledge.find(p, p.yo, p.getY(), falling);
            if (spot != null) {
                if (p.isUsingItem()) p.stopUsingItem();
                GuardInput.lowerLocal(p, s, true);
                p.setYRot(spot.yaw());
                p.yRotO = p.getYRot();
                if (start(p, s, Move.LEDGE_HANG, spot)) {
                    swallowUse(mc);
                    return true;
                }
            }
        }
        return false;
    }

    /** Hanging, or moving on the edge: the keys of the ledge. */
    private static void onLedge(Minecraft mc, LocalPlayer p, Stamina s, boolean dodgePressed, boolean jumpPressed,
                                boolean slidePressed, boolean attackPressed) {
        Move m = s.move;
        Direction d = Ledge.dir(s.ledgeDir);
        boolean back = mc.options.keyDown.isDown(), up = mc.options.keyUp.isDown();
        boolean left = mc.options.keyLeft.isDown(), right = mc.options.keyRight.isDown();
        StrikeInput.swallow(mc);
        dev.mcreload.moves.compat.KinetrixBridge.blockCrawl(250);     // C lets go here, it does not crawl
        // The edge gave way (broken, or it never was): fall.
        if (m.hanging() && !Ledge.holds(p.level(), p.position(), d, s.ledgeTop)) {
            letGo(p, s);
            return;
        }
        if (m.hanging()) {
            // let go: S + R, C, S + Space, the left button
            if ((back && dodgePressed) || slidePressed || (back && jumpPressed) || attackPressed) {
                letGo(p, s);
                return;
            }
            if (up && jumpPressed) {
                Vec3 top = Ledge.onTop(p.position(), d, s.ledgeTop);
                if (Ledge.fits(p, top)) {
                    hangAt = p.position();
                    MovesClient.start(p, s, Move.LEDGE_CLIMB, s.ledgeDir);
                } else {
                    p.displayClientMessage(Component.translatable("kinetrix_moves.ledge.no_room"), true);
                }
                return;
            }
            if (dodgePressed && !back) {
                if (left != right) {
                    hop(p, s, d, left);
                } else {
                    hopMode = !hopMode;
                    p.displayClientMessage(Component.translatable(hopMode ? "kinetrix_moves.ledge.hops" : "kinetrix_moves.ledge.shimmy"), true);
                }
                return;
            }
            if (left != right && hopMode) {
                hop(p, s, d, left);
                return;
            }
            Move want = left == right ? Move.LEDGE_HANG : left ? Move.LEDGE_SHIMMY_LEFT : Move.LEDGE_SHIMMY_RIGHT;
            if (want != Move.LEDGE_HANG && !Ledge.holds(p.level(), p.position().add(side(d, want == Move.LEDGE_SHIMMY_LEFT).scale(0.32)), d, s.ledgeTop)) {
                want = Move.LEDGE_HANG;     // the edge ends there
            }
            if (want != m) MovesClient.start(p, s, want, s.ledgeDir);
            return;
        }
        // A move on the edge that ends in the hang: the hop and the climb down hand over to it before they run out.
        if ((m == Move.LEDGE_HOP_LEFT || m == Move.LEDGE_HOP_RIGHT || m == Move.LEDGE_DOWN) && s.moveTick >= m.length - 4) {
            MovesClient.start(p, s, Move.LEDGE_HANG, s.ledgeDir);
        }
    }

    private static void hop(LocalPlayer p, Stamina s, Direction d, boolean left) {
        Vec3 there = p.position().add(side(d, left));
        if (!Ledge.holds(p.level(), there, d, s.ledgeTop) || !Ledge.fits(p, there)) return;
        hangAt = p.position();
        MovesClient.start(p, s, left ? Move.LEDGE_HOP_LEFT : Move.LEDGE_HOP_RIGHT, s.ledgeDir);
    }

    /** The player's left (or right) along the edge, a block long, facing the wall that way. */
    private static Vec3 side(Direction d, boolean left) {
        Direction l = d.getCounterClockWise();
        return left ? new Vec3(l.getStepX(), 0, l.getStepZ()) : new Vec3(-l.getStepX(), 0, -l.getStepZ());
    }

    private static void letGo(LocalPlayer p, Stamina s) {
        hopMode = false;
        MovesClient.cancel(p, s, 4);
        // a little away from the wall, so the fall does not scrape it
        Direction d = Ledge.dir(s.ledgeDir);
        p.setDeltaMovement(-d.getStepX() * 0.08, -0.1, -d.getStepZ() * 0.08);
    }

    private static boolean start(LocalPlayer p, Stamina s, Move move, Ledge.Spot spot) {
        if (!MoveServer.allowed(p, s, move)) return false;
        s.ledgeDir = Ledge.id(spot.dir());
        s.ledgeTop = spot.top();
        hopMode = false;
        if (move == Move.LEDGE_HANG) {
            p.setPos(spot.at());
            p.setDeltaMovement(Vec3.ZERO);
        }
        hangAt = spot.at();
        return MovesClient.start(p, s, move, s.ledgeDir);
    }

    /** The right button on a player hanging below the edge this one stands on: pull them up. */
    private static boolean friendBelow(Minecraft mc, LocalPlayer p, Stamina s) {
        Entity looked = mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : mc.crosshairPickEntity;
        if (!(looked instanceof Player other) || other == p) return false;
        Stamina os = Stamina.of(other);
        if (os.move == null || !os.move.hanging() || os.ledgeDir < 0) return false;
        Direction d = Ledge.dir(os.ledgeDir);
        Vec3 at = Ledge.friendAt(other.position(), d, os.ledgeTop);
        if (Math.abs(p.getY() - os.ledgeTop) > 0.6 || p.position().distanceTo(at) > 2.0) return false;
        if (!Ledge.fits(p, at) || !MoveServer.allowed(p, s, Move.LEDGE_HELP)) return false;
        from = at;
        fromYaw = d.getOpposite().toYRot();
        p.setPos(at);
        p.setYRot(fromYaw);
        p.setDeltaMovement(Vec3.ZERO);
        s.ledgeDir = os.ledgeDir;
        return MovesClient.start(p, s, Move.LEDGE_HELP, other.getId());
    }

    /** Our own player was pulled up by a friend (the server says so): it hangs where it is. */
    static void pulled(LocalPlayer p, Stamina s) {
        hangAt = p.position();
        hopMode = false;
    }

    // ------------------------------------------------------------------ carrying the player

    /** A move on the edge has started: one that ends a carry (a hop, the climb down) hangs from where it got to. */
    static void started(LocalPlayer p, Move move, Move was) {
        if (move == Move.LEDGE_HANG && (was == null || !was.hanging())) hangAt = p.position();
    }

    /**
     * After vanilla has moved the player this tick (so it does not push them out of the edge's
     * corner on the climb), the move on the edge puts them where its path says.
     */
    static void place(LocalPlayer p, Stamina s) {
        Move m = s.move;
        if (m == null || (!m.ledge() && m != Move.LEDGE_HELP)) return;
        Direction d = Ledge.dir(s.ledgeDir);
        Vec3 w = new Vec3(d.getStepX(), 0, d.getStepZ());
        int t = s.moveTick;
        if (m == Move.LEDGE_HELP) {
            // the friend steps back from the edge as they pull (the edge's wall is the way they step)
            if (from != null) {
                Vec3 at = from.add(w.scale(Paths.at("ledge_help_pull", t, 0)));
                p.setPos(at.x, p.getY(), at.z);
                p.setDeltaMovement(0, p.getDeltaMovement().y, 0);
            }
            return;
        }
        if (hangAt == null) hangAt = p.position();
        Vec3 at;
        switch (m) {
            case LEDGE_SHIMMY_LEFT, LEDGE_SHIMMY_RIGHT -> {
                Vec3 step = side(d, m == Move.LEDGE_SHIMMY_LEFT);
                Vec3 next = hangAt.add(step.scale(Ledge.SHIMMY_SPEED));
                // as far as the edge goes (the hand ahead still on it) and there is room
                if (Ledge.holds(p.level(), next.add(step.scale(0.3)), d, s.ledgeTop) && Ledge.fits(p, next)) hangAt = next;
                at = hangAt;
            }
            case LEDGE_HOP_LEFT, LEDGE_HOP_RIGHT -> at = hangAt.add(side(d, m == Move.LEDGE_HOP_LEFT).scale(Paths.at("ledge_hop", t, 0)));
            case LEDGE_CLIMB -> at = onTop(carried("ledge_climb_up", t, w), s);
            case LEDGE_PULLED -> at = onTop(carried("ledge_pulled_up", t, w), s);
            case LEDGE_DOWN -> {
                at = onTop(carried("ledge_climb_down", t, w), s);
                // first the walk to the edge and the turn round, the back to the drop
                if (t < DOWN_WALK && from != null) {
                    float f = (t + 1) / (float) DOWN_WALK;
                    at = from.add(at.subtract(from).scale(f));
                    float yaw = Mth.rotLerp(f, fromYaw, d.toYRot());
                    p.setYRot(yaw);
                    p.setYHeadRot(yaw);
                }
            }
            default -> at = hangAt;
        }
        p.setPos(at);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0f;
        // the body faces the wall; the head may look round
        if (m != Move.LEDGE_DOWN || t >= DOWN_WALK) p.yBodyRot = d.toYRot();
    }

    /** Ticks of the climb down spent walking to the edge and turning round. */
    private static final int DOWN_WALK = 6;

    /**
     * At the top the feet stand exactly on it (the path is rounded): a hair under it, vanilla takes the
     * player for stuck in the block and pushes them out, over the edge.
     */
    private static Vec3 onTop(Vec3 at, Stamina s) {
        return at.y > s.ledgeTop - 0.02 ? new Vec3(at.x, s.ledgeTop + 1.0E-4, at.z) : at;
    }

    /** The climb's path from where the player hung: up, and forward onto the top. */
    private static Vec3 carried(String path, int t, Vec3 w) {
        return hangAt.add(w.scale(Paths.at(path, t, 1))).add(0, Paths.at(path, t, 0), 0);
    }

    /** The bodies of other players on an edge face its wall (their heads may look round). */
    static void faceWall(AbstractClientPlayer p, Stamina s) {
        if (s.move == null || !s.move.ledge() || s.ledgeDir < 0) return;
        float yaw = Ledge.dir(s.ledgeDir).toYRot();
        p.yBodyRot = yaw;
        p.yBodyRotO = yaw;
    }

    /** Lets go of the right button: its clicks this tick are used up and it reads as up until pressed again. */
    static void swallowUse(Minecraft mc) {
        while (mc.options.keyUse.consumeClick()) {
            // used up
        }
        mc.options.keyUse.setDown(false);
    }

    private LedgeInput() {}
}
