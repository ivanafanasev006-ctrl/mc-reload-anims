package dev.mcreload.moves;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The bow's marks (author, 2026-10-30, 23:01): "with the right button held, aim at an enemy and press
 * the left one on him: a red mark in a white square appears on him; so up to 8 enemies. Let go of the
 * right button and the character, aiming by himself, shoots at each target, if it is not behind a
 * barricade where the arrow will not reach it, with the quick turns and poses of the right and the
 * left button. With marks, G shoots an arrow at each in turn (1 into the first, 2 into the second, 3
 * into the first again), G+ the same, as quick as the volley. A mark goes by itself after a minute, or
 * the left button on him again takes it off, and the right button, Shift and the left button take
 * them all off; with 8 already, each new one takes the place of the first marked, then the second...;
 * the volley at all the marked once in 25 seconds."
 *
 * <p>Both ends keep the marks (Stamina.marks, in the order they were made): the client draws them and
 * turns the player to them, the server aims the arrows. The client tells the server of each mark it
 * makes or takes off (MovesNet.Mark).</p>
 */
public final class Marks {
    /** The mark on {@code e} made or taken off (with as many as there may be, the first marked makes room). */
    public static void toggle(Player p, Stamina s, Entity e) {
        if (s.marks.remove(e.getId()) != null) return;
        while (s.marks.size() >= MovesConfig.marksMax()) {
            Iterator<Integer> first = s.marks.keySet().iterator();
            first.next();
            first.remove();
        }
        s.marks.put(e.getId(), p.level().getGameTime() + MovesConfig.markTicks());
    }

    /** A mob that can be marked: alive, not the player, within reach. */
    public static boolean markable(Player p, Entity e) {
        return e instanceof LivingEntity le && le != p && le.isAlive() && !(le instanceof ArmorStand)
                && p.distanceToSqr(le) <= MovesConfig.markRange() * MovesConfig.markRange();
    }

    /** The marks that ran out, and of mobs gone (dead, unloaded, in another world), forgotten. */
    public static void prune(Player p, Stamina s) {
        long now = p.level().getGameTime();
        for (Iterator<Map.Entry<Integer, Long>> it = s.marks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> m = it.next();
            Entity e = p.level().getEntity(m.getKey());
            if (m.getValue() <= now || !(e instanceof LivingEntity le) || !le.isAlive()) it.remove();
        }
    }

    /** The marked mobs an arrow can reach from the player now, in the order they were marked. */
    public static List<LivingEntity> inSight(Player p, Stamina s) {
        List<LivingEntity> out = new ArrayList<>();
        for (int id : s.marks.keySet()) {
            if (p.level().getEntity(id) instanceof LivingEntity e && e.isAlive() && markable(p, e) && seen(p, e)) out.add(e);
        }
        return out;
    }

    /**
     * The volley at the marked can start now: marked mobs in sight, its wait over (the server lets a
     * client a few ticks ahead of it through), the energy for its start and its first mob, something to shoot.
     */
    public static boolean volleyReady(Player p, Stamina s) {
        if (s.markedCooldown > (p.level().isClientSide ? 0 : 5) || !Bow.volleyReady(p)) return false;
        if (!s.free() && !p.getAbilities().instabuild && s.energy < MovesConfig.costMarked() + MovesConfig.costMarkedEach()) return false;
        return !inSight(p, s).isEmpty();
    }

    /** Nothing solid between the player's eyes and the mob (its middle or its head): the arrow gets there. */
    public static boolean seen(Player p, LivingEntity e) {
        Vec3 eye = p.getEyePosition();
        for (double h : new double[]{0.5, 0.85}) {
            Vec3 to = e.position().add(0, e.getBbHeight() * h, 0);
            if (p.level().clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }

    /**
     * The way to send an arrow of {@code speed} (blocks a tick) from the player's eyes so it meets the
     * mob: at its middle, where it will be when the arrow gets there (walking on as it walks now), and
     * that much higher as the arrow drops on the way (0.05 a tick each tick, vanilla's).
     */
    public static Vec3 aim(Player p, LivingEntity e, float speed) {
        Vec3 eye = p.getEyePosition();
        Vec3 to = e.position().add(0, e.getBbHeight() * 0.55, 0);
        double t = to.subtract(eye).length() / speed;
        Vec3 v = e.getDeltaMovement();
        to = to.add(v.x * t, 0, v.z * t);
        t = to.subtract(eye).length() / speed;
        return to.add(0, 0.5 * 0.05 * t * t, 0).subtract(eye);
    }

    /** The yaw and pitch (degrees) that look along {@code dir}. */
    public static float[] look(Vec3 dir) {
        float yaw = (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG);
        return new float[]{yaw, pitch};
    }

    private Marks() {}
}
