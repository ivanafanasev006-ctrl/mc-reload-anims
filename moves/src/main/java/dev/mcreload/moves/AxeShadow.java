package dev.mcreload.moves;

import dev.mcreload.moves.net.MovesNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The axe's shadow (author, 2026-11-20), the axe's ultimate in the awakening square: an axe in hand, the right button and G
 * held, the time stop's conditions (5 to 10 enemies about, 99 % of the bar, its 30 s wait; author: "as the time stop").
 *
 * <p>He turns once about himself and is gone (not drawn, not seen). The enemies stand where they are, lost, looking about
 * (MobPose.CONFUSED). Then he comes out behind one of them: a blow from above, and it falls dead; gone again; a second or
 * so later at another's side, as a stealthy assassin, and so on, a different blow each time, until all are dead. Where he
 * struck his pale after-image stays a moment and each blow leaves a wave in the air (the time stop's pixel slash). The
 * camera stays where it was and turns all round with the mouse (client/TimeStopView). He cannot be hurt meanwhile.</p>
 */
public final class AxeShadow {
    /** The turn (as_spin, gone at its end); a blow and its hit (as_chop's, as_side's, as_hook's keys). */
    public static final int SPIN_LEN = 14, STRIKE_LEN = 12, HIT = 5;
    /** How long they look about before the first blow; the time gone between blows (author: "1-1.5 s" from one to the next). */
    public static final int LOST = 24, GONE = 14;
    private static final Move[] STRIKES = {Move.AS_CHOP, Move.AS_SIDE, Move.AS_HOOK};

    private static final class State {
        final List<Integer> targets;
        int next, wait;

        State(List<Integer> targets) {
            this.targets = targets;
        }
    }

    private static final Map<UUID, State> ON = new HashMap<>();

    /** Can it be made now: an axe, enough enemies about, the bar nearly full, its wait over (the time stop's numbers). */
    public static boolean ready(Player p, Stamina s) {
        boolean client = p.level().isClientSide;
        if (!Strikes.axe(p) || s.axeTripleCooldown > (client ? 0 : 5)) return false;
        if (!s.free() && !p.getAbilities().instabuild && s.energy < MovesConfig.maxEnergy() * MovesConfig.timeStopShare() - 0.01f) return false;
        return TimeStop.enemies(p).size() >= MovesConfig.timeStopMin();
    }

    /** It starts (MoveServer, the client's AS_SPIN let through): the bar, the wait, the enemies lost. */
    static void started(ServerPlayer p, Stamina s) {
        if (!s.free() && !p.getAbilities().instabuild) s.energy = Math.max(0f, s.energy - MovesConfig.maxEnergy() * MovesConfig.timeStopShare());
        s.axeTripleCooldown = s.cooled(MovesConfig.timeStopCooldownTicks());
        List<LivingEntity> mobs = TimeStop.enemies(p);
        List<Integer> ids = new ArrayList<>();
        int total = SPIN_LEN + LOST + mobs.size() * (STRIKE_LEN + GONE) + 40;
        for (LivingEntity mob : mobs) {
            ids.add(mob.getId());
            MovesNet.mobPose(mob, MobPose.CONFUSED, total, false);
        }
        ON.put(p.getUUID(), new State(ids));
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), MovesSounds.TIME_STOP_SWING.get(), SoundSource.PLAYERS, 1f, 0.7f);
    }

    static void serverTick(ServerPlayer p, Stamina s) {
        State st = ON.get(p.getUUID());
        if (st == null) return;
        if (s.move == null || !s.move.axeShadow()) {
            // broken off: seen again, the enemies as they were
            for (LivingEntity mob : alive(p, st)) MovesNet.mobPose(mob, MobPose.NONE, 0, false);
            p.setInvisible(false);
            ON.remove(p.getUUID());
            return;
        }
        p.fallDistance = 0f;
        // the lost ones stand where they are, striking no one
        for (LivingEntity mob : alive(p, st)) {
            Vec3 v = mob.getDeltaMovement();
            mob.setDeltaMovement(0, Math.min(0, v.y), 0);
            mob.hurtMarked = true;
            if (mob instanceof Mob m) {
                m.getNavigation().stop();
                m.setTarget(null);
            }
        }
        if (s.move == Move.AS_SPIN) {
            if (s.moveTick >= SPIN_LEN - 1) vanish(p, s, st, LOST);
            return;
        }
        if (s.move.asStrike()) {
            LivingEntity mob = byId(p, s.target);
            if (s.moveTick == HIT && mob != null) strike(p, s.move, mob);
            if (s.moveTick >= STRIKE_LEN - 1) {
                if (alive(p, st).isEmpty()) end(p, s);
                else vanish(p, s, st, GONE);
            }
            return;
        }
        // gone: out behind the next one when the wait is over
        if (--st.wait > 0) return;
        while (st.next < st.targets.size()) {
            LivingEntity mob = byId(p, st.targets.get(st.next));
            Move blow = STRIKES[st.next % STRIKES.length];
            st.next++;
            if (mob == null) continue;
            Vec3 at = spot(p, mob, blow);
            TimeStop.go(p, at);
            p.setInvisible(false);
            s.start(p, blow, false);
            s.target = mob.getId();
            MovesNet.force(p, blow, mob.getId());
            ServerLevel level = p.serverLevel();
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1, at.z, 8, 0.25, 0.5, 0.25, 0.01);
            level.playSound(null, at.x, at.y, at.z, MovesSounds.TIME_STOP_SWING.get(), SoundSource.PLAYERS, 1f, 0.8f);
            return;
        }
        end(p, s);
    }

    /** Gone: not drawn, not seen, a puff of smoke where he was; the next blow after {@code wait} ticks. */
    private static void vanish(ServerPlayer p, Stamina s, State st, int wait) {
        st.wait = wait;
        s.start(p, Move.AS_HIDDEN, false);
        s.target = -1;
        MovesNet.force(p, Move.AS_HIDDEN, -1);
        p.setInvisible(true);
        ServerLevel level = p.serverLevel();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1, p.getZ(), 10, 0.3, 0.6, 0.3, 0.01);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.4f, 0.6f);
    }

    /** All dead (or gone): seen again where the last blow fell; the camera comes back. */
    private static void end(ServerPlayer p, Stamina s) {
        p.setInvisible(false);
        ON.remove(p.getUUID());
        s.end(p);
        MovesNet.forceStop(p);
        MoveServer.sync(p, s, true);
    }

    /** Where he comes out for this blow: behind it (the way it faces), or at its side for the side blow; not in a wall. */
    private static Vec3 spot(ServerPlayer p, LivingEntity mob, Move blow) {
        float yaw = mob.getYRot() + (blow == Move.AS_SIDE ? 90f : 180f);
        double reach = mob.getBbWidth() / 2 + 1.0;
        for (int tryIt = 0; tryIt < 8; tryIt++) {
            float a = (yaw + tryIt * 45f) * Mth.DEG_TO_RAD;
            Vec3 at = mob.position().add(-Mth.sin(a) * reach, 0, Mth.cos(a) * reach);
            if (p.level().noCollision(p, p.getBoundingBox().move(at.subtract(p.position())))) return at;
        }
        return mob.position();
    }

    /** The blow: it falls dead, the way the blow sends it. */
    private static void strike(ServerPlayer p, Move blow, LivingEntity mob) {
        MobPose fall = blow == Move.AS_SIDE ? MobPose.STUMBLE : blow == Move.AS_HOOK ? MobPose.FALL_BACK : MobPose.FALL_FACE;
        MovesNet.mobPose(mob, fall, 80, false);
        ServerLevel level = p.serverLevel();
        Vec3 at = mob.position().add(0, mob.getBbHeight() * 0.6, 0);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.25, 0.3, 0.25, 0.3);
        level.playSound(null, at.x, at.y, at.z, MovesSounds.TIME_STOP_HIT.get(), SoundSource.PLAYERS, 1f, 0.8f);
        GuardAnswers.kill(p, mob);
        Vec3 v = mob.getDeltaMovement();
        mob.setDeltaMovement(0, Math.min(0, v.y), 0);
        mob.hurtMarked = true;
    }

    /** This mob is held lost in someone's shadow (it strikes no one). */
    static boolean lost(LivingEntity e) {
        if (ON.isEmpty()) return false;
        for (State st : ON.values()) if (st.targets.contains(e.getId())) return true;
        return false;
    }

    private static List<LivingEntity> alive(ServerPlayer p, State st) {
        List<LivingEntity> out = new ArrayList<>();
        for (int id : st.targets) {
            LivingEntity e = byId(p, id);
            if (e != null) out.add(e);
        }
        return out;
    }

    private static LivingEntity byId(ServerPlayer p, int id) {
        return p.level().getEntity(id) instanceof LivingEntity e && e.isAlive() ? e : null;
    }

    private AxeShadow() {}
}
