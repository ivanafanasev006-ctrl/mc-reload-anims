package dev.mcreload.moves;

import dev.mcreload.moves.net.MovesNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The bow's ultimate (author, 2026-11-23, instead of the 0.18 rain): the right button and G held with a bow, the time
 * stop's conditions. He shakes this way and that a moment (BW_RAIN, 0.9 s) and is gone, as in the axe's shadow; the
 * enemies freeze. Then at each in turn a phantom of his, half a second each, in a pose of its own: the archer's draw in
 * front of it; drawing in a jump from above; from its back in the air, falling back and down, the legs forward as if
 * sitting; upside down, shooting down as if sky and ground had changed places; out of a whole turn. Each looses an arrow
 * that hangs where it was loosed. Then the real one is back where he began, skidding backwards with the bow low in both
 * hands, and the arrows all fly at once: all dead (the time stop's sounds). The camera stays where it was and turns all
 * round with the mouse (client/TimeStopView); he cannot be hurt meanwhile.
 */
public final class BowPhantoms {
    /** Gone between the phantoms (ticks); a phantom's loosing (the poses' 0.3 s). */
    public static final int GAP = 2, LOOSE = 6;
    /** In the skid: the arrows fly; all die. */
    public static final int FLY = 10, DIE = 16;
    private static final Move[] POSES = {Move.BW_AIM, Move.BW_JUMP, Move.BW_FALLBACK, Move.BW_FLIP, Move.BW_TURN};

    private static final class State {
        final List<Integer> targets = new ArrayList<>();
        /** The hanging arrows and each one's mob. */
        final List<int[]> arrows = new ArrayList<>();
        Vec3 anchor;
        float yaw;
        int next, wait;
    }

    private static final Map<UUID, State> ON = new HashMap<>();

    /** It starts (HeavyMoves.started, the client's BW_RAIN let through): the bar, the wait, the enemies frozen. */
    static void started(ServerPlayer p, Stamina s) {
        if (!s.free() && !p.getAbilities().instabuild) s.energy = Math.max(0f, s.energy - MovesConfig.maxEnergy() * MovesConfig.timeStopShare());
        s.rainCooldown = s.cooled(MovesConfig.rainCooldownTicks());
        State st = new State();
        st.anchor = p.position();
        st.yaw = p.getYRot();
        List<LivingEntity> mobs = TimeStop.enemies(p);
        int total = Move.BW_RAIN.length + mobs.size() * (Move.BW_AIM.length + GAP) + Move.BW_BRAKE.length + 40;
        int i = 0;
        for (LivingEntity mob : mobs) {
            st.targets.add(mob.getId());
            float face = TimeStop.facing(mob.position(), p.position());
            mob.setYRot(face);
            mob.setYBodyRot(face);
            mob.setYHeadRot(face);
            MovesNet.mobPose(mob, TimeStop.FROZEN[i++ % TimeStop.FROZEN.length], total, false);
        }
        ON.put(p.getUUID(), st);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), MovesSounds.TIME_STOP_SLOW.get(), SoundSource.PLAYERS, 1.2f, 1f);
    }

    static void serverTick(ServerPlayer p, Stamina s) {
        State st = ON.get(p.getUUID());
        if (st == null) return;
        if (s.move == null || !s.move.bowRain()) {
            // broken off: seen again, the frozen ones as they were, the hanging arrows down
            for (LivingEntity mob : HeavyMoves.alive(p, st.targets)) MovesNet.mobPose(mob, MobPose.NONE, 0, false);
            for (int[] a : st.arrows) if (p.level().getEntity(a[0]) instanceof AbstractArrow arrow) arrow.setNoGravity(false);
            p.setInvisible(false);
            ON.remove(p.getUUID());
            return;
        }
        p.fallDistance = 0f;
        List<LivingEntity> mobs = HeavyMoves.alive(p, st.targets);
        if (s.move != Move.BW_BRAKE || s.moveTick < DIE) for (LivingEntity mob : mobs) HeavyMoves.hold(mob);
        int t = s.moveTick;
        if (s.move == Move.BW_RAIN) {
            // a little before the shaking runs out on the client, so it goes on into the gone without a gap
            if (t >= Move.BW_RAIN.length - 3) vanish(p, s, st, GAP + 2);
            return;
        }
        if (s.move.bowPhantom()) {
            LivingEntity mob = p.level().getEntity(s.target) instanceof LivingEntity le && le.isAlive() ? le : null;
            if (t == LOOSE && mob != null) loose(p, st, mob);
            if (t >= s.move.length - 1) vanish(p, s, st, GAP);
            return;
        }
        if (s.move == Move.BW_HIDDEN) {
            if (--st.wait > 0) return;
            while (st.next < st.targets.size()) {
                Move pose = POSES[st.next % POSES.length];
                LivingEntity mob = p.level().getEntity(st.targets.get(st.next++)) instanceof LivingEntity le && le.isAlive() ? le : null;
                if (mob == null) continue;
                Vec3 at = spot(p, st, mob, pose);
                TimeStop.go(p, at);
                p.setInvisible(false);
                s.start(p, pose, false);
                s.target = mob.getId();
                MovesNet.force(p, pose, mob.getId());
                ServerLevel level = p.serverLevel();
                level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1, at.z, 6, 0.25, 0.5, 0.25, 0.01);
                level.playSound(null, at.x, at.y, at.z, MovesSounds.TIME_STOP_SWING.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
                return;
            }
            brake(p, s, st, mobs);
            return;
        }
        if (s.move == Move.BW_BRAKE) {
            if (t < 12) TimeStop.skidFx(p, t);
            if (t == FLY) {
                // all at once, each at its own
                for (int[] a : st.arrows) {
                    if (!(p.level().getEntity(a[0]) instanceof AbstractArrow arrow)) continue;
                    LivingEntity aim = p.level().getEntity(a[1]) instanceof LivingEntity le && le.isAlive() ? le : null;
                    if (aim == null) {
                        arrow.setNoGravity(false);
                        continue;
                    }
                    Vec3 to = aim.position().add(0, aim.getBbHeight() * 0.6, 0).subtract(arrow.position()).normalize();
                    arrow.setNoGravity(false);
                    arrow.setDeltaMovement(to.scale(2.6));
                    arrow.hurtMarked = true;
                }
                p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.2f, 0.8f);
            }
            if (t == DIE) {
                for (LivingEntity mob : mobs) GuardAnswers.kill(p, mob);
                ON.remove(p.getUUID());
            }
        }
    }

    /** Gone: not drawn, not seen, a puff of smoke; the next phantom after {@code wait} ticks. */
    private static void vanish(ServerPlayer p, Stamina s, State st, int wait) {
        st.wait = wait;
        s.start(p, Move.BW_HIDDEN, false);
        s.target = -1;
        MovesNet.force(p, Move.BW_HIDDEN, -1);
        p.setInvisible(true);
        ServerLevel level = p.serverLevel();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1, p.getZ(), 8, 0.3, 0.6, 0.3, 0.01);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.35f, 1.3f);
    }

    /** Where this phantom is: by its pose, round the mob from where he began; in the air for the leaping ones. */
    private static Vec3 spot(ServerPlayer p, State st, LivingEntity mob, Move pose) {
        Vec3 out = Strikes.flat(st.anchor.subtract(mob.position()));
        double base = out.lengthSqr() < 1.0E-4 ? Math.toRadians(st.yaw + 90f) : Math.atan2(out.z, out.x);
        double side, reach, up;
        switch (pose) {
            case BW_JUMP -> { side = Math.toRadians(40); reach = 2.8; up = 2.2; }
            case BW_FALLBACK -> { side = Math.PI; reach = 2.2; up = 2.0; }
            case BW_FLIP -> { side = 0; reach = 1.4; up = 3.2; }
            case BW_TURN -> { side = -Math.PI / 2; reach = 3.0; up = 0; }
            default -> { side = 0; reach = 3.4; up = 0; }
        }
        for (int i = 0; i < 8; i++) {
            double a = base + side + i * Math.PI / 4;
            Vec3 at = mob.position().add(Math.cos(a) * reach, up, Math.sin(a) * reach);
            if (p.level().noCollision(p, p.getBoundingBox().move(at.subtract(p.position())))) return at;
        }
        return mob.position().add(Math.cos(base) * 2, 0, Math.sin(base) * 2);
    }

    /** The phantom looses: an arrow left hanging in the air, pointed at its mob. */
    private static void loose(ServerPlayer p, State st, LivingEntity mob) {
        Vec3 eye = p.getEyePosition();
        Vec3 to = mob.position().add(0, mob.getBbHeight() * 0.6, 0).subtract(eye).normalize();
        Vec3 at = eye.add(to.scale(0.9));
        Arrow arrow = new Arrow(p.level(), p);
        arrow.setPos(at);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(to.scale(0.002));
        arrow.setYRot((float) (Mth.atan2(to.x, to.z) * Mth.RAD_TO_DEG));
        arrow.setXRot((float) (Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG));
        arrow.yRotO = arrow.getYRot();
        arrow.xRotO = arrow.getXRot();
        arrow.setBaseDamage(MovesConfig.rainDamage() / 2.6);
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        arrow.setCritArrow(true);
        p.level().addFreshEntity(arrow);
        st.arrows.add(new int[]{arrow.getId(), mob.getId()});
        p.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.8f, 1.3f);
    }

    /** The real one back where he began: in front of it a little, facing them, skidding backwards onto his place. */
    private static void brake(ServerPlayer p, Stamina s, State st, List<LivingEntity> mobs) {
        Vec3 mid = Vec3.ZERO;
        for (LivingEntity mob : mobs) mid = mid.add(mob.position());
        float face = mobs.isEmpty() ? st.yaw : TimeStop.facing(st.anchor, mid.scale(1.0 / mobs.size()));
        Vec3 ahead = new Vec3(-Mth.sin(face * Mth.DEG_TO_RAD), 0, Mth.cos(face * Mth.DEG_TO_RAD));
        Vec3 in = st.anchor.add(ahead.scale(1.6));
        if (!p.level().noCollision(p, p.getBoundingBox().move(in.subtract(p.position())))) in = st.anchor;
        int yaw = Math.floorMod(Math.round(face), 360);
        TimeStop.go(p, in);
        p.setInvisible(false);
        s.start(p, Move.BW_BRAKE, false);
        s.target = yaw;
        MovesNet.force(p, Move.BW_BRAKE, yaw);
        p.setDeltaMovement(-ahead.x * 0.75, 0, -ahead.z * 0.75);
        p.hurtMarked = true;
        ServerLevel level = p.serverLevel();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, in.x, in.y + 1, in.z, 8, 0.3, 0.6, 0.3, 0.01);
        level.playSound(null, in.x, in.y, in.z, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1f, 0.6f);
        level.playSound(null, in.x, in.y, in.z, MovesSounds.TIME_STOP_UNWIND.get(), SoundSource.PLAYERS, 1.2f, 1f);
    }

    /** This mob is frozen in someone's bow ultimate: it strikes no one. */
    static boolean frozen(LivingEntity e) {
        if (ON.isEmpty()) return false;
        for (State st : ON.values()) if (st.targets.contains(e.getId())) return true;
        return false;
    }

    private BowPhantoms() {}
}
