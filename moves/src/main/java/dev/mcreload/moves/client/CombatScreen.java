package dev.mcreload.moves.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.Progress;
import dev.mcreload.moves.Scrolls;
import dev.mcreload.moves.Skill;
import dev.mcreload.moves.Stamina;
import dev.mcreload.moves.net.MovesNet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.client.settings.KeyModifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The combat menu on K (author, 2026-11-16): the build of the wheel (6 places; above it, smaller, the row of 5 perks:
 * "abilities like the deep roll"; every move with a lock until learned, a search that finds by other words too, a
 * preview of each), the moves' own keys, the guide (the fight, the energy bar, how
 * it fills, its ranks), and the old settings. Drawn in vanilla's way, fitting every GUI scale.
 */
public final class CombatScreen extends Screen {
    public enum Tab { BUILD, KEYS, GUIDE }

    private static final int ROW = 18, SLOT = 26, PERK = 22, PERK_GAP = 3;
    private static Tab lastTab = Tab.BUILD;

    private final Screen parent;
    private Tab tab;
    private EditBox search;
    private final List<Skill> shown = new ArrayList<>();
    private int scroll, selectedSlot = -1;
    private Skill hovered, focused;
    private SkillPreview preview;
    /** The keys' page: the skill whose key is being set (the next key pressed). */
    private Skill listening;
    private int guideScroll;
    private List<FormattedCharSequence> guideLines = List.of();

    public CombatScreen(Screen parent) {
        this(parent, lastTab);
    }

    public CombatScreen(Screen parent, Tab tab) {
        super(Component.translatable("kinetrix_combat.combat.title"));
        this.parent = parent;
        this.tab = tab;
    }

    /** The move shown in the preview and the description (the self-test's: no mouse over the list there). */
    void focus(Skill k) {
        focused = k;
    }

    private Progress progress() {
        return Stamina.of(minecraft.player).progress;
    }

    // ------------------------------------------------------------------ layout

    private int top() { return 46; }
    private int bottom() { return height - 30; }
    private int wheelX() { return 12 + 78; }
    private int listX() { return 12 + 160; }
    private int listW() { return Mth.clamp(width - listX() - 130, 110, 170); }
    private int previewX() { return listX() + listW() + 8; }

    @Override
    protected void init() {
        lastTab = tab;
        if (minecraft.player != null && preview == null) preview = new SkillPreview();
        int tw = Math.min(100, (width - 40) / 4);
        int tx = width / 2 - (tw * 4 + 12) / 2;
        String[] names = {"build", "keys", "guide"};
        for (int i = 0; i < 3; i++) {
            Tab t = Tab.values()[i];
            Button b = Button.builder(Component.translatable("kinetrix_combat.combat.tab." + names[i]), x -> {
                tab = t;
                listening = null;
                rebuildWidgets();
            }).bounds(tx + i * (tw + 4), 18, tw, 20).build();
            b.active = tab != t;
            addRenderableWidget(b);
        }
        addRenderableWidget(Button.builder(Component.translatable("kinetrix_combat.combat.tab.settings"),
                x -> minecraft.setScreen(new MovesMenuScreen(this))).bounds(tx + 3 * (tw + 4), 18, tw, 20).build());
        if (tab == Tab.BUILD) {
            search = new EditBox(font, listX(), top(), listW(), 16, Component.translatable("kinetrix_combat.combat.search"));
            search.setHint(Component.translatable("kinetrix_combat.combat.search").withStyle(ChatFormatting.DARK_GRAY));
            search.setResponder(q -> {
                scroll = 0;
                filter();
            });
            addRenderableWidget(search);
            addRenderableWidget(Button.builder(Component.translatable("kinetrix_combat.combat.clear"), b -> {
                progress().clearWheel();
                MovesNet.CHANNEL.sendToServer(new MovesNet.Equip(-1, -1));
            }).bounds(wheelX() - 45, bottom() - 20, 90, 20).build());
            filter();
        }
        if (tab == Tab.GUIDE) guideLines = font.split(Component.translatable("kinetrix_combat.guide.text"), width - 60);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 50, height - 25, 100, 20).build());
    }

    private void filter() {
        shown.clear();
        String q = search == null ? "" : search.getValue();
        for (Skill k : Skill.values()) if (SkillSearch.matches(k, q)) shown.add(k);
    }

    @Override
    public void tick() {
        if (search != null) search.tick();
        if (preview != null) {
            Skill k = hovered != null ? hovered : focused;
            preview.show(k);
            preview.tick();
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);
        hovered = null;
        switch (tab) {
            case BUILD -> renderBuild(g, mx, my, pt);
            case KEYS -> renderKeys(g, mx, my);
            case GUIDE -> renderGuide(g);
        }
        super.render(g, mx, my, pt);
        if (tab == Tab.BUILD && hovered != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Scrolls.name(hovered).copy().withStyle(ChatFormatting.YELLOW));
            tip.add(Component.translatable("kinetrix_combat.weapon." + hovered.weapon.name().toLowerCase()).withStyle(ChatFormatting.GRAY));
            if (hovered.perk) tip.add(Component.translatable("kinetrix_combat.combat.perk").withStyle(ChatFormatting.DARK_AQUA));
            String row = hovered.perk ? "perks" : "wheel";
            tip.add(progress().learned.contains(hovered) ? Component.translatable(progress().equipped(hovered)
                    ? "kinetrix_combat.combat.on_" + row : "kinetrix_combat.combat.learned_" + row).withStyle(ChatFormatting.GREEN)
                    : Component.translatable("kinetrix_combat.combat.locked").withStyle(ChatFormatting.RED));
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    /** The top of the perks' row (its label above it). */
    private int perksY() { return top() + 11; }

    /** The top of the wheel's ring (its label above it). */
    private int wheelTop() { return perksY() + PERK + 17; }

    private int wheelR() { return Mth.clamp((bottom() - 26 - wheelTop() - SLOT) / 2, 24, 40); }

    /** The centre of place {@code i}: the wheel's 0 to 5 round its middle, the perks' 6 to 10 in a row above it. */
    private int[] placeAt(int i) {
        if (i >= Skill.WHEEL) {
            int n = i - Skill.WHEEL, row = Skill.PERKS * PERK + (Skill.PERKS - 1) * PERK_GAP;
            return new int[]{wheelX() - row / 2 + n * (PERK + PERK_GAP) + PERK / 2, perksY() + PERK / 2};
        }
        int r = wheelR(), cy = wheelTop() + SLOT / 2 + r;
        double a = Math.toRadians(-90 + 60 * i);
        return new int[]{wheelX() + (int) Math.round(Math.cos(a) * r), cy + (int) Math.round(Math.sin(a) * r)};
    }

    private static int size(int place) {
        return place >= Skill.WHEEL ? PERK : SLOT;
    }

    private void renderBuild(GuiGraphics g, int mx, int my, float pt) {
        Progress pr = progress();
        // the perks above, the wheel below them
        g.drawCenteredString(font, Component.translatable("kinetrix_combat.combat.perks", count(pr.perks), Skill.PERKS), wheelX(), perksY() - 10, 0xA0E0D8);
        g.drawCenteredString(font, Component.translatable("kinetrix_combat.combat.wheel", count(pr.wheel), Skill.WHEEL), wheelX(), wheelTop() - 11, 0xFFD8A0);
        for (int i = 0; i < Progress.PLACES; i++) {
            int[] c = placeAt(i);
            int sz = size(i), x = c[0] - sz / 2, y = c[1] - sz / 2;
            boolean over = mx >= x && mx < x + sz && my >= y && my < y + sz;
            Skill k = pr.at(i);
            if (over && k != null) hovered = k;
            boolean perk = i >= Skill.WHEEL;
            frame(g, x, y, sz, sz, i == selectedSlot ? 0xFFFFD860 : over ? 0xFFC0C0C0 : perk ? 0xFF3E7A74 : 0xFF555555);
            if (k == null) {
                g.drawCenteredString(font, "+", x + sz / 2, y + sz / 2 - 4, 0x808080);
            } else if (perk) {
                g.renderItem(SkillPreview.icon(k.weapon), x + (sz - 16) / 2, y + (sz - 16) / 2);
            } else {
                g.renderItem(SkillPreview.icon(k.weapon), x + (sz - 16) / 2, y + 2);
                g.pose().pushPose();
                g.pose().translate(x + sz / 2f, y + sz - 6, 200);
                g.pose().scale(0.45f, 0.45f, 1f);
                g.drawCenteredString(font, font.plainSubstrByWidth(shortName(k), (int) ((sz - 2) / 0.45f)), 0, 0, 0xFFFFFF);
                g.pose().popPose();
            }
        }
        // the list
        int lx = listX(), lw = listW(), ly = top() + 20, lh = bottom() - ly;
        g.fill(lx, ly, lx + lw, ly + lh, 0x80000000);
        int rows = lh / ROW;
        scroll = Mth.clamp(scroll, 0, Math.max(0, shown.size() - rows));
        g.enableScissor(lx, ly, lx + lw, ly + lh);
        for (int r = 0; r < rows && r + scroll < shown.size(); r++) {
            Skill k = shown.get(r + scroll);
            int y = ly + r * ROW;
            boolean over = mx >= lx && mx < lx + lw && my >= y && my < y + ROW;
            if (over) hovered = k;
            boolean learned = pr.learned.contains(k);
            if (over) g.fill(lx, y, lx + lw, y + ROW, 0x40FFFFFF);
            if (k == focused) g.fill(lx, y, lx + 2, y + ROW, 0xFFFFD860);
            g.renderItem(SkillPreview.icon(k.weapon), lx + 2, y + 1);
            int color = learned ? 0xFFFFFF : 0x808080;
            g.drawString(font, font.plainSubstrByWidth(Scrolls.name(k).getString(), lw - 34), lx + 21, y + 5, color);
            if (!learned) lock(g, lx + lw - 11, y + 4);
            else if (pr.equipped(k)) g.drawString(font, "●", lx + lw - 10, y + 5, k.perk ? 0x70E0D0 : 0xFFD860);
        }
        g.disableScissor();
        // the preview and what it is
        Skill k = hovered != null ? hovered : focused;
        int px = previewX(), pw = width - px - 10;
        if (pw >= 60 && k != null && preview != null) {
            int ph = Math.min(110, (bottom() - top()) / 2 + 20);
            g.fill(px, top(), px + pw, top() + ph, 0x60000000);
            preview.render(g, px + pw / 2, top() + ph - 8, ph - 28, 35f + (minecraft.player.tickCount + pt) * 0.6f, pt);
            int ty = top() + ph + 4;
            g.drawString(font, Scrolls.name(k), px, ty, pr.learned.contains(k) ? 0xFFFFD860 : 0xFF909090);
            List<FormattedCharSequence> lines = font.split(Component.translatable("kinetrix_combat.settings.move." + k.key + ".tip"), pw);
            for (int i = 0; i < lines.size() && ty + 12 + i * 9 < bottom() - 4; i++) g.drawString(font, lines.get(i), px, ty + 12 + i * 9, 0xD0D0D0);
        }
    }

    private static int count(Skill[] row) {
        int n = 0;
        for (Skill k : row) if (k != null) n++;
        return n;
    }

    /** A short name for a slot: the move's name up to its bracket. */
    static String shortName(Skill k) {
        String n = Scrolls.name(k).getString();
        int b = n.indexOf(" (");
        return b > 0 ? n.substring(0, b) : n;
    }

    static void frame(GuiGraphics g, int x, int y, int w, int h, int edge) {
        g.fill(x, y, x + w, y + h, 0xC0101010);
        g.fill(x, y, x + w, y + 1, edge);
        g.fill(x, y + h - 1, x + w, y + h, edge);
        g.fill(x, y, x + 1, y + h, edge);
        g.fill(x + w - 1, y, x + w, y + h, edge);
    }

    /** A little padlock, 7 x 9: not learned yet. */
    static void lock(GuiGraphics g, int x, int y) {
        int dark = 0xFF3A3A3A, gold = 0xFFD8B040, light = 0xFFF4DC80;
        g.fill(x + 1, y, x + 6, y + 1, dark);
        g.fill(x + 1, y, x + 2, y + 4, dark);
        g.fill(x + 5, y, x + 6, y + 4, dark);
        g.fill(x, y + 4, x + 7, y + 9, dark);
        g.fill(x + 1, y + 5, x + 6, y + 8, gold);
        g.fill(x + 1, y + 5, x + 3, y + 6, light);
        g.fill(x + 3, y + 6, x + 4, y + 8, dark);
    }

    private void renderKeys(GuiGraphics g, int mx, int my) {
        int x = width / 2 - 170, y0 = top();
        List<FormattedCharSequence> hint = font.split(Component.translatable("kinetrix_combat.combat.keys.hint"), 340);
        for (int i = 0; i < Math.min(2, hint.size()); i++) g.drawString(font, hint.get(i), x, y0 - 4 + i * 9, 0xA0A0A0);
        int rows = (bottom() - y0 - 16) / ROW;
        scroll = Mth.clamp(scroll, 0, Math.max(0, Skill.values().length - rows));
        for (int r = 0; r < rows && r + scroll < Skill.values().length; r++) {
            Skill k = Skill.values()[r + scroll];
            int y = y0 + 16 + r * ROW;
            g.renderItem(SkillPreview.icon(k.weapon), x, y);
            boolean learned = progress().learned.contains(k);
            g.drawString(font, Scrolls.name(k), x + 20, y + 4, learned ? 0xFFFFFF : 0x808080);
            KeyMapping km = MovesClient.SKILL_KEYS[k.ordinal()];
            Component key = listening == k ? Component.literal("> ? <").withStyle(ChatFormatting.YELLOW)
                    : km.isUnbound() ? Component.literal("—").withStyle(ChatFormatting.GRAY) : km.getTranslatedKeyMessage();
            int bx = x + 250, bw = 90;
            boolean over = mx >= bx && mx < bx + bw && my >= y && my < y + 16;
            frame(g, bx, y, bw, 16, over ? 0xFFFFFFFF : 0xFF808080);
            g.drawCenteredString(font, key, bx + bw / 2, y + 4, 0xFFFFFF);
        }
    }

    private void renderGuide(GuiGraphics g) {
        int x = 30, y0 = top(), h = bottom() - y0;
        int rows = h / 10;
        guideScroll = Mth.clamp(guideScroll, 0, Math.max(0, guideLines.size() - rows));
        g.fill(x - 6, y0 - 4, width - x + 6, bottom(), 0x80000000);
        for (int i = 0; i < rows && i + guideScroll < guideLines.size(); i++) g.drawString(font, guideLines.get(i + guideScroll), x, y0 + i * 10, 0xE0E0E0);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (tab == Tab.BUILD) {
            Progress pr = progress();
            for (int i = 0; i < Progress.PLACES; i++) {
                int[] c = placeAt(i);
                if (Math.abs(mx - c[0]) <= size(i) / 2.0 && Math.abs(my - c[1]) <= size(i) / 2.0) {
                    if (button == 1) equip(i, null);
                    else selectedSlot = selectedSlot == i ? -1 : i;
                    click();
                    return true;
                }
            }
            int lx = listX(), lw = listW(), ly = top() + 20;
            if (mx >= lx && mx < lx + lw && my >= ly && my < bottom()) {
                int r = (int) ((my - ly) / ROW) + scroll;
                if (r >= 0 && r < shown.size()) {
                    Skill k = shown.get(r);
                    focused = k;
                    if (pr.learned.contains(k)) {
                        // into the place picked, if it is of its row (a perk's square, the wheel's place), else its row's first free one
                        boolean fits = selectedSlot >= 0 && (selectedSlot >= Skill.WHEEL) == k.perk;
                        int slot = fits ? selectedSlot : firstFree(pr, k);
                        if (slot >= 0) equip(slot, k);
                        selectedSlot = -1;
                    }
                    click();
                    return true;
                }
            }
        }
        if (tab == Tab.KEYS) {
            int x = width / 2 - 170, y0 = top();
            int rows = (bottom() - y0 - 16) / ROW;
            for (int r = 0; r < rows && r + scroll < Skill.values().length; r++) {
                int y = y0 + 16 + r * ROW, bx = x + 250;
                if (mx >= bx && mx < bx + 90 && my >= y && my < y + 16) {
                    listening = Skill.values()[r + scroll];
                    click();
                    return true;
                }
            }
        }
        return false;
    }

    /** The first free place of the skill's row (numbered as the places: the perks' from 6), or -1. */
    private static int firstFree(Progress pr, Skill k) {
        Skill[] row = pr.row(k);
        for (int i = 0; i < row.length; i++) if (row[i] == null) return k.perk ? Skill.WHEEL + i : i;
        return -1;
    }

    private void equip(int slot, Skill k) {
        progress().equip(slot, k);
        MovesNet.CHANNEL.sendToServer(new MovesNet.Equip(slot, k == null ? -1 : k.ordinal()));
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (tab == Tab.GUIDE) guideScroll -= (int) Math.signum(delta) * 3;
        else scroll -= (int) Math.signum(delta);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (tab == Tab.KEYS && listening != null) {
            KeyMapping km = MovesClient.SKILL_KEYS[listening.ordinal()];
            if (key == InputConstants.KEY_ESCAPE) {
                km.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
            } else if (!isModifier(key)) {
                KeyModifier mod = hasControlDown() ? KeyModifier.CONTROL : hasShiftDown() ? KeyModifier.SHIFT : hasAltDown() ? KeyModifier.ALT : KeyModifier.NONE;
                km.setKeyModifierAndCode(mod, InputConstants.getKey(key, scan));
            } else {
                return true;     // a modifier alone: wait for the key with it
            }
            KeyMapping.resetMapping();
            minecraft.options.save();
            listening = null;
            return true;
        }
        // K again closes the menu (not while typing a search)
        if (MovesClient.MENU.matches(key, scan) && (search == null || !search.isFocused())) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private static boolean isModifier(int key) {
        return key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL || key == InputConstants.KEY_LSHIFT
                || key == InputConstants.KEY_RSHIFT || key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT;
    }

    @Override
    public void removed() {
        if (preview != null) preview.show(null);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Ticks left of the skill's wait and its whole wait (the tray's numbers). */
    static int[] cooldown(Stamina s, Skill k) {
        Move m = switch (k) {
            case DASH -> Move.DASH;
            case SWORD_SPIN -> Move.SWORD_SPIN;
            case AIR_SPIN -> Move.AIR_SPIN;
            case CHAIN -> Move.CHAIN_LEAP;
            case THROW -> Move.THROW;
            case WHIRL -> Move.WHIRL;
            case AXE_COMBO -> Move.AXE_TRIPLE;
            case VOLLEY, HAIL -> Move.BARRAGE;
            case MARKS -> Move.MARKED_SPIN_LEFT;
            case LEGOLAS -> Move.LEGOLAS_LEFT;
            case SLIDE_SHOT -> Move.SLIDE_SHOT;
            case DIVE -> Move.DIVE;
            case XBOW_G -> Move.XBOW_VOLLEY;
            case XBOW_FAN -> Move.XBOW_FAN;
            case XBOW_ROLLS -> Move.XBOW_ROLL_BACK;
            case GUARD_KICK -> Move.GUARD_KICK;
            case GUARD_COUNTER -> Move.GUARD_SHOVE;
            case GUARD_FINISH -> Move.GUARD_FINISH;
        };
        return new int[]{StrikeInput.cooldown(s, m), Math.max(1, MovesHud.cooldownTotal(m))};
    }
}
