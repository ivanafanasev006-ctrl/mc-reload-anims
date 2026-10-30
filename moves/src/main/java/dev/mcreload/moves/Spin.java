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
 * the sword held out turned flat, as in the attack out of a slide or the axe's whirlwind"). A short
 * wind-up to the right, one turn to the left in 0.3 s with the blade out flat, its edge leading, and
 * the stance again by 0.8 s. The blade cuts every mob within reach once, as it passes it.
 */
public final class Spin {
    /** The turn: from 0.1 s to 0.4 s (Blockbench: SPIN_FROM, SPIN_TO in round6.py). */
    public static final int SPIN_FROM = 2, SPIN_TO = 8;
    /** Where the blade points at the turn's start and how far round it goes (degrees from the facing, Minecraft's way:
     *  +90 is the right): wound up 40 degrees to the right, the arm out to the right, then 400 degrees to the left. */
    static final float BLADE_FROM = 130f, SWEEP = 400f;
    /** How hard the blade throws the mobs out from the player. [default] */
    static final double THROW = 0.3;

    static void started(ServerPlayer p, Stamina s) {
        s.spinCooldown = MovesConfig.swordSpinCooldownTicks();
        s.attackYaw = p.getYRot();
        s.whirlHits.clear();
    }

    /** Every server tick of the turn: the blade meets whoever is within reach, as it sweeps past them. */
    static void serverTick(ServerPlayer p, Stamina s) {
        if (s.move != Move.SWORD_SPIN || s.moveTick <= SPIN_FROM || s.moveTick > SPIN_TO) return;
        ServerLevel level = p.serverLevel();
        if (s.moveTick == SPIN_FROM + 1) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 1.15f);
        }
        float span = SPIN_TO - SPIN_FROM;
        // the arc the blade swept this tick, from where it was to where it is (it turns to the left: the angle falls)
        float was = BLADE_FROM - SWEEP * (s.moveTick - 1 - SPIN_FROM) / span;
        float now = BLADE_FROM - SWEEP * (s.moveTick - SPIN_FROM) / span;
        double reach = MovesConfig.swordSpinReach();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach + 1, 1.0, reach + 1),
                e -> e != p && e.isAlive() && e.isPickable() && !p.isAlliedTo(e) && !(e instanceof ArmorStand))) {
            if (s.whirlHits.containsKey(e.getId())) continue;
            Vec3 to = Strikes.flat(e.position().subtract(p.position()));
            if (to.length() - e.getBbWidth() / 2 > reach) continue;
            if (to.lengthSqr() > 0.09 && !swept(angle(s.attackYaw, to), now, was)) continue;
            s.whirlHits.put(e.getId(), 0);
            e.invulnerableTime = 0;
            Strikes.blow(p, e, MovesConfig.swordSpinDamage(), false);
            Strikes.shove(e, to.lengthSqr() > 1.0E-4 ? to : Move.SWORD_SPIN.direction(s.attackYaw), THROW);
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
