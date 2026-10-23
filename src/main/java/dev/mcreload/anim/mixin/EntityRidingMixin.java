package dev.mcreload.anim.mixin;

import dev.mcreload.anim.ride.TwoRiders;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Two on one horse (see TwoRiders): room for a second passenger, and where that one sits. */
@Mixin(Entity.class)
abstract class EntityRidingMixin {
    @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
    private void kinetrix$secondSeat(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (TwoRiders.seats(self) >= 2) cir.setReturnValue(self.getPassengers().size() < 2);
    }

    @Inject(method = "rideTick", at = @At("TAIL"))
    private void kinetrix$sitBehind(CallbackInfo ci) {
        TwoRiders.placeBehind((Entity) (Object) this);
    }
}
