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
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * The chain finisher on the player's side (Chain): Ctrl, W and Space down with a sword, the left button (or the sword's
 * finisher just over), two weak mobs about at least: a leap onto the weak mob in front, the blade over its shoulder into
 * its back; then onto the next weak one near, onto its back, the blade into its head, and so on in turn, up to
 * MovesConfig.chainMax; then the back flip off and a back roll. The player is carried along an arc onto each mob's shoulders, turned
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
        // the first, the third, the fifth from the front (face to face, the blade into the back), the others from behind
        // (into the head), in turn (author, 2026-11-09); the hop's take turning the way the player turns (clockwise from
        // above: the yaw growing), a whole turn more if the turn is short (author: "180-360")
        boolean first = move == Move.CHAIN_LEAP, f = first || DONE.size() % 2 == 0;
        float yaw = p.getYRot();
        float d = Mth.wrapDegrees(mob.getYRot() + (f ? 180f : 0f) - yaw);
        if (!first && Math.abs(d) < 120) d += d < 0 ? -360 : 360;
        if (!first) move = f ? (d >= 0 ? Move.CHAIN_HOP_STAB : Move.CHAIN_HOP_STAB_CCW) : (d >= 0 ? Move.CHAIN_HOP : Move.CHAIN_HOP_CCW);
        if (!MovesClient.start(p, s, move, mob.getId())) return false;
        DONE.add(mob.getId());
        front = f;
        from = p.position();
        yawFrom = yaw;
        yawWas = yawFrom;
        yawTo = yawFrom + d;
        return true;
    }

    /** Every client tick after moving: the player carried by the chain; the next leap or the flip once the blade is in. */
    static void tick(LocalPlayer p, Stamina s) {
        afterFinisher(p, s);
        Move m = s.move;
        if (m == null || !m.chain()) {
            if (m == null) from = null;
            return;
        }
        if (from == null) return;
        int t = s.moveTick;
        p.fallDistance = 0f;
        if (m == Move.CHAIN_FLIP) {
            // off the mob backwards and down by itself (author, 2026-11-09: "as with Ctrl + W + Space + R"), a back roll on
            // the ground once down
            float r = p.getYRot() * Mth.DEG_TO_RAD;
            Vec3 back = new Vec3(Mth.sin(r), 0, -Mth.cos(r));
            if (t < Chain.FLIP_OFF) {
                p.setPos(from);
                p.setDeltaMovement(Vec3.ZERO);
            } else if (t == Chain.FLIP_OFF) {
                p.setDeltaMovement(back.scale(Chain.FLIP_BACK).add(0, Chain.FLIP_UP, 0));
            } else if (t >= Chain.ROLL_FROM && t <= Chain.ROLL_TO && p.onGround()) {
                p.setDeltaMovement(back.scale(Chain.ROLL_SPEED).add(0, p.getDeltaMovement().y, 0));
            }
            return;
        }
        Entity e = p.level().getEntity(s.target);
        LivingEntity mob = e instanceof LivingEntity le ? le : null;
        if (mob != null && (mob.isAlive() || t <= Chain.kill(m) + 1)) {
            // on the mob itself, as in the author's take (it was a block over its head): in front of it or on its back
            Vec3 top = Chain.contact(mob, front);
            if (t <= Chain.land(m)) {
                int off = Chain.off(m);
                float f = Mth.clamp((t - off) / (float) (Chain.land(m) - off), 0f, 1f);
                double h = 0.45 + Math.max(0, top.y - from.y) * 0.3;
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
        // the blade is in and out: on to the next weak one near, or the flip down
        if (t >= Chain.next(m)) {
            LivingEntity next = DONE.size() < MovesConfig.chainMax() ? next(p, p.position(), MovesConfig.chainRange(), 180) : null;
            if (next == null || !leap(p, s, Move.CHAIN_HOP, next)) {
                Vec3 was = p.position();
                if (MovesClient.start(p, s, Move.CHAIN_FLIP, -1)) from = was;
            }
        }
    }

    private static Vec3 lastTop;
    /** This leap's mob is taken from the front (else from behind). */
    private static boolean front;

    private ChainInput() {}
}
