package dev.mcreload.moves;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/**
 * The moves a player learns (author, 2026-11-16): written in scrolls (found in dungeons and village chests, given by the
 * trainer for his quests), at most {@link #WHEEL} of them on the wheel at a time, and only those work. The rest of the
 * mod's moves every player has from the start: the fast run, the dodges and rolls, the vault and the kicks on R, the
 * slide and the attack out of it, the soft landing, the block, the finisher on the left button, the ledge, the crossbow's
 * blows on the left button (author, 2026-11-16: "the crossbow's strikes by default, without a scroll").
 *
 * <p>{@code key}: the move's name in the language files (kinetrix_combat.settings.move.&lt;key&gt;) and in the saves.</p>
 */
public enum Skill {
    DASH("dash", Weapon.SWORD),
    SWORD_SPIN("swordSpin", Weapon.SWORD),
    AIR_SPIN("airSpin", Weapon.SWORD),
    CHAIN("chainFinisher", Weapon.SWORD),
    THROW("throw", Weapon.BLADE),
    WHIRL("whirlwind", Weapon.AXE),
    AXE_COMBO("axeCombo", Weapon.AXE),
    /** The sword's ultimate (author, 2026-11-20: into the square of the class's awakening abilities). */
    TIME_STOP("timeStop", Weapon.SWORD, Place.ULT),
    VOLLEY("bowVolley", Weapon.BOW),
    HAIL("bowHail", Weapon.BOW),
    MARKS("bowMarks", Weapon.BOW),
    LEGOLAS("legolas", Weapon.BOW),
    SLIDE_SHOT("slideShot", Weapon.BOW),
    XBOW_G("crossbowAbility", Weapon.CROSSBOW),
    XBOW_FAN("crossbowFan", Weapon.CROSSBOW),
    XBOW_ROLLS("crossbowRolls", Weapon.CROSSBOW),
    /**
     * The deep jump, W + Space + R with no mob near (author, 2026-11-16: "a scroll for the deep roll"); a perk (author,
     * 2026-11-16, later: "the perks, abilities like the deep roll and the attacks we add later").
     */
    DIVE("dive", Weapon.ANY, Place.PERK),
    /** The sword block's answers (author, 2026-11-19; GuardAnswers): perks, working while in the perks' row. */
    GUARD_KICK("guardKick", Weapon.SWORD, Place.PERK),
    GUARD_COUNTER("guardCounter", Weapon.SWORD, Place.PERK),
    GUARD_FINISH("guardFinish", Weapon.SWORD, Place.PERK),
    /**
     * The axe's ultimate (author, 2026-11-20, on the right button and G held, instead of the old triple whirl): a turn about
     * himself and he is gone; they stand lost, looking about; he comes out behind one, a blow, and it falls; a moment later
     * at another's side, and so on until all are dead (AxeShadow).
     */
    AXE_SHADOW("axeShadow", Weapon.AXE, Place.ULT),
    /** The moves of 2026-11-20, night (author; HeavyMoves). */
    AXE_SLAM("axeSlam", Weapon.AXE),
    AXE_HEAVY("axeHeavy", Weapon.AXE),
    AXE_CHAIN("axeChain", Weapon.AXE),
    SWORD_CHARGE("swordCharge", Weapon.SWORD),
    /** Perks (author, 2026-11-23: "the grab on V or the sword's advanced finisher: not skills but perks"). */
    SWORD_KNEE("swordKnee", Weapon.SWORD, Place.PERK),
    /** The living shield on V: with a sword the blade into its back (GuardAnswers), with a crossbow held at the left hand (HeavyMoves). */
    HOSTAGE("hostage", Weapon.GRAB, Place.PERK),
    BACK_SLIDE("backSlide", Weapon.RANGED, Place.PERK),
    /** The bow's ultimate (the awakening square). */
    BOW_RAIN("bowRain", Weapon.BOW, Place.ULT);

    /**
     * Where a skill goes (author, 2026-11-20): the wheel of moves, the row of perks above it, or the square of the class's
     * awakening abilities (the ultimates) beside it.
     */
    public enum Place { WHEEL, PERK, ULT }

    /** Moves on the wheel at most, perks in their row, ultimates in their 2 x 2 square (author, 2026-11-20: 10 perks). */
    public static final int WHEEL = 6, PERKS = 10, ULTS = 4;

    public final String key;
    public final Weapon weapon;
    public final Place place;
    /** A perk: it goes into the perks' row, not on the wheel. */
    public final boolean perk;
    /** An ultimate: it goes into the awakening square. */
    public final boolean ult;

    Skill(String key, Weapon weapon) {
        this(key, weapon, Place.WHEEL);
    }

    Skill(String key, Weapon weapon, Place place) {
        this.key = key;
        this.weapon = weapon;
        this.place = place;
        this.perk = place == Place.PERK;
        this.ult = place == Place.ULT;
    }

    public static Skill byKey(String key) {
        for (Skill s : values()) if (s.key.equals(key)) return s;
        return null;
    }

    public static Skill byId(int id) {
        Skill[] all = values();
        return id >= 0 && id < all.length ? all[id] : null;
    }

    /** The skill a move belongs to; null: one every player has (the dodges, the slide, the block, the ledge...). */
    public static Skill of(Move m) {
        return switch (m) {
            case DASH -> DASH;
            case SWORD_SPIN, SPIN_END -> SWORD_SPIN;
            case AIR_SPIN -> AIR_SPIN;
            case CHAIN_LEAP, CHAIN_HOP, CHAIN_HOP_CCW, CHAIN_HOP_STAB, CHAIN_HOP_STAB_CCW, CHAIN_FLIP, CR_LEAP, CR_HOP, CR_BLINK_A, CR_BLINK_B,
                 CR_BLINK_C, CR_BLINK_D -> CHAIN;
            case THROW_AIM, THROW, WEAPON_PULL -> THROW;
            case WHIRL, WHIRL_END -> WHIRL;
            case AXE_TRIPLE, AXE_COMBO_CHOP, AXE_COMBO_2, AXE_COMBO_DIAG, AXE_COMBO_3, AXE_COMBO_END -> AXE_COMBO;
            case BARRAGE -> VOLLEY;
            case HAIL -> HAIL;
            case MARKED_SPIN_LEFT, MARKED_SPIN_RIGHT, MARKED_ROLL_LEFT, MARKED_ROLL_RIGHT, MARKED_BACKSTEP, MARKED_CROUCH,
                 MARKED_STAND, MARKED_KNEEL -> MARKS;
            case LEGOLAS_LEFT, LEGOLAS_RIGHT -> LEGOLAS;
            case SLIDE_SHOT, SLIDE_SHOT_END -> SLIDE_SHOT;
            case DIVE -> DIVE;
            case GUARD_KICK -> GUARD_KICK;
            case GUARD_SHOVE -> GUARD_COUNTER;
            case GUARD_GRAB, HOSTAGE_HOLD, HOSTAGE_KICK, XB_GRAB, XB_HOLD, XB_SHOT -> HOSTAGE;
            case GUARD_FINISH -> GUARD_FINISH;
            case TS_STANCE, TS_SLASH, TS_THRUST, TS_BACK, TS_BELLY, TS_LEAP, TS_SPIN, TS_BRAKE -> TIME_STOP;
            case AS_SPIN, AS_HIDDEN, AS_CHOP, AS_SIDE, AS_HOOK -> AXE_SHADOW;
            case AX_SLAM -> AXE_SLAM;
            case AX_HEAVY -> AXE_HEAVY;
            case AXC_OPEN, AXC_SKULL, AXC_KICK, AXC_SPIN, AXC_UPPER -> AXE_CHAIN;
            case SW_CHARGE, SW_SKEWER, SW_CUT, SW_HALT -> SWORD_CHARGE;
            case SW_KNEE -> SWORD_KNEE;
            case XB_BACKSLIDE, XB_BELLY -> BACK_SLIDE;
            case BW_RAIN, BW_HIDDEN, BW_AIM, BW_JUMP, BW_FALLBACK, BW_FLIP, BW_TURN, BW_BRAKE -> BOW_RAIN;
            case XBOW_VOLLEY, XBOW_SPIN -> XBOW_G;
            case XBOW_FAN, XBOW_FAN_SHOT -> XBOW_FAN;
            case XBOW_ROLL_BACK, XBOW_ROLL_FRONT, XBOW_ROLL_LEFT, XBOW_ROLL_RIGHT -> XBOW_ROLLS;
            default -> null;
        };
    }

    /** What a skill is made with. */
    public enum Weapon {
        SWORD, AXE, BLADE, BOW, CROSSBOW, RANGED, GRAB, ANY;

        public boolean in(Player p) {
            ItemStack hand = p.getMainHandItem();
            return switch (this) {
                case SWORD -> Strikes.sword(p);
                case AXE -> Strikes.axe(p);
                case BLADE -> Strikes.sword(p) || Strikes.axe(p);
                case BOW -> Bow.isBow(hand);
                case CROSSBOW -> hand.getItem() instanceof CrossbowItem;
                case RANGED -> hand.getItem() instanceof CrossbowItem || Bow.isBow(hand);
                case GRAB -> Strikes.sword(p) || hand.getItem() instanceof CrossbowItem;
                case ANY -> true;
            };
        }
    }
}
