package dev.mcreload.anim.mixin;

import dev.mcreload.anim.client.joint.JointHolder;
import dev.mcreload.anim.client.joint.JointRig;
import dev.mcreload.anim.client.joint.Joints;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Joints for the player's armour models; straight arms in first person. */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void mcreload$armorJoints(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        if (!Joints.enabled()) return;
        for (RenderLayer<?, ?> layer : ((LivingEntityRendererAccessor) this).mcreload$layers()) {
            if (layer instanceof HumanoidArmorLayer<?, ?, ?> armor) {
                HumanoidArmorLayerAccessor acc = (HumanoidArmorLayerAccessor) armor;
                mcreload$joint(acc.mcreload$inner());
                mcreload$joint(acc.mcreload$outer());
            }
        }
    }

    @Unique
    private static void mcreload$joint(HumanoidModel<?> model) {
        if (model != null && ((JointHolder) model).mcreload$getJoints() == null) {
            ((JointHolder) model).mcreload$setJoints(JointRig.forArmor(model));
        }
    }

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void mcreload$firstPersonOn(CallbackInfo ci) {
        Joints.firstPerson = true;
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void mcreload$firstPersonOff(CallbackInfo ci) {
        Joints.firstPerson = false;
    }
}
