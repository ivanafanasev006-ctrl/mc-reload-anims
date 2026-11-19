package dev.mcreload.moves;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's own sounds (assets/kinetrix_combat/sounds). The time stop's (author, 2026-11-19): the slowing down and
 * the time running again, a mix of the Sandevistan's sound (Cyberpunk 2077, as he sent it) changed, with the vanilla
 * beacon and bell; the blows from his sword sounds of 2026-11-16 (whooshes, the slash, the swords' ring, the stab).
 */
public final class MovesSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, KinetrixMoves.MODID);

    public static final RegistryObject<SoundEvent> TIME_STOP_SLOW = sound("time_stop.slow");
    public static final RegistryObject<SoundEvent> TIME_STOP_UNWIND = sound("time_stop.unwind");
    public static final RegistryObject<SoundEvent> TIME_STOP_SWING = sound("time_stop.swing");
    public static final RegistryObject<SoundEvent> TIME_STOP_HIT = sound("time_stop.hit");

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(KinetrixMoves.MODID, name)));
    }

    static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    private MovesSounds() {}
}
