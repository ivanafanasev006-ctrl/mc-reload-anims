package dev.mcreload.anim.mixin;

import net.minecraft.client.model.LlamaModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LlamaModel.class)
public interface LlamaModelAccessor {
    @Accessor("head") ModelPart kinetrix$head();
}
