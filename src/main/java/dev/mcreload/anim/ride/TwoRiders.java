package dev.mcreload.anim.ride;

import dev.mcreload.anim.config.CommonConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Two on one horse, like in a boat: a second player climbs on behind the rider and sits on the
 * horse's back behind the saddle; the one in front steers. Works on any horse-like mount (vanilla
 * horses, donkeys, mules, and other mods' horses built on them, such as DragN's Livestock
 * Overhaul's), whatever way the mount seats its rider: the one behind is moved back from wherever
 * the mount put them. Llamas (not ridden) and camels (two seats of their own) are left alone.
 * Runs on both sides (server: who may get on; both: where they sit). Getting on goes through
 * Forge's entity-interact event, before the horse's own click handling, so it works the same for
 * every mod's horses whatever they do with a click on a horse that already has a rider.
 */
public final class TwoRiders {
    /** How far behind the rider the second one sits, as a share of the mount's width. */
    private static final double BACK_PER_WIDTH = 0.40;

    /** Seats on this mount: 2 for horses and the like, else 1 (left to the game). */
    public static int seats(Entity vehicle) {
        if (!(vehicle instanceof AbstractHorse) || vehicle instanceof Llama || vehicle instanceof Camel) return 1;
        if (!CommonConfig.twoRiders()) return 1;
        ResourceLocation id = EntityType.getKey(vehicle.getType());
        if (id.getPath().contains("llama") || id.getPath().contains("camel")) return 1;
        return 2;
    }

    /** Right-clicking this horse would put this player on behind its rider. */
    public static boolean canClimbOnBehind(AbstractHorse horse, Player player) {
        if (seats(horse) < 2 || player.isSecondaryUseActive() || player.isPassenger()) return false;
        if (horse.isBaby() || !horse.isTamed() || !horse.isSaddled()) return false;
        List<Entity> riders = horse.getPassengers();
        return riders.size() == 1 && riders.get(0) instanceof LivingEntity && riders.get(0) != player;
    }

    /** Right-clicking a horse someone is riding: climb on behind them. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof AbstractHorse horse)) return;
        Player player = event.getEntity();
        if (!canClimbOnBehind(horse, player)) return;
        if (!player.level().isClientSide) {
            player.setYRot(horse.getYRot());
            player.setXRot(horse.getXRot());
            // Forced: the checks that matter were made above, and a mod's horse may keep its own
            // idea of how many may ride it.
            player.startRiding(horse, true);
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        event.setCanceled(true);
    }

    /** The second rider of a two-seat mount (the one behind), else false. */
    public static boolean isBehind(Entity passenger) {
        Entity vehicle = passenger.getVehicle();
        if (vehicle == null || seats(vehicle) < 2) return false;
        List<Entity> riders = vehicle.getPassengers();
        return riders.size() >= 2 && riders.get(0) != passenger;
    }

    /** After the mount has seated this passenger: the one behind moves back onto the horse's back. */
    public static void placeBehind(Entity passenger) {
        if (!isBehind(passenger)) return;
        Entity vehicle = passenger.getVehicle();
        float yaw = (vehicle instanceof LivingEntity living ? living.yBodyRot : vehicle.getYRot()) * Mth.DEG_TO_RAD;
        double back = BACK_PER_WIDTH * vehicle.getBbWidth();
        passenger.setPos(passenger.getX() + Mth.sin(yaw) * back, passenger.getY(), passenger.getZ() - Mth.cos(yaw) * back);
    }

    private TwoRiders() {}
}
