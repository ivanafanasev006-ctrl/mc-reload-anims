package dev.mcreload.anim.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcreload.anim.client.mob.MobAnimator;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Livelier mobs: right after vanilla has posed the model for this frame, MobAnimator layers its
 * own motion on top; everything it changed on the (shared) model is put back once the entity
 * and its layers have been drawn.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void kinetrix$mobLife(LivingEntity entity, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light,
                                  CallbackInfo ci) {
        MobAnimator.apply(entity, ((LivingEntityRenderer<?, ?>) (Object) this).getModel(), ps, pt);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void kinetrix$mobRestore(LivingEntity entity, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light,
                                     CallbackInfo ci) {
        MobAnimator.restore();
    }
}
