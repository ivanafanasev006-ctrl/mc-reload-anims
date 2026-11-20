package dev.mcreload.moves.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * The wheel of moves drawn as a real wheel in Minecraft's own look (author, 2026-11-20: "a wheel, but pixelly, as
 * Minecraft's things"): a ring of {@code n} parts, one GUI pixel a texel (so it is as blocky as the vanilla screens at any
 * scale), lit from the upper left as vanilla's frames are: the outer rim light above and on the left, dark below and on the
 * right, the inner rim the other way round, dark seams between the parts; the part under the mouse in gold. Each look is
 * drawn once into a little texture and kept.
 */
final class PixelWheel {
    private static final Map<String, ResourceLocation> MADE = new HashMap<>();
    private static int made;

    /** The ring round (cx, cy) from {@code rIn} to {@code rOut}, part 0 at the top, going clockwise; {@code lit}: gold, -1 none. */
    static void draw(GuiGraphics g, int cx, int cy, int rIn, int rOut, int n, int lit) {
        String key = rIn + ":" + rOut + ":" + n + ":" + lit;
        ResourceLocation tex = MADE.computeIfAbsent(key, k -> make(rIn, rOut, n, lit));
        int size = 2 * rOut;
        RenderSystem.enableBlend();
        g.blit(tex, cx - rOut, cy - rOut, 0, 0, size, size, size, size);
        RenderSystem.disableBlend();
    }

    /** Which part of the ring a point is in (its angle from the top, clockwise), with the parts centred on 0, 360/n... */
    static int part(double dx, double dy, int n) {
        double a = Math.toDegrees(Math.atan2(dy, dx)) + 90 + 180.0 / n;
        return Math.floorMod((int) Math.floor(a / (360.0 / n)), n);
    }

    private static ResourceLocation make(int rIn, int rOut, int n, int lit) {
        int size = 2 * rOut;
        NativeImage img = new NativeImage(size, size, true);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = x + 0.5 - rOut, dy = y + 0.5 - rOut, d = Math.sqrt(dx * dx + dy * dy);
                int argb = 0;
                if (d <= rOut && d >= rIn) {
                    int part = part(dx, dy, n);
                    boolean gold = part == lit;
                    // how far round from the nearest seam, in pixels along the ring
                    double a = Math.toDegrees(Math.atan2(dy, dx)) + 90 + 180.0 / n;
                    double into = Math.floorMod((long) Math.floor(a * 1000), (long) (360000.0 / n)) / 1000.0;
                    double seam = Math.min(into, 360.0 / n - into) * Math.PI / 180 * d;
                    // lit from the upper left: the light side of a rim faces up and left
                    boolean upLeft = dx + dy < 0;
                    if (d > rOut - 1) argb = gold ? 0xFFFFE070 : upLeft ? 0xFFB8B8B8 : 0xFF141414;
                    else if (d > rOut - 2) argb = gold ? 0xFFC8A030 : upLeft ? 0xFF6E6E6E : 0xFF2A2A2A;
                    else if (d < rIn + 1) argb = gold ? 0xFFFFE070 : upLeft ? 0xFF141414 : 0xFF8E8E8E;
                    else if (seam < 1.0) argb = 0xFF0C0C0C;
                    else if (seam < 2.0) argb = gold ? 0xE0806010 : 0xE0383838;
                    else argb = gold ? 0xD8604A12 : 0xC8202020;
                }
                img.setPixelRGBA(x, y, abgr(argb));
            }
        }
        DynamicTexture tex = new DynamicTexture(img);
        return Minecraft.getInstance().getTextureManager().register("kinetrix_combat_wheel_" + made++, tex);
    }

    /** NativeImage keeps its pixels as ABGR. */
    private static int abgr(int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private PixelWheel() {}
}
