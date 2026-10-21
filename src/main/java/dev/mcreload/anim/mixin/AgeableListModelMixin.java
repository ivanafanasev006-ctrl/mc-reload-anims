package dev.mcreload.anim.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mcreload.anim.client.joint.JointHolder;
import dev.mcreload.anim.client.joint.JointRig;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** When the torso is bent, head and arms are drawn swung around the waist with it. */
@Mixin(AgeableListModel.class)
public abstract class AgeableListModelMixin {
    @Shadow
    protected abstract Iterable<ModelPart> headParts();

    @Shadow
    protected abstract Iterable<ModelPart> bodyParts();

    @Inject(method = "renderToBuffer", at = @At("HEAD"), cancellable = true)
    private void mcreload$bentTorso(PoseStack ps, VertexConsumer vc, int light, int overlay,
                                    float r, float g, float b, float a, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof JointHolder holder) || !(self instanceof HumanoidModel<?> model)) return;
        JointRig rig = holder.mcreload$getJoints();
        if (rig == null || !rig.torsoBent() || model.young) return;
        for (ModelPart p : headParts()) if (!rig.isUpper(p)) p.render(ps, vc, light, overlay, r, g, b, a);
        for (ModelPart p : bodyParts()) if (!rig.isUpper(p)) p.render(ps, vc, light, overlay, r, g, b, a);
        ps.pushPose();
        rig.applyUpperBody(ps, model.body);
        for (ModelPart p : headParts()) if (rig.isUpper(p)) p.render(ps, vc, light, overlay, r, g, b, a);
        for (ModelPart p : bodyParts()) if (rig.isUpper(p)) p.render(ps, vc, light, overlay, r, g, b, a);
        ps.popPose();
        ci.cancel();
    }
}
