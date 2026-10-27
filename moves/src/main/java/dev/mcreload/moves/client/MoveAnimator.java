package dev.mcreload.moves.client;

import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import dev.mcreload.moves.KinetrixMoves;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.compat.KinetrixBridge;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Plays the moves on the players through playerAnimator, on a layer of our own. Kinetrix bends the
 * elbows, knees and waist from what this layer says; it sits above Better Combat's attacks (2000)
 * and below Kinetrix's harmonizer.
 */
public final class MoveAnimator {
    public static final ResourceLocation LAYER = new ResourceLocation(KinetrixMoves.MODID, "moves");
    public static final int PRIORITY = 2500;

    static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> new MoveLayer());
    }

    /** The layer, with a speed control (the slide plays faster than in Blockbench). */
    private static final class MoveLayer extends ModifierLayer<IAnimation> {
        final SpeedModifier speed = new SpeedModifier(1f);

        MoveLayer() {
            addModifierBefore(speed);
        }
    }

    private static MoveLayer layer(AbstractClientPlayer p) {
        return PlayerAnimationAccess.getPlayerAssociatedData(p).get(LAYER) instanceof MoveLayer l ? l : null;
    }

    /** Starts the move's animation, blended in over two ticks. */
    public static void play(AbstractClientPlayer p, Move move) {
        MoveLayer layer = layer(p);
        KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(move.animation);
        if (layer == null) return;
        if (anim == null) {
            KinetrixMoves.LOG.warn("[kinetrix_moves] animation {} is missing", move.animation);
            return;
        }
        layer.speed.speed = move.animSpeed;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, Ease.INOUTSINE), new KeyframeAnimationPlayer(anim));
        KinetrixBridge.wholeBody(p.getUUID(), move.length * 50L + 100L);
    }

    /** Ends the animation early, blended out over {@code ticks}. */
    public static void stop(AbstractClientPlayer p, int ticks) {
        MoveLayer layer = layer(p);
        KinetrixBridge.wholeBody(p.getUUID(), 0);
        if (layer == null || layer.getAnimation() == null) return;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(ticks, Ease.INOUTSINE), null);
    }

    private MoveAnimator() {}
}
