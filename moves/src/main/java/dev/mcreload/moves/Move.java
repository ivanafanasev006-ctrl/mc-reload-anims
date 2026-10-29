package dev.mcreload.moves;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.function.DoubleSupplier;

/**
 * The moves, each tied to the keys of its animation (docs/moves.md). Times are game ticks from the
 * start of the move (20 a second); a move played faster than its animation has its ticks already
 * divided by the speed.
 *
 * <p>Both ends use the same table: the client to move the player and play the animation, the
 * server to make them untouchable or low at the right ticks.</p>
 */
public enum Move {
    /** R + A: push at 0.08 s, about 2.2 blocks by 0.29 s, untouchable from the push to the landing. */
    DODGE_LEFT("dodge_left", 1f, 13, 2, 6, -1, -1, Side.LEFT, MovesConfig::costDodge, 0.62f, 0.52f, 0.40f, 0.26f),
    /** R + D: the mirror of the left dodge. */
    DODGE_RIGHT("dodge_right", 1f, 13, 2, 6, -1, -1, Side.RIGHT, MovesConfig::costDodge, 0.62f, 0.52f, 0.40f, 0.26f),
    /** R + S: roll back, about 3 blocks by 0.5 s; low (0.6 block) while tucked, untouchable 0.1-0.45 s. */
    DODGE_BACK("dodge_back", 1f, 18, 2, 9, 2, 12, Side.BACK, MovesConfig::costRoll, 0.45f, 0.43f, 0.40f, 0.36f, 0.31f, 0.27f, 0.22f, 0.18f),
    /** R + W: roll forward, the same as the back roll. */
    DODGE_FRONT("dodge_front", 1f, 18, 2, 9, 2, 12, Side.FRONT, MovesConfig::costRoll, 0.45f, 0.43f, 0.40f, 0.36f, 0.31f, 0.27f, 0.22f, 0.18f),
    /**
     * Sprint + C: a slide along the look, faster than running; low from the first tick (a sprint
     * into a one-block gap must not hit its edge before the body drops). The animation
     * (1.7 s) plays 1.4 times faster, so its slide (0.15-1.2 s) lasts as long as the push does:
     * 4 blocks tapped, 5 held (then the player crawls on). See {@link #slideSpeed}.
     */
    SLIDE("slide", 1.4f, 25, -1, -1, 1, 18, Side.FRONT, MovesConfig::costSlide),
    /**
     * R + W at point blank to a mob of some size (a zombie, a skeleton, a pig, a sheep; not a
     * chicken): a vault over its back (hop_roll_dodge, docs/combat-spec.md, 5), quick and on the
     * run (author, 2026-10-29: "run, press, over, run on without a stop"). Crouch until 0.2 s (its
     * blow in this window is parried), the hand on its head, take-off at 0.2 s pressing it down, a
     * sideways roll over it, the foot shoving it back at 0.63 s, landing beyond it at 0.72 s and
     * running on. It cannot hurt the player until the landing. See {@link #HOP_TAKEOFF}.
     */
    HOP("hop_roll_dodge", 1f, 22, -1, -1, -1, -1, Side.FRONT, MovesConfig::costHop),
    /**
     * The volley (docs/combat-spec.md, 6.2): a fully drawn bow, then the left button. A sharp step
     * back down onto a knee, sliding on into the kneel (1.85 blocks in 0.1-0.55 s; author: a block
     * more than the 0.85 it was), and three arrows at the animation's releases, 0.54, 0.79 and
     * 1.04 s (Bow.VOLLEY_SHOTS).
     */
    BARRAGE("bow_barrage", 1f, 24, -1, -1, -1, -1, Side.BACK, MovesConfig::costBarrage,
            0.30f, 0.28f, 0.26f, 0.23f, 0.20f, 0.17f, 0.14f, 0.11f, 0.09f, 0.07f),

    // ---- the bow's combat flow (docs/combat-spec.md, 6.3): after the volley, the right button
    // held, each left click is a dodge by the keys held then (a spin almost in place with none),
    // and straight after it the next pose of the round: crouched, standing, on one knee. The bow
    // stays drawn through both; the arrow leaves on the pose's release, with the draw it has.
    // Dodges: 10 energy; another move may follow 4 ticks before their end (the pose, on the
    // server a little late).
    /** No keys (or W: forward): a full spin to the left, almost in place; untouchable like a side dodge. */
    FLOW_SPIN_LEFT("bow_flow_turn_left", Bow.shot(0, 13, -1, 9), 13, 2, 6, -1, -1, Side.FRONT, MovesConfig::costBowDodge,
            0.62f, 0.52f, 0.40f, 0.26f),
    /** The same to the right, every other click. */
    FLOW_SPIN_RIGHT("bow_flow_turn_right", Bow.shot(0, 13, -1, 9), 13, 2, 6, -1, -1, Side.FRONT, MovesConfig::costBowDodge,
            0.62f, 0.52f, 0.40f, 0.26f),
    /** A: a roll to the left (0.08-0.5 s, as far as a back roll), low and untouchable like one. */
    FLOW_ROLL_LEFT("bow_flow_roll_left_shot", Bow.shot(0, 11, -1, 7), 11, 2, 9, 2, 10, Side.LEFT, MovesConfig::costBowDodge,
            0.45f, 0.43f, 0.40f, 0.36f, 0.31f, 0.27f, 0.22f, 0.18f),
    /** D: the same to the right. */
    FLOW_ROLL_RIGHT("bow_flow_roll_right_shot", Bow.shot(0, 11, -1, 7), 11, 2, 9, 2, 10, Side.RIGHT, MovesConfig::costBowDodge,
            0.45f, 0.43f, 0.40f, 0.36f, 0.31f, 0.27f, 0.22f, 0.18f),
    /** S: a hop back (about 1.3 blocks, landing at 0.44 s), untouchable like a side dodge. */
    FLOW_BACKSTEP("bow_flow_backstep_shot", Bow.shot(0, 10, -1, 6), 10, 2, 6, -1, -1, Side.BACK, MovesConfig::costBowDodge,
            0.30f, 0.26f, 0.22f, 0.17f, 0.12f, 0.07f),
    /** The round's poses, from where their animations are already in the pose: crouched side-on, the arrow at 0.6 s. */
    FLOW_CROUCH("bow_flow_crouch_shot", Bow.shot(3, -1, 12, 17), 27, -1, -1, -1, -1, Side.FRONT, () -> 0),
    /** Standing side-on, the arrow at 0.6 s. */
    FLOW_STAND("bow_flow_stand_shot", Bow.shot(3, -1, 12, 17), 27, -1, -1, -1, -1, Side.FRONT, () -> 0),
    /** Down onto one knee (the end of the volley's spin), the arrow at 0.15 s; then held on the knee. */
    FLOW_KNEEL("bow_barrage", Bow.shot(8, -1, 3, 6), 6, -1, -1, -1, -1, Side.FRONT, () -> 0),

    // ---- the Legolas stance (docs/combat-spec.md, 6.4): the bow fully drawn, R once. Each time the
    // draw is full again the arrow leaves by itself, then a dodge spinning round: to the side of A or
    // D, the way the keys point (S + A: back and to the left), almost in place with none. The halves
    // of bow_walk_around, 1 s each.
    /** A (or every other dodge without it): the spin to the left (the first half), pushed like a side dodge, untouchable as one. */
    LEGOLAS_LEFT("bow_walk_around", Bow.shot(0, 20, -1, 17), 20, 2, 6, -1, -1, Side.LEFT, MovesConfig::costBowDodge,
            0.62f, 0.52f, 0.40f, 0.26f),
    /** D: the spin back to the right (the second half). */
    LEGOLAS_RIGHT("bow_walk_around", Bow.shot(20, 40, -1, 17), 20, 2, 6, -1, -1, Side.RIGHT, MovesConfig::costBowDodge,
            0.62f, 0.52f, 0.40f, 0.26f),

    // ---- sword moves (author, 2026-10-28)
    /**
     * Sprinting with W and a sword, the left button: a dash into a lunge, the blade into the
     * belly (dash_strike). The run carries on and speeds up into the lunge (about 3.4 blocks by
     * 0.45 s, less if someone is in the way); the blade hits the first one it reaches on the way,
     * from 0.25 s to 0.55 s (Strikes.DASH_REACH_FROM, DASH_REACH_TO), with a crit, as a blow in a
     * jump does. The run follows the look and goes on to the mob's side. It neither stops nor slows
     * the player (author, 2026-10-29): from the stab the player runs on at once, the animation
     * playing out over the run. Then 30 s before the next one (MovesConfig.dashCooldownTicks).
     */
    DASH("dash_strike", 1f, 31, -1, -1, -1, -1, Side.FRONT, MovesConfig::costDash),
    /**
     * A weakened mob (a fifth of its health or less) at point blank, the left button held with a
     * sword or an axe: the finishing stab (finisher_stab). The free hand takes it by the shoulder, the blade
     * goes in at 0.35 s and it is held on it until the blade comes out at 0.95 s, when it dies
     * (Strikes.FINISH_STAB, FINISH_KILL). Not on bosses. The player stands still meanwhile, so
     * nothing hurts them until the kill (author), the mob least of all.
     */
    FINISHER("finisher_stab", 1f, 30, 0, Strikes.FINISH_KILL + 1, -1, -1, Side.FRONT, MovesConfig::costFinisher),

    // ---- the author's second message of 2026-10-29
    /**
     * W held, Space and R with a mob of some size in front: the vault with a kick (hop_on_kick),
     * a cutscene (author: "slowed time and perception, a hard kick and a shove, the player comes
     * back round"). Over the mob and a hang in the air turned to its back (0.7-1.27 s), the kick
     * at 1.27 s (Kick.HIT: 1 heart, flung away), the turn back and the landing at 1.95 s. The mob
     * is held still meanwhile and nothing hurts the player until the landing.
     */
    KICK("hop_on_kick", 1f, 52, 0, Kick.LAND, -1, -1, Side.FRONT, MovesConfig::costKick),
    /**
     * The left button while sliding, a sword or an axe in hand: a half turn clockwise with the blade
     * out to the right (slide_under_attack), a crit to everything it sweeps through at 0.3 s, and the
     * push of a tapped slide carrying the player on the way they slid, now backwards; they stand up
     * facing the way they turned. Low like the slide while it slides. 30 s before the next one.
     */
    SLIDE_ATTACK("slide_under_attack", 1f, 28, -1, -1, 0, 14, Side.FRONT, MovesConfig::costSlideAttack),
    /** R held while falling: tucked until the landing (fall_tuck, looped). */
    FALL_TUCK("fall_tuck", 1f, Move.FOREVER, -1, -1, -1, -1, Side.FRONT, () -> 0),
    /**
     * The landing out of the tuck: a roll forward (1.6 blocks, 0.08-0.5 s), then on one knee, stunned,
     * until 1.5 s, and up by 1.9 s (fall_roll). The fall hurts 70 % less (Fall).
     */
    FALL_ROLL("fall_roll", 1f, 41, -1, -1, 2, 10, Side.FRONT, () -> 0),

    // ---- the ledge (Ledge): the mechanic carries the player, the animations hold on to the edge
    /** Hanging by one hand, swinging (ledge_hang, looped from 1.25 s). */
    LEDGE_HANG("ledge_hang", 1f, Move.FOREVER, -1, -1, -1, -1, Side.FRONT, () -> 0),
    /** A held: hand over hand along the edge to the left (0.59 block a second). */
    LEDGE_SHIMMY_LEFT("ledge_shimmy_left", 1f, Move.FOREVER, -1, -1, -1, -1, Side.LEFT, () -> 0),
    /** D held: the same to the right. */
    LEDGE_SHIMMY_RIGHT("ledge_shimmy_right", 1f, Move.FOREVER, -1, -1, -1, -1, Side.RIGHT, () -> 0),
    /** In the hop mode (R), A: a hop a block along the edge to the left (carried 0.55-0.85 s). */
    LEDGE_HOP_LEFT("ledge_hop_left", 1f, 40, -1, -1, -1, -1, Side.LEFT, MovesConfig::costLedgeHop),
    /** In the hop mode, D: the same to the right. */
    LEDGE_HOP_RIGHT("ledge_hop_right", 1f, 40, -1, -1, -1, -1, Side.RIGHT, MovesConfig::costLedgeHop),
    /** W + Space: up onto the top (2.0 s). */
    LEDGE_CLIMB("ledge_climb_up", 1f, 43, -1, -1, -1, -1, Side.FRONT, () -> 0),
    /** Ctrl + W + C at a drop: the player turns round and lowers themself over the edge into the hang (1.55 s). */
    LEDGE_DOWN("ledge_climb_down", 1f, 34, -1, -1, -1, -1, Side.FRONT, () -> 0),
    /** Pulled up by a friend (the server starts it, when the friend takes their hand). */
    LEDGE_PULLED("ledge_pulled_up", 1f, 55, -1, -1, -1, -1, Side.FRONT, () -> 0),
    /** The friend: the right button on a player hanging below them; they kneel, take the hand and pull. */
    LEDGE_HELP("ledge_help_pull", 1f, 55, -1, -1, -1, -1, Side.BACK, () -> 0);

    /** The length of a move that lasts until it is ended (a hang, a fall): never runs out. */
    public static final int FOREVER = 1 << 24;

    /** The vault: take-off and landing ticks (0.2 s and 0.72 s of the animation; author, 2026-10-29: quick,
     *  "1 s or 1.25" in all, so the PC session's longer push-off, landing at 1.0 s, was taken back). */
    public static final int HOP_TAKEOFF = 4, HOP_LAND = 14;
    /** The vault shoves the mob back as the foot kicks off its back (0.63 s of the animation). */
    public static final int HOP_SHOVE = 13;
    /** The attack out of a slide: up from its crouch (0.8 s), moving breaks off the rest of the getting up. */
    public static final int SLIDE_ATTACK_UP = 16;
    /** The vault lands this far beyond the mob it goes over. */
    public static final float HOP_BEYOND = 1.4f;
    /** Side dodges knock an arrow aside with the weapon in these ticks (0-0.21 s, docs/moves.md). */
    public static final int DEFLECT_FROM = 0, DEFLECT_TO = 4;

    /** The slide push: full speed (12 blocks a second), then an even slowdown to a stop at the chosen
     *  length. 4 blocks take 0.6 s, 5 blocks 0.7 s; running takes 0.7 and 0.9 s. */
    public static final float SLIDE_TOP = 0.6f, SLIDE_BRAKE = 0.035f;
    /** Slide tick at which a held C hands the player over to crawling (the end of the slide in the animation). */
    public static final int SLIDE_CRAWL_TICK = 17;
    /** The push starts here (the animation's 0.08 s for dodges and rolls, 0.15 s for the slide). */
    public static final int PUSH_TICK = 2;

    public final ResourceLocation animation;
    public final float animSpeed;
    /** Ticks until the move is over and the player is free again (the animation's length plus its fade). */
    public final int length;
    /** Untouchable from {@code iFrom} to {@code iTo} (inclusive); -1 for none. */
    public final int iFrom, iTo;
    /** A 0.6-block hitbox from {@code lowFrom} to {@code lowTo} (inclusive); -1 for none. */
    public final int lowFrom, lowTo;
    public final Side side;
    /** Bow moves: where the animation starts and stops, when the arrow leaves; null for the rest. */
    public final Bow.Shot shot;
    private final DoubleSupplier cost;
    /** Horizontal speed (blocks a tick) for each tick from {@link #PUSH_TICK}; empty for the slide. */
    private final float[] push;

    Move(String anim, float animSpeed, int length, int iFrom, int iTo, int lowFrom, int lowTo, Side side, DoubleSupplier cost, float... push) {
        this(anim, animSpeed, null, length, iFrom, iTo, lowFrom, lowTo, side, cost, push);
    }

    Move(String anim, Bow.Shot shot, int length, int iFrom, int iTo, int lowFrom, int lowTo, Side side, DoubleSupplier cost, float... push) {
        this(anim, 1f, shot, length, iFrom, iTo, lowFrom, lowTo, side, cost, push);
    }

    Move(String anim, float animSpeed, Bow.Shot shot, int length, int iFrom, int iTo, int lowFrom, int lowTo, Side side, DoubleSupplier cost, float... push) {
        this.animation = new ResourceLocation(KinetrixMoves.MODID, anim);
        this.shot = shot;
        this.animSpeed = animSpeed;
        this.length = length;
        this.iFrom = iFrom;
        this.iTo = iTo;
        this.lowFrom = lowFrom;
        this.lowTo = lowTo;
        this.side = side;
        this.cost = cost;
        this.push = push;
    }

    public float cost() {
        return (float) cost.getAsDouble();
    }

    public boolean untouchable(int tick) {
        return iFrom >= 0 && tick >= iFrom && tick <= iTo;
    }

    public boolean low(int tick) {
        return lowFrom >= 0 && tick >= lowFrom && tick <= lowTo;
    }

    public boolean rolls() {
        return this == DODGE_BACK || this == DODGE_FRONT;
    }

    public boolean sideDodge() {
        return this == DODGE_LEFT || this == DODGE_RIGHT;
    }

    /** A move of the drawn bow (the flow, the Legolas stance): the bow stays drawn through it. */
    public boolean drawnBow() {
        return shot != null;
    }

    public boolean legolas() {
        return this == LEGOLAS_LEFT || this == LEGOLAS_RIGHT;
    }

    /** A move made at someone (the vault, the kick, the finisher, a friend's hand): the client names them, the server checks them. */
    public boolean targeted() {
        return this == HOP || this == KICK || this == FINISHER || this == LEDGE_HELP;
    }

    /** A sword move started by the left button: while it plays the button strikes nothing else. */
    public boolean strike() {
        return this == DASH || this == FINISHER || this == SLIDE_ATTACK;
    }

    /** On the ledge: hanging, moving along it, climbing onto it or off it, being pulled up (not the friend pulling). */
    public boolean ledge() {
        return this == LEDGE_HANG || this == LEDGE_SHIMMY_LEFT || this == LEDGE_SHIMMY_RIGHT || this == LEDGE_HOP_LEFT
                || this == LEDGE_HOP_RIGHT || this == LEDGE_CLIMB || this == LEDGE_DOWN || this == LEDGE_PULLED;
    }

    /** Hanging on the ledge by the hands, as a friend can pull them up and the keys can let go. */
    public boolean hanging() {
        return this == LEDGE_HANG || this == LEDGE_SHIMMY_LEFT || this == LEDGE_SHIMMY_RIGHT;
    }

    /** The mechanic carries the player through the corner of the edge: no collisions (Ledge.serverTick). */
    public boolean throughEdge() {
        return this == LEDGE_CLIMB || this == LEDGE_DOWN || this == LEDGE_PULLED;
    }

    /**
     * This move may take over from {@code now} at any moment (not only once it has played out): the
     * attack out of a slide, the roll out of a tuck, the moves on a ledge from the hang and back.
     */
    public boolean follows(Move now) {
        if (now == null) return false;
        return switch (this) {
            case SLIDE_ATTACK -> now == SLIDE;
            case FALL_ROLL -> now == FALL_TUCK;
            case LEDGE_HANG -> now == FALL_TUCK || now.hanging() || now == LEDGE_HOP_LEFT || now == LEDGE_HOP_RIGHT || now == LEDGE_DOWN;
            case LEDGE_SHIMMY_LEFT, LEDGE_SHIMMY_RIGHT, LEDGE_HOP_LEFT, LEDGE_HOP_RIGHT, LEDGE_CLIMB, LEDGE_PULLED -> now.hanging();
            default -> false;
        };
    }

    /** Started by the server on a player who did not ask (pulled up by a friend): their own client plays it when told. */
    public boolean serverStarted() {
        return this == LEDGE_PULLED;
    }

    /** Seen in first person too (the arms and what they hold), as the sword guard is (author, 2026-10-28). */
    public boolean firstPerson() {
        return this == HOP || this == DASH || this == FINISHER || this == SLIDE_ATTACK || this == FALL_ROLL || ledge();
    }

    /**
     * The tick from which moving, jumping or striking ends the move early: once its push is over
     * (a dodge or a roll gets up, the hop has landed), so it does not hold the player in place
     * while it plays out. -1: never (the slide decides by its own push, see the client).
     */
    public int recoverFrom() {
        return switch (this) {
            case DODGE_LEFT, DODGE_RIGHT, DODGE_BACK, DODGE_FRONT -> PUSH_TICK + push.length + 1;
            case HOP -> HOP_LAND + 1;
            case DASH -> Strikes.DASH_FREE;
            case KICK -> Kick.LAND + 1;
            case SLIDE_ATTACK -> SLIDE_ATTACK_UP;
            case FALL_ROLL -> Fall.STAND;
            case FINISHER -> Strikes.FINISH_KILL + 1;
            default -> -1;
        };
    }

    /** The tick from which another move may cut in: once this one has played out (its fade aside), or earlier for some bow moves. */
    public int freeFrom() {
        return shot != null ? shot.freeFrom() : length - 3;
    }

    /** Push speed at this tick of a dodge or roll (blocks a tick), 0 outside the push. */
    public float pushSpeed(int tick) {
        int i = tick - PUSH_TICK;
        return i >= 0 && i < push.length ? push[i] : 0f;
    }

    /**
     * Slide speed for this tick: full speed, then braking evenly so the player stops after
     * {@code blocks}. {@code done} is how far the slide has gone so far.
     */
    public static float slideSpeed(float blocks, float done) {
        float left = blocks - done;
        if (left <= 0.02f) return 0f;
        return Math.min(SLIDE_TOP, Math.min(left, Mth.sqrt(2f * SLIDE_BRAKE * left) + SLIDE_BRAKE * 0.5f));
    }

    /** The direction of the push for a player facing {@code yaw} (degrees), unit length, horizontal. */
    public Vec3 direction(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        double sin = Mth.sin(r), cos = Mth.cos(r);
        // Minecraft's own strafe maths (Entity.moveRelative): forward = (-sin, cos), left = (cos, sin).
        return switch (side) {
            case FRONT -> new Vec3(-sin, 0, cos);
            case BACK -> new Vec3(sin, 0, -cos);
            case LEFT -> new Vec3(cos, 0, sin);
            case RIGHT -> new Vec3(-cos, 0, -sin);
        };
    }

    public static Move byId(int id) {
        Move[] all = values();
        return id >= 0 && id < all.length ? all[id] : null;
    }

    public enum Side { FRONT, BACK, LEFT, RIGHT }
}
