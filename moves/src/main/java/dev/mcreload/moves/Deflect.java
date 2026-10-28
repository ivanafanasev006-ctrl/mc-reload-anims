package dev.mcreload.moves;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Knocking an arrow aside (docs/combat-spec.md, 6.6): R as it comes, with a sword or a bow in
 * hand. The side dodge carries the player out of the way as usual; at its start (0-0.21 s) the
 * weapon also knocks off to its side an arrow about to reach the player (the dodge would soon have
 * carried them out of its way). Costs 1.5 shields (the dodge pays the energy); without shields the
 * arrow just flies past.
 */
public final class Deflect {
    /** What the knocked arrow keeps of its speed. */
    private static final double KEEP = 0.45;

    /** The arm holding the sword or bow (the main hand first), or null. */
    static HumanoidArm weaponHand(ServerPlayer p) {
        if (isWeapon(p.getMainHandItem())) return p.getMainArm();
        if (isWeapon(p.getOffhandItem())) return p.getMainArm().getOpposite();
        return null;
    }

    private static boolean isWeapon(ItemStack stack) {
        return Guard.isSword(stack) || stack.getItem() instanceof BowItem;
    }

    /**
     * Every server tick at the start of a side dodge: arrows about to reach the player are swatted
     * before the dodge has carried them out of the arrow's way (it moves half a block a tick).
     */
    static void serverTick(ServerPlayer p, Stamina s) {
        if (!inFrames(s) || weaponHand(p) == null || s.shields <= 0f) return;
        Vec3 chest = p.position().add(0, p.getBbHeight() * 0.6, 0);
        for (Projectile arrow : p.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(REACH + 2))) {
            if (arrow.getOwner() == p || arrow.getTags().contains(TAG)) continue;
            Vec3 v = arrow.getDeltaMovement();
            if (v.lengthSqr() < 0.04) continue;             // lying in the ground, or barely moving
            // Where along its next few ticks does it pass closest to the chest, and how close?
            Vec3 to = chest.subtract(arrow.position());
            double along = to.dot(v) / v.lengthSqr();
            if (along < 0 || along > 3) continue;           // flying away, or still too far
            if (arrow.position().add(v.scale(along)).distanceTo(chest) > REACH) continue;
            tryDeflect(p, s, arrow);
        }
    }

    /** How close an arrow must pass to the chest to be swatted (blocks). */
    private static final double REACH = 1.1;

    private static boolean inFrames(Stamina s) {
        return s.move != null && s.move.sideDodge() && s.moveTick >= Move.DEFLECT_FROM && s.moveTick <= Move.DEFLECT_TO;
    }

    /** A projectile meets a dodging player: knock it aside if the dodge is in its deflect frames. */
    static void tryDeflect(ServerPlayer p, Stamina s, Projectile arrow) {
        if (!inFrames(s)) return;
        HumanoidArm arm = weaponHand(p);
        float cost = MovesConfig.deflectShields();
        if (arm == null || s.shields <= 0f || arrow.getTags().contains(TAG)) return;
        s.shields = Math.max(0f, s.shields - cost);
        s.shieldTimer = 0;
        arrow.addTag(TAG);    // once; it may cross the player's box again on its way out
        // Off to the weapon's side, a little up, much slower: the blade (or the bow) swats it away.
        Vec3 look = p.getViewVector(1f);
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        Vec3 side = arm == HumanoidArm.RIGHT ? right : right.scale(-1);
        double speed = arrow.getDeltaMovement().length() * KEEP;
        arrow.setDeltaMovement(side.scale(speed).add(look.scale(speed * 0.3)).add(0, speed * 0.25, 0));
        arrow.hasImpulse = true;
        ServerLevel level = p.serverLevel();
        Vec3 at = arrow.position();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3f, 2.0f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5f, 1.4f);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.1, 0.1, 0.1, 0.3);
        MoveServer.sync(p, s, true);
    }

    /** Marks a projectile already knocked aside. */
    static final String TAG = KinetrixMoves.MODID + ".deflected";

    private Deflect() {}
}
