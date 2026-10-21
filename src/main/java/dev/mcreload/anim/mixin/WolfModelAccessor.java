package dev.mcreload.anim.mixin;

import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WolfModel.class)
public interface WolfModelAccessor {
    @Accessor("realHead") ModelPart kinetrix$realHead();
    @Accessor("tail") ModelPart kinetrix$tail();
    @Accessor("upperBody") ModelPart kinetrix$upperBody();
}
