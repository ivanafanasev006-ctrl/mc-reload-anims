package dev.mcreload.moves;

import dev.mcreload.moves.net.MovesNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * The crossbow's attacks on the left button (author, 2026-11-08: "attacks on LMB, different ones: the first
 * a push forward with one hand, then a turn clockwise and a blow with the crossbow like a slap with a
 * run-up, after that a turn the other way and a blow with a swing onto the spine, the arm going down
 * forward and down, and the last LMB a hop back into a crouch and a shot if the crossbow is loaded; if it
 * is not, just a dash back, with its own animation too"). Each click is the next blow of the chain while
 * it lasts (MovesConfig.crossbowChainTicks after a blow), then it starts over from the push:
 *
 * <ol>
 *   <li><b>The push</b> ({@code crossbow_thrust}): the crossbow's front into the chest of the nearest mob in
 *   front at 0.2 s, shoving it back.</li>
 *   <li><b>The slap</b> ({@code crossbow_slap}): a whole turn clockwise, the crossbow's flat through the face
 *   of every mob in the arc in front at 0.35 s, knocking it to the player's right; it doubles over
 *   (StoopFx) for the next blow.</li>
 *   <li><b>Onto the spine</b> ({@code crossbow_spine}): a whole turn the other way, the crossbow overhead,
 *   down onto the back of the mob in front at 0.55 s, driving it lower.</li>
 *   <li><b>The hop back and the shot</b> ({@code crossbow_leap_shot}, loaded): about 2 blocks back into a crouch,
 *   the crossbow's own shot at 0.55 s, as vanilla's (its arrows or rocket, multishot and piercing too);
 *   or, not loaded, <b>the dash back</b> ({@code crossbow_dash_back}), about 2.7 blocks low along the floor.
 *   Both untouchable while they carry the player, as a side dodge.</li>
 * </ol>
 *
 * <p>A click before a blow has followed through is kept for it: the next one starts from the blow's chain tick
 * (StrikeInput). The ticks are the animations' keys (tools/blockbench/refine_author_2026_10_29/round9.py).</p>
 */
public final class Crossbow {
    /** The blows land (the crossbow on the mob) and the shot leaves, ticks from each one's start. */
    public static final int THRUST_HIT = 4, SLAP_HIT = 7, SPINE_HIT = 11, LEAP_SHOT = 11;
    /** From these ticks the next click goes on into the next blow (each followed through); a click before is kept for then. */
    public static final int THRUST_CHAIN = 6, SLAP_CHAIN = 10, SPINE_CHAIN = 14;
    /** The hop and the dash: moving breaks off the rest from here (the shot out, up out of the crouch). */
    public static final int LEAP_UP = 15, DASH_UP = 10;
    /** How far either side of the look each blow finds its mobs (degrees): the push straight, the slap's sweep wide. [default] */
    static final double THRUST_HALF = 40, SLAP_HALF = 80, SPINE_HALF = 50;
    /**
     * The blows shove the mob their own way instead of vanilla's knockback (a hop away, two blocks and more: the
     * next blow would not reach it): the slap to the player's right (as the crossbow sweeps) and a little away,
     * the blow onto the spine down only. Velocities, blocks a tick; about 2.2 times that is the distance. [default]
     */
    static final double SLAP_PUSH = 0.25, SLAP_AWAY = 0.1;
    /**
     * Each blow steps the player in to the mob it is aimed at (the nearest in front within
     * MovesConfig.crossbowStepReach, STEP_HALF degrees of the look), up to these blocks from the player's middle
     * to the mob's side by the blow (where the animation's crossbow meets it), at most STEP_TOP blocks a tick:
     * the chain keeps on the mob the push shoved back. [default]
     */
    static final double THRUST_AT = 1.25, SLAP_AT = 1.25, SPINE_AT = 1.5, STEP_TOP = 0.3, STEP_HALF = 60;
    /** The slap doubles the mob over until the blow onto its spine can come; that blow drives it lower. [default] */
    static final int SLAP_STOOP_TICKS = 20, SPINE_STOOP_TICKS = 10;
    static final int SLAP_STOOP = 18, SPINE_STOOP = 36;

    public static boolean held(Player p) {
        return p.getMainHandItem().getItem() instanceof CrossbowItem;
    }

    public static boolean loaded(Player p) {
        return CrossbowItem.isCharged(p.getMainHandItem());
    }

    /** The place of a move in the chain (1-4), 0 for any other. */
    public static int step(Move m) {
        if (m == null) return 0;
        return switch (m) {
            case XBOW_THRUST -> 1;
            case XBOW_SLAP -> 2;
            case XBOW_SPINE -> 3;
            case XBOW_SHOT, XBOW_DASH -> 4;
            default -> 0;
        };
    }

    /** The blow a click makes now: the next of the chain, or the push. The last one shoots if the crossbow is loaded. */
    public static Move next(Player p, Stamina s) {
        return switch (s.chainTicks > 0 ? s.chainStep : 0) {
            case 1 -> Move.XBOW_SLAP;
            case 2 -> Move.XBOW_SPINE;
            case 3 -> loaded(p) ? Move.XBOW_SHOT : Move.XBOW_DASH;
            default -> Move.XBOW_THRUST;
        };
    }

    /** The tick of a blow from which the next one may follow (-1: none follows it). */
    public static int chainFrom(Move m) {
        return switch (m) {
            case XBOW_THRUST -> THRUST_CHAIN;
            case XBOW_SLAP -> SLAP_CHAIN;
            case XBOW_SPINE -> SPINE_CHAIN;
            default -> -1;
        };
    }

    /**
     * A blow of the chain started (both ends): the next click within its length and the wait after it is the
     * next blow; after the last the chain starts over. The server waits a little longer (the click comes late).
     */
    public static void started(Player p, Stamina s, Move m) {
        int k = step(m);
        if (k == 0) return;
        s.chainStep = k == 4 ? 0 : k;
        s.chainTicks = k == 4 ? 0 : m.length + MovesConfig.crossbowChainTicks() + (p.level().isClientSide ? 0 : CHAIN_SLACK);
    }

    /** Ticks the server keeps the chain longer than the client (the next click reaches it that late). */
    private static final int CHAIN_SLACK = 10;

    /**
     * The blow may be made now as far as the chain goes: the push any time (once the move before has played
     * out, as every move), each other one right after the one before it, from that one's chain tick (the
     * server a little earlier: the client runs ahead).
     */
    public static boolean follows(Move next, Stamina s, boolean client) {
        int k = step(next);
        if (k <= 1) return k == 1;
        if (s.chainStep != k - 1 || s.chainTicks <= 0) return false;
        if (s.move == null || step(s.move) == 0) return true;
        return step(s.move) == k - 1 && s.moveTick >= chainFrom(s.move) - (client ? 0 : 2);
    }

    /** The mob a blow steps in to (both ends; the client moves the player): the nearest in front within reach, or null. */
    public static LivingEntity stepTarget(Player p) {
        Vec3 look = look(p);
        double reach = MovesConfig.crossbowStepReach();
        if (reach <= 0) return null;
        return p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach + 1, 1.0, reach + 1),
                        e -> e != p && e.isAlive() && e.isPickable() && !p.isAlliedTo(e) && !(e instanceof ArmorStand)
                                && Math.abs(e.getY() - p.getY()) < 2.0 && within(p, e, look, reach, STEP_HALF))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElse(null);
    }

    /**
     * The player's speed this tick of a blow (blocks a tick, flat): towards its mob until the blow lands, so the
     * gap is the blow's own by then; zero when it is already that close or there is none.
     */
    public static Vec3 stepIn(Player p, LivingEntity target, Move m, int tick) {
        int hit = switch (m) {
            case XBOW_THRUST -> THRUST_HIT;
            case XBOW_SLAP -> SLAP_HIT;
            case XBOW_SPINE -> SPINE_HIT;
            default -> 0;
        };
        if (target == null || !target.isAlive() || tick >= hit) return Vec3.ZERO;
        double at = m == Move.XBOW_SPINE ? SPINE_AT : m == Move.XBOW_SLAP ? SLAP_AT : THRUST_AT;
        Vec3 to = Strikes.flat(target.position().subtract(p.position()));
        double gap = to.length() - target.getBbWidth() / 2 - at;
        if (gap <= 0.02 || to.lengthSqr() < 1.0E-4) return Vec3.ZERO;
        return to.normalize().scale(Math.min(STEP_TOP, gap / (hit - tick)));
    }

    // ------------------------------------------------------------------ server

    /** Every server tick: the blows land on their ticks, the hop's shot leaves. */
    static void serverTick(ServerPlayer p, Stamina s) {
        if (s.move == null || step(s.move) == 0) return;
        int t = s.moveTick;
        switch (s.move) {
            case XBOW_THRUST -> {
                if (t == THRUST_HIT) thrust(p);
            }
            case XBOW_SLAP -> {
                if (t == SLAP_HIT) slap(p);
            }
            case XBOW_SPINE -> {
                if (t == SPINE_HIT) spine(p);
            }
            case XBOW_SHOT -> {
                if (t == LEAP_SHOT) shoot(p);
            }
            default -> {
            }
        }
    }

    /** The push: the crossbow's front into the nearest mob in front, shoving it back along the look. */
    static void thrust(ServerPlayer p) {
        sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.9f, 1.1f);
        List<LivingEntity> mobs = inFront(p, MovesConfig.crossbowReach(), THRUST_HALF);
        if (mobs.isEmpty()) return;
        LivingEntity e = mobs.get(0);
        if (hit(p, e, MovesConfig.crossbowThrustDamage())) shove(e, look(p).scale(MovesConfig.crossbowThrustPush()), 0.1);
    }

    /** The slap: the crossbow's flat sweeps through every mob in the arc in front, knocking each to the player's right. */
    static void slap(ServerPlayer p) {
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.2f);
        ServerLevel level = p.serverLevel();
        Vec3 look = look(p);
        Vec3 right = new Vec3(-look.z, 0, look.x);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + look.x * 1.2, p.getY() + 1.4, p.getZ() + look.z * 1.2, 1, 0, 0, 0, 0);
        for (LivingEntity e : inFront(p, MovesConfig.crossbowReach(), SLAP_HALF)) {
            if (!hit(p, e, MovesConfig.crossbowSlapDamage())) continue;
            shove(e, right.scale(SLAP_PUSH).add(Strikes.flat(e.position().subtract(p.position())).normalize().scale(SLAP_AWAY)), 0.05);
            MovesNet.stoop(e, 1, SLAP_STOOP_TICKS, SLAP_STOOP);
        }
    }

    /** Onto the spine: the crossbow comes down on the back of the mob in front, driving it lower. */
    static void spine(ServerPlayer p) {
        List<LivingEntity> mobs = inFront(p, MovesConfig.crossbowReach(), SPINE_HALF);
        if (mobs.isEmpty()) {
            sound(p, SoundEvents.PLAYER_ATTACK_WEAK, 0.8f, 0.8f);
            return;
        }
        LivingEntity e = mobs.get(0);
        if (!hit(p, e, MovesConfig.crossbowSpineDamage())) return;
        shove(e, Vec3.ZERO, -0.2);
        sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.8f);
        p.crit(e);
        Vec3 at = e.position().add(0, e.getBbHeight() * 0.7, 0);
        p.serverLevel().sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.2, 0.15, 0.2, 0.25);
        MovesNet.stoop(e, 0, SPINE_STOOP_TICKS, SPINE_STOOP);
    }

    /**
     * The hop's shot: the loaded crossbow fires as vanilla fires it (CrossbowItem.use: its arrows or rocket,
     * multishot, piercing, its sound, its wear), the way the player looks now; it is empty after.
     */
    static void shoot(ServerPlayer p) {
        ItemStack crossbow = p.getMainHandItem();
        if (!(crossbow.getItem() instanceof CrossbowItem) || !CrossbowItem.isCharged(crossbow)) return;
        float power = CrossbowItem.containsChargedProjectile(crossbow, Items.FIREWORK_ROCKET) ? 1.6f : 3.15f;
        CrossbowItem.performShooting(p.level(), p, InteractionHand.MAIN_HAND, crossbow, power, 1.0f);
        CrossbowItem.setCharged(crossbow, false);
        Stamina.of(p).combatTicks = MovesConfig.combatTicks();
    }

    /**
     * A blow of the crossbow: its fixed damage (and any damage enchantment it has), fire aspect, the crossbow's
     * wear (1, as a sword's blow); vanilla's knockback undone (each blow shoves its own way, shove). Returns
     * whether it hurt.
     */
    static boolean hit(ServerPlayer p, LivingEntity target, float damage) {
        ItemStack crossbow = p.getMainHandItem();
        float total = damage + EnchantmentHelper.getDamageBonus(crossbow, target.getMobType());
        int fire = EnchantmentHelper.getFireAspect(p);
        if (fire > 0 && !target.isOnFire()) target.setSecondsOnFire(1);
        // the chain's blows come 9 ticks apart and more: within vanilla's moment after a hit only what is over the
        // last blow would count (as with the sword's turns, Spin)
        target.invulnerableTime = 0;
        Vec3 was = target.getDeltaMovement();
        if (!target.hurt(p.damageSources().playerAttack(p), total)) return false;
        target.setDeltaMovement(was);
        if (fire > 0) target.setSecondsOnFire(fire * 4);
        EnchantmentHelper.doPostHurtEffects(target, p);
        EnchantmentHelper.doPostDamageEffects(p, target);
        crossbow.hurtAndBreak(1, p, e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        p.setLastHurtMob(target);
        Stamina.of(p).combatTicks = MovesConfig.combatTicks();
        return true;
    }

    /**
     * Sets a mob moving along {@code push} (blocks a tick, flat) and {@code up}, as much as its knockback
     * resistance lets it (an iron golem stands).
     */
    static void shove(LivingEntity e, Vec3 push, double up) {
        double k = 1.0 - Mth.clamp(e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
        if (k <= 0) return;
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(v.x * 0.5 + push.x * k, e.onGround() || up < 0 ? up * k : v.y, v.z * 0.5 + push.z * k);
        e.hasImpulse = true;
        e.hurtMarked = true;
    }

    /** Mobs within {@code reach} (blocks from the player's middle to their side) and {@code half} degrees of the look, nearest first. */
    static List<LivingEntity> inFront(ServerPlayer p, double reach, double half) {
        Vec3 look = look(p);
        return p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach + 1, 1.0, reach + 1),
                        e -> e != p && e.isAlive() && e.isPickable() && !p.isAlliedTo(e) && !(e instanceof ArmorStand)
                                && Math.abs(e.getY() - p.getY()) < 2.0 && within(p, e, look, reach, half))
                .stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(p))).toList();
    }

    private static boolean within(Player p, LivingEntity e, Vec3 look, double reach, double half) {
        Vec3 to = Strikes.flat(e.position().subtract(p.position()));
        if (to.length() - e.getBbWidth() / 2 > reach) return false;
        return to.lengthSqr() < 0.04 || Strikes.angle(look, to) <= half;
    }

    /** Where the player faces, flat (unit). */
    static Vec3 look(Player p) {
        float r = p.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    private static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private Crossbow() {}
}
