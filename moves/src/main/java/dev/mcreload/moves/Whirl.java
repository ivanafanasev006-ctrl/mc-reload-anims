package dev.mcreload.moves;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/**
 * The axe whirlwind (axe_whirlwind; author, 2026-10-29, fifth message: "three turns round, flying
 * forward like a top, hurting everything round like a berserker; not very fast, not sluggish; 45 s
 * before the next one"; 2026-10-30: on G; after his video: "G held: up to 5 turns, and it goes where
 * the camera looks, only forward, A, S, D do nothing"). The wind-up to 0.25 s, the first turn to
 * 0.8 s, then 0.45 s a turn (the client carries the player forward meanwhile, Whirl.SPEED); each turn
 * the axe hits every mob within reach once with its blow times MovesConfig.whirlDamage and throws it
 * out. Three turns, then the ending (WHIRL_END) unless G is held; held, up to five, each more one
 * for MovesConfig.costWhirlExtra.
 */
public final class Whirl {
    /** The turns: the first from 0.25 s to 0.8 s, then 9 ticks each (0.45 s; round6.AXE). */
    public static final int SPIN_FROM = 5;
    public static final int[] TURN_ENDS = {16, 25, 34, 43, 52};
    /** Turns before the ending without G held; turns at most with it. */
    public static final int TURNS = 3, MAX_TURNS = 5;
    /** The whirlwind's own ending (after the fifth turn) starts here; WHIRL_END is that ending alone. */
    public static final int STOP = 51;
    /** Forward speed while turning, blocks a tick (3 a second). [default] */
    public static final float SPEED = 0.15f;
    /** How hard each turn throws the mobs out from the player: a little, so the next turn still finds them. [default] */
    public static final double THROW = 0.2;

    /** The turn (0-based) the whirlwind is in at this tick, -1 before the first or after the last. */
    /** G with an axe: this many turns, not held for (author, 2026-11-10: "the old G+ goes on G, 4 turns"). */
    public static final int G_TURNS = 4;
    /**
     * The combo (author, 2026-11-10, evening: "EACH one 70 % of his health", the animation built anew from his example,
     * round19.py; 20:00: each blow a different one, "only 3 of the 3 to 6 were hit"): a blow for each mob, one after another,
     * a turning one and a chop in turn. Its reach (blocks: the mobs it takes, from where the player stands as each blow
     * starts) and its fewest and most mobs. [default reach]
     */
    public static final double TRIPLE_REACH = 5.0;
    public static final int TRIPLE_FEWEST = 3, TRIPLE_MOST = 6;

    /** The tick of this blow of the combo where the axe meets its mob (round19.py: its head passing in front, the fastest). */
    public static int comboHit(Move m) {
        return switch (m) {
            case AXE_TRIPLE, AXE_COMBO_2 -> 7;
            case AXE_COMBO_CHOP, AXE_COMBO_DIAG -> 6;
            case AXE_COMBO_3 -> 10;
            default -> -1;
        };
    }

    /** How far from its mob the player stands for this blow (the axe's head that far out then, round19.py). */
    public static double comboStand(Move m) {
        return switch (m) {
            case AXE_COMBO_CHOP, AXE_COMBO_2 -> 1.2;
            case AXE_COMBO_3 -> 1.4;
            case AXE_COMBO_DIAG -> 1.5;
            default -> 1.8;
        };
    }

    /** How far to the right of the player's look the axe comes down in this blow (degrees): its mob put there. */
    public static float comboAim(Move m) {
        return m == Move.AXE_COMBO_DIAG ? 65f : 0f;
    }

    /** The blow after this one: a chop and a turning one in turn (author: "the poses taking turns"). */
    public static Move comboNext(Move m) {
        return switch (m) {
            case AXE_TRIPLE -> Move.AXE_COMBO_CHOP;
            case AXE_COMBO_CHOP -> Move.AXE_COMBO_2;
            case AXE_COMBO_2 -> Move.AXE_COMBO_DIAG;
            case AXE_COMBO_DIAG -> Move.AXE_COMBO_3;
            default -> Move.AXE_COMBO_CHOP;
        };
    }

    /** A mob the combo may take: alive, not a boss, not allied. */
    public static boolean comboMob(net.minecraft.world.entity.player.Player p, net.minecraft.world.entity.Entity e) {
        return e instanceof net.minecraft.world.entity.Mob m && m.isAlive() && !Strikes.boss(m) && !p.isAlliedTo(m);
    }

    /** The mobs the combo may take, the nearest first, TRIPLE_MOST at most. */
    public static java.util.List<LivingEntity> tripleTargets(net.minecraft.world.entity.player.Player p) {
        return comboMobs(p, TRIPLE_REACH);
    }

    /** The mobs the combo may take within {@code reach} (to their sides), the nearest first, TRIPLE_MOST at most. */
    public static java.util.List<LivingEntity> comboMobs(net.minecraft.world.entity.player.Player p, double reach) {
        java.util.List<LivingEntity> out = new java.util.ArrayList<>(p.level().getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(reach + 1, 1.5, reach + 1),
                e -> comboMob(p, e) && Strikes.flat(e.position().subtract(p.position())).length() - e.getBbWidth() / 2 <= reach));
        out.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        return out.size() > TRIPLE_MOST ? out.subList(0, TRIPLE_MOST) : out;
    }

    /** The mob a blow of the combo is made at (the client named it): one it may take, near (the player steps to it). */
    public static boolean comboValid(net.minecraft.world.entity.player.Player p, net.minecraft.world.entity.Entity e) {
        return comboMob(p, e) && Strikes.flat(e.position().subtract(p.position())).length() <= TRIPLE_REACH + 3;
    }

    /** Every server tick of the combo: the blow's mob held where it is until the axe meets it, then 70 % of its health off. */
    static void tripleTick(net.minecraft.server.level.ServerPlayer p, Stamina s) {
        if (s.move == null || !s.move.axeCombo()) return;
        if (!(p.level().getEntity(s.target) instanceof LivingEntity e) || !e.isAlive()) return;
        int hit = comboHit(s.move);
        if (s.moveTick < hit) {
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(0, Math.min(v.y, 0), 0);
            e.hurtMarked = true;
            if (e instanceof net.minecraft.world.entity.Mob m) m.getNavigation().stop();
            return;
        }
        if (s.moveTick != hit || e.distanceTo(p) > TRIPLE_REACH + 3) return;
        ServerLevel level = p.serverLevel();
        e.invulnerableTime = 0;
        Vec3 was = e.getDeltaMovement();
        e.hurt(p.damageSources().playerAttack(p), Plunge.throughArmour(e, e.getMaxHealth() * MovesConfig.axeTripleShare()));
        e.setDeltaMovement(was.x * 0.3, Math.min(was.y, 0.1), was.z * 0.3);     // not knocked away: the axe goes on to the next
        e.hurtMarked = true;
        Vec3 at = e.position().add(0, e.getBbHeight() * 0.6, 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1f, 0.8f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 0.7f);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.3);
        p.getMainHandItem().hurtAndBreak(1, p, x -> x.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
        p.setLastHurtMob(e);
        s.combatTicks = MovesConfig.combatTicks();
    }

    public static int turn(int tick) {
        if (tick < SPIN_FROM) return -1;
        for (int k = 0; k < TURN_ENDS.length; k++) if (tick < TURN_ENDS[k]) return k;
        return -1;
    }

    /**
     * The whirlwind goes on into another turn or ends (WHIRL_END in its place) at this tick, a tick
     * before the turn would end: after the third and the fourth.
     */
    public static boolean decides(int tick) {
        return tick == TURN_ENDS[TURNS - 1] - 1 || tick == TURN_ENDS[TURNS] - 1;
    }

    static void started(ServerPlayer p, Stamina s) {
        s.whirlCooldown = s.cooled(MovesConfig.whirlCooldownTicks());
        s.whirlHits.clear();
    }

    /** Every server tick of the whirlwind: the axe meets whoever is within reach, once a turn. */
    static void serverTick(ServerPlayer p, Stamina s) {
        if (s.move != Move.WHIRL) return;
        int turn = turn(s.moveTick);
        if (turn < 0) return;
        ServerLevel level = p.serverLevel();
        int from = turn == 0 ? SPIN_FROM : TURN_ENDS[turn - 1];
        // a turn more than the three: G held, more energy (a tick into it: the client's ending may come that late)
        if (turn >= TURNS && s.moveTick == from + 1) s.spend(MovesConfig.costWhirlExtra(), p);
        if (s.moveTick == from) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 0.7f + 0.1f * turn);
        }
        double reach = MovesConfig.whirlReach();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach + 1, 1.0, reach + 1),
                e -> e != p && e.isAlive() && e.isPickable() && !p.isAlliedTo(e) && !(e instanceof ArmorStand))) {
            Vec3 to = Strikes.flat(e.position().subtract(p.position()));
            if (to.length() - e.getBbWidth() / 2 > reach) continue;
            Integer last = s.whirlHits.get(e.getId());
            if (last != null && last >= turn) continue;
            s.whirlHits.put(e.getId(), turn);
            e.invulnerableTime = 0;
            Strikes.blow(p, e, MovesConfig.whirlDamage(), false);
            Strikes.shove(e, to.lengthSqr() > 1.0E-4 ? to : new Vec3(-Mth.sin(p.getYRot() * Mth.DEG_TO_RAD), 0, Mth.cos(p.getYRot() * Mth.DEG_TO_RAD)), THROW);
            Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    private Whirl() {}
}
