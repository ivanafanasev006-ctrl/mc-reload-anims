package dev.mcreload.moves;

import dev.mcreload.moves.net.MovesNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The moves of 2026-11-20, night (author; animations round25.py; numbers not his are [default], in the config's newMoves).
 *
 * <ul>
 *   <li><b>The axe's slam</b> (W + Space + G): up and down onto the ground with the axe; a wave runs out 7 blocks round him
 *   (author) and throws each one up out of the ground as it gets to it.</li>
 *   <li><b>The axe's heavy blows</b> (G held): three, each with a big slash: down along from the upper right, level from the
 *   side, up along from the lower left (author: "the first along from above, the second from the side, the last from
 *   below upwards, at an angle as the first").</li>
 *   <li><b>The axe's chain</b> (Ctrl + W + Space + LMB, the sword's chain's conditions): they slow down and freeze; the axe
 *   into the skull of the first, a kick sending it off; a dash to the next and a spinning blow that throws it away; the
 *   uppercut; and so on, a variation each, all dead; the time stop's camera, after-images and slashes, a shock ring at
 *   each blow.</li>
 *   <li><b>The sword's charge</b> (Ctrl + W + G): a run, fast, 3 s (author); one in its way is run through the belly,
 *   carried on the blade a second and thrown off forward.</li>
 *   <li><b>The flying knee</b> (Ctrl + W + Space + R with a sword, at a mob under a fifth of its health, author): time
 *   slows (the animation's slow middle), the knee into its head, a whole turn in the air, a calm landing; it flies off
 *   dead.</li>
 *   <li><b>The crossbow's living shield</b> (its own key, V): one caught by the left hand; the right button shoots, a
 *   second to load the next (author: "almost as a bow").</li>
 *   <li><b>The slide on the back</b> (Ctrl + W + C + LMB with the crossbow or the bow): a long slide feet first on the
 *   back; one he slides under falls onto him and is shot through the belly, and he is up with a kip; else the slide
 *   dies and he stands.</li>
 *   <li><b>The bow's rain</b> (the bow's ultimate, RMB + G held, the time stop's conditions): they freeze facing him, he
 *   goes up into the air turning and loosing arrows that hang there, lands on one knee, and they all fly at once.</li>
 * </ul>
 */
public final class HeavyMoves {
    /** The slam's hit; the heavy blows' hits; the fling's; the knee's; the belly shot's; the rain's landing and loosing. */
    public static final int SLAM_HIT = 10, FLING_HIT = 4, KNEE_HIT = 12, BELLY_HIT = 3, RAIN_LAND = 46, RAIN_LOOSE = 48;
    public static final int[] HEAVY_HITS = {8, 19, 30};
    /** The slide on the back: the ticks it slides in; one this near over him falls onto him. */
    public static final int SLIDE_FROM = 4, SLIDE_TO = 34;

    // ------------------------------------------------------------------ when

    /** The blow's hit tick of the axe's chain's moves. */
    public static int chainHit(Move m) {
        return switch (m) {
            case AXC_KICK -> 4;
            case AXC_SPIN -> 6;
            default -> 5;
        };
    }

    /** A mob the flying knee may be made at: weak enough (author: a fifth), near. */
    public static boolean kneeTarget(Player p, Entity e) {
        return e instanceof LivingEntity le && e instanceof Mob && le.isAlive() && !Strikes.boss(le) && le.distanceTo(p) <= 8.5f
                && le.getHealth() <= le.getMaxHealth() * MovesConfig.kneeHealth() + 0.01f;
    }

    /** A mob that can be caught by the crossbow's hand: of a man's build, near. */
    public static boolean grabTarget(Player p, Entity e) {
        return e instanceof LivingEntity le && GuardAnswers.catchable(le) && le.isAlive() && le.distanceTo(p) <= 3.5f;
    }

    /** The rain can be made now: a bow, enough enemies about, the bar nearly full, its wait over (the time stop's numbers). */
    public static boolean rainReady(Player p, Stamina s) {
        boolean client = p.level().isClientSide;
        if (!Bow.isBow(p.getMainHandItem()) || s.rainCooldown > (client ? 0 : 5)) return false;
        if (!s.free() && !p.getAbilities().instabuild && s.energy < MovesConfig.maxEnergy() * MovesConfig.timeStopShare() - 0.01f) return false;
        return TimeStop.enemies(p).size() >= MovesConfig.timeStopMin();
    }

    /** The wait of a move of these (Stamina's), -1 for one with none of its own. */
    public static int cooldown(Stamina s, Move m) {
        return switch (m) {
            case AX_SLAM -> s.slamCooldown;
            case AX_HEAVY -> s.heavyCooldown;
            case AXC_OPEN -> s.axeChainCooldown;
            case SW_CHARGE -> s.chargeCooldown;
            case SW_KNEE -> s.kneeCooldown;
            case XB_GRAB -> s.holdCooldown;
            case XB_BACKSLIDE -> s.backSlideCooldown;
            case BW_RAIN -> s.rainCooldown;
            default -> -1;
        };
    }

    /** The client asks for one of these: may it start now? (MoveServer.allowed) */
    public static boolean allowed(Player p, Stamina s, Move m) {
        boolean client = p.level().isClientSide;
        int grace = client ? 0 : 5;
        int cd = cooldown(s, m);
        if (cd > grace) return false;
        return switch (m) {
            case AX_SLAM, AX_HEAVY -> Strikes.axe(p) && s.move == null;
            case AXC_OPEN -> Strikes.axe(p) && s.move == null && Chain.enough(p);
            case SW_CHARGE, SW_KNEE -> Strikes.sword(p) && s.move == null;
            case XB_GRAB -> p.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem && s.move == null;
            case XB_SHOT -> s.move == Move.XB_HOLD;
            case XB_BACKSLIDE -> Skill.Weapon.RANGED.in(p) && (s.move == null || s.move == Move.SLIDE);
            case BW_RAIN -> s.move == null && rainReady(p, s);
            default -> true;
        };
    }

    // ------------------------------------------------------------------ the start

    private static final class Run {
        final List<Integer> targets = new ArrayList<>();
        final List<Integer> arrows = new ArrayList<>();
        final Set<Integer> hit = new HashSet<>();
        Vec3 anchor;
        int next, blow;
    }

    private static final Map<UUID, Run> ON = new HashMap<>();

    /** A move of these let through (MoveServer.started): its wait, and what it sets going. */
    static void started(ServerPlayer p, Stamina s, Move m, int target) {
        switch (m) {
            case AX_SLAM -> s.slamCooldown = s.cooled(MovesConfig.slamCooldownTicks());
            case AX_HEAVY -> s.heavyCooldown = s.cooled(MovesConfig.heavyCooldownTicks());
            case SW_CHARGE -> s.chargeCooldown = s.cooled(MovesConfig.chargeCooldownTicks());
            case SW_KNEE -> {
                s.kneeCooldown = s.cooled(MovesConfig.kneeCooldownTicks());
                s.target = target;
                hold(p.level().getEntity(target));
            }
            case XB_GRAB -> {
                s.holdCooldown = s.cooled(MovesConfig.holdCooldownTicks());
                s.target = target;
            }
            case XB_SHOT -> shoot(p);
            case XB_BACKSLIDE -> s.backSlideCooldown = s.cooled(MovesConfig.backSlideCooldownTicks());
            case AXC_OPEN -> {
                s.axeChainCooldown = s.cooled(MovesConfig.axeChainCooldownTicks());
                s.target = target;
                axeChainStarted(p, s, target);
            }
            case BW_RAIN -> rainStarted(p, s);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ every tick

    static void serverTick(ServerPlayer p, Stamina s) {
        Run run = ON.get(p.getUUID());
        if (run != null && (s.move == null || !(s.move.axeChain() || s.move == Move.TS_BRAKE || s.move == Move.BW_RAIN))) {
            // broken off: the frozen ones as they were, the hanging arrows down
            for (LivingEntity mob : alive(p, run.targets)) MovesNet.mobPose(mob, MobPose.NONE, 0, false);
            for (int id : run.arrows) if (p.level().getEntity(id) instanceof AbstractArrow a) a.setNoGravity(false);
            ON.remove(p.getUUID());
            run = null;
        }
        if (s.move == null) return;
        int t = s.moveTick;
        switch (s.move) {
            case AX_SLAM -> slamTick(p, t);
            case AX_HEAVY -> {
                for (int i = 0; i < HEAVY_HITS.length; i++) if (t == HEAVY_HITS[i]) heavyHit(p, i);
            }
            case SW_CHARGE -> chargeTick(p, s);
            case SW_SKEWER -> skewerTick(p, s);
            case SW_FLING -> flingTick(p, s);
            case SW_KNEE -> kneeTick(p, s);
            case XB_GRAB, XB_HOLD, XB_SHOT -> holdTick(p, s);
            case XB_BACKSLIDE -> backSlideTick(p, s);
            case XB_BELLY -> bellyTick(p, s);
            case AXC_OPEN, AXC_SKULL, AXC_KICK, AXC_SPIN, AXC_UPPER -> {
                if (run != null) axeChainTick(p, s, run);
            }
            case BW_RAIN -> {
                if (run != null) rainTick(p, s, run);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ the axe's slam

    /** The wave: from the hit it runs out a block a tick; each one it gets to is thrown up out of the ground. */
    private static void slamTick(ServerPlayer p, int t) {
        if (t < SLAM_HIT || t > SLAM_HIT + Mth.ceil(MovesConfig.slamRadius())) return;
        ServerLevel level = p.serverLevel();
        double r0 = t - SLAM_HIT, r1 = r0 + 1;
        if (t == SLAM_HIT) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 0.6f);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), MovesSounds.TIME_STOP_HIT.get(), SoundSource.PLAYERS, 1f, 0.6f);
        }
        // the ground thrown up along the ring
        int n = (int) (6 + r1 * 6);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double x = p.getX() + Math.cos(a) * r1, z = p.getZ() + Math.sin(a) * r1;
            BlockState ground = level.getBlockState(BlockPos.containing(x, p.getY() - 0.5, z));
            if (!ground.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, p.getY() + 0.1, z, 3, 0.1, 0.05, 0.1, 0.2);
        }
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(MovesConfig.slamRadius(), 2, MovesConfig.slamRadius()),
                e -> e != p && e.isAlive() && !(e instanceof Player))) {
            double d = Math.sqrt(e.distanceToSqr(p.getX(), e.getY(), p.getZ()));
            if (d < r0 || d >= r1 || d > MovesConfig.slamRadius() || Math.abs(e.getY() - p.getY()) > 2.5) continue;
            e.invulnerableTime = 0;
            e.hurt(p.damageSources().playerAttack(p), MovesConfig.slamDamage());
            Vec3 away = Strikes.flat(e.position().subtract(p.position()));
            away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
            e.setDeltaMovement(away.x * 0.25, 0.75 - 0.25 * d / MovesConfig.slamRadius(), away.z * 0.25);
            e.hurtMarked = true;
            if (GuardAnswers.catchable(e)) MovesNet.mobPose(e, MobPose.FLUNG, 14, false);
            BlockState ground = level.getBlockState(e.blockPosition().below());
            if (!ground.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), e.getX(), e.getY(), e.getZ(), 14, 0.3, 0.1, 0.3, 0.25);
            p.setLastHurtMob(e);
        }
    }

    // ------------------------------------------------------------------ the axe's heavy blows

    private static void heavyHit(ServerPlayer p, int i) {
        ServerLevel level = p.serverLevel();
        Vec3 look = Strikes.flat(p.getViewVector(1f)).normalize();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), MovesSounds.TIME_STOP_SWING.get(), SoundSource.PLAYERS, 1.1f, 0.7f + i * 0.08f);
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(4.0, 1.5, 4.0),
                e -> e != p && e.isAlive() && !(e instanceof Player))) {
            Vec3 to = Strikes.flat(e.position().subtract(p.position()));
            if (to.length() > 4.0 || (to.lengthSqr() > 1.0E-4 && look.dot(to.normalize()) < 0.35)) continue;
            e.invulnerableTime = 0;
            e.hurt(p.damageSources().playerAttack(p), MovesConfig.heavyDamage());
            Strikes.shove(e, to.lengthSqr() < 1.0E-4 ? look : to.normalize(), 0.7);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), MovesSounds.TIME_STOP_HIT.get(), SoundSource.PLAYERS, 1f, 0.75f);
            Vec3 at = e.position().add(0, e.getBbHeight() * 0.6, 0);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.25, 0.3, 0.25, 0.3);
            p.setLastHurtMob(e);
        }
    }

    // ------------------------------------------------------------------ the sword's charge

    /** Running: one in the way (in front, near) is run through. */
    private static void chargeTick(ServerPlayer p, Stamina s) {
        if (s.moveTick < 3) return;
        Vec3 look = Strikes.flat(p.getViewVector(1f)).normalize();
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(1.8, 0.5, 1.8),
                e -> e != p && e instanceof Mob && e.isAlive() && !Strikes.boss(e))) {
            Vec3 to = Strikes.flat(e.position().subtract(p.position()));
            if (to.length() > 1.9 || (to.lengthSqr() > 1.0E-4 && look.dot(to.normalize()) < 0.5)) continue;
            s.start(p, Move.SW_SKEWER, false);
            s.target = e.getId();
            MovesNet.force(p, Move.SW_SKEWER, e.getId());
            if (GuardAnswers.catchable(e)) MovesNet.mobPose(e, MobPose.HOSTAGE, 40, true);
            ServerLevel level = p.serverLevel();
            level.playSound(null, e.getX(), e.getY(), e.getZ(), MovesSounds.TIME_STOP_HIT.get(), SoundSource.PLAYERS, 1f, 0.8f);
            Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.1);
            return;
        }
    }

    /** On the blade, carried in front as he runs; thrown off at the end. */
    private static void skewerTick(ServerPlayer p, Stamina s) {
        if (!(p.level().getEntity(s.target) instanceof LivingEntity mob) || !mob.isAlive()) return;
        carry(p, mob, 1.1);
        if (s.moveTick >= Move.SW_SKEWER.length - 1) {
            s.start(p, Move.SW_FLING, false);
            MovesNet.force(p, Move.SW_FLING, mob.getId());
        }
    }

    private static void flingTick(ServerPlayer p, Stamina s) {
        if (!(p.level().getEntity(s.target) instanceof LivingEntity mob) || !mob.isAlive()) return;
        if (s.moveTick < FLING_HIT) {
            carry(p, mob, 1.1);
            return;
        }
        if (s.moveTick != FLING_HIT) return;
        Vec3 look = Strikes.flat(p.getViewVector(1f)).normalize();
        mob.invulnerableTime = 0;
        mob.hurt(p.damageSources().playerAttack(p), MovesConfig.chargeDamage());
        mob.setDeltaMovement(look.x * 1.2, 0.4, look.z * 1.2);
        mob.hurtMarked = true;
        if (GuardAnswers.catchable(mob)) MovesNet.mobPose(mob, MobPose.THROWN, 40, false);
        p.serverLevel().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2f, 0.7f);
        p.setLastHurtMob(mob);
    }

    /** A mob carried in front of the player, facing him (on the blade), not into walls. */
    private static void carry(ServerPlayer p, LivingEntity mob, double at) {
        float yaw = p.getYRot();
        Vec3 look = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
        Vec3 to = p.position().add(look.scale(at));
        if (!p.level().noCollision(mob, mob.getBoundingBox().move(to.subtract(mob.position())))) to = mob.position();
        mob.moveTo(to.x, Math.max(to.y, p.getY()), to.z, yaw + 180f, 0f);
        mob.setYHeadRot(yaw + 180f);
        mob.setYBodyRot(yaw + 180f);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.hurtMarked = true;
        mob.fallDistance = 0f;
        if (mob instanceof Mob m) {
            m.getNavigation().stop();
            m.setTarget(null);
        }
    }

    // ------------------------------------------------------------------ the flying knee

    private static void kneeTick(ServerPlayer p, Stamina s) {
        p.fallDistance = 0f;
        if (!(p.level().getEntity(s.target) instanceof LivingEntity mob) || !mob.isAlive()) return;
        if (s.moveTick < KNEE_HIT) {
            hold(mob);
            return;
        }
        if (s.moveTick != KNEE_HIT) return;
        ServerLevel level = p.serverLevel();
        Vec3 at = mob.position().add(0, mob.getBbHeight() * 0.85, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 30, 0.3, 0.3, 0.3, 0.6);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.playSound(null, at.x, at.y, at.z, MovesSounds.TIME_STOP_UNWIND.get(), SoundSource.PLAYERS, 1f, 1.4f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2f, 0.6f);
        level.playSound(null, at.x, at.y, at.z, MovesSounds.TIME_STOP_HIT.get(), SoundSource.PLAYERS, 1.2f, 0.6f);
        Vec3 away = Strikes.flat(mob.position().subtract(p.position()));
        away = away.lengthSqr() < 1.0E-4 ? Strikes.flat(p.getViewVector(1f)) : away;
        away = away.normalize();
        GuardAnswers.kill(p, mob);
        // off it goes, far (author: "flies off and is gone")
        mob.setDeltaMovement(away.x * 2.2, 0.8, away.z * 2.2);
        mob.hurtMarked = true;
        if (GuardAnswers.catchable(mob)) MovesNet.mobPose(mob, MobPose.BLASTED, 60, false);
    }

    /** Held where it is (no walking, no aim). */
    private static void hold(Entity e) {
        if (!(e instanceof LivingEntity mob)) return;
        Vec3 v = mob.getDeltaMovement();
        mob.setDeltaMovement(0, Math.min(0, v.y), 0);
        mob.hurtMarked = true;
        if (mob instanceof Mob m) {
            m.getNavigation().stop();
            m.setTarget(null);
        }
    }

    // ------------------------------------------------------------------ the crossbow's living shield

    private static void holdTick(ServerPlayer p, Stamina s) {
        LivingEntity mob = p.level().getEntity(s.target) instanceof LivingEntity le && le.isAlive() ? le : null;
        boolean crossbow = p.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem;
        if (mob == null || !crossbow || (s.move == Move.XB_HOLD && s.moveTick >= MovesConfig.holdTicks())) {
            if (mob != null) MovesNet.mobPose(mob, MobPose.NONE, 0, false);
            s.end(p);
            MovesNet.forceStop(p);
            MoveServer.sync(p, s, true);
            return;
        }
        carryShield(p, mob);
        if (s.move == Move.XB_GRAB && s.moveTick >= Move.XB_GRAB.length - 1) {
            MovesNet.mobPose(mob, MobPose.HOSTAGE, MovesConfig.holdTicks() + 40, false);
            s.start(p, Move.XB_HOLD, false);
            s.target = mob.getId();
            MovesNet.force(p, Move.XB_HOLD, mob.getId());
        } else if (s.move == Move.XB_SHOT && s.moveTick >= Move.XB_SHOT.length - 1) {
            int id = mob.getId();
            s.start(p, Move.XB_HOLD, false);
            s.target = id;
            MovesNet.force(p, Move.XB_HOLD, id);
        }
    }

    /** The shield held in front, its back to the player, at the left hand. */
    private static void carryShield(ServerPlayer p, LivingEntity mob) {
        float yaw = p.getYRot();
        Vec3 look = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
        Vec3 to = p.position().add(look.scale(GuardAnswers.SHIELD_AT));
        if (!p.level().noCollision(mob, mob.getBoundingBox().move(to.subtract(mob.position())))) to = mob.position();
        mob.moveTo(to.x, Math.max(to.y, p.getY()), to.z, yaw, 0f);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.hurtMarked = true;
        mob.fallDistance = 0f;
        if (mob instanceof Mob m) {
            m.getNavigation().stop();
            m.setTarget(null);
        }
    }

    /** A bolt from the crossbow held one-handed: an arrow from the inventory (any, in creative none needed). */
    private static void shoot(ServerPlayer p) {
        if (!p.getAbilities().instabuild) {
            ItemStack ammo = p.getProjectile(p.getMainHandItem());
            if (ammo.isEmpty()) {
                p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 0.6f, 1.6f);
                return;
            }
            ammo.shrink(1);
        }
        Arrow arrow = new Arrow(p.level(), p);
        arrow.setPos(p.getEyePosition().add(p.getViewVector(1f).scale(0.6)).subtract(0, 0.15, 0));
        arrow.shootFromRotation(p, p.getXRot(), p.getYRot(), 0f, 3.15f, 0.5f);
        arrow.setShotFromCrossbow(true);
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        p.level().addFreshEntity(arrow);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1f, 1f);
        p.getMainHandItem().hurtAndBreak(1, p, e -> e.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
    }

    // ------------------------------------------------------------------ the slide on the back

    private static void backSlideTick(ServerPlayer p, Stamina s) {
        if (s.moveTick < SLIDE_FROM || s.moveTick > SLIDE_TO) return;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(1.2, 1.0, 1.2),
                e -> e != p && e instanceof Mob && e instanceof Enemy && e.isAlive() && !Strikes.boss(e))) {
            double flat = Math.sqrt(e.distanceToSqr(p.getX(), e.getY(), p.getZ()));
            if (flat > 1.1) continue;
            s.start(p, Move.XB_BELLY, false);
            s.target = e.getId();
            MovesNet.force(p, Move.XB_BELLY, e.getId());
            // it falls onto him
            e.moveTo(p.getX(), p.getY() + 0.2, p.getZ(), e.getYRot(), 0f);
            e.setDeltaMovement(Vec3.ZERO);
            e.hurtMarked = true;
            if (GuardAnswers.catchable(e)) MovesNet.mobPose(e, MobPose.FALL_FACE, 60, false);
            return;
        }
    }

    private static void bellyTick(ServerPlayer p, Stamina s) {
        if (!(p.level().getEntity(s.target) instanceof LivingEntity mob) || !mob.isAlive()) return;
        if (s.moveTick < BELLY_HIT) {
            hold(mob);
            return;
        }
        if (s.moveTick != BELLY_HIT) return;
        ServerLevel level = p.serverLevel();
        Vec3 at = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), Bow.isBow(p.getMainHandItem()) ? SoundEvents.ARROW_SHOOT : SoundEvents.CROSSBOW_SHOOT,
                SoundSource.PLAYERS, 1f, 1f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1f, 0.6f);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 16, 0.2, 0.4, 0.2, 0.4);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.1);
        GuardAnswers.kill(p, mob);
        mob.setDeltaMovement(0, 0.35, 0);
        mob.hurtMarked = true;
    }

    // ------------------------------------------------------------------ the axe's chain

    private static final Move[][] AXE_TURNS = {{Move.AXC_SPIN}, {Move.AXC_UPPER}, {Move.AXC_SKULL, Move.AXC_KICK}, {Move.AXC_KICK}};

    private static void axeChainStarted(ServerPlayer p, Stamina s, int first) {
        double reach = Chain.FIRST_REACH + MovesConfig.chainRange();
        List<LivingEntity> pool = new ArrayList<>(p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach),
                e -> e != p && Chain.weak(e) && e.distanceTo(p) <= reach && Marks.seen(p, e)));
        LivingEntity at = p.level().getEntity(first) instanceof LivingEntity le && pool.contains(le) ? le : pool.isEmpty() ? null : pool.get(0);
        Run run = new Run();
        run.anchor = p.position();
        while (at != null && run.targets.size() < MovesConfig.chainMax()) {
            run.targets.add(at.getId());
            pool.remove(at);
            LivingEntity from = at;
            at = pool.stream().min((a, b) -> Double.compare(a.distanceToSqr(from), b.distanceToSqr(from))).orElse(null);
        }
        if (run.targets.isEmpty()) return;
        int i = 0;
        for (int id : run.targets) {
            if (p.level().getEntity(id) instanceof LivingEntity mob) MovesNet.mobPose(mob, TimeStop.FROZEN[i++ % TimeStop.FROZEN.length], 400, false);
        }
        // the first: the axe into its skull now (the blow the client began), the kick after it
        run.next = 0;
        run.blow = 0;
        ON.put(p.getUUID(), run);
        LivingEntity mob = (LivingEntity) p.level().getEntity(run.targets.get(0));
        TimeStop.go(p, front(p, mob));
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), MovesSounds.TIME_STOP_SLOW.get(), SoundSource.PLAYERS, 1.2f, 1.25f);
    }

    /** The blows on mob {@code n}: the first gets the skull and the kick, the others a variation each. */
    private static Move[] turnsOf(int n) {
        return n == 0 ? new Move[]{Move.AXC_OPEN, Move.AXC_KICK} : AXE_TURNS[(n - 1) % AXE_TURNS.length];
    }

    private static void axeChainTick(ServerPlayer p, Stamina s, Run run) {
        // the others held frozen
        for (LivingEntity mob : alive(p, run.targets)) if (mob.getId() != s.target || s.moveTick < chainHit(s.move)) hold(mob);
        LivingEntity mob = p.level().getEntity(s.target) instanceof LivingEntity le && le.isAlive() ? le : null;
        Move[] turns = turnsOf(run.next);
        boolean last = run.blow >= turns.length - 1;
        if (mob != null && s.moveTick == chainHit(s.move)) chainBlow(p, s.move, mob, last);
        if (s.moveTick < s.move.length - 1) return;
        // the next blow on it, or a dash to the next one
        if (!last && mob != null) {
            run.blow++;
            Move m = turns[run.blow];
            s.start(p, m, false);
            s.target = mob.getId();
            MovesNet.force(p, m, mob.getId());
            return;
        }
        while (++run.next < run.targets.size()) {
            LivingEntity next = p.level().getEntity(run.targets.get(run.next)) instanceof LivingEntity le && le.isAlive() ? le : null;
            if (next == null) continue;
            run.blow = 0;
            Move m = turnsOf(run.next)[0];
            Vec3 from = p.position(), to = front(p, next);
            s.start(p, m, false);
            s.target = next.getId();
            MovesNet.force(p, m, next.getId());
            TimeStop.go(p, to);
            TimeStop.flickerFx(p, from, to);
            return;
        }
        // all done: on his feet where the last fell
        ON.remove(p.getUUID());
        for (LivingEntity left : alive(p, run.targets)) MovesNet.mobPose(left, MobPose.NONE, 0, false);
        s.end(p);
        MovesNet.forceStop(p);
        MoveServer.sync(p, s, true);
    }

    /** A blow of the axe's chain lands: the skull's holds it; the last on it throws it off dead, the way the blow sends it. */
    private static void chainBlow(ServerPlayer p, Move m, LivingEntity mob, boolean last) {
        ServerLevel level = p.serverLevel();
        Vec3 at = mob.position().add(0, mob.getBbHeight() * 0.7, 0);
        level.playSound(null, at.x, at.y, at.z, MovesSounds.TIME_STOP_HIT.get(), SoundSource.PLAYERS, 1.1f, 0.75f);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 16, 0.25, 0.3, 0.25, 0.4);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.1);
        if (!last) {
            if (GuardAnswers.catchable(mob)) MovesNet.mobPose(mob, MobPose.CAUGHT, 14, false);
            return;
        }
        Vec3 away = Strikes.flat(mob.position().subtract(p.position()));
        away = away.lengthSqr() < 1.0E-4 ? Strikes.flat(p.getViewVector(1f)) : away;
        away = away.normalize();
        GuardAnswers.kill(p, mob);
        double out = m == Move.AXC_UPPER ? 0.4 : m == Move.AXC_SPIN ? 1.6 : 1.3, up = m == Move.AXC_UPPER ? 1.2 : 0.45;
        mob.setDeltaMovement(away.x * out, up, away.z * out);
        mob.hurtMarked = true;
        if (GuardAnswers.catchable(mob)) MovesNet.mobPose(mob, m == Move.AXC_UPPER ? MobPose.THROWN : MobPose.BLASTED, 60, false);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2f, 0.6f);
    }

    /** Where he stands for a blow: in front of it, face to face, not in a wall. */
    private static Vec3 front(ServerPlayer p, LivingEntity mob) {
        Vec3 d = Strikes.flat(p.position().subtract(mob.position()));
        double base = d.lengthSqr() < 1.0E-4 ? 0 : Math.atan2(d.z, d.x);
        double reach = mob.getBbWidth() / 2 + 1.0;
        for (int i = 0; i < 8; i++) {
            double a = base + i * Math.PI / 4;
            Vec3 at = mob.position().add(Math.cos(a) * reach, 0, Math.sin(a) * reach);
            if (p.level().noCollision(p, p.getBoundingBox().move(at.subtract(p.position())))) return at;
        }
        return p.position();
    }

    // ------------------------------------------------------------------ the bow's rain

    private static void rainStarted(ServerPlayer p, Stamina s) {
        if (!s.free() && !p.getAbilities().instabuild) s.energy = Math.max(0f, s.energy - MovesConfig.maxEnergy() * MovesConfig.timeStopShare());
        s.rainCooldown = s.cooled(MovesConfig.rainCooldownTicks());
        Run run = new Run();
        run.anchor = p.position();
        int i = 0;
        for (LivingEntity mob : TimeStop.enemies(p)) {
            run.targets.add(mob.getId());
            // turned to face him, each in a pose of its own
            float face = TimeStop.facing(mob.position(), p.position());
            mob.setYRot(face);
            mob.setYBodyRot(face);
            mob.setYHeadRot(face);
            MovesNet.mobPose(mob, TimeStop.FROZEN[i++ % TimeStop.FROZEN.length], Move.BW_RAIN.length + 40, false);
        }
        ON.put(p.getUUID(), run);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), MovesSounds.TIME_STOP_SLOW.get(), SoundSource.PLAYERS, 1.2f, 1.1f);
    }

    private static void rainTick(ServerPlayer p, Stamina s, Run run) {
        p.fallDistance = 0f;
        int t = s.moveTick;
        List<LivingEntity> mobs = alive(p, run.targets);
        for (LivingEntity mob : mobs) hold(mob);
        // in the air, turning: an arrow loosed every other tick, left hanging round him
        if (t >= 12 && t <= 40 && t % 2 == 0 && !mobs.isEmpty() && run.arrows.size() < Math.max(6, mobs.size() * 3)) {
            LivingEntity aim = mobs.get(run.arrows.size() % mobs.size());
            double a = t * 0.9;
            Vec3 at = p.position().add(Math.cos(a) * 1.6, 1.2 + (t % 6) * 0.15, Math.sin(a) * 1.6);
            Vec3 to = aim.getEyePosition().subtract(at).normalize();
            Arrow arrow = new Arrow(p.level(), p);
            arrow.setPos(at);
            arrow.setNoGravity(true);
            arrow.setDeltaMovement(to.scale(0.002));
            arrow.setYRot((float) (Mth.atan2(to.x, to.z) * Mth.RAD_TO_DEG));
            arrow.setXRot((float) (Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG));
            arrow.setBaseDamage(MovesConfig.rainDamage() / 2.5);
            arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            arrow.setCritArrow(true);
            p.level().addFreshEntity(arrow);
            run.arrows.add(arrow.getId());
            run.hit.add(aim.getId());
            p.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.7f, 1.2f + (t % 4) * 0.05f);
        }
        if (t == RAIN_LAND) {
            BlockState ground = p.level().getBlockState(p.blockPosition().below());
            if (!ground.isAir()) p.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), p.getX(), p.getY(), p.getZ(), 30, 0.6, 0.1, 0.6, 0.2);
            p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_BIG_FALL, SoundSource.PLAYERS, 1f, 0.7f);
        }
        if (t == RAIN_LOOSE) {
            // all at once, each at its own
            int k = 0;
            for (int id : run.arrows) {
                if (!(p.level().getEntity(id) instanceof AbstractArrow arrow) || mobs.isEmpty()) continue;
                LivingEntity aim = mobs.get(k++ % mobs.size());
                Vec3 to = aim.getEyePosition().subtract(0, 0.3, 0).subtract(arrow.position()).normalize();
                arrow.setNoGravity(false);
                arrow.setDeltaMovement(to.scale(2.5));
                arrow.hurtMarked = true;
            }
            p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), MovesSounds.TIME_STOP_UNWIND.get(), SoundSource.PLAYERS, 1.2f, 1.2f);
            for (LivingEntity mob : mobs) MovesNet.mobPose(mob, MobPose.NONE, 0, false);
        }
        if (t >= Move.BW_RAIN.length - 1) ON.remove(p.getUUID());
    }

    private static List<LivingEntity> alive(ServerPlayer p, List<Integer> ids) {
        List<LivingEntity> out = new ArrayList<>();
        for (int id : ids) if (p.level().getEntity(id) instanceof LivingEntity e && e.isAlive()) out.add(e);
        return out;
    }

    /** This mob is frozen in one of these (the axe's chain, the rain): it strikes no one. */
    static boolean frozen(LivingEntity e) {
        if (ON.isEmpty()) return false;
        for (Run run : ON.values()) if (run.targets.contains(e.getId())) return true;
        return false;
    }

    private HeavyMoves() {}
}
