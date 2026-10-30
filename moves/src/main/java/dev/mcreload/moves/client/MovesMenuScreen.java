package dev.mcreload.moves.client;

import com.mojang.brigadier.Command;
import dev.mcreload.moves.HudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

import java.util.List;
import java.util.function.Consumer;

/**
 * The mod's menu ({@code /kinetrix_moves}, or Config in the Mods list; author, 2026-10-30: "the
 * mechanics and the attacks in the menu of kinetrix_moves, on the command /kinetrix_moves"): every
 * attack and mechanic, each with its keys (as they are bound now) and a switch for this player's
 * keys, in vanilla's scrolling list so it fits any GUI scale; the hover tells what it does. The look
 * of the bars and of the tray is one button away (MovesSettingsScreen).
 */
public final class MovesMenuScreen extends Screen {
    /** The attacks, then the moves and the rest, as the menu lists them. */
    private static final HudConfig.Moves[] ATTACKS = {HudConfig.Moves.SWORD_SPIN, HudConfig.Moves.WHIRL, HudConfig.Moves.DASH,
            HudConfig.Moves.FINISHER, HudConfig.Moves.SLIDE_ATTACK, HudConfig.Moves.KICK, HudConfig.Moves.ROLL_KICK,
            HudConfig.Moves.PLUNGE, HudConfig.Moves.VOLLEY, HudConfig.Moves.MARKS, HudConfig.Moves.FLOW, HudConfig.Moves.LEGOLAS};
    private static final HudConfig.Moves[] MOVES = {HudConfig.Moves.DODGES, HudConfig.Moves.ROLLS, HudConfig.Moves.FAST_RUN,
            HudConfig.Moves.SLIDE, HudConfig.Moves.VAULT, HudConfig.Moves.DIVE, HudConfig.Moves.GUARD, HudConfig.Moves.FALL_ROLL,
            HudConfig.Moves.LEDGE_GRAB, HudConfig.Moves.AUTO_HANG, HudConfig.Moves.LEDGE_HOPS};
    private static final int ROW_W = 340;

    private final Screen parent;
    private Rows rows;

    public MovesMenuScreen(Screen parent) {
        super(Component.translatable("kinetrix_moves.menu.title"));
        this.parent = parent;
    }

    static void registerCommand(RegisterClientCommandsEvent event) {
        Command<CommandSourceStack> open = ctx -> {
            Minecraft mc = Minecraft.getInstance();
            // After the chat screen has closed.
            mc.tell(() -> mc.setScreen(new MovesMenuScreen(null)));
            return 1;
        };
        event.getDispatcher().register(Commands.literal("kinetrix_moves").executes(open));
    }

    @Override
    protected void init() {
        rows = new Rows(minecraft, width, height, 36, height - 34);
        rows.header("kinetrix_moves.menu.attacks");
        for (HudConfig.Moves m : ATTACKS) rows.move(m);
        rows.header("kinetrix_moves.menu.moves");
        for (HudConfig.Moves m : MOVES) rows.move(m);
        rows.header("kinetrix_moves.menu.view");
        rows.toggle("kinetrix_moves.settings.move.kickCutscene", HudConfig.kickCutscene(), HudConfig.KICK_CUTSCENE::set);
        rows.toggle("kinetrix_moves.settings.move.attackCamera", HudConfig.attackCamera(), HudConfig.ATTACK_CAMERA::set);
        addWidget(rows);
        int bw = Math.min(150, (width - 30) / 2);
        addRenderableWidget(Button.builder(Component.translatable("kinetrix_moves.menu.look"), b -> {
                    HudConfig.SPEC.save();
                    minecraft.setScreen(new MovesSettingsScreen(this));
                })
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("kinetrix_moves.menu.look.tip")))
                .bounds(width / 2 - bw - 4, height - 27, bw, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 + 4, height - 27, bw, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        rows.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("kinetrix_moves.menu.hint"), width / 2, 22, 0xA0A0A0);
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

    /** The keys the menu names, as they are bound now: G, R, C, the left and the right button. */
    private static Object[] keys() {
        Minecraft mc = Minecraft.getInstance();
        return new Object[]{MovesHud.shortKey(MovesClient.ABILITY), MovesHud.shortKey(MovesClient.DODGE),
                MovesHud.shortKey(MovesClient.slideKey(mc)), MovesHud.shortKey(mc.options.keyAttack), MovesHud.shortKey(mc.options.keyUse)};
    }

    // ------------------------------------------------------------------ the list

    private final class Rows extends ContainerObjectSelectionList<Row> {
        Rows(Minecraft mc, int width, int height, int top, int bottom) {
            super(mc, width, height, top, bottom, 25);
        }

        void header(String key) {
            addEntry(new Header(Component.translatable(key)));
        }

        void move(HudConfig.Moves m) {
            String key = "kinetrix_moves.settings.move." + m.key;
            addEntry(new Switch(Component.translatable(key), Component.translatable(key + ".keys", keys()),
                    Component.translatable(key + ".tip"), m.on(), m::set));
        }

        void toggle(String key, boolean on, Consumer<Boolean> set) {
            addEntry(new Switch(Component.translatable(key), Component.translatable(key + ".keys", keys()),
                    Component.translatable(key + ".tip"), on, set));
        }

        @Override
        public int getRowWidth() {
            return Math.min(ROW_W, width - 24);
        }

        @Override
        protected int getScrollbarPosition() {
            return width / 2 + getRowWidth() / 2 + 6;
        }
    }

    private abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
    }

    /** A heading over a part of the list: the attacks, the moves, the look. */
    private final class Header extends Row {
        private final Component text;

        Header(Component text) {
            this.text = text;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int w, int h, int mouseX, int mouseY, boolean hovered, float pt) {
            g.drawCenteredString(font, text, left + w / 2, top + h - 13, 0xFFD050);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    /** A move: its name and keys, the switch on the right; the hover tells what it does. */
    private final class Switch extends Row {
        private final Component name, keys, tip;
        private final CycleButton<Boolean> button;

        Switch(Component name, Component keys, Component tip, boolean on, Consumer<Boolean> set) {
            this.name = name;
            this.keys = keys;
            this.tip = tip;
            this.button = CycleButton.onOffBuilder(on).displayOnlyValue()
                    .create(0, 0, 52, 20, name, (b, v) -> set.accept(v));
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int w, int h, int mouseX, int mouseY, boolean hovered, float pt) {
            int textW = w - 52 - 8;
            g.drawString(font, font.plainSubstrByWidth(name.getString(), textW), left + 2, top + 2, 0xFFFFFF, false);
            g.drawString(font, font.plainSubstrByWidth(keys.getString(), textW), left + 2, top + 13, 0x9A9A9A, false);
            button.setX(left + w - 52);
            button.setY(top);
            button.render(g, mouseX, mouseY, pt);
            if (hovered && mouseX < left + textW) setTooltipForNextRenderPass(font.split(tip, 220));
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(button);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(button);
        }
    }
}
