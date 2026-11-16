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
    VOLLEY("bowVolley", Weapon.BOW),
    HAIL("bowHail", Weapon.BOW),
    MARKS("bowMarks", Weapon.BOW),
    LEGOLAS("legolas", Weapon.BOW),
    SLIDE_SHOT("slideShot", Weapon.BOW),
    XBOW_G("crossbowAbility", Weapon.CROSSBOW),
    XBOW_FAN("crossbowFan", Weapon.CROSSBOW),
    XBOW_ROLLS("crossbowRolls", Weapon.CROSSBOW),
    /** The deep jump, W + Space + R with no mob near (author, 2026-11-16: "a scroll for the deep roll"). */
    DIVE("dive", Weapon.ANY);

    /** Moves on the wheel at most. */
    public static final int WHEEL = 6;

    public final String key;
    public final Weapon weapon;

    Skill(String key, Weapon weapon) {
        this.key = key;
        this.weapon = weapon;
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
            case CHAIN_LEAP, CHAIN_HOP, CHAIN_HOP_CCW, CHAIN_HOP_STAB, CHAIN_HOP_STAB_CCW, CHAIN_FLIP -> CHAIN;
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
            case XBOW_VOLLEY, XBOW_SPIN -> XBOW_G;
            case XBOW_FAN, XBOW_FAN_SHOT -> XBOW_FAN;
            case XBOW_ROLL_BACK, XBOW_ROLL_FRONT, XBOW_ROLL_LEFT, XBOW_ROLL_RIGHT -> XBOW_ROLLS;
            default -> null;
        };
    }

    /** What a skill is made with. */
    public enum Weapon {
        SWORD, AXE, BLADE, BOW, CROSSBOW, ANY;

        public boolean in(Player p) {
            ItemStack hand = p.getMainHandItem();
            return switch (this) {
                case SWORD -> Strikes.sword(p);
                case AXE -> Strikes.axe(p);
                case BLADE -> Strikes.sword(p) || Strikes.axe(p);
                case BOW -> Bow.isBow(hand);
                case CROSSBOW -> hand.getItem() instanceof CrossbowItem;
                case ANY -> true;
            };
        }
    }
}
