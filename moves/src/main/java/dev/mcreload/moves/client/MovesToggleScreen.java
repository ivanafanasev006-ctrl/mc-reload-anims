package dev.mcreload.moves.client;

import dev.mcreload.moves.HudConfig;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

/**
 * Which moves this player's keys make (author, 2026-10-29: "a settings menu where all these lunges,
 * strikes, combos can be switched off"): one on / off button a move, two to a row, in vanilla's
 * scrolling list (as Video Settings), so it fits any GUI scale and a small window. The hover tells
 * the keys of the move. Opened from the bars' settings (the Moves... button).
 */
public final class MovesToggleScreen extends Screen {
    private final Screen parent;
    private OptionsList list;

    public MovesToggleScreen(Screen parent) {
        super(Component.translatable("kinetrix_moves.settings.moves.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        list = new OptionsList(minecraft, width, height, 32, height - 32, 25);
        HudConfig.Moves[] moves = HudConfig.Moves.values();
        OptionInstance<?>[] options = new OptionInstance<?>[moves.length + 1];
        for (int i = 0; i < moves.length; i++) {
            HudConfig.Moves m = moves[i];
            String key = "kinetrix_moves.settings.move." + m.key;
            options[i] = OptionInstance.createBoolean(key, OptionInstance.cachedConstantTooltip(Component.translatable(key + ".tip")),
                    m.on(), m::set);
        }
        options[moves.length] = OptionInstance.createBoolean("kinetrix_moves.settings.move.kickCutscene",
                OptionInstance.cachedConstantTooltip(Component.translatable("kinetrix_moves.settings.move.kickCutscene.tip")),
                HudConfig.kickCutscene(), HudConfig.KICK_CUTSCENE::set);
        list.addSmall(options);
        addWidget(list);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 100, height - 27, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        list.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        HudConfig.SPEC.save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return parent != null && parent.isPauseScreen();
    }
}
