package com.example.bcaddon.combat;

import com.example.bcaddon.AddonConfig;
import com.example.bcaddon.BcAddon;
import net.bettercombat.api.AttackHand;
import net.bettercombat.api.EntityPlayer_BetterCombat;
import net.bettercombat.logic.PlayerAttackProperties;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Server side: which damage is a Better Combat swing, and which attack of the combo it is.
 *
 * <p>Better Combat finds the targets on the client and sends their ids
 * ({@code C2S_AttackRequest}); the server then sets the player's combo counter to that swing,
 * attacks every target through the vanilla attack path (so {@code LivingAttackEvent},
 * {@code LivingHurtEvent}, enchantments, Apotheosis affixes... all fire as usual) and sets the
 * counter back to -1. So while those events run, {@link #current} is the swing being performed.</p>
 */
public final class BcAttacks {

    /** The Better Combat swing that is hitting right now, or null for any other damage. */
    @Nullable
    public static AttackHand current(Player player) {
        return ((EntityPlayer_BetterCombat) player).getCurrentAttack();
    }

    /**
     * A new player entity starts with Better Combat's server-side combo counter at 0, which reads as
     * "attack 0 is in progress" until its first swing. -1 is what Better Combat itself leaves
     * after every swing, so {@link #current} is null outside swings from the start.
     */
    public static void resetCombo(ServerPlayer player) {
        ((PlayerAttackProperties) player).setComboCount(-1);
    }

    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getSource().getDirectEntity() != player) return; // arrows, spells, thorns: not a swing
        AttackHand hand = current(player);
        if (hand == null) return;
        // An addon mechanic goes here: posture damage, stagger, finishers, effects per animation...
        // hand.attack().animation(), hand.combo().current() / total(), hand.attributes().category(),
        // hand.isOffHand(), hand.itemStack(); event.setAmount(...) changes the damage.
        if (AddonConfig.LOG_HITS.get()) {
            BcAddon.LOG.info("[bcaddon] {} hit {}: {} (combo {}/{}, {} hand, category {}), damage {}",
                    player.getGameProfile().getName(), event.getEntity().getName().getString(),
                    hand.attack().animation(), hand.combo().current(), hand.combo().total(),
                    hand.isOffHand() ? "off" : "main", hand.attributes().category(), event.getAmount());
        }
    }

    private BcAttacks() {}
}
