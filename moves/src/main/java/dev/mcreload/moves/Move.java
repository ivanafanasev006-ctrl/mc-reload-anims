package dev.mcreload.moves;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.function.IntSupplier;

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
     * Sprint + C: a slide along the look, faster than running; low the whole way. The animation
     * (1.7 s) plays 1.4 times faster, so its slide (0.15-1.2 s) lasts as long as the push does:
     * 4 blocks tapped, 5 held (then the player crawls on). See {@link #slideSpeed}.
     */
    SLIDE("slide", 1.4f, 25, -1, -1, 2, 18, Side.FRONT, MovesConfig::costSlide);

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
    private final IntSupplier cost;
    /** Horizontal speed (blocks a tick) for each tick from {@link #PUSH_TICK}; empty for the slide. */
    private final float[] push;

    Move(String anim, float animSpeed, int length, int iFrom, int iTo, int lowFrom, int lowTo, Side side, IntSupplier cost, float... push) {
        this.animation = new ResourceLocation(KinetrixMoves.MODID, anim);
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

    public int cost() {
        return cost.getAsInt();
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
