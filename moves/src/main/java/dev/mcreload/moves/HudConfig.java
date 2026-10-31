package dev.mcreload.moves;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * config/kinetrix_moves-client.toml: how the bars look (docs/combat-spec.md, 9), and which moves
 * this player's keys make (author, 2026-10-29: a settings menu to switch off the lunges, the
 * strikes, the combos...). A move switched off is not started by its keys; the server's rules
 * are the same for everyone.
 */
public final class HudConfig {
    public static final ForgeConfigSpec SPEC;

    public enum Style { CLOCK, MEDIEVAL, STEEL }

    public enum Shape { HEATER, ROUND }

    public static final ForgeConfigSpec.EnumValue<Style> STYLE;
    public static final ForgeConfigSpec.EnumValue<Shape> SHAPE;
    public static final ForgeConfigSpec.BooleanValue SWAP_SIDES, ARROWS, TRAY, KICK_CUTSCENE, ATTACK_CAMERA;

    /** The moves the player may switch off, in the order of the settings' page. */
    public enum Moves {
        DODGES("dodges", "Side dodges: R + A / R + D."),
        ROLLS("rolls", "Rolls: R + W / R + S."),
        FAST_RUN("fastRun", "The fast run: Ctrl held while sprinting (7 blocks a second, eats energy)."),
        SLIDE("slide", "The slide: sprinting, C."),
        SLIDE_ATTACK("slideAttack", "The attack out of a slide: the left button while sliding with a sword, an axe or a bow."),
        SLIDE_SHOT("slideShot", "The shot out of a slide: the right button while sliding with a bow (a whole turn onto one knee, three arrows",
                "when it is let go; 20 s cooldown)."),
        VAULT("vault", "The vault over a mob: R + W at point blank."),
        KICK("kick", "The vault with a kick: W and Space down, R at a mob."),
        ROLL_KICK("rollKick", "The roll kick: Ctrl (the fast run), W and Space down, R at a mob (a back roll after it)."),
        DIVE("dive", "The deep jump: W and Space down, R with no mob near."),
        DASH("dash", "The dash strike: the left and the right button held together with a sword."),
        SWORD_SPIN("swordSpin", "The sword's quick turn: G with a sword (held: up to 3 turns; the keys walk the player meanwhile)."),
        WHIRL("whirlwind", "The axe whirlwind: G with an axe, 3 turns the way the camera looks (held: up to 5)."),
        FINISHER("finisher", "The finisher: the left button at point blank to a weakened mob."),
        GUARD("guard", "The sword block: the right button with a sword."),
        FALL_ROLL("fallRoll", "The tuck and the roll: R held while falling."),
        PLUNGE("plunge", "The blade driven into the ground on landing from a fall, a sword or an axe in hand."),
        LEDGE_GRAB("ledgeGrab", "Catching a ledge: the right button in a jump or a fall."),
        AUTO_HANG("autoHang", "Down onto a ledge: Ctrl + W + C at a drop."),
        LEDGE_HOPS("ledgeHops", "The hops along a ledge (A / D while hanging) and the leap to another edge looked at (Space)."),
        VOLLEY("bowVolley", "G with a bow: the volley, three arrows, 10 s cooldown (held: the hail, 5 to 10 spread out, 40 s)."),
        MARKS("bowMarks", "The bow's marks (the right button held, the left one on a mob) and the volley at the marked (the right button let go)."),
        LEGOLAS("legolas", "The Legolas stance: R with the bow fully drawn (12 s cooldown from its end).");

        /** The key in the file and in the language files (kinetrix_moves.settings.move.<key>). */
        public final String key;
        private final String[] comment;
        private ForgeConfigSpec.BooleanValue value;

        Moves(String key, String... comment) {
            this.key = key;
            this.comment = comment;
        }

        /** The player's keys make this move. */
        public boolean on() {
            return value == null || get(value, true);
        }

        public void set(boolean on) {
            value.set(on);
        }
    }

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("hud");
        STYLE = b.comment("Colours of the energy bar: CLOCK (gold of the vanilla clock), MEDIEVAL (dark orange,",
                        "medieval RPG) or STEEL. The shields are always steel grey.")
                .defineEnum("style", Style.CLOCK);
        SHAPE = b.comment("Shield icon: HEATER (a heraldic shield) or ROUND.").defineEnum("shieldShape", Shape.HEATER);
        SWAP_SIDES = b.comment("Shields on the right above hunger and energy on the left above armour (normally the other way round).")
                .define("swapSides", false);
        ARROWS = b.comment("Arrow counter left of the hotbar while a bow is in hand.").define("arrowCounter", true);
        TRAY = b.comment("The tray in the bottom right corner: the strikes with a cooldown that the weapon in hand can make (G, the",
                        "dash, the attacks out of a slide), each with its key and a bar that fills as it comes back (author, 2026-10-30).")
                .define("cooldownTray", true);
        b.pop();
        b.comment("Which moves this player's keys make (false: the keys do what they do without the mod).").push("moves");
        for (Moves m : Moves.values()) m.value = b.comment(m.comment).define(m.key, true);
        KICK_CUTSCENE = b.comment("The vault with a kick is a cutscene: the camera goes behind the player from first person and the",
                        "view narrows as time seems to slow (author, 2026-10-29).")
                .define("kickCutscene", true);
        ATTACK_CAMERA = b.comment("In first person the camera goes behind the player for an attack (the dash, the finishers, G, the attack out",
                        "of a slide, the blade into the ground) and comes back after it, so the move is seen (author, 2026-10-30).",
                        "false: the camera stays, the arms are drawn in front of it.")
                .define("attackCamera", true);
        b.pop();
        SPEC = b.build();
    }

    public static Style style() { return get(STYLE, Style.CLOCK); }
    public static Shape shape() { return get(SHAPE, Shape.HEATER); }
    public static boolean swapSides() { return get(SWAP_SIDES, false); }
    public static boolean arrows() { return get(ARROWS, true); }
    public static boolean tray() { return get(TRAY, true); }
    public static boolean kickCutscene() { return get(KICK_CUTSCENE, true); }
    public static boolean attackCamera() { return get(ATTACK_CAMERA, true); }

    private static <T> T get(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    private HudConfig() {}
}
