package dev.mcreload.moves.client;

import dev.mcreload.moves.HudConfig;
import dev.mcreload.moves.Kick;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.Stamina;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * The kick's cutscene on the player's own screen (author, 2026-10-29: "a cutscene with time and
 * perception slowed"): the camera goes behind the player for the move if it was in first person
 * (and back after), and the view narrows from the vault over the mob to the kick, as if time
 * slowed, opening again as the player lands. The world is not slowed for anyone else. Switched off
 * in the settings (the moves' page).
 */
public final class Cutscene {
    /** The camera before the cutscene took it, or null. */
    private static CameraType before;

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(Cutscene::fov);
    }

    /** Every client tick: the camera taken for the kick and given back when it ends. */
    static void tick(Minecraft mc, LocalPlayer p, Stamina s) {
        boolean kicking = p != null && s.move == Move.KICK && s.moveTick < Kick.LAND;
        if (kicking && before == null && HudConfig.kickCutscene() && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            before = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!kicking && before != null) {
            if (mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(before);
            before = null;
        }
    }

    /** How narrow the view is this far into the kick: 0 none, 1 the narrowest. */
    private static float slow(Stamina s, float partial) {
        if (s.move != Move.KICK) return 0f;
        float t = s.moveTick + partial;
        float in = Mth.clamp((t - Kick.TAKEOFF) / 10f, 0f, 1f), out = Mth.clamp((Kick.LAND + 2 - t) / 5f, 0f, 1f);
        float f = Math.min(in, out);
        return f * f * (3 - 2 * f);
    }

    private static void fov(ComputeFovModifierEvent event) {
        if (!HudConfig.kickCutscene()) return;
        float f = slow(Stamina.of(event.getPlayer()), Minecraft.getInstance().getFrameTime());
        if (f > 0f) event.setNewFovModifier(event.getNewFovModifier() * (1f - 0.18f * f));
    }

    private Cutscene() {}
}
