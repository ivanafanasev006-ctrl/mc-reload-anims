package dev.mcreload.moves;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Players' arrows in water (author, after 0.9.1: "in water the bow is useless now; it must at least attack as ever
 * there"): vanilla's arrow keeps 0.6 of its speed a tick in water (a full draw crawls after 4 ticks, 7 blocks on); a player's (a
 * bow's, a crossbow's) keeps MovesConfig.waterArrowInertia of it instead, as a trident nearly does. Every server
 * tick, after the arrows have moved, the ones in water are given back what vanilla took over that.
 */
public final class WaterArrows {
    /** Vanilla's arrow in water (AbstractArrow.getWaterInertia). */
    private static final float VANILLA = 0.6f;
    private static final Map<AbstractArrow, Boolean> ARROWS = new WeakHashMap<>();

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof AbstractArrow a)) return;
        if (a.getOwner() instanceof Player && a.getClass() != net.minecraft.world.entity.projectile.ThrownTrident.class) ARROWS.put(a, true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ARROWS.isEmpty()) return;
        float k = MovesConfig.waterArrowInertia() / VANILLA;
        ARROWS.keySet().removeIf(a -> {
            if (a.isRemoved()) return true;
            // stuck in a block it does not move (and is never a flying arrow again)
            if (a.getDeltaMovement().lengthSqr() < 1.0E-6) return a.tickCount > 2;
            if (a.isInWater()) a.setDeltaMovement(a.getDeltaMovement().scale(k));
            return false;
        });
    }

    private WaterArrows() {}
}
