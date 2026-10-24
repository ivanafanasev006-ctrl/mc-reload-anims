package dev.mcreload.anim.client.gallery;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

/**
 * {@code /kinetrix} (also {@code menu}, {@code animations}, {@code set}): opens the animation
 * gallery; {@code /kinetrix show <animation>} opens it on that one. Client side: works on any server.
 */
public final class KinetrixCommand {
    public static void register(RegisterClientCommandsEvent event) {
        Command<CommandSourceStack> open = ctx -> {
            GalleryScreen.openSoon(null);
            return 1;
        };
        event.getDispatcher().register(Commands.literal("kinetrix").executes(open)
                .then(Commands.literal("menu").executes(open))
                .then(Commands.literal("animations").executes(open))
                .then(Commands.literal("set").executes(open))
                .then(Commands.literal("show").then(Commands.argument("animation", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Demos.ALL.stream().map(Demo::id), builder))
                        .executes(ctx -> {
                            GalleryScreen.openSoon(StringArgumentType.getString(ctx, "animation"));
                            return 1;
                        }))));
    }

    private KinetrixCommand() {}
}
