package dev.mcreload.moves.client;

import dev.mcreload.moves.Chain;
import dev.mcreload.moves.HudConfig;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.MovesConfig;
import dev.mcreload.moves.Stamina;
import dev.mcreload.moves.Strikes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * The chain finisher on the player's side (Chain): Ctrl, W and Space down with a sword, the left button (or the sword's
 * finisher just over), two weak mobs about at least: a leap onto the shoulders of the weak mob in front (from behind it),
 * the blade into its head; then onto the next weak one near, up to MovesConfig.chainMax; then the back flip down. The player is carried along an arc onto each mob's shoulders, turned
 * with it to the way the mob faces (the camera too), and held there until the next leap.
 */
public final class ChainInput {
    /** The mobs of this chain so far (not landed on again), how many, where the leap started, the yaw turned from and to. */
    private static final Set<Integer> DONE = new HashSet<>();
    private static Vec3 from;
    private static float yawFrom, yawTo, yawWas;
    private static double groundY;

    static void reset() {
        DONE.clear();
        from = null;
    }

    /**
     * The left button pressed: with Ctrl, W and Space (held, or Space pressed a moment before) and a sword, the chain starts
     * at the weak mob in front. Returns whether the button was taken.
     */
    static boolean start(Minecraft mc, LocalPlayer p, Stamina s, boolean jumped) {
        if (!HudConfig.Moves.CHAIN.on() || !Strikes.sword(p) || s.move != null) return false;
        if (!mc.options.keySprint.isDown() || !mc.options.keyUp.isDown() || !jumped) return false;
        if (StrikeInput.waits(p, s, Move.CHAIN_LEAP)) return true;
        return begin(p, s, Chain.FIRST_REACH, 50);
    }

    /**
     * The chain from the ground: the weak mob nearest within {@code reach} (and {@code half} degrees of the look), another one
     * near it at least (author, 2026-11-09: "only with 2 to 6 about").
     */
    private static boolean begin(LocalPlayer p, Stamina s, double reach, double half) {
        DONE.clear();
        groundY = p.getY();
        LivingEntity mob = next(p, p.position(), reach, half);
        if (mob == null) return false;
        DONE.add(mob.getId());
        LivingEntity second = next(p, mob.position(), MovesConfig.chainRange(), 180);
        DONE.clear();
        return second != null && leap(p, s, Move.CHAIN_LEAP, mob);
    }

    /**
     * The sword's finisher on LMB just over (its blade out): two weak mobs about or more, the chain goes on by itself
     * (author, 2026-11-09), from where the player stands, at the nearest of them.
     */
    private static void afterFinisher(LocalPlayer p, Stamina s) {
        if (s.move != Move.FINISHER || s.moveTick != Strikes.FINISH_OUT || !HudConfig.Moves.CHAIN.on() || !Strikes.sword(p)) return;
        if (s.chainCooldown > 0) return;
        begin(p, s, MovesConfig.chainRange(), 180);
    }

    /** The weak mob nearest {@code at} within {@code reach} (and {@code half} degrees of the look, for the first), not landed on yet. */
    private static LivingEntity next(LocalPlayer p, Vec3 at, double reach, double half) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        Vec3 look = p.getViewVector(1f);
        look = new Vec3(look.x, 0, look.z).normalize();
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach + 2))) {
            if (e == p || DONE.contains(e.getId()) || !Chain.weak(e)) continue;
            Vec3 to = e.position().subtract(at);
            double d = to.length();
            if (d > reach || Math.abs(e.getY() - groundY) > 3) continue;
            if (half < 180) {
                Vec3 flat = new Vec3(to.x, 0, to.z);
                if (flat.lengthSqr() > 1.0E-4 && Math.toDegrees(Math.acos(Mth.clamp(look.dot(flat.normalize()), -1, 1))) > half) continue;
            }
            if (d < bestD) {
                best = e;
                bestD = d;
            }
        }
        return best;
    }

    private static boolean leap(LocalPlayer p, Stamina s, Move move, LivingEntity mob) {
        // behind it, facing the way it faces: the turn the shorter way, a whole turn more if it is short (author: "180-360");
        // the hop's take turning that way (clockwise from above: the yaw growing)
        float yaw = p.getYRot();
        float d = Mth.wrapDegrees(mob.getYRot() - yaw);
        if (Math.abs(d) < 120) d += d < 0 ? -360 : 360;
        if (move.chainHop()) move = d >= 0 ? Move.CHAIN_HOP : Move.CHAIN_HOP_CCW;
        if (!MovesClient.start(p, s, move, mob.getId())) return false;
        DONE.add(mob.getId());
        from = p.position();
        yawFrom = yaw;
        yawWas = yawFrom;
        yawTo = yawFrom + d;
        return true;
    }

    /** Where the player stands on the mob: on its shoulders, a little behind its middle. */
    private static Vec3 shoulders(LivingEntity mob) {
        float r = mob.getYRot() * Mth.DEG_TO_RAD;
        Vec3 back = new Vec3(Mth.sin(r), 0, -Mth.cos(r)).scale(0.25);
        return mob.position().add(back).add(0, Math.max(0.6, mob.getBbHeight() - 0.35), 0);
    }

    /** Every client tick after moving: the player carried by the chain; the next leap or the flip once the blade is in. */
    static void tick(LocalPlayer p, Stamina s) {
        Move m = s.move;
        afterFinisher(p, s);
        m = s.move;
        if (m == null || !m.chain()) {
            if (m == null) from = null;
            return;
        }
        if (from == null) return;
        int t = s.moveTick;
        p.fallDistance = 0f;
        if (m == Move.CHAIN_FLIP) {
            float f = Mth.clamp((t - Chain.FLIP_OFF) / (float) (Chain.FLIP_LAND - Chain.FLIP_OFF), 0f, 1f);
            if (f >= 1f) return;
            Vec3 to = flipTo(p);
            Vec3 at = from.lerp(to, f).add(0, 1.2 * Math.sin(Math.PI * f), 0);
            p.setPos(at);
            p.setDeltaMovement(Vec3.ZERO);
            return;
        }
        Entity e = p.level().getEntity(s.target);
        LivingEntity mob = e instanceof LivingEntity le ? le : null;
        if (mob != null && (mob.isAlive() || t <= Chain.kill(m) + 1)) {
            Vec3 top = shoulders(mob);
            if (t <= Chain.land(m)) {
                int off = Chain.off(m);
                float f = Mth.clamp((t - off) / (float) (Chain.land(m) - off), 0f, 1f);
                double h = 1.0 + Math.max(0, top.y - from.y) * 0.3;
                p.setPos(from.lerp(top, f).add(0, h * Math.sin(Math.PI * f), 0));
                float yaw = Mth.lerp(f, yawFrom, yawTo);
                p.setYRot(p.getYRot() + (yaw - yawWas));
                p.yBodyRot = yaw;
                yawWas = yaw;
            } else {
                p.setPos(top);
            }
            lastTop = top;
        } else if (lastTop != null) {
            p.setPos(lastTop);
        }
        p.setDeltaMovement(Vec3.ZERO);
        // the blade is in: on to the next weak one near, or the flip down
        if (t >= Chain.next(m)) {
            LivingEntity next = DONE.size() < MovesConfig.chainMax() ? next(p, p.position(), MovesConfig.chainRange(), 180) : null;
            if (next == null || !leap(p, s, Move.CHAIN_HOP, next)) {
                Vec3 was = p.position();
                if (MovesClient.start(p, s, Move.CHAIN_FLIP, -1)) {
                    from = was;
                    flipTo = null;
                }
            }
        }
    }

    private static Vec3 lastTop, flipTo;

    /** Where the flip comes down: 2.5 blocks behind, on the floor below (or as low as the chain began). */
    private static Vec3 flipTo(LocalPlayer p) {
        if (flipTo != null) return flipTo;
        float r = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 back = from.add(new Vec3(Mth.sin(r), 0, -Mth.cos(r)).scale(2.5));
        HitResult hit = p.level().clip(new ClipContext(back.add(0, 1.0, 0), back.add(0, -6.0, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p));
        double y = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().y : groundY;
        flipTo = new Vec3(back.x, y, back.z);
        return flipTo;
    }

    private ChainInput() {}
}
