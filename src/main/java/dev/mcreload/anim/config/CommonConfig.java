package dev.mcreload.anim.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * config/kinetrix-common.toml: the few things that are not drawing (they need Kinetrix on the
 * server too, or on the host of a single-player / LAN world).
 */
public final class CommonConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue TWO_RIDERS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("riding");
        TWO_RIDERS = b.comment("Two on one horse, like in a boat: right-click a tamed, saddled horse (donkey, mule,",
                        "or a horse from another mod, e.g. DragN's Livestock Overhaul) that someone is already riding",
                        "to climb on behind them. The one in front steers; sneak to get off.",
                        "Needs Kinetrix on the server (or the host of the world).")
                .define("twoRiders", true);
        b.pop();
        SPEC = b.build();
    }

    /** The value, or the default before the config has loaded. */
    public static boolean twoRiders() {
        try {
            return TWO_RIDERS.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private CommonConfig() {}
}
