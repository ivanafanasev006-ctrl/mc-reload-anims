package dev.mcreload.moves;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The chain finisher (author, after 0.9.1: "several weak enemies about 5 blocks from each other: the sword in hand, Ctrl +
 * W + Space + LMB: he leaps onto the shoulders of one from behind (under 50 % health) and drives the sword into its head,
 * as on landing with the sword; then onto the next under 50 % near, turning 180-360 degrees, up to 5; then a back flip;
 * the camera from behind as for Ctrl + W + Space + R; 120 s cooldown, 10 of 100 energy for each enemy killed"; 2026-11-09:
 * "only with 2 to 6 of them about, at 50 % health or under; 7 blocks; the cooldown 30 s shorter; after the sword's finisher
 * on LMB the chain goes on by itself if they are about", with the author's new takes, round14.py).
 *
 * <p>The client picks the mobs and carries the player (ChainInput): an arc onto the shoulders of each (Move.CHAIN_LEAP, then
 * CHAIN_HOP), the back flip down (CHAIN_FLIP). The server checks each mob, holds it still until the blade goes in and
 * kills it then (KILL), and keeps the fall from hurting.</p>
 */
public final class Chain {
    /** The flip (round14.py's chain_flip): off the shoulders (0.15 s), the feet down (0.9 s). */
    public static final int FLIP_OFF = 3, FLIP_LAND = 18;
    /** The first mob may be this far (blocks, from the player); the leap is a jump of about that. [default] */
    public static final double FIRST_REACH = 7.0;
    /** The fewest weak mobs about for the chain (author, 2026-11-09: "from 2 to 6"; the most is MovesConfig.chainMax). */
    public static final int FEWEST = 2;

    /** Off the ground (the leap: 0.2 s) or the shoulders (the hop: 0.1 s), round14.py's keys. */
    public static int off(Move m) {
        return m == Move.CHAIN_LEAP ? 4 : 2;
    }

    /** On the mob's shoulders: the leap at 0.5 s, the hop at 0.55 s. */
    public static int land(Move m) {
        return m == Move.CHAIN_LEAP ? 10 : 11;
    }

    /** The blade into the head, the mob dead: the leap at 0.8 s, the hop at 0.75 s. */
    public static int kill(Move m) {
        return m == Move.CHAIN_LEAP ? 16 : 15;
    }

    /** The blade out again, the arms up: the next leap (or the flip) may start (the leap's and the hop's last ticks). */
    public static int next(Move m) {
        return m.length - 2;
    }

    /** A mob the chain may land on: a mob (not a player, not a boss), alive, at MovesConfig.chainHealth of its health or under. */
    public static boolean weak(LivingEntity e) {
        return e instanceof Mob && e.isAlive() && !e.isInvulnerable() && !Strikes.boss(e)
                && e.getHealth() <= e.getMaxHealth() * MovesConfig.chainHealth() + 0.01f;
    }

    /** Enough weak mobs about the player for the chain (FEWEST), within the first leap and a hop on. */
    public static boolean enough(Player p) {
        double reach = FIRST_REACH + MovesConfig.chainRange();
        return p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach),
                e -> e != p && weak(e) && e.distanceTo(p) <= reach).size() >= FEWEST;
    }

    /** The mob a leap of the chain is made at: weak, within reach of where the player is (more for the first). */
    public static boolean valid(Player p, Move move, Entity e) {
        if (!(e instanceof LivingEntity le) || !weak(le)) return false;
        double reach = (move == Move.CHAIN_LEAP ? FIRST_REACH : MovesConfig.chainRange()) + 1.5;     // the client is ahead
        return e.position().distanceTo(p.position()) <= reach;
    }

    /** Every server tick of the chain: no fall to hurt; the mob held until the blade goes in, killed then. */
    static void serverTick(ServerPlayer p, Stamina s) {
        if (s.move == null || !s.move.chain()) return;
        p.fallDistance = 0f;
        if (s.move == Move.CHAIN_FLIP) return;
        if (!(p.level().getEntity(s.target) instanceof LivingEntity mob) || !mob.isAlive()) return;
        int kill = kill(s.move);
        if (s.moveTick < kill) {
            Vec3 v = mob.getDeltaMovement();
            mob.setDeltaMovement(0, Math.min(v.y, 0), 0);
            mob.hurtMarked = true;
            if (mob instanceof Mob m) m.getNavigation().stop();
        } else if (s.moveTick == kill) {
            mob.invulnerableTime = 0;
            mob.hurt(p.damageSources().playerAttack(p), mob.getHealth() + mob.getAbsorptionAmount() + 1000f);
            ServerLevel level = p.serverLevel();
            Vec3 at = mob.position().add(0, mob.getBbHeight() * 0.85, 0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1f, 0.7f);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8f, 0.6f);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 16, 0.2, 0.2, 0.2, 0.3);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.1);
            p.getMainHandItem().hurtAndBreak(1, p, e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            p.setLastHurtMob(mob);
            s.combatTicks = MovesConfig.combatTicks();
        }
    }

    private Chain() {}
}
