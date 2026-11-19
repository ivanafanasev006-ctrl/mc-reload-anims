package dev.mcreload.moves;

import dev.mcreload.moves.net.MovesNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The time stop (author, 2026-11-19): a sword in hand, the right button and G held, 5 to 10 enemies about.
 *
 * <p>Time stops for the player and those enemies only (the server and the world go on): the player goes into his
 * stance (the sword in both hands before him, looking at it) while they slow down within a second and freeze, each in a pose of its own (running,
 * in a deep leap, jumping down at him with a knee bent and the arms swung up, roaring with the arms thrown back). Then he
 * flickers from one to the next, 0.35 s each (19.11: 1.5 times faster), a different blow each time (from above, leaping
 * down, a thrust, a whole turn, from behind, into the belly), round them all twice; back where he began he yawns, the sword in his right hand. Then time
 * runs again: they fly off, each its own way (as if blown up, onto the back, onto the face, tripping), and die.</p>
 *
 * <p>99 % of the bar, 30 s before the next (author). The camera stays where it was, behind the player's place, and turns
 * all round with the mouse (client/TimeStopView). The player cannot be hurt meanwhile. Not on bosses or players.</p>
 */
public final class TimeStop {
    /** The stance and the freeze (ticks); a blow; its hit; the yawn; when the enemies die in it. */
    public static final int STANCE_LEN = 30, FREEZE = 20, STEP = 7, HIT = 3, YAWN_LEN = 40, DIE_AT = 22;
    /** The frozen poses, given round in turn; the poses they fly off in. */
    private static final MobPose[] FROZEN = {MobPose.FROZEN_RUN, MobPose.FROZEN_LEAP, MobPose.FROZEN_POUNCE, MobPose.FROZEN_RAGE};
    private static final MobPose[] FLY_OFF = {MobPose.BLASTED, MobPose.FALL_BACK, MobPose.FALL_FACE, MobPose.STUMBLE};
    /** The blows, in turn (author 19.11: "the blows and the look of the player varied"). */
    public static final Move[] BLOWS = {Move.TS_SLASH, Move.TS_LEAP, Move.TS_THRUST, Move.TS_SPIN, Move.TS_BACK, Move.TS_BELLY};

    private static final class State {
        final Vec3 anchor;
        final float yaw;
        final List<Integer> targets;
        int step;

        State(Vec3 anchor, float yaw, List<Integer> targets) {
            this.anchor = anchor;
            this.yaw = yaw;
            this.targets = targets;
        }
    }

    private static final Map<UUID, State> ON = new HashMap<>();

    /** The enemies it would take now: the nearest, up to the most, of a man's build or not, bosses aside. */
    static List<LivingEntity> enemies(Player p) {
        double r = MovesConfig.timeStopRange();
        List<LivingEntity> out = new ArrayList<>(p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                e -> e instanceof Mob && e instanceof Enemy && e.isAlive() && !Strikes.boss(e) && e.distanceTo(p) <= r
                        && (e.hasLineOfSight(p) || p.hasLineOfSight(e))));
        out.sort((a, b) -> Float.compare(a.distanceTo(p), b.distanceTo(p)));
        int max = MovesConfig.timeStopMax();
        return out.size() > max ? out.subList(0, max) : out;
    }

    /** Can it be made now: enough enemies about, the bar nearly full, its wait over. */
    public static boolean ready(Player p, Stamina s) {
        boolean client = p.level().isClientSide;
        if (s.timeStopCooldown > (client ? 0 : 5)) return false;
        if (!s.free() && !p.getAbilities().instabuild && s.energy < MovesConfig.maxEnergy() * MovesConfig.timeStopShare() - 0.01f) return false;
        return enemies(p).size() >= MovesConfig.timeStopMin();
    }

    /** It starts (MoveServer, the client's request let through): the bar, the wait, the enemies frozen. */
    static void started(ServerPlayer p, Stamina s) {
        if (!s.free() && !p.getAbilities().instabuild) s.energy = Math.max(0f, s.energy - MovesConfig.maxEnergy() * MovesConfig.timeStopShare());
        s.timeStopCooldown = s.cooled(MovesConfig.timeStopCooldownTicks());
        List<LivingEntity> mobs = enemies(p);
        List<Integer> ids = new ArrayList<>();
        int total = STANCE_LEN + mobs.size() * MovesConfig.timeStopRounds() * STEP + YAWN_LEN + 20;
        for (int i = 0; i < mobs.size(); i++) {
            ids.add(mobs.get(i).getId());
            MovesNet.mobPose(mobs.get(i), FROZEN[i % FROZEN.length], total, false);
        }
        ON.put(p.getUUID(), new State(p.position(), p.getYRot(), ids));
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), MovesSounds.TIME_STOP_SLOW.get(), SoundSource.PLAYERS, 1.2f, 1f);
    }

    static void serverTick(ServerPlayer p, Stamina s) {
        State st = ON.get(p.getUUID());
        if (st == null) return;
        if (s.move == null || !s.move.timeStop()) {
            // broken off (died, logged out, another move): time runs again, the enemies as they were
            for (LivingEntity mob : alive(p, st)) MovesNet.mobPose(mob, MobPose.NONE, 0, false);
            ON.remove(p.getUUID());
            return;
        }
        p.fallDistance = 0f;
        List<LivingEntity> mobs = alive(p, st);
        if (s.move == Move.TS_YAWN) {
            if (s.moveTick == DIE_AT) for (LivingEntity mob : mobs) GuardAnswers.kill(p, mob);
            else if (s.moveTick < DIE_AT) for (LivingEntity mob : mobs) mob.fallDistance = 0f;
            if (s.moveTick >= Move.TS_YAWN.length - 1) ON.remove(p.getUUID());
            return;
        }
        // slowing down to a stop within the freeze, then held as they are
        for (LivingEntity mob : mobs) {
            float k = s.move == Move.TS_STANCE ? Math.max(0f, 1f - s.moveTick / (float) FREEZE) : 0f;
            Vec3 v = mob.getDeltaMovement();
            mob.setDeltaMovement(v.x * k, Math.min(0, v.y) * k, v.z * k);
            mob.hurtMarked = true;
            mob.fallDistance = 0f;
            if (mob instanceof Mob m) {
                m.getNavigation().stop();
                m.setTarget(null);
            }
        }
        int steps = st.targets.size() * MovesConfig.timeStopRounds();
        if (s.move != Move.TS_STANCE && s.moveTick == HIT) {
            LivingEntity mob = byId(p, st.targets.get((st.step - 1) % st.targets.size()));
            if (mob != null) hitFx(p, mob);
        }
        if (s.moveTick < s.move.length - 1) return;
        // the next blow: the next enemy still about (all dead or gone: the yawn)
        while (st.step < steps) {
            int i = st.step % st.targets.size();
            LivingEntity mob = byId(p, st.targets.get(i));
            st.step++;
            if (mob == null) continue;
            Move blow = BLOWS[(st.step - 1) % BLOWS.length];
            Vec3 from = p.position();
            Vec3 spot = spot(p, st, mob, blow, st.step - 1);
            s.start(p, blow, false);
            s.target = mob.getId();
            MovesNet.force(p, blow, mob.getId());
            go(p, spot);
            flickerFx(p, from, spot);
            return;
        }
        // back where he began, and the yawn; time runs again
        Vec3 from = p.position();
        s.start(p, Move.TS_YAWN, false);
        s.target = -1;
        MovesNet.force(p, Move.TS_YAWN, -1);
        go(p, st.anchor);
        flickerFx(p, from, st.anchor);
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), MovesSounds.TIME_STOP_UNWIND.get(), SoundSource.PLAYERS, 1.2f, 1f);
        for (int i = 0; i < mobs.size(); i++) flyOff(p, st, mobs.get(i), i);
    }

    /**
     * Where the player stands for this blow: by its kind (in front, behind, at its side; from above and the thrust in
     * front), round the mob from where the player began, a quarter turn further each round.
     */
    private static Vec3 spot(ServerPlayer p, State st, LivingEntity mob, Move blow, int step) {
        Vec3 out = Strikes.flat(st.anchor.subtract(mob.position()));
        double base = out.lengthSqr() < 1.0E-4 ? Math.toRadians(st.yaw) : Math.atan2(out.z, out.x);
        double side = switch (blow) {
            case TS_BACK -> Math.PI;
            case TS_BELLY -> Math.PI / 2;
            case TS_SPIN -> -Math.PI / 2;
            case TS_LEAP -> Math.PI / 4;
            default -> 0;
        };
        int round = step / Math.max(1, st.targets.size());
        double reach = mob.getBbWidth() / 2 + 1.1;
        for (int tryIt = 0; tryIt < 8; tryIt++) {
            double a = base + side + round * Math.PI / 2 + tryIt * Math.PI / 4;
            Vec3 at = mob.position().add(Math.cos(a) * reach, 0, Math.sin(a) * reach);
            if (p.level().noCollision(p, p.getBoundingBox().move(at.subtract(p.position())))) return at;
        }
        return p.position();
    }

    /**
     * The player put there at once, the look kept (the camera turns on with the mouse). The look is relative and its
     * change nothing: the server's own yaw and pitch go in, as vanilla takes them away again (0 there snapped the look
     * to the south and level at each blow: the camera's jerk, author 19.11).
     */
    static void go(ServerPlayer p, Vec3 at) {
        p.connection.teleport(at.x, at.y, at.z, p.getYRot(), p.getXRot(), EnumSet.of(RelativeMovement.Y_ROT, RelativeMovement.X_ROT));
        p.setDeltaMovement(Vec3.ZERO);
    }

    static void flickerFx(ServerPlayer p, Vec3 from, Vec3 to) {
        ServerLevel level = p.serverLevel();
        Vec3 d = to.subtract(from);
        int n = (int) Math.min(12, Math.ceil(d.length() * 1.5));
        for (int i = 0; i <= n; i++) {
            Vec3 at = from.add(d.scale(i / (double) Math.max(1, n))).add(0, 1.0, 0);
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 1, 0.1, 0.3, 0.1, 0.0);
        }
        level.playSound(null, to.x, to.y, to.z, MovesSounds.TIME_STOP_SWING.get(), SoundSource.PLAYERS, 0.9f, 0.95f + p.getRandom().nextFloat() * 0.15f);
    }

    private static void hitFx(ServerPlayer p, LivingEntity mob) {
        ServerLevel level = p.serverLevel();
        Vec3 at = mob.position().add(0, mob.getBbHeight() * 0.55, 0);
        // the white slash where the blade went is drawn by the clients (TimeStopView), turned each its own way
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.25, 0.3, 0.25, 0.3);
        level.playSound(null, at.x, at.y, at.z, MovesSounds.TIME_STOP_HIT.get(), SoundSource.PLAYERS, 1f, 0.95f + p.getRandom().nextFloat() * 0.1f);
        // the hurt flash and sound, the blow's damage held back until time runs again
        level.getChunkSource().broadcastAndSend(mob, new ClientboundEntityEventPacket(mob, (byte) 2));
    }

    /** Time runs again: it flies off its own way and lies there until it dies. */
    private static void flyOff(ServerPlayer p, State st, LivingEntity mob, int i) {
        MobPose pose = FLY_OFF[i % FLY_OFF.length];
        Vec3 away = Strikes.flat(mob.position().subtract(st.anchor));
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize();
        double push = switch (pose) {
            case BLASTED -> 1.1;
            case FALL_BACK -> 0.35;
            default -> 0.2;
        };
        mob.setDeltaMovement(away.x * push, pose == MobPose.BLASTED ? 0.55 : 0.12, away.z * push);
        mob.hurtMarked = true;
        mob.hasImpulse = true;
        MovesNet.mobPose(mob, pose, 80, false);
        if (pose == MobPose.BLASTED) {
            Vec3 at = mob.position().add(0, 1, 0);
            p.serverLevel().sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
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

    /** This mob is frozen in someone's time stop (it strikes no one). */
    static boolean frozen(LivingEntity e) {
        if (ON.isEmpty()) return false;
        for (State st : ON.values()) if (st.targets.contains(e.getId())) return true;
        return false;
    }

    /** The facing of a blow: the player at its spot, turned to its mob (the client turns the body so). */
    public static float facing(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(-(to.x - from.x), to.z - from.z) * Mth.RAD_TO_DEG);
    }

    private TimeStop() {}
}
