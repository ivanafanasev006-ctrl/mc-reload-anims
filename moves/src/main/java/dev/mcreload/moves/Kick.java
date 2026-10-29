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
 * The vault with a kick (hop_on_kick; author, 2026-10-29, fourth message): W and Space down (held,
 * or pressed a moment before; no timing), R near a mob of some size, from the ground or a jump. A
 * cutscene: the vault over it as in hop_roll_dodge, the turn to face it (the camera with the
 * player), a jump spinning a whole turn with the leg swung out, which meets its back (1 heart,
 * flung away however heavy it is), the landing facing it ("after it he stays facing the mob, as
 * after Slide + LMB"). The mob stoops under the hand as in the vault and is held still until the
 * kick; nothing hurts the player until the landing.
 */
public final class Kick {
    /** Take-off (0.2 s) and the vault's landing (0.72 s), as in hop_roll_dodge; in ticks. */
    public static final int TAKEOFF = Move.HOP_TAKEOFF, VAULT_LAND = Move.HOP_LAND;
    /** The half turn to the mob (0.72-0.9 s), the spinning jump (0.95 s), the kick (1.31 s), the landing (1.46 s). */
    public static final int TURN_FROM = 14, TURN_TO = 18, SPIN = 19, HIT = 26, LAND = 29;
    /** The leg reaches this far from the player's middle to the mob's side. */
    public static final double REACH = 2.4;
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
        if (target instanceof LivingEntity le) MovesNet.stoop(target, TAKEOFF, Hop.STOOP_TICKS, Hop.lean(le));
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
        if (!s.kickDone && s.moveTick >= HIT) {
            // The leg meets the mob if it is still in reach (it was held still; a lagging client is let through).
            double gap = mob.position().subtract(p.position()).horizontalDistance() - mob.getBbWidth() / 2;
            if (gap <= REACH + 1.0) kick(p, s, mob);
            else s.kickDone = true;
        }
    }

    /**
     * The kick into its back: 1 heart (the author's), and it flies back the way the player came,
     * whatever resists knockback in it (author: "if the mob is heavy and resists being knocked
     * back, ignore it").
     */
    static void kick(ServerPlayer p, Stamina s, LivingEntity mob) {
        s.kickDone = true;
        // Away from the player (who has turned to it), else back the way they came.
        Vec3 away = mob.position().subtract(p.position());
        away = new Vec3(away.x, 0, away.z);
        if (away.lengthSqr() < 1.0E-4) {
            float r = s.kickYaw * net.minecraft.util.Mth.DEG_TO_RAD;
            away = new Vec3(net.minecraft.util.Mth.sin(r), 0, -net.minecraft.util.Mth.cos(r));
        }
        Vec3 back = away.normalize();
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
