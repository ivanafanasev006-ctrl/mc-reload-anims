package dev.mcreload.anim.mixin;

import dev.mcreload.anim.client.joint.JointHolder;
import dev.mcreload.anim.client.joint.JointRig;
import net.minecraft.client.model.HumanoidModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stores the jointed rig on humanoid models and passes the bends on to armour models. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin implements JointHolder {
    @Unique
    private JointRig mcreload$joints;

    @Override
    public JointRig mcreload$getJoints() {
        return mcreload$joints;
    }

    @Override
    public void mcreload$setJoints(JointRig rig) {
        mcreload$joints = rig;
    }

    @Inject(method = "copyPropertiesTo", at = @At("RETURN"))
    private void mcreload$copyJoints(HumanoidModel<?> target, CallbackInfo ci) {
        JointRig dst = ((JointHolder) target).mcreload$getJoints();
        if (dst == null) return;
        if (mcreload$joints != null) dst.copyFrom(mcreload$joints);
    }
}
