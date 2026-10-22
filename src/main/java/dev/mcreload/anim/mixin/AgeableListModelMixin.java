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

/**
 * Jointed models draw themselves while a joint is bent: each part cut by a bent joint is drawn
 * in two halves with the hinge between them, head and arms ride along with a bent waist, and
 * everything else is drawn the vanilla way. With no joint bent, vanilla draws the model.
 */
@Mixin(AgeableListModel.class)
public abstract class AgeableListModelMixin {
    @Shadow
    protected abstract Iterable<ModelPart> headParts();

    @Shadow
    protected abstract Iterable<ModelPart> bodyParts();

    @Inject(method = "renderToBuffer", at = @At("HEAD"), cancellable = true)
    private void mcreload$jointed(PoseStack ps, VertexConsumer vc, int light, int overlay,
                                  float r, float g, float b, float a, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof JointHolder holder) || !(self instanceof HumanoidModel<?> model)) return;
        JointRig rig = holder.mcreload$getJoints();
        if (rig == null || model.young || !rig.anyBent()) return;
        boolean torso = rig.torsoBent();
        for (ModelPart p : headParts()) mcreload$part(rig, model, p, torso, ps, vc, light, overlay, r, g, b, a);
        for (ModelPart p : bodyParts()) mcreload$part(rig, model, p, torso, ps, vc, light, overlay, r, g, b, a);
        ci.cancel();
    }

    @org.spongepowered.asm.mixin.Unique
    private static void mcreload$part(JointRig rig, HumanoidModel<?> model, ModelPart p, boolean torso, PoseStack ps,
                                      VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        boolean upper = torso && rig.isUpper(p);
        if (upper) {
            ps.pushPose();
            if (rig.isHead(p)) rig.applyNeck(ps, model.body, p);
            else rig.applyUpperBody(ps, model.body);
        }
        rig.draw(p, ps, vc, light, overlay, r, g, b, a);
        if (upper) ps.popPose();
    }
}
