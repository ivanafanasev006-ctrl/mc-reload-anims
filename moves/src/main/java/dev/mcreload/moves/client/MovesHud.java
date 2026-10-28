package dev.mcreload.moves.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mcreload.moves.HudConfig;
import dev.mcreload.moves.KinetrixMoves;
import dev.mcreload.moves.Stamina;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

import java.util.Locale;

/**
 * The bars (docs/combat-spec.md, 9): shields as a row of grey icons on the left above armour,
 * energy as a bar with a lightning icon on the right above hunger, coloured in the chosen style
 * (sides swap in the settings), and an arrow counter left of the hotbar while a bow is in hand.
 *
 * <p>Rows take their place through Forge's shared row count ({@code leftHeight} / {@code
 * rightHeight}), so other mods' rows (thirst, extra hearts...) stack above or below ours instead of
 * on top of them. Textures: art/refined/hud (made by art/refined/hud/make_hud.py).</p>
 */
public final class MovesHud {
    /** The energy row is as wide as the hunger row: the 9-pixel icon, a gap, a 71-pixel bar. */
    private static final int BAR_W = 71, BAR_H = 5, ROW_W = 81;
    /** What the bar shows, eased towards the real value so refills and costs slide, not jump. */
    private static float shownEnergy = -1;

    static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.ARMOR_LEVEL.id(), "left_bar", MovesHud::left);
        event.registerAbove(VanillaGuiOverlay.FOOD_LEVEL.id(), "right_bar", MovesHud::right);
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "arrows", MovesHud::arrows);
    }

    private static boolean show(ForgeGui gui) {
        Minecraft mc = gui.getMinecraft();
        return !mc.options.hideGui && gui.shouldDrawSurvivalElements() && mc.player != null && MovesClient.serverHasMod();
    }

    private static void left(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        if (!show(gui)) return;
        Stamina s = Stamina.of(gui.getMinecraft().player);
        int x = width / 2 - 91, top = height - gui.leftHeight;
        if (HudConfig.swapSides()) energy(g, x, top, shownEnergy(s), s, time(gui));
        else gui.leftHeight += shields(g, x, top, s.shields, MovesClient.maxShields) - 10;
        gui.leftHeight += 10;
    }

    private static void right(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        if (!show(gui)) return;
        Stamina s = Stamina.of(gui.getMinecraft().player);
        int x = width / 2 + 91 - ROW_W, top = height - gui.rightHeight;
        if (HudConfig.swapSides()) gui.rightHeight += shields(g, x, top, s.shields, MovesClient.maxShields) - 10;
        else energy(g, x, top, shownEnergy(s), s, time(gui));
        gui.rightHeight += 10;
    }

    private static float time(ForgeGui gui) {
        Minecraft mc = gui.getMinecraft();
        return mc.level == null ? 0f : mc.level.getGameTime() + mc.getFrameTime();
    }

    /** The bars as they will look (the settings screen): shields 6.5 of 10, energy 60 %, around {@code cx}. */
    static void preview(GuiGraphics g, int cx, int top) {
        int left = cx - 91, right = cx + 91 - ROW_W;
        // A framed panel, so the preview does not mix with the real bars under the screen.
        g.fill(cx - 96, top - 5, cx + 96, top + 14, 0xFF6F6F6F);
        g.fill(cx - 95, top - 4, cx + 95, top + 13, 0xF0141414);
        float time = Minecraft.getInstance().level == null ? 0f : Minecraft.getInstance().level.getGameTime();
        if (HudConfig.swapSides()) {
            energy(g, left, top, 0.6f, null, time);
            shields(g, right, top, 6.5f, 10f);
        } else {
            shields(g, left, top, 6.5f, 10f);
            energy(g, right, top, 0.6f, null, time);
        }
    }

    // ------------------------------------------------------------------ shields

    /** Draws the shields from {@code (x, top)} in rows of ten going up; returns the height used. */
    private static int shields(GuiGraphics g, int x, int top, float shields, float max) {
        int icons = Math.max(1, Mth.ceil(max));
        String shape = HudConfig.shape() == HudConfig.Shape.ROUND ? "round" : "shield";
        RenderSystem.enableBlend();
        for (int i = 0; i < icons; i++) {
            float left = shields - i;
            String state = left >= 1f ? "full" : left >= 0.5f ? "half" : "empty";
            g.blit(shieldTex(shape + "_" + state), x + (i % 10) * 8, top - (i / 10) * 10, 0, 0, 9, 9, 9, 9);
        }
        RenderSystem.disableBlend();
        return ((icons - 1) / 10 + 1) * 10;
    }

    // ------------------------------------------------------------------ energy

    /** The bar's fill, eased towards the real value so refills and costs slide instead of jumping. */
    private static float shownEnergy(Stamina s) {
        float target = Mth.clamp(s.energy / Math.max(1f, MovesClient.maxEnergy), 0f, 1f);
        if (shownEnergy < 0) shownEnergy = target;
        shownEnergy += (target - shownEnergy) * 0.25f;
        if (Math.abs(target - shownEnergy) < 0.002f) shownEnergy = target;
        return shownEnergy;
    }

    /**
     * The lightning icon and the bar, {@link #ROW_W} wide from {@code x}, filled to {@code fraction}.
     * {@code s} gives the golden apple glow and the refusal flash (none in the preview).
     */
    private static void energy(GuiGraphics g, int x, int top, float fraction, Stamina s, float time) {
        boolean free = s != null && s.free();
        RenderSystem.enableBlend();
        g.blit(tex(free ? "energy_icon_infinite" : "energy_icon"), x, top, 0, 0, 9, 9, 9, 9);
        int bx = x + 10, by = top + 2;
        if (s != null && MovesClient.flash > 0 && (MovesClient.flash / 2) % 2 == 0) g.setColor(1f, 0.45f, 0.45f, 1f);
        g.blit(tex("energy_bar_bg"), bx, by, 0, 0, BAR_W, BAR_H, BAR_W, BAR_H);
        g.setColor(1f, 1f, 1f, 1f);
        int fill = Math.round(BAR_W * fraction);
        if (fill > 0) g.blit(tex("energy_bar_fill"), bx, by, 0, 0, fill, BAR_H, BAR_W, BAR_H);
        if (free && s.freeTotal > 0) {
            // Free energy (golden apples): a glowing bar that shrinks as the time runs out.
            int glow = Math.round(BAR_W * Mth.clamp(s.freeTicks / (float) s.freeTotal, 0f, 1f));
            float pulse = 0.65f + 0.35f * Mth.sin(time * 0.35f);
            g.setColor(1f, 1f, 1f, pulse);
            if (glow > 0) g.blit(tex("energy_bar_fill_infinite"), bx, by, 0, 0, glow, BAR_H, BAR_W, BAR_H);
            g.setColor(1f, 1f, 1f, 1f);
        }
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ arrows

    /** The arrow counter: an arrow and, under it, how many are in the inventory. Only with a bow in hand. */
    private static void arrows(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = gui.getMinecraft();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || p.isSpectator() || !HudConfig.arrows()) return;
        if (!(p.getMainHandItem().getItem() instanceof BowItem) && !(p.getOffhandItem().getItem() instanceof BowItem)) return;
        int count = 0;
        for (ItemStack st : p.getInventory().items) if (st.getItem() instanceof ArrowItem) count += st.getCount();
        for (ItemStack st : p.getInventory().offhand) if (st.getItem() instanceof ArrowItem) count += st.getCount();
        // Left of the hotbar, and of the off-hand slot when that is drawn there.
        int x = width / 2 - 91 - 4 - 16;
        if (!p.getOffhandItem().isEmpty() && p.getMainArm() == HumanoidArm.RIGHT) x -= 29;
        int y = height - 29;
        g.renderItem(new ItemStack(Items.ARROW), x, y);
        String text = count > 9999 ? "9999+" : String.format(Locale.ROOT, "%d", count);
        int color = count == 0 ? 0xFF5555 : 0xFFFFFF;
        g.drawString(mc.font, text, x + 8 - mc.font.width(text) / 2, height - 11, color, true);
    }

    /** Energy bar textures, in the chosen style. */
    private static ResourceLocation tex(String name) {
        return new ResourceLocation(KinetrixMoves.MODID, "textures/gui/hud/" + HudConfig.style().name().toLowerCase(Locale.ROOT) + "/" + name + ".png");
    }

    /** Shield icons: always steel grey, whatever the style (the author's choice). */
    private static ResourceLocation shieldTex(String name) {
        return new ResourceLocation(KinetrixMoves.MODID, "textures/gui/hud/shields/" + name + ".png");
    }

    private MovesHud() {}
}
