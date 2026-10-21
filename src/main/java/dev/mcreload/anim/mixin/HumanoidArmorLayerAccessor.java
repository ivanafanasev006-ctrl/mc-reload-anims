package dev.mcreload.anim.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HumanoidArmorLayer.class)
public interface HumanoidArmorLayerAccessor {
    @Accessor("innerModel")
    HumanoidModel<?> mcreload$inner();

    @Accessor("outerModel")
    HumanoidModel<?> mcreload$outer();
}
