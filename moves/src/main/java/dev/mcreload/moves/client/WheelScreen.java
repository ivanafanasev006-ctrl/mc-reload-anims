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
 * which weapon to take. Above the wheel, smaller, the row of ten perks; on its right the square of four of the class's
 * awakening abilities (the ultimates), made the same way (author, 2026-11-20: "a real wheel, pixelly, in Minecraft's
 * style; smaller icons; 10 perks in one row; the second is not a wheel but 4 equal squares in one whole piece"). Below,
 * the way to the menu's build.
 */
public final class WheelScreen extends Screen {
    private static final int PERK = 20, PERK_GAP = 2, ULT = 22, RING = 26;
    /** The place under the mouse (Progress's numbering); -1 none. */
    private int hoveredSlot = -1;

    public WheelScreen() {
        super(Component.translatable("kinetrix_combat.wheel.title"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("kinetrix_combat.wheel.menu"),
                b -> minecraft.setScreen(new CombatScreen(null, CombatScreen.Tab.BUILD))).bounds(width / 2 - 60, height - 34, 120, 20).build());
    }

    /** The ring's outer radius. */
    private int radius() {
        return Math.max(40, Math.min(66, (height - 70 - PERK - 14 - 20) / 2));
    }

    /** The wheel's middle: the perks' row and the wheel together in the middle above the button. */
    private int cy() {
        // (under the wheel, 20 px for the name of the one under the mouse)
        int whole = PERK + 14 + 2 * radius() + 20;
        return (height - 40 - whole) / 2 + PERK + 14 + radius();
    }

    /** The centre of place {@code i}. */
    private int[] at(int i) {
        Skill.Place place = Progress.placeOf(i);
        int n = i - Progress.firstOf(place);
        if (place == Skill.Place.PERK) {
            int row = Skill.PERKS * PERK + (Skill.PERKS - 1) * PERK_GAP;
            return new int[]{width / 2 - row / 2 + n * (PERK + PERK_GAP) + PERK / 2, cy() - radius() - 14 - PERK / 2};
        }
        if (place == Skill.Place.ULT) {
            int x0 = width / 2 + radius() + 18, y0 = cy() - ULT;
            return new int[]{x0 + (n % 2) * ULT + ULT / 2, y0 + (n / 2) * ULT + ULT / 2};
        }
        double a = Math.toRadians(-90 + 60 * i);
        int r = radius() - RING / 2;
        return new int[]{width / 2 + (int) Math.round(Math.cos(a) * r), cy() + (int) Math.round(Math.sin(a) * r)};
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
        int cx = width / 2, cy = cy(), rOut = radius(), rIn = rOut - RING;
        hoveredSlot = -1;
        for (int i = Skill.WHEEL; i < Progress.PLACES; i++) {
            int[] c = at(i);
            int half = Progress.placeOf(i) == Skill.Place.ULT ? ULT / 2 : PERK / 2;
            if (Math.abs(mx - c[0]) <= half && Math.abs(my - c[1]) <= half) hoveredSlot = i;
        }
        double dx = mx - cx, dy = my - cy, d2 = dx * dx + dy * dy;
        if (hoveredSlot < 0 && d2 >= (rIn - 6) * (rIn - 6) && d2 <= (rOut + 8) * (rOut + 8)) hoveredSlot = PixelWheel.part(dx, dy, 6);
        // the wheel, a ring of six; the part under the mouse in gold
        PixelWheel.draw(g, cx, cy, rIn, rOut, 6, hoveredSlot >= 0 && hoveredSlot < Skill.WHEEL && pr.at(hoveredSlot) != null ? hoveredSlot : -1);
        g.drawCenteredString(font, title, cx, cy - 4, 0xFFD8A0);
        // the awakening square: one framed piece of four
        int[] u0 = at(Progress.firstOf(Skill.Place.ULT));
        int ux = u0[0] - ULT / 2, uy = u0[1] - ULT / 2;
        CombatScreen.frame(g, ux - 1, uy - 1, 2 * ULT + 2, 2 * ULT + 2, 0xFF8A6AD0);
        g.fill(ux + ULT, uy, ux + ULT + 1, uy + 2 * ULT, 0xFF4A3A78);
        g.fill(ux, uy + ULT, ux + 2 * ULT, uy + ULT + 1, 0xFF4A3A78);
        g.drawCenteredString(font, Component.translatable("kinetrix_combat.wheel.ults"), ux + ULT, uy - 11, 0xC8B0FF);
        for (int i = 0; i < Progress.PLACES; i++) {
            int[] c = at(i);
            Skill.Place place = Progress.placeOf(i);
            Skill k = pr.at(i);
            boolean over = i == hoveredSlot && k != null;
            boolean dim = k != null && grey(k);
            if (place == Skill.Place.PERK) {
                int x = c[0] - PERK / 2, y = c[1] - PERK / 2;
                CombatScreen.frame(g, x, y, PERK, PERK, over && !dim ? 0xFFFFD860 : dim ? 0xFF404040 : 0xFF3E7A74);
            } else if (place == Skill.Place.ULT && over && !dim) {
                CombatScreen.frame(g, c[0] - ULT / 2, c[1] - ULT / 2, ULT, ULT, 0xFFFFD860);
            }
            if (k == null) {
                g.drawCenteredString(font, "·", c[0], c[1] - 4, 0x606060);
                continue;
            }
            CombatScreen.icon(g, SkillPreview.icon(k.weapon), c[0], c[1], 0.75f);
            int[] cd = CombatScreen.cooldown(s, k);
            if (cd[0] > 0) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 250);
                g.drawCenteredString(font, String.valueOf((cd[0] + 19) / 20), c[0], c[1] + 2, 0xFF6060);
                g.pose().popPose();
            }
            if (dim) g.fill(c[0] - 7, c[1] - 7, c[0] + 7, c[1] + 7, 0x90202020);
        }
        // what the one under the mouse is, its name in full
        if (hoveredSlot >= 0 && pr.at(hoveredSlot) != null) {
            g.drawCenteredString(font, Scrolls.name(pr.at(hoveredSlot)), cx, cy + rOut + 6, 0xFFFFFF);
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
