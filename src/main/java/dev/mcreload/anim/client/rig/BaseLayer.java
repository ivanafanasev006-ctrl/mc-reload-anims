package dev.mcreload.anim.client.rig;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import org.jetbrains.annotations.NotNull;

/** Bottom-of-stack layer: procedural motion added onto the vanilla pose. */
public final class BaseLayer implements IAnimation {
    private final PlayerRig rig;

    BaseLayer(PlayerRig rig) {
        this.rig = rig;
    }

    @Override
    public boolean isActive() {
        return rig.shouldAnimate();
    }

    @Override
    public void tick() {
        rig.motion.tick();
        rig.emote.follow();
    }

    @Override
    public void setupAnim(float tickDelta) {
        if (!isActive()) return;
        rig.computeFrame(tickDelta);
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float tickDelta, @NotNull Vec3f value0) {
        return rig.applyBase(modelName, type, value0);
    }
}
