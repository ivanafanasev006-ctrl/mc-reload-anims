package com.example.bcaddon.mixin;

import com.example.bcaddon.combat.Heavy;
import com.example.bcaddon.data.WeaponExtras;
import net.bettercombat.api.AttackHand;
import net.bettercombat.logic.PlayerAttackHelper;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every place in Better Combat asks {@code PlayerAttackHelper.getCurrentAttack} which attack comes
 * next: the client to play the animation, time the upswing and find targets, the server to apply
 * the damage multiplier. Swapping the result here changes the attack everywhere at once.
 *
 * <p>{@code remap = false}: Better Combat is a mod, its names are not obfuscated. Other mods of the
 * Otherworld pack hook the same method (Otherworld Origins replaces the attack of shapeshifted
 * players at HEAD and returns early, so this RETURN hook simply does not run for them). Inject,
 * never overwrite, so all of them keep working.</p>
 */
@Mixin(value = PlayerAttackHelper.class, remap = false)
public abstract class PlayerAttackHelperMixin {

    @Inject(method = "getCurrentAttack", at = @At("RETURN"), cancellable = true)
    private static void bcaddon$heavyAttack(Player player, int comboCount, CallbackInfoReturnable<AttackHand> cir) {
        AttackHand hand = cir.getReturnValue();
        if (hand == null || hand.isOffHand() || !Heavy.isHeld(player)) return;
        WeaponExtras extras = WeaponExtras.of(hand.itemStack());
        if (extras == null || extras.heavy() == null) return;
        cir.setReturnValue(new AttackHand(extras.heavy().applyTo(hand.attack()), hand.combo(),
                hand.isOffHand(), hand.attributes(), hand.itemStack()));
    }
}
