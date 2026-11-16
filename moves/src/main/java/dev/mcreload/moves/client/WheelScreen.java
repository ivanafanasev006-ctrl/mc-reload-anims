package dev.mcreload.moves.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mcreload.moves.Progress;
import dev.mcreload.moves.Scrolls;
import dev.mcreload.moves.Skill;
import dev.mcreload.moves.Stamina;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The wheel of the six moves, while Z is held (author, 2026-11-16): the mouse picks one, the left button makes it. With a
 * weapon in hand the others' moves are grey and do nothing; with none of them (an empty hand, anything else) a move says
 * which weapon to take. Above the wheel, smaller, the row of five perks (the deep roll...), made the same way. Below, the
 * way to the menu's build.
 */
public final class WheelScreen extends Screen {
    private static final int BOX = 34, PERK = 24, PERK_GAP = 4;
    /** The place under the mouse: the wheel's 0 to 5, the perks' 6 to 10; -1 none. */
    private int hoveredSlot = -1;

    public WheelScreen() {
        super(Component.translatable("kinetrix_combat.wheel.title"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("kinetrix_combat.wheel.menu"),
                b -> minecraft.setScreen(new CombatScreen(null, CombatScreen.Tab.BUILD))).bounds(width / 2 - 60, height - 34, 120, 20).build());
    }

    private int radius() {
        return Math.max(36, Math.min(62, (height - 70 - PERK - 14 - BOX) / 2));
    }

    /** The wheel's middle: the perks' row and the wheel together in the middle above the button. */
    private int cy() {
        // (under the wheel, 20 px for the name of the one under the mouse)
        int whole = PERK + 14 + 2 * radius() + BOX + 20;
        return (height - 40 - whole) / 2 + PERK + 14 + radius() + BOX / 2;
    }

    /** The centre of place {@code i}. */
    private int[] at(int i) {
        if (i >= Skill.WHEEL) {
            int n = i - Skill.WHEEL, row = Skill.PERKS * PERK + (Skill.PERKS - 1) * PERK_GAP;
            return new int[]{width / 2 - row / 2 + n * (PERK + PERK_GAP) + PERK / 2, cy() - radius() - BOX / 2 - 14 - PERK / 2};
        }
        double a = Math.toRadians(-90 + 60 * i);
        return new int[]{width / 2 + (int) Math.round(Math.cos(a) * radius()), cy() + (int) Math.round(Math.sin(a) * radius())};
    }

    private static int size(int i) {
        return i >= Skill.WHEEL ? PERK : BOX;
    }

    /** A weapon of some kind in hand, not this skill's: grey, it does nothing. */
    private boolean grey(Skill k) {
        if (k.weapon.in(minecraft.player)) return false;
        for (Skill.Weapon w : Skill.Weapon.values()) if (w != Skill.Weapon.BLADE && w != Skill.Weapon.ANY && w.in(minecraft.player)) return true;
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x60000000);
        Progress pr = Stamina.of(minecraft.player).progress;
        Stamina s = Stamina.of(minecraft.player);
        int cx = width / 2, cy = cy();
        hoveredSlot = -1;
        for (int i = Skill.WHEEL; i < Progress.PLACES; i++) {
            int[] c = at(i);
            if (Math.abs(mx - c[0]) <= PERK / 2 + PERK_GAP / 2 && Math.abs(my - c[1]) <= PERK / 2) hoveredSlot = i;
        }
        double dx = mx - cx, dy = my - cy;
        if (hoveredSlot < 0 && my > at(Skill.WHEEL)[1] + PERK / 2 && dx * dx + dy * dy > 16 * 16) {
            double a = Math.toDegrees(Math.atan2(dy, dx)) + 90 + 30;
            hoveredSlot = Math.floorMod((int) Math.floor(a / 60), 6);
        }
        g.drawCenteredString(font, title, cx, cy - 4, 0xFFD8A0);
        for (int i = 0; i < Progress.PLACES; i++) {
            int[] c = at(i);
            int sz = size(i), x = c[0] - sz / 2, y = c[1] - sz / 2;
            boolean perk = i >= Skill.WHEEL;
            Skill k = pr.at(i);
            boolean over = i == hoveredSlot && k != null;
            boolean dim = k != null && grey(k);
            CombatScreen.frame(g, x, y, sz, sz, over && !dim ? 0xFFFFD860 : dim ? 0xFF404040 : perk ? 0xFF3E7A74 : 0xFF909090);
            if (k == null) {
                g.drawCenteredString(font, "—", c[0], c[1] - 4, 0x707070);
                continue;
            }
            g.renderItem(SkillPreview.icon(k.weapon), c[0] - 8, perk ? c[1] - 8 : y + 4);
            if (!perk) {
                g.pose().pushPose();
                g.pose().translate(c[0], y + 23, 200);
                g.pose().scale(0.5f, 0.5f, 1f);
                g.drawCenteredString(font, font.plainSubstrByWidth(CombatScreen.shortName(k), (BOX - 2) * 2), 0, 0, dim ? 0x707070 : 0xFFFFFF);
                g.pose().popPose();
            }
            int[] cd = CombatScreen.cooldown(s, k);
            if (cd[0] > 0) {
                int h = Math.round(sz * (cd[0] / (float) cd[1]));
                g.fill(x + 1, y + sz - 1 - Math.min(sz - 2, h), x + sz - 1, y + sz - 1, 0x90000000);
                g.drawCenteredString(font, String.valueOf((cd[0] + 19) / 20), c[0], y + sz - 11, 0xFF6060);
            }
            if (dim) g.fill(x + 1, y + 1, x + sz - 1, y + sz - 1, 0x90202020);
        }
        // what the one under the mouse is, its name in full
        if (hoveredSlot >= 0 && pr.at(hoveredSlot) != null) {
            g.drawCenteredString(font, Scrolls.name(pr.at(hoveredSlot)), cx, cy + radius() + BOX / 2 + 6, 0xFFFFFF);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0 || hoveredSlot < 0) return false;
        Skill k = Stamina.of(minecraft.player).progress.at(hoveredSlot);
        if (k == null || grey(k)) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.6f));
            return true;
        }
        onClose();
        SkillUse.use(k);
        return true;
    }

    @Override
    public void tick() {
        // Z let go: the wheel closes
        InputConstants.Key key = MovesClient.WHEEL.getKey();
        long win = minecraft.getWindow().getWindow();
        boolean down = key.getValue() >= 0 && (key.getType() == InputConstants.Type.MOUSE
                ? org.lwjgl.glfw.GLFW.glfwGetMouseButton(win, key.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                : key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(win, key.getValue()));
        if (!down && !MovesSelfTest.enabled()) onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
