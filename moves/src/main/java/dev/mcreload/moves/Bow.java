package dev.mcreload.moves;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * The bow as a weapon (docs/combat-spec.md, 6): +50 % durability; in close combat a fixed 4
 * damage at a sword's pace, with a sword's sweep and a jump crit; the volley (full draw, then the
 * left button) fires three arrows on the keys of {@code bow_barrage}.
 */
public final class Bow {
    /** Bow durability multiplier (author: 384 -> 576). */
    public static final float DURABILITY = 1.5f;
    /** Close combat with a bow (author: 4 damage; a sword's attack speed, 1.6 a second). */
    public static final double MELEE_DAMAGE = 4.0, MELEE_SPEED = 1.6;
    /** The volley's arrows fly with the power of the bow's second drawing stage (vanilla: 0.65-0.9 of a full draw). */
    public static final float VOLLEY_POWER = 0.7f;
    /** Ticks of {@code bow_barrage} at which the volley's arrows leave the string (0.54, 0.79, 1.04 s). */
    public static final int[] VOLLEY_SHOTS = {11, 16, 21};
    /** Vanilla's ids for an item's own damage and speed, so the tooltip shows them like a sword's. */
    private static final UUID DAMAGE_ID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF"),
            SPEED_ID = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");

    public static boolean isBow(ItemStack stack) {
        return stack.getItem() instanceof BowItem;
    }

    /** Every bow in the game lasts half as long again. */
    static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (Item item : ForgeRegistries.ITEMS) {
                if (!(item instanceof BowItem) || item.getMaxDamage() <= 0) continue;
                int more = Math.round(item.getMaxDamage() * DURABILITY);
                try {
                    ObfuscationReflectionHelper.setPrivateValue(Item.class, item, more, "f_41371_");
                } catch (RuntimeException e) {
                    KinetrixMoves.LOG.warn("[kinetrix_moves] could not change the durability of {}", ForgeRegistries.ITEMS.getKey(item), e);
                }
            }
        });
    }

    /** In the main hand a bow hits like a sword: 4 damage, 1.6 attacks a second. */
    @SubscribeEvent
    public static void attributes(ItemAttributeModifierEvent event) {
        if (event.getSlotType() != EquipmentSlot.MAINHAND || !isBow(event.getItemStack())) return;
        // The player's base is 1 damage and 4 attacks a second.
        event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(DAMAGE_ID, "Bow melee", MELEE_DAMAGE - 1.0, AttributeModifier.Operation.ADDITION));
        event.addModifier(Attributes.ATTACK_SPEED, new AttributeModifier(SPEED_ID, "Bow melee", MELEE_SPEED - 4.0, AttributeModifier.Operation.ADDITION));
    }

    /**
     * A sword's sweep with a bow: a full-strength blow on the ground, not a crit nor a sprint hit,
     * also hits whoever stands around the target (vanilla's rules and numbers).
     */
    @SubscribeEvent
    public static void sweep(AttackEntityEvent event) {
        Player p = event.getEntity();
        Entity target = event.getTarget();
        if (p.level().isClientSide || !isBow(p.getMainHandItem()) || !target.isAttackable() || target.skipAttackInteraction(p)) return;
        boolean full = p.getAttackStrengthScale(0.5f) > 0.9f;
        boolean crit = full && p.fallDistance > 0f && !p.onGround() && !p.onClimbable() && !p.isInWater() && !p.isPassenger();
        boolean sprintHit = full && p.isSprinting();
        double walked = p.walkDist - p.walkDistO;
        if (!full || crit || sprintHit || !p.onGround() || walked >= p.getSpeed()) return;
        float damage = 1.0f + EnchantmentHelper.getSweepingDamageRatio(p) * (float) MELEE_DAMAGE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(1.0, 0.25, 1.0))) {
            if (e == p || e == target || p.isAlliedTo(e) || (e instanceof ArmorStand stand && stand.isMarker()) || p.distanceToSqr(e) >= 9.0) continue;
            e.knockback(0.4, Mth.sin(p.getYRot() * Mth.DEG_TO_RAD), -Mth.cos(p.getYRot() * Mth.DEG_TO_RAD));
            e.hurt(p.damageSources().playerAttack(p), damage);
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, p.getSoundSource(), 1.0f, 1.0f);
        if (p.level() instanceof ServerLevel level) {
            double dx = -Mth.sin(p.getYRot() * Mth.DEG_TO_RAD), dz = Mth.cos(p.getYRot() * Mth.DEG_TO_RAD);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + dx, p.getY(0.5), p.getZ() + dz, 0, dx, 0.0, dz, 0.0);
        }
    }

    // ------------------------------------------------------------------ the volley

    /** Can the volley start: a bow fully drawn, with something to shoot. */
    public static boolean volleyReady(Player p) {
        ItemStack bow = p.getUseItem();
        return p.isUsingItem() && isBow(bow) && p.getTicksUsingItem() >= 20
                && (!p.getProjectile(bow).isEmpty() || p.getAbilities().instabuild);
    }

    /** Every server tick of the volley: the arrows leave the string on the animation's keys. */
    static void serverTick(ServerPlayer p, Stamina s) {
        if (s.move != Move.BARRAGE) return;
        for (int shot : VOLLEY_SHOTS) if (s.moveTick == shot) shoot(p, VOLLEY_POWER);
    }

    /** One arrow from the bow in hand, as the vanilla bow looses it (enchantments, ammunition, wear). */
    static void shoot(ServerPlayer p, float power) {
        ItemStack bow = p.getMainHandItem();
        if (!isBow(bow)) return;
        boolean infinite = p.getAbilities().instabuild || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, bow) > 0;
        ItemStack ammo = p.getProjectile(bow);
        if (ammo.isEmpty()) {
            if (!infinite) return;
            ammo = new ItemStack(Items.ARROW);
        }
        boolean free = p.getAbilities().instabuild
                || (ammo.getItem() instanceof ArrowItem arrowItem && arrowItem.isInfinite(ammo, bow, p));
        ArrowItem arrowItem = (ArrowItem) (ammo.getItem() instanceof ArrowItem ? ammo.getItem() : Items.ARROW);
        AbstractArrow arrow = ((BowItem) bow.getItem()).customArrow(arrowItem.createArrow(p.level(), ammo, p));
        arrow.shootFromRotation(p, p.getXRot(), p.getYRot(), 0f, power * 3f, 1f);
        int powerLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, bow);
        if (powerLevel > 0) arrow.setBaseDamage(arrow.getBaseDamage() + powerLevel * 0.5 + 0.5);
        int punch = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, bow);
        if (punch > 0) arrow.setKnockback(punch);
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, bow) > 0) arrow.setSecondsOnFire(100);
        bow.hurtAndBreak(1, p, e -> e.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
        if (free || (p.getAbilities().instabuild && (ammo.is(Items.SPECTRAL_ARROW) || ammo.is(Items.TIPPED_ARROW)))) {
            arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        }
        p.level().addFreshEntity(arrow);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1f,
                1f / (p.getRandom().nextFloat() * 0.4f + 1.2f) + power * 0.5f);
        if (!free && !p.getAbilities().instabuild) {
            ammo.shrink(1);
            if (ammo.isEmpty()) p.getInventory().removeItem(ammo);
        }
        p.awardStat(Stats.ITEM_USED.get(bow.getItem()));
    }

    private Bow() {}
}
