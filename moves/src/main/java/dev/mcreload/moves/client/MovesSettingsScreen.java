package dev.mcreload.moves.client;

import dev.mcreload.moves.HudConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

import java.util.Locale;

/**
 * How the bars look: style, shield icon, which side is which, the arrow counter and the tray of
 * cooldowns. Opened from the mod's menu (MovesMenuScreen); shows the bars and the tray as they will be.
 */
public final class MovesSettingsScreen extends Screen {
    private static final int BUTTON_H = 20, GAP = 4;
    private final Screen parent;
    /** Where the preview goes (below the buttons), or -1 when the window is too small for it. */
    private int previewTop = -1;

    public MovesSettingsScreen(Screen parent) {
        super(Component.translatable("kinetrix_moves.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // One column of five; in a small window (big GUI scale) two columns, the moves' button across both.
        boolean twoColumns = height < 32 + 5 * (BUTTON_H + GAP) + 36;
        int w = twoColumns ? Math.min(200, (width - 24) / 2) : Math.min(260, width - 20);
        int x0 = twoColumns ? width / 2 - w - GAP / 2 : (width - w) / 2;
        int y0 = twoColumns ? 32 : Math.max(32, height / 2 - 82);
        int[][] at = new int[5][];
        for (int i = 0; i < 5; i++) {
            at[i] = twoColumns ? new int[]{x0 + (i % 2) * (w + GAP), y0 + (i / 2) * (BUTTON_H + GAP)}
                    : new int[]{x0, y0 + i * (BUTTON_H + GAP)};
        }
        addRenderableWidget(CycleButton.<HudConfig.Style>builder(v -> Component.translatable(key("style", v.name())))
                .withValues(HudConfig.Style.values())
                .withInitialValue(HudConfig.style())
                .withTooltip(v -> Tooltip.create(Component.translatable(key("style", v.name()) + ".tip")))
                .create(at[0][0], at[0][1], w, BUTTON_H, Component.translatable("kinetrix_moves.settings.style"),
                        (b, v) -> HudConfig.STYLE.set(v)));
        addRenderableWidget(CycleButton.<HudConfig.Shape>builder(v -> Component.translatable(key("shape", v.name())))
                .withValues(HudConfig.Shape.values())
                .withInitialValue(HudConfig.shape())
                .create(at[1][0], at[1][1], w, BUTTON_H, Component.translatable("kinetrix_moves.settings.shape"),
                        (b, v) -> HudConfig.SHAPE.set(v)));
        addRenderableWidget(CycleButton.booleanBuilder(Component.translatable("kinetrix_moves.settings.sides.swapped"),
                        Component.translatable("kinetrix_moves.settings.sides.normal"))
                .withInitialValue(HudConfig.swapSides())
                .withTooltip(v -> Tooltip.create(Component.translatable("kinetrix_moves.settings.sides.tip")))
                .create(at[2][0], at[2][1], w, BUTTON_H, Component.translatable("kinetrix_moves.settings.sides"),
                        (b, v) -> HudConfig.SWAP_SIDES.set(v)));
        addRenderableWidget(CycleButton.onOffBuilder(HudConfig.arrows())
                .withTooltip(v -> Tooltip.create(Component.translatable("kinetrix_moves.settings.arrows.tip")))
                .create(at[3][0], at[3][1], w, BUTTON_H, Component.translatable("kinetrix_moves.settings.arrows"),
                        (b, v) -> HudConfig.ARROWS.set(v)));
        addRenderableWidget(CycleButton.onOffBuilder(HudConfig.tray())
                .withTooltip(v -> Tooltip.create(Component.translatable("kinetrix_moves.settings.tray.tip")))
                .create(at[4][0], at[4][1], twoColumns ? 2 * w + GAP : w, BUTTON_H, Component.translatable("kinetrix_moves.settings.tray"),
                        (b, v) -> HudConfig.TRAY.set(v)));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds((width - 150) / 2, height - 28, 150, BUTTON_H).build());
        int below = at[4][1] + BUTTON_H + 8;
        previewTop = below + 40 <= height - 32 ? below : -1;
    }

    private static String key(String what, String value) {
        return "kinetrix_moves.settings." + what + "." + value.toLowerCase(Locale.ROOT);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
        // The bars as they will look: shields 6.5 of 10, energy 60 %; under them the tray.
        if (previewTop >= 0 && width >= 200) {
            g.drawCenteredString(font, Component.translatable("kinetrix_moves.settings.preview"), width / 2, previewTop, 0xA0A0A0);
            boolean tray = HudConfig.tray();
            if (tray && width >= 400) {
                // side by side: the bars on the left, the tray on the right
                MovesHud.preview(g, width / 2 - 98, previewTop + 16);
                MovesHud.trayPreview(g, width / 2 + 98, previewTop + 14);
            } else {
                MovesHud.preview(g, width / 2, previewTop + 16);
                if (tray && previewTop + 70 <= height - 32) MovesHud.trayPreview(g, width / 2, previewTop + 44);
            }
        }
    }

    @Override
    public void onClose() {
        HudConfig.SPEC.save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return parent != null;
    }
}
