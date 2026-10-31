package dev.mcreload.moves.client;

import dev.mcreload.moves.Bow;
import dev.mcreload.moves.HudConfig;
import dev.mcreload.moves.Marks;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.MovesConfig;
import dev.mcreload.moves.Stamina;
import dev.mcreload.moves.net.MovesNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The bow on the player's side (author, 2026-10-30, 23:01; Marks, Bow).
 *
 * <ul>
 *   <li><b>G.</b> Tapped: the volley, a slide back onto a knee and three arrows 0.55 s apart. Held
 *   (still down after HOLD ticks): the hail, the slide a block longer and an arrow every 4 ticks,
 *   five at least, on while G is held, ten at most, spread about the look. With marked mobs in sight
 *   the arrows go at them in turn, and the player turns to each as its arrow leaves.</li>
 *   <li><b>Marks.</b> The right button held (the bow drawn), the left one on a mob: a mark on it, or
 *   off it if it had one; with Shift: all of them off.</li>
 *   <li><b>The volley at the marked.</b> The right button let go at the full draw with marked mobs in
 *   sight, the volley not waiting out its 25 s: no arrow of the bow's own; instead an arrow at each
 *   in turn, each a step of the flow's turns and poses played twice as fast, the player turned to its
 *   mob. W, A, S, D: a spin forward, a roll to that side, a hop back; none: a spin and a pose in turn
 *   almost in place.</li>
 * </ul>
 */
public final class BowVolley {
    /** G held this many ticks makes the hail; let go before, the volley. */
    private static final int HOLD = 4;
    /** Without keys the volley at the marked goes round these: a spin, a pose, the other spin, another pose... */
    private static final Move[] ROUND = {Move.MARKED_SPIN_LEFT, Move.MARKED_CROUCH, Move.MARKED_SPIN_RIGHT, Move.MARKED_STAND,
            Move.MARKED_SPIN_LEFT, Move.MARKED_KNEEL, Move.MARKED_SPIN_RIGHT, Move.MARKED_CROUCH};

    /** Ticks G has been down since it was pressed with a bow (0: not waiting). */
    private static int gDown;
    /** The volley at the marked going on: the mobs still to shoot at, the step it is at, the mob of the step. */
    private static List<Integer> queue;
    private static int step;
    private static LivingEntity facing;
    /** The next spin forward (W held) goes left, then right... */
    private static boolean spinLeft = true;

    static void reset() {
        gDown = 0;
        queue = null;
        facing = null;
    }

    /** The volley at the marked is going on (the bow's own release is not let through). */
    static boolean marking() {
        return queue != null;
    }

    /**
     * Every client tick, before the flow and the Legolas stance. Returns whether the left button was
     * taken (a mark made or taken off).
     */
    static boolean tickLocal(Minecraft mc, LocalPlayer p, Stamina s, boolean attackPressed, boolean abilityPressed) {
        boolean bow = Bow.isBow(p.getMainHandItem());
        if (!bow) {
            reset();
            return false;
        }
        // ---- G: tapped, the volley; held, the hail
        if (abilityPressed && s.move == null && gDown == 0 && HudConfig.Moves.VOLLEY.on() && Bow.volleyReady(p)
                && !StrikeInput.waits(p, s, Move.BARRAGE)) gDown = 1;
        if (gDown > 0) {
            gDown++;
            boolean held = MovesClient.held(mc, MovesClient.ABILITY);
            if (!held || gDown > HOLD) {
                gDown = 0;
                Move move = held ? Move.HAIL : Move.BARRAGE;
                if (s.move == null && MovesClient.start(p, s, move, -1)) {
                    if (p.isUsingItem()) p.stopUsingItem();     // the volley takes over the drawn bow
                    StrikeInput.swallow(mc);
                }
            }
        }
        // ---- the hail goes on while G is held (and there is the energy for the next arrow), five arrows at least
        if (s.move == Move.HAIL && Bow.hailDecides(s.moveTick)) {
            boolean more = MovesClient.held(mc, MovesClient.ABILITY)
                    && (s.free() || p.getAbilities().instabuild || s.energy >= MovesConfig.costHailExtra());
            if (!more) MovesClient.cancel(p, s, 4);
        }
        // ---- G at the marked: turned to the mob of the next arrow a tick before it leaves
        if (s.move != null && s.move.volley()) {
            int k = s.move == Move.BARRAGE ? Bow.volleyArrow(s.moveTick + 1) : Bow.hailArrow(s.moveTick + 1);
            if (k >= 0) {
                List<LivingEntity> marked = Marks.inSight(p, s);
                if (!marked.isEmpty()) face(p, marked.get(k % marked.size()));
            }
        }
        // ---- the marks: the right button held, the left one on a mob (with Shift: all off)
        boolean taken = false;
        if (attackPressed && HudConfig.Moves.MARKS.on() && mc.options.keyUse.isDown() && s.move == null) {
            if (mc.options.keyShift.isDown()) {
                if (!s.marks.isEmpty()) {
                    s.marks.clear();
                    MovesNet.CHANNEL.sendToServer(new MovesNet.Mark(-1));
                    p.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3f, 0.8f);
                    p.displayClientMessage(Component.translatable("kinetrix_moves.marks.cleared"), true);
                }
                taken = true;
            } else {
                LivingEntity mob = picked(p);
                if (mob != null) {
                    boolean had = s.marks.containsKey(mob.getId());
                    Marks.toggle(p, s, mob);
                    MovesNet.CHANNEL.sendToServer(new MovesNet.Mark(mob.getId()));
                    p.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.35f, had ? 0.8f : 1.7f);
                    p.displayClientMessage(Component.translatable("kinetrix_moves.marks.count", s.marks.size(), MovesConfig.marksMax()), true);
                    taken = true;
                }
            }
        }
        // ---- the volley at the marked starts when the bow is let go (released, below); its first step here
        if (queue != null && queue.size() > 0 && step == 0 && (s.move == null || !s.move.marked())) {
            if (s.move != null) MovesClient.cancel(p, s, 2);
            next(mc, p, s);
        }
        // ---- the volley's next step once this one's arrow is out; facing its mob meanwhile
        if (queue != null) {
            if (s.move != null && !s.move.marked()) {
                queue = null;
                facing = null;
            } else if (s.move == null || s.moveTick >= s.move.freeFrom()) {
                next(mc, p, s);
            } else if (facing != null && facing.isAlive()) {
                face(p, facing);
            }
        }
        return taken;
    }

    static void init() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(BowVolley::released);
    }

    /**
     * The local player lets go of the drawn bow (vanilla's release, whatever let go of the button): at
     * the full draw with marked mobs in sight and the volley at them ready, the bow looses no arrow of its
     * own (the server keeps it too, Bow.released) and the volley starts on the next tick.
     */
    private static void released(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Stop event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() != mc.player || !Bow.isBow(event.getItem()) || queue != null || !HudConfig.Moves.MARKS.on()) return;
        LocalPlayer p = mc.player;
        Stamina s = Stamina.of(p);
        if (p.getTicksUsingItem() < 20 || (s.move != null && !s.move.drawnBow()) || !Marks.volleyReady(p, s)) return;
        event.setCanceled(true);
        queue = new ArrayList<>();
        for (LivingEntity e : Marks.inSight(p, s)) queue.add(e.getId());
        step = 0;
    }

    /** The next step of the volley at the marked: the next mob still there and in sight, or the end. */
    private static void next(Minecraft mc, LocalPlayer p, Stamina s) {
        while (queue != null && !queue.isEmpty()) {
            int id = queue.remove(0);
            if (!(p.level().getEntity(id) instanceof LivingEntity e) || !e.isAlive() || !Marks.seen(p, e)) continue;
            face(p, e);
            facing = e;
            boolean left = mc.options.keyLeft.isDown(), right = mc.options.keyRight.isDown();
            boolean back = mc.options.keyDown.isDown(), fwd = mc.options.keyUp.isDown();
            Move move;
            if (left != right) move = left ? Move.MARKED_ROLL_LEFT : Move.MARKED_ROLL_RIGHT;
            else if (back && !fwd) move = Move.MARKED_BACKSTEP;
            else if (fwd) move = (spinLeft = !spinLeft) ? Move.MARKED_SPIN_RIGHT : Move.MARKED_SPIN_LEFT;
            else move = ROUND[step % ROUND.length];
            boolean first = s.move == null || !s.move.marked();
            if (!MovesClient.start(p, s, move, e.getId(), BowStance.keys(mc))) break;
            // as the server counts it: the first step starts the wait and pays for the start, each one for its mob
            if (first) {
                s.markedCooldown = MovesConfig.markedCooldownTicks();
                s.spend(MovesConfig.costMarked(), p);
            }
            s.spend(MovesConfig.costMarkedEach(), p);
            step++;
            return;
        }
        queue = null;
        facing = null;
    }

    /** The player turned to aim at the mob (where its arrow will meet it). */
    private static void face(LocalPlayer p, LivingEntity e) {
        float[] look = Marks.look(Marks.aim(p, e, Bow.AIMED_POWER * 3f));
        p.setYRot(look[0]);
        p.setXRot(look[1]);
        p.setYHeadRot(look[0]);
        p.yBodyRot = look[0];
    }

    /** The mob along the look the bow may mark (within its reach, nothing solid before it; a little wide at range). */
    private static LivingEntity picked(LocalPlayer p) {
        double range = MovesConfig.markRange();
        Vec3 eye = p.getEyePosition(1f), look = Vec3.directionFromRotation(p.getXRot(), p.getYRot());
        Vec3 end = eye.add(look.scale(range));
        HitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        double best = eye.distanceToSqr(end);
        LivingEntity found = null;
        AABB around = p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.5);
        for (Entity e : p.level().getEntities(p, around, x -> x instanceof LivingEntity && !x.isSpectator() && x.isPickable())) {
            double d = Math.sqrt(eye.distanceToSqr(e.position()));
            AABB box = e.getBoundingBox().inflate(e.getPickRadius() + 0.15 + 0.01 * d);
            java.util.Optional<Vec3> hit = box.contains(eye) ? java.util.Optional.of(eye) : box.clip(eye, end);
            if (hit.isEmpty()) continue;
            double dist = eye.distanceToSqr(hit.get());
            if (dist < best && Marks.markable(p, e)) {
                best = dist;
                found = (LivingEntity) e;
            }
        }
        return found;
    }

    private BowVolley() {}
}
