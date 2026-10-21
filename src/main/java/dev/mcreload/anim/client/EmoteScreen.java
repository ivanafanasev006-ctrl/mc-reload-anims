package dev.mcreload.anim.client;

import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.client.rig.RigRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Emote menu: a grid of buttons; picking one plays it and closes the menu. */
public final class EmoteScreen extends Screen {
    public EmoteScreen() {
        super(Component.translatable("screen.kinetrix.emotes"));
    }

    @Override
    protected void init() {
        int cols = 3, bw = 120, bh = 20, gap = 4;
        int n = MotionState.EMOTE_COUNT;
        int rows = (n + cols - 1) / cols;
        int x0 = (width - (cols * bw + (cols - 1) * gap)) / 2;
        int y0 = (height - (rows * bh + (rows - 1) * gap)) / 2 + 10;
        for (int i = 0; i < n; i++) {
            final int emote = i;
            int x = x0 + (i % cols) * (bw + gap), y = y0 + (i / cols) * (bh + gap);
            addRenderableWidget(Button.builder(
                    Component.translatable("emote.kinetrix." + MotionState.EMOTE_KEYS[i]),
                    b -> play(emote)).bounds(x, y, bw, bh).build());
        }
    }

    private void play(int emote) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            PlayerRig rig = RigRegistry.get(mc.player);
            if (rig != null) rig.motion.playEmote(emote);
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.kinetrix.emotes.hint"), width / 2, 34, 0xA0A0A0);
        super.render(g, mouseX, mouseY, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
