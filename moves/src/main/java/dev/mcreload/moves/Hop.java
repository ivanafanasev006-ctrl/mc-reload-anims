package dev.mcreload.moves;

import dev.mcreload.moves.net.MovesNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The hop over an enemy at point blank (docs/combat-spec.md, 5): R + W when an enemy right in
 * front is about to strike. Their blow in the wind-up (0-0.35 s) does not land; until the player
 * lands behind them they cannot hurt the player, and they stoop forward as if they had missed.
 * After the hop the player has Speed I for 1 s to strike back.
 */
public final class Hop {
    /** The enemy stoops (as if they had missed) from the take-off until shortly after the landing. */
    public static final int STOOP_TICKS = Move.HOP_LAND - Move.HOP_TAKEOFF + 6;

    /**
     * The enemy R + W would hop over, or null: the nearest one within reach in front of the player
     * that means to strike them. {@code strict} (the client choosing between hop and roll) also
     * wants them facing the player; the server's check is looser, for lag.
     */
    public static LivingEntity target(Player p, boolean strict) {
        double range = MovesConfig.hopRange() + (strict ? 0 : 1.5);
        Vec3 look = horizontal(p.getViewVector(1f));
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range + 2),
                e -> e != p && e.isAlive() && !e.isSpectator() && !e.isPassengerOfSameVehicle(p))) {
            Vec3 to = horizontal(e.position().subtract(p.position()));
            double dist = to.length() - e.getBbWidth() / 2;
            if (dist > range || dist >= bestDist) continue;
            if (to.lengthSqr() > 1.0E-4 && angle(look, to) > (strict ? 50 : 80)) continue;
            if (!threatens(e, p, strict)) continue;
            best = e;
            bestDist = dist;
        }
        return best;
    }

    /** The server's check of the enemy a client hops over: near enough, in front, set on the player. */
    public static boolean valid(Player p, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le == p) return false;
        Vec3 to = horizontal(le.position().subtract(p.position()));
        if (to.length() - le.getBbWidth() / 2 > MovesConfig.hopRange() + 1.5) return false;
        if (to.lengthSqr() > 1.0E-4 && angle(horizontal(p.getViewVector(1f)), to) > 80) return false;
        return threatens(le, p, false);
    }

    /** A melee enemy set on the player (a monster that is aggressive or targets them, or a player). */
    private static boolean threatens(LivingEntity e, Player p, boolean strict) {
        if (e instanceof Player other) {
            if (other.isCreative() || other.isSpectator()) return false;
            return !strict || facing(e, p, 70);
        }
        if (!(e instanceof Mob mob) || !(e instanceof Enemy)) return false;
        if (mob.getMainHandItem().getItem() instanceof ProjectileWeaponItem) return false;    // archers do not wind up
        boolean set = mob.isAggressive() || mob.getTarget() == p;
        return set && (!strict || facing(e, p, 70));
    }

    private static boolean facing(LivingEntity e, Player p, double degrees) {
        Vec3 look = horizontal(Vec3.directionFromRotation(0, e.getYHeadRot()));
        Vec3 to = horizontal(p.position().subtract(e.position()));
        return to.lengthSqr() < 1.0E-4 || angle(look, to) <= degrees;
    }

    private static Vec3 horizontal(Vec3 v) {
        return new Vec3(v.x, 0, v.z);
    }

    private static double angle(Vec3 a, Vec3 b) {
        double la = a.length(), lb = b.length();
        if (la < 1.0E-6 || lb < 1.0E-6) return 0;
        return Math.toDegrees(Math.acos(Mth.clamp(a.dot(b) / (la * lb), -1, 1)));
    }

    // ------------------------------------------------------------------ server

    /** The hop starts: the enemy is remembered and everyone who sees them is told to make them stoop. */
    static void started(ServerPlayer p, Stamina s, Entity target) {
        s.hopTarget = target.getId();
        s.hopSped = false;
        MovesNet.stoop(target, Move.HOP_TAKEOFF, STOOP_TICKS);
    }

    /** Every server tick of a hop: no fall damage from it, and Speed I from the landing. */
    static void serverTick(ServerPlayer p, Stamina s) {
        if (s.move != Move.HOP) return;
        p.fallDistance = 0f;
        if (s.moveTick >= Move.HOP_LAND) landed(p, s);
    }

    /** Speed I to strike back (author: 1 s), once: at the landing, or when the player runs off before it. */
    static void landed(ServerPlayer p, Stamina s) {
        if (s.hopSped) return;
        s.hopSped = true;
        int ticks = MovesConfig.hopSpeedTicks();
        if (ticks > 0) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0, false, true, true));
    }

    /**
     * The enemy's blow: in the wind-up it is parried (it does not land, a clang); later, until the
     * landing, they cannot hurt the player at all.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.isCanceled()) return;
        Stamina s = Stamina.of(p);
        if (s.move != Move.HOP || s.moveTick > Move.HOP_LAND + 3) return;
        Entity by = event.getSource().getEntity();
        if (by == null || by.getId() != s.hopTarget) return;
        event.setCanceled(true);
        if (s.moveTick <= Move.HOP_TAKEOFF && p.invulnerableTime <= 10) {
            ServerLevel level = p.serverLevel();
            Vec3 at = by.position().add(p.position()).scale(0.5).add(0, 1.2, 0);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 1.9f);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.15, 0.15, 0.15, 0.4);
            s.combatTicks = MovesConfig.combatTicks();
        }
    }

    private Hop() {}
}
