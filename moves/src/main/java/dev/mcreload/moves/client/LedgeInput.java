package dev.mcreload.moves.client;

import dev.mcreload.moves.HudConfig;
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
 *   player (the wall in front) or past an edge while falling (any wall around), or just after going
 *   over a drop, sliding or rolling off it too (the hand catches the edge behind, the player is
 *   carried back to it): one hand catches it. Then the button can be let go. It goes before
 *   everything else the right button does (author: the sword block, placing a block, stripping a
 *   log...), in the air.</li>
 *   <li><b>Hanging.</b> A / D held: hops a block at a time, one after another, round the block's
 *   corners and onto the next block a step up or down (author, 2026-10-30, 23:01: only the hops, no
 *   creeping along the edge). Another edge looked at lights up, green within the leap's reach, red
 *   out of it (LedgeAim); Space: the leap there. W + Space: a quick pull-up onto the top, running on
 *   from it.</li>
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
    private static boolean useWasDown;
    /** The edge looked at while hanging (LedgeAim draws it), null: none. */
    static Ledge.Target aim;
    /** The leap going on: from where, to where, the turn on the way. */
    private static Vec3 leapFrom, leapTo;
    private static float leapYawFrom, leapYawTo, leapYawWas;
    /** The edge caught behind (just gone over): where the player was and their facing, carried back to the hang over CATCH ticks. */
    private static Vec3 catchFrom;
    private static float catchYaw;
    private static final int CATCH = 4;
    /** The hop going on: from where, round which point (an outer corner; null: straight), to where, and the turn on the way. */
    private static Vec3 hopFrom, hopVia, hopTo;
    private static float hopYawFrom, hopYawTo, hopYawWas;

    static void reset() {
        aim = null;
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
        aim = m != null && m.hanging() && HudConfig.Moves.LEDGE_HOPS.on() ? Ledge.aimed(p, p.position(), Ledge.dir(s.ledgeDir), s.ledgeTop) : null;
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
        if (usePressed && p.onGround() && m == null && HudConfig.Moves.LEDGE_GRAB.on() && friendBelow(mc, p, s)) {
            swallowUse(mc);
            return true;
        }
        // The automatic hang: Ctrl + W + C at a drop (before the slide, which is sprint + C, and
        // Kinetrix's crawl, which is C).
        boolean sprintW = p.onGround() && HudConfig.Moves.AUTO_HANG.on() && (mc.options.keySprint.isDown() || p.isSprinting())
                && mc.options.keyUp.isDown();
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
        // Catching the edge: in the air, the right button held (out of a fall, a slide, a roll off a drop too).
        if (useDown && !p.onGround() && !p.getAbilities().flying && !p.isInWater() && !p.onClimbable()
                && HudConfig.Moves.LEDGE_GRAB.on() && (m == null || Move.LEDGE_HANG.follows(m))) {
            boolean falling = p.getDeltaMovement().y < -0.1 || p.fallDistance > 0.6f;
            Ledge.Spot spot = Ledge.find(p, p.yo, p.getY(), falling);
            catchFrom = null;
            // the edge just gone over, behind the player: they are carried back to it
            Vec3 ground = MoveServer.groundAt(p);
            if (spot == null && ground != null && p.tickCount - MoveServer.lastGround(p) <= Ledge.LEFT_TICKS) {
                spot = Ledge.justLeft(p, ground, Ledge.LEFT_REACH, true);
                if (spot != null) {
                    catchFrom = p.position();
                    catchYaw = p.getYRot();
                }
            }
            if (spot != null) {
                if (p.isUsingItem()) p.stopUsingItem();
                GuardInput.lowerLocal(p, s, true);
                if (catchFrom == null) {
                    p.setYRot(spot.yaw());
                    p.yRotO = p.getYRot();
                }
                if (m != null && !m.ledge()) MovesClient.cancel(p, s, 2);
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
        // Caught behind: carried back to the edge first (place), the keys wait.
        if (m == Move.LEDGE_HANG && catchFrom != null && s.moveTick < CATCH) return;
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
            // Space: the leap to the edge looked at (W + Space onto the top, if there is room there)
            Vec3 top = Ledge.onTop(p.level(), p.position(), d, s.ledgeTop);
            boolean climb = up && Ledge.fits(p, top);
            if (jumpPressed && !climb && aim != null) {
                if (aim.reachable() && HudConfig.Moves.LEDGE_HOPS.on()) leap(p, s, d, aim.spot());
                else p.displayClientMessage(Component.translatable("kinetrix_moves.ledge.too_far"), true);
                return;
            }
            if (up && jumpPressed) {
                if (climb) {
                    hangAt = p.position();
                    MovesClient.start(p, s, Move.LEDGE_CLIMB, s.ledgeDir);
                } else {
                    p.displayClientMessage(Component.translatable("kinetrix_moves.ledge.no_room"), true);
                }
                return;
            }
            // A / D: hops along the edge (and round its corners, onto the next block a step up or down)
            if (left != right && HudConfig.Moves.LEDGE_HOPS.on() && s.hasEnergy(p)) hop(p, s, d, left, true);
            return;
        }
        // A or D held: the next hop as the second hand takes the edge (author: one after another,
        // almost without a pause); the way the keys say, so it can turn back too.
        boolean hopping = m == Move.LEDGE_HOP_LEFT || m == Move.LEDGE_HOP_RIGHT;
        if (hopping && left != right && s.moveTick >= Move.HOP_CHAIN) {
            hop(p, s, d, left, true);
            if (s.move != m || s.moveTick == 0) return;
        }
        // The climb: once on the top, moving, jumping or striking runs on from it (no standing up first).
        if (m == Move.LEDGE_CLIMB && s.moveTick >= Move.CLIMB_ON_TOP && (up || back || left || right || jumpPressed || attackPressed)) {
            MovesClient.cancel(p, s, 3);
            return;
        }
        // A move on the edge that ends in the hang: the hop, the leap and the climb down hand over to it before they run out.
        if ((hopping || m == Move.LEDGE_LEAP || m == Move.LEDGE_DOWN) && s.moveTick >= m.length - 4) {
            MovesClient.start(p, s, Move.LEDGE_HANG, s.ledgeDir);
        }
    }

    /**
     * A hop to the left or right (Ledge.next): along the edge, round a corner of the block, into a wall
     * at the side or onto the next block a step up or down. {@code straight}: the plain hop along the
     * same edge too. The edge it goes on to is the player's from its start (the server is told which).
     */
    private static boolean hop(LocalPlayer p, Stamina s, Direction d, boolean left, boolean straight) {
        Ledge.Step step = Ledge.next(p, p.position(), d, s.ledgeTop, left, straight);
        if (step == null) return false;
        Ledge.Spot to = step.spot();
        int dirWas = s.ledgeDir;
        double topWas = s.ledgeTop;
        Vec3 was = hangAt;
        hangAt = p.position();
        hopFrom = p.position();
        hopVia = step.via();
        hopTo = to.at();
        hopYawFrom = d.toYRot();
        hopYawTo = to.yaw();
        hopYawWas = hopYawFrom;
        s.ledgeDir = Ledge.id(to.dir());
        s.ledgeTop = to.top();
        if (MovesClient.start(p, s, left ? Move.LEDGE_HOP_LEFT : Move.LEDGE_HOP_RIGHT, Ledge.hopTarget(to.dir(), to.top() - topWas))) return true;
        hangAt = was;
        s.ledgeDir = dirWas;
        s.ledgeTop = topWas;
        return false;
    }

    /** The leap to another edge (Space, the edge looked at within reach): the player is carried there and turned to its wall. */
    private static void leap(LocalPlayer p, Stamina s, Direction d, Ledge.Spot to) {
        int dirWas = s.ledgeDir;
        double topWas = s.ledgeTop;
        s.ledgeDir = Ledge.id(to.dir());
        s.ledgeTop = to.top();
        leapFrom = p.position();
        leapTo = to.at();
        leapYawFrom = d.toYRot();
        leapYawTo = to.yaw();
        leapYawWas = leapYawFrom;
        if (MovesClient.start(p, s, Move.LEDGE_LEAP, Ledge.leapTarget(to.dir(), to.top() - topWas))) return;
        s.ledgeDir = dirWas;
        s.ledgeTop = topWas;
        leapFrom = null;
    }

    /** Where the hop has carried the player at {@code f} of its way (0-1): straight, or round the corner's point. */
    private static Vec3 hopAt(float f) {
        if (hopVia == null) return hopFrom.add(hopTo.subtract(hopFrom).scale(f));
        double a = hopVia.subtract(hopFrom).length(), b = hopTo.subtract(hopVia).length();
        double g = f * (a + b);
        return g <= a ? hopFrom.add(hopVia.subtract(hopFrom).scale(a < 1.0E-6 ? 1 : g / a))
                : hopVia.add(hopTo.subtract(hopVia).scale(b < 1.0E-6 ? 1 : (g - a) / b));
    }

    /** The player's left (or right) along the edge, a block long, facing the wall that way. */
    private static Vec3 side(Direction d, boolean left) {
        Direction l = d.getCounterClockWise();
        return left ? new Vec3(l.getStepX(), 0, l.getStepZ()) : new Vec3(-l.getStepX(), 0, -l.getStepZ());
    }

    private static void letGo(LocalPlayer p, Stamina s) {
        MovesClient.cancel(p, s, 4);
        // a little away from the wall, so the fall does not scrape it
        Direction d = Ledge.dir(s.ledgeDir);
        p.setDeltaMovement(-d.getStepX() * 0.08, -0.1, -d.getStepZ() * 0.08);
    }

    private static boolean start(LocalPlayer p, Stamina s, Move move, Ledge.Spot spot) {
        if (!MoveServer.allowed(p, s, move)) return false;
        s.ledgeDir = Ledge.id(spot.dir());
        s.ledgeTop = spot.top();
        if (move == Move.LEDGE_HANG) {
            if (catchFrom == null) p.setPos(spot.at());     // caught behind: place() carries the player back to it
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
    }

    // ------------------------------------------------------------------ carrying the player

    /** A move on the edge has started: one that ends a carry (a hop, the climb down) hangs from where it got to. */
    static void started(LocalPlayer p, Move move, Move was) {
        if (move != Move.LEDGE_HOP_LEFT && move != Move.LEDGE_HOP_RIGHT) hopFrom = null;
        if (move != Move.LEDGE_LEAP) leapFrom = null;
        if (move == Move.LEDGE_HANG && was != null && was.ledge() && !was.hanging()) hangAt = p.position();
        // only a fresh catch behind is carried back (not the hang after a hop or the climb down)
        if (move != Move.LEDGE_HANG || (was != null && was.ledge())) catchFrom = null;
    }

    /**
     * After vanilla has moved the player this tick (so it does not push them out of the edge's
     * corner on the climb), the move on the edge puts them where its path says.
     */
    static void place(LocalPlayer p, Stamina s) {
        Move m = s.move;
        settle(p, m);
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
            case LEDGE_LEAP -> {
                if (leapFrom == null) {
                    at = hangAt;
                    break;
                }
                float f = Mth.clamp((t + 1 - Ledge.LEAP_PUSH) / (float) (Ledge.LEAP_CATCH - Ledge.LEAP_PUSH), 0f, 1f);
                at = Ledge.leapAt(leapFrom, leapTo, f);
                // turned to the other edge's wall on the way, the camera too
                float yaw = Mth.rotLerp(f, leapYawFrom, leapYawTo);
                p.setYRot(p.getYRot() + Mth.wrapDegrees(yaw - leapYawWas));
                leapYawWas = yaw;
                p.setPos(at);
                p.setDeltaMovement(Vec3.ZERO);
                p.fallDistance = 0f;
                p.yBodyRot = yaw;
                return;
            }
            case LEDGE_HOP_LEFT, LEDGE_HOP_RIGHT -> {
                float f = Paths.lerp("ledge_hop", t * Move.HOP_SPEED, 0);
                if (hopFrom == null) {
                    at = hangAt.add(side(d, m == Move.LEDGE_HOP_LEFT).scale(f));
                } else {
                    at = hopAt(f);
                    // round a corner the player turns with it, the camera too
                    float yaw = Mth.rotLerp(f, hopYawFrom, hopYawTo);
                    p.setYRot(p.getYRot() + Mth.wrapDegrees(yaw - hopYawWas));
                    hopYawWas = yaw;
                    p.setPos(at);
                    p.setDeltaMovement(Vec3.ZERO);
                    p.fallDistance = 0f;
                    p.yBodyRot = yaw;
                    return;
                }
            }
            case LEDGE_HANG -> {
                at = hangAt;
                // caught behind: carried back to the edge, turning to face it
                if (catchFrom != null && t < CATCH) {
                    float f = (t + 1) / (float) CATCH;
                    f = f * f * (3 - 2 * f);
                    at = catchFrom.add(hangAt.subtract(catchFrom).scale(f));
                    float yaw = Mth.rotLerp(f, catchYaw, d.toYRot());
                    p.setYRot(yaw);
                    p.setYHeadRot(yaw);
                } else {
                    catchFrom = null;
                }
            }
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

    /**
     * Off the edge (up on its top, or let go): Kinetrix still remembers how fast the player fell before
     * the hand caught the edge, and on the first step onto the top it took that for a hard landing and
     * rolled them forward (author, 2026-10-29: "the pull-up turns you over into a roll"). Until the feet
     * have been on the ground a few ticks (at most SETTLE), the move still owns the whole body, so
     * Kinetrix forgets that fall instead.
     */
    private static int settle, settled;
    private static final int SETTLE = 20;

    private static void settle(LocalPlayer p, Move m) {
        if (m != null && m.ledge()) {
            settle = SETTLE;
            settled = 0;
            return;
        }
        if (settle <= 0) return;
        settle--;
        settled = p.onGround() ? settled + 1 : 0;
        if (settled > 4) settle = 0;
        else dev.mcreload.moves.compat.KinetrixBridge.wholeBody(p.getUUID(), 250);
    }

    /** Ticks of the climb down spent walking to the edge and turning round. */
    private static final int DOWN_WALK = 6;

    /**
     * At the top the feet stand exactly on it (the path is rounded): a hair under it, vanilla takes the
     * player for stuck in the block and pushes them out, over the edge.
     */
    private static Vec3 onTop(Vec3 at, Stamina s) {
        if (at.y <= s.ledgeTop - 0.02) return at;
        // on a carpet or a snow layer there, if any (the hand took the edge under it)
        Minecraft mc = Minecraft.getInstance();
        double layer = mc.level == null ? 0 : Ledge.layer(mc.level, at, s.ledgeTop);
        return new Vec3(at.x, s.ledgeTop + Math.max(1.0E-4, layer), at.z);
    }

    /** The climb's path from where the player hung: up, and forward onto the top. */
    private static Vec3 carried(String path, int t, Vec3 w) {
        return hangAt.add(w.scale(Paths.at(path, t, 1))).add(0, Paths.at(path, t, 0), 0);
    }

    /** The bodies of other players on an edge face its wall (their heads may look round). */
    static void faceWall(AbstractClientPlayer p, Stamina s) {
        if (s.move == null || !s.move.ledge() || s.ledgeDir < 0) return;
        // a hop may go round a corner, a leap to another wall: the body turns with the player meanwhile
        if (s.move == Move.LEDGE_HOP_LEFT || s.move == Move.LEDGE_HOP_RIGHT || s.move == Move.LEDGE_LEAP) return;
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
