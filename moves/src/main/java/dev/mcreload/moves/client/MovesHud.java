package dev.mcreload.moves.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mcreload.moves.HudConfig;
import dev.mcreload.moves.KinetrixMoves;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.MovesConfig;
import dev.mcreload.moves.Stamina;
import dev.mcreload.moves.Strikes;
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
 * (sides swap in the settings), an arrow counter left of the hotbar while a bow is in hand, and the
 * tray of cooldowns in the bottom right corner (author, 2026-10-30: "the weapon's icon, the key G
 * and a bar beside it that fills while the attack is not there and is full when it is").
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
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "finisher_hint", MovesHud::finisherHint);
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "cooldown_tray", MovesHud::tray);
    }

    /** How visible the finisher hint is, 0..1 (it fades in and out instead of blinking). */
    private static float hint;
    private static long hintFrame;

    /**
     * Under the crosshair, while a weakened mob can be finished (StrikeInput): which button does it.
     * Fades in over 0.15 s and out over 0.25 s, pulsing a little so the eye catches it.
     */
    private static void finisherHint(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = gui.getMinecraft();
        long now = net.minecraft.Util.getMillis();
        float dt = hintFrame == 0 ? 0f : Math.min(0.1f, (now - hintFrame) / 1000f);
        hintFrame = now;
        boolean on = !mc.options.hideGui && mc.player != null && mc.screen == null && StrikeInput.finisherTarget() != null;
        hint = Mth.clamp(hint + (on ? dt / 0.15f : -dt / 0.25f), 0f, 1f);
        if (hint <= 0f) return;
        float pulse = 0.85f + 0.15f * Mth.sin(now / 1000f * Mth.TWO_PI * 1.5f);
        int alpha = Math.max(8, Math.round(255 * hint * pulse));
        net.minecraft.network.chat.Component text = net.minecraft.network.chat.Component.translatable(
                "kinetrix_moves.hint.finisher", mc.options.keyAttack.getTranslatedKeyMessage());
        int y = height / 2 + 10;
        int w = gui.getFont().width(text);
        g.fill(width / 2 - w / 2 - 3, y - 2, width / 2 + (w + 1) / 2 + 3, y + 10, (Math.round(0x90 * hint) << 24));
        g.drawCenteredString(gui.getFont(), text, width / 2, y, (alpha << 24) | 0xFFD050);
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
            // A shield just spent: the row shakes for a moment, like hearts when hurt.
            int shake = MovesClient.shieldFlash > 0 && ((i * 7 + MovesClient.shieldFlash) % 3 == 0) ? -1 : 0;
            g.blit(shieldTex(shape + "_" + state), x + (i % 10) * 8, top - (i / 10) * 10 + shake, 0, 0, 9, 9, 9, 9);
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

    // ------------------------------------------------------------------ the tray

    /** A row of the tray: the slot (as the hotbar's), a gap, the key over the bar. */
    private static final int SLOT = 22, TRAY_BAR = 48, TRAY_W = SLOT + 3 + TRAY_BAR, TRAY_ROW = 24, EDGE = 4;
    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");
    /** The strike a key was pressed for while it still waited (its bar flashes), and until when (ms). */
    private static Move refused;
    private static long refusedUntil;
    /** When each strike last came back (its slot glints), and its cooldown the frame before. */
    private static final java.util.Map<Move, Long> READY_AT = new java.util.EnumMap<>(Move.class);
    private static final java.util.Map<Move, Integer> WAS = new java.util.EnumMap<>(Move.class);
    /** How long each strike's wait was when it started (the bar's whole: shorter after G than after G held, halved by the apple). */
    private static final java.util.Map<Move, Integer> TOTAL = new java.util.EnumMap<>(Move.class);

    /** The key of a strike was pressed while it waited: its bar flashes red for a moment. */
    static void refused(Move move) {
        refused = move;
        refusedUntil = net.minecraft.Util.getMillis() + 450;
    }

    /** The strikes with a cooldown the weapon in hand makes, the ability (G) first. */
    static java.util.List<Move> traySlots(LocalPlayer p) {
        java.util.List<Move> out = new java.util.ArrayList<>();
        Move ability = StrikeInput.ability(p);
        if (ability != null) out.add(ability);
        if (Strikes.sword(p) && HudConfig.Moves.DASH.on()) out.add(Move.DASH);
        if (Strikes.slideWeapon(p) && HudConfig.Moves.SLIDE_ATTACK.on() && HudConfig.Moves.SLIDE.on()) out.add(Move.SLIDE_ATTACK);
        boolean bow = dev.mcreload.moves.Bow.isBow(p.getMainHandItem());
        if (bow && HudConfig.Moves.SLIDE_SHOT.on() && HudConfig.Moves.SLIDE.on()) out.add(Move.SLIDE_SHOT);
        // the volley at the marked (by its first step), the Legolas stance (by its dodge)
        if (bow && HudConfig.Moves.MARKS.on()) out.add(Move.MARKED_SPIN_LEFT);
        if (bow && HudConfig.Moves.LEGOLAS.on()) out.add(Move.LEGOLAS_LEFT);
        return out;
    }

    /** The whole cooldown of a strike, ticks. */
    static int cooldownTotal(Move move) {
        return switch (move) {
            case DASH -> MovesConfig.dashCooldownTicks();
            case WHIRL -> MovesConfig.whirlCooldownTicks();
            case SWORD_SPIN -> MovesConfig.swordSpinCooldownTicks();
            case SLIDE_ATTACK -> MovesConfig.slideAttackCooldownTicks();
            case MARKED_SPIN_LEFT -> MovesConfig.markedCooldownTicks();
            case BARRAGE -> MovesConfig.volleyCooldownTicks();
            case SLIDE_SHOT -> MovesConfig.slideShotCooldownTicks();
            case LEGOLAS_LEFT -> MovesConfig.legolasCooldownTicks();
            default -> 0;
        };
    }

    /**
     * The bar's whole for a strike waiting {@code left} ticks: how long the wait was when it started
     * (G's after the hail is 40 s, after the volley 10; the enchanted golden apple halves them), or,
     * not seen starting (just joined, another weapon in hand then), the usual wait.
     */
    private static int trayTotal(Move move, Stamina s, int left, Integer was) {
        int usual = s.cooled(cooldownTotal(move));
        if (was == null || left > was) TOTAL.put(move, Math.max(left, usual));
        return Math.max(left, TOTAL.getOrDefault(move, usual));
    }

    /**
     * The tray in the bottom right corner: a row for each strike with a cooldown the weapon in hand
     * makes, the ability on G at the bottom. The slot shows the weapon (greyed over by vanilla's
     * cooldown shade, the seconds left in its corner, as a stack's count), beside it the keys and
     * the bar, which fills as the strike comes back and is full and bright when it is there.
     */
    private static void tray(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = gui.getMinecraft();
        LocalPlayer p = mc.player;
        if (!HudConfig.tray() || p == null || mc.options.hideGui || p.isSpectator() || !MovesClient.serverHasMod()) return;
        java.util.List<Move> slots = traySlots(p);
        if (slots.isEmpty()) return;
        Stamina s = Stamina.of(p);
        long now = net.minecraft.Util.getMillis();
        int x = width - EDGE - TRAY_W, y = height - EDGE - SLOT;
        for (Move move : slots) {
            int left = StrikeInput.cooldown(s, move);
            Integer was = WAS.put(move, left);
            if (was != null && was > 0 && left == 0) READY_AT.put(move, now);
            long ready = READY_AT.getOrDefault(move, 0L);
            float glint = Mth.clamp(1f - (now - ready) / 400f, 0f, 1f);
            boolean flash = refused == move && now < refusedUntil && (now / 75) % 2 == 0;
            int total = trayTotal(move, s, left, was);
            trayRow(g, mc.font, x, y, p.getMainHandItem(), trayKeys(mc, move), left, total, glint, flash);
            y -= TRAY_ROW;
        }
    }

    /** The tray as it will look (the settings screen): the sword's turn waiting, the dash ready, side by side round {@code cx}. */
    static void trayPreview(GuiGraphics g, int cx, int top) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        g.fill(cx - 96, top - 4, cx + 96, top + SLOT + 4, 0xFF6F6F6F);
        g.fill(cx - 95, top - 3, cx + 95, top + SLOT + 3, 0xF0141414);
        int total = MovesConfig.swordSpinCooldownTicks();
        trayRow(g, mc.font, cx - TRAY_W - 6, top, sword, trayKeys(mc, Move.SWORD_SPIN), Math.round(total * 0.4f), total, 0f, false);
        trayRow(g, mc.font, cx + 6, top, sword, trayKeys(mc, Move.DASH), 0, MovesConfig.dashCooldownTicks(), 0f, false);
    }

    /** One row of the tray at (x, y); {@code left} of {@code total} ticks still to wait. */
    static void trayRow(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, ItemStack weapon, String keys,
                        int left, int total, float glint, boolean flash) {
        RenderSystem.enableBlend();
        g.blit(WIDGETS, x, y, 0, 0, SLOT - 1, SLOT);
        g.blit(WIDGETS, x + SLOT - 1, y, 181, 0, 1, SLOT);
        g.renderItem(weapon, x + 3, y + 3);
        float share = total > 0 ? Mth.clamp(left / (float) total, 0f, 1f) : 0f;
        boolean waiting = left > 0;
        if (waiting) {
            // vanilla's cooldown shade over the item (as on an ender pearl just thrown)
            int top = y + 3 + Mth.floor(16f * (1f - share));
            g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x + 3, top, x + 19, top + Mth.ceil(16f * share), Integer.MAX_VALUE);
            String sec = Integer.toString((left + 19) / 20);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(font, sec, x + 20 - font.width(sec), y + 12, 0xFFFFFF, true);
            g.pose().popPose();
        }
        if (glint > 0f) g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, (Math.round(0x90 * glint) << 24) | 0xFFFFFF);
        int tx = x + SLOT + 3;
        g.drawString(font, font.plainSubstrByWidth(keys, TRAY_BAR), tx, y + 3, waiting ? 0xA0A0A0 : 0xFFFFFF, true);
        int by = y + 14;
        if (flash) g.setColor(1f, 0.45f, 0.45f, 1f);
        shortBar(g, tex("energy_bar_bg"), tx, by, TRAY_BAR);
        int fill = Math.round(TRAY_BAR * (1f - share));
        if (waiting && !flash) g.setColor(0.6f, 0.6f, 0.6f, 1f);
        if (fill >= TRAY_BAR) shortBar(g, tex("energy_bar_fill"), tx, by, TRAY_BAR);
        else if (fill > 0) g.blit(tex("energy_bar_fill"), tx, by, 0, 0, fill, BAR_H, BAR_W, BAR_H);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /** A bar texture made {@code w} wide: its left part and its right edge. */
    private static void shortBar(GuiGraphics g, ResourceLocation tex, int x, int y, int w) {
        g.blit(tex, x, y, 0, 0, w - 1, BAR_H, BAR_W, BAR_H);
        g.blit(tex, x + w - 1, y, BAR_W - 1, 0, 1, BAR_H, BAR_W, BAR_H);
    }

    /**
     * The keys of a strike, short: "G(G+)" (author, 2026-10-31: on every weapon, so it is plain that G
     * may be tapped or held), "LMB+RMB", "C+LMB", "C+RMB", "↑RMB", "RMB+R" (as they are bound).
     */
    static String trayKeys(Minecraft mc, Move move) {
        return switch (move) {
            case DASH -> shortKey(mc.options.keyAttack) + "+" + shortKey(mc.options.keyUse);
            case SLIDE_ATTACK -> shortKey(MovesClient.slideKey(mc)) + "+" + shortKey(mc.options.keyAttack);
            case SLIDE_SHOT -> shortKey(MovesClient.slideKey(mc)) + "+" + shortKey(mc.options.keyUse);
            case MARKED_SPIN_LEFT -> "↑" + shortKey(mc.options.keyUse);
            case LEGOLAS_LEFT -> shortKey(mc.options.keyUse) + "+" + shortKey(MovesClient.DODGE);
            default -> {
                String g = shortKey(MovesClient.ABILITY);
                yield g + "(" + g + "+)";
            }
        };
    }

    /** A key's name, the mouse buttons as LMB, RMB, MMB. */
    static String shortKey(net.minecraft.client.KeyMapping key) {
        com.mojang.blaze3d.platform.InputConstants.Key k = key.getKey();
        if (k.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE && k.getValue() >= 0 && k.getValue() <= 2) {
            return net.minecraft.network.chat.Component.translatable("kinetrix_moves.key.mouse" + k.getValue()).getString();
        }
        return key.getTranslatedKeyMessage().getString();
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
