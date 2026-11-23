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
 * down, a thrust, a whole turn, from behind, into the belly), round them all twice; back where he began he skids to a stop as on a skateboard. Then time
 * runs again: they fly off, each its own way (as if blown up, onto the back, onto the face, tripping), and die.</p>
 *
 * <p>99 % of the bar, 30 s before the next (author). The camera stays where it was, behind the player's place, and turns
 * all round with the mouse (client/TimeStopView). The player cannot be hurt meanwhile. Not on bosses or players.</p>
 */
public final class TimeStop {
    /** The stance and the freeze (ticks); a blow; its hit; the brake; when the enemies die in it. */
    public static final int STANCE_LEN = 30, FREEZE = 20, STEP = 7, HIT = 3, BRAKE_LEN = 36, DIE_AT = 22;
    /**
     * The chain finisher in this way (author, 2026-11-20): a leap (chain_leap at 2.5 times: on the mob at tick 3, the blade
     * in at 5); a mad flicker and its hit; the freeze (they slow down as he leaps); the flickers' rounds.
     */
    public static final int CR_HOP_LEN = 7, CR_LAND = 3, CR_STAB = 5, CR_BLINK = 3, CR_BLINK_HIT = 1, CR_FREEZE = 8, CR_ROUNDS = 3;
    /** The mad flickers, in turn. */
    private static final Move[] BLINKS = {Move.CR_BLINK_A, Move.CR_BLINK_B, Move.CR_BLINK_C, Move.CR_BLINK_D};
    /** The frozen poses, given round in turn; the poses they fly off in. */
    static final MobPose[] FROZEN = {MobPose.FROZEN_RUN, MobPose.FROZEN_LEAP, MobPose.FROZEN_POUNCE, MobPose.FROZEN_RAGE};
    private static final MobPose[] FLY_OFF = {MobPose.BLASTED, MobPose.FALL_BACK, MobPose.FALL_FACE, MobPose.STUMBLE};
    /** The blows, in turn (author 19.11: "the blows and the look of the player varied"). */
    public static final Move[] BLOWS = {Move.TS_SLASH, Move.TS_LEAP, Move.TS_THRUST, Move.TS_SPIN, Move.TS_BACK, Move.TS_BELLY};

    private static final class State {
        final Vec3 anchor;
        final float yaw;
        final List<Integer> targets;
        int step;
        /** The chain finisher's run (leaps, then the mad flickers), and how many leaps are made, and its ticks so far. */
        boolean chain;
        int leaps, age;

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
        int total = STANCE_LEN + mobs.size() * MovesConfig.timeStopRounds() * STEP + BRAKE_LEN + 20;
        for (int i = 0; i < mobs.size(); i++) {
            ids.add(mobs.get(i).getId());
            MovesNet.mobPose(mobs.get(i), FROZEN[i % FROZEN.length], total, false);
        }
        ON.put(p.getUUID(), new State(p.position(), p.getYRot(), ids));
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), MovesSounds.TIME_STOP_SLOW.get(), SoundSource.PLAYERS, 1.2f, 1f);
    }

    /** When a blow's blade goes through (the time stop's at HIT, the chain's mad flickers at CR_BLINK_HIT). */
    public static int hitTick(Move m) {
        return m.crBlink() ? CR_BLINK_HIT : m.asStrike() ? AxeShadow.HIT : HIT;
    }

    /**
     * The chain finisher in the time stop's way starts (MoveServer: CR_LEAP let through at the weak mob in front): the
     * weak ones about (Chain.weak, in sight), from that one on, each next the nearest to the one before, up to
     * MovesConfig.chainMax; they slow down and freeze as he leaps.
     */
    static void chainStarted(ServerPlayer p, Stamina s, int first) {
        double reach = Chain.FIRST_REACH + MovesConfig.chainRange();
        List<LivingEntity> pool = new ArrayList<>(p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach),
                e -> e != p && Chain.weak(e) && e.distanceTo(p) <= reach && Marks.seen(p, e)));
        List<LivingEntity> order = new ArrayList<>();
        LivingEntity at = p.level().getEntity(first) instanceof LivingEntity le && pool.contains(le) ? le : null;
        if (at == null && !pool.isEmpty()) at = pool.get(0);
        while (at != null && order.size() < MovesConfig.chainMax()) {
            order.add(at);
            pool.remove(at);
            LivingEntity from = at;
            at = pool.stream().min((a, b) -> Double.compare(a.distanceToSqr(from), b.distanceToSqr(from))).orElse(null);
        }
        List<Integer> ids = new ArrayList<>();
        int total = order.size() * (CR_HOP_LEN + CR_ROUNDS * CR_BLINK) + BRAKE_LEN + 20;
        for (int i = 0; i < order.size(); i++) {
            ids.add(order.get(i).getId());
            MovesNet.mobPose(order.get(i), FROZEN[i % FROZEN.length], total, false);
        }
        State st = new State(p.position(), p.getYRot(), ids);
        st.chain = true;
        st.leaps = 1;
        ON.put(p.getUUID(), st);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), MovesSounds.TIME_STOP_SLOW.get(), SoundSource.PLAYERS, 1.2f, 1.15f);
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
        if (s.move == Move.TS_BRAKE) {
            if (s.moveTick < SKID) skidFx(p, s.moveTick);
            if (s.moveTick == DIE_AT) for (LivingEntity mob : mobs) GuardAnswers.kill(p, mob);
            else if (s.moveTick < DIE_AT) for (LivingEntity mob : mobs) mob.fallDistance = 0f;
            if (s.moveTick >= Move.TS_BRAKE.length - 1) ON.remove(p.getUUID());
            return;
        }
        // slowing down to a stop within the freeze, then held as they are
        for (LivingEntity mob : mobs) {
            float k = s.move == Move.TS_STANCE ? Math.max(0f, 1f - s.moveTick / (float) FREEZE)
                    : st.chain ? Math.max(0f, 1f - st.age / (float) CR_FREEZE) : 0f;
            Vec3 v = mob.getDeltaMovement();
            mob.setDeltaMovement(v.x * k, Math.min(0, v.y) * k, v.z * k);
            mob.hurtMarked = true;
            mob.fallDistance = 0f;
            if (mob instanceof Mob m) {
                m.getNavigation().stop();
                m.setTarget(null);
            }
        }
        st.age++;
        int steps = st.targets.size() * (st.chain ? CR_ROUNDS : MovesConfig.timeStopRounds());
        if ((s.move.timeStopBlow() && s.moveTick == hitTick(s.move)) || (s.move.crLeap() && s.moveTick == CR_STAB)) {
            LivingEntity mob = byId(p, s.target);
            if (mob != null) hitFx(p, mob);
        }
        if (s.moveTick < s.move.length - 1) return;
        // the chain: a leap on to each in turn first (the client carries him there)
        while (st.chain && st.leaps < st.targets.size()) {
            LivingEntity mob = byId(p, st.targets.get(st.leaps++));
            if (mob == null) continue;
            s.start(p, Move.CR_HOP, false);
            s.target = mob.getId();
            MovesNet.force(p, Move.CR_HOP, mob.getId());
            return;
        }
        // the next blow: the next enemy still about (all dead or gone: the brake)
        while (st.step < steps) {
            int i = st.step % st.targets.size();
            LivingEntity mob = byId(p, st.targets.get(i));
            st.step++;
            if (mob == null) continue;
            Move blow = st.chain ? BLINKS[(st.step - 1) % BLINKS.length] : BLOWS[(st.step - 1) % BLOWS.length];
            Vec3 from = p.position();
            Vec3 spot = spot(p, st, mob, blow, st.step - 1);
            s.start(p, blow, false);
            s.target = mob.getId();
            MovesNet.force(p, blow, mob.getId());
            go(p, spot);
            flickerFx(p, from, spot);
            return;
        }
        // back where he began, skidding to a stop as on a skateboard (author 19.11, instead of the yawn): he comes out a
        // little short of his place, going on the way he came, and the ground stops him on it; time runs again
        Vec3 from = p.position();
        Vec3 way = Strikes.flat(st.anchor.subtract(from));
        way = way.lengthSqr() < 1.0E-4 ? Strikes.flat(Vec3.directionFromRotation(0f, st.yaw)) : way.normalize();
        Vec3 in = st.anchor.subtract(way.scale(SKID_SHORT));
        if (!p.level().noCollision(p, p.getBoundingBox().move(in.subtract(p.position())))) in = st.anchor;
        int wayYaw = Math.floorMod(Math.round(facing(Vec3.ZERO, way)), 360);
        s.start(p, Move.TS_BRAKE, false);
        // the brake's extra: the way he skids (degrees; the clients turn the body side-on to it)
        s.target = wayYaw;
        MovesNet.force(p, Move.TS_BRAKE, wayYaw);
        go(p, in);
        p.setDeltaMovement(way.x * SKID_SPEED, 0.0, way.z * SKID_SPEED);
        p.hurtMarked = true;
        flickerFx(p, from, in);
        p.serverLevel().playSound(null, in.x, in.y, in.z, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1f, 0.6f);
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
        if (st.chain) side += step * 2.39996;
        double reach = mob.getBbWidth() / 2 + 1.1;
        for (int tryIt = 0; tryIt < 8; tryIt++) {
            double a = base + side + round * Math.PI / 2 + tryIt * Math.PI / 4;
            Vec3 at = mob.position().add(Math.cos(a) * reach, 0, Math.sin(a) * reach);
            if (p.level().noCollision(p, p.getBoundingBox().move(at.subtract(p.position())))) return at;
        }
        return p.position();
    }

    /** The brake: how far short of his place he comes out (blocks), his speed into the skid, how long it scrapes (ticks). */
    private static final double SKID_SHORT = 1.6, SKID_SPEED = 0.75;
    private static final int SKID = 11;

    /** The skid's dust and scrape: the ground under his feet thrown up behind him. */
    static void skidFx(ServerPlayer p, int tick) {
        ServerLevel level = p.serverLevel();
        net.minecraft.core.BlockPos under = p.blockPosition().below();
        net.minecraft.world.level.block.state.BlockState ground = level.getBlockState(under);
        if (ground.isAir()) return;
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, ground), p.getX(), p.getY() + 0.1,
                p.getZ(), 6, 0.35, 0.05, 0.35, 0.15);
        if (tick % 3 == 0) {
            level.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0.2, 0.0, 0.2, 0.01);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), ground.getSoundType().getStepSound(), SoundSource.PLAYERS, 0.8f, 0.7f);
        }
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
