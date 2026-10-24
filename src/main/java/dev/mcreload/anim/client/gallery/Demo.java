package dev.mcreload.anim.client.gallery;

import dev.mcreload.anim.client.rig.MotionState;

import java.util.function.Consumer;

/**
 * One entry of the animation gallery: its name and "when it plays" text come from the language
 * file ({@code kinetrix.demo.<id>} and {@code kinetrix.demo.<id>.desc}).
 *
 * @param script    what the dummy does, tick by tick (null: only seen in the world, not in the preview)
 * @param loopTicks the script starts over after this many ticks (0 = runs on)
 * @param onSelf    plays it on your own player ("show on me"), or null
 */
public record Demo(String id, String category, int loopTicks, Script script, Consumer<MotionState> onSelf) {
    @FunctionalInterface
    public interface Script {
        void tick(DemoStage s, int t);
    }

    public boolean previewable() {
        return script != null;
    }

    /** Emotes keep the names they have in the emote menu and share one description. */
    public String nameKey() {
        return id.startsWith("emote_") ? "emote.kinetrix." + id.substring(6) : "kinetrix.demo." + id;
    }

    public String descKey() {
        return id.startsWith("emote_") ? "kinetrix.demo.emote.desc" : "kinetrix.demo." + id + ".desc";
    }
}
