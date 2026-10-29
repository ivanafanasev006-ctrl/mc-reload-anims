package dev.mcreload.moves;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The sword moves of the left button (author, 2026-10-28):
 *
 * <ul>
 *   <li><b>Dash strike</b> ({@code dash_strike}): sprinting with W, the left button. The player runs
 *   on into a lunge and the blade goes into the belly of whoever is in front at 0.45 s, as a crit
 *   (the weapon's blow times 1.5, as in a jump).</li>
 *   <li><b>Finisher</b> ({@code finisher_stab}): a mob with a fifth of its health or less at point
 *   blank, the left button held, a sword or an axe in hand. It is held on the blade from the stab (0.35 s) and dies when the
 *   blade comes out (0.95 s). Not on bosses (the dragon, the wither, the forge:bosses tag, our
 *   kinetrix_moves:no_finisher tag, or anything with more health than a config limit).</li>
 * </ul>
 */
public final class Strikes {
    /** Mobs no finisher works on, besides bosses (data/kinetrix_moves/tags/entity_types/no_finisher.json). */
    public static final TagKey<EntityType<?>> NO_FINISHER =
            TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, new ResourceLocation(KinetrixMoves.MODID, "no_finisher"));
    private static final TagKey<EntityType<?>> FORGE_BOSSES = net.minecraftforge.common.Tags.EntityTypes.BOSSES;

    /** Dash: the lunge's arm is straight (0.45 s). */
    public static final int DASH_HIT = 9;
    /** Dash: the blade finds a mob on its way from the lunge's start to its end (0.25-0.55 s, the hitbox
     *  moving with the player; author, 2026-10-29: "a dynamic hitbox or a bigger one"). */
    public static final int DASH_REACH_FROM = 5, DASH_REACH_TO = 11;
    /** Dash: from here running on breaks off the rest (the blade coming back) without a stop. */
    public static final int DASH_FREE = DASH_REACH_TO + 1;
    /** Dash: speed (blocks a tick) for each tick from the start: the run goes on, speeds up into the lunge
     *  and eases back to a sprint, which it keeps while the animation plays out (it does not stop the player). */
    public static final float[] DASH_RUN = {0.30f, 0.30f, 0.31f, 0.32f, 0.33f, 0.35f, 0.38f, 0.46f, 0.50f, 0.42f, 0.36f, 0.32f};
    /** Dash: the run after the lunge, if the player does not run on themselves (a sprint, slowing down a little a tick). */
    public static final float DASH_AFTER = 0.28f, DASH_AFTER_DRAG = 0.93f;
    /** Dash: the run goes on until it is this close to the mob in front (blocks between the hitboxes), right at its side:
     *  a mob already near does not hold the player in place (author, 2026-10-28). */
    public static final float DASH_STOP = 0.15f;
    /** Dash: how far in front the blade reaches (blocks from the player's centre to the mob's side). */
    public static final double DASH_REACH = 2.6;
    /** The attack out of a slide: the blow lands as the blade, sweeping round with the half turn, is out (0.3 s). */
    public static final int SLIDE_HIT = 6;
    /** The attack out of a slide: the player turns half round clockwise over these ticks. */
    public static final int SLIDE_TURN = 6;
    /** A crit's blow, as in a jump (vanilla). */
    public static final float CRIT = 1.5f;
    /** Finisher: the blade goes in (0.35 s) and comes out, when the mob dies (0.95 s). */
    public static final int FINISH_STAB = 7, FINISH_KILL = 19;

    // ------------------------------------------------------------------ who

    /** A boss: the ender dragon, the wither, anything tagged forge:bosses or kinetrix_moves:no_finisher, or with more health than the config allows. */
    public static boolean boss(LivingEntity e) {
        if (e instanceof EnderDragon || e instanceof WitherBoss) return true;
        if (e.getType().is(FORGE_BOSSES) || e.getType().is(NO_FINISHER)) return true;
        return e.getMaxHealth() > MovesConfig.finisherMaxHealth();
    }

    /** Swords (and other mods' blades in the tag kinetrix_moves:swords) make the sword moves. */
    public static boolean sword(Player p) {
        return Guard.isSword(p.getMainHandItem());
    }

    /** The finisher is made with a sword or an axe (author, 2026-10-28; other mods' axes too: whatever chops like one). */
    public static boolean finisherWeapon(Player p) {
        ItemStack hand = p.getMainHandItem();
        return Guard.isSword(hand) || hand.getItem() instanceof net.minecraft.world.item.AxeItem
                || hand.canPerformAction(net.minecraftforge.common.ToolActions.AXE_DIG);
    }

    /**
     * The mob a finisher would be made on, or null: the one the player looks at (or, without one,
     * the nearest in front), at point blank, a mob (not a player) with a fifth of its health or less,
     * not a boss; a sword or an axe in hand. {@code strict}: the client deciding; the server is looser, for lag.
     */
    public static LivingEntity finisherTarget(Player p, Entity looked, boolean strict) {
        if (!finisherWeapon(p) || p.isUsingItem()) return null;
        if (looked instanceof LivingEntity le && finishable(p, le, strict)) return le;
        double range = MovesConfig.finisherRange() + (strict ? 0 : 1.0);
        Vec3 look = flat(p.getViewVector(1f));
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range + 1.5),
                e -> e != p && finishable(p, e, strict))) {
            Vec3 to = flat(e.position().subtract(p.position()));
            double dist = to.length() - e.getBbWidth() / 2;
            if (dist >= bestDist || (to.lengthSqr() > 1.0E-4 && angle(look, to) > (strict ? 40 : 80))) continue;
            best = e;
            bestDist = dist;
        }
        return best;
    }

    /** Close enough, weak enough, not a boss, not a player. */
    public static boolean finishable(Player p, LivingEntity e, boolean strict) {
        if (!(e instanceof Mob) || !e.isAlive() || e.isInvulnerable() || boss(e) || e instanceof ArmorStand) return false;
        double share = MovesConfig.finisherHealth() + (strict ? 0 : 0.05);
        if (e.getHealth() > e.getMaxHealth() * share) return false;
        Vec3 to = flat(e.position().subtract(p.position()));
        double range = MovesConfig.finisherRange() + (strict ? 0 : 1.0);
        if (to.length() - e.getBbWidth() / 2 > range || Math.abs(e.getY() - p.getY()) > 1.5) return false;
        return to.lengthSqr() < 1.0E-4 || angle(flat(p.getViewVector(1f)), to) <= (strict ? 60 : 90);
    }

    static Vec3 flat(Vec3 v) {
        return new Vec3(v.x, 0, v.z);
    }

    static double angle(Vec3 a, Vec3 b) {
        double la = a.length(), lb = b.length();
        if (la < 1.0E-6 || lb < 1.0E-6) return 0;
        return Math.toDegrees(Math.acos(Mth.clamp(a.dot(b) / (la * lb), -1, 1)));
    }

    // ------------------------------------------------------------------ server

    /** Every server tick: the dash's stab, the attack out of a slide, the finisher's hold, stab and kill. */
    static void serverTick(ServerPlayer p, Stamina s) {
        if (s.move == Move.DASH) dashTick(p, s);
        if (s.move == Move.SLIDE_ATTACK && s.moveTick == SLIDE_HIT) slideHit(p, s);
        if (s.move == Move.FINISHER) finisherTick(p, s);
    }

    /** The dash: the blade looks for its mob every tick of the lunge, in a box that moves with the player. */
    static void dashTick(ServerPlayer p, Stamina s) {
        if (s.moveTick == DASH_HIT) {
            p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5f, 1.4f);
        }
        if (s.dashHit || s.moveTick < DASH_REACH_FROM || s.moveTick > DASH_REACH_TO) return;
        LivingEntity target = dashTarget(p);
        if (target == null) return;
        s.dashHit = true;
        blow(p, target, (float) MovesConfig.dashCrit(), true);
    }

    /**
     * The mob in the way of a dash's blade: the nearest in front, within reach and a box as wide as
     * MovesConfig.dashWidth to either side of the lunge's line (author: bigger than it was, 0.7).
     */
    public static LivingEntity dashTarget(Player p) {
        Vec3 look = flat(p.getViewVector(1f));
        if (look.lengthSqr() < 1.0E-6) return null;
        look = look.normalize();
        Vec3 from = p.position();
        double width = MovesConfig.dashWidth();
        AABB box = p.getBoundingBox().expandTowards(look.scale(DASH_REACH)).inflate(width + 0.5, 0.5, width + 0.5);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != p && e.isAlive() && e.isPickable() && !p.isAlliedTo(e) && !(e instanceof ArmorStand))) {
            Vec3 to = flat(e.position().subtract(from));
            double along = to.dot(look) - e.getBbWidth() / 2;
            double aside = to.subtract(look.scale(to.dot(look))).length() - e.getBbWidth() / 2;
            if (along < -0.3 || along > DASH_REACH || aside > width || along >= bestDist) continue;
            best = e;
            bestDist = along;
        }
        return best;
    }

    /**
     * The attack out of a slide: the blade, out to the right, sweeps round with the half turn and
     * cuts everything within reach on the way, a crit each (author). It sweeps the side the player
     * turned through and ends facing back the way they slid, so it reaches the mobs they slid past.
     */
    static void slideHit(ServerPlayer p, Stamina s) {
        double reach = MovesConfig.slideAttackReach();
        float r = (s.attackYaw + 180f) * Mth.DEG_TO_RAD;
        Vec3 facing = new Vec3(-Mth.sin(r), 0, Mth.cos(r));
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 0.9f);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + facing.x, p.getY() + 0.6, p.getZ() + facing.z, 1, 0, 0, 0, 0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach + 1, 1.0, reach + 1),
                e -> e != p && e.isAlive() && e.isPickable() && !p.isAlliedTo(e) && !(e instanceof ArmorStand))) {
            Vec3 to = flat(e.position().subtract(p.position()));
            if (to.length() - e.getBbWidth() / 2 > reach) continue;
            // the front half of the new facing and the right side it turned through (all but behind its back)
            if (to.lengthSqr() > 0.04 && angle(facing, to) > 115) continue;
            blow(p, e, MovesConfig.slideAttackCrit(), false);
        }
    }

    /**
     * A crit of the weapon in hand (its damage and enchantments, times {@code crit}), with fire,
     * the blade's wear and the vanilla knockback. {@code once}: the swing's cooldown starts over.
     */
    static void blow(ServerPlayer p, LivingEntity target, float crit, boolean once) {
        ServerLevel level = p.serverLevel();
        ItemStack weapon = p.getMainHandItem();
        float damage = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE)
                + EnchantmentHelper.getDamageBonus(weapon, target.getMobType());
        damage *= crit;
        int fire = EnchantmentHelper.getFireAspect(p);
        if (fire > 0 && !target.isOnFire()) target.setSecondsOnFire(1);
        if (!target.hurt(p.damageSources().playerAttack(p), damage)) return;
        // The blow's own knockback (vanilla's 0.4) is all: a stab doubles the mob over, it does not fling it.
        if (fire > 0) target.setSecondsOnFire(fire * 4);
        p.crit(target);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, 1f);
        EnchantmentHelper.doPostHurtEffects(target, p);
        EnchantmentHelper.doPostDamageEffects(p, target);
        weapon.hurtEnemy(target, p);
        if (weapon.isEmpty()) p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.setLastHurtMob(target);
        if (once) p.resetAttackStrengthTicker();
        Stamina.of(p).combatTicks = MovesConfig.combatTicks();
    }

    /**
     * Knocks an entity along {@code dir} as vanilla knockback of this strength would, but whatever
     * resists knockback in it (an iron golem, a ravager, armour with knockback resistance): the
     * vault's shove and the kick (author, 2026-10-29).
     */
    public static void shove(LivingEntity e, Vec3 dir, double strength) {
        Vec3 d = flat(dir);
        if (d.lengthSqr() < 1.0E-6 || strength <= 0) return;
        d = d.normalize().scale(strength);
        Vec3 v = e.getDeltaMovement();
        e.hasImpulse = true;
        e.setDeltaMovement(v.x / 2 + d.x, e.onGround() ? Math.min(0.4, v.y / 2 + strength) : v.y, v.z / 2 + d.z);
        e.hurtMarked = true;
    }

    /** The finisher starts: the mob is remembered (the client named it, MoveServer checked it). */
    static void finisherStarted(ServerPlayer p, Stamina s, Entity target) {
        s.target = target.getId();
    }

    /**
     * The mob is held on the blade: it stays where it is, turned to the player, and does nothing.
     * The stab hurts it (a flinch, nothing more); the blade coming out kills it.
     */
    static void finisherTick(ServerPlayer p, Stamina s) {
        if (!(p.level().getEntity(s.target) instanceof LivingEntity mob) || !mob.isAlive()) return;
        if (s.moveTick > FINISH_KILL) return;
        Vec3 v = mob.getDeltaMovement();
        mob.setDeltaMovement(0, Math.min(v.y, 0), 0);
        mob.hurtMarked = true;
        if (mob instanceof Mob m) {
            m.getNavigation().stop();
            m.getLookControl().setLookAt(p, 30f, 30f);
            m.setTarget(p);
        }
        ServerLevel level = p.serverLevel();
        if (s.moveTick == FINISH_STAB) {
            DamageSource src = p.damageSources().playerAttack(p);
            mob.hurt(src, Math.min(1f, Math.max(0f, mob.getHealth() - 0.5f)));
            mob.setDeltaMovement(0, 0, 0);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1f, 0.8f);
            Vec3 at = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.1);
            p.getMainHandItem().hurtAndBreak(1, p, e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        } else if (s.moveTick == FINISH_KILL) {
            mob.invulnerableTime = 0;
            mob.hurt(p.damageSources().playerAttack(p), mob.getHealth() + mob.getAbsorptionAmount() + 1000f);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, 0.7f);
            Vec3 at = mob.position().add(0, mob.getBbHeight() * 0.6, 0);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 16, 0.2, 0.2, 0.2, 0.3);
            p.setLastHurtMob(mob);
            s.combatTicks = MovesConfig.combatTicks();
        }
    }

    /** The mob on the blade cannot hurt the player while it is there. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.isCanceled()) return;
        Stamina s = Stamina.of(p);
        if (s.move != Move.FINISHER || s.moveTick > FINISH_KILL + 2) return;
        Entity by = event.getSource().getEntity();
        if (by != null && by.getId() == s.target) event.setCanceled(true);
    }

    private Strikes() {}
}
