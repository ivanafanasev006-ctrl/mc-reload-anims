package dev.mcreload.moves.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mcreload.moves.Progress;
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
 * which weapon to take. Below, the way to the menu's build.
 */
public final class WheelScreen extends Screen {
    private static final int BOX = 40;
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
        return Math.min(80, Math.min(width, height - 50) / 3);
    }

    private int[] at(int i) {
        double a = Math.toRadians(-90 + 60 * i);
        int cx = width / 2, cy = (height - 30) / 2;
        return new int[]{cx + (int) Math.round(Math.cos(a) * radius()), cy + (int) Math.round(Math.sin(a) * radius())};
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
        int cx = width / 2, cy = (height - 30) / 2;
        double dx = mx - cx, dy = my - cy;
        hoveredSlot = -1;
        if (dx * dx + dy * dy > 18 * 18) {
            double a = Math.toDegrees(Math.atan2(dy, dx)) + 90 + 30;
            hoveredSlot = Math.floorMod((int) Math.floor(a / 60), 6);
        }
        g.drawCenteredString(font, title, cx, cy - 4, 0xFFD8A0);
        for (int i = 0; i < Skill.WHEEL; i++) {
            int[] c = at(i);
            int x = c[0] - BOX / 2, y = c[1] - BOX / 2;
            Skill k = pr.wheel[i];
            boolean over = i == hoveredSlot && k != null;
            boolean dim = k != null && grey(k);
            CombatScreen.frame(g, x, y, BOX, BOX, over && !dim ? 0xFFFFD860 : dim ? 0xFF404040 : 0xFF909090);
            if (k == null) {
                g.drawCenteredString(font, "—", c[0], c[1] - 4, 0x707070);
                continue;
            }
            g.renderItem(SkillPreview.icon(k.weapon), c[0] - 8, y + 5);
            String n = CombatScreen.shortName(k);
            g.pose().pushPose();
            g.pose().translate(c[0], y + 25, 200);
            g.pose().scale(0.55f, 0.55f, 1f);
            g.drawCenteredString(font, n, 0, 0, dim ? 0x707070 : 0xFFFFFF);
            g.pose().popPose();
            int[] cd = CombatScreen.cooldown(s, k);
            if (cd[0] > 0) {
                int h = Math.round(BOX * (cd[0] / (float) cd[1]));
                g.fill(x + 1, y + BOX - 1 - Math.min(BOX - 2, h), x + BOX - 1, y + BOX - 1, 0x90000000);
                g.drawCenteredString(font, String.valueOf((cd[0] + 19) / 20), c[0], y + BOX - 11, 0xFF6060);
            }
            if (dim) g.fill(x + 1, y + 1, x + BOX - 1, y + BOX - 1, 0x90202020);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0 || hoveredSlot < 0) return false;
        Skill k = Stamina.of(minecraft.player).progress.wheel[hoveredSlot];
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
