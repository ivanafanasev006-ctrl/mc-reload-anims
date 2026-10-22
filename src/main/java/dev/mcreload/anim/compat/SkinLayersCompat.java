package dev.mcreload.anim.compat;

import dev.mcreload.anim.McReloadAnim;
import dev.mcreload.anim.client.joint.Joints;
import net.minecraftforge.fml.ModList;

/**
 * Skin Layers 3D (tr7zw) draws the outer skin layer (hat, jacket, sleeves, trousers) as voxels on
 * top of the model, using each body part's transform. Without help those voxels stay straight
 * and hide the bent elbows and knees underneath. Through its public API we bend the voxels with
 * the same hinges as the limbs and carry head and arms along with a bent waist.
 */
public final class SkinLayersCompat {
    public static final String MODID = "skinlayers3d";
    public static boolean active;

    public static void init() {
        if (!ModList.get().isLoaded(MODID) || !Joints.enabled()) return;
        try {
            SkinLayersBend.install();
            active = true;
            McReloadAnim.LOG.info("[kinetrix] Skin Layers 3D detected: the 3D skin layer bends with elbows, knees and waist.");
        } catch (Throwable t) {
            McReloadAnim.LOG.warn("[kinetrix] Skin Layers 3D is installed but its API could not be used ({}); its 3D layer"
                    + " will not bend.", t.toString());
        }
    }

    private static boolean injectorBroken;

    /**
     * Skin Layers 3D (compatibility mode) draws its 3D layer from inside this part's render
     * call: the part must then be rendered the normal way (the layer still bends, see
     * SkinLayersBend).
     */
    public static boolean drawsPart(net.minecraft.client.model.geom.ModelPart p) {
        if (!active || injectorBroken) return false;
        try {
            return SkinLayersBend.hasInjectedMesh(p);
        } catch (Throwable t) {
            injectorBroken = true;
            return false;
        }
    }

    private SkinLayersCompat() {}
}
