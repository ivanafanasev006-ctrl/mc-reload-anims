package dev.mcreload.anim.mixin.compat;

import dev.mcreload.anim.compat.EmfCompat;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity Model Features: the player (and the player's armour) keep the vanilla model. This is
 * the entry point where EMF swaps a freshly baked model for its own, present in every EMF
 * version from 2.x on (older ones have no working "disable model" setting to hook).
 */
@Pseudo
@Mixin(targets = "traben.entity_model_features.EMFManager", remap = false)
public abstract class EmfManagerMixin {
    @Inject(method = "injectIntoModelRootGetter(Lnet/minecraft/client/model/geom/ModelLayerLocation;Lnet/minecraft/client/model/geom/ModelPart;)Lnet/minecraft/client/model/geom/ModelPart;",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void kinetrix$vanillaPlayer(ModelLayerLocation layer, ModelPart root, CallbackInfoReturnable<ModelPart> cir) {
        if (EmfCompat.keepVanilla(layer)) cir.setReturnValue(root);
    }
}
