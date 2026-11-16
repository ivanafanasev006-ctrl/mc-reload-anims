package dev.mcreload.moves.client;

import dev.mcreload.moves.Skill;
import net.minecraft.client.resources.language.I18n;

import java.util.Locale;

/**
 * The menu's search for a move (author, 2026-11-16: "it should find by similar words or synonyms"): each word asked for is
 * found in the move's name, its description, its weapon or its list of other words for it (kinetrix_combat.skill.*.words),
 * as written or a letter or two off (a slip, another ending).
 */
final class SkillSearch {
    static boolean matches(Skill k, String query) {
        String q = norm(query).trim();
        if (q.isEmpty()) return true;
        String hay = norm(I18n.get("kinetrix_combat.settings.move." + k.key) + " " + I18n.get("kinetrix_combat.settings.move." + k.key + ".tip")
                + " " + I18n.get("kinetrix_combat.skill." + k.key + ".words") + " "
                + I18n.get("kinetrix_combat.weapon." + k.weapon.name().toLowerCase(Locale.ROOT)) + " " + k.key);
        String[] tokens = hay.split("[^\\p{L}\\p{N}]+");
        for (String w : q.split("\\s+")) if (!word(hay, tokens, w)) return false;
        return true;
    }

    private static boolean word(String hay, String[] tokens, String w) {
        if (hay.contains(w)) return true;
        if (w.length() < 4) return false;
        int slack = w.length() >= 7 ? 2 : 1;
        for (String t : tokens) {
            if (t.length() < 3) continue;
            String head = t.length() > w.length() ? t.substring(0, w.length()) : t;
            if (distance(w, head) <= slack) return true;
        }
        return false;
    }

    static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    /** Letters to change, add or take away to make one word the other (Levenshtein). */
    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    private SkillSearch() {}
}
