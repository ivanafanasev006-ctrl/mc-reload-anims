package dev.mcreload.moves;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/kinetrix_moves-client.toml: how the bars look (docs/combat-spec.md, 9). */
public final class HudConfig {
    public static final ForgeConfigSpec SPEC;

    public enum Style { CLOCK, MEDIEVAL, STEEL }

    public enum Shape { HEATER, ROUND }

    public static final ForgeConfigSpec.EnumValue<Style> STYLE;
    public static final ForgeConfigSpec.EnumValue<Shape> SHAPE;
    public static final ForgeConfigSpec.BooleanValue SWAP_SIDES, ARROWS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("hud");
        STYLE = b.comment("Colours of the shields and the energy bar: CLOCK (gold and steel of the vanilla clock),",
                        "MEDIEVAL (dark orange, medieval RPG) or STEEL.")
                .defineEnum("style", Style.CLOCK);
        SHAPE = b.comment("Shield icon: HEATER (a heraldic shield) or ROUND.").defineEnum("shieldShape", Shape.HEATER);
        SWAP_SIDES = b.comment("Shields on the right above hunger and energy on the left above armour (normally the other way round).")
                .define("swapSides", false);
        ARROWS = b.comment("Arrow counter left of the hotbar while a bow is in hand.").define("arrowCounter", true);
        b.pop();
        SPEC = b.build();
    }

    public static Style style() { return get(STYLE, Style.CLOCK); }
    public static Shape shape() { return get(SHAPE, Shape.HEATER); }
    public static boolean swapSides() { return get(SWAP_SIDES, false); }
    public static boolean arrows() { return get(ARROWS, true); }

    private static <T> T get(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    private HudConfig() {}
}
