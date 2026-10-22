package dev.mcreload.anim.mixin.compat;

import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.mcreload.anim.client.rig.AnimPriority;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lets Kinetrix mute other mods' player animation layers (see {@link AnimPriority}). */
@Mixin(value = AnimationStack.class, remap = false)
public abstract class AnimationStackMixin {
    @Redirect(method = {"get3DTransform", "tick", "getFirstPersonMode", "getFirstPersonConfiguration"},
            at = @At(value = "INVOKE", target = "Ldev/kosmx/playerAnim/api/layered/IAnimation;isActive()Z"),
            require = 0, remap = false)
    private boolean kinetrix$priority(IAnimation layer) {
        return layer.isActive() && !AnimPriority.muted(this, layer);
    }
}
