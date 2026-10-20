package dev.mcreload.anim.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/mcreload-client.toml */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue OTHER_PLAYERS;
    public static final ForgeConfigSpec.DoubleValue INTENSITY;
    public static final ForgeConfigSpec.DoubleValue LIVELINESS;

    public static final ForgeConfigSpec.BooleanValue LOCOMOTION;
    public static final ForgeConfigSpec.BooleanValue IDLE;
    public static final ForgeConfigSpec.BooleanValue AIRBORNE;
    public static final ForgeConfigSpec.BooleanValue LANDING;
    public static final ForgeConfigSpec.BooleanValue WATER;
    public static final ForgeConfigSpec.BooleanValue CLIMBING;
    public static final ForgeConfigSpec.BooleanValue COMBAT;
    public static final ForgeConfigSpec.BooleanValue REACTIONS;

    public static final ForgeConfigSpec.BooleanValue TRAILS;
    public static final ForgeConfigSpec.DoubleValue TRAIL_SECONDS;
    public static final ForgeConfigSpec.DoubleValue TRAIL_OPACITY;
    public static final ForgeConfigSpec.ConfigValue<String> TRAIL_COLOR;

    public static final ForgeConfigSpec.BooleanValue HARMONIZER;
    public static final ForgeConfigSpec.DoubleValue FADE_IN;
    public static final ForgeConfigSpec.DoubleValue FADE_OUT;
    public static final ForgeConfigSpec.DoubleValue YIELD;
    public static final ForgeConfigSpec.DoubleValue KEEP_LEGS;
    public static final ForgeConfigSpec.DoubleValue KEEP_HEAD;
    public static final ForgeConfigSpec.BooleanValue IRONS_SPELLS;

    public static final ForgeConfigSpec.BooleanValue INVERT_KNEE;
    public static final ForgeConfigSpec.BooleanValue INVERT_ELBOW;
    public static final ForgeConfigSpec.BooleanValue INVERT_TORSO;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("general");
        ENABLED = b.comment("Master switch for all animations (also bindable to a key in Controls).")
                .define("enabled", true);
        OTHER_PLAYERS = b.comment("Animate other players too, not only yourself.")
                .define("animateOtherPlayers", true);
        INTENSITY = b.comment("Global strength of every animation. 0 = vanilla look, 1 = default, 2 = very exaggerated.")
                .defineInRange("intensity", 1.0, 0.0, 2.0);
        LIVELINESS = b.comment("Secondary motion: limbs follow through springs, with weight, overshoot and a soft ragdoll wave. 0 = off (rigid), 1 = very floppy.")
                .defineInRange("liveliness", 0.6, 0.0, 1.0);
        b.pop();

        b.push("modules");
        LOCOMOTION = b.comment("Walking / sprinting / sneaking: knee and elbow bends, lean, torso twist, body bob.")
                .define("locomotion", true);
        IDLE = b.comment("Breathing and weight shifting while standing still.")
                .define("idle", true);
        AIRBORNE = b.comment("Jump tuck and falling flail.")
                .define("airborne", true);
        LANDING = b.comment("Landing squash after a fall.")
                .define("landing", true);
        WATER = b.comment("Swimming kicks and treading water.")
                .define("water", true);
        CLIMBING = b.comment("Ladder and vine climbing.")
                .define("climbing", true);
        COMBAT = b.comment("Keyframed weapon slashes (3-hit combo) and punches, bow, crossbow, trident, shield, eating, drinking.")
                .define("combat", true);
        REACTIONS = b.comment("Recoil when taking damage.")
                .define("reactions", true);
        b.pop();

        b.comment("White wind streak behind swung weapons (our slashes and Better Combat attacks).").push("trails");
        TRAILS = b.define("enabled", true);
        TRAIL_SECONDS = b.comment("How long the streak lingers, seconds.")
                .defineInRange("lifetimeSeconds", 0.22, 0.05, 1.0);
        TRAIL_OPACITY = b.comment("Brightness of the streak, 0..1.")
                .defineInRange("opacity", 0.75, 0.0, 1.0);
        TRAIL_COLOR = b.comment("Colour as RRGGBB hex.")
                .define("color", "FFFFFF");
        b.pop();

        b.comment("Blending with other playerAnimator-based mods (Better Combat, Iron's Spells, Emotecraft...).").push("compat");
        HARMONIZER = b.comment("Smoothly cross-fade whenever another mod's animation starts or stops on a body part.")
                .define("harmonizer", true);
        FADE_IN = b.comment("Seconds to blend INTO another mod's animation.")
                .defineInRange("fadeInSeconds", 0.10, 0.0, 1.0);
        FADE_OUT = b.comment("Seconds to blend back OUT of another mod's animation.")
                .defineInRange("fadeOutSeconds", 0.22, 0.0, 1.5);
        YIELD = b.comment("How much this mod quiets its own arm/torso/head motion while another mod animates that part (0..1).")
                .defineInRange("yieldStrength", 0.85, 0.0, 1.0);
        KEEP_LEGS = b.comment("While you are moving, keep our running legs even if another mod animates the legs (e.g. Better Combat attacks, spell casts). 0 = off, 1 = full.")
                .defineInRange("keepLegsWhileMoving", 0.85, 0.0, 1.0);
        KEEP_HEAD = b.comment("Keep the head looking where you look even if another mod animates it. 0 = off.")
                .defineInRange("keepHeadLook", 0.0, 0.0, 1.0);
        IRONS_SPELLS = b.comment("Extra casting stance, school-flavoured posture and release recoil for Iron's Spells 'n Spellbooks.")
                .define("ironsSpellsStance", true);
        b.pop();

        b.comment("Flip these if a bend goes the wrong way with your setup.").push("debug");
        INVERT_KNEE = b.define("invertKnees", false);
        INVERT_ELBOW = b.define("invertElbows", false);
        INVERT_TORSO = b.define("invertTorsoBend", false);
        b.pop();

        SPEC = b.build();
    }

    private ClientConfig() {}
}
