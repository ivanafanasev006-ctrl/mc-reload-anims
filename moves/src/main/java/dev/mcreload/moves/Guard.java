package dev.mcreload.moves;

import dev.mcreload.moves.net.MovesNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The sword block (docs/combat-spec.md, 4): right mouse button with a sword, as in 1.7.10.
 *
 * <ul>
 *   <li>One sword ({@code cross_guard}): blows from the front lose 75 %, the sword 1 durability,
 *   the player 1 shield.</li>
 *   <li>Two swords ({@code cross_guard_double}): 90 %, each sword 1 durability, 1.5 shields.</li>
 *   <li>Perfect parry: a blow in the first moments after the blade is up does not land; nothing
 *   is spent.</li>
 *   <li>No block with a shield in the other hand (that blocks the vanilla way) or without shields
 *   left.</li>
 * </ul>
 *
 * <p>Times follow the animations' keys (docs/moves.md): the blade is across at 0.2 s (0.25 s with
 * two), the parry window is the 0.2 s after that.</p>
 */
public final class Guard {
    /** Swords that are not {@link SwordItem}s (other mods' blades) can join through this tag. */
    public static final TagKey<Item> SWORDS = ItemTags.create(new ResourceLocation(KinetrixMoves.MODID, "swords"));

    public enum Kind {
        NONE("", 0, 0, 0, 0f, 0f),
        /** One sword: blade up at 0.2 s, parry 0.2-0.4 s. */
        SINGLE("cross_guard", 4, 4, 8, 0.25f, 1f),
        /** Two swords crossed: blades up at 0.25 s, parry 0.25-0.45 s. */
        DOUBLE("cross_guard_double", 5, 5, 9, 0.10f, 1.5f);

        public final ResourceLocation animation;
        /** Guard tick from which blows are blocked; the perfect parry window (inclusive). */
        public final int activeFrom, parryFrom, parryTo;
        /** What is left of a blocked blow, and the shields it costs. */
        public final float damageLeft, shieldCost;

        Kind(String anim, int activeFrom, int parryFrom, int parryTo, float damageLeft, float shieldCost) {
            this.animation = anim.isEmpty() ? null : new ResourceLocation(KinetrixMoves.MODID, anim);
            this.activeFrom = activeFrom;
            this.parryFrom = parryFrom;
            this.parryTo = parryTo;
            this.damageLeft = damageLeft;
            this.shieldCost = shieldCost;
        }

        public static Kind byId(int id) {
            Kind[] all = values();
            return id >= 0 && id < all.length ? all[id] : NONE;
        }
    }

    public static boolean isSword(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof SwordItem || stack.is(SWORDS));
    }

    /** The guard this player could raise now: by what is in their hands. */
    public static Kind kindFor(Player p) {
        ItemStack main = p.getMainHandItem(), off = p.getOffhandItem();
        if (!isSword(main)) return Kind.NONE;
        if (!off.isEmpty() && off.canPerformAction(ToolActions.SHIELD_BLOCK)) return Kind.NONE;
        return isSword(off) ? Kind.DOUBLE : Kind.SINGLE;
    }

    /** Can this player raise (or keep) a guard: a sword in hand, shields left, free to act. */
    public static boolean allowed(Player p, Stamina s) {
        return kindFor(p) != Kind.NONE && s.shields > 0f && p.isAlive() && !p.isSpectator() && !p.isPassenger()
                && !p.isSleeping() && !p.isFallFlying() && s.move == null;
    }

    // ------------------------------------------------------------------ server

    /** The player's client raises or lowers the guard. */
    public static void request(ServerPlayer p, boolean up) {
        Stamina s = Stamina.of(p);
        if (up && allowed(p, s)) {
            if (s.guard == Kind.NONE) raise(p, s, kindFor(p));
        } else {
            lower(p, s);
        }
    }

    static void raise(Player p, Stamina s, Kind kind) {
        // Raising again right after lowering gives no new parry window (tapping the button).
        s.guardParry = p.tickCount - s.guardEndTick >= MovesConfig.parryRearmTicks();
        s.guard = kind;
        s.guardTick = 0;
        if (p instanceof ServerPlayer sp) MovesNet.guard(sp, kind);
    }

    static void lower(Player p, Stamina s) {
        if (s.guard == Kind.NONE) return;
        s.guard = Kind.NONE;
        s.guardTick = 0;
        s.guardEndTick = p.tickCount;
        if (p instanceof ServerPlayer sp) MovesNet.guard(sp, Kind.NONE);
    }

    /** Every server tick: the guard falls when the hands, the shields or the player no longer allow it. */
    static void serverTick(ServerPlayer p, Stamina s) {
        if (s.guard == Kind.NONE) return;
        Kind now = kindFor(p);
        if (!allowed(p, s) || now != s.guard) lower(p, s);
    }

    /** The blow comes from in front of the player (the half they face), like a vanilla shield. */
    static boolean fromFront(Player p, DamageSource src) {
        Vec3 from = src.getSourcePosition();
        if (from == null) return false;
        Vec3 view = p.getViewVector(1f);
        Vec3 to = from.subtract(p.position());
        return to.x * view.x + to.z * view.z > 0;
    }

    static boolean blockable(Player p, Stamina s, DamageSource src) {
        return s.guard != Kind.NONE && s.guardTick >= s.guard.activeFrom && s.shields > 0f
                && (src.getEntity() != null || src.getDirectEntity() != null)
                && !src.is(DamageTypeTags.BYPASSES_SHIELD) && !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && fromFront(p, src);
    }

    public static boolean inParryWindow(Stamina s) {
        return s.guardParry && s.guardTick >= s.guard.parryFrom && s.guardTick <= s.guard.parryTo;
    }

    /** Perfect parry: the blow does not land at all (no damage, no knockback, nothing spent). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.isCanceled()) return;
        Stamina s = Stamina.of(p);
        if (!blockable(p, s, event.getSource()) || !inParryWindow(s)) return;
        // During the player's hurt invulnerability vanilla ignores the blow anyway.
        if (p.invulnerableTime > 10) return;
        event.setCanceled(true);
        s.combatTicks = MovesConfig.combatTicks();
        effects(p, true);
    }

    /** A blocked blow: most of it held by the blade; the swords and the shields pay for it. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void hurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Stamina s = Stamina.of(p);
        if (!blockable(p, s, event.getSource())) return;
        Kind kind = s.guard;
        event.setAmount(event.getAmount() * kind.damageLeft);
        p.getMainHandItem().hurtAndBreak(1, p, e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        if (kind == Kind.DOUBLE) p.getOffhandItem().hurtAndBreak(1, p, e -> e.broadcastBreakEvent(InteractionHand.OFF_HAND));
        s.shields = Math.max(0f, s.shields - kind.shieldCost);
        s.shieldTimer = 0;
        effects(p, false);
        if (s.shields <= 0f) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 0.8f, 1.1f);
            lower(p, s);
        }
        MoveServer.sync(p, s, true);
    }

    /** Swords and shields: right-click raises the guard (the client tells the server it is held). */
    @SubscribeEvent
    public static void rightClick(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !allowed(event.getEntity(), Stamina.of(event.getEntity()))) return;
        // Taken by the guard: the other hand is not used (no block placed from it).
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
    }

    private static void effects(ServerPlayer p, boolean parry) {
        ServerLevel level = p.serverLevel();
        Vec3 at = p.getEyePosition().add(p.getViewVector(1f).scale(0.6)).subtract(0, 0.3, 0);
        if (parry) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 1.9f);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.15, 0.15, 0.15, 0.4);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.2);
        } else {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.15f);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 5, 0.1, 0.1, 0.1, 0.2);
        }
    }

    private Guard() {}
}
