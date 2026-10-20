package dev.mcreload.anim.client.rig;

import net.minecraft.client.player.AbstractClientPlayer;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Finds the rig of a player (for effects rendered outside the animation stack, e.g. trails). */
public final class RigRegistry {
    private static final Map<AbstractClientPlayer, PlayerRig> RIGS = Collections.synchronizedMap(new WeakHashMap<>());

    public static void put(AbstractClientPlayer player, PlayerRig rig) {
        RIGS.put(player, rig);
    }

    public static PlayerRig get(AbstractClientPlayer player) {
        return RIGS.get(player);
    }

    private RigRegistry() {}
}
