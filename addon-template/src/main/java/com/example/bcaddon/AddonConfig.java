package com.example.bcaddon;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/bcaddon-common.toml */
public final class AddonConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue LOG_HITS;
    public static final ForgeConfigSpec.IntValue GUARD_TICKS;
    public static final ForgeConfigSpec.IntValue GUARD_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue HEAVY_SPEED_PENALTY;
    public static final ForgeConfigSpec.DoubleValue HEAVY_RANGE_BONUS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        LOG_HITS = b.comment("Log every Better Combat hit the server sees: attack of the combo, animation, hand, damage.")
                .define("logHits", false);
        b.push("guard");
        GUARD_TICKS = b.comment("Parry window in ticks, for weapons without weapon_extras data.")
                .defineInRange("windowTicks", 5, 0, 40);
        GUARD_COOLDOWN = b.comment("Ticks before the guard can be raised again.")
                .defineInRange("cooldownTicks", 24, 0, 200);
        b.pop();
        b.push("heavy");
        HEAVY_SPEED_PENALTY = b.comment("Attack speed lost while the heavy attack key is held (0.35 = swings 35% slower).")
                .defineInRange("attackSpeedPenalty", 0.35, 0.0, 0.9);
        HEAVY_RANGE_BONUS = b.comment("Extra reach of heavy attacks in blocks (applied on the client, where Better Combat looks for targets).")
                .defineInRange("rangeBonus", 0.5, 0.0, 2.0);
        b.pop();
        SPEC = b.build();
    }

    private AddonConfig() {}
}
