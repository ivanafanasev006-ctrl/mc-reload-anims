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
 * The drawn bow's moves on the player's side (docs/combat-spec.md, 6.2-6.4).
 *
 * <p><b>The flow.</b> After the volley, with the right button still held, the player stays on one
 * knee, draws the next arrow at once and creeps along (6.2). Each left click then is a step of the
 * flow, by the keys held: A / D a roll to that side, S a hop back, none the poses in turn (crouched,
 * standing, back on the knee), with a full turn between two of them. The arrow leaves on the
 * step's release, with the draw it has by then, and the next one is drawn straight after (6.3).</p>
 *
 * <p><b>The Legolas stance.</b> The bow fully drawn, R once: from then on the arrow leaves by itself
 * each time the draw is full, and with A or D held the player dodges to that side spinning round
 * (S or W with it: on the slant), 10 energy a dodge (6.4).</p>
 *
 * <p>Both end when the right button is let go (the last arrow shoots as usual) or the bow is put
 * away. Other players' poses follow the moves the server reports; between two moves they are held
 * while the player draws.</p>
 */
public final class BowStance {
    /** What the local player does with the drawn bow. */
    private enum Mode { NONE, FLOW, LEGOLAS }

    /** A pose held between moves: down on one knee, or side-on at the end of a Legolas spin. */
    private enum Hold { KNEEL, SIDE }

    /** The volley's tick by which the player is down on one knee. */
    private static final int KNELT = 10;
    /** Ticks a player may be seen without a drawn bow (between two arrows, lag) before a held pose ends. */
    private static final int GRACE = 5;
    /** The flow's poses in place, in turn (the volley leaves the player on the knee, so the crouch comes next). */
    private static final Move[] POSES = {Move.FLOW_CROUCH, Move.FLOW_STAND, Move.FLOW_KNEEL};

    private static Mode mode = Mode.NONE;
    private static int pose;
    private static boolean turnLeft;
    /** The local player's move last tick (a pose in place ending is followed by a turn). */
    private static Move lastMove;
    /** Players holding a pose between moves: the pose and the ticks of grace left without a drawn bow. */
    private static final Map<AbstractClientPlayer, HeldPose> HOLDING = new WeakHashMap<>();

    private static final class HeldPose {
        final Hold hold;
        int grace = GRACE;

        HeldPose(Hold hold) {
            this.hold = hold;
        }
    }

    /** The local player is in the flow or the Legolas stance: the left button does not swing the bow. */
    static boolean active(Player p) {
        return p == Minecraft.getInstance().player && mode != Mode.NONE;
    }

    public static boolean kneeling(Player p) {
        HeldPose h = HOLDING.get(p);
        return h != null && h.hold == Hold.KNEEL;
    }

    public static boolean inFlow() {
        return mode == Mode.FLOW;
    }

    public static boolean inLegolas() {
        return mode == Mode.LEGOLAS;
    }

    // ------------------------------------------------------------------ every player

    /**
     * A player's move ended ({@code reached}: the tick it got to), no other one in its place. A
     * volley or a flow step onto the knee leaves them on one knee while they draw; a Legolas spin
     * leaves them side-on. Otherwise those animations (the volley's loops, a spin's frame is held)
     * are stopped.
     */
    static void moveEnded(AbstractClientPlayer p, Move move, int reached) {
        Minecraft mc = Minecraft.getInstance();
        boolean local = p == mc.player;
        boolean bow = Bow.isBow(p.getMainHandItem()) && (!local || mc.options.keyUse.isDown());
        if (move == Move.BARRAGE || move == Move.FLOW_KNEEL) {
            if (bow && p.onGround() && (move != Move.BARRAGE || reached >= KNELT)) {
                HOLDING.put(p, new HeldPose(Hold.KNEEL));
                MoveAnimator.playKneel(p);
                if (local && move == Move.BARRAGE) startMode(Mode.FLOW);
            } else {
                MoveAnimator.stop(p, 4);
            }
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
        if (Bow.drawing(p)) h.grace = GRACE;
        else h.grace--;
        boolean left = local && mode != (h.hold == Hold.KNEEL ? Mode.FLOW : Mode.LEGOLAS);
        if (h.grace < 0 || !Bow.isBow(p.getMainHandItem()) || (h.hold == Hold.KNEEL && !p.onGround()) || left) {
            HOLDING.remove(p);
            MoveAnimator.stop(p, 4);
            return;
        }
        KinetrixBridge.wholeBody(p.getUUID(), 200);
        if (local && h.hold == Hold.KNEEL) p.setSprinting(false);
    }

    // ------------------------------------------------------------------ the local player

    private static void startMode(Mode m) {
        mode = m;
        pose = 0;
        turnLeft = true;
    }

    /** R: with the bow fully drawn it starts the Legolas stance (instead of a dodge). Returns whether R was taken. */
    static boolean dodgeKey(LocalPlayer p) {
        if (mode == Mode.LEGOLAS || !Bow.fullyDrawn(p)) return false;
        startMode(Mode.LEGOLAS);
        return true;
    }

    /** The left button: a step of the flow; in the Legolas stance it does nothing. Returns whether it was taken. */
    static boolean attackKey(LocalPlayer p, Stamina s) {
        if (mode == Mode.LEGOLAS) return true;
        if (mode != Mode.FLOW) return false;
        if (!Bow.drawing(p) || (s.move != null && s.moveTick < s.move.freeFrom())) return true;
        Minecraft mc = Minecraft.getInstance();
        boolean left = mc.options.keyLeft.isDown(), right = mc.options.keyRight.isDown();
        Move step = null;
        if (left != right) step = left ? Move.FLOW_ROLL_LEFT : Move.FLOW_ROLL_RIGHT;
        else if (mc.options.keyDown.isDown()) step = Move.FLOW_BACKSTEP;
        // Out of energy for a roll or a hop back: a shot in place (the bar flashes).
        if (step != null && MovesClient.start(p, s, step, slant(mc, step))) return true;
        step = POSES[pose];
        pose = (pose + 1) % POSES.length;
        MovesClient.start(p, s, step, null);
        return true;
    }

    /** Every client tick, before the keys: the arrows, the turns, the end of the flow and the stance. */
    static void tickLocal(LocalPlayer p, Stamina s) {
        Minecraft mc = Minecraft.getInstance();
        boolean held = mc.options.keyUse.isDown() && Bow.isBow(p.getMainHandItem());
        if (mode != Mode.NONE && !held) {
            mode = Mode.NONE;
            // Let go before the arrow left: vanilla's release has shot it, the step (or a turn) stops here.
            boolean turning = s.move == Move.FLOW_TURN_LEFT || s.move == Move.FLOW_TURN_RIGHT;
            if (s.move != null && s.move.drawnBow() && (turning || s.moveTick < s.move.shot.release())) MovesClient.cancel(p, s, 4);
        }
        // The arrow of a flow step leaves on its animation's release.
        if (s.move != null && s.move.drawnBow() && s.moveTick == s.move.shot.release() && Bow.drawing(p)) loose(p);
        if (mode == Mode.FLOW && s.move == null && (lastMove == Move.FLOW_CROUCH || lastMove == Move.FLOW_STAND)
                && Bow.drawing(p)) {
            // Between two poses in place: a full turn, to either side in turn.
            MovesClient.start(p, s, turnLeft ? Move.FLOW_TURN_LEFT : Move.FLOW_TURN_RIGHT, null);
            turnLeft = !turnLeft;
        }
        if (mode == Mode.LEGOLAS && Bow.fullyDrawn(p) && (s.move == null || s.moveTick >= s.move.freeFrom())) {
            loose(p);
            boolean left = mc.options.keyLeft.isDown(), right = mc.options.keyRight.isDown();
            if (left != right) {
                Move dodge = left ? Move.LEGOLAS_LEFT : Move.LEGOLAS_RIGHT;
                MovesClient.start(p, s, dodge, slant(mc, dodge));
            }
        }
        lastMove = s.move;
    }

    /** The arrow leaves now, with the draw it has; with the right button still held the next one is drawn at once. */
    private static void loose(LocalPlayer p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null) return;
        mc.gameMode.releaseUsingItem(p);
        if (mc.options.keyUse.isDown()) mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
    }

    /**
     * Which way a dodge with the bow goes: to its side, and with S or W held also back or forward
     * (S + A: back and to the left). Unit length, horizontal.
     */
    private static Vec3 slant(Minecraft mc, Move move) {
        float yaw = mc.player.getYRot();
        Vec3 dir = move.direction(yaw);
        if (move.side == Move.Side.LEFT || move.side == Move.Side.RIGHT) {
            float r = yaw * Mth.DEG_TO_RAD;
            Vec3 forward = new Vec3(-Mth.sin(r), 0, Mth.cos(r));
            if (mc.options.keyDown.isDown() && !mc.options.keyUp.isDown()) dir = dir.subtract(forward);
            else if (mc.options.keyUp.isDown() && !mc.options.keyDown.isDown()) dir = dir.add(forward);
        }
        return dir.normalize();
    }

    /** On one knee the player creeps, as when sneaking with a drawn bow, and does not jump. */
    static void movementInput(Player p, Input in) {
        if (!kneeling(p)) return;
        float k = MovesConfig.kneelMoveFactor();
        in.forwardImpulse *= k;
        in.leftImpulse *= k;
        in.jumping = false;
    }

    private BowStance() {}
}
