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
import org.spongepowered.asm.mixin.injection.Redirect;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.client.rig.RigRegistry;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Joints for the player's armour models; straight arms in first person; and a smooth way out of
 * the swimming pose. Vanilla drops two parts of it at once the moment you stop swimming (the
 * pitch term once you are out of the water, and a one-block shift of the model), which made the
 * body and head jerk when climbing out onto land. Here both fade out with the swim amount.
 */
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

    @Unique
    private static final String RENDER = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    /**
     * Every player drawn with this renderer shares one model, and its joints are set at the end of
     * setupAnim. A mod that skips setupAnim for its own animation (ParCool does for its parkour
     * moves) would otherwise draw this player with the bends left over from the player drawn
     * before: start every player straight.
     */
    @Inject(method = RENDER, at = @At("HEAD"))
    private void kinetrix$straightJoints(AbstractClientPlayer player, float yaw, float pt, PoseStack ps,
                                         net.minecraft.client.renderer.MultiBufferSource buffers, int light, CallbackInfo ci) {
        JointRig rig = ((JointHolder) ((PlayerRenderer) (Object) this).getModel()).mcreload$getJoints();
        if (rig != null) rig.setBends(0f, 0f, 0f, 0f, 0f);
    }

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void mcreload$firstPersonOn(CallbackInfo ci) {
        Joints.firstPerson = true;
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void mcreload$firstPersonOff(CallbackInfo ci) {
        Joints.firstPerson = false;
    }
    @Unique
    private static final String SETUP_ROT =
            "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V";
    @Unique
    private AbstractClientPlayer kinetrix$player;
    @Unique
    private float kinetrix$pt;

    @Inject(method = SETUP_ROT, at = @At("HEAD"))
    private void kinetrix$capture(AbstractClientPlayer player, PoseStack ps, float age, float yaw, float pt, CallbackInfo ci) {
        kinetrix$player = player;
        kinetrix$pt = pt;
    }

    @Unique
    private static boolean kinetrix$fading(AbstractClientPlayer player) {
        PlayerRig rig = RigRegistry.get(player);
        return rig != null && rig.motion.swimFading();
    }

    @Redirect(method = SETUP_ROT, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;isInWater()Z"))
    private boolean kinetrix$stillInWater(AbstractClientPlayer player) {
        if (player.isInWater()) return true;
        PlayerRig rig = RigRegistry.get(player);
        return rig != null && rig.motion.swimFading() && rig.motion.wasInWaterRecently();
    }

    @Redirect(method = SETUP_ROT, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;isVisuallySwimming()Z"))
    private boolean kinetrix$stillSwimming(AbstractClientPlayer player) {
        return player.isVisuallySwimming() || kinetrix$fading(player);
    }

    @Redirect(method = SETUP_ROT, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void kinetrix$swimShift(PoseStack ps, float x, float y, float z) {
        // The swim pose's shift, faded in and out with the swim amount instead of all at once.
        float k = kinetrix$player != null ? kinetrix$player.getSwimAmount(kinetrix$pt) : 1f;
        ps.translate(x * k, y * k, z * k);
    }
}
