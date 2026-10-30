package dev.mcreload.moves.client;

import dev.mcreload.moves.Move;
import dev.mcreload.moves.MoveServer;
import dev.mcreload.moves.Stamina;
import dev.mcreload.moves.Strikes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

/**
 * The sword moves of the left button on the player's side (author, 2026-10-28).
 *
 * <ul>
 *   <li><b>Finisher.</b> A mob with a fifth of its health or less at point blank, a sword or an axe in hand: a
 *   hint shows under the crosshair; the left button held (pressed while the hint shows, or kept
 *   down a moment, as when holding it to attack) starts the finishing stab, or with an axe the chop
 *   from above (author, 2026-10-30).</li>
 *   <li><b>Dash strike.</b> A sword in hand, S and the left button held, the right button once
 *   (author, 2026-10-29, fifth message): a lunge forward; then 30 s before the next one (the
 *   hotbar says how long).</li>
 *   <li><b>Axe whirlwind.</b> An axe in hand, S and the left button held, then the right button
 *   held: three turns round with the axe out (Whirl); it goes on by itself while both buttons are
 *   held, letting go of either stops it. 45 s before the next one.</li>
 * </ul>
 *
 * <p>They take the click: the button is let go of for vanilla and for Better Combat (whose
 * hold-to-attack reads it every tick), so nothing else swings for it until it is pressed again.</p>
 */
public final class StrikeInput {
    /** Ticks the button must be held on a mob that has just become weak enough (holding it to attack). */
    private static final int HOLD_TICKS = 6;

    /** The mob the finisher would be made on now (for the hint), or null. */
    private static LivingEntity finisherTarget;
    /** Ticks the left button has been down. */
    private static int held;
    /** The right button was down last tick (as it is under the hand). */
    private static boolean useWasDown;

    static LivingEntity finisherTarget() {
        return finisherTarget;
    }

    /** No world, a screen open or a server without the mod: no hint, nothing held. */
    static void reset() {
        finisherTarget = null;
        held = 0;
    }

    /**
     * Every client tick, before the bow and the dodges. Returns whether the left button was taken
     * (a strike started, or one is still going and holds the button).
     */
    static boolean tick(Minecraft mc, LocalPlayer p, Stamina s, boolean attackDown, boolean attackPressed) {
        held = attackDown ? held + 1 : 0;
        boolean lmb = MovesClient.held(mc, mc.options.keyAttack), rmb = MovesClient.held(mc, mc.options.keyUse);
        boolean rmbPressed = rmb && !useWasDown;
        useWasDown = rmb;
        // The whirlwind goes on while both buttons are held (author: letting go of either stops it);
        // they swing nothing and use nothing meanwhile.
        if (s.move == Move.WHIRL) {
            finisherTarget = null;
            swallow(mc);
            LedgeInput.swallowUse(mc);
            if ((!lmb || !rmb) && s.moveTick < dev.mcreload.moves.Whirl.SPIN_TO) MovesClient.cancel(p, s, 4);
            return true;
        }
        // A strike going on: the button strikes nothing else until the blade is back.
        if (s.move != null && s.move.strike() && s.moveTick < s.move.recoverFrom()) {
            finisherTarget = null;
            swallow(mc);
            return true;
        }
        // the finisher: the stab with a sword, the chop with an axe (author, 2026-10-30)
        Move finisher = Strikes.axe(p) ? Move.FINISHER_AXE : Move.FINISHER;
        finisherTarget = s.move == null && dev.mcreload.moves.HudConfig.Moves.FINISHER.on() && MoveServer.allowed(p, s, finisher)
                ? Strikes.finisherTarget(p, looked(mc), true) : null;
        if (finisherTarget != null && attackDown && (attackPressed || held >= HOLD_TICKS)) {
            if (MovesClient.start(p, s, finisher, finisherTarget.getId())) {
                swallow(mc);
                return true;
            }
        }
        // S and the left button held, the right one: the dash with a sword, the whirlwind with an axe
        if (rmbPressed && s.move == null && lmb && mc.options.keyDown.isDown()) {
            Move move = Strikes.sword(p) && dev.mcreload.moves.HudConfig.Moves.DASH.on() ? Move.DASH
                    : Strikes.axe(p) && dev.mcreload.moves.HudConfig.Moves.WHIRL.on() ? Move.WHIRL : null;
            if (move != null) {
                // the right button is theirs either way: no sword block, no log stripped
                LedgeInput.swallowUse(mc);
                int cooldown = move == Move.DASH ? s.dashCooldown : s.whirlCooldown;
                if (cooldown > 0) {
                    // Not yet: a word above the hotbar of how long it still needs.
                    p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            move == Move.DASH ? "kinetrix_moves.dash.cooldown" : "kinetrix_moves.whirl.cooldown", (cooldown + 19) / 20), true);
                    return false;
                }
                if (MovesClient.start(p, s, move, -1)) {
                    swallow(mc);
                    return true;
                }
            }
        }
        return false;
    }

    /** The entity under the crosshair, if any. */
    private static Entity looked(Minecraft mc) {
        return mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : mc.crosshairPickEntity;
    }

    /** Lets go of the left button: its clicks this tick are used up and it reads as up until pressed again. */
    static void swallow(Minecraft mc) {
        while (mc.options.keyAttack.consumeClick()) {
            // used up
        }
        mc.options.keyAttack.setDown(false);
    }

    private StrikeInput() {}
}
