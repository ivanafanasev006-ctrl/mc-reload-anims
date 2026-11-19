package dev.mcreload.moves.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcreload.moves.client.MobPoses;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A mob in one of our moves (caught by the wrist, its arm twisted behind its back, falling on its face, held on the
 * blade, frozen in time) takes its pose: right after its model is set up the vanilla way, before it and its layers
 * (armour, held items) are drawn (MobPoses).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Shadow
    protected EntityModel<?> model;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
    private void kinetrixCombat$pose(LivingEntity entity, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light,
                                     CallbackInfo ci) {
        MobPoses.limbs(entity, model, partialTick);
    }
}
