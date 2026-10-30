package dev.mcreload.moves;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * A player's energy and shields, and the move they are doing. Every player has one on both ends:
 * the server's is the real one; the client's own copy shows the bars (the server sends its values)
 * and runs the move it has just started; other players' copies on a client only hold their move,
 * for the animation and the hitbox.
 */
public final class Stamina {
    public static final Capability<Stamina> CAP = CapabilityManager.get(new CapabilityToken<>() {});
    private static final ResourceLocation KEY = new ResourceLocation(KinetrixMoves.MODID, "stamina");

    public float energy = MovesConfig.maxEnergy();
    public float shields = MovesConfig.maxShields();
    /** Game ticks left of free moves (golden apples) and how long that spell was, for the bar. */
    public int freeTicks, freeTotal;
    /** Game ticks left of the golden apple's faster refill. */
    public int boostTicks;
    /** Game ticks left of being in combat (hit or was hit). */
    public int combatTicks;
    /** Ticks towards the next shield refill. */
    public int shieldTimer;

    /** The move being done, or null; {@link #moveTick} counts its ticks from 0. */
    public Move move;
    public int moveTick;
    /** Slide: C still held (it goes 5 blocks and ends in a crawl). */
    public boolean held;
    /** Slide: blocks gone so far (the client moves the player). */
    public float slid;
    /** The sword guard held up (RMB), how long it has been up, and whether it can still parry. */
    public Guard.Kind guard = Guard.Kind.NONE;
    public int guardTick;
    public boolean guardParry;
    /** When the guard was last lowered (player ticks), for the parry re-arm time. */
    public int guardEndTick = Integer.MIN_VALUE / 2;

    /** The vault and the finisher: the entity id of the mob they are made at (-1: none). */
    public int target = -1;
    /** The vault's Speed I was given (at the landing, or when the player broke off the vault's end). */
    public boolean hopSped;
    /** The vault's shove was given (server). */
    public boolean hopShoved;
    /** Where the vault took off (server): the mob is shoved back that way. */
    public net.minecraft.world.phys.Vec3 hopFrom;
    /** Ticks before the next dash strike (both ends: the client does not try it before, the server refuses it). */
    public int dashCooldown;
    /** The dash's blade found its mob (server): it hits once. */
    public boolean dashHit;
    /** Ticks before the next axe whirlwind (both ends, as the dash's). */
    public int whirlCooldown;
    /** The whirlwind: the last turn each mob was hit in (server; entity id to turn). */
    public final java.util.Map<Integer, Integer> whirlHits = new java.util.HashMap<>();
    /** Ticks before the next sword's turn (G with a sword; both ends, as the dash's). */
    public int spinCooldown;
    /** The bow's marks (Marks; both ends): the marked mob's entity id to the game time the mark ends, in the order made. */
    public final java.util.LinkedHashMap<Integer, Long> marks = new java.util.LinkedHashMap<>();
    /** Ticks before the next volley at the marked (both ends, as the dash's). */
    public int markedCooldown;
    /** The volley at the marked (server): the mobs shot at in it so far, and whether the step going on has shot its arrow. */
    public final java.util.Set<Integer> markedShot = new java.util.HashSet<>();
    public boolean stepShot;
    /** Ticks before the next attack out of a slide (both ends, as the dash's). */
    public int slideAttackCooldown;
    /** The facing when the attack out of a slide started (both ends): it ends half a turn round from it. */
    public float attackYaw;
    /** The kick: given (server), where the player took off and which way they faced. */
    public boolean kickDone;
    public net.minecraft.world.phys.Vec3 kickFrom;
    public float kickYaw;
    /** Ticks a tuck has been on the ground without rolling (server). */
    public int tuckLanded;
    /** Ticks the blade held for the plunge has been on the ground without the landing (server). */
    public int plungeLanded;
    /** The fast run (server): the client says its key is held; the player runs fast now. */
    public boolean runKey, fastRunning;
    /** The ledge being held (both ends): which way its wall is from the player (Ledge.dir) and the height of its top. */
    public int ledgeDir = -1;
    public double ledgeTop;

    /** The pose this move forced to keep the low hitbox standing (cleared when it ends). */
    boolean forcedStanding;
    /** This is the server's copy (its low frames last a little longer, see {@link #LOW_GRACE}). */
    private boolean server;

    /**
     * The server keeps the low hitbox this many ticks longer than the client: the client's movement
     * reaches it a tick or two late, and a slide still under a one-block gap must not be pulled back.
     */
    private static final int LOW_GRACE = 3;

    public static Stamina of(Player player) {
        return player.getCapability(CAP).orElseGet(Stamina::new);
    }

    public boolean free() {
        return freeTicks > 0;
    }

    public boolean canAfford(Move m, Player p) {
        return free() || p.getAbilities().instabuild || energy >= m.cost(move);
    }

    /** Some energy is left (the moves paid by the second, creeping along a ledge, go on while it lasts). */
    public boolean hasEnergy(Player p) {
        return free() || p.getAbilities().instabuild || energy > 0.01f;
    }

    public void spend(float amount, Player p) {
        if (free() || p.getAbilities().instabuild) return;
        energy = Math.max(0f, energy - amount);
    }

    /** Low hitbox now (a roll or a slide in its low frames). */
    public boolean low() {
        return move != null && move.lowFrom >= 0 && moveTick >= move.lowFrom && moveTick <= move.lowTo + (server ? LOW_GRACE : 0);
    }

    public boolean untouchable() {
        return move != null && move.untouchable(moveTick);
    }

    // ------------------------------------------------------------------ the move, both ends

    /** Starts a move; the caller has checked and paid. */
    public void start(Player p, Move m, boolean held) {
        end(p);
        move = m;
        moveTick = 0;
        this.held = held;
        slid = 0f;
        server = !p.level().isClientSide;
        // On the ledge the player stays upright whatever touches their hitbox (the climb goes through
        // the edge's corner; vanilla would make them crouch or crawl there, the eyes in the block).
        if (m.ledge() && p.getForcedPose() == null) {
            p.setForcedPose(net.minecraft.world.entity.Pose.STANDING);
            forcedStanding = true;
            p.refreshDimensions();
        }
    }

    /** Ends the move (finished or cancelled) and gives the player their normal hitbox back. */
    public void end(Player p) {
        boolean wasLow = low();
        move = null;
        moveTick = 0;
        held = false;
        if (wasLow || forcedStanding) stand(p);
    }

    /** Counts the move on; at the edges of its low frames the hitbox changes. Every tick, both ends. */
    public void tickMove(Player p) {
        if (guard != Guard.Kind.NONE) guardTick++;
        if (dashCooldown > 0) dashCooldown--;
        if (slideAttackCooldown > 0) slideAttackCooldown--;
        if (whirlCooldown > 0) whirlCooldown--;
        if (spinCooldown > 0) spinCooldown--;
        if (markedCooldown > 0) markedCooldown--;
        if (!marks.isEmpty() && p.tickCount % 10 == 0) Marks.prune(p, this);
        if (move == null) return;
        boolean wasLow = low();
        moveTick++;
        if (moveTick >= move.length) {
            end(p);
            return;
        }
        boolean nowLow = low();
        if (nowLow && !wasLow) {
            // Keep the standing pose (vanilla would switch to the crawl under a low ceiling and lay the
            // model flat under the animation); the 0.6-block hitbox comes from onSize.
            if (p.getForcedPose() == null) {
                p.setForcedPose(net.minecraft.world.entity.Pose.STANDING);
                forcedStanding = true;
            }
            p.refreshDimensions();
        } else if (!nowLow && wasLow) {
            stand(p);
        }
    }

    /**
     * Back to a normal hitbox: standing if there is room, else crouching, else the vanilla crawl
     * (a slide that ends under a one-block gap leaves the player crawling, never stuck in a block).
     */
    private void stand(Player p) {
        if (forcedStanding && p.getForcedPose() == net.minecraft.world.entity.Pose.STANDING) p.setForcedPose(null);
        forcedStanding = false;
        net.minecraft.world.entity.Pose pose = net.minecraft.world.entity.Pose.STANDING;
        if (!fits(p, pose)) pose = fits(p, net.minecraft.world.entity.Pose.CROUCHING)
                ? net.minecraft.world.entity.Pose.CROUCHING : net.minecraft.world.entity.Pose.SWIMMING;
        if (p.getForcedPose() == null) p.setPose(pose);
        p.refreshDimensions();
    }

    private static boolean fits(Player p, net.minecraft.world.entity.Pose pose) {
        return p.level().noCollision(p, p.getDimensions(pose).makeBoundingBox(p.position()).deflate(1.0E-7));
    }

    // ------------------------------------------------------------------ energy and shields, server

    /** Every server tick: refill energy from food, refill shields from energy, count timers down. */
    public void serverTick(ServerPlayer p) {
        if (freeTicks > 0) freeTicks--;
        if (boostTicks > 0) boostTicks--;
        if (combatTicks > 0) combatTicks--;
        float max = MovesConfig.maxEnergy();
        if (p.isCreative() || p.isSpectator()) {
            energy = max;
            shields = MovesConfig.maxShields();
            return;
        }
        if (energy > max) energy = max;
        if (energy < max) {
            float gain = refillPerTick(p);
            if (gain > 0f) {
                gain = Math.min(gain, max - energy);
                energy += gain;
                p.causeFoodExhaustion(gain * MovesConfig.exhaustionPerEnergy());
            }
        }
        float maxShields = MovesConfig.maxShields();
        if (shields > maxShields) shields = maxShields;
        if (shields < maxShields) {
            if (++shieldTimer >= MovesConfig.shieldRegenTicks()) {
                float cost = MovesConfig.shieldRegenCost();
                if (free() || energy >= cost) {
                    spend(cost, p);
                    shields = Math.min(maxShields, shields + MovesConfig.shieldRegen());
                    shieldTimer = 0;
                } else {
                    shieldTimer = MovesConfig.shieldRegenTicks();    // try again as soon as there is energy
                }
            }
        } else {
            shieldTimer = 0;
        }
    }

    /** Energy refilled this tick, by the author's rules (docs/combat-spec.md, 7). */
    public float refillPerTick(Player p) {
        FoodData food = p.getFoodData();
        if (food.getFoodLevel() <= 0 || fastRunning) return 0f;
        float rate = MovesConfig.regenPerSecond() / 20f;
        boolean busy = combatTicks > 0 || p.isSprinting() || move != null;
        if (busy) rate *= MovesConfig.busyFactor();
        else if (p.isShiftKeyDown() || still(p)) rate *= MovesConfig.stillFactor();
        if (food.getFoodLevel() >= 20 && food.getSaturationLevel() > 0f) rate *= MovesConfig.wellFedFactor();
        if (boostTicks > 0) rate *= MovesConfig.appleBoostFactor();
        return rate;
    }

    private static boolean still(Player p) {
        double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
        return p.onGround() && dx * dx + dz * dz < 1.0E-4;
    }

    /** Cooked or prepared food, which gives back a little energy (MovesConfig.cookedFoodEnergy). */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> COOKED_FOOD =
            net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation(KinetrixMoves.MODID, "cooked_food"));
    /** Other mods' cooked food, by name (Farmer's Delight's roast chicken, a fried egg, a beef stew...). */
    private static final java.util.regex.Pattern COOKED_NAME =
            java.util.regex.Pattern.compile("(^|_)(cooked|baked|roast|roasted|fried|grilled|smoked|stew|soup|pie|bread)(_|$)");

    public static boolean cooked(ItemStack item) {
        if (!item.isEdible()) return false;
        if (item.is(COOKED_FOOD)) return true;
        net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem());
        return id != null && COOKED_NAME.matcher(id.getPath()).find();
    }

    public void ateGoldenApple(boolean enchanted) {
        if (enchanted) {
            energy = MovesConfig.maxEnergy();
            setFree(MovesConfig.enchantedFreeTicks());
        } else {
            setFree(MovesConfig.appleFreeTicks());
            boostTicks = Math.max(boostTicks, MovesConfig.appleBoostTicks());
        }
    }

    private void setFree(int ticks) {
        if (ticks > freeTicks) {
            freeTicks = ticks;
            freeTotal = ticks;
        }
    }

    // ------------------------------------------------------------------ saving

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putFloat("energy", energy);
        t.putFloat("shields", shields);
        t.putInt("free", freeTicks);
        t.putInt("freeTotal", freeTotal);
        t.putInt("boost", boostTicks);
        return t;
    }

    public void load(CompoundTag t) {
        if (t.contains("energy")) energy = t.getFloat("energy");
        if (t.contains("shields")) shields = t.getFloat("shields");
        freeTicks = t.getInt("free");
        freeTotal = t.getInt("freeTotal");
        boostTicks = t.getInt("boost");
    }

    static void registerCapability(RegisterCapabilitiesEvent event) {
        event.register(Stamina.class);
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final Stamina stamina = new Stamina();
        private final LazyOptional<Stamina> holder = LazyOptional.of(() -> stamina);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
            return CAP.orEmpty(cap, holder);
        }

        @Override
        public CompoundTag serializeNBT() {
            return stamina.save();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            stamina.load(nbt);
        }
    }

    // ------------------------------------------------------------------ events

    public static final class Events {
        @SubscribeEvent
        public static void attach(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) event.addCapability(KEY, new Provider());
        }

        /** Back from the End keeps the bars; after death they start full. */
        @SubscribeEvent
        public static void clone(PlayerEvent.Clone event) {
            if (event.isWasDeath()) return;
            Player old = event.getOriginal();
            old.reviveCaps();
            CompoundTag saved = of(old).save();
            old.invalidateCaps();
            of(event.getEntity()).load(saved);
        }

        @SubscribeEvent
        public static void playerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Player p = event.player;
            Stamina s = of(p);
            s.tickMove(p);
            if (p instanceof ServerPlayer sp) {
                s.serverTick(sp);
                Guard.serverTick(sp, s);
                Hop.serverTick(sp, s);
                Kick.serverTick(sp, s);
                Fall.serverTick(sp, s);
                Ledge.serverTick(sp, s);
                FastRun.serverTick(sp, s);
                Plunge.serverTick(sp, s);
                Whirl.serverTick(sp, s);
                Spin.serverTick(sp, s);
                Strikes.serverTick(sp, s);
                Deflect.serverTick(sp, s);
                Bow.serverTick(sp, s);
                MoveServer.afterTick(sp, s);
            }
        }

        @SubscribeEvent
        public static void ate(LivingEntityUseItemEvent.Finish event) {
            if (!(event.getEntity() instanceof ServerPlayer p)) return;
            ItemStack item = event.getItem();
            Stamina s = of(p);
            if (item.is(Items.GOLDEN_APPLE)) s.ateGoldenApple(false);
            else if (item.is(Items.ENCHANTED_GOLDEN_APPLE)) s.ateGoldenApple(true);
            else if (cooked(item)) s.energy = Math.min(MovesConfig.maxEnergy(), s.energy + MovesConfig.cookedFoodEnergy());
            else return;
            MoveServer.sync(p, s, true);
        }

        /** Hitting someone puts the player in combat (slower refill). */
        @SubscribeEvent
        public static void attacked(AttackEntityEvent event) {
            if (!event.getEntity().level().isClientSide) of(event.getEntity()).combatTicks = MovesConfig.combatTicks();
        }

        /**
         * A dodge's untouchable frames: hits from anyone (blows, arrows, spells) miss. Falling, fire,
         * drowning and the like still hurt, and so does anything that ignores invulnerability.
         * Otherwise being hit by someone puts the player in combat.
         */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void attack(LivingAttackEvent event) {
            if (!(event.getEntity() instanceof Player p) || p.level().isClientSide) return;
            DamageSource src = event.getSource();
            if (src.getEntity() == null && src.getDirectEntity() == null) return;
            Stamina s = of(p);
            if (s.untouchable() && !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                event.setCanceled(true);
                return;
            }
            s.combatTicks = MovesConfig.combatTicks();
        }

        /**
         * Arrows and other projectiles fly through a player in their untouchable frames; at the start
         * of a side dodge, with a sword or a bow in hand, the player knocks them aside instead.
         */
        @SubscribeEvent
        public static void projectile(ProjectileImpactEvent event) {
            if (!(event.getRayTraceResult() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof Player p)) return;
            Stamina s = of(p);
            if (p instanceof ServerPlayer sp) Deflect.tryDeflect(sp, s, event.getProjectile());
            if (s.untouchable() || event.getProjectile().getTags().contains(Deflect.TAG)) {
                event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
            }
        }

        /** The low hitbox of rolls and the slide (0.6 block, like crawling), on both ends. */
        @SubscribeEvent
        @SuppressWarnings("removal")
        public static void size(EntityEvent.Size event) {
            if (!(event.getEntity() instanceof Player p) || p.level() == null) return;
            if (!of(p).low()) return;
            EntityDimensions d = event.getNewSize();
            event.setNewSize(EntityDimensions.scalable(d.width, Math.min(d.height, 0.6f)));
            event.setNewEyeHeight(Math.min(event.getNewEyeHeight(), 0.4f));
        }
    }
}
