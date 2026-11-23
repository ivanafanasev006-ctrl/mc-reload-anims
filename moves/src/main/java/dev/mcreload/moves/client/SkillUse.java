package dev.mcreload.moves.client;

import dev.mcreload.moves.Chain;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.MovesConfig;
import dev.mcreload.moves.Scrolls;
import dev.mcreload.moves.Skill;
import dev.mcreload.moves.Stamina;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * A skill made from the wheel (Z) or its own key (author, 2026-11-16): as if its keys were pressed, so its rules are its
 * keys' (the weapon, the wait, held or tapped): G tapped or held, the right button held too for the axe's combo, both
 * buttons for the throw. The few made only in a certain state (in a slide, the bow drawn) say how.
 */
public final class SkillUse {
    /** Keys held by a skill made this way: how many ticks more. */
    private static final Map<KeyMapping, Integer> HELD = new HashMap<>();
    /** G pressed by one (once). */
    private static boolean abilityClick;

    static boolean held(KeyMapping k) {
        return HELD.getOrDefault(k, 0) > 0;
    }

    /** The G press made by a skill this tick (once). */
    static boolean takeAbilityClick() {
        boolean c = abilityClick;
        abilityClick = false;
        return c;
    }

    /** Every client tick: the held keys count down. */
    static void tick() {
        HELD.replaceAll((k, v) -> v - 1);
        HELD.values().removeIf(v -> v <= 0);
    }

    static void reset() {
        HELD.clear();
        abilityClick = false;
    }

    /** Held for {@code ticks} ticks from the next one (the count goes down as each begins). */
    private static void hold(KeyMapping k, int ticks) {
        HELD.put(k, ticks + 1);
    }

    /** The weapon this skill is made with is in hand (else the player is told which). */
    static boolean armed(LocalPlayer p, Skill k) {
        if (k.weapon.in(p)) return true;
        p.displayClientMessage(Component.translatable("kinetrix_combat.wheel.take",
                Component.translatable("kinetrix_combat.weapon.take." + k.weapon.name().toLowerCase())), true);
        return false;
    }

    /** Makes the skill now (from the wheel or its key). */
    static void use(Skill k) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        Stamina s = Stamina.of(p);
        if (!s.progress.usable(k)) {
            p.displayClientMessage(Component.translatable(!s.progress.learned.contains(k) ? "kinetrix_combat.wheel.not_learned"
                    : k.perk ? "kinetrix_combat.wheel.not_on_perk" : k.ult ? "kinetrix_combat.wheel.not_on_ult" : "kinetrix_combat.wheel.not_on", Scrolls.name(k)), true);
            return;
        }
        if (!armed(p, k)) return;
        KeyMapping g = MovesClient.ABILITY, use = mc.options.keyUse, attack = mc.options.keyAttack;
        switch (k) {
            // G tapped
            case DASH, WHIRL, VOLLEY, XBOW_G -> {
                abilityClick = true;
                hold(g, 1);
            }
            // G held: the sword's three turns, the bow's storm, the crossbow's fan (five arrows)
            case SWORD_SPIN -> {
                abilityClick = true;
                hold(g, 30);
            }
            case HAIL -> {
                abilityClick = true;
                hold(g, 12);
            }
            case XBOW_FAN -> {
                abilityClick = true;
                hold(g, 52);
            }
            // the right button and G held
            case AXE_SLAM, AXE_HEAVY, AXE_CHAIN, SWORD_CHARGE, SWORD_KNEE, HOSTAGE, BACK_SLIDE -> {
                if (!HeavyInput.use(p, s, k)) p.displayClientMessage(Component.translatable("kinetrix_combat.wheel.how." + k.key), true);
            }
            case AXE_COMBO, TIME_STOP, AXE_SHADOW, BOW_RAIN -> {
                abilityClick = true;
                hold(g, 8);
                hold(use, 8);
            }
            // both buttons, the left let go first: the full throw
            case THROW -> {
                hold(attack, MovesConfig.throwFullTicks() + 4);
                hold(use, MovesConfig.throwFullTicks() + 6);
            }
            case AIR_SPIN -> MovesClient.start(p, s, Move.AIR_SPIN, -1);
            case CHAIN -> ChainInput.begin(p, s, Chain.FIRST_REACH, 50, -1);
            case DIVE -> MovesClient.start(p, s, Move.DIVE, -1);
            case MARKS -> {
                if (!BowVolley.wheel(p, s)) p.displayClientMessage(Component.translatable("kinetrix_combat.wheel.how.bowMarks"), true);
            }
            // made only in a state of their own
            case LEGOLAS, SLIDE_SHOT, XBOW_ROLLS, GUARD_KICK, GUARD_COUNTER, GUARD_FINISH -> p.displayClientMessage(Component.translatable("kinetrix_combat.wheel.how." + k.key), true);
        }
    }

    private SkillUse() {}
}
