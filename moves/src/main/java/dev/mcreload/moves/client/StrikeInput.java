package dev.mcreload.moves.client;

import dev.mcreload.moves.Crossbow;
import dev.mcreload.moves.HudConfig;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.MoveServer;
import dev.mcreload.moves.Stamina;
import dev.mcreload.moves.Strikes;
import dev.mcreload.moves.Spin;
import dev.mcreload.moves.Whirl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

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
 *   (Spin), the keys walking the player meanwhile; held, up to 3 turns; 20 s before the next one.
 *   With an axe the whirlwind, three turns carried forward the way the camera looks (Whirl); held,
 *   up to 5; 45 s before the next one. Each turn more than those costs more energy. With a bow the
 *   volley and the hail (BowVolley). The tray at the bottom shows how long each still needs (MovesHud).</li>
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
    /** The mob a pull is taking the weapon out of (for the finisher right after it), -1: none. */
    private static int pulledFrom = -1;
    /** G held this long with a sword makes its turns; let go before, the lunge. */
    private static final int HOLD_G = 4;
    /** Ticks G has been down with an axe (tapped or held). */
    private static int axeG;
    /** Ticks G has been down since it was pressed with a sword (0: not waiting). */
    private static int swordG;
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
        // the sword's: tapped the lunge (the dash), held its turns (author, after 0.9.1); the tray shows the turns apart
        if (Strikes.sword(p)) return HudConfig.Moves.DASH.on() ? Move.DASH : HudConfig.Moves.SWORD_SPIN.on() ? Move.SWORD_SPIN : null;
        if (Strikes.axe(p)) return HudConfig.Moves.WHIRL.on() ? Move.WHIRL : null;
        // the bow's: the volley, held the hail (BowVolley makes them)
        if (dev.mcreload.moves.Bow.isBow(p.getMainHandItem())) return HudConfig.Moves.VOLLEY.on() ? Move.BARRAGE : null;
        // the crossbow's: the hop with three arrows or the turn, held the fan (CrossbowInput makes them)
        if (Crossbow.held(p)) return HudConfig.Moves.CROSSBOW_G.on() ? Move.XBOW_VOLLEY : null;
        return null;
    }

    /** Ticks left before the move may be made again (0: ready). */
    public static int cooldown(Stamina s, Move move) {
        return switch (move) {
            case DASH -> s.dashCooldown;
            case WHIRL -> s.whirlCooldown;
            case SWORD_SPIN -> s.spinCooldown;
            case SLIDE_ATTACK -> s.slideAttackCooldown;
            // the volley at the marked, in the tray by its first step
            case MARKED_SPIN_LEFT -> s.markedCooldown;
            case BARRAGE -> s.bowCooldown;
            case SLIDE_SHOT -> s.slideShotCooldown;
            // the Legolas stance, in the tray by its dodge
            case LEGOLAS_LEFT -> s.legolasCooldown;
            // the crossbow's G (one wait for the hop, the turn and the fan), its rolls (in the tray by the back roll)
            case XBOW_VOLLEY, XBOW_SPIN, XBOW_FAN -> s.xbowCooldown;
            case XBOW_ROLL_BACK -> s.xbowRollsCooldown;
            case CHAIN_LEAP -> s.chainCooldown;
            case THROW -> s.throwCooldown;
            case AIR_SPIN -> s.airSpinCooldown;
            case AXE_TRIPLE -> s.axeTripleCooldown;
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
        // G's turns: the buttons swing and use nothing meanwhile. G held, another turn (up to 3 with a sword,
        // 5 with an axe, each for more energy); let go (or no energy for it), the ending in its place, a tick
        // before the turn would end (author, 2026-10-30, after the video).
        if (s.move != null && s.move.turning()) {
            finisherTarget = null;
            swallow(mc);
            LedgeInput.swallowUse(mc);
            boolean sword = s.move == Move.SWORD_SPIN;
            if ((sword && Spin.decides(s.moveTick)) || (s.move == Move.WHIRL && Whirl.decides(s.moveTick))) {
                float extra = sword ? dev.mcreload.moves.MovesConfig.costSpinExtra() : dev.mcreload.moves.MovesConfig.costWhirlExtra();
                // the sword's turns while G is held; the axe's whirl its 4 (author, 2026-11-10: "the old G+ on G, 4 turns")
                boolean more = (sword ? MovesClient.held(mc, MovesClient.ABILITY) : Whirl.turn(s.moveTick) < Whirl.G_TURNS - 1)
                        && (s.free() || p.getAbilities().instabuild || s.energy >= extra);
                if (more) s.spend(extra, p);    // as the server does (a tick later)
                else MovesClient.start(p, s, sword ? Move.SPIN_END : Move.WHIRL_END, -1);
            }
            return true;
        }
        // The chain finisher (author, after 0.9.1): Ctrl + W + Space + LMB with a sword
        if (lmbPressed && ChainInput.start(mc, p, s, mc.options.keyJump.isDown() || MovesClient.sinceJump() <= 6)) {
            finisherTarget = null;
            swallow(mc);
            return true;
        }
        // The throw (author, after 0.9.1): a sword or an axe, the left and the right button held together, the weapon drawn
        // back; the left let go, it is thrown; the right let go, the aim is put down
        if (s.move == Move.THROW_AIM) {
            finisherTarget = null;
            swallow(mc);
            LedgeInput.swallowUse(mc);
            // the auto-aim (author, 2026-11-09: "as the bow's, a little red square on the mob: the crosshair a little off, it
            // still flies at it"): the mob nearest the look, its square drawn (MarkRender)
            LivingEntity aimed = dev.mcreload.moves.Thrown.aim(p, dev.mcreload.moves.Thrown.AIM_CONE);
            ThrownClient.aimed = aimed == null ? -1 : aimed.getId();
            // either button let go: thrown (the left only threw: both let go at once, or the right first, put the aim down,
            // "held long and it would not throw"); a moment's aim, or a throw that cannot be (no energy), put down
            if (!lmb || !rmb) {
                boolean thrown = s.moveTick >= AIM_MIN && MovesClient.start(p, s, Move.THROW, ThrownClient.aimed);
                if (!thrown) MovesClient.cancel(p, s, 4);
                ThrownClient.aimed = -1;
            }
            return true;
        }
        ThrownClient.aimed = -1;
        if (!lmb || !rmb) aimRefused = false;
        if (lmb && rmb && s.move == null && !aimRefused && dev.mcreload.moves.Thrown.throwable(p) && HudConfig.Moves.THROW.on()
                && MoveServer.allowed(p, s, Move.THROW_AIM)) {
            GuardInput.lowerLocal(p, s, true);
            LedgeInput.swallowUse(mc);
            if (begin(mc, p, s, Move.THROW_AIM)) return true;
        }
        // The pull: the left or the right button at point blank to a mob with the player's weapon in it; a finisher right
        // after it if the mob is weak (the author: "he pulls it out, then drives it in under the ribs ... / chops from above")
        if ((lmbPressed || rmbPressed) && s.move == null && HudConfig.Moves.THROW.on()) {
            int weapon = ThrownClient.pullable(p, looked(mc));
            if (weapon >= 0 && MovesClient.start(p, s, Move.WEAPON_PULL, weapon)) {
                pulledFrom = ThrownClient.stuckIn(weapon);
                swallow(mc);
                LedgeInput.swallowUse(mc);
                return true;
            }
        }
        if (s.move == Move.WEAPON_PULL && s.moveTick >= dev.mcreload.moves.Thrown.PULL_TICK + 2 && pulledFrom >= 0) {
            if (p.level().getEntity(pulledFrom) instanceof LivingEntity mob && Strikes.finisherWeapon(p) && HudConfig.Moves.FINISHER.on()
                    && Strikes.finishable(p, mob, false)) {
                Move fin = Strikes.axe(p) ? Move.FINISHER_AXE : Move.FINISHER;
                if (MovesClient.start(p, s, fin, mob.getId())) pulledFrom = -1;
            }
            if (s.moveTick >= dev.mcreload.moves.Thrown.PULL_TICK + 9) pulledFrom = -1;     // the weapon reaches the hand a tick or two after the pull
        }
        // The crossbow's finisher before its chain (author, after 0.9.1): a click at a weak mob at point blank, an arrow in hand
        if (lmbPressed && s.move == null && !rmb && Crossbow.held(p) && HudConfig.Moves.FINISHER.on() && MoveServer.allowed(p, s, Move.FINISHER_XBOW)) {
            LivingEntity mob = Strikes.finisherTarget(p, looked(mc), true);
            if (mob != null && MovesClient.start(p, s, Move.FINISHER_XBOW, mob.getId())) {
                finisherTarget = null;
                swallow(mc);
                return true;
            }
        }
        // The crossbow's chain (author, 2026-11-08): each click the next blow
        if (crossbow(mc, p, s, lmbPressed)) {
            finisherTarget = null;
            return true;
        }
        // A strike going on: the button strikes nothing else until the blade is back.
        if (s.move != null && s.move.strike() && s.moveTick < s.move.recoverFrom()) {
            finisherTarget = null;
            swallow(mc);
            return true;
        }
        // G with a sword (author, after 0.9.1: "the sword's lunge on G, and on G+ the 3 turns"): tapped, the lunge; still
        // down after HOLD_G ticks, the turns (they go on while it is held, up to 3)
        if (!Strikes.sword(p)) swordG = 0;
        if (abilityPressed && s.move == null && swordG == 0 && Strikes.sword(p)) swordG = 1;
        if (swordG > 0) {
            swordG++;
            boolean down = MovesClient.held(mc, MovesClient.ABILITY);
            if (!down || swordG > HOLD_G) {
                swordG = 0;
                Move move = down ? Move.SWORD_SPIN : Move.DASH;
                boolean on = down ? HudConfig.Moves.SWORD_SPIN.on() : HudConfig.Moves.DASH.on();
                // W and Space with it: the turns in the air (author, 2026-11-10)
                if (down && HudConfig.Moves.AIR_SPIN.on() && mc.options.keyUp.isDown()
                        && (mc.options.keyJump.isDown() || MovesClient.sinceJump() <= 8) && MoveServer.allowed(p, s, Move.AIR_SPIN)) {
                    move = Move.AIR_SPIN;
                }
                if (on && s.move == null && begin(mc, p, s, move)) {
                    if (move == Move.DASH) GuardInput.lowerLocal(p, s, true);
                    return true;
                }
            }
        }
        // G with an axe: tapped the whirlwind (4 turns), held the combo with 3 to 6 mobs about (else the whirlwind)
        if (!Strikes.axe(p)) axeG = 0;
        if (abilityPressed && s.move == null && axeG == 0 && Strikes.axe(p)) axeG = 1;
        if (axeG > 0) {
            axeG++;
            boolean down = MovesClient.held(mc, MovesClient.ABILITY);
            if (!down || axeG > HOLD_G) {
                axeG = 0;
                Move move = down && HudConfig.Moves.AXE_TRIPLE.on() && MoveServer.allowed(p, s, Move.AXE_TRIPLE) ? Move.AXE_TRIPLE : ability(p);
                if (move != null && s.move == null && begin(mc, p, s, move)) return true;
            }
        }
        // G: the others' abilities (the bow's and the crossbow's make their own)
        if (abilityPressed && s.move == null && !Strikes.sword(p) && !Strikes.axe(p)) {
            Move move = ability(p);
            if (move != null && !move.volley() && !move.crossbowSkill() && begin(mc, p, s, move)) return true;
        }
        // the finisher: the stab with a sword, the chop with an axe (author, 2026-10-30); not with the
        // right button down too (that is the dash)
        Move finisher = Strikes.finisherOf(p);
        finisherTarget = s.move == null && !rmb && HudConfig.Moves.FINISHER.on() && MoveServer.allowed(p, s, finisher)
                ? Strikes.finisherTarget(p, looked(mc), true) : null;
        if (finisherTarget != null && attackDown && (attackPressed || held >= HOLD_TICKS)) {
            if (MovesClient.start(p, s, finisher, finisherTarget.getId())) {
                swallow(mc);
                return true;
            }
        }
        return false;
    }

    /** A click kept for the crossbow's next blow: made before the blow going on had followed through. */
    private static boolean xbowClick;

    /**
     * The crossbow's chain on the left button (Crossbow): a click is the next blow, the push when the chain has
     * lapsed; one made before the blow going on has followed through is kept and starts the next blow from that
     * blow's chain tick (a quick double click is not lost). While a blow plays the button is the chain's. Out of
     * the chain, a click on a block digs it as ever. Returns whether the button was taken.
     */
    private static boolean crossbow(Minecraft mc, LocalPlayer p, Stamina s, boolean pressed) {
        boolean chain = s.move != null && s.move.crossbow();
        if (!Crossbow.held(p) || !HudConfig.Moves.CROSSBOW.on() || (s.move != null && !chain)) {
            xbowClick = false;
            return false;
        }
        boolean block = mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
        if (pressed && !chain && block && s.chainTicks <= 0) return false;
        if (pressed) xbowClick = true;
        if (xbowClick) {
            Move next = Crossbow.next(p, s);
            if (MoveServer.allowed(p, s, next)) {
                xbowClick = false;
                if (!MovesClient.start(p, s, next, -1)) return false;
                swallow(mc);
                return true;
            }
            // not yet (the blow going on has not followed through, the last one is still up): kept while the chain
            // plays; out of it (in the air, no energy) the click is vanilla's
            if (!chain) {
                xbowClick = false;
                return false;
            }
        }
        if (!chain) return false;
        swallow(mc);
        return true;
    }

    /** Starts a strike that waits out a cooldown, or says above the hotbar how long it still needs. */
    private static boolean begin(Minecraft mc, LocalPlayer p, Stamina s, Move move) {
        if (waits(p, s, move)) return false;
        if (!MovesClient.start(p, s, move, -1)) return false;
        swallow(mc);
        if (move != Move.DASH) LedgeInput.swallowUse(mc);
        return true;
    }

    /** The strike still waits out its cooldown: its bar in the tray flashes and how long it needs shows above the hotbar. */
    static boolean waits(LocalPlayer p, Stamina s, Move move) {
        int cooldown = cooldown(s, move);
        if (cooldown <= 0) return false;
        MovesHud.refused(move);
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("kinetrix_moves.cooldown",
                net.minecraft.network.chat.Component.translatable("kinetrix_moves.settings.move." + key(move)), (cooldown + 19) / 20), true);
        return true;
    }

    /** The move's name key in the settings (kinetrix_moves.settings.move.<key>). */
    static String key(Move move) {
        return switch (move) {
            case DASH -> HudConfig.Moves.DASH.key;
            case WHIRL -> HudConfig.Moves.WHIRL.key;
            case SWORD_SPIN -> HudConfig.Moves.SWORD_SPIN.key;
            case SLIDE_ATTACK -> HudConfig.Moves.SLIDE_ATTACK.key;
            case MARKED_SPIN_LEFT -> HudConfig.Moves.MARKS.key;
            case BARRAGE -> HudConfig.Moves.VOLLEY.key;
            case SLIDE_SHOT -> HudConfig.Moves.SLIDE_SHOT.key;
            case LEGOLAS_LEFT -> HudConfig.Moves.LEGOLAS.key;
            case XBOW_THRUST, XBOW_SLAP, XBOW_SPINE, XBOW_SHOT, XBOW_DASH -> HudConfig.Moves.CROSSBOW.key;
            case XBOW_VOLLEY, XBOW_SPIN, XBOW_FAN -> HudConfig.Moves.CROSSBOW_G.key;
            case XBOW_ROLL_BACK -> HudConfig.Moves.CROSSBOW_ROLLS.key;
            case CHAIN_LEAP -> HudConfig.Moves.CHAIN.key;
            case THROW -> HudConfig.Moves.THROW.key;
            case AIR_SPIN -> HudConfig.Moves.AIR_SPIN.key;
            case AXE_TRIPLE -> HudConfig.Moves.AXE_TRIPLE.key;
            default -> move.name().toLowerCase(java.util.Locale.ROOT);
        };
    }

    /** The entity under the crosshair, if any. */
    static Entity looked(Minecraft mc) {
        return mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : mc.crosshairPickEntity;
    }

    /** Lets go of the left button: its clicks this tick are used up and it reads as up until pressed again. */
    /** An aim shorter than this (ticks) let go is put down, not thrown (a slip of the fingers). */
    private static final int AIM_MIN = 3;

    /** The server said no to the aim: not drawn back again until the buttons are let go (it was, over and over). */
    static boolean aimRefused;

    static void swallow(Minecraft mc) {
        while (mc.options.keyAttack.consumeClick()) {
            // used up
        }
        mc.options.keyAttack.setDown(false);
    }

    private StrikeInput() {}
}
