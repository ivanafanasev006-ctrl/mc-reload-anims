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

    public static volatile boolean locomotion = true, idle = true, airborne = true, landing = true,
            water = true, climbing = true, combat = true, reactions = true;

    public static volatile boolean harmonizer = true;
    public static volatile float fadeIn = 0.10f, fadeOut = 0.22f, yield = 0.85f, keepLegs = 0.85f, keepHead = 0f;
    public static volatile boolean ironsSpells = true;

    public static volatile Signs signs = Signs.DEFAULT;

    public static void refresh() {
        if (!ClientConfig.SPEC.isLoaded()) return;
        enabled = ClientConfig.ENABLED.get();
        otherPlayers = ClientConfig.OTHER_PLAYERS.get();
        intensity = ClientConfig.INTENSITY.get().floatValue();

        locomotion = ClientConfig.LOCOMOTION.get();
        idle = ClientConfig.IDLE.get();
        airborne = ClientConfig.AIRBORNE.get();
        landing = ClientConfig.LANDING.get();
        water = ClientConfig.WATER.get();
        climbing = ClientConfig.CLIMBING.get();
        combat = ClientConfig.COMBAT.get();
        reactions = ClientConfig.REACTIONS.get();

        harmonizer = ClientConfig.HARMONIZER.get();
        fadeIn = ClientConfig.FADE_IN.get().floatValue();
        fadeOut = ClientConfig.FADE_OUT.get().floatValue();
        yield = ClientConfig.YIELD.get().floatValue();
        keepLegs = ClientConfig.KEEP_LEGS.get().floatValue();
        keepHead = ClientConfig.KEEP_HEAD.get().floatValue();
        ironsSpells = ClientConfig.IRONS_SPELLS.get();

        signs = Signs.of(ClientConfig.INVERT_KNEE.get(), ClientConfig.INVERT_ELBOW.get(), ClientConfig.INVERT_TORSO.get());
    }

    private Cfg() {}
}
