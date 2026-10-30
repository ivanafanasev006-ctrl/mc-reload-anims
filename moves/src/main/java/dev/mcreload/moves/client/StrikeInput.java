package dev.mcreload.moves.client;

import dev.mcreload.moves.HudConfig;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.MoveServer;
import dev.mcreload.moves.Stamina;
import dev.mcreload.moves.Strikes;
import dev.mcreload.moves.Whirl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

/**
 * The strikes on the player's side (author, 2026-10-28, 2026-10-30).
 *
 * <ul>
 *   <li><b>Finisher.</b> A mob with a fifth of its health or less at point blank, a sword or an axe in hand: a
 *   hint shows under the crosshair; the left button held (pressed while the hint shows, or kept
 *   down a moment, as when holding it to attack) starts the finishing stab, or with an axe the chop
 *   from above (author, 2026-10-30).</li>
 *   <li><b>Dash strike.</b> A sword in hand, the left and the right button held together (author,
 *   2026-10-30; whichever goes down second starts it): a lunge forward; then 30 s before the next one.</li>
 *   <li><b>The ability, G</b> (rebindable; author, 2026-10-30): with a sword the sword's quick turn
 *   on the spot (Spin), 20 s before the next one; with an axe the whirlwind, three turns carried
 *   forward (Whirl), G again stops it, 45 s before the next one. The tray at the bottom shows how
 *   long each still needs (MovesHud).</li>
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
    /** The buttons were down last tick (as they are under the hand). */
    private static boolean attackWasDown, useWasDown;

    static LivingEntity finisherTarget() {
        return finisherTarget;
    }

    /** No world, a screen open or a server without the mod: no hint, nothing held. */
    static void reset() {
        finisherTarget = null;
        held = 0;
    }

    /** The move G makes with what is in hand (null: none, or switched off in the settings). */
    public static Move ability(LocalPlayer p) {
        if (Strikes.sword(p)) return HudConfig.Moves.SWORD_SPIN.on() ? Move.SWORD_SPIN : null;
        if (Strikes.axe(p)) return HudConfig.Moves.WHIRL.on() ? Move.WHIRL : null;
        return null;
    }

    /** Ticks left before the move may be made again (0: ready). */
    public static int cooldown(Stamina s, Move move) {
        return switch (move) {
            case DASH -> s.dashCooldown;
            case WHIRL -> s.whirlCooldown;
            case SWORD_SPIN -> s.spinCooldown;
            case SLIDE_ATTACK -> s.slideAttackCooldown;
            default -> 0;
        };
    }

    /**
     * Every client tick, before the bow and the dodges. Returns whether the left button was taken
     * (a strike started, or one is still going and holds the button). {@code abilityPressed}: G.
     */
    static boolean tick(Minecraft mc, LocalPlayer p, Stamina s, boolean attackDown, boolean attackPressed, boolean abilityPressed) {
        held = attackDown ? held + 1 : 0;
        boolean lmb = MovesClient.held(mc, mc.options.keyAttack), rmb = MovesClient.held(mc, mc.options.keyUse);
        boolean lmbPressed = lmb && !attackWasDown, rmbPressed = rmb && !useWasDown;
        attackWasDown = lmb;
        useWasDown = rmb;
        // The whirlwind and the sword's turn: the buttons swing and use nothing meanwhile; G again stops the whirlwind.
        if (s.move == Move.WHIRL || s.move == Move.SWORD_SPIN) {
            finisherTarget = null;
            swallow(mc);
            LedgeInput.swallowUse(mc);
            if (abilityPressed && s.move == Move.WHIRL && s.moveTick < Whirl.SPIN_TO) MovesClient.cancel(p, s, 4);
            return true;
        }
        // A strike going on: the button strikes nothing else until the blade is back.
        if (s.move != null && s.move.strike() && s.moveTick < s.move.recoverFrom()) {
            finisherTarget = null;
            swallow(mc);
            return true;
        }
        // G: the sword's turn with a sword, the whirlwind with an axe
        if (abilityPressed && s.move == null) {
            Move move = ability(p);
            if (move != null && begin(mc, p, s, move)) return true;
        }
        // the finisher: the stab with a sword, the chop with an axe (author, 2026-10-30); not with the
        // right button down too (that is the dash)
        Move finisher = Strikes.axe(p) ? Move.FINISHER_AXE : Move.FINISHER;
        finisherTarget = s.move == null && !rmb && HudConfig.Moves.FINISHER.on() && MoveServer.allowed(p, s, finisher)
                ? Strikes.finisherTarget(p, looked(mc), true) : null;
        if (finisherTarget != null && attackDown && (attackPressed || held >= HOLD_TICKS)) {
            if (MovesClient.start(p, s, finisher, finisherTarget.getId())) {
                swallow(mc);
                return true;
            }
        }
        // the left and the right button held together with a sword: the dash
        if (lmb && rmb && (lmbPressed || rmbPressed) && s.move == null && Strikes.sword(p) && HudConfig.Moves.DASH.on()) {
            // the right button is the dash's either way: no sword block
            LedgeInput.swallowUse(mc);
            GuardInput.lowerLocal(p, s, true);
            return begin(mc, p, s, Move.DASH);
        }
        return false;
    }

    /** Starts a strike that waits out a cooldown, or says above the hotbar how long it still needs. */
    private static boolean begin(Minecraft mc, LocalPlayer p, Stamina s, Move move) {
        int cooldown = cooldown(s, move);
        if (cooldown > 0) {
            MovesHud.refused(move);
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("kinetrix_moves.cooldown",
                    net.minecraft.network.chat.Component.translatable("kinetrix_moves.settings.move." + key(move)), (cooldown + 19) / 20), true);
            return false;
        }
        if (!MovesClient.start(p, s, move, -1)) return false;
        swallow(mc);
        if (move != Move.DASH) LedgeInput.swallowUse(mc);
        return true;
    }

    /** The move's name key in the settings (kinetrix_moves.settings.move.<key>). */
    static String key(Move move) {
        return switch (move) {
            case DASH -> HudConfig.Moves.DASH.key;
            case WHIRL -> HudConfig.Moves.WHIRL.key;
            case SWORD_SPIN -> HudConfig.Moves.SWORD_SPIN.key;
            case SLIDE_ATTACK -> HudConfig.Moves.SLIDE_ATTACK.key;
            default -> move.name().toLowerCase(java.util.Locale.ROOT);
        };
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
