package dev.mcreload.anim.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.util.Set;
import java.util.UUID;

/**
 * Personality (Team Abnormals) has its own crawl (C) and sit (X) keys. With it installed our keys
 * start unbound (no double binding); its crawl is the same vanilla pose, so it gets our crawl
 * animation, and its sitting players get our sitting animation (it already lowers the model by
 * 0.55 units, so we lower it by the rest only).
 */
public final class PersonalityCompat {
    public static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("personality");
    /** How far Personality itself lowers a sitting player, blocks (0.55 model units at 15/16 scale). */
    public static final float OWN_DROP = 0.55f * 0.9375f;

    private static Set<UUID> synced;
    private static boolean lookedUp;

    @SuppressWarnings("unchecked")
    public static boolean isSitting(Player p) {
        if (!LOADED) return false;
        if (!lookedUp) {
            lookedUp = true;
            try {
                Object f = Class.forName("com.teamabnormals.personality.core.Personality")
                        .getField("SYNCED_SITTING_PLAYERS").get(null);
                if (f instanceof Set<?> s) synced = (Set<UUID>) s;
            } catch (Throwable t) {
                synced = null;
            }
        }
        try {
            return synced != null && synced.contains(p.getUUID());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private PersonalityCompat() {}
}
