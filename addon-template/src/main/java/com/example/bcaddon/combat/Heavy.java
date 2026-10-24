package com.example.bcaddon.combat;

import com.example.bcaddon.AddonConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Held heavy attack: while the key is held, Better Combat performs the weapon's heavy attack
 * (see {@code mixin.PlayerAttackHelperMixin}) and swings are slower.
 *
 * <p>The state must be the same on both sides: the client picks the attack it animates and the
 * targets it sends, the server picks the attack whose damage multiplier it applies. The client
 * sends key changes ({@code net.HeavyHold}) before its swings, on the same connection, so the
 * server sees them in order. Better Combat may ask the server for the attack on a network
 * thread, hence the concurrent set.</p>
 */
public final class Heavy {
    private static final UUID SLOWDOWN = UUID.fromString("2f0a8b4e-6a43-4c39-9a51-7f1e2c9d3b10");
    private static final Set<UUID> HELD = ConcurrentHashMap.newKeySet();
    private static volatile boolean localHeld;

    /** Is this player holding the heavy attack key? Asked by the mixin, on both sides. */
    public static boolean isHeld(Player player) {
        if (player.level().isClientSide()) return localHeld && player.isLocalPlayer();
        return HELD.contains(player.getUUID());
    }

    public static boolean localHeld() {
        return localHeld;
    }

    public static void setLocal(boolean held) {
        localHeld = held;
    }

    /** Server: key state from the client; the slowdown is an attribute, so the client's swing timing follows. */
    public static void setServer(ServerPlayer player, boolean held) {
        if (held) HELD.add(player.getUUID()); else HELD.remove(player.getUUID());
        AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speed == null) return;
        speed.removeModifier(SLOWDOWN);
        double penalty = AddonConfig.HEAVY_SPEED_PENALTY.get();
        if (held && penalty > 0) {
            speed.addTransientModifier(new AttributeModifier(SLOWDOWN, "bcaddon heavy attack", -penalty,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    public static void forget(UUID player) {
        HELD.remove(player);
    }

    private Heavy() {}
}
