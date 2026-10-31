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
 * The sword's turn (sword_spin; author, 2026-10-30: "on G a sword turns once round on the spot, fast,
 * the sword held out turned flat, as in the attack out of a slide or the axe's whirlwind"; after his
 * video: "no stun, he just turns round and strikes in the moment; G held, he may move any way and
 * makes up to 3 turns, for more energy"). A short wind-up to the right, a turn to the left from 0.1 to
 * 0.4 s with the blade out flat, its edge leading; G held, up to two more of 0.25 s each (each for
 * MovesConfig.costSpinExtra); then the ending (SPIN_END, or the animation's own after the third). The
 * blade cuts every mob within reach once a turn, as it passes it. The keys walk the player meanwhile.
 */
public final class Spin {
    /** The turns: the first from 0.1 s to 0.4 s, then 5 ticks each (0.25 s; round6.SWORD). */
    public static final int SPIN_FROM = 2;
    public static final int[] TURN_ENDS = {8, 13, 18};
    /** Turns at most, G held. */
    public static final int MAX_TURNS = 3;
    /** The animation's own ending (after the third turn) starts here; SPIN_END is that ending alone. */
    public static final int STOP = 17;
    /** Where the blade points at the first turn's start and how far round it goes in that turn (degrees from
     *  the facing, Minecraft's way: +90 is the right): wound up 40 degrees to the right, the arm out to the
     *  right, then 400 degrees to the left; a whole turn each after it. */
    static final float BLADE_FROM = 130f, FIRST_SWEEP = 400f;
    /** How hard the blade throws the mobs out from the player. [default] */
    static final double THROW = 0.3;

    /** The turn (0-based) the sword is in at this tick, -1 before the first or after the last. */
    public static int turn(int tick) {
        if (tick <= SPIN_FROM) return -1;
        for (int k = 0; k < TURN_ENDS.length; k++) if (tick <= TURN_ENDS[k]) return k;
        return -1;
    }

    /** The turns go on into another one or end (SPIN_END in its place) at this tick, a tick before the turn would end. */
    public static boolean decides(int tick) {
        return tick == TURN_ENDS[0] - 1 || tick == TURN_ENDS[1] - 1;
    }

    /** Where the blade points at this tick (degrees from the facing, Minecraft's way). */
    static float blade(int tick) {
        if (tick <= TURN_ENDS[0]) return BLADE_FROM - FIRST_SWEEP * (tick - SPIN_FROM) / (TURN_ENDS[0] - SPIN_FROM);
        float after = 360f / (TURN_ENDS[1] - TURN_ENDS[0]);
        return BLADE_FROM - FIRST_SWEEP - after * (tick - TURN_ENDS[0]);
    }

    static void started(ServerPlayer p, Stamina s) {
        s.spinCooldown = s.cooled(MovesConfig.swordSpinCooldownTicks());
        s.attackYaw = p.getYRot();
        s.whirlHits.clear();
    }

    /** Every server tick of the turns: the blade meets whoever is within reach, as it sweeps past them, once a turn. */
    static void serverTick(ServerPlayer p, Stamina s) {
        if (s.move != Move.SWORD_SPIN) return;
        int turn = turn(s.moveTick);
        if (turn < 0) return;
        ServerLevel level = p.serverLevel();
        int from = turn == 0 ? SPIN_FROM : TURN_ENDS[turn - 1];
        // a turn more than the first: G held, more energy (a tick into it: the client's ending may come that late)
        if (turn > 0 && s.moveTick == from + 1) s.spend(MovesConfig.costSpinExtra(), p);
        if (s.moveTick == from + 1) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 1.15f + 0.1f * turn);
        }
        // the arc the blade swept this tick, from where it was to where it is (it turns to the left: the angle falls);
        // the player may turn meanwhile (the keys walk them, the mouse turns them): the facing now
        float was = blade(s.moveTick - 1), now = blade(s.moveTick);
        float yaw = p.getYRot();
        double reach = MovesConfig.swordSpinReach();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach + 1, 1.0, reach + 1),
                e -> e != p && e.isAlive() && e.isPickable() && !p.isAlliedTo(e) && !(e instanceof ArmorStand))) {
            Integer last = s.whirlHits.get(e.getId());
            if (last != null && last >= turn) continue;
            Vec3 to = Strikes.flat(e.position().subtract(p.position()));
            if (to.length() - e.getBbWidth() / 2 > reach) continue;
            if (to.lengthSqr() > 0.09 && !swept(angle(yaw, to), now, was)) continue;
            s.whirlHits.put(e.getId(), turn);
            e.invulnerableTime = 0;
            Strikes.blow(p, e, MovesConfig.swordSpinDamage(), false);
            Strikes.shove(e, to.lengthSqr() > 1.0E-4 ? to : Move.SWORD_SPIN.direction(yaw), THROW);
            Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    /** Where {@code to} lies from the player facing {@code yaw}: degrees, Minecraft's way (+90 the right). */
    static float angle(float yaw, Vec3 to) {
        float at = (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
        return Mth.wrapDegrees(at - yaw);
    }

    /** {@code a} (degrees) lies within the arc from {@code lo} up to {@code hi}, whole turns aside. */
    static boolean swept(float a, float lo, float hi) {
        float k = (float) Math.ceil((lo - a) / 360f);
        return a + 360f * k <= hi;
    }

    private Spin() {}
}
