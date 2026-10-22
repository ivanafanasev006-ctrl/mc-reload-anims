package dev.mcreload.anim.client.rig;

import com.google.common.collect.MapMaker;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.Map;

/**
 * Finds the rig of a player (for effects rendered outside the animation stack, e.g. trails).
 * Keys are compared by identity and held weakly: Entity.equals/hashCode use the entity id, which
 * changes after construction, so a normal map would lose the entry.
 */
public final class RigRegistry {
    private static final Map<AbstractClientPlayer, PlayerRig> RIGS = new MapMaker().weakKeys().makeMap();
    private static final Map<Object, PlayerRig> BY_STACK = new MapMaker().weakKeys().makeMap();

    public static void put(AbstractClientPlayer player, PlayerRig rig) {
        RIGS.put(player, rig);
    }

    public static PlayerRig get(AbstractClientPlayer player) {
        return RIGS.get(player);
    }

    /** The rig living in a player's playerAnimator stack. */
    public static void putStack(Object stack, PlayerRig rig) {
        BY_STACK.put(stack, rig);
    }

    public static PlayerRig byStack(Object stack) {
        return BY_STACK.get(stack);
    }

    private RigRegistry() {}
}
