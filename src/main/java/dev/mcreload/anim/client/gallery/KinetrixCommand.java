package dev.mcreload.anim.client.gallery;

import net.minecraft.commands.Commands;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

/**
 * {@code /kinetrix}: opens the menu of the emotes and the animations (the gallery), and that is all
 * (author, 2026-10-30: one command, no {@code menu} after it). Client side: works on any server.
 */
public final class KinetrixCommand {
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("kinetrix").executes(ctx -> {
            GalleryScreen.openSoon(null);
            return 1;
        }));
    }

    private KinetrixCommand() {}
}
