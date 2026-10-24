package dev.mcreload.anim.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The skin layers a player shows (hat, jacket, sleeves...): the preview dummy copies yours. */
@Mixin(Player.class)
public interface PlayerAccessor {
    @Accessor("DATA_PLAYER_MODE_CUSTOMISATION")
    static EntityDataAccessor<Byte> kinetrix$modelParts() {
        throw new AssertionError();
    }
}
