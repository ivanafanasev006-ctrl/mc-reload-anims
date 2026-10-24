package com.example.bcaddon.client;

import com.example.bcaddon.AddonConfig;
import com.example.bcaddon.BcAddon;
import com.example.bcaddon.combat.Heavy;
import net.bettercombat.api.client.AttackRangeExtensions;
import net.bettercombat.api.client.BetterCombatClientEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;

/** Better Combat's client API: the two attack events and the attack range extension. */
final class ClientHooks {

    static void register() {
        // Wind-up: the attack animation has just started, the hit comes `upswing` of the swing later.
        BetterCombatClientEvents.ATTACK_START.register((player, hand) -> {
            if (Heavy.isHeld(player)) player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 0.55f);
        });

        // The hit: Better Combat found these targets on this client and sent their ids to the server.
        // Purely visual reactions belong here (they are free and instant); anything that changes the
        // game must be decided on the server (see combat.BcAttacks).
        BetterCombatClientEvents.ATTACK_HIT.register((player, hand, targets, cursorTarget) -> {
            if (!Heavy.isHeld(player)) return;
            for (Entity target : targets) {
                for (int i = 0; i < 8; i++) {
                    player.level().addParticle(ParticleTypes.CRIT, target.getRandomX(0.6), target.getRandomY(), target.getRandomZ(0.6),
                            (player.getRandom().nextDouble() - 0.5) * 0.4, 0.2, (player.getRandom().nextDouble() - 0.5) * 0.4);
                }
            }
        });

        // Reach: asked on the client whenever Better Combat looks for targets. The candidates are
        // gathered before this is applied, within twice the weapon range, so keep bonuses small.
        AttackRangeExtensions.register(context -> new AttackRangeExtensions.Modifier(
                Heavy.isHeld(context.player()) ? AddonConfig.HEAVY_RANGE_BONUS.get() : 0.0,
                AttackRangeExtensions.Operation.ADD));

        BcAddon.LOG.info("[bcaddon] Better Combat client hooks registered");
    }

    private ClientHooks() {}
}
