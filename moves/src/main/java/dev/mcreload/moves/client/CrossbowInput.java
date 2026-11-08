package dev.mcreload.moves.client;

import dev.mcreload.moves.Crossbow;
import dev.mcreload.moves.HudConfig;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.MovesConfig;
import dev.mcreload.moves.Stamina;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The crossbow on the player's side, the second half of the author's message of 2026-11-08 (Crossbow; the chain on
 * the left button is StrikeInput's).
 *
 * <ul>
 *   <li><b>G.</b> Tapped: loaded, the hop back with three arrows spread; not loaded, the turn with the crossbow out and
 *   the shove. Held (still down after HOLD ticks): the fan of arrows loads, an arrow every half a second (shown above
 *   the hotbar), eight at most and no more than there are arrows; let go, they all leave at once.</li>
 *   <li><b>The rolls.</b> The right button held, R: a roll the way the movement keys point (back with none), an arrow
 *   out of it, and the next roll, on and on while the right button is held; let go (or the crossbow put away), they
 *   end and their wait starts. Meanwhile the crossbow is not loaded by the held button.</li>
 * </ul>
 */
public final class CrossbowInput {
    /** G held this many ticks loads the fan; let go before, G tapped. */
    private static final int HOLD = 4;

    /** Ticks G has been down since it was pressed with a crossbow (0: not waiting). */
    private static int gDown;
    /** The rolls are on; one of them has been made (its end starts the wait, as the server counts it). */
    private static boolean rolling, rolled;
    /** The fan's arrows last shown above the hotbar. */
    private static int fanShown;
    /** When the rolls last ended here (the player's tick count): the server starts their wait a little later. */
    private static int endedAt = -1000;

    static void reset() {
        gDown = 0;
        rolling = false;
        rolled = false;
        fanShown = 0;
    }

    /** The rolls are on: the right button neither loads the crossbow nor uses anything. */
    static boolean rolling() {
        return rolling;
    }

    /** Every client tick, after the bow's. */
    static void tickLocal(Minecraft mc, LocalPlayer p, Stamina s, boolean abilityPressed) {
        if (!Crossbow.held(p)) {
            // put away: the fan's loading and the rolls end (the server ends them too)
            if (s.move == Move.XBOW_FAN) MovesClient.cancel(p, s, 4);
            gDown = 0;
            endRolls(s);
            return;
        }
        // ---- G: tapped, the hop with three arrows (loaded) or the turn with the shove; held, the fan
        if (abilityPressed && s.move == null && gDown == 0 && !rolling && HudConfig.Moves.CROSSBOW_G.on()
                && !StrikeInput.waits(p, s, Move.XBOW_VOLLEY)) gDown = 1;
        if (gDown > 0) {
            gDown++;
            boolean held = MovesClient.held(mc, MovesClient.ABILITY);
            if (!held || gDown > HOLD) {
                gDown = 0;
                Move move = held ? Move.XBOW_FAN : Crossbow.loaded(p) ? Move.XBOW_VOLLEY : Move.XBOW_SPIN;
                if (move == Move.XBOW_FAN && Crossbow.arrows(p) <= 0) {
                    p.displayClientMessage(Component.translatable("kinetrix_moves.crossbow.no_arrows"), true);
                } else if (s.move == null && MovesClient.start(p, s, move, -1)) {
                    if (p.isUsingItem()) p.stopUsingItem();     // G takes the crossbow over from the loading
                    StrikeInput.swallow(mc);
                    LedgeInput.swallowUse(mc);
                    fanShown = 0;
                }
            }
        }
        // ---- the fan: an arrow every FAN_EVERY ticks while G is held; let go, the shot with all of them
        if (s.move == Move.XBOW_FAN) {
            int n = Math.min(Crossbow.fanLoaded(s.moveTick), Math.max(1, Crossbow.arrows(p)));
            if (n != fanShown) {
                fanShown = n;
                p.playSound(SoundEvents.CROSSBOW_LOADING_MIDDLE, 0.6f, 0.9f + 0.05f * n);
                p.displayClientMessage(Component.translatable("kinetrix_moves.crossbow.fan", n, MovesConfig.crossbowFanMax()), true);
            }
            if (!MovesClient.held(mc, MovesClient.ABILITY)) MovesClient.start(p, s, Move.XBOW_FAN_SHOT, n);
        }
        // ---- the rolls: on while the right button is held, the next once the arrow of this one is out
        if (rolling) {
            if (!MovesClient.held(mc, mc.options.keyUse)) {
                endRolls(s);
            } else {
                LedgeInput.swallowUse(mc);
                if (p.isUsingItem()) p.stopUsingItem();
                if (s.move == null || (s.move.crossbowRoll() && s.moveTick >= s.move.freeFrom())) roll(mc, p, s);
            }
        }
    }

    /**
     * R: with the right button held and a crossbow in hand the rolls start (instead of a dodge), once their wait is
     * over. Returns whether R was taken.
     */
    static boolean dodgeKey(Minecraft mc, LocalPlayer p, Stamina s) {
        if (rolling || !Crossbow.held(p) || !HudConfig.Moves.CROSSBOW_ROLLS.on() || !MovesClient.held(mc, mc.options.keyUse)) return false;
        if (s.move != null && !s.move.crossbowRoll() && s.moveTick < s.move.freeFrom()) return false;
        if (StrikeInput.waits(p, s, Move.XBOW_ROLL_BACK)) return true;
        rolling = true;
        rolled = false;
        // the loading the button started is let go (not loaded: too short; else vanilla loads it, as ever)
        if (p.isUsingItem() && mc.gameMode != null) mc.gameMode.releaseUsingItem(p);
        LedgeInput.swallowUse(mc);
        roll(mc, p, s);
        return true;
    }

    /** The next roll, the way the keys point: A or D to that side, S back, W forward, none back. */
    private static void roll(Minecraft mc, LocalPlayer p, Stamina s) {
        boolean left = mc.options.keyLeft.isDown(), right = mc.options.keyRight.isDown();
        Move move = left != right ? (left ? Move.XBOW_ROLL_LEFT : Move.XBOW_ROLL_RIGHT)
                : mc.options.keyUp.isDown() && !mc.options.keyDown.isDown() ? Move.XBOW_ROLL_FRONT : Move.XBOW_ROLL_BACK;
        if (MovesClient.start(p, s, move, -1)) rolled = true;
    }

    private static void endRolls(Stamina s) {
        if (!rolling) return;
        rolling = false;
        // over: a new run of rolls waits (the server starts the same wait, Crossbow.rollsTick)
        if (rolled) {
            s.xbowRollsCooldown = s.cooled(MovesConfig.crossbowRollsCooldownTicks());
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) endedAt = mc.player.tickCount;
        }
        rolled = false;
    }

    /**
     * The rolls' wait as the server's bars say it, kept no shorter than this end's own for a while after the rolls
     * ended here: the server ends them once the last roll has played out and a little more (it does not hear the right
     * button), and its bars meanwhile still say there is no wait.
     */
    static int rollsWait(LocalPlayer p, int mine, int server) {
        return p.tickCount - endedAt < 60 ? Math.max(mine, server) : server;
    }

    private CrossbowInput() {}
}
