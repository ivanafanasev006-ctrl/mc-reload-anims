package dev.mcreload.moves;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * serverconfig/kinetrix_moves-server.toml (in the world folder): the numbers of the combat rules.
 * Forge sends them to every client that joins, so the client's guesses (the bars, whether a move
 * is affordable) use the same numbers as the server.
 *
 * <p>The rules are the author's (docs/combat-spec.md). Where the author gave no number the value
 * is a default, marked "[default]" in its comment.</p>
 */
public final class MovesConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.DoubleValue MAX_ENERGY, REGEN, REGEN_BUSY, REGEN_STILL, REGEN_FED, EXHAUSTION,
            APPLE_BOOST, MAX_SHIELDS, SHIELD_REGEN, SHIELD_REGEN_COST, SLIDE_TAP, SLIDE_HELD, COOKED_FOOD;
    private static final ForgeConfigSpec.IntValue COMBAT_SECONDS, APPLE_INFINITE, APPLE_BOOST_SECONDS, ENCHANTED_INFINITE,
            SHIELD_REGEN_SECONDS, COST_DODGE, COST_ROLL, COST_SLIDE, COST_HOP, COST_BARRAGE, COST_BOW_DODGE, HOP_SPEED_TICKS;
    private static final ForgeConfigSpec.BooleanValue DODGE_ON_GROUND;
    private static final ForgeConfigSpec.IntValue PARRY_REARM;
    private static final ForgeConfigSpec.DoubleValue GUARD_MOVE, DEFLECT_SHIELDS, HOP_RANGE, KNEEL_MOVE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Energy: spent by every combat move, refilled from food (hunger and saturation).").push("energy");
        MAX_ENERGY = b.comment("Size of the energy bar. [default]").defineInRange("max", 100.0, 1.0, 10000.0);
        REGEN = b.comment("Energy refilled per second at rest. [default]").defineInRange("regenPerSecond", 4.0, 0.0, 1000.0);
        REGEN_BUSY = b.comment("Refill multiplier in combat, while sprinting or during a move (slower). [default]")
                .defineInRange("busyFactor", 0.5, 0.0, 10.0);
        REGEN_STILL = b.comment("Refill multiplier standing still or sneaking (faster). [default]")
                .defineInRange("stillFactor", 2.0, 0.0, 10.0);
        REGEN_FED = b.comment("Refill multiplier with a full hunger bar and some saturation left (faster). [default]")
                .defineInRange("wellFedFactor", 1.5, 0.0, 10.0);
        EXHAUSTION = b.comment("Food exhaustion per point of energy refilled (vanilla: 4 exhaustion = 1 saturation or",
                        "hunger point, so 0.05 makes a full bar cost about 1.25 hunger points). With an empty",
                        "hunger bar energy does not refill. [default]")
                .defineInRange("exhaustionPerEnergy", 0.05, 0.0, 4.0);
        COMBAT_SECONDS = b.comment("Seconds a player counts as in combat after hitting or being hit. [default]")
                .defineInRange("combatSeconds", 5, 0, 600);
        APPLE_INFINITE = b.comment("Golden apple: seconds during which moves cost no energy (author: 5, then 10 more: 15).")
                .defineInRange("goldenAppleInfiniteSeconds", 15, 0, 3600);
        APPLE_BOOST_SECONDS = b.comment("Golden apple: seconds of faster refill. [default]")
                .defineInRange("goldenAppleBoostSeconds", 30, 0, 3600);
        APPLE_BOOST = b.comment("Golden apple: refill multiplier during the boost. [default]")
                .defineInRange("goldenAppleBoostFactor", 2.0, 1.0, 100.0);
        COOKED_FOOD = b.comment("Energy a cooked or prepared food gives back when eaten (roasted meat and fish, a baked potato,",
                        "bread, stews and soups, pie; the item tag kinetrix_moves:cooked_food, and other mods' foods named",
                        "cooked, baked, roasted, fried, grilled, smoked, stew, soup, pie or bread). A little energy in a",
                        "fight without potions or golden apples (author: 25 of 100).")
                .defineInRange("cookedFoodEnergy", 25.0, 0.0, 10000.0);
        ENCHANTED_INFINITE = b.comment("Enchanted golden apple: fills the bar, then moves cost nothing for this many seconds (author: 30).")
                .defineInRange("enchantedAppleFreeSeconds", 30, 0, 3600);
        b.pop();

        b.comment("Shields: spent by blocks and deflected arrows, refilled from energy.").push("shields");
        MAX_SHIELDS = b.comment("Number of shields (one row of icons holds 10; halves count). [default]")
                .defineInRange("max", 10.0, 0.5, 100.0);
        SHIELD_REGEN = b.comment("Shields refilled at a time (author: 0.5).").defineInRange("regenAmount", 0.5, 0.0, 100.0);
        SHIELD_REGEN_SECONDS = b.comment("Seconds between refills (author: 3-5). [default]")
                .defineInRange("regenSeconds", 4, 1, 600);
        SHIELD_REGEN_COST = b.comment("Energy each refill costs; without it shields do not refill. [default]")
                .defineInRange("regenEnergyCost", 5.0, 0.0, 10000.0);
        b.pop();

        b.comment("Energy cost of each move.").push("costs");
        COST_DODGE = b.comment("Side dodge, R + A / R + D. [default]").defineInRange("dodge", 15, 0, 10000);
        COST_ROLL = b.comment("Roll back or forward, R + S / R + W. [default]").defineInRange("roll", 20, 0, 10000);
        COST_SLIDE = b.comment("Slide, sprint + C. [default]").defineInRange("slide", 20, 0, 10000);
        COST_HOP = b.comment("Hop over an enemy winding up, R + W at point blank. [default]").defineInRange("hop", 25, 0, 10000);
        COST_BARRAGE = b.comment("Bow volley: full draw, then the left button. [default]").defineInRange("volley", 20, 0, 10000);
        COST_BOW_DODGE = b.comment("A dodge with a shot: in the Legolas stance and the bow's combat flow (a roll, a step back). [default]")
                .defineInRange("bowDodge", 10, 0, 10000);
        b.pop();

        b.comment("How the moves behave.").push("moves");
        DODGE_ON_GROUND = b.comment("Dodges and rolls only from the ground, not in mid-air. [default]")
                .define("dodgeOnlyOnGround", true);
        SLIDE_TAP = b.comment("Slide length in blocks when C is tapped (author: 4).").defineInRange("slideBlocks", 4.0, 1.0, 16.0);
        SLIDE_HELD = b.comment("Slide length in blocks when C is held; the player then goes on crawling (author: 5).")
                .defineInRange("slideHeldBlocks", 5.0, 1.0, 16.0);
        b.pop();

        b.comment("The hop over an enemy winding up (R + W at point blank).").push("hop");
        HOP_RANGE = b.comment("Blocks between the player and the enemy for R + W to hop over them instead of rolling. [default]")
                .defineInRange("range", 2.5, 1.0, 6.0);
        HOP_SPEED_TICKS = b.comment("Ticks of Speed I after the hop, to strike back (author: 1 s = 20).")
                .defineInRange("speedTicks", 20, 0, 600);
        b.pop();

        b.comment("Knocking an arrow aside: R (a side dodge) with a sword or a bow in hand as it comes.").push("deflect");
        DEFLECT_SHIELDS = b.comment("Shields it costs (author: 1.5). The dodge pays the energy.")
                .defineInRange("shields", 1.5, 0.0, 100.0);
        b.pop();

        b.comment("The sword block (right mouse button with a sword).").push("guard");
        PARRY_REARM = b.comment("Ticks the guard must stay down before raising it again gives a new perfect parry",
                        "window, so tapping the button does not parry everything (20 ticks = 1 s). [default]")
                .defineInRange("parryRearmTicks", 10, 0, 200);
        GUARD_MOVE = b.comment("Walking speed while guarding, as a share of normal (like using an item in vanilla",
                        "and blocking with a sword in 1.7.10). [default]")
                .defineInRange("moveFactor", 0.2, 0.0, 1.0);
        b.pop();

        b.comment("The bow.").push("bow");
        KNEEL_MOVE = b.comment("After the volley, with the right button still held, the player stays on one knee and aims:",
                        "walking speed as a share of the drawn bow's, as when sneaking (author: \"at a snail's pace\"). [default]")
                .defineInRange("kneelMoveFactor", 0.3, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }

    public static float maxEnergy() { return f(MAX_ENERGY, 100.0); }
    public static float regenPerSecond() { return f(REGEN, 4.0); }
    public static float busyFactor() { return f(REGEN_BUSY, 0.5); }
    public static float stillFactor() { return f(REGEN_STILL, 2.0); }
    public static float wellFedFactor() { return f(REGEN_FED, 1.5); }
    public static float exhaustionPerEnergy() { return f(EXHAUSTION, 0.05); }
    public static float cookedFoodEnergy() { return f(COOKED_FOOD, 25.0); }
    public static int combatTicks() { return get(COMBAT_SECONDS, 5) * 20; }
    public static int appleFreeTicks() { return get(APPLE_INFINITE, 15) * 20; }
    public static int appleBoostTicks() { return get(APPLE_BOOST_SECONDS, 30) * 20; }
    public static float appleBoostFactor() { return f(APPLE_BOOST, 2.0); }
    public static int enchantedFreeTicks() { return get(ENCHANTED_INFINITE, 30) * 20; }
    public static float maxShields() { return f(MAX_SHIELDS, 10.0); }
    public static float shieldRegen() { return f(SHIELD_REGEN, 0.5); }
    public static int shieldRegenTicks() { return get(SHIELD_REGEN_SECONDS, 4) * 20; }
    public static float shieldRegenCost() { return f(SHIELD_REGEN_COST, 5.0); }
    public static int costDodge() { return get(COST_DODGE, 15); }
    public static int costRoll() { return get(COST_ROLL, 20); }
    public static int costSlide() { return get(COST_SLIDE, 20); }
    public static boolean dodgeOnlyOnGround() { return get(DODGE_ON_GROUND, true); }
    public static float slideBlocks() { return f(SLIDE_TAP, 4.0); }
    public static float slideHeldBlocks() { return f(SLIDE_HELD, 5.0); }
    public static int parryRearmTicks() { return get(PARRY_REARM, 10); }
    public static int costHop() { return get(COST_HOP, 25); }
    public static int costBarrage() { return get(COST_BARRAGE, 20); }
    public static int costBowDodge() { return get(COST_BOW_DODGE, 10); }
    public static float hopRange() { return f(HOP_RANGE, 2.5); }
    public static int hopSpeedTicks() { return get(HOP_SPEED_TICKS, 20); }
    public static float deflectShields() { return f(DEFLECT_SHIELDS, 1.5); }
    public static float guardMoveFactor() { return f(GUARD_MOVE, 0.2); }
    public static float kneelMoveFactor() { return f(KNEEL_MOVE, 0.3); }

    private static float f(ForgeConfigSpec.DoubleValue value, double fallback) {
        return get(value, fallback).floatValue();
    }

    /** The value, or the default while no world is loaded (server configs load with the world). */
    private static <T> T get(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    private MovesConfig() {}
}
