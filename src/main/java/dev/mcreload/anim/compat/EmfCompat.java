package dev.mcreload.anim.compat;

import dev.mcreload.anim.McReloadAnim;
import dev.mcreload.anim.client.joint.JointPart;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Entity Model Features (EMF, the engine behind Fresh Animations / Fresh Moves).
 *
 * <p>EMF replaces whole entity models with its own model parts and animates them while they are
 * drawn, after every other mod has posed them. On the player that fights Kinetrix (its pose wins,
 * our joints are thrown away when EMF swaps part states, armour halves vanish). So, unless the
 * user turns it off, EMF is told that the player models are "disabled" (see EmfConfigMixin): the
 * player keeps the vanilla model, which Kinetrix animates. Mobs keep their Fresh Animations
 * models; Kinetrix leaves every EMF-made model alone.</p>
 */
public final class EmfCompat {
    public static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("entity_model_features");
    private static boolean logged;

    /** Called by EMF (through our mixin) for every model it is about to replace. */
    public static boolean keepVanilla(String modelName) {
        if (modelName == null || !Cfg.playerOverEmf || !Cfg.enabled) return false;
        boolean player = modelName.equals("player") || (modelName.startsWith("player_") && !modelName.equals("player_head"));
        if (player && !logged) {
            logged = true;
            McReloadAnim.LOG.info("[kinetrix] EMF: keeping the vanilla player model ({}) so Kinetrix can animate it"
                    + " (set compat.playerOverEmf = false to let resource packs like Fresh Moves take it).", modelName);
        }
        return player;
    }

    /**
     * True for a model part that belongs to another mod's model system (EMF parts, GeckoLib
     * bones...): such parts may swap their cubes and children at any time, so they are never cut
     * into jointed halves.
     */
    public static boolean foreignPart(ModelPart p) {
        return p != null && p.getClass() != ModelPart.class && !(p instanceof JointPart);
    }

    private static final Map<EntityModel<?>, Boolean> CUSTOM = Collections.synchronizedMap(new WeakHashMap<>());
    private static MethodHandle isCustomized;
    private static boolean lookedUp;

    /** EMF has its own (Fresh Animations) model and animation for this entity model. */
    public static boolean isEmfModel(EntityModel<?> model) {
        if (!LOADED || model == null) return false;
        Boolean b = CUSTOM.get(model);
        if (b != null) return b;
        boolean r = false;
        try {
            if (!lookedUp) {
                lookedUp = true;
                Class<?> api = Class.forName("traben.entity_model_features.EMFAnimationApi");
                isCustomized = MethodHandles.publicLookup().findStatic(api, "isModelCustomizedByEMF",
                        MethodType.methodType(boolean.class, EntityModel.class));
            }
            if (isCustomized != null) r = (boolean) isCustomized.invoke(model);
        } catch (Throwable t) {
            isCustomized = null;
        }
        CUSTOM.put(model, r);
        return r;
    }

    private EmfCompat() {}
}
