package dev.mcreload.moves;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The paths the mechanic carries the player along in the moves whose animations hold on to
 * something (the ledge, the kick, the fall roll): a value a game tick from the move's start, in
 * blocks, worked out from the animations themselves (tools/blockbench/refine_author_2026_10_29,
 * moves.paths) so the hands stay on the edge while the player moves. Read from
 * kinetrix_moves_paths.json; a path is [up, forward] pairs or single values.
 */
public final class Paths {
    private static Map<String, float[][]> paths;

    /** The path's value at {@code tick} (held at its end), component {@code i}; 0 if there is none. */
    public static float at(String name, int tick, int i) {
        float[][] p = all().get(name);
        if (p == null || p.length == 0) return 0f;
        float[] v = p[Math.max(0, Math.min(tick, p.length - 1))];
        return i < v.length ? v[i] : 0f;
    }

    /** The same between ticks: {@code t} in ticks, fractions too. */
    public static float lerp(String name, float t, int i) {
        int a = (int) Math.floor(t);
        float f = t - a;
        return at(name, a, i) * (1 - f) + at(name, a + 1, i) * f;
    }

    /** Ticks the path has (its last value is held after). */
    public static int length(String name) {
        float[][] p = all().get(name);
        return p == null ? 0 : p.length;
    }

    private static synchronized Map<String, float[][]> all() {
        if (paths != null) return paths;
        Map<String, float[][]> out = new HashMap<>();
        try (InputStream in = Paths.class.getResourceAsStream("/kinetrix_moves_paths.json")) {
            if (in == null) throw new IllegalStateException("kinetrix_moves_paths.json is missing");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonArray ticks = e.getValue().getAsJsonArray();
                float[][] p = new float[ticks.size()][];
                for (int t = 0; t < p.length; t++) {
                    JsonElement v = ticks.get(t);
                    if (v.isJsonArray()) {
                        JsonArray a = v.getAsJsonArray();
                        p[t] = new float[a.size()];
                        for (int i = 0; i < a.size(); i++) p[t][i] = a.get(i).getAsFloat();
                    } else {
                        p[t] = new float[]{v.getAsFloat()};
                    }
                }
                out.put(e.getKey(), p);
            }
        } catch (Exception ex) {
            KinetrixMoves.LOG.error("[kinetrix_moves] could not read the move paths", ex);
        }
        paths = out;
        return paths;
    }

    private Paths() {}
}
