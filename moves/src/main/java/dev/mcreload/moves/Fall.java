package dev.mcreload.moves;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The tuck and the roll out of a fall (author, 2026-10-29): R held while falling, the player tucks
 * (fall_tuck), and landing rolls forward, stays on one knee in a half crouch a moment (stunned) and
 * stands up (fall_roll). The fall then hurts 70 % less.
 */
public final class Fall {
    /** The player may get up (moving breaks off the rest) from 1.5 s: the stun is over. */
    public static final int STAND = 30;
    /** The roll carries the player forward from 0.08 s to 0.5 s (Paths "fall_roll"). */
    public static final int ROLL_FROM = 2, ROLL_TO = 10;
    /** Falling this far (blocks) or more, R tucks: a jump or a step down does not. */
    public static final float TUCK_FROM = 2.5f;

    /** The tuck may start: falling, far enough to hurt. */
    public static boolean canTuck(net.minecraft.world.entity.player.Player p) {
        return !p.onGround() && p.fallDistance >= TUCK_FROM && !p.isInWater() && !p.onClimbable() && !p.isFallFlying();
    }

    /** Landing tucked, or rolling out of it (the client may start the roll a tick before this end sees the landing). */
    @SubscribeEvent
    public static void fall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Stamina s = Stamina.of(p);
        boolean tucked = s.move == Move.FALL_TUCK || (s.move == Move.FALL_ROLL && s.moveTick <= 4);
        if (!tucked) return;
        event.setDamageMultiplier(event.getDamageMultiplier() * (1f - MovesConfig.fallRollCut()));
    }

    /** Every server tick: a tuck that reaches the ground without the roll (the client never asked) ends there. */
    static void serverTick(ServerPlayer p, Stamina s) {
        if (s.move == Move.FALL_TUCK && (p.onGround() || p.isInWater()) && s.moveTick > 2) {
            s.tuckLanded++;
            if (s.tuckLanded > 10) {
                s.end(p);
                dev.mcreload.moves.net.MovesNet.stop(p);
            }
        } else {
            s.tuckLanded = 0;
        }
    }

    private Fall() {}
}
