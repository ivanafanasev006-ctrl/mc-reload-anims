package dev.mcreload.moves;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The plunge (author, 2026-10-29, fifth message: "falling with a sword in the hands he flies and
 * drives it into the ground, it stuns him a little, a short time, he pulls the sword out of the
 * ground; it all takes 20 % off the landing's damage; the same with an axe"). Falling far enough to
 * be hurt (MovesConfig.plungeFrom) with a sword or an axe in hand, R not held for the tuck, the blade
 * is held point down (plunge_fall); landing drives it in (plunge_land): down on one knee, a short
 * stun, the blade heaved out at 0.68 s, up by 1.0 s.
 */
public final class Plunge {
    /** The blade is out of the ground (0.68 s): the stun is over, moving breaks off the rest. */
    public static final int STAND = 14;
    /** The blade goes into the ground (0.12 s): the sound, the dirt. */
    public static final int STAB = 2;

    /** The blade may be held ready: falling far enough to be hurt, a sword or an axe in hand. */
    public static boolean canHold(net.minecraft.world.entity.player.Player p) {
        return !p.onGround() && p.fallDistance >= MovesConfig.plungeFrom() && Strikes.finisherWeapon(p) && !p.isInWater()
                && !p.onClimbable() && !p.isFallFlying() && !p.getAbilities().flying;
    }

    /** Landing with the blade held ready, or just driven in (the client may start it a tick before this end sees the landing). */
    @SubscribeEvent
    public static void fall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Stamina s = Stamina.of(p);
        boolean ready = s.move == Move.PLUNGE_FALL || (s.move == Move.PLUNGE && s.moveTick <= 4);
        if (!ready) return;
        event.setDamageMultiplier(event.getDamageMultiplier() * (1f - MovesConfig.plungeCut()));
    }

    /**
     * Every server tick: the blade held ready that reaches the ground without the landing (the client
     * never asked) ends there; the blade going into the ground sounds and throws up dirt, and wears.
     */
    static void serverTick(ServerPlayer p, Stamina s) {
        if (s.move == Move.PLUNGE_FALL) {
            if ((p.onGround() || p.isInWater()) && s.moveTick > 2) {
                if (++s.plungeLanded > 10) {
                    s.end(p);
                    dev.mcreload.moves.net.MovesNet.stop(p);
                }
            } else {
                s.plungeLanded = 0;
            }
            return;
        }
        if (s.move != Move.PLUNGE || s.moveTick != STAB) return;
        ServerLevel level = p.serverLevel();
        float r = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 at = p.position().add(-Mth.sin(r) * 0.65, 0, Mth.cos(r) * 0.65);
        BlockPos below = BlockPos.containing(at.x, p.getY() - 0.2, at.z);
        BlockState ground = level.getBlockState(below);
        level.playSound(null, at.x, at.y, at.z, ground.getSoundType(level, below, p).getBreakSound(), SoundSource.PLAYERS, 1f, 0.8f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8f, 0.6f);
        if (!ground.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, p.getY() + 0.1, at.z,
                18, 0.2, 0.05, 0.2, 0.15);
        p.getMainHandItem().hurtAndBreak(1, p, e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
    }

    private Plunge() {}
}
