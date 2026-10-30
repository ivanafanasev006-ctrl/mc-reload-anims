package dev.mcreload.moves;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The celebrate emote (author, 2026-10-29: "the music I attached plays in the background, as from a
 * record, when the emote is used"; 2026-10-30: "squat, wait until 3.6-4.1 seconds, then fly up fast
 * into a jump, hang in the air until the 8th second, then fall back"; Move.CELEBRATE, 9 s: the music
 * ends as the fall begins). The music is the author's clip (assets/kinetrix_moves/sounds/celebrate.ogg,
 * mono so it comes from the player and fades with distance, as a jukebox does), on the Jukebox/Note
 * Blocks volume. It follows the player and everyone near hears it; breaking the emote off (moving,
 * a hit) stops it.
 */
public final class Celebrate {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, KinetrixMoves.MODID);
    public static final ResourceLocation MUSIC_ID = new ResourceLocation(KinetrixMoves.MODID, "celebrate");
    public static final RegistryObject<SoundEvent> MUSIC = SOUNDS.register("celebrate", () -> SoundEvent.createVariableRangeEvent(MUSIC_ID));

    /** The emote starts: its music, bound to the player. */
    static void started(ServerPlayer p) {
        p.level().playSound(null, p, MUSIC.get(), SoundSource.RECORDS, 1f, 1f);
    }

    /** The emote broken off before its end: the music stops for everyone who hears it. */
    static void stopped(ServerPlayer p) {
        ClientboundStopSoundPacket stop = new ClientboundStopSoundPacket(MUSIC_ID, SoundSource.RECORDS);
        p.serverLevel().getChunkSource().broadcastAndSend(p, stop);
    }

    private Celebrate() {}
}
