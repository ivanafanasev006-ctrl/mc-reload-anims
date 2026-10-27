package dev.mcreload.moves.compat;

import dev.mcreload.moves.KinetrixMoves;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * What this mod asks of Kinetrix, through its small API (dev.mcreload.anim.api.KinetrixApi).
 * Reached by name so the mod runs without Kinetrix too, or with a Kinetrix too old to have it.
 */
public final class KinetrixBridge {
    private static final MethodHandle BLOCK_CRAWL = find("blockCrawl", MethodType.methodType(void.class, long.class));
    private static final MethodHandle WHOLE_BODY = find("wholeBody", MethodType.methodType(void.class, java.util.UUID.class, long.class));

    /**
     * Kinetrix does not start crawling on its key for this many milliseconds (0: from now on it may):
     * sprint + C is our slide, and a held C hands over to Kinetrix's crawl when the slide ends.
     */
    public static void blockCrawl(long millis) {
        if (BLOCK_CRAWL == null) return;
        try {
            BLOCK_CRAWL.invokeExact(millis);
        } catch (Throwable t) {
            KinetrixMoves.LOG.debug("[kinetrix_moves] KinetrixApi.blockCrawl failed", t);
        }
    }

    /**
     * Our move owns the whole of this player for {@code millis} (0: no longer): Kinetrix keeps
     * none of its running legs or head turn under it while the move carries the player along.
     */
    public static void wholeBody(java.util.UUID player, long millis) {
        if (WHOLE_BODY == null) return;
        try {
            WHOLE_BODY.invokeExact(player, millis);
        } catch (Throwable t) {
            KinetrixMoves.LOG.debug("[kinetrix_moves] KinetrixApi.wholeBody failed", t);
        }
    }

    private static MethodHandle find(String name, MethodType type) {
        try {
            Class<?> api = Class.forName("dev.mcreload.anim.api.KinetrixApi");
            return MethodHandles.publicLookup().findStatic(api, name, type);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private KinetrixBridge() {}
}
