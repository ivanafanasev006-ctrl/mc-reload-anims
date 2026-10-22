package dev.mcreload.anim.client.rig;

import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import dev.mcreload.anim.McReloadAnim;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Who wins when several mods animate the player through playerAnimator.
 *
 * <p>Kinetrix replaces the vanilla animations. Mods that only do the same (listed in
 * {@code compat.priorityOverMods}) are muted outright. Everything else (Better Combat attacks,
 * Iron's Spells casts, Emotecraft...) still plays on top, blended by the harmonizer, except while
 * one of our own full-body moves (an emote, a roll, a landing) is running.</p>
 */
public final class AnimPriority {
    private static final Map<Object, String> OWNER = Collections.synchronizedMap(new WeakHashMap<>());
    private static Map<KeyframeAnimation, String> byAnimation = new IdentityHashMap<>();
    private static int registrySize = -1;
    private static final Set<String> LOGGED = Collections.synchronizedSet(new HashSet<>());

    /** Called by the AnimationStack mixin for every layer it is about to use. */
    public static boolean muted(Object stack, IAnimation layer) {
        if (!Cfg.enabled) return false;
        PlayerRig rig = RigRegistry.byStack(stack);
        if (rig == null || layer == rig.baseLayer() || layer == rig.harmonizerLayer()) return false;
        if (Cfg.ownMovesFirst && rig.motion.fullBodyMove()) return true;
        Set<String> list = Cfg.priorityOver;
        if (list.isEmpty()) return false;
        String owner = owner(layer);
        if (owner != null && list.contains(owner)) {
            if (LOGGED.add(owner)) McReloadAnim.LOG.info("[kinetrix] Muting player animations from '{}' (compat.priorityOverMods).", owner);
            return true;
        }
        return false;
    }

    /** The mod an animation layer comes from: the animation's namespace, or the class's module. */
    static String owner(IAnimation layer) {
        Object inner = layer;
        for (int i = 0; i < 4 && inner instanceof ModifierLayer<?> ml; i++) inner = ml.getAnimation();
        if (inner == null) return null;
        Object key = inner instanceof KeyframeAnimationPlayer kap ? kap.getData() : inner;
        String cached = OWNER.get(key);
        if (cached != null) return cached;
        String owner = null;
        if (inner instanceof KeyframeAnimationPlayer kap) owner = namespaceOf(kap.getData());
        if (owner == null) {
            String module = inner.getClass().getModule().getName();
            if (module == null) {
                String pkg = inner.getClass().getPackageName();
                module = pkg.isEmpty() ? null : pkg;
            }
            owner = module;
        }
        owner = owner == null ? "" : owner.toLowerCase(Locale.ROOT);
        OWNER.put(key, owner);
        return owner;
    }

    private static synchronized String namespaceOf(KeyframeAnimation anim) {
        if (anim == null) return null;
        Map<ResourceLocation, KeyframeAnimation> all = PlayerAnimationRegistry.getAnimations();
        if (all.size() != registrySize) {
            registrySize = all.size();
            Map<KeyframeAnimation, String> m = new IdentityHashMap<>();
            all.forEach((id, a) -> m.put(a, id.getNamespace()));
            byAnimation = m;
        }
        return byAnimation.get(anim);
    }

    private AnimPriority() {}
}
