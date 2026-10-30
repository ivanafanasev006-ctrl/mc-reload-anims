package dev.mcreload.anim.client.fx;

import dev.mcreload.anim.client.rig.EmoteLayer;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * The music of an emote (author: "the music I attached plays in the background while the emote is
 * used, as from a record"): the author's clip (assets/kinetrix/sounds/celebrate.ogg, mono) from the
 * player, following them and fading with distance as a jukebox does, on the Jukebox/Note Blocks
 * volume. Each client plays it for each player it sees doing the emote (the emote reaches other
 * players with Kinetrix through the server), so it needs nothing on the server; it fades out at once
 * when the emote is broken off. The gallery's dummy plays it too, while the gallery is open.
 */
public final class EmoteMusic extends AbstractTickableSoundInstance {
    /** Played by its name from assets/kinetrix/sounds.json: nothing is registered, so a server needs nothing. */
    private static final SoundEvent CELEBRATE = SoundEvent.createVariableRangeEvent(EmoteLayer.CELEBRATE);
    /** Ticks the music takes to fade out when the emote is broken off. */
    private static final int FADE = 5;

    private final PlayerRig rig;
    private final float start;
    private int fading = -1;

    private EmoteMusic(PlayerRig rig, float start) {
        super(CELEBRATE, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.rig = rig;
        this.start = start;
        this.looping = false;
        this.delay = 0;
        this.volume = 1f;
        follow();
    }

    /** The emote that started at {@code start} (MotionState.emoteStart) has music: play it from the player. */
    public static void play(PlayerRig rig, float start) {
        Minecraft.getInstance().getSoundManager().play(new EmoteMusic(rig, start));
    }

    @Override
    public void tick() {
        MotionState m = rig.motion;
        boolean on = m.emote == MotionState.EMOTE_CELEBRATE && m.emoteStart == start && heard();
        if (!on) {
            fading++;
            volume = Math.max(0f, 1f - fading / (float) FADE);
            if (fading >= FADE) stop();
        }
        follow();
    }

    /** The player is still there to be heard: in this world (the gallery's dummy: while the gallery is open). */
    private boolean heard() {
        Minecraft mc = Minecraft.getInstance();
        if (rig.preview) return mc.screen instanceof dev.mcreload.anim.client.gallery.GalleryScreen;
        Player p = rig.player;
        return !p.isRemoved() && mc.level != null && p.level() == mc.level;
    }

    private void follow() {
        Player p = rig.player;
        x = p.getX();
        y = p.getY() + 1.0;
        z = p.getZ();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
