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

    /** Dash: the stab lands (0.45 s, the arm straight in the lunge); free to move once the blade is out (0.85 s). */
    public static final int DASH_HIT = 9, DASH_FREE = 17;
    /** Dash: speed (blocks a tick) for each tick from the start: the run goes on and speeds up into the lunge. */
    public static final float[] DASH_RUN = {0.30f, 0.30f, 0.31f, 0.32f, 0.33f, 0.35f, 0.38f, 0.46f, 0.50f, 0.20f};
    /** Dash: the run stops this close to the mob in front (blocks between the hitboxes): the blade reaches it there. */
    public static final float DASH_STOP = 0.7f;
    /** Dash: how far in front the blade reaches (blocks from the player's centre). */
    public static final double DASH_REACH = 2.6;
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

    /** Every server tick: the dash's stab, the finisher's hold, stab and kill. */
    static void serverTick(ServerPlayer p, Stamina s) {
        if (s.move == Move.DASH && s.moveTick == DASH_HIT) dashHit(p);
        if (s.move == Move.FINISHER) finisherTick(p, s);
    }

    /** The mob in the way of a dash's blade: the nearest in front, within reach and a narrow box. */
    public static LivingEntity dashTarget(Player p) {
        Vec3 look = flat(p.getViewVector(1f));
        if (look.lengthSqr() < 1.0E-6) return null;
        look = look.normalize();
        Vec3 from = p.position();
        AABB box = p.getBoundingBox().expandTowards(look.scale(DASH_REACH)).inflate(0.6, 0.3, 0.6);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != p && e.isAlive() && e.isPickable() && !p.isAlliedTo(e) && !(e instanceof ArmorStand))) {
            Vec3 to = flat(e.position().subtract(from));
            double along = to.dot(look) - e.getBbWidth() / 2;
            double aside = to.subtract(look.scale(to.dot(look))).length() - e.getBbWidth() / 2;
            if (along < -0.3 || along > DASH_REACH || aside > 0.7 || along >= bestDist) continue;
            best = e;
            bestDist = along;
        }
        return best;
    }

    /** The dash's stab: a crit into the first one in front (nothing if the lunge went into the air). */
    static void dashHit(ServerPlayer p) {
        LivingEntity target = dashTarget(p);
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5f, 1.4f);
        if (target == null) return;
        ItemStack sword = p.getMainHandItem();
        float damage = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE)
                + EnchantmentHelper.getDamageBonus(sword, target.getMobType());
        damage *= (float) MovesConfig.dashCrit();
        int fire = EnchantmentHelper.getFireAspect(p);
        if (fire > 0 && !target.isOnFire()) target.setSecondsOnFire(1);
        if (!target.hurt(p.damageSources().playerAttack(p), damage)) return;
        // The blow's own knockback (vanilla's 0.4) is all: a stab doubles the mob over, it does not fling it.
        if (fire > 0) target.setSecondsOnFire(fire * 4);
        p.crit(target);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, 1f);
        EnchantmentHelper.doPostHurtEffects(target, p);
        EnchantmentHelper.doPostDamageEffects(p, target);
        sword.hurtEnemy(target, p);
        if (sword.isEmpty()) p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.setLastHurtMob(target);
        p.resetAttackStrengthTicker();
        Stamina.of(p).combatTicks = MovesConfig.combatTicks();
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
