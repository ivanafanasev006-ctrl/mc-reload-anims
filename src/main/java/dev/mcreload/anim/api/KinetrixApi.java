package dev.mcreload.anim.api;

/**
 * What other mods may call on Kinetrix. Kept to plain static methods so a mod that only
 * optionally depends on Kinetrix can reach them by reflection (Kinetrix Moves does).
 */
public final class KinetrixApi {
    private static volatile long crawlBlockedUntil;
    private static final java.util.Map<java.util.UUID, Long> WHOLE_BODY = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Client: do not start crawling from the crawl key for the next {@code millis}, even if the key
     * is held (a move that uses the same key, like a sliding tackle on sprint + crawl, runs first;
     * once the time is up a still-held key crawls as usual). 0 lifts the block.
     */
    public static void blockCrawl(long millis) {
        crawlBlockedUntil = millis <= 0 ? 0 : System.currentTimeMillis() + millis;
    }

    public static boolean crawlBlocked() {
        return System.currentTimeMillis() < crawlBlockedUntil;
    }

    /**
     * Client: another mod plays a move on the whole of this player for the next {@code millis} (a
     * dodge, a roll, a slide). Kinetrix then keeps none of its own stride on the legs or its head
     * turn under that move, even while the player is carried along. 0 ends it early.
     */
    public static void wholeBody(java.util.UUID player, long millis) {
        if (millis <= 0) WHOLE_BODY.remove(player);
        else WHOLE_BODY.put(player, System.currentTimeMillis() + millis);
    }

    public static boolean wholeBody(java.util.UUID player) {
        Long until = WHOLE_BODY.get(player);
        if (until == null) return false;
        if (System.currentTimeMillis() < until) return true;
        WHOLE_BODY.remove(player, until);
        return false;
    }

    private KinetrixApi() {}
}
