package dev.mcreload.anim.config;

import dev.mcreload.anim.client.pose.Signs;

/**
 * Plain-field snapshot of the config, refreshed once per client tick, so the render path
 * never touches ForgeConfigSpec directly.
 */
public final class Cfg {
    public static volatile boolean enabled = true;
    public static volatile boolean otherPlayers = true;
    public static volatile float intensity = 1f;
    public static volatile float liveliness = 0.6f;

    public static volatile boolean locomotion = true, idle = true, airborne = true, landing = true,
            water = true, climbing = true, combat = true, reactions = true, idleActions = true, twoHanded = false,
            postures = true, toggleCrawl = true, toggleSit = true;

    public static volatile boolean trails = true;
    public static volatile float trailSeconds = 0.22f, trailOpacity = 0.75f;
    public static volatile int trailColor = 0xFFFFFF;

    public static volatile boolean harmonizer = true;
    public static volatile float fadeIn = 0.10f, fadeOut = 0.22f, yield = 0.85f, keepLegs = 0.85f, keepHead = 0f;
    public static volatile boolean ironsSpells = true;
    public static volatile boolean playerOverEmf = true, ownMovesFirst = true, bendSkinLayers = true, sync = true;
    public static volatile java.util.Set<String> priorityOver = java.util.Set.of("vintage_animations", "vintageanimations");
    private static java.util.List<? extends String> priorityList;

    public static volatile Signs signs = Signs.DEFAULT;

    public static void refresh() {
        if (!ClientConfig.SPEC.isLoaded()) return;
        enabled = ClientConfig.ENABLED.get();
        otherPlayers = ClientConfig.OTHER_PLAYERS.get();
        intensity = ClientConfig.INTENSITY.get().floatValue();
        liveliness = ClientConfig.LIVELINESS.get().floatValue();

        locomotion = ClientConfig.LOCOMOTION.get();
        idle = ClientConfig.IDLE.get();
        idleActions = ClientConfig.IDLE_ACTIONS.get();
        twoHanded = ClientConfig.TWO_HANDED.get();
        airborne = ClientConfig.AIRBORNE.get();
        landing = ClientConfig.LANDING.get();
        water = ClientConfig.WATER.get();
        climbing = ClientConfig.CLIMBING.get();
        combat = ClientConfig.COMBAT.get();
        reactions = ClientConfig.REACTIONS.get();
        postures = ClientConfig.POSTURES.get();
        toggleCrawl = ClientConfig.TOGGLE_CRAWL.get();
        toggleSit = ClientConfig.TOGGLE_SIT.get();

        trails = ClientConfig.TRAILS.get();
        trailSeconds = ClientConfig.TRAIL_SECONDS.get().floatValue();
        trailOpacity = ClientConfig.TRAIL_OPACITY.get().floatValue();
        trailColor = parseColor(ClientConfig.TRAIL_COLOR.get());

        harmonizer = ClientConfig.HARMONIZER.get();
        fadeIn = ClientConfig.FADE_IN.get().floatValue();
        fadeOut = ClientConfig.FADE_OUT.get().floatValue();
        yield = ClientConfig.YIELD.get().floatValue();
        keepLegs = ClientConfig.KEEP_LEGS.get().floatValue();
        keepHead = ClientConfig.KEEP_HEAD.get().floatValue();
        ironsSpells = ClientConfig.IRONS_SPELLS.get();
        playerOverEmf = ClientConfig.PLAYER_OVER_EMF.get();
        ownMovesFirst = ClientConfig.OWN_MOVES_FIRST.get();
        bendSkinLayers = ClientConfig.BEND_SKIN_LAYERS.get();
        sync = ClientConfig.SYNC.get();
        java.util.List<? extends String> pl = ClientConfig.PRIORITY_OVER.get();
        if (pl != priorityList) {
            priorityList = pl;
            java.util.Set<String> set = new java.util.HashSet<>();
            for (String id : pl) if (id != null && !id.isBlank()) set.add(id.trim().toLowerCase(java.util.Locale.ROOT));
            priorityOver = java.util.Set.copyOf(set);
        }

        signs = Signs.of(ClientConfig.INVERT_KNEE.get(), ClientConfig.INVERT_ELBOW.get(), ClientConfig.INVERT_TORSO.get());
    }

    private static int parseColor(String hex) {
        try {
            return Integer.parseInt(hex.trim().replace("#", ""), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }

    private Cfg() {}
}
