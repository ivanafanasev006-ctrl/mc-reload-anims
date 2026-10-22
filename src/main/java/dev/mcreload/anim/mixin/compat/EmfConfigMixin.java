package dev.mcreload.anim.mixin.compat;

import dev.mcreload.anim.compat.EmfCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity Model Features: keep the player on the vanilla model (Kinetrix animates it). Only
 * applied when EMF is installed; does nothing if EMF ever renames the method.
 */
@Pseudo
@Mixin(targets = "traben.entity_model_features.config.EMFConfig", remap = false)
public abstract class EmfConfigMixin {
    @Inject(method = "isModelDisabled(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void kinetrix$keepPlayerVanilla(String modelName, CallbackInfoReturnable<Boolean> cir) {
        if (EmfCompat.keepVanilla(modelName)) cir.setReturnValue(true);
    }
}
