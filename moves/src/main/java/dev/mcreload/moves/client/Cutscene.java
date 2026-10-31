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
 *
 * <p>The volley at the marked too (Move.cameraBehind): it aims by itself and turns the player to each
 * mob, so the camera goes behind the player and comes back a few ticks after it, unless switched off
 * (HudConfig.attackCamera). The other attacks stay in first person with both arms seen (author,
 * 2026-10-31: "the only place where third person may be is the auto-aimed shooting at the marked";
 * FirstPersonArms); from 2026-10-30, 23:01 to then they all took the camera behind.</p>
 */
public final class Cutscene {
    /** The camera before the cutscene took it, or null. */
    private static CameraType before;
    /** Ticks the camera stays behind after the move (an attack straight after another keeps it there). */
    private static final int LINGER = 4;
    private static int linger;
    /** The bow's G going on shoots at marked mobs in sight (decided as it starts). */
    private static boolean volleyAtMarks;

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(Cutscene::fov);
    }

    /** Every client tick: the camera taken for the kick or an attack and given back when it ends. */
    static void tick(Minecraft mc, LocalPlayer p, Stamina s) {
        boolean kicking = p != null && (s.move == Move.KICK || s.move == Move.ROLL_KICK) && s.moveTick < Kick.land(s.move);
        // G and G held with a bow: from behind when marked mobs are in sight as it starts (it aims at them by
        // itself), else in first person, aimed by the player (author, 2026-10-31)
        if (p != null && s.move != null && s.move.volley() && s.moveTick <= 1) volleyAtMarks = !dev.mcreload.moves.Marks.inSight(p, s).isEmpty();
        boolean aimed = p != null && s.move != null && (s.move.cameraBehind() || (s.move.volley() && volleyAtMarks));
        boolean behind = kicking ? HudConfig.kickCutscene() : aimed && HudConfig.attackCamera();
        if (behind) linger = LINGER;
        else if (linger > 0) linger--;
        if (behind && before == null && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            before = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!behind && linger == 0 && before != null) {
            if (mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(before);
            before = null;
        }
    }

    /** The camera is behind the player for a move now, taken from first person. */
    public static boolean taken() {
        return before != null;
    }

    /** How narrow the view is this far into the kick: 0 none, 1 the narrowest. */
    private static float slow(Stamina s, float partial) {
        if (s.move != Move.KICK && s.move != Move.ROLL_KICK) return 0f;
        float t = s.moveTick + partial;
        boolean roll = s.move == Move.ROLL_KICK;
        float in = Mth.clamp((t - (roll ? Kick.RK_TAKEOFF : Kick.TAKEOFF)) / (roll ? 6f : 10f), 0f, 1f),
                out = Mth.clamp((Kick.land(s.move) + 2 - t) / 5f, 0f, 1f);
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
