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
 * The axe whirlwind (axe_whirlwind; author, 2026-10-29, fifth message: "an axe in the hands, LMB +
 * S held, then RMB held: three turns round, flying forward like a top, hurting everything round like
 * a berserker; not very fast, not sluggish; the turns go by themselves once the keys are down,
 * letting go of LMB or RMB stops it; 45 s before the next one"). The wind-up to 0.25 s, three turns
 * of 0.5 s (the client carries the player forward meanwhile, Whirl.SPEED), each turn the axe hits
 * every mob within reach once with its blow times MovesConfig.whirlDamage and throws it out.
 */
public final class Whirl {
    /** The turns: from 0.25 s to 1.75 s, half a second each. */
    public static final int SPIN_FROM = 5, SPIN_TO = 35, TURN = 10;
    /** Forward speed while turning, blocks a tick (3 a second: 4.5 blocks over the three turns). [default] */
    public static final float SPEED = 0.15f;
    /** How hard each turn throws the mobs out from the player: a little, so the next turn still finds them. [default] */
    public static final double THROW = 0.2;

    static void started(ServerPlayer p, Stamina s) {
        s.whirlCooldown = MovesConfig.whirlCooldownTicks();
        s.whirlHits.clear();
    }

    /** Every server tick of the whirlwind: the axe meets whoever is within reach, once a turn. */
    static void serverTick(ServerPlayer p, Stamina s) {
        if (s.move != Move.WHIRL || s.moveTick < SPIN_FROM || s.moveTick >= SPIN_TO) return;
        int turn = (s.moveTick - SPIN_FROM) / TURN;
        ServerLevel level = p.serverLevel();
        if ((s.moveTick - SPIN_FROM) % TURN == 0) {
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
