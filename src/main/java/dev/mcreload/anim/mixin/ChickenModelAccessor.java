package dev.mcreload.anim.mixin;

import net.minecraft.client.model.ChickenModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChickenModel.class)
public interface ChickenModelAccessor {
    @Accessor("head") ModelPart kinetrix$head();
    @Accessor("beak") ModelPart kinetrix$beak();
    @Accessor("redThing") ModelPart kinetrix$redThing();
    @Accessor("rightWing") ModelPart kinetrix$rightWing();
    @Accessor("leftWing") ModelPart kinetrix$leftWing();
}
