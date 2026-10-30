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
 * <p><b>The flow.</b> After the volley (G), with the right button held, the player stays on one
 * knee, draws the next arrow at once and creeps along (6.2). Each left click then is a dodge by the
 * keys held (A / D a roll to that side, S a hop back, none a spin almost in place, W a spin
 * forward) and straight after it the round's next pose: crouched, standing, on one knee, and
 * round again. The arrow leaves on the pose's release, with the draw it has by then, and the next
 * one is drawn straight after (6.3).</p>
 *
 * <p><b>The Legolas stance.</b> The bow fully drawn, R once: from then on the arrow leaves by itself
 * each time the draw is full, and after it the player dodges spinning round: to the side of A or
 * D, the way the movement keys point (S + A: back and to the left), almost in place with none,
 * 10 energy a dodge (6.4).</p>
 *
 * <p>Both end when the right button is let go (the last arrow shoots as usual) or the bow is put
 * away. Other players' poses follow the moves the server reports; between two moves they are held
 * while the player draws.</p>
 */
public final class BowStance {
    /** What the local player does with the drawn bow. */
    private enum Mode { NONE, FLOW, LEGOLAS }

    /**
     * A pose held between moves: down on one knee, side-on at the end of a Legolas spin, or the
     * last frame of a flow dodge for the moment until its pose starts.
     */
    private enum Hold { KNEEL, SIDE, STEP }

    /** The volley's tick by which the player is down on one knee. */
    private static final int KNELT = 10;
    /** Ticks a player may be seen without a drawn bow (between two arrows, lag) before a held pose ends. */
    private static final int GRACE = 5;
    /** The flow's poses, in turn (the volley leaves the player on the knee, so the crouch comes next). */
    private static final Move[] POSES = {Move.FLOW_CROUCH, Move.FLOW_STAND, Move.FLOW_KNEEL};

    private static Mode mode = Mode.NONE;
    /** The round's next pose; the pose to take once the dodge of this click is over. */
    private static int pose;
    private static Move nextPose;
    /** Which way the next dodge without A or D spins (they take turns). */
    private static boolean spinLeft;
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
        if (move.volley() || move == Move.FLOW_KNEEL) {
            if (bow && p.onGround() && (!move.volley() || reached >= KNELT)) {
                HOLDING.put(p, new HeldPose(Hold.KNEEL));
                MoveAnimator.playKneel(p);
                if (local && move.volley() && dev.mcreload.moves.HudConfig.Moves.FLOW.on()) startMode(Mode.FLOW);
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
        } else if (move.drawnBow() && move.shot.holdAt() >= 0) {
            // A flow dodge holds its last frame too, for the pose that follows it; let go of the
            // button during the dodge and there is no pose: the player stands up (it was left
            // frozen in the dodge's last frame).
            if (bow) HOLDING.put(p, new HeldPose(Hold.STEP));
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
        // A dodge's frame waits a moment for its pose and no longer; the stances wait while the bow is drawn.
        if (Bow.drawing(p) && h.hold != Hold.STEP) h.grace = GRACE;
        else h.grace--;
        boolean left = local && mode != (h.hold == Hold.SIDE ? Mode.LEGOLAS : Mode.FLOW);
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
        nextPose = null;
        spinLeft = true;
    }

    /** R: with the bow fully drawn it starts the Legolas stance (instead of a dodge). Returns whether R was taken. */
    static boolean dodgeKey(LocalPlayer p) {
        if (mode == Mode.LEGOLAS || !Bow.fullyDrawn(p) || !dev.mcreload.moves.HudConfig.Moves.LEGOLAS.on()) return false;
        startMode(Mode.LEGOLAS);
        return true;
    }

    /**
     * The left button: a step of the flow, a dodge by the keys held (none: a spin almost in place),
     * then the round's next pose. In the Legolas stance it does nothing. Returns whether it was taken.
     */
    static boolean attackKey(LocalPlayer p, Stamina s) {
        if (mode == Mode.LEGOLAS) return true;
        if (mode != Mode.FLOW) return false;
        if (!Bow.drawing(p) || nextPose != null || (s.move != null && s.moveTick < s.move.freeFrom())) return true;
        Minecraft mc = Minecraft.getInstance();
        boolean left = mc.options.keyLeft.isDown(), right = mc.options.keyRight.isDown();
        Move dodge;
        if (left != right) dodge = left ? Move.FLOW_ROLL_LEFT : Move.FLOW_ROLL_RIGHT;
        else if (mc.options.keyDown.isDown() && !mc.options.keyUp.isDown()) dodge = Move.FLOW_BACKSTEP;
        else dodge = spinLeft ? Move.FLOW_SPIN_LEFT : Move.FLOW_SPIN_RIGHT;
        nextPose = POSES[pose];
        pose = (pose + 1) % POSES.length;
        // Out of energy for the dodge: the pose straight away (the bar flashes).
        if (MovesClient.start(p, s, dodge, keys(mc)) && (dodge == Move.FLOW_SPIN_LEFT || dodge == Move.FLOW_SPIN_RIGHT)) spinLeft = !spinLeft;
        return true;
    }

    /** Every client tick, before the keys: the arrows, the poses after the dodges, the end of the flow and the stance. */
    static void tickLocal(LocalPlayer p, Stamina s) {
        Minecraft mc = Minecraft.getInstance();
        boolean held = mc.options.keyUse.isDown() && Bow.isBow(p.getMainHandItem());
        if (mode != Mode.NONE && !held) {
            mode = Mode.NONE;
            nextPose = null;
            // Let go before a pose's arrow left: vanilla's release has shot it, the pose stops here
            // (a dodge plays out).
            if (s.move != null && s.move.drawnBow() && s.moveTick < s.move.shot.release()) MovesClient.cancel(p, s, 4);
        }
        // The pose after the dodge (or at once, if there was no energy for the dodge).
        if (mode == Mode.FLOW && nextPose != null && s.move == null) {
            Move m = nextPose;
            nextPose = null;
            if (Bow.drawing(p)) MovesClient.start(p, s, m, null);
        }
        // The arrow of a pose leaves on its animation's release.
        if (s.move != null && s.move.drawnBow() && s.moveTick == s.move.shot.release() && Bow.drawing(p)) loose(p);
        // The Legolas stance: the arrow by itself at every full draw, then a dodge: to the side of A
        // or D, else the other way from the last one; pushed the way the keys point, so almost in
        // place with none.
        if (mode == Mode.LEGOLAS && Bow.fullyDrawn(p) && (s.move == null || s.moveTick >= s.move.freeFrom())) {
            loose(p);
            boolean left = mc.options.keyLeft.isDown(), right = mc.options.keyRight.isDown();
            boolean toLeft = left != right ? left : spinLeft;
            if (MovesClient.start(p, s, toLeft ? Move.LEGOLAS_LEFT : Move.LEGOLAS_RIGHT, keys(mc))) spinLeft = !toLeft;
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
        if (!kneeling(p)) return;
        float k = MovesConfig.kneelMoveFactor();
        in.forwardImpulse *= k;
        in.leftImpulse *= k;
        in.jumping = false;
    }

    private BowStance() {}
}
