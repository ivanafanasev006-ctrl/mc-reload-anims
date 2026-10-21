package dev.mcreload.anim.mixin;

import net.minecraft.client.model.OcelotModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OcelotModel.class)
public interface OcelotModelAccessor {
    @Accessor("head") ModelPart kinetrix$head();
    @Accessor("tail1") ModelPart kinetrix$tail1();
    @Accessor("tail2") ModelPart kinetrix$tail2();
}
