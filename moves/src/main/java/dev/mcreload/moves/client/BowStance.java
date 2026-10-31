package dev.mcreload.moves.client;

import dev.mcreload.moves.Bow;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.MovesConfig;
import dev.mcreload.moves.Stamina;
import dev.mcreload.moves.compat.KinetrixBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The drawn bow's moves on the player's side (docs/combat-spec.md, 6.2, 6.4).
 *
 * <p><b>On the knee after G.</b> With the right button held after the volley, the player stays on one
 * knee, draws the next arrow at once and creeps along (6.2). The left button does nothing there but
 * mark mobs (BowVolley): the old combat flow (dodges and poses on the left button) is gone, the Legolas
 * stance took its place (author, 2026-10-31).</p>
 *
 * <p><b>The Legolas stance.</b> The bow fully drawn, R once: from then on the arrow leaves by itself
 * each time the draw is full, and after it the player dodges spinning round: to the side of A or
 * D, the way the movement keys point (S + A: back and to the left), almost in place with none,
 * 5 energy a dodge (6.4). It ends when the right button is let go (the last arrow shoots as usual) or
 * the bow is put away; a new one waits MovesConfig.legolasCooldown from that end (author, 2026-10-31:
 * "10-15 seconds").</p>
 *
 * <p>Other players' poses follow the moves the server reports; between two moves they are held while
 * the player draws.</p>
 */
public final class BowStance {
    /** What the local player does with the drawn bow. */
    private enum Mode { NONE, LEGOLAS }

    /** A pose held between moves: down on one knee after the volley, side-on at the end of a Legolas spin. */
    private enum Hold { KNEEL, SIDE }

    /** The volley's tick by which the player is down on one knee. */
    private static final int KNELT = 10;
    /** Ticks a player may be seen without a drawn bow (between two arrows, lag) before a held pose ends. */
    private static final int GRACE = 5;

    private static Mode mode = Mode.NONE;
    /** Which way the next dodge without A or D spins (they take turns). */
    private static boolean spinLeft;
    /** A dodge of this Legolas stance was made: its end starts the cooldown (as the server counts it). */
    private static boolean spun;
    /** Players holding a pose between moves: the pose and the ticks of grace left without a drawn bow. */
    private static final Map<AbstractClientPlayer, HeldPose> HOLDING = new WeakHashMap<>();

    private static final class HeldPose {
        final Hold hold;
        int grace = GRACE;

        HeldPose(Hold hold) {
            this.hold = hold;
        }
    }

    /** The local player is in the Legolas stance: the left button does not swing the bow. */
    static boolean active(Player p) {
        return p == Minecraft.getInstance().player && mode != Mode.NONE;
    }

    /** A bow pose is held on the player between moves (on the knee, side-on). */
    static boolean holding(Player p) {
        return HOLDING.containsKey(p);
    }

    public static boolean kneeling(Player p) {
        HeldPose h = HOLDING.get(p);
        return h != null && h.hold == Hold.KNEEL;
    }

    public static boolean inLegolas() {
        return mode == Mode.LEGOLAS;
    }

    // ------------------------------------------------------------------ every player

    /**
     * A player's move ended ({@code reached}: the tick it got to), no other one in its place. A
     * volley leaves them on one knee while they draw; a Legolas spin leaves them side-on. Otherwise
     * those animations (the volley's loops, a spin's frame is held) are stopped.
     */
    static void moveEnded(AbstractClientPlayer p, Move move, int reached) {
        Minecraft mc = Minecraft.getInstance();
        boolean local = p == mc.player;
        boolean bow = Bow.isBow(p.getMainHandItem()) && (!local || mc.options.keyUse.isDown());
        if (move.volley()) {
            if (bow && p.onGround() && reached >= KNELT) {
                HOLDING.put(p, new HeldPose(Hold.KNEEL));
                MoveAnimator.playKneel(p);
            } else {
                MoveAnimator.stop(p, 4);
            }
        } else if (move.marked()) {
            // the volley at the marked is over: its last step's frame is not held
            MoveAnimator.stop(p, 4);
        } else if (move.legolas()) {
            // The spin's last frame is held (the animation stops there by itself) until the next one.
            if (bow) HOLDING.put(p, new HeldPose(Hold.SIDE));
            else MoveAnimator.stop(p, 4);
        }
    }

    /** Every client tick, for every player: a held pose ends once the bow is no longer drawn. */
    static void tick(AbstractClientPlayer p) {
        HeldPose h = HOLDING.get(p);
        if (h == null) return;
        Stamina s = Stamina.of(p);
        if (s.move != null) {
            HOLDING.remove(p);    // a new move took over, with its own animation
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean local = p == mc.player;
        // The stances wait while the bow is drawn, a few ticks more between two arrows.
        if (Bow.drawing(p)) h.grace = GRACE;
        else h.grace--;
        boolean left = local && h.hold == Hold.SIDE && mode != Mode.LEGOLAS;
        if (h.grace < 0 || !Bow.isBow(p.getMainHandItem()) || (h.hold == Hold.KNEEL && !p.onGround()) || left) {
            HOLDING.remove(p);
            MoveAnimator.stop(p, 4);
            return;
        }
        KinetrixBridge.wholeBody(p.getUUID(), 200);
        if (local && h.hold == Hold.KNEEL) p.setSprinting(false);
    }

    // ------------------------------------------------------------------ the local player

    /**
     * R: with the bow fully drawn it starts the Legolas stance (instead of a dodge), once its cooldown
     * is over. Returns whether R was taken.
     */
    static boolean dodgeKey(LocalPlayer p) {
        Stamina s = Stamina.of(p);
        if (mode == Mode.LEGOLAS || !Bow.fullyDrawn(p) || !dev.mcreload.moves.HudConfig.Moves.LEGOLAS.on()
                || (s.move != null && !s.move.legolas())) return false;
        if (StrikeInput.waits(p, s, Move.LEGOLAS_LEFT)) return true;
        mode = Mode.LEGOLAS;
        spinLeft = true;
        spun = false;
        return true;
    }

    /** Every client tick, before the keys: the Legolas stance's arrows and dodges, and its end. */
    static void tickLocal(LocalPlayer p, Stamina s) {
        Minecraft mc = Minecraft.getInstance();
        boolean held = mc.options.keyUse.isDown() && Bow.isBow(p.getMainHandItem());
        if (mode != Mode.NONE && !held) {
            mode = Mode.NONE;
            // the stance is over: a new one waits (the server starts the same wait, Bow.legolasTick)
            if (spun) s.legolasCooldown = s.cooled(dev.mcreload.moves.MovesConfig.legolasCooldownTicks());
            spun = false;
        }
        // The Legolas stance: the arrow by itself at every full draw, then a dodge: to the side of A
        // or D, else the other way from the last one; pushed the way the keys point, so almost in
        // place with none.
        if (mode == Mode.LEGOLAS && Bow.fullyDrawn(p) && (s.move == null || s.moveTick >= s.move.freeFrom())) {
            loose(p);
            boolean left = mc.options.keyLeft.isDown(), right = mc.options.keyRight.isDown();
            boolean toLeft = left != right ? left : spinLeft;
            if (MovesClient.start(p, s, toLeft ? Move.LEGOLAS_LEFT : Move.LEGOLAS_RIGHT, keys(mc))) {
                spinLeft = !toLeft;
                spun = true;
            }
        }
    }

    /** The arrow leaves now, with the draw it has; with the right button still held the next one is drawn at once. */
    private static void loose(LocalPlayer p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null) return;
        mc.gameMode.releaseUsingItem(p);
        if (mc.options.keyUse.isDown()) mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
    }

    /**
     * Which way a dodge with the bow goes: the way the movement keys point (S + A: back and to the
     * left), unit length and horizontal; zero with none (almost in place).
     */
    static Vec3 keys(Minecraft mc) {
        float r = mc.player.getYRot() * Mth.DEG_TO_RAD;
        // Minecraft's own strafe maths (Entity.moveRelative): forward = (-sin, cos), left = (cos, sin).
        Vec3 forward = new Vec3(-Mth.sin(r), 0, Mth.cos(r)), left = new Vec3(Mth.cos(r), 0, Mth.sin(r));
        Vec3 dir = Vec3.ZERO;
        if (mc.options.keyUp.isDown()) dir = dir.add(forward);
        if (mc.options.keyDown.isDown()) dir = dir.subtract(forward);
        if (mc.options.keyLeft.isDown()) dir = dir.add(left);
        if (mc.options.keyRight.isDown()) dir = dir.subtract(left);
        return dir.lengthSqr() < 1.0E-4 ? Vec3.ZERO : dir.normalize();
    }

    /** On one knee the player creeps, as when sneaking with a drawn bow, and does not jump. */
    static void movementInput(Player p, Input in) {
        if (kneeling(p)) creep(in);
    }

    /** The keys creep (on one knee: after G, in the shot out of a slide), no jumping. */
    static void creep(Input in) {
        float k = MovesConfig.kneelMoveFactor();
        in.forwardImpulse *= k;
        in.leftImpulse *= k;
        in.jumping = false;
    }

    private BowStance() {}
}
