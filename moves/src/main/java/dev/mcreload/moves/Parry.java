package dev.mcreload.moves;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The sword's turns parry (author, after 0.9.1: "these 3 turns must parry any blows but bosses' skills, and send back
 * the arrows and missiles shot at the player, a ghast's or a blaze's"): while the player turns (Move.SWORD_SPIN, G held
 * with a sword) a mob's blow glances off the blade (no damage, the mob knocked back a little) and a projectile flies back
 * the way it came (a fireball as the player's own, as one punched back); a boss's (Strikes.boss) blows and missiles, and
 * what is not a blow or a missile (fire, a fall, magic, an explosion), still hurt.
 */
public final class Parry {
    /** The blade sends a projectile back with this much of its speed. [default] */
    static final double BACK = 0.8;

    static boolean parrying(Entity e) {
        return e instanceof ServerPlayer p && Stamina.of(p).move == Move.SWORD_SPIN;
    }

    /** A mob's blow while the player turns: it glances off the blade. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void blow(LivingAttackEvent event) {
        if (event.isCanceled() || !parrying(event.getEntity())) return;
        ServerPlayer p = (ServerPlayer) event.getEntity();
        var src = event.getSource();
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || src.is(DamageTypeTags.IS_EXPLOSION) || src.is(DamageTypeTags.IS_FIRE)) return;
        Entity by = src.getEntity(), direct = src.getDirectEntity();
        if (by instanceof LivingEntity mob && Strikes.boss(mob)) return;
        if (direct instanceof Projectile) {
            // a projectile that got through (the impact below sends most back)
            event.setCanceled(true);
            return;
        }
        if (!(by instanceof LivingEntity mob) || direct != by) return;
        event.setCanceled(true);
        Vec3 away = Strikes.flat(mob.position().subtract(p.position()));
        if (away.lengthSqr() > 1.0E-4) mob.knockback(0.4, -away.x, -away.z);
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position().add(away.normalize().scale(0.8)).add(0, 1.2, 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 1.8f);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.3);
    }

    /** A projectile reaching the player while they turn flies back the way it came (not a boss's). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void missile(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit) || !parrying(hit.getEntity())) return;
        ServerPlayer p = (ServerPlayer) hit.getEntity();
        Projectile shot = event.getProjectile();
        if (shot.getOwner() instanceof LivingEntity owner && Strikes.boss(owner)) return;
        if (shot.getTags().contains(Deflect.TAG)) {
            event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
            return;
        }
        shot.addTag(Deflect.TAG);
        shot.setDeltaMovement(shot.getDeltaMovement().scale(-BACK));
        if (shot instanceof AbstractHurtingProjectile fire) {
            fire.xPower = -fire.xPower;
            fire.yPower = -fire.yPower;
            fire.zPower = -fire.zPower;
            fire.setOwner(p);
        }
        shot.hasImpulse = true;
        event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        Vec3 at = shot.position();
        p.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3f, 2.0f);
        p.serverLevel().sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.1, 0.1, 0.1, 0.3);
    }

    private Parry() {}
}
