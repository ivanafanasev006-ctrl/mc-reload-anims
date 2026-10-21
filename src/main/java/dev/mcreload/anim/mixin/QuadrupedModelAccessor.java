package dev.mcreload.anim.mixin;

import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(QuadrupedModel.class)
public interface QuadrupedModelAccessor {
    @Accessor("head") ModelPart kinetrix$head();
    @Accessor("body") ModelPart kinetrix$body();
}
