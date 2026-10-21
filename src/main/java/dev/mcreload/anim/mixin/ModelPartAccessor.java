package dev.mcreload.anim.mixin;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Accessor("cubes")
    List<ModelPart.Cube> mcreload$cubes();

    @Accessor("children")
    Map<String, ModelPart> mcreload$children();

    @Mutable
    @Accessor("children")
    void mcreload$setChildren(Map<String, ModelPart> children);
}
