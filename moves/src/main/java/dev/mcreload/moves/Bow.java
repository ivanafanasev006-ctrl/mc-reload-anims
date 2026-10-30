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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * The bow as a weapon (docs/combat-spec.md, 6): +50 % durability; in close combat a fixed 4
 * damage at a sword's pace, with a sword's sweep and a jump crit; on G the volley, three arrows on
 * the keys of {@code bow_barrage}, 0.55 s apart, and held, the hail, five to ten quick ones spread
 * about the look; the volley at the marked mobs (Marks), an arrow at each in turn. The arrows of all
 * three hit whatever they hit, the mob's moment of invulnerability after a hit aside (author,
 * 2026-10-30, 23:01: "it does not register, the arrow bounces off").
 */
public final class Bow {
    /** Bow durability multiplier (author: 384 -> 576). */
    public static final float DURABILITY = 1.5f;
    /** Close combat with a bow (author: 4 damage; a sword's attack speed, 1.6 a second). */
    public static final double MELEE_DAMAGE = 4.0, MELEE_SPEED = 1.6;
    /** The volley's arrows fly with the power of the bow's second drawing stage (vanilla: 0.65-0.9 of a full draw). */
    public static final float VOLLEY_POWER = 0.7f;
    /**
     * Ticks of the volley (G) at which its arrows leave the string: 0.55 s and then 0.55 s apart
     * (author, 2026-10-30, 23:01: "a fraction of a second slower"; they were 0.25 s apart, on the
     * animation's own releases at 11, 16 and 21, whose draws between are played at VOLLEY_PACE now).
     */
    public static final int[] VOLLEY_SHOTS = {11, 22, 33};
    public static final float VOLLEY_PACE = 5f / 11f;
    /**
     * The hail (G held): an arrow every HAIL_EVERY ticks from HAIL_FROM (the volley's draw and release
     * of 5 animation ticks looped 1.25 times faster), HAIL_MIN at least, HAIL_MAX at most, spread up
     * to HAIL_SPREAD degrees about the look. [default]
     */
    public static final int HAIL_FROM = 11, HAIL_EVERY = 4, HAIL_MIN = 5, HAIL_MAX = 10;
    public static final float HAIL_PACE = 5f / 4f, HAIL_SPREAD = 8f;
    /** The hail's animation: bow_barrage's draw and release from tick 16 to 21, over and over. */
    public static final int HAIL_LOOP_FROM = 16, HAIL_LOOP_TO = 21;
    /** The arrows of the volleys and the hail fly as from a full draw at the marked mobs (straighter, further). */
    public static final float AIMED_POWER = 1f;
    /** An arrow of a volley: it hits through the mob's moment of invulnerability after a hit. */
    private static final String VOLLEY_ARROW = "kinetrix_moves.volley";
    /** Vanilla's ids for an item's own damage and speed, so the tooltip shows them like a sword's. */
    private static final UUID DAMAGE_ID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF"),
            SPEED_ID = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");

    public static boolean isBow(ItemStack stack) {
        return stack.getItem() instanceof BowItem;
    }

    /** Drawing a bow in the main hand, as the flow and the Legolas stance need. */
    public static boolean drawing(Player p) {
        return p.isUsingItem() && isBow(p.getUseItem()) && isBow(p.getMainHandItem());
    }

    /** The bow fully drawn (vanilla: 1 s). */
    public static boolean fullyDrawn(Player p) {
        return drawing(p) && p.getTicksUsingItem() >= 20;
    }

    /**
     * A bow move's timing (Move): the animation from tick {@code animStart}, held still at tick
     * {@code holdAt} (-1: it plays out by itself); the arrow leaves at move tick {@code release}
     * (-1: none); another move may cut in from {@code freeFrom}.
     */
    public record Shot(int animStart, int holdAt, int release, int freeFrom) {}

    static Shot shot(int animStart, int holdAt, int release, int freeFrom) {
        return new Shot(animStart, holdAt, release, freeFrom);
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

    /** Better Combat swings the bow itself (its own strikes, data/minecraft/weapon_attributes/bow.json), hitting all in the arc. */
    private static final boolean BETTER_COMBAT = net.minecraftforge.fml.ModList.get().isLoaded("bettercombat");

    /**
     * A sword's sweep with a bow: a full-strength blow on the ground, not a crit nor a sprint hit,
     * also hits whoever stands around the target (vanilla's rules and numbers). With Better Combat
     * its strikes hit everyone in their arc instead.
     */
    @SubscribeEvent
    public static void sweep(AttackEntityEvent event) {
        if (BETTER_COMBAT) return;
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

    // ------------------------------------------------------------------ the volley, the hail, the marked

    /**
     * Ticks of drawing the server lets the full draw go short: the server hears of the draw that much
     * after the client (ping, a busy tick).
     */
    private static final int SERVER_DRAW_SLACK = 8;

    /** G with a bow can be made: a bow in the main hand and something to shoot (drawn or not: the move takes the bow over). */
    public static boolean volleyReady(Player p) {
        ItemStack bow = p.getMainHandItem();
        return isBow(bow) && (!p.getProjectile(bow).isEmpty() || p.getAbilities().instabuild);
    }

    /** The arrow of the hail at this tick (0-based), -1: none. */
    public static int hailArrow(int tick) {
        int d = tick - HAIL_FROM;
        return d >= 0 && d % HAIL_EVERY == 0 && d / HAIL_EVERY < HAIL_MAX ? d / HAIL_EVERY : -1;
    }

    /** The hail goes on to another arrow or ends here, a tick before it (after the fifth). */
    public static boolean hailDecides(int tick) {
        int k = hailArrow(tick + 1);
        return k >= HAIL_MIN;
    }

    /** The arrow of the volley at this tick (0-based), -1: none. */
    public static int volleyArrow(int tick) {
        for (int k = 0; k < VOLLEY_SHOTS.length; k++) if (VOLLEY_SHOTS[k] == tick) return k;
        return -1;
    }

    /** Every server tick of the volley, the hail and the volley at the marked: their arrows. */
    static void serverTick(ServerPlayer p, Stamina s) {
        if (s.move == Move.BARRAGE) {
            int k = volleyArrow(s.moveTick);
            if (k >= 0) volleyShot(p, s, k, 0f);
        } else if (s.move == Move.HAIL) {
            int k = hailArrow(s.moveTick);
            if (k >= 0) {
                if (k >= VOLLEY_SHOTS.length) s.spend(MovesConfig.costHailExtra(), p);
                volleyShot(p, s, k, HAIL_SPREAD);
            }
        } else if (s.move != null && s.move.marked() && s.moveTick == s.move.shot.release()) {
            markedShot(p, s);
        }
    }

    /** The k-th arrow of G: at the marked mobs in sight in turn, or the way the player looks (spread up to {@code spread} degrees). */
    private static void volleyShot(ServerPlayer p, Stamina s, int k, float spread) {
        java.util.List<LivingEntity> marked = Marks.inSight(p, s);
        if (!marked.isEmpty()) {
            shoot(p, AIMED_POWER, Marks.aim(p, marked.get(k % marked.size()), AIMED_POWER * 3f));
            return;
        }
        Vec3 look = Vec3.directionFromRotation(p.getXRot(), p.getYRot());     // as the vanilla bow shoots
        if (spread > 0f) {
            float yaw = p.getYRot() + (p.getRandom().nextFloat() * 2 - 1) * spread;
            float pitch = p.getXRot() + (p.getRandom().nextFloat() * 2 - 1) * spread * 0.5f;
            look = Vec3.directionFromRotation(pitch, yaw);
        }
        shoot(p, VOLLEY_POWER, look);
    }

    /** The arrow of the volley at the marked's step: at its mob, if it is still there to hit. */
    static void markedShot(ServerPlayer p, Stamina s) {
        if (s.stepShot) return;
        s.stepShot = true;
        if (p.level().getEntity(s.target) instanceof LivingEntity e && e.isAlive() && Marks.seen(p, e)) {
            s.markedShot.add(e.getId());
            shoot(p, AIMED_POWER, Marks.aim(p, e, AIMED_POWER * 3f));
        }
    }

    /** The mob a step of the volley at the marked is made at: marked, in sight, not shot at yet in this volley. */
    static boolean markedTarget(ServerPlayer p, Stamina s, Entity e) {
        return e instanceof LivingEntity le && s.marks.containsKey(e.getId()) && Marks.markable(p, le) && Marks.seen(p, le)
                && (s.move == null || !s.move.marked() || !s.markedShot.contains(e.getId()));
    }

    /**
     * The bow let go at the full draw with marked mobs in sight and the volley at them ready: no arrow of
     * the bow's own, the volley's steps shoot (the client starts them, BowVolley.released).
     */
    @SubscribeEvent
    public static void released(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Stop event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !isBow(event.getItem())) return;
        Stamina s = Stamina.of(p);
        if (p.getTicksUsingItem() >= 20 - SERVER_DRAW_SLACK && !s.marks.isEmpty() && Marks.volleyReady(p, s)) event.setCanceled(true);
    }

    /** An arrow of a volley hits through the moment of invulnerability the hit before gave the mob (it would bounce off). */
    @SubscribeEvent
    public static void volleyHit(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getSource().getDirectEntity() instanceof AbstractArrow a && a.getPersistentData().getBoolean(VOLLEY_ARROW)) {
            event.getEntity().invulnerableTime = 0;
        }
    }

    /** One arrow from the bow in hand, as the vanilla bow looses it (enchantments, ammunition, wear), along {@code dir}. */
    static void shoot(ServerPlayer p, float power, Vec3 dir) {
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
        arrow.shoot(dir.x, dir.y, dir.z, power * 3f, 1f);
        arrow.getPersistentData().putBoolean(VOLLEY_ARROW, true);
        if (power >= 1f) arrow.setCritArrow(true);
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
