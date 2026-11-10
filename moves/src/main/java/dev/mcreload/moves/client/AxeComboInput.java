package dev.mcreload.moves.client;

import dev.mcreload.moves.Move;
import dev.mcreload.moves.Stamina;
import dev.mcreload.moves.Strikes;
import dev.mcreload.moves.Whirl;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The axe's combo on the player's side (author, 2026-11-10, evening: "EACH one 70 % of his health"): G held with an axe, 3 to
 * 6 mobs about. A blow for each of them, one after another (Move.AXE_TRIPLE, then AXE_COMBO_2, _3, _1 in turn): each one at
 * the mob the least turn away of those left, the player turned to it by the time the axe's head passes in front
 * (Whirl.comboHit; the camera too) and stepped up to it (Whirl.comboStand); then the end, back to the stance.
 */
public final class AxeComboInput {
    /** The combo's mobs (taken at its start), those already hit, the yaw turned from and to, the blows so far. */
    private static final List<Integer> MOBS = new ArrayList<>(), DONE = new ArrayList<>();
    private static float yawFrom, yawWas;
    private static int blows;

    static void reset() {
        MOBS.clear();
        DONE.clear();
        blows = 0;
    }

    /** G held long enough with an axe and the combo allowed: its first blow at the mob the nearest the look. */
    static boolean start(LocalPlayer p, Stamina s) {
        reset();
        for (LivingEntity e : Whirl.tripleTargets(p)) MOBS.add(e.getId());
        if (MOBS.size() < Whirl.TRIPLE_FEWEST) return false;
        LivingEntity first = next(p);
        return first != null && blow(p, s, Move.AXE_TRIPLE, first);
    }

    /** The combo's mob the least turn from the look of those not hit yet, still alive and near. */
    private static LivingEntity next(LocalPlayer p) {
        LivingEntity best = null;
        float bestTurn = Float.MAX_VALUE;
        for (int id : MOBS) {
            if (DONE.contains(id) || !(p.level().getEntity(id) instanceof LivingEntity e) || !e.isAlive()) continue;
            if (Strikes.flat(e.position().subtract(p.position())).length() > Whirl.TRIPLE_REACH + 2) continue;
            float turn = Math.abs(Mth.wrapDegrees(yawTo(p, e) - p.getYRot()));
            if (turn < bestTurn) {
                best = e;
                bestTurn = turn;
            }
        }
        return best;
    }

    private static float yawTo(LocalPlayer p, Entity e) {
        Vec3 d = e.position().subtract(p.position());
        return (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
    }

    private static boolean blow(LocalPlayer p, Stamina s, Move move, LivingEntity mob) {
        if (!MovesClient.start(p, s, move, mob.getId())) return false;
        DONE.add(mob.getId());
        blows++;
        yawFrom = p.getYRot();
        yawWas = yawFrom;
        return true;
    }

    /** Every client tick after moving: turned and stepped to this blow's mob until the axe meets it; the next blow or the end. */
    static void tick(LocalPlayer p, Stamina s) {
        Move m = s.move;
        if (m == null || !m.axeCombo()) return;
        int t = s.moveTick, hit = Whirl.comboHit(m);
        Vec3 v = p.getDeltaMovement();
        if (t <= hit && p.level().getEntity(s.target) instanceof LivingEntity mob && mob.isAlive()) {
            float f = Mth.clamp((t + 1) / (float) (hit + 1), 0f, 1f);
            f = f * f * (3 - 2 * f);
            float yaw = yawFrom + Mth.wrapDegrees(yawTo(p, mob) - yawFrom) * f;
            p.setYRot(p.getYRot() + (yaw - yawWas));
            p.yBodyRot = yaw;
            yawWas = yaw;
            Vec3 to = Strikes.flat(mob.position().subtract(p.position()));
            double d = to.length();
            double step = d > 1.0E-3 ? Mth.clamp((d - Whirl.comboStand(m)) / Math.max(1, hit - t), -0.25, 0.5) : 0;
            Vec3 go = d > 1.0E-3 ? to.scale(step / d) : Vec3.ZERO;
            p.setDeltaMovement(go.x, v.y, go.z);
        } else {
            p.setDeltaMovement(0, v.y, 0);
        }
        // the blow over (its ready pose again): on to the next mob, or the end
        if (t >= m.length - 1) {
            LivingEntity next = blows < Whirl.TRIPLE_MOST ? next(p) : null;
            if (next == null || !blow(p, s, Whirl.comboNext(m), next)) {
                if (!MovesClient.start(p, s, Move.AXE_COMBO_END, -1)) dev.mcreload.moves.KinetrixMoves.LOG.info("axe combo: no end at {} tick {}", m, t);
            }
        }
    }

    private AxeComboInput() {}
}
