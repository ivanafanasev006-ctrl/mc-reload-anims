package dev.mcreload.anim.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcreload.anim.client.joint.JointHolder;
import dev.mcreload.anim.client.joint.JointRig;
import dev.mcreload.anim.client.joint.Joints;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cuts the player model at elbows, knees and waist, and bends it every frame. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void mcreload$joints(ModelPart root, boolean slim, CallbackInfo ci) {
        if (!Joints.enabled()) return;
        PlayerModel<?> self = (PlayerModel<?>) (Object) this;
        ((JointHolder) self).mcreload$setJoints(JointRig.forPlayer(self));
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void mcreload$bend(LivingEntity entity, float limbSwing, float limbAmount, float age, float yaw, float pitch,
                               CallbackInfo ci) {
        JointRig rig = ((JointHolder) this).mcreload$getJoints();
        if (rig != null) rig.update(entity);
    }

    @Inject(method = "translateToHand", at = @At("HEAD"))
    private void mcreload$handBody(HumanoidArm arm, PoseStack ps, CallbackInfo ci) {
        JointRig rig = ((JointHolder) this).mcreload$getJoints();
        if (rig != null) rig.applyUpperBody(ps, ((PlayerModel<?>) (Object) this).body);
    }

    @Inject(method = "translateToHand", at = @At("RETURN"))
    private void mcreload$handElbow(HumanoidArm arm, PoseStack ps, CallbackInfo ci) {
        JointRig rig = ((JointHolder) this).mcreload$getJoints();
        if (rig != null) rig.applyElbow(ps, arm);
    }
}
