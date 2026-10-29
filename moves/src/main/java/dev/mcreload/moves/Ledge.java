package dev.mcreload.moves;

import dev.mcreload.moves.net.MovesNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The ledge (author, 2026-10-29): the right button held near a block higher than the player in a
 * jump, or falling past one, or just after going over a drop (a run, a slide or a roll off it;
 * author's fourth message), catches its edge with one hand; then the player hangs until they let
 * go (S + R, C, S + Space or the left button), moves along it (A / D, or hops a block at a time in
 * the hop mode R toggles), climbs onto it (W + Space) or a friend standing above pulls them up (the
 * right button on them). Ctrl + W + C at a drop lowers the player over its edge into the hang.
 *
 * <p>Where the edge is (the animations were made to it, tools/blockbench/refined/ledge_2026-10-29):
 * its top {@link #HANG} blocks above the hanging player's feet, its face against the front of their
 * hitbox. The mechanic carries the player (Paths); the animations keep the hands on the edge.</p>
 *
 * <p>On a server, a player against a wall is never "flying" (the wall is next to their hitbox), and
 * while the climb carries them through the edge's corner they pass through blocks
 * ({@link #serverTick}).</p>
 */
public final class Ledge {
    /** The edge's top above the hanging player's feet: the rig's 31 px, drawn at 15/16 in the game. */
    public static final double HANG = 31 * 0.9375 / 16;
    /** Half the player's width: the hitbox's front is on the wall's face. */
    public static final double HALF = 0.3;
    /** Hand over hand along the edge: the animation's 10 px a second at 15/16, a tick. */
    public static final double SHIMMY_SPEED = 10 * 0.9375 / 16 / 20;
    /**
     * How far from the wall (between it and the front of the hitbox) the hand still reaches its
     * edge (author, 2026-10-29: only right at the edge; from the far side of a block the hand does
     * not reach). [default]
     */
    public static final double REACH = 0.3;
    /** How far below the hands the edge may be caught (a fall goes past it between two ticks), and above them (an arm's stretch). */
    public static final double BELOW = 0.45, ABOVE = 0.35;
    /** The automatic hang: the front of the feet this close to the drop's edge at most (standing at it, not a step away). */
    public static final double AT_EDGE = 0.3;
    /** The server checks the client's catch with this much more room: it sees the player a tick or two late. */
    private static final double SLACK = 0.6;
    /**
     * The edge just gone over (a run, a slide or a roll off a drop) is caught up to this many ticks
     * after leaving it and this far from where the player would hang (they are carried back to it).
     * [default]
     */
    public static final int LEFT_TICKS = 14;
    public static final double LEFT_REACH = 2.5;
    /** The friend stands this far from the edge (their middle), facing the drop (the animation's 0.49). */
    public static final double FRIEND = 8.4 * 0.9375 / 16;

    /** An edge to hang from: the wall faces the player the way {@code dir} points from them, its top at {@code top}; {@code at} is where the hanging player stands. */
    public record Spot(Direction dir, double top, Vec3 at) {
        public float yaw() {
            return dir.toYRot();
        }
    }

    // ------------------------------------------------------------------ finding an edge

    /**
     * An edge the player can catch now, its top between their feet's {@code from} and {@code to}
     * (the way the feet went since the last tick) plus HANG, give or take what the hands reach;
     * the wall the player faces first, then ({@code anyWay}: falling) the others. Null: none.
     */
    public static Spot find(Player p, double from, double to, boolean anyWay) {
        Direction facing = Direction.fromYRot(p.getYRot());
        Direction[] ways = anyWay ? new Direction[]{facing, facing.getClockWise(), facing.getCounterClockWise(), facing.getOpposite()}
                : new Direction[]{facing};
        double lo = Math.min(from, to) + HANG - BELOW, hi = Math.max(from, to) + HANG + ABOVE;
        for (Direction d : ways) {
            Spot s = findTowards(p, d, lo, hi, REACH);
            if (s != null) return s;
        }
        return null;
    }

    /** The edge of the wall that way, within {@code reach} of the front of the hitbox. */
    private static Spot findTowards(Player p, Direction d, double lo, double hi, double reach) {
        Level level = p.level();
        Vec3 c = p.position();
        double front = along(c, d) + HALF;
        for (double ahead : new double[]{0.001, reach / 2, reach}) {
            Vec3 probe = c.add(d.getStepX() * (HALF + ahead), 0, d.getStepZ() * (HALF + ahead));
            int bx = Mth.floor(probe.x), bz = Mth.floor(probe.z);
            double face = face(bx, bz, d);
            double gap = face - front;
            if (gap < -0.05 || gap > reach) continue;
            double top = topIn(level, bx, bz, lo, hi);
            if (Double.isNaN(top)) continue;
            Vec3 at = hangAt(c, d, face, top);
            if (!fits(p, at)) continue;
            return new Spot(d, top, at);
        }
        return null;
    }

    /**
     * The edge the player has just gone over (author, 2026-10-29: sliding or rolling off a drop, the
     * right button catches it): the edge of the block they last stood on ({@code ground}: where their
     * feet were), the way they went from it, a drop of two blocks or more beyond it. They have come
     * down past it ({@code strict}: the server, seeing the player late, does not ask) and are within
     * {@code reach} of where they will hang, facing back at it, along the edge where they are now
     * (else where they left it). Null: none.
     */
    public static Spot justLeft(Player p, Vec3 ground, double reach, boolean strict) {
        Vec3 c = p.position();
        Vec3 way = new Vec3(c.x - ground.x, 0, c.z - ground.z);
        if (way.lengthSqr() < 1.0E-4 || (strict && c.y > ground.y - 0.05)) return null;
        Direction d = Direction.getNearest(way.x, 0, way.z);
        Level level = p.level();
        double feet = ground.y;
        int by = Mth.floor(feet - 0.01);
        // the column under the feet then (the middle may already have been past the edge: one back)
        int sx = Mth.floor(ground.x), sz = Mth.floor(ground.z);
        if (Double.isNaN(topIn(level, sx, sz, feet - 0.05, feet + 0.05))) {
            sx -= d.getStepX();
            sz -= d.getStepZ();
        }
        for (int i = 0; i < 3; i++, sx += d.getStepX(), sz += d.getStepZ()) {
            double top = topIn(level, sx, sz, feet - 0.05, feet + 0.05);
            if (Double.isNaN(top)) return null;
            int nx = sx + d.getStepX(), nz = sz + d.getStepZ();
            if (!emptyAt(level, nx, by, nz) || !emptyAt(level, nx, by - 1, nz)) continue;
            Direction wall = d.getOpposite();
            double face = face(sx, sz, wall);
            Vec3 at = hangAt(c, wall, face, top);
            if (!holds(level, at, wall, top)) at = hangAt(ground, wall, face, top);
            if (!holds(level, at, wall, top) || !fits(p, at)) return null;
            if (new Vec3(at.x - c.x, 0, at.z - c.z).length() > reach || c.y < at.y - 1.2) return null;
            return new Spot(wall, top, at);
        }
        return null;
    }

    /**
     * A move along the edge: the hang it ends in, and the point it goes round on the way (round an
     * outer corner; null when it goes straight).
     */
    public record Step(Spot spot, Vec3 via) {}

    /**
     * Where a hop from {@code at} (hanging from the wall that way, {@code top} high) goes, to the left
     * or right (author, 2026-10-29, fifth message: on to the next blocks, not only along one edge, and
     * every side of a block): into a wall at the side, turning to it (an inner corner); a block along
     * the same edge; onto the next block's edge a step up or down (half a block or a block); round the
     * end of the block to its side (an outer corner). Null: nowhere to go. {@code straight}: the
     * plain hop along the same edge counts too (the shimmy asks only for the others).
     */
    public static Step next(Player p, Vec3 at, Direction d, double top, boolean left, boolean straight) {
        Level level = p.level();
        Direction side = left ? d.getCounterClockWise() : d.getClockWise();
        Vec3 sv = new Vec3(side.getStepX(), 0, side.getStepZ());
        // an inner corner: a wall at the side within the hop's reach, its edge as high or a little higher
        for (double reach : new double[]{0.05, 0.5, 1.0}) {
            Vec3 beside = at.add(sv.scale(HALF + reach));
            int bx = Mth.floor(beside.x), bz = Mth.floor(beside.z);
            if (emptyAt(level, bx, Mth.floor(at.y + 0.5), bz) && emptyAt(level, bx, Mth.floor(at.y + 1.4), bz)) continue;
            double t2 = topIn(level, bx, bz, top - 0.05, top + 1.05);
            if (Double.isNaN(t2)) return null;
            Vec3 there = hangAt(at, side, face(bx, bz, side), t2);
            return holds(level, there, side, t2) && fits(p, there) ? new Step(new Spot(side, t2, there), null) : null;
        }
        Vec3 there = at.add(sv);
        if (straight && holds(level, there, d, top) && fits(p, there)) return new Step(new Spot(d, top, there), null);
        // the next block's edge a step up or down, its face in line with this one
        Vec3 probe = there.add(d.getStepX() * (HALF + 0.02), 0, d.getStepZ() * (HALF + 0.02));
        int nx = Mth.floor(probe.x), nz = Mth.floor(probe.z);
        if (face(nx, nz, d) <= along(at, d) + HALF + 0.05) {
            double t2 = topIn(level, nx, nz, top - 1.05, top + 1.05);
            if (!Double.isNaN(t2) && Math.abs(t2 - top) > 0.05) {
                Vec3 step = new Vec3(there.x, t2 - HANG, there.z);
                if (fits(p, step) && holds(level, step, d, t2)) return new Step(new Spot(d, t2, step), null);
            }
        }
        // the end of the block: round its corner to its side
        Vec3 hand = at.add(d.getStepX() * (HALF + 0.02), 0, d.getStepZ() * (HALF + 0.02));
        int hx = Mth.floor(hand.x), hz = Mth.floor(hand.z);
        if (Double.isNaN(topIn(level, hx, hz, top - 0.05, top + 0.05))) return null;
        Direction wall = side.getOpposite();
        Vec3 inside = at.add(d.getStepX() * (2 * HALF + 0.002), 0, d.getStepZ() * (2 * HALF + 0.002));
        Vec3 round = hangAt(inside, wall, face(hx, hz, wall), top);
        Vec3 via = at.add(sv.scale(along(round, side) - along(at, side)));
        if (!holds(level, round, wall, top) || !fits(p, round) || !fits(p, via)) return null;
        return new Step(new Spot(wall, top, round), via);
    }

    /** The edge is still there for a player hanging at {@code at} (the wall that way, its top where it was). */
    public static boolean holds(Level level, Vec3 at, Direction d, double top) {
        Vec3 probe = at.add(d.getStepX() * (HALF + 0.02), 0, d.getStepZ() * (HALF + 0.02));
        double t = topIn(level, Mth.floor(probe.x), Mth.floor(probe.z), top - 0.05, top + 0.05);
        return !Double.isNaN(t);
    }

    /** Where a player hanging from the edge whose wall face is at {@code face} stands. */
    static Vec3 hangAt(Vec3 c, Direction d, double face, double top) {
        double x = c.x, z = c.z;
        // face is a coordinate along d: the player's middle is HALF short of it, back in the world's axes
        if (d.getAxis() == Direction.Axis.X) x = (face - HALF - 0.001) * d.getStepX();
        else z = (face - HALF - 0.001) * d.getStepZ();
        return new Vec3(x, top - HANG, z);
    }

    /** Where, along {@code d} (see {@link #along}), the face of block column (bx, bz) is that a player coming from -d meets. */
    static double face(int bx, int bz, Direction d) {
        return switch (d) {
            case EAST -> bx;
            case WEST -> -(bx + 1);
            case SOUTH -> bz;
            default -> -(bz + 1);
        };
    }

    /** A position's coordinate along {@code d} (so that further along d is more). */
    static double along(Vec3 v, Direction d) {
        return d.getAxis() == Direction.Axis.X ? v.x * d.getStepX() : v.z * d.getStepZ();
    }

    /**
     * The top of an edge in column (bx, bz) between lo and hi: a block with something to stand on
     * (its collision at least half a block high) and room above it for a hand (nothing solid in the
     * block above). NaN: none.
     */
    static double topIn(Level level, int bx, int bz, double lo, double hi) {
        for (int by = Mth.floor(hi); by >= Mth.floor(lo) - 1; by--) {
            BlockPos pos = new BlockPos(bx, by, bz);
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double h = shape.max(Direction.Axis.Y);
            double top = by + h;
            if (h < 0.5 || top < lo || top > hi) continue;
            BlockPos up = pos.above();
            VoxelShape above = level.getBlockState(up).getCollisionShape(level, up);
            if (!above.isEmpty() && above.min(Direction.Axis.Y) < 0.6) continue;
            if (h > 1.0) continue;   // a fence or a wall: nothing to hold on its top
            return top;
        }
        return Double.NaN;
    }

    /** The player's hitbox has room at {@code at}. */
    public static boolean fits(Player p, Vec3 at) {
        AABB box = p.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(at).deflate(1.0E-3);
        return p.level().noCollision(p, box);
    }

    /** Where the climb onto the edge ends (on the top, 0.3 block from its edge), and whether there is room. */
    public static Vec3 onTop(Vec3 hang, Direction d, double top) {
        return new Vec3(hang.x + d.getStepX() * 0.6, top, hang.z + d.getStepZ() * 0.6);
    }

    /**
     * A drop in front of a player standing at its edge (for the automatic hang): the edge of the
     * block they stand on, the front of their feet at most {@link #AT_EDGE} from it, with at least
     * two blocks of air under it on the far side. The spot is where they will hang, facing back at it.
     */
    public static Spot dropAhead(Player p) {
        return dropAhead(p, AT_EDGE);
    }

    /** The same, the edge at most {@code near} from the front of the feet. */
    static Spot dropAhead(Player p, double near) {
        Direction d = Direction.fromYRot(p.getYRot());
        Level level = p.level();
        Vec3 c = p.position();
        double feet = c.y;
        double front = along(c, d) + HALF;
        for (double ahead = 0.001; ahead <= near + 0.001; ahead += 0.1) {
            Vec3 probe = c.add(d.getStepX() * (HALF + ahead), 0, d.getStepZ() * (HALF + ahead));
            int bx = Mth.floor(probe.x), bz = Mth.floor(probe.z);
            int by = Mth.floor(feet - 0.01);
            if (!emptyAt(level, bx, by, bz) || !emptyAt(level, bx, by - 1, bz)) continue;
            // the edge is where that column starts; the feet are on the near side of it, close enough
            double gap = face(bx, bz, d) - front;
            if (gap > near || gap < -HALF) return null;
            // the column before it (back towards the player) carries the player's feet: the edge
            int sx = bx - d.getStepX(), sz = bz - d.getStepZ();
            double top = topIn(level, sx, sz, feet - 0.05, feet + 0.05);
            if (Double.isNaN(top)) return null;
            // the wall faces the hanging player the other way round
            Direction wall = d.getOpposite();
            double face = face(sx, sz, wall);
            Vec3 at = hangAt(c, wall, face, top);
            if (!fits(p, at) || !fits(p, new Vec3(at.x, at.y + 0.9, at.z))) return null;
            return new Spot(wall, top, at);
        }
        return null;
    }

    private static boolean emptyAt(Level level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** A hop's target in the request: the way of the edge it goes on to and how many half blocks higher it is. */
    public static int hopTarget(Direction d, double rise) {
        return id(d) | ((int) Math.round(rise * 2) + 4) << 2;
    }

    /** The direction (0-3, Direction's 2D value) as it travels in packets, and back. */
    public static int id(Direction d) {
        return d.get2DDataValue();
    }

    public static Direction dir(int id) {
        return Direction.from2DDataValue(Math.floorMod(id, 4));
    }

    // ------------------------------------------------------------------ the server

    /**
     * A client says its player caught an edge (or moves on it): the server looks for one near where
     * it sees them, loosely (it sees them a tick late). Remembers the edge.
     */
    static boolean valid(ServerPlayer p, Stamina s, Move move, int dirId) {
        if (move == Move.LEDGE_DOWN) {
            if (!p.onGround() && p.fallDistance > 1f) return false;
            // a step's slack: this end sees the player walking to the edge a tick late
            Spot spot = dropAhead(p, AT_EDGE + SLACK);
            if (spot == null) return false;
            remember(s, spot);
            return true;
        }
        if (s.move != null && s.move.ledge()) {
            // moving on an edge already caught; a hop says which edge it goes on to (Ledge.next)
            if (move == Move.LEDGE_HOP_LEFT || move == Move.LEDGE_HOP_RIGHT) {
                int step = (dirId >> 2) - 4;
                if (Math.abs(step) > 2) return false;
                s.ledgeDir = dirId & 3;
                s.ledgeTop += step * 0.5;
            }
            return true;
        }
        Direction d = dir(dirId);
        Spot spot = findTowards(p, d, p.getY() + HANG - 1.5, p.getY() + HANG + 1.5, REACH + SLACK);
        if (spot == null) {
            for (Direction other : Direction.Plane.HORIZONTAL) {
                spot = findTowards(p, other, p.getY() + HANG - 1.5, p.getY() + HANG + 1.5, REACH + SLACK);
                if (spot != null) break;
            }
        }
        // the edge just gone over (the client carries the player back to it)
        Vec3 ground = MoveServer.groundAt(p);
        if (spot == null && ground != null && p.tickCount - MoveServer.lastGround(p) <= LEFT_TICKS + 6) {
            spot = justLeft(p, ground, LEFT_REACH + 1.0, false);
        }
        if (spot == null) return false;
        remember(s, spot);
        return true;
    }

    private static void remember(Stamina s, Spot spot) {
        s.ledgeDir = id(spot.dir);
        s.ledgeTop = spot.top;
    }

    /**
     * Every server tick: nothing hurts from the fall that ended on the edge; while the climb carries
     * the player through the edge's corner, they pass through blocks (set after the player's own
     * tick, so it holds when their movement arrives; the next tick clears it again).
     */
    static void serverTick(ServerPlayer p, Stamina s) {
        if (s.move == null || (!s.move.ledge() && s.move != Move.LEDGE_HELP)) return;
        p.fallDistance = 0f;
        if (s.move.throughEdge()) p.noPhysics = true;
        // creeping along costs energy by the second (author: every move eats energy)
        if (s.move == Move.LEDGE_SHIMMY_LEFT || s.move == Move.LEDGE_SHIMMY_RIGHT) s.spend(MovesConfig.shimmyPerSecond() / 20f, p);
    }

    /**
     * A friend takes the hand of a player hanging below them: both moves start together, the one
     * hanging is told by the server (they did not ask).
     */
    static boolean help(ServerPlayer friend, Entity target) {
        if (!(target instanceof ServerPlayer hanging) || hanging == friend) return false;
        Stamina hs = Stamina.of(hanging);
        if (hs.move == null || !hs.move.hanging()) return false;
        if (friend.distanceToSqr(hanging) > 3.5 * 3.5 || friend.getY() < hanging.getY() + 0.8) return false;
        hs.start(hanging, Move.LEDGE_PULLED, false);
        MovesNet.force(hanging, Move.LEDGE_PULLED, hs.ledgeDir);
        return true;
    }

    /** The climb through the edge's corner does not choke the player in the block (the eyes may brush it). */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void attack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Stamina s = Stamina.of(p);
        if (s.move != null && s.move.ledge() && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) {
            event.setCanceled(true);
        }
    }

    /** Where a friend stands to pull up the player hanging at {@code hang} from an edge that way. */
    public static Vec3 friendAt(Vec3 hang, Direction d, double top) {
        double off = HALF + FRIEND;
        return new Vec3(hang.x + d.getStepX() * off, top, hang.z + d.getStepZ() * off);
    }

    private Ledge() {}
}
