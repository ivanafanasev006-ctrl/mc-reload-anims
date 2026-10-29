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
            APPLE_BOOST, MAX_SHIELDS, SHIELD_REGEN, SHIELD_REGEN_COST, SLIDE_TAP, SLIDE_HELD, COOKED_FOOD,
            COST_DODGE, COST_ROLL, COST_SLIDE, COST_HOP, COST_BARRAGE, COST_BOW_DODGE, COST_DASH, COST_FINISHER,
            COST_KICK, COST_SLIDE_ATTACK, COST_LEDGE_HOP;
    private static final ForgeConfigSpec.IntValue COMBAT_SECONDS, APPLE_INFINITE, APPLE_BOOST_SECONDS, ENCHANTED_INFINITE,
            SHIELD_REGEN_SECONDS, HOP_SPEED_TICKS;
    private static final ForgeConfigSpec.BooleanValue DODGE_ON_GROUND;
    private static final ForgeConfigSpec.IntValue PARRY_REARM;
    private static final ForgeConfigSpec.DoubleValue GUARD_MOVE, DEFLECT_SHIELDS, HOP_RANGE, KNEEL_MOVE,
            VAULT_MIN_WIDTH, VAULT_MIN_HEIGHT, VAULT_MAX_HEIGHT, VAULT_SHOVE, DASH_CRIT, FINISH_HEALTH, FINISH_RANGE, FINISH_MAX_HEALTH;
    private static final ForgeConfigSpec.IntValue DASH_COOLDOWN, SLIDE_ATTACK_COOLDOWN;
    private static final ForgeConfigSpec.DoubleValue DASH_WIDTH, SLIDE_ATTACK_CRIT, SLIDE_ATTACK_REACH, KICK_DAMAGE, KICK_STRENGTH,
            FALL_CUT;
    private static final ForgeConfigSpec.BooleanValue LEDGE_GRAB;

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
        // Renamed from goldenAppleInfiniteSeconds (15) so worlds made before get the new number.
        APPLE_INFINITE = b.comment("Golden apple: seconds during which moves cost no energy (author: 5, then 15, then 10).")
                .defineInRange("goldenAppleFreeSeconds", 10, 0, 3600);
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

        // Renamed from "costs" (whole numbers, twice as much) so worlds made before get the new numbers.
        b.comment("Energy cost of each move (author, 2026-10-28: half of what they were).").push("energyCost");
        COST_DODGE = b.comment("Side dodge, R + A / R + D (was 15). [default]").defineInRange("dodge", 7.5, 0.0, 10000.0);
        COST_ROLL = b.comment("Roll back or forward, R + S / R + W (was 20). [default]").defineInRange("roll", 10.0, 0.0, 10000.0);
        COST_SLIDE = b.comment("Slide, sprint + C (was 20). [default]").defineInRange("slide", 10.0, 0.0, 10000.0);
        COST_HOP = b.comment("Vault over a mob at point blank, R + W (was 25). [default]").defineInRange("hop", 12.5, 0.0, 10000.0);
        COST_BARRAGE = b.comment("Bow volley: full draw, then the left button (was 20). [default]").defineInRange("volley", 10.0, 0.0, 10000.0);
        COST_BOW_DODGE = b.comment("A dodge with a shot: in the Legolas stance and the bow's combat flow (was 10). [default]")
                .defineInRange("bowDodge", 5.0, 0.0, 10000.0);
        // Renamed from dashStrike (7.5) so worlds made before get the author's number.
        COST_DASH = b.comment("Dash strike: sprinting with W and a sword, the left button (author: 15).")
                .defineInRange("dash", 15.0, 0.0, 10000.0);
        COST_FINISHER = b.comment("Finisher: the left button held at point blank to a weakened enemy. [default]")
                .defineInRange("finisher", 5.0, 0.0, 10000.0);
        COST_KICK = b.comment("The vault with a kick: W held, Space and R at a mob. [default]")
                .defineInRange("kick", 15.0, 0.0, 10000.0);
        COST_SLIDE_ATTACK = b.comment("The attack out of a slide: the left button while sliding with a sword or an axe. [default]")
                .defineInRange("slideAttack", 10.0, 0.0, 10000.0);
        COST_LEDGE_HOP = b.comment("A hop along a ledge (hanging, R for the hop mode, then A or D). [default]")
                .defineInRange("ledgeHop", 5.0, 0.0, 10000.0);
        b.pop();

        b.comment("How the moves behave.").push("moves");
        DODGE_ON_GROUND = b.comment("Dodges and rolls only from the ground, not in mid-air. [default]")
                .define("dodgeOnlyOnGround", true);
        // Renamed from slideBlocks / slideHeldBlocks (4 / 5) so worlds made before get the new lengths.
        SLIDE_TAP = b.comment("Slide length in blocks when C is tapped (author: 4, then a block more: 5).")
                .defineInRange("slideTapBlocks", 5.0, 1.0, 16.0);
        SLIDE_HELD = b.comment("Slide length in blocks when C is held; the player then goes on crawling (author: 5, then 6).")
                .defineInRange("slideHoldBlocks", 6.0, 1.0, 16.0);
        b.pop();

        b.comment("The vault over a mob at point blank (R + W): the player rolls over its back, it stoops and is shoved back.").push("hop");
        HOP_RANGE = b.comment("Blocks between the player and the mob for R + W to vault over it instead of rolling. [default]")
                .defineInRange("range", 2.5, 1.0, 6.0);
        HOP_SPEED_TICKS = b.comment("Ticks of Speed I after the vault, to run on (author: 1 s = 20).")
                .defineInRange("speedTicks", 20, 0, 600);
        VAULT_MIN_WIDTH = b.comment("Mobs narrower than this are not vaulted (a chicken is 0.4, a zombie 0.6, a pig 0.9). [default]")
                .defineInRange("minWidth", 0.6, 0.0, 10.0);
        VAULT_MIN_HEIGHT = b.comment("Mobs lower than this are not vaulted (a chicken is 0.7, a pig 0.9). [default]")
                .defineInRange("minHeight", 0.85, 0.0, 10.0);
        VAULT_MAX_HEIGHT = b.comment("Mobs taller than this are not vaulted (a zombie is 1.95, an enderman 2.9). [default]")
                .defineInRange("maxHeight", 2.2, 0.5, 10.0);
        // Renamed from shove (0.3) so worlds made before get the new strength.
        VAULT_SHOVE = b.comment("How hard the hand shoves the mob back as the player comes down beyond it (0.45: about 2.3 blocks;",
                        "author: 1-2 blocks, then half as far again). Heavy mobs that resist knockback go as far (author).")
                .defineInRange("shoveStrength", 0.45, 0.0, 3.0);
        KICK_DAMAGE = b.comment("The vault with a kick: the kick's damage (author: 1 heart = 2).")
                .defineInRange("kickDamage", 2.0, 0.0, 1000.0);
        KICK_STRENGTH = b.comment("The vault with a kick: how hard the kick flings the mob, whatever resists knockback in it",
                        "(0.9: about 4 blocks; author: \"a hard kick\"). [default]")
                .defineInRange("kickStrength", 0.9, 0.0, 3.0);
        b.pop();

        b.comment("The sword moves of the left button: the dash strike and the finisher.").push("strikes");
        DASH_CRIT = b.comment("Dash strike (sprinting with W, the left button): the blow is the weapon's times this (author: a crit",
                        "as in a jump; vanilla's crit is 1.5).")
                .defineInRange("dashCrit", 1.5, 1.0, 10.0);
        DASH_COOLDOWN = b.comment("Dash strike: seconds before it can be made again (author: at least 30).")
                .defineInRange("dashCooldownSeconds", 30, 0, 3600);
        DASH_WIDTH = b.comment("Dash strike: how far to either side of the lunge's line the blade still finds a mob (blocks, from",
                        "the line to the mob's side; author: a bigger hitbox, it was 0.7). [default]")
                .defineInRange("dashWidth", 1.1, 0.2, 4.0);
        SLIDE_ATTACK_CRIT = b.comment("The attack out of a slide: the blow is the weapon's times this (author: a crit).")
                .defineInRange("slideAttackCrit", 1.5, 1.0, 10.0);
        SLIDE_ATTACK_REACH = b.comment("The attack out of a slide: how far round the player the blade reaches (blocks; author: a normal",
                        "hitbox, not a tiny one). [default]")
                .defineInRange("slideAttackReach", 3.0, 1.0, 8.0);
        SLIDE_ATTACK_COOLDOWN = b.comment("The attack out of a slide: seconds before it can be made again (author: as the dash, 30).")
                .defineInRange("slideAttackCooldownSeconds", 30, 0, 3600);
        FINISH_HEALTH = b.comment("Finisher: a mob with this share of its health or less can be finished (author: 20 %).")
                .defineInRange("finisherHealth", 0.2, 0.0, 1.0);
        // Renamed from finisherRange (1.5) so worlds made before get the shorter reach.
        FINISH_RANGE = b.comment("Finisher: blocks from the player's middle to the mob's side (0.8: the two nearly touch;",
                        "author: only at point blank). [default]")
                .defineInRange("finisherReach", 0.8, 0.3, 4.0);
        FINISH_MAX_HEALTH = b.comment("Finisher and vault: mobs with more maximum health than this count as bosses (author: not on the",
                        "dragon nor the wither; those and the forge:bosses tag never). [default]")
                .defineInRange("bossHealth", 100.0, 1.0, 100000.0);
        b.pop();

        b.comment("Falling and the ledge.").push("fall");
        FALL_CUT = b.comment("R held while falling: the tuck and the roll on landing take this share off the fall's damage (author: 70 %).")
                .defineInRange("rollDamageCut", 0.7, 0.0, 1.0);
        LEDGE_GRAB = b.comment("The right button in a jump or a fall catches a ledge (author: before the sword block, placing",
                        "blocks and the rest of the right button).")
                .define("ledgeGrab", true);
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
    public static int appleFreeTicks() { return get(APPLE_INFINITE, 10) * 20; }
    public static int appleBoostTicks() { return get(APPLE_BOOST_SECONDS, 30) * 20; }
    public static float appleBoostFactor() { return f(APPLE_BOOST, 2.0); }
    public static int enchantedFreeTicks() { return get(ENCHANTED_INFINITE, 30) * 20; }
    public static float maxShields() { return f(MAX_SHIELDS, 10.0); }
    public static float shieldRegen() { return f(SHIELD_REGEN, 0.5); }
    public static int shieldRegenTicks() { return get(SHIELD_REGEN_SECONDS, 4) * 20; }
    public static float shieldRegenCost() { return f(SHIELD_REGEN_COST, 5.0); }
    public static float costDodge() { return f(COST_DODGE, 7.5); }
    public static float costRoll() { return f(COST_ROLL, 10.0); }
    public static float costSlide() { return f(COST_SLIDE, 10.0); }
    public static boolean dodgeOnlyOnGround() { return get(DODGE_ON_GROUND, true); }
    public static float slideBlocks() { return f(SLIDE_TAP, 5.0); }
    public static float slideHeldBlocks() { return f(SLIDE_HELD, 6.0); }
    public static int parryRearmTicks() { return get(PARRY_REARM, 10); }
    public static float costHop() { return f(COST_HOP, 12.5); }
    public static float costBarrage() { return f(COST_BARRAGE, 10.0); }
    public static float costBowDodge() { return f(COST_BOW_DODGE, 5.0); }
    public static float costDash() { return f(COST_DASH, 15.0); }
    public static float costFinisher() { return f(COST_FINISHER, 5.0); }
    public static float hopRange() { return f(HOP_RANGE, 2.5); }
    public static int hopSpeedTicks() { return get(HOP_SPEED_TICKS, 20); }
    public static float deflectShields() { return f(DEFLECT_SHIELDS, 1.5); }
    public static float guardMoveFactor() { return f(GUARD_MOVE, 0.2); }
    public static float kneelMoveFactor() { return f(KNEEL_MOVE, 0.3); }
    public static float vaultMinWidth() { return f(VAULT_MIN_WIDTH, 0.6); }
    public static float vaultMinHeight() { return f(VAULT_MIN_HEIGHT, 0.85); }
    public static float vaultMaxHeight() { return f(VAULT_MAX_HEIGHT, 2.2); }
    public static float vaultShove() { return f(VAULT_SHOVE, 0.45); }
    public static float dashCrit() { return f(DASH_CRIT, 1.5); }
    public static float finisherHealth() { return f(FINISH_HEALTH, 0.2); }
    public static float finisherRange() { return f(FINISH_RANGE, 0.8); }
    public static int dashCooldownTicks() { return get(DASH_COOLDOWN, 30) * 20; }
    public static float finisherMaxHealth() { return f(FINISH_MAX_HEALTH, 100.0); }
    public static float costKick() { return f(COST_KICK, 15.0); }
    public static float costSlideAttack() { return f(COST_SLIDE_ATTACK, 10.0); }
    public static float costLedgeHop() { return f(COST_LEDGE_HOP, 5.0); }
    public static float dashWidth() { return f(DASH_WIDTH, 1.1); }
    public static float slideAttackCrit() { return f(SLIDE_ATTACK_CRIT, 1.5); }
    public static float slideAttackReach() { return f(SLIDE_ATTACK_REACH, 3.0); }
    public static int slideAttackCooldownTicks() { return get(SLIDE_ATTACK_COOLDOWN, 30) * 20; }
    public static float kickDamage() { return f(KICK_DAMAGE, 2.0); }
    public static float kickStrength() { return f(KICK_STRENGTH, 0.9); }
    public static float fallRollCut() { return f(FALL_CUT, 0.7); }
    public static boolean ledgeGrab() { return get(LEDGE_GRAB, true); }

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
