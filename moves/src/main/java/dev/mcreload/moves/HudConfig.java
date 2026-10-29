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
    public static final ForgeConfigSpec.BooleanValue SWAP_SIDES, ARROWS, KICK_CUTSCENE;

    /** The moves the player may switch off, in the order of the settings' page. */
    public enum Moves {
        DODGES("dodges", "Side dodges: R + A / R + D."),
        ROLLS("rolls", "Rolls: R + W / R + S."),
        SLIDE("slide", "The slide: sprinting, C."),
        SLIDE_ATTACK("slideAttack", "The attack out of a slide: the left button while sliding with a sword or an axe."),
        VAULT("vault", "The vault over a mob: R + W at point blank."),
        KICK("kick", "The vault with a kick: W and Space down, R at a mob."),
        DIVE("dive", "The deep jump: W and Space down, R with no mob near."),
        DASH("dash", "The dash strike: sprinting with W and a sword, the left button."),
        FINISHER("finisher", "The finisher: the left button at point blank to a weakened mob."),
        GUARD("guard", "The sword block: the right button with a sword."),
        FALL_ROLL("fallRoll", "The tuck and the roll: R held while falling."),
        LEDGE_GRAB("ledgeGrab", "Catching a ledge: the right button in a jump or a fall."),
        AUTO_HANG("autoHang", "Down onto a ledge: Ctrl + W + C at a drop."),
        LEDGE_HOPS("ledgeHops", "The hops along a ledge: R while hanging."),
        VOLLEY("bowVolley", "The bow volley: a full draw, then the left button."),
        FLOW("bowFlow", "The bow's combat flow after the volley: the left button dodges and shoots."),
        LEGOLAS("legolas", "The Legolas stance: R with the bow fully drawn.");

        /** The key in the file and in the language files (kinetrix_moves.settings.move.<key>). */
        public final String key;
        private final String comment;
        private ForgeConfigSpec.BooleanValue value;

        Moves(String key, String comment) {
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
        b.pop();
        b.comment("Which moves this player's keys make (false: the keys do what they do without the mod).").push("moves");
        for (Moves m : Moves.values()) m.value = b.comment(m.comment).define(m.key, true);
        KICK_CUTSCENE = b.comment("The vault with a kick is a cutscene: the camera goes behind the player from first person and the",
                        "view narrows as time seems to slow (author, 2026-10-29).")
                .define("kickCutscene", true);
        b.pop();
        SPEC = b.build();
    }

    public static Style style() { return get(STYLE, Style.CLOCK); }
    public static Shape shape() { return get(SHAPE, Shape.HEATER); }
    public static boolean swapSides() { return get(SWAP_SIDES, false); }
    public static boolean arrows() { return get(ARROWS, true); }
    public static boolean kickCutscene() { return get(KICK_CUTSCENE, true); }

    private static <T> T get(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    private HudConfig() {}
}
