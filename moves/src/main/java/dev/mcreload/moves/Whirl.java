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
    /** The combo's three blows (axe_triple: 0.70, 1.33, 1.95 s), its reach (blocks) and its fewest and most mobs. [default reach] */
    public static final int[] TRIPLE_HITS = {14, 27, 39};
    public static final double TRIPLE_REACH = 3.5;
    public static final int TRIPLE_FEWEST = 3, TRIPLE_MOST = 6;

    /** The mobs the combo may take (alive, not a boss, not allied), the nearest first, TRIPLE_MOST at most. */
    public static java.util.List<LivingEntity> tripleTargets(net.minecraft.world.entity.player.Player p) {
        java.util.List<LivingEntity> out = new java.util.ArrayList<>(p.level().getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(TRIPLE_REACH + 1, 1.5, TRIPLE_REACH + 1),
                e -> e instanceof net.minecraft.world.entity.Mob && e != p && e.isAlive() && !Strikes.boss(e) && !p.isAlliedTo(e)
                        && Strikes.flat(e.position().subtract(p.position())).length() - e.getBbWidth() / 2 <= TRIPLE_REACH));
        out.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        return out.size() > TRIPLE_MOST ? out.subList(0, TRIPLE_MOST) : out;
    }

    /** Every server tick of the combo: at each blow a quarter of each one's health, through its armour. */
    static void tripleTick(net.minecraft.server.level.ServerPlayer p, Stamina s) {
        if (s.move != Move.AXE_TRIPLE) return;
        int k = -1;
        for (int i = 0; i < TRIPLE_HITS.length; i++) if (TRIPLE_HITS[i] == s.moveTick) k = i;
        if (k < 0) return;
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 0.8f + 0.1f * k);
        float share = MovesConfig.axeTripleShare() / TRIPLE_HITS.length;
        // the mobs of the first blow are the combo's, the next blows find them where they are (each blow shoved them out of reach)
        if (k == 0) {
            s.whirlHits.clear();
            for (LivingEntity e : tripleTargets(p)) s.whirlHits.put(e.getId(), 0);
        }
        for (int id : s.whirlHits.keySet()) {
            if (!(level.getEntity(id) instanceof LivingEntity e) || !e.isAlive() || e.distanceTo(p) > TRIPLE_REACH + 4) continue;
            e.invulnerableTime = 0;
            Vec3 was = e.getDeltaMovement();
            e.hurt(p.damageSources().playerAttack(p), Plunge.throughArmour(e, e.getMaxHealth() * share));
            e.setDeltaMovement(was.x * 0.3, Math.min(was.y, 0.1), was.z * 0.3);     // held about the player, not knocked away
            e.hurtMarked = true;
            Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
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
