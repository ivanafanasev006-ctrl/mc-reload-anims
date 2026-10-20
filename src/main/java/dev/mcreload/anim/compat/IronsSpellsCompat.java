package dev.mcreload.anim.compat;

import dev.mcreload.anim.McReloadAnim;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads Iron's Spells 'n Spellbooks casting state through reflection, so this mod has no
 * compile-time or run-time dependency on it. Works for every player Iron's syncs to the client.
 */
public final class IronsSpellsCompat {
    public static final String MODID = "irons_spellbooks";

    /** Rough posture families for spell schools. */
    public enum Style {
        /** fire, blood, evocation, eldritch: leaning into the cast */
        AGGRESSIVE,
        /** holy, lightning: chest up, looking up */
        ASCENDANT,
        /** ice, nature, ender, aqua: light, floaty */
        FLOWING,
        NEUTRAL
    }

    public record CastInfo(boolean casting, String spellId, Style style, boolean continuous) {
        public static final CastInfo NONE = new CastInfo(false, "", Style.NEUTRAL, false);
    }

    private static boolean ready;
    private static boolean warned;
    private static Method getSyncedData, isCasting, getSpellId;
    private static Method getSpell, getSchoolType, getCastType;
    private static final Map<String, CastInfo> CACHE = new HashMap<>();

    public static void init() {
        if (!ModList.get().isLoaded(MODID)) return;
        try {
            Class<?> clientData = Class.forName("io.redspace.ironsspellbooks.player.ClientMagicData");
            Class<?> synced = Class.forName("io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData");
            getSyncedData = clientData.getMethod("getSyncedSpellData", LivingEntity.class);
            isCasting = synced.getMethod("isCasting");
            getSpellId = synced.getMethod("getCastingSpellId");
            ready = true;
        } catch (Throwable t) {
            McReloadAnim.LOG.warn("[mcreload] Iron's Spells found but its casting API changed; spell stances disabled.", t);
            return;
        }
        try {
            Class<?> registry = Class.forName("io.redspace.ironsspellbooks.api.registry.SpellRegistry");
            Class<?> spell = Class.forName("io.redspace.ironsspellbooks.api.spells.AbstractSpell");
            getSpell = registry.getMethod("getSpell", String.class);
            getSchoolType = spell.getMethod("getSchoolType");
            getCastType = spell.getMethod("getCastType");
        } catch (Throwable t) {
            McReloadAnim.LOG.info("[mcreload] Iron's Spells school lookup unavailable, using neutral stance for all spells.");
        }
        McReloadAnim.LOG.info("[mcreload] Iron's Spells 'n Spellbooks detected: casting stances enabled.");
    }

    public static boolean isReady() {
        return ready;
    }

    public static CastInfo query(LivingEntity entity) {
        if (!ready) return CastInfo.NONE;
        try {
            Object data = getSyncedData.invoke(null, entity);
            if (data == null || !(Boolean) isCasting.invoke(data)) return CastInfo.NONE;
            Object id = getSpellId.invoke(data);
            return describe(id == null ? "" : id.toString());
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                McReloadAnim.LOG.warn("[mcreload] Could not read Iron's Spells casting state; spell stances disabled.", t);
            }
            ready = false;
            return CastInfo.NONE;
        }
    }

    private static CastInfo describe(String spellId) {
        CastInfo cached = CACHE.get(spellId);
        if (cached != null) return cached;

        Style style = Style.NEUTRAL;
        boolean continuous = false;
        if (getSpell != null) {
            try {
                Object spell = getSpell.invoke(null, spellId);
                if (spell != null) {
                    Object school = getSchoolType.invoke(spell);
                    style = styleOf(schoolName(school));
                    Object castType = getCastType.invoke(spell);
                    continuous = castType != null && castType.toString().toUpperCase(Locale.ROOT).contains("CONTINUOUS");
                }
            } catch (Throwable ignored) {
                // fall back to neutral
            }
        }
        CastInfo info = new CastInfo(true, spellId, style, continuous);
        CACHE.put(spellId, info);
        return info;
    }

    private static String schoolName(Object school) {
        if (school == null) return "";
        try {
            Object id = school.getClass().getMethod("getId").invoke(school);
            if (id != null) return id.toString().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            // older/newer API: use toString
        }
        return school.toString().toLowerCase(Locale.ROOT);
    }

    private static Style styleOf(String school) {
        if (school.contains("fire") || school.contains("blood") || school.contains("evocation") || school.contains("eldritch")) {
            return Style.AGGRESSIVE;
        }
        if (school.contains("holy") || school.contains("lightning")) return Style.ASCENDANT;
        if (school.contains("ice") || school.contains("nature") || school.contains("ender") || school.contains("aqua")) {
            return Style.FLOWING;
        }
        return Style.NEUTRAL;
    }

    private IronsSpellsCompat() {}
}
