package dev.mcreload.moves;

import dev.mcreload.moves.net.MovesNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The sword and the axe thrown (author, after 0.9.1: "hold RMB + LMB, let go of LMB and he throws it, about as a trident;
 * if it hits a mob it sticks in it; to get it out, LMB at point blank: he pulls it out, a little damage as it comes out,
 * a little stun; if that was the last blow the mob dies and the sword falls and sticks in the ground; from the ground he
 * picks it up himself").
 *
 * <p>The weapon flies as vanilla's trident does (a ThrownTrident carrying the weapon, so it flies, sticks in blocks and is
 * picked up by its owner walking over it as a trident is; players without the mod see a trident), point first. It hits
 * with the weapon's own damage (its attack damage and enchantments, MovesConfig.throwDamage) and, the mob alive, sticks in
 * it and goes about with it until pulled out (Move.WEAPON_PULL) or the mob dies (then it falls and sticks in the ground).
 * Clients with the mod draw the weapon instead of the trident (MovesNet.Thrown: what it is and where it is stuck).</p>
 */
public final class Thrown {
    /** The weapon leaves the hand (weapon_throw, 0.1 s); it comes out of the mob (weapon_pull, 0.3 s). round12.py's keys. */
    public static final int THROW_RELEASE = 2, PULL_TICK = 6;
    /** crossbow-like looping of the aim (weapon_throw_aim, 0.25 - 0.5 s), ticks of the animation. */
    public static final int AIM_LOOP_FROM = 5, AIM_LOOP_TO = 10;
    /** How far a stuck weapon may be pulled out from: blocks from the player's middle to the mob's side. [default] */
    public static final double PULL_REACH = 2.0;
    /** The weapon, saved with the trident (it is reloaded as a plain trident carrying it). */
    static final String TAG = KinetrixMoves.MODID + ".thrown";

    /** A weapon in the air or stuck (server): what it is, what it is stuck in (-1: none) and how. */
    public static final class State {
        public final ItemStack item;
        public final int owner;
        public int stuckIn = -1;
        public Vec3 offset = Vec3.ZERO;
        public float yaw, pitch;

        State(ItemStack item, int owner) {
            this.item = item;
            this.owner = owner;
        }
    }

    private static final Map<ThrownTrident, State> LIVE = new WeakHashMap<>();

    /** A sword or an axe in the main hand: it can be thrown. */
    public static boolean throwable(Player p) {
        return Strikes.sword(p) || Strikes.axe(p);
    }

    public static State state(Entity e) {
        return e instanceof ThrownTrident t ? LIVE.get(t) : null;
    }

    // ------------------------------------------------------------------ the throw

    /** The weapon leaves the hand along the look (the throw's release tick). */
    static void release(ServerPlayer p) {
        if (!throwable(p)) return;
        ItemStack item = p.getMainHandItem().copy();
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ThrownTrident t = new ThrownTrident(p.level(), p, item);
        t.shootFromRotation(p, p.getXRot(), p.getYRot(), 0f, MovesConfig.throwSpeed() * power(Stamina.of(p).throwHeld), 1f);
        // seen through walls wherever it is, to be found again (author, 2026-11-09)
        t.setGlowingTag(true);
        t.pickup = AbstractArrow.Pickup.ALLOWED;
        t.getPersistentData().put(TAG, item.save(new CompoundTag()));
        p.level().addFreshEntity(t);
        State st = new State(item, p.getId());
        LIVE.put(t, st);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1f, 1.2f);
        MovesNet.thrown(t, st, p);
        Stamina.of(p).combatTicks = MovesConfig.combatTicks();
    }

    /** The weapon's own damage thrown: its attack damage (the player's 1 and its modifiers), enchantments, MovesConfig.throwDamage. */
    /**
     * The share of the full speed for an aim held so long (author, 2026-11-09: "the longer held, the further, not to send it
     * into space by chance"): a bow's draw over MovesConfig.throwFullTicks, a fifth at the least.
     */
    public static float power(int held) {
        float f = Math.min(1f, held / (float) MovesConfig.throwFullTicks());
        return Math.max(0.2f, (f * f + 2f * f) / 3f);
    }

    static float damage(ItemStack item, LivingEntity target) {
        double d = 1.0;
        for (AttributeModifier m : item.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) d += m.getAmount();
        }
        return (float) (d * MovesConfig.throwDamage()) + EnchantmentHelper.getDamageBonus(item, target.getMobType());
    }

    /** It reaches a mob: hurt with the weapon's damage, then stuck in it, or, dead, the weapon falls to the ground. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void impact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrownTrident t) || t.level().isClientSide) return;
        State st = LIVE.get(t);
        if (st == null || !(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof LivingEntity mob) || mob.getId() == st.owner || st.stuckIn >= 0) return;
        event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        Entity owner = t.getOwner();
        mob.invulnerableTime = 0;
        boolean hurt = mob.hurt(t.damageSources().trident(t, owner == null ? t : owner), damage(st.item, mob));
        if (hurt && owner instanceof LivingEntity lo) {
            EnchantmentHelper.doPostHurtEffects(mob, lo);
            EnchantmentHelper.doPostDamageEffects(lo, mob);
            if (owner instanceof ServerPlayer sp) Stamina.of(sp).combatTicks = MovesConfig.combatTicks();
        }
        if (st.item.isDamageableItem()) st.item.setDamageValue(Math.min(st.item.getMaxDamage() - 1, st.item.getDamageValue() + 1));
        t.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1f, 1f);
        // where it went in (the hit's own location is the mob's feet): its way through the mob's box this tick
        Vec3 from = t.position(), to = from.add(t.getDeltaMovement());
        Vec3 at = mob.getBoundingBox().inflate(0.1).clip(from, to)
                .orElse(new Vec3(mob.getX(), Math.min(Math.max(from.y, mob.getY() + 0.2), mob.getY() + mob.getBbHeight() * 0.9), mob.getZ()));
        if (hurt && mob.isAlive()) stick(t, st, mob, at);
        else drop(t, st, at);
    }

    private static void stick(ThrownTrident t, State st, LivingEntity mob, Vec3 at) {
        // its middle where it went in: half of it in the body, half out to be seen (author, 2026-11-09)
        Vec3 in = at;
        st.stuckIn = mob.getId();
        st.offset = in.subtract(mob.position());
        st.yaw = t.getYRot();
        st.pitch = t.getXRot();
        t.setNoPhysics(true);
        t.setDeltaMovement(Vec3.ZERO);
        t.setPos(in);
        t.shakeTime = 7;
        MovesNet.thrown(t, st, null);
    }

    /** Off the mob (it died, or the hit killed it): straight down, point first, into the ground. */
    private static void drop(ThrownTrident t, State st, Vec3 at) {
        st.stuckIn = -1;
        t.setNoPhysics(false);
        t.setPos(at);
        t.setDeltaMovement(0, -0.45, 0);
        t.setXRot(-90f);
        t.xRotO = -90f;
        MovesNet.thrown(t, st, null);
    }

    // ------------------------------------------------------------------ every tick

    /** Stuck weapons go about with their mob; picked up, the weapon goes into the owner's hand if it is empty. */
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || LIVE.isEmpty()) return;
        LIVE.entrySet().removeIf(en -> {
            ThrownTrident t = en.getKey();
            State st = en.getValue();
            if (t.isRemoved()) {
                if (t.getOwner() instanceof ServerPlayer p) toHand(p, st.item);
                MovesNet.thrownGone(t);
                return true;
            }
            if (st.stuckIn < 0) return false;
            Entity mob = t.level().getEntity(st.stuckIn);
            if (mob == null || !mob.isAlive()) {
                drop(t, st, t.position());
                return false;
            }
            t.setPos(mob.position().add(st.offset));
            t.setDeltaMovement(Vec3.ZERO);
            t.setNoPhysics(true);
            t.shakeTime = 7;     // not picked up by walking into the mob: pulled out (Move.WEAPON_PULL)
            return false;
        });
    }

    /** The weapon picked up (as a trident is, walking over it) goes into the main hand, if that is empty. */
    private static void toHand(ServerPlayer p, ItemStack item) {
        if (!p.getMainHandItem().isEmpty()) return;
        var inv = p.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack it = inv.items.get(i);
            if (i != inv.selected && ItemStack.isSameItemSameTags(it, item)) {
                inv.items.set(inv.selected, it);
                inv.items.set(i, ItemStack.EMPTY);
                return;
            }
        }
    }

    /** A trident carrying a weapon after a reload: thrown again as far as this end goes (lying where it lay). */
    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ThrownTrident t) || LIVE.containsKey(t)) return;
        if (!t.getPersistentData().contains(TAG)) return;
        ItemStack item = ItemStack.of(t.getPersistentData().getCompound(TAG));
        if (item.isEmpty()) return;
        LIVE.put(t, new State(item, t.getOwner() == null ? -1 : t.getOwner().getId()));
    }

    /** A player coming near a thrown weapon is told what it is (their client draws it). */
    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ThrownTrident t && LIVE.get(t) != null && event.getEntity() instanceof ServerPlayer p) {
            MovesNet.thrownTo(p, t, LIVE.get(t));
        }
    }

    // ------------------------------------------------------------------ the pull

    /** The weapon a pull is made at: the player's own, stuck in a mob within reach. */
    public static boolean valid(Player p, Entity e) {
        State st = state(e);
        if (st == null || st.owner != p.getId() || st.stuckIn < 0) return false;
        Entity mob = p.level().getEntity(st.stuckIn);
        if (!(mob instanceof LivingEntity) || !mob.isAlive()) return false;
        Vec3 to = Strikes.flat(mob.position().subtract(p.position()));
        return to.length() - mob.getBbWidth() / 2 <= PULL_REACH + 0.5 && Math.abs(mob.getY() - p.getY()) < 2.0;
    }

    /** Every server tick of a throw and a pull: the weapon leaves the hand; it comes out of the mob. */
    static void serverTick(ServerPlayer p, Stamina s) {
        if (s.move == Move.THROW && s.moveTick == THROW_RELEASE) release(p);
        if (s.move == Move.WEAPON_PULL && s.moveTick == PULL_TICK) pull(p, s);
    }

    /** Out of the mob: the weapon back in the hand (or the inventory), the mob hurt a little and stunned a moment. */
    static void pull(ServerPlayer p, Stamina s) {
        if (!(p.level().getEntity(s.target) instanceof ThrownTrident t) || !LIVE.containsKey(t)) return;
        State st = LIVE.remove(t);
        Entity mob = p.level().getEntity(st.stuckIn);
        t.discard();
        if (p.getMainHandItem().isEmpty()) p.setItemInHand(InteractionHand.MAIN_HAND, st.item);
        else if (!p.getInventory().add(st.item)) p.drop(st.item, false);
        MovesNet.thrownGone(t);
        if (!(mob instanceof LivingEntity le) || !le.isAlive()) return;
        le.invulnerableTime = 0;
        // no knockback: it stays at arm's length (the finisher may follow at once)
        Vec3 was = le.getDeltaMovement();
        le.hurt(p.damageSources().playerAttack(p), MovesConfig.pullDamage());
        le.setDeltaMovement(was.x * 0.2, Math.min(was.y, 0), was.z * 0.2);
        le.hurtMarked = true;
        le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, MovesConfig.pullStunTicks(), 3, false, false));
        MovesNet.stoop(le, 1, MovesConfig.pullStunTicks(), 20);
        ServerLevel level = p.serverLevel();
        Vec3 at = le.position().add(0, le.getBbHeight() * 0.6, 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 1f, 0.7f);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.1);
        s.combatTicks = MovesConfig.combatTicks();
    }

    private Thrown() {}
}
