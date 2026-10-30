package dev.mcreload.anim.client.rig;

import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import dev.mcreload.anim.McReloadAnim;
import dev.mcreload.anim.client.fx.EmoteMusic;
import net.minecraft.resources.ResourceLocation;

/**
 * Our emotes made in Blockbench (author, 2026-10-30: celebrate belongs to Kinetrix, not to Kinetrix
 * Moves): while one is the player's emote, its keyframe animation plays here, over our procedural
 * motion and under every other mod's layer, and its music plays from the player (EmoteMusic). The
 * emote itself is an ordinary one of ours (MotionState.emote): the emote menu starts it, other
 * players with Kinetrix hear of it, and moving, a blow or a hit breaks it off, which stops both.
 *
 * <p>celebrate (assets/kinetrix/player_animation/celebrate.json, made from
 * tools/blockbench/refined/kinetrix/emotes.bbmodel): down into a squat, waiting for the music's drop;
 * the leap at 3.71 s, the figure 1.4 blocks up by 4.0 s, hanging there while the music plays, the fall
 * as it stops (8.0 s), the landing, the stance by 9 s. Only the figure leaves the ground.</p>
 */
public final class EmoteLayer extends ModifierLayer<IAnimation> {
    public static final ResourceLocation CELEBRATE = new ResourceLocation(McReloadAnim.MODID, "celebrate");
    /** celebrate's length (its animation: 180 ticks). */
    public static final float CELEBRATE_SECONDS = 9.0f;

    private final PlayerRig rig;
    /** When the emote being played here started (MotionState.emoteStart), NaN: none. */
    private float playing = Float.NaN;

    EmoteLayer(PlayerRig rig) {
        this.rig = rig;
    }

    /**
     * Every game tick, from our base layer (the stack ticks only the layers that play, so this one
     * could not start itself): follows the player's emote.
     */
    void follow() {
        MotionState m = rig.motion;
        boolean want = m.emote == MotionState.EMOTE_CELEBRATE;
        if (want && m.emoteStart != playing) {
            playing = m.emoteStart;
            KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(CELEBRATE);
            setAnimation(anim == null ? null : new KeyframeAnimationPlayer(anim));
            EmoteMusic.play(rig, playing);
        } else if (!want && !Float.isNaN(playing)) {
            playing = Float.NaN;
            setAnimation(null);
        }
        // the emote owns the whole figure: none of our stride under it, the head with the animation
        if (getAnimation() != null) dev.mcreload.anim.api.KinetrixApi.wholeBody(rig.player.getUUID(), 250);
    }

    /** The emote whose start this layer plays now (MotionState.emoteStart), NaN: none. */
    public float playing() {
        return playing;
    }

    @Override
    public boolean isActive() {
        return rig.shouldAnimate() && super.isActive();
    }
}
