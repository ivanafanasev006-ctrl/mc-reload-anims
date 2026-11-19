package dev.mcreload.moves;

import dev.mcreload.moves.net.MovesNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The sword block's answers (author, 2026-11-19), perks each (Skill.GUARD_*), started by the server when a mob's blow
 * meets the raised blade (Guard), one sword or two:
 *
 * <ul>
 *   <li><b>The finisher</b> (GUARD_FINISH): any blow blocked from a mob with less than a quarter of its health left. Its
 *   wrist caught on the blade (0.5 s), its arm twisted behind its back (1 s), the kick into its back; it stumbles, falls on
 *   its face and dies. The mob is held meanwhile, the player untouchable and staying where they stood.</li>
 *   <li><b>The kick</b> (GUARD_KICK): a blow blocked (not perfectly): the front kick throws it about 2 blocks back, no
 *   stun; the blade goes up again if the button is still held.</li>
 *   <li><b>The shove</b> (GUARD_COUNTER): a perfect parry: caught mid-blade, the body gives back with the blow, then body
 *   and blade drive it 5 blocks away, 2 hearts.</li>
 *   <li><b>The living shield</b> (GUARD_COUNTER): a perfect parry with 2 or more enemies near or a shooter in range: the
 *   wrist caught, a turn, the blade into its back from behind, the free hand on its shoulder. Held so, it takes every blow
 *   and arrow from the front meant for the player (the back is open); the right button again kicks it off the blade like a
 *   shot, knocking down those it flies into; else it dies by itself after a while.</li>
 * </ul>
 *
 * Bosses, players and mobs not of a man's build are not caught (the shove and the kick still work on the last).
 */
public final class GuardAnswers {
    /** The finisher (ticks): the wrist caught, the twist, the kick, the death, the end. */
    public static final int FIN_CATCH = 10, FIN_KICK = 30, FIN_KILL = 42, FIN_LEN = 46;
    /** The kick: the foot meets it; the end. */
    public static final int KICK_HIT = 5, KICK_LEN = 13;
    /** The shove: the body drives it off; the end. */
    public static final int SHOVE_PUSH = 9, SHOVE_LEN = 17;
    /** The grab: the wrist caught, swung round in front, the blade in; the end (on into the hold). */
    public static final int GRAB_TURN = 6, GRAB_STAB = 14, GRAB_LEN = 22;
    /** The living shield kicked off: the foot; the end. */
    public static final int THROW_HIT = 5, THROW_LEN = 15;
    /** The mob is held this far in front of the player (blocks, between their middles). */
    static final double HOLD_AT = 1.05, SHIELD_AT = 0.8;
    /** A flying living shield goes straight this many ticks, then falls; it dies on landing or after FLIGHT_MAX. */
    static final int FLIGHT_STRAIGHT = 7, FLIGHT_MAX = 30;

    private static final class Flight {
        final int mob;
        final Vec3 dir;
        int tick;
        final Set<Integer> hit = new HashSet<>();

        Flight(int mob, Vec3 dir) {
            this.mob = mob;
            this.dir = dir;
        }
    }

    private static final Map<UUID, Flight> FLYING = new HashMap<>();

    // ------------------------------------------------------------------ when

    /** A mob of a man's build (a zombie, a skeleton, a villager, a piglin...): one that can be caught and held. */
    static boolean catchable(LivingEntity e) {
        return e instanceof Mob && !Strikes.boss(e) && e.getBbWidth() <= 1.0f && e.getBbHeight() >= 1.0f && e.getBbHeight() <= 2.5f;
    }

    /** The mob behind a blow, if it struck in reach with its own hand (no arrow, no spell). */
    static LivingEntity striker(DamageSource src) {
        Entity by = src.getEntity();
        if (!(by instanceof Mob mob) || src.getDirectEntity() != by || Strikes.boss(mob) || !mob.isAlive()) return null;
        return mob;
    }

    /**
     * A blow meets the raised blade (Guard.attack: blockable, before the vanilla hurt): the finisher for a weak mob,
     * at any moment of the guard; in the parry window the shove or the living shield. True: answered (the blow cancelled).
     */
    static boolean answerBlow(ServerPlayer p, Stamina s, DamageSource src, boolean parry) {
        LivingEntity mob = striker(src);
        if (mob == null || s.move != null) return false;
        float left = mob.getHealth() / Math.max(1f, mob.getMaxHealth());
        if (left < MovesConfig.guardFinishHealth() && catchable(mob) && s.progress.usable(Skill.GUARD_FINISH)
                && s.canAfford(MovesConfig.costGuardFinish(), p)) {
            s.spend(MovesConfig.costGuardFinish(), p);
            begin(p, s, Move.GUARD_FINISH, mob);
            MovesNet.mobPose(mob, MobPose.CAUGHT, FIN_CATCH + 2, false);
            return true;
        }
        if (!parry || !s.progress.usable(Skill.GUARD_COUNTER)) return false;
        if (catchable(mob) && crowded(p, mob) && s.canAfford(MovesConfig.costLivingShield(), p)) {
            s.spend(MovesConfig.costLivingShield(), p);
            begin(p, s, Move.GUARD_GRAB, mob);
            MovesNet.mobPose(mob, MobPose.CAUGHT, GRAB_STAB, false);
            return true;
        }
        if (!s.canAfford(MovesConfig.costGuardShove(), p)) return false;
        s.spend(MovesConfig.costGuardShove(), p);
        begin(p, s, Move.GUARD_SHOVE, mob);
        MovesNet.mobPose(mob, MobPose.CAUGHT, SHOVE_PUSH + 1, false);
        return true;
    }

    /** A blow blocked (not perfectly; Guard.hurt): the kick, if the perk is in its row. */
    static void blocked(ServerPlayer p, Stamina s, DamageSource src) {
        LivingEntity mob = striker(src);
        if (mob == null || s.move != null || !s.progress.usable(Skill.GUARD_KICK) || mob.distanceTo(p) > 3.5f) return;
        begin(p, s, Move.GUARD_KICK, mob);
    }

    /** Two or more enemies near (the one that struck too), or one that shoots in range. */
    static boolean crowded(ServerPlayer p, LivingEntity mob) {
        double near = MovesConfig.shieldCrowdRange(), far = MovesConfig.shieldArcherRange();
        int enemies = 0;
        boolean archer = false;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(Math.max(near, far)),
                e -> e.isAlive() && e != p && e instanceof Enemy)) {
            double d = e.distanceTo(p);
            if (d <= near) enemies++;
            if (e instanceof RangedAttackMob && d <= far) archer = true;
        }
        return archer || enemies >= MovesConfig.shieldCrowd();
    }

    private static void begin(ServerPlayer p, Stamina s, Move m, LivingEntity mob) {
        Guard.lower(p, s);
        s.start(p, m, false);
        s.target = mob.getId();
        s.combatTicks = MovesConfig.combatTicks();
        MovesNet.force(p, m, -1);
        MoveServer.sync(p, s, true);
        ServerLevel level = p.serverLevel();
        Vec3 at = p.getEyePosition().add(p.getViewVector(1f).scale(0.7)).subtract(0, 0.3, 0);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3f, 1.9f);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.35);
    }

    // ------------------------------------------------------------------ every tick

    static void serverTick(ServerPlayer p, Stamina s) {
        Flight f = FLYING.get(p.getUUID());
        if (f != null && fly(p, f)) FLYING.remove(p.getUUID());
        if (s.move == null || !(s.move.guardAnswer() || s.move == Move.HOSTAGE_KICK)) return;
        Entity e = p.level().getEntity(s.target);
        LivingEntity mob = e instanceof LivingEntity le && le.isAlive() ? le : null;
        switch (s.move) {
            case GUARD_FINISH -> finishTick(p, s, mob);
            case GUARD_KICK -> {
                if (mob != null && s.moveTick == KICK_HIT && mob.distanceTo(p) <= 3.5f) {
                    hit(p, mob, MovesConfig.guardKickDamage(), MovesConfig.guardKickStrength());
                    MovesNet.mobPose(mob, MobPose.FLUNG, 10, false);
                }
            }
            case GUARD_SHOVE -> {
                if (mob == null) return;
                if (s.moveTick < SHOVE_PUSH) hold(p, mob, HOLD_AT, false);
                else if (s.moveTick == SHOVE_PUSH) {
                    hit(p, mob, MovesConfig.guardShoveDamage(), MovesConfig.guardShoveStrength());
                    MovesNet.mobPose(mob, MobPose.FLUNG, 20, false);
                }
            }
            case GUARD_GRAB -> {
                if (mob == null) {
                    stop(p, s);
                    return;
                }
                // caught at arm's length, swung round to the front (its back to the player), the blade in
                hold(p, mob, s.moveTick < GRAB_TURN ? HOLD_AT : SHIELD_AT, s.moveTick >= GRAB_TURN);
                if (s.moveTick == GRAB_STAB) stab(p, mob);
                if (s.moveTick == GRAB_LEN - 1) {
                    s.start(p, Move.HOSTAGE_HOLD, false);
                    s.target = mob.getId();
                    MovesNet.force(p, Move.HOSTAGE_HOLD, -1);
                    MovesNet.mobPose(mob, MobPose.HOSTAGE, MovesConfig.shieldHoldTicks() + 40, false);
                }
            }
            case HOSTAGE_HOLD -> {
                if (mob == null || !Guard.isSword(p.getMainHandItem())) {
                    if (mob != null) MovesNet.mobPose(mob, MobPose.NONE, 0, false);
                    stop(p, s);
                    return;
                }
                hold(p, mob, SHIELD_AT, true);
                if (s.moveTick >= MovesConfig.shieldHoldTicks()) {
                    // it dies by itself on the blade (author)
                    kill(p, mob);
                    stop(p, s);
                }
            }
            case HOSTAGE_KICK -> {
                if (mob == null) return;
                if (s.moveTick < THROW_HIT) hold(p, mob, SHIELD_AT, true);
                else if (s.moveTick == THROW_HIT) launch(p, mob);
            }
            default -> {
            }
        }
    }

    private static void finishTick(ServerPlayer p, Stamina s, LivingEntity mob) {
        if (mob == null) return;
        if (s.moveTick < FIN_KICK) {
            hold(p, mob, HOLD_AT, false);
            if (s.moveTick == FIN_CATCH) MovesNet.mobPose(mob, MobPose.TWISTED, FIN_KICK - FIN_CATCH + 2, false);
        } else if (s.moveTick == FIN_KICK) {
            // the kick into its back: it stumbles off, falls on its face (turned: its back was to the player)
            MovesNet.mobPose(mob, MobPose.FALL_FACE, 60, true);
            Vec3 away = away(p, mob);
            mob.setDeltaMovement(away.x * 0.32, 0.05, away.z * 0.32);
            mob.hurtMarked = true;
            ServerLevel level = p.serverLevel();
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.1f, 0.75f);
        } else if (s.moveTick < FIN_KILL) {
            still(mob);
        } else if (s.moveTick == FIN_KILL) {
            kill(p, mob);
        }
    }

    /** The mob held where the move wants it: in front of the player, turned to them (or, facing away, their way). */
    private static void hold(ServerPlayer p, LivingEntity mob, double at, boolean facingAway) {
        float yaw = p.getYRot();
        Vec3 look = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
        Vec3 to = p.position().add(look.scale(at));
        // not into a wall: where it is, if the place in front is taken
        if (!p.level().noCollision(mob, mob.getBoundingBox().move(to.subtract(mob.position())))) to = mob.position();
        float face = facingAway ? yaw : yaw + 180f;
        mob.moveTo(to.x, Math.max(to.y, p.getY()), to.z, face, 0f);
        mob.setYHeadRot(face);
        mob.setYBodyRot(face);
        still(mob);
    }

    /** Doing nothing: no walking, no aim, no fall. */
    private static void still(LivingEntity mob) {
        mob.setDeltaMovement(0, Math.min(0, mob.getDeltaMovement().y), 0);
        mob.hurtMarked = true;
        mob.fallDistance = 0f;
        if (mob instanceof Mob m) {
            m.getNavigation().stop();
            m.setTarget(null);
        }
    }

    private static Vec3 away(ServerPlayer p, LivingEntity mob) {
        Vec3 d = Strikes.flat(mob.position().subtract(p.position()));
        if (d.lengthSqr() < 1.0E-4) d = Strikes.flat(p.getViewVector(1f));
        return d.normalize();
    }

    private static void hit(ServerPlayer p, LivingEntity mob, float damage, double strength) {
        Vec3 away = away(p, mob);
        if (damage > 0f) {
            mob.invulnerableTime = 0;
            mob.hurt(p.damageSources().playerAttack(p), damage);
        }
        Strikes.shove(mob, away, strength);
        ServerLevel level = p.serverLevel();
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2f, 0.8f);
        Vec3 at = mob.position().add(0, mob.getBbHeight() * 0.6, 0);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.4);
        p.setLastHurtMob(mob);
    }

    private static void stab(ServerPlayer p, LivingEntity mob) {
        ServerLevel level = p.serverLevel();
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1f, 0.9f);
        Vec3 at = mob.position().add(0, mob.getBbHeight() * 0.55, 0);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 5, 0.15, 0.15, 0.15, 0.1);
        p.setLastHurtMob(mob);
    }

    static void kill(ServerPlayer p, LivingEntity mob) {
        mob.invulnerableTime = 0;
        mob.hurt(p.damageSources().playerAttack(p), mob.getHealth() + mob.getAbsorptionAmount() + 1000f);
        p.setLastHurtMob(mob);
    }

    /** The server ended the move: the player's own client too. */
    private static void stop(ServerPlayer p, Stamina s) {
        s.end(p);
        MovesNet.forceStop(p);
        MoveServer.sync(p, s, true);
    }

    // ------------------------------------------------------------------ the living shield kicked off

    /** The right button again, holding it (MoveServer: HOSTAGE_KICK asked for by the client). */
    static void kickOff(ServerPlayer p, Stamina s) {
        // s.target is still the one held
        s.combatTicks = MovesConfig.combatTicks();
    }

    private static void launch(ServerPlayer p, LivingEntity mob) {
        Vec3 dir = Strikes.flat(p.getViewVector(1f));
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
        mob.moveTo(p.getX() + dir.x * 1.2, p.getY() + 0.3, p.getZ() + dir.z * 1.2, p.getYRot(), 0f);
        MovesNet.mobPose(mob, MobPose.THROWN, 60, false);
        FLYING.put(p.getUUID(), new Flight(mob.getId(), dir));
        ServerLevel level = p.serverLevel();
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.3f, 0.6f);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 0.5f, 1.3f);
        p.setLastHurtMob(mob);
    }

    /** A tick of the flight: straight on at first, knocking down whoever it meets; dead where it lands. True: over. */
    private static boolean fly(ServerPlayer p, Flight f) {
        if (!(p.level().getEntity(f.mob) instanceof LivingEntity mob) || !mob.isAlive()) return true;
        f.tick++;
        double speed = MovesConfig.shieldThrowSpeed();
        if (f.tick <= FLIGHT_STRAIGHT) mob.setDeltaMovement(f.dir.x * speed, 0.02, f.dir.z * speed);
        mob.hurtMarked = true;
        mob.hasImpulse = true;
        if (mob instanceof Mob m) {
            m.getNavigation().stop();
            m.setTarget(null);
        }
        AABB box = mob.getBoundingBox().inflate(0.4).expandTowards(mob.getDeltaMovement());
        for (LivingEntity other : p.level().getEntitiesOfClass(LivingEntity.class, box,
                o -> o.isAlive() && o != mob && o != p && !f.hit.contains(o.getId()))) {
            f.hit.add(other.getId());
            other.invulnerableTime = 0;
            other.hurt(p.damageSources().playerAttack(p), MovesConfig.shieldThrowDamage());
            Strikes.shove(other, f.dir, 0.8);
            if (catchable(other)) MovesNet.mobPose(other, MobPose.FLUNG, 16, false);
            p.serverLevel().playSound(null, other.getX(), other.getY(), other.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, 0.8f);
        }
        boolean wall = f.tick <= FLIGHT_STRAIGHT && f.tick > 1 && mob.horizontalCollision;
        if ((f.tick > 3 && mob.onGround()) || wall || f.tick >= FLIGHT_MAX) {
            kill(p, mob);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ the shield takes the blows

    /**
     * Holding the living shield (from the stab on): a blow or an arrow from the front meant for the player lands on it
     * instead (author: "it soaks all of it; in the back it gets through"); its own blows do nothing.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(LivingAttackEvent event) {
        if (event.isCanceled()) return;
        LivingEntity hurt = event.getEntity();
        if (hurt.level().isClientSide) return;
        // the held one (or caught: its wrist on the blade) strikes no one
        if (event.getSource().getEntity() instanceof LivingEntity by && (heldBySomeone(by) || TimeStop.frozen(by))) {
            event.setCanceled(true);
            return;
        }
        if (!(hurt instanceof ServerPlayer p)) return;
        Stamina s = Stamina.of(p);
        boolean shielded = s.move == Move.HOSTAGE_HOLD || (s.move == Move.GUARD_GRAB && s.moveTick >= GRAB_STAB)
                || (s.move == Move.HOSTAGE_KICK && s.moveTick < THROW_HIT);
        if (!shielded || !Guard.fromFront(p, event.getSource())) return;
        if (!(p.level().getEntity(s.target) instanceof LivingEntity mob) || !mob.isAlive()) return;
        event.setCanceled(true);
        mob.invulnerableTime = 0;
        mob.hurt(event.getSource(), event.getAmount());
        if (!mob.isAlive()) MovesNet.mobPose(mob, MobPose.FALL_FACE, 60, false);
    }

    /** Some player holds this mob in one of the answers (caught, twisted, on the blade). */
    private static boolean heldBySomeone(LivingEntity e) {
        if (!(e instanceof Mob)) return false;
        for (Player pl : e.level().players()) {
            Stamina s = Stamina.of(pl);
            if (s.target == e.getId() && s.move != null && (s.move.guardAnswer() || s.move == Move.HOSTAGE_KICK)) return true;
        }
        return false;
    }

    private GuardAnswers() {}
}
