package dev.mcreload.anim.client.gallery;

import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.client.rig.RigRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * The animation gallery ({@code /kinetrix}): every animation and emote by category, what makes
 * it play, and a live preview on a copy of you that does it over and over (drag it to look
 * round). Emotes and fidgets can also be played on yourself.
 */
public final class GalleryScreen extends Screen {
    private static String lastCategory = Demos.CATEGORIES[0];
    private static String lastDemo;

    private final String startDemo;
    private DemoStage stage;
    private DemoList list;
    private String category;
    private Demo selected;
    private Button selfButton;
    private float viewYaw = 35f, viewPitch = 10f;
    private boolean dragging;

    // layout
    private int catX, catW, listX, listW, top, bottom, viewX, viewY, viewW, viewH;

    public GalleryScreen(String demoId) {
        super(Component.translatable("screen.kinetrix.gallery"));
        this.startDemo = demoId;
    }

    /** Open it once the chat (the command came from) has closed. */
    public static void openSoon(String demoId) {
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> {
            if (mc.player != null) mc.setScreen(new GalleryScreen(demoId));
        });
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        if (stage == null && minecraft != null && minecraft.player != null && minecraft.level != null) stage = new DemoStage();
        top = 26;
        bottom = height - 8;
        catX = 8;
        catW = Mth.clamp(width / 6, 70, 110);
        listX = catX + catW + 6;
        listW = Mth.clamp(width / 4, 100, 170);
        viewX = listX + listW + 8;
        viewW = width - viewX - 8;
        viewY = top;
        viewH = Math.max(80, (bottom - top) * 11 / 20);

        for (int i = 0; i < Demos.CATEGORIES.length; i++) {
            String c = Demos.CATEGORIES[i];
            addRenderableWidget(Button.builder(Component.translatable("kinetrix.cat." + c), b -> showCategory(c, null))
                    .bounds(catX, top + i * 22, catW, 20).build());
        }
        list = new DemoList(minecraft, listW, bottom - top, top, bottom);
        list.setLeftPos(listX);
        addWidget(list);

        int by = bottom - 20;
        selfButton = addRenderableWidget(Button.builder(Component.translatable("screen.kinetrix.gallery.self"), b -> playOnSelf())
                .bounds(viewX, by, Math.max(60, viewW / 2 - 2), 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(viewX + viewW / 2 + 2, by, Math.max(60, viewW / 2 - 2), 20).build());

        Demo first = startDemo != null ? Demos.byId(startDemo) : selected != null ? selected : lastDemo != null ? Demos.byId(lastDemo) : null;
        showCategory(first != null ? first.category() : category != null ? category : lastCategory, first);
    }

    private void showCategory(String c, Demo pick) {
        category = c;
        lastCategory = c;
        List<Demo> demos = Demos.in(c);
        list.show(demos);
        select(pick != null ? pick : demos.isEmpty() ? null : demos.get(0));
    }

    /** Show this one, switching to its category if need be (also used by the self-test). */
    public void select(Demo d) {
        if (d != null && !d.category().equals(category)) {
            showCategory(d.category(), d);
            return;
        }
        selected = d;
        if (d == null) return;
        lastDemo = d.id();
        list.highlight(d);
        if (stage != null && stage.ready()) stage.play(d);
        if (selfButton != null) selfButton.active = d.onSelf() != null;
    }

    private void playOnSelf() {
        if (selected == null || selected.onSelf() == null || minecraft == null || minecraft.player == null) return;
        PlayerRig rig = RigRegistry.get(minecraft.player);
        onClose();
        if (rig != null) selected.onSelf().accept(rig.motion);
    }

    @Override
    public void tick() {
        if (stage != null) stage.update();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        list.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 9, 0xFFFFFF);

        // The stage.
        g.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0x60000000);
        if (stage != null && stage.ready() && selected != null) {
            int size = (int) (viewH * 0.62f);
            float pt = minecraft.getFrameTime();
            g.enableScissor(viewX, viewY, viewX + viewW, viewY + viewH);
            stage.render(g, viewX + viewW / 2, viewY + viewH - viewH / 7, size, viewYaw, viewPitch, pt);
            if (stage.napping()) zzz(g, viewX + viewW / 2 + size / 6, viewY + viewH / 4, stage.time());
            g.disableScissor();
            if (!selected.previewable()) {
                g.drawCenteredString(font, Component.translatable("screen.kinetrix.gallery.world"), viewX + viewW / 2, viewY + 6, 0xFFAA55);
            } else {
                g.drawString(font, Component.translatable("screen.kinetrix.gallery.drag"), viewX + 4, viewY + viewH - 11, 0x909090);
            }
        }

        // What it is and when it plays.
        if (selected != null) {
            int ty = viewY + viewH + 6;
            g.drawString(font, Component.translatable(selected.nameKey()), viewX, ty, 0xFFFF55);
            ty += 13;
            List<FormattedCharSequence> lines = font.split(Component.translatable(selected.descKey()), viewW);
            int maxLines = Math.max(1, (bottom - 26 - ty) / 10);
            for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
                g.drawString(font, lines.get(i), viewX, ty + i * 10, 0xDDDDDD);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (dev.mcreload.anim.client.selftest.SelfTest.enabled()) {
            g.flush();
            dev.mcreload.anim.client.selftest.SelfTest.screenRendered(minecraft);
        }
    }

    /** "Z z z" over the dummy's head, one at a time, as over a sleeping player. */
    private void zzz(GuiGraphics g, int x, int y, float time) {
        int newest = Mth.floor(time);
        for (int k = newest - 1; k <= newest; k++) {
            float age = time - k;
            if (age < 0f || age >= 1.8f) continue;
            float u = age / 1.8f;
            int alpha = (int) (255 * Math.min(1f, u / 0.12f) * (1f - Mth.clamp((u - 0.45f) / 0.55f, 0f, 1f)));
            if (alpha < 8) continue;
            float scale = 1f + 1.2f * u;
            g.pose().pushPose();
            g.pose().translate(x + ((k & 1) == 0 ? 1 : -1) * 18f * u, y - 40f * u, 200);
            g.pose().scale(scale, scale, 1f);
            g.drawString(font, Math.floorMod(k, 3) == 0 ? "Z" : "z", 0, 0, (alpha << 24) | 0xFFFFFF);
            g.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mx >= viewX && mx < viewX + viewW && my >= viewY && my < viewY + viewH) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            viewYaw += (float) dx * 1.5f;
            viewPitch = Mth.clamp(viewPitch + (float) dy, -30f, 60f);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    /** The list of animations in the chosen category. */
    private final class DemoList extends ObjectSelectionList<DemoList.Entry> {
        DemoList(Minecraft mc, int w, int h, int y0, int y1) {
            super(mc, w, h, y0, y1, 14);
            setRenderBackground(false);
            setRenderTopAndBottom(false);
        }

        void show(List<Demo> demos) {
            clearEntries();
            for (Demo d : demos) addEntry(new Entry(d));
            setScrollAmount(0);
        }

        void highlight(Demo d) {
            for (Entry e : children()) {
                if (e.demo == d) {
                    setSelected(e);
                    return;
                }
            }
        }

        @Override
        public int getRowWidth() {
            return width - 10;
        }

        @Override
        protected int getScrollbarPosition() {
            return x0 + width - 6;
        }

        final class Entry extends ObjectSelectionList.Entry<Entry> {
            final Demo demo;

            Entry(Demo demo) {
                this.demo = demo;
            }

            @Override
            public void render(GuiGraphics g, int index, int y, int x, int w, int h, int mouseX, int mouseY, boolean hovered, float pt) {
                int color = demo == selected ? 0xFFFF55 : demo.previewable() ? 0xFFFFFF : 0xA0A0A0;
                g.drawString(font, font.substrByWidth(Component.translatable(demo.nameKey()), w - 4).getString(), x + 2, y + 2, color);
            }

            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                select(demo);
                return true;
            }

            @Override
            public Component getNarration() {
                return Component.translatable(demo.nameKey());
            }
        }
    }
}
