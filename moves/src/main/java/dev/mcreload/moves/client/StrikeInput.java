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
 *   down a moment, as when holding it to attack) starts the finishing stab.</li>
 *   <li><b>Dash strike.</b> Sprinting with W, a sword in hand: the left button lunges; then 30 s
 *   before the next one (until then the button strikes as usual, and the hotbar says how long).</li>
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
        // A strike going on: the button strikes nothing else until the blade is back.
        if (s.move != null && s.move.strike() && s.moveTick < s.move.recoverFrom()) {
            finisherTarget = null;
            swallow(mc);
            return true;
        }
        finisherTarget = s.move == null && dev.mcreload.moves.HudConfig.Moves.FINISHER.on() && MoveServer.allowed(p, s, Move.FINISHER)
                ? Strikes.finisherTarget(p, looked(mc), true) : null;
        if (finisherTarget != null && attackDown && (attackPressed || held >= HOLD_TICKS)) {
            if (MovesClient.start(p, s, Move.FINISHER, finisherTarget.getId())) {
                swallow(mc);
                return true;
            }
        }
        if (attackPressed && s.move == null && dev.mcreload.moves.HudConfig.Moves.DASH.on() && p.isSprinting()
                && mc.options.keyUp.isDown() && Strikes.sword(p)) {
            if (s.dashCooldown > 0) {
                // Not yet: an ordinary blow, and a word above the hotbar of how long the dash still needs.
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("kinetrix_moves.dash.cooldown",
                        (s.dashCooldown + 19) / 20), true);
                return false;
            }
            if (MovesClient.start(p, s, Move.DASH, -1)) {
                swallow(mc);
                return true;
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
