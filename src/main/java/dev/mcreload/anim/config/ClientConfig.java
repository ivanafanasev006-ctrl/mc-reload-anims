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
    public static final ForgeConfigSpec.BooleanValue IDLE_ACTIONS;
    public static final ForgeConfigSpec.BooleanValue TWO_HANDED;
    public static final ForgeConfigSpec.BooleanValue AIRBORNE;
    public static final ForgeConfigSpec.BooleanValue LANDING;
    public static final ForgeConfigSpec.BooleanValue WATER;
    public static final ForgeConfigSpec.BooleanValue CLIMBING;
    public static final ForgeConfigSpec.BooleanValue COMBAT;
    public static final ForgeConfigSpec.BooleanValue REACTIONS;
    public static final ForgeConfigSpec.BooleanValue POSTURES;
    public static final ForgeConfigSpec.BooleanValue TOGGLE_SIT;

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
    public static final ForgeConfigSpec.BooleanValue PLAYER_OVER_EMF;
    public static final ForgeConfigSpec.BooleanValue OWN_MOVES_FIRST;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> PRIORITY_OVER;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> WIDE_MOUNTS;
    public static final ForgeConfigSpec.BooleanValue BEND_SKIN_LAYERS;
    public static final ForgeConfigSpec.BooleanValue SYNC;

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
        IDLE_ACTIONS = b.comment("After standing still for a while: stretching, looking around, cracking the neck, kicking a pebble; playing with the item in hand.")
                .define("idleActions", true);
        TWO_HANDED = b.comment("Hold swords with both hands (samurai grip) when the other hand is empty.")
                .define("twoHandedSwords", false);
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
        POSTURES = b.comment("Crawling on the ground (a cave-diver crawl, not a swim stroke) and sitting.")
                .define("postures", true);
        b.pop();

        b.comment("Crawl (C) and sit (X) keys, see Controls. You crawl while the crawl key is held. Crawling needs",
                "Kinetrix on the server too (single player and the host of a LAN game always have it).").push("controls");
        TOGGLE_SIT = b.comment("true = press the sit key to sit down and again (or move) to stand up; false = sit while it is held.")
                .define("toggleSit", true);
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
        OWN_MOVES_FIRST = b.comment("While a Kinetrix emote, roll or landing is playing, other mods' player animations are muted.")
                .define("ownMovesFirst", true);
        PRIORITY_OVER = b.comment("Player Animator based mods (mod ids or animation namespaces) that only replace vanilla",
                        "animations (walking, mining, chopping...). Kinetrix always wins over them: their layers are muted.",
                        "Combat and magic mods (Better Combat, Iron's Spells...) are not listed and keep playing on top.")
                .defineListAllowEmpty(java.util.List.of("priorityOverMods"),
                        () -> java.util.List.of("vintage_animations", "vintageanimations"), o -> o instanceof String);
        WIDE_MOUNTS = b.comment("Mounts with a big, wide body (mod ids or entity ids, e.g. \"dragnlivestock\" or \"dragnlivestock:o_horse\"):",
                        "the rider sits lower, the legs point forward a little wider apart, and the hands hold the reins",
                        "low in front, apart, with the elbows bent. DragN's Livestock Overhaul horses, donkeys and mules by default.")
                .defineListAllowEmpty(java.util.List.of("wideMounts"), () -> java.util.List.of("dragnlivestock"), o -> o instanceof String);
        PLAYER_OVER_EMF = b.comment("Entity Model Features (Fresh Animations / Fresh Moves): keep the player on the vanilla model so",
                        "Kinetrix animates it with joints. Resource packs like Fresh Moves otherwise replace the player model and",
                        "animate it on top of Kinetrix. Mobs keep their Fresh Animations models. Takes effect after F3+T.")
                .define("playerOverEmf", true);
        BEND_SKIN_LAYERS = b.comment("Skin Layers 3D: bend the 3D outer skin layer at the elbows, knees and waist too.")
                .define("bendSkinLayers", true);
        SYNC = b.comment("Multiplayer: share your emotes, rolls, landings, idle fidgets and what you are doing (mining or",
                        "fighting, keys held) with other players who have Kinetrix. Needs Kinetrix on the server",
                        "(or on the host when playing over LAN); without it everyone is animated from what the game shows.")
                .define("syncWithOtherPlayers", true);
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
