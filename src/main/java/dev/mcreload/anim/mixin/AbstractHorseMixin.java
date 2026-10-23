package dev.mcreload.anim.mixin;

import dev.mcreload.anim.ride.TwoRiders;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two on one horse (see TwoRiders): right-clicking a horse someone is riding climbs on behind
 * them. Vanilla horses (and mods' horses, whose own click handling hands a ridden horse back to
 * this method) otherwise do nothing for a horse that already has a rider.
 */
@Mixin(AbstractHorse.class)
abstract class AbstractHorseMixin {
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void kinetrix$climbOnBehind(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        AbstractHorse self = (AbstractHorse) (Object) this;
        if (!TwoRiders.canClimbOnBehind(self, player)) return;
        if (!self.level().isClientSide) {
            player.setYRot(self.getYRot());
            player.setXRot(self.getXRot());
            player.startRiding(self);
        }
        cir.setReturnValue(InteractionResult.sidedSuccess(self.level().isClientSide));
    }
}
