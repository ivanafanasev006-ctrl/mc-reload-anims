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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The vault over a mob at point blank (docs/combat-spec.md, 5): R + W with a mob of some size right
 * in front (a zombie, a skeleton, a pig, a sheep; not a chicken nor anything as small). The player
 * rolls over its back: it stoops under the hand, its blow in the crouch (0-0.25 s) does not land,
 * and until the player lands beyond it it cannot hurt them; at 0.8 s it is shoved back 1-2 blocks
 * and the player runs on, with Speed I for 1 s.
 */
public final class Hop {
    /** The mob stoops from the take-off until shortly after the landing. */
    public static final int STOOP_TICKS = Move.HOP_LAND - Move.HOP_TAKEOFF + 6;
    /** How far the mob stoops under the hand (degrees, from its feet): a tall one deep, a low one hardly. */
    public static final float LEAN_TALL = 40f, LEAN_LOW = 12f;
    /** Above this height a mob stoops deep (a zombie does, a pig or a sheep does not). */
    public static final float TALL = 1.4f;
    /** Room left between the rolling body and the mob's back (blocks). */
    public static final float CLEARANCE = 0.15f;

    /**
     * The mob R + W would vault over, or null: the nearest one within reach in front of the player
     * that is big enough to vault. {@code strict} (the client choosing between vault and roll) wants
     * it within 50 degrees of the look; the server's check is looser, for lag.
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
            if (!vaultable(e)) continue;
            best = e;
            bestDist = dist;
        }
        return best;
    }

    /** The server's check of the mob a client vaults over: near enough, in front, the right size. */
    public static boolean valid(Player p, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le == p) return false;
        Vec3 to = horizontal(le.position().subtract(p.position()));
        if (to.length() - le.getBbWidth() / 2 > MovesConfig.hopRange() + 1.5) return false;
        if (to.lengthSqr() > 1.0E-4 && angle(horizontal(p.getViewVector(1f)), to) > 80) return false;
        return vaultable(le);
    }

    /**
     * Big enough to put a hand on and low enough to go over (author: a zombie, a skeleton, a pig, a
     * sheep, not a chicken nor a small mob), standing on something, not a boss; another player too.
     */
    public static boolean vaultable(LivingEntity e) {
        if (e instanceof Player other) return !other.isCreative() && !other.isSpectator();
        if (!(e instanceof Mob) || Strikes.boss(e) || e.isPassenger() || e.isVehicle()) return false;
        float w = e.getBbWidth(), h = e.getBbHeight();
        return w >= MovesConfig.vaultMinWidth() && h >= MovesConfig.vaultMinHeight() && h <= MovesConfig.vaultMaxHeight();
    }

    /** How far this mob stoops under the vaulting hand (degrees). */
    public static float lean(LivingEntity e) {
        return e.getBbHeight() > TALL ? LEAN_TALL : LEAN_LOW;
    }

    /** Height of the mob's back as it stoops (blocks above its feet): what the vault must clear. */
    public static float back(LivingEntity e) {
        return e.getBbHeight() * Mth.cos(lean(e) * Mth.DEG_TO_RAD) + CLEARANCE;
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

    /** The vault starts: the mob is remembered, and everyone who sees it is told to make it stoop. */
    static void started(ServerPlayer p, Stamina s, Entity target) {
        s.target = target.getId();
        s.hopSped = false;
        s.hopShoved = false;
        s.hopFrom = p.position();
        if (target instanceof LivingEntity le) MovesNet.stoop(target, Move.HOP_TAKEOFF, STOOP_TICKS, lean(le));
    }

    /**
     * Every server tick of a vault: no fall damage from it, the shove, and Speed I from the landing.
     * The shove waits until this end sees the player past the mob (it hears of the player's moves a
     * little late), or the landing at the latest: shoved while the player is still over it, the mob
     * would fly into them and stop.
     */
    static void serverTick(ServerPlayer p, Stamina s) {
        if (s.move != Move.HOP) return;
        p.fallDistance = 0f;
        if (!s.hopShoved && s.moveTick >= Move.HOP_SHOVE && (past(p, s) || s.moveTick >= Move.HOP_LAND + 2)) shove(p, s);
        if (s.moveTick >= Move.HOP_LAND) landed(p, s);
    }

    /** The player is beyond the mob, seen from where the vault took off. */
    private static boolean past(ServerPlayer p, Stamina s) {
        Entity e = p.level().getEntity(s.target);
        if (e == null || s.hopFrom == null) return true;
        Vec3 way = horizontal(e.position().subtract(s.hopFrom));
        if (way.lengthSqr() < 1.0E-4) return true;
        return horizontal(p.position().subtract(e.position())).dot(way.normalize()) > e.getBbWidth() / 2 + 0.3;
    }

    /**
     * The hand pushes off the mob's back: it is shoved back the way the player came, 1-2 blocks
     * (less if it resists knockback), without harm.
     */
    static void shove(ServerPlayer p, Stamina s) {
        s.hopShoved = true;
        Entity e = p.level().getEntity(s.target);
        if (!(e instanceof LivingEntity mob) || !mob.isAlive() || s.hopFrom == null) return;
        Vec3 back = horizontal(s.hopFrom.subtract(mob.position()));
        if (back.lengthSqr() < 1.0E-4) back = horizontal(p.getViewVector(1f)).scale(-1);
        back = back.normalize();
        // knockback(strength, x, z) pushes away from (x, z): pass the opposite of where it should go.
        mob.knockback(MovesConfig.vaultShove(), -back.x, -back.z);
        mob.hurtMarked = true;
        p.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.6f, 0.9f);
    }

    /** Speed I to run on (author: 1 s), once: at the landing, or when the player runs off before it. */
    static void landed(ServerPlayer p, Stamina s) {
        if (s.hopSped) return;
        s.hopSped = true;
        int ticks = MovesConfig.hopSpeedTicks();
        if (ticks > 0) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0, false, true, true));
    }

    /**
     * The mob's blow: in the crouch it is parried (it does not land, a clang); later, until the
     * landing, it cannot hurt the player at all.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.isCanceled()) return;
        Stamina s = Stamina.of(p);
        if (s.move != Move.HOP || s.moveTick > Move.HOP_LAND + 3) return;
        Entity by = event.getSource().getEntity();
        if (by == null || by.getId() != s.target) return;
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
