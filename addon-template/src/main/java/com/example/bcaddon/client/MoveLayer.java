package com.example.bcaddon.client;

import com.example.bcaddon.BcAddon;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The addon's own playerAnimator layer, on every player.
 *
 * <p>Stack of the Otherworld pack, bottom to top: Kinetrix base (-100000, its procedural body),
 * Better Combat weapon poses (1-4), Iron's Spells casts (42), Better Combat attacks (2000), this
 * layer (2500), Kinetrix harmonizer (100000, blends whatever the layers below changed into its
 * jointed body and keeps the legs running). A higher layer wins on the body parts it animates.</p>
 */
public final class MoveLayer {
    public static final ResourceLocation LAYER = BcAddon.id("moves");
    public static final int PRIORITY = 2500;
    private static final Set<ResourceLocation> MISSING = new HashSet<>();

    static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> new ModifierLayer<>());
    }

    /** Once the game has loaded its resources: which of our animations loaded (a bad JSON is skipped with an error). */
    static void logLoaded() {
        Map<String, KeyframeAnimation> ours = PlayerAnimationRegistry.getModAnimations(BcAddon.MODID);
        BcAddon.LOG.info("[bcaddon] player animations: {}", ours.isEmpty() ? "none" : String.join(", ", ours.keySet()));
    }

    /** Plays an animation from assets/<namespace>/player_animation (id = namespace:name in the file). */
    @SuppressWarnings("unchecked")
    public static void play(AbstractClientPlayer player, ResourceLocation animationId) {
        KeyframeAnimation animation = PlayerAnimationRegistry.getAnimation(animationId);
        if (animation == null) {
            if (MISSING.add(animationId)) BcAddon.LOG.warn("[bcaddon] No player animation {}", animationId);
            return;
        }
        var layer = (ModifierLayer<IAnimation>) PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        if (layer == null) return;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(3, Ease.OUTQUAD),
                new KeyframeAnimationPlayer(animation), true);
    }

    private MoveLayer() {}
}
