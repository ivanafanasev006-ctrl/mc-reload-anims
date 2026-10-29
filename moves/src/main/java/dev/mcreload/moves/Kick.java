package dev.mcreload.moves;

import dev.mcreload.moves.net.MovesNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The vault with a kick (hop_on_kick; author, 2026-10-29): W held, Space and R with a mob of some
 * size in front. A cutscene: over the mob, a hang in the air turned to its back, a hard kick into
 * it that takes 1 heart and flings it away (however heavy it is), the turn back, the landing. The
 * mob stoops under the hand and is held still in the air time (it is "slowed" with the player's
 * perception, the client's side of the cutscene); nothing hurts the player until the landing.
 */
public final class Kick {
    /** Take-off (0.25 s), the kick (1.27 s) and the landing (1.95 s) of the animation, in ticks. */
    public static final int TAKEOFF = 5, HIT = 25, LAND = 39;
    /** The mob is held still from the take-off until it is kicked. */
    public static final int HOLD_FROM = TAKEOFF, HOLD_TO = HIT - 1;
    /** The mob a kick goes over: one the vault could go over. */
    public static boolean valid(ServerPlayer p, Entity e) {
        return Hop.valid(p, e);
    }

    /** The kick starts: the mob is remembered, it stoops under the hand as in the vault. */
    static void started(ServerPlayer p, Stamina s, Entity target) {
        s.target = target.getId();
        s.kickDone = false;
        s.kickFrom = p.position();
        s.kickYaw = p.getYRot();
        if (target instanceof LivingEntity le) MovesNet.stoop(target, TAKEOFF, HIT - TAKEOFF + 4, Hop.lean(le));
    }

    /** Every server tick of a kick: no fall damage, the mob held still in the air time, the kick. */
    static void serverTick(ServerPlayer p, Stamina s) {
        if (s.move != Move.KICK) return;
        p.fallDistance = 0f;
        Entity e = p.level().getEntity(s.target);
        if (!(e instanceof LivingEntity mob) || !mob.isAlive()) return;
        if (s.moveTick >= HOLD_FROM && s.moveTick <= HOLD_TO) {
            Vec3 v = mob.getDeltaMovement();
            mob.setDeltaMovement(0, Math.min(v.y, 0), 0);
            mob.hurtMarked = true;
            if (mob instanceof Mob m) m.getNavigation().stop();
        }
        if (!s.kickDone && s.moveTick >= HIT) kick(p, s, mob);
    }

    /**
     * The kick into its back: 1 heart (the author's), and it flies back the way the player came,
     * whatever resists knockback in it (author: "if the mob is heavy and resists being knocked
     * back, ignore it").
     */
    static void kick(ServerPlayer p, Stamina s, LivingEntity mob) {
        s.kickDone = true;
        float r = s.kickYaw * net.minecraft.util.Mth.DEG_TO_RAD;
        Vec3 back = new Vec3(net.minecraft.util.Mth.sin(r), 0, -net.minecraft.util.Mth.cos(r));    // the way the player came
        mob.invulnerableTime = 0;
        mob.hurt(p.damageSources().playerAttack(p), MovesConfig.kickDamage());
        Strikes.shove(mob, back, MovesConfig.kickStrength());
        ServerLevel level = p.serverLevel();
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2f, 0.7f);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 0.5f, 1.4f);
        Vec3 at = mob.position().add(0, mob.getBbHeight() * 0.6, 0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.2, 0.2, 0.2, 0.4);
        p.setLastHurtMob(mob);
        s.combatTicks = MovesConfig.combatTicks();
    }

    /** The mob kicked cannot hurt the player through the kick; nothing else can either (the move's untouchable frames). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.isCanceled()) return;
        Stamina s = Stamina.of(p);
        if (s.move != Move.KICK || s.moveTick > LAND + 3) return;
        Entity by = event.getSource().getEntity();
        if (by != null && by.getId() == s.target) event.setCanceled(true);
    }

    private Kick() {}
}
