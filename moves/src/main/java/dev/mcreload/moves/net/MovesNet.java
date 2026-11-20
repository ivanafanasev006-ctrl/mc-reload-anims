package dev.mcreload.moves.net;

import dev.mcreload.moves.KinetrixMoves;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.MoveServer;
import dev.mcreload.moves.Stamina;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Messages between a player's client and the server. A client asks for a move (and plays it at
 * once); the server answers by playing it for everyone who can see the player, or by telling the
 * client to stop. The server also sends each player their bars.
 *
 * <p>A client can still join a server without the mod (it just has no moves), and the other way
 * round.</p>
 */
public final class MovesNet {
    public static final String VERSION = "25";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(KinetrixMoves.MODID, "main"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    public static void init() {
        int id = 0;
        CHANNEL.registerMessage(id++, Request.class, Request::write, Request::read, Request::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Cancel.class, Cancel::write, Cancel::read, Cancel::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Play.class, Play::write, Play::read, Play::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Deny.class, Deny::write, Deny::read, Deny::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Bars.class, Bars::write, Bars::read, Bars::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, GuardUp.class, GuardUp::write, GuardUp::read, GuardUp::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, GuardState.class, GuardState::write, GuardState::read, GuardState::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Stoop.class, Stoop::write, Stoop::read, Stoop::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Run.class, Run::write, Run::read, Run::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Mark.class, Mark::write, Mark::read, Mark::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ThrownInfo.class, ThrownInfo::write, ThrownInfo::read, ThrownInfo::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Learned.class, Learned::write, Learned::read, Learned::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Equip.class, Equip::write, Equip::read, Equip::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, TrainerData.class, TrainerData::write, TrainerData::read, TrainerData::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, TrainerAct.class, TrainerAct::write, TrainerAct::read, TrainerAct::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, PoseMob.class, PoseMob::write, PoseMob::read, PoseMob::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /**
     * The trainer's page for this player: his offers (with {@code v}), the player's quests (time left, kills so far), the rank's
     * line (its stage, what it asks, what the player has of it). {@code open}: open the page; else only refresh it if open.
     */
    public static void trainer(ServerPlayer p, net.minecraft.world.entity.npc.Villager v, boolean open) {
        if (!connected(p)) return;
        dev.mcreload.moves.Progress pr = dev.mcreload.moves.Stamina.of(p).progress;
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        if (v != null) t.put("offers", dev.mcreload.moves.Quests.offers(v, p).copy());
        net.minecraft.nbt.ListTag qs = new net.minecraft.nbt.ListTag();
        long now = System.currentTimeMillis();
        for (net.minecraft.nbt.CompoundTag q : pr.quests) {
            net.minecraft.nbt.CompoundTag c = q.copy();
            c.putLong("left", q.getLong("until") - now);
            c.putBoolean("failed", dev.mcreload.moves.Quests.failed(q));
            c.putBoolean("done", dev.mcreload.moves.Quests.done(q));
            qs.add(c);
        }
        t.put("quests", qs);
        t.putInt("rank", pr.rank);
        t.putInt("rankLine", pr.rankLine);
        t.putInt("rankStage", pr.rankStage);
        t.putInt("levels", p.experienceLevel);
        if (pr.rankActive()) {
            dev.mcreload.moves.Quests.Stage st = dev.mcreload.moves.Quests.line(pr.rankLine).get(pr.rankStage);
            t.putString("kind", st.kind().name());
            net.minecraft.nbt.CompoundTag what = new net.minecraft.nbt.CompoundTag(), have = new net.minecraft.nbt.CompoundTag();
            for (java.util.Map.Entry<String, Integer> e : st.what().entrySet()) {
                what.putInt(e.getKey(), e.getValue());
                int n = switch (st.kind()) {
                    case KILL -> pr.rankKills.getInt(e.getKey());
                    case BRING -> p.getInventory().countItem(dev.mcreload.moves.Quests.item(e.getKey()));
                    case PAY -> p.experienceLevel;
                };
                have.putInt(e.getKey(), n);
            }
            t.put("what", what);
            t.put("have", have);
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new TrainerData(v == null ? -1 : v.getId(), open, t));
    }

    /** Server -> client: the trainer's page (see trainer). */
    public record TrainerData(int villager, boolean open, net.minecraft.nbt.CompoundTag data) {
        static void write(TrainerData m, FriendlyByteBuf b) {
            b.writeVarInt(m.villager + 1);
            b.writeBoolean(m.open);
            b.writeNbt(m.data);
        }
        static TrainerData read(FriendlyByteBuf b) { return new TrainerData(b.readVarInt() - 1, b.readBoolean(), b.readNbt()); }
        static void handle(TrainerData m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> dev.mcreload.moves.client.TrainerScreen.received(m.villager, m.open, m.data)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: on the trainer's page, an action (Quests.Action) with its argument. */
    public record TrainerAct(int villager, int action, int arg) {
        static void write(TrainerAct m, FriendlyByteBuf b) {
            b.writeVarInt(m.villager);
            b.writeByte(m.action);
            b.writeByte(m.arg);
        }
        static TrainerAct read(FriendlyByteBuf b) { return new TrainerAct(b.readVarInt(), b.readByte(), b.readByte()); }
        static void handle(TrainerAct m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) ctx.get().enqueueWork(() -> {
                if (!(p.level().getEntity(m.villager) instanceof net.minecraft.world.entity.npc.Villager v) || !dev.mcreload.moves.Trainer.is(v)
                        || v.distanceToSqr(p) > 8 * 8) return;
                dev.mcreload.moves.Quests.Action[] all = dev.mcreload.moves.Quests.Action.values();
                if (m.action >= 0 && m.action < all.length) dev.mcreload.moves.Quests.act(p, v, all[m.action], m.arg);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** What this player has learned (Progress), to their client: on joining and whenever it changes. */
    public static void progress(ServerPlayer p) {
        if (!connected(p)) return;
        dev.mcreload.moves.Progress pr = dev.mcreload.moves.Stamina.of(p).progress;
        byte[] wheel = new byte[dev.mcreload.moves.Progress.PLACES];
        for (int i = 0; i < wheel.length; i++) wheel[i] = (byte) (pr.at(i) == null ? -1 : pr.at(i).ordinal());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Learned(pr.learnedBits(), wheel, (byte) pr.rank));
    }

    /** Server -> client: the learned skills (bits), the wheel's and the perks' places (skill ids, -1 empty), the bar's rank. */
    public record Learned(long bits, byte[] wheel, byte rank) {
        static void write(Learned m, FriendlyByteBuf b) {
            b.writeLong(m.bits);
            b.writeByteArray(m.wheel);
            b.writeByte(m.rank);
        }
        static Learned read(FriendlyByteBuf b) { return new Learned(b.readLong(), b.readByteArray(64), b.readByte()); }
        static void handle(Learned m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> dev.mcreload.moves.client.MovesClient.learned(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: a place (the wheel's 0-5, the perks' 6-10) given a learned skill (-1: emptied); slot -1: all emptied. */
    public record Equip(int slot, int skill) {
        static void write(Equip m, FriendlyByteBuf b) {
            b.writeByte(m.slot);
            b.writeByte(m.skill);
        }
        static Equip read(FriendlyByteBuf b) { return new Equip(b.readByte(), b.readByte()); }
        static void handle(Equip m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) ctx.get().enqueueWork(() -> {
                dev.mcreload.moves.Progress pr = dev.mcreload.moves.Stamina.of(p).progress;
                if (m.slot < 0) pr.clearWheel();
                else pr.equip(m.slot, dev.mcreload.moves.Skill.byId(m.skill));
                progress(p);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * A thrown weapon (Thrown): who sees the thrower ({@code self}, at the throw: the weapon is not tracked yet) or the
     * weapon learn what it is and where it is stuck; their clients draw it in place of the trident it flies as.
     */
    public static void thrown(net.minecraft.world.entity.projectile.ThrownTrident t, dev.mcreload.moves.Thrown.State st, ServerPlayer self) {
        ThrownInfo m = ThrownInfo.of(t, st);
        if (self != null) CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> self), m);
        else CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> t), m);
    }

    /** The same to one player (coming near it). */
    public static void thrownTo(ServerPlayer p, net.minecraft.world.entity.projectile.ThrownTrident t, dev.mcreload.moves.Thrown.State st) {
        if (connected(p)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), ThrownInfo.of(t, st));
    }

    /**
     * A thrown weapon taken back (or gone): its clients forget it. To all near, not to the entity's trackers: once it is
     * removed it has none, and the word never came (author's video, 2026-11-09: the hint "pull the sword out" stayed, the
     * buttons only jerked the arms).
     */
    public static void thrownGone(net.minecraft.world.entity.projectile.ThrownTrident t) {
        CHANNEL.send(PacketDistributor.NEAR.with(PacketDistributor.TargetPoint.p(t.getX(), t.getY(), t.getZ(), 128, t.level().dimension())),
                new ThrownInfo(t.getId(), -1, net.minecraft.world.item.ItemStack.EMPTY, -1, 0f, 0f, 0f, 0f, 0f));
    }

    // ------------------------------------------------------------------ server -> clients

    /**
     * Everyone who can see {@code p} (not {@code p}: their client already plays it) sees the move.
     * {@code extra}: for the moves on a ledge, which way its wall is (Ledge.id); else -1.
     */
    public static void play(ServerPlayer p, Move move, int extra) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> p), new Play(p.getId(), (byte) move.ordinal(), extra));
    }

    /** A move the server starts on {@code p} (pulled up by a friend): everyone who sees them, and their own client. */
    public static void force(ServerPlayer p, Move move, int extra) {
        Play msg = new Play(p.getId(), (byte) move.ordinal(), extra);
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> p), msg);
        if (connected(p)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    /** The server ended the move it started on {@code p}: everyone who sees them, and their own client. */
    public static void forceStop(ServerPlayer p) {
        Play msg = new Play(p.getId(), (byte) -1, -1);
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> p), msg);
        if (connected(p)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    /** Everyone who can see {@code p} stops their move. */
    public static void stop(ServerPlayer p) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> p), new Play(p.getId(), (byte) -1, -1));
    }

    /** The player's guard went up or down: everyone who sees them, and the player's own client. */
    public static void guard(ServerPlayer p, dev.mcreload.moves.Guard.Kind kind) {
        GuardState msg = new GuardState(p.getId(), (byte) kind.ordinal());
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> p), msg);
        if (connected(p)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    /** Everyone who sees {@code e} sees it stoop forward by {@code lean} degrees (under a vaulting hand) in {@code delay} ticks, for {@code ticks}. */
    public static void stoop(net.minecraft.world.entity.Entity e, int delay, int ticks, float lean) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e), new Stoop(e.getId(), (byte) delay, (byte) ticks, (byte) Math.round(lean)));
    }

    /** Everyone who sees {@code e} sees it take this pose for {@code ticks} (turned: facing the other way meanwhile). */
    public static void mobPose(net.minecraft.world.entity.Entity e, dev.mcreload.moves.MobPose pose, int ticks, boolean turned) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e), new PoseMob(e.getId(), (byte) pose.ordinal(), ticks, turned));
    }

    public static void deny(ServerPlayer p, Move move) {
        if (connected(p)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Deny((byte) move.ordinal()));
    }

    public static void stamina(ServerPlayer p, Stamina s) {
        if (connected(p)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new Bars(s.energy, s.shields, dev.mcreload.moves.MovesConfig.maxEnergy(), dev.mcreload.moves.MovesConfig.maxShields(),
                        s.freeTicks, s.freeTotal, s.hasteTicks, s.combatTicks, s.cooldowns()));
    }

    /** A real player whose client has the mod (fake players and test players have no client). */
    private static boolean connected(ServerPlayer p) {
        if (p instanceof net.minecraftforge.common.util.FakePlayer || p.connection == null) return false;
        try {
            return CHANNEL.isRemotePresent(p.connection.connection);
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ messages

    /** Client -> server: the player started this move (held: C still down, for the slide; target: see MoveServer.request). */
    public record Request(byte move, boolean held, int target) {
        static void write(Request m, FriendlyByteBuf b) { b.writeByte(m.move); b.writeBoolean(m.held); b.writeVarInt(m.target + 1); }
        static Request read(FriendlyByteBuf b) { return new Request(b.readByte(), b.readBoolean(), b.readVarInt() - 1); }
        static void handle(Request m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            Move move = Move.byId(m.move);
            if (p != null && move != null) ctx.get().enqueueWork(() -> MoveServer.request(p, move, m.held, m.target));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: the player broke off this move (a jump or a hit during the slide). */
    public record Cancel(byte move) {
        static void write(Cancel m, FriendlyByteBuf b) { b.writeByte(m.move); }
        static Cancel read(FriendlyByteBuf b) { return new Cancel(b.readByte()); }
        static void handle(Cancel m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            Move move = Move.byId(m.move);
            if (p != null && move != null) ctx.get().enqueueWork(() -> MoveServer.cancel(p, move));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> client: player {@code entity} does this move (-1: stops it); {@code extra}: see {@link #play}. */
    public record Play(int entity, byte move, int extra) {
        static void write(Play m, FriendlyByteBuf b) { b.writeVarInt(m.entity); b.writeByte(m.move); b.writeVarInt(m.extra + 1); }
        static Play read(FriendlyByteBuf b) { return new Play(b.readVarInt(), b.readByte(), b.readVarInt() - 1); }
        static void handle(Play m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> dev.mcreload.moves.client.MovesClient.remoteMove(m.entity, Move.byId(m.move), m.extra)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> client: the move this client started is not allowed; stop it. */
    public record Deny(byte move) {
        static void write(Deny m, FriendlyByteBuf b) { b.writeByte(m.move); }
        static Deny read(FriendlyByteBuf b) { return new Deny(b.readByte()); }
        static void handle(Deny m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> dev.mcreload.moves.client.MovesClient.denied(Move.byId(m.move))));
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * Server -> client: the player's own bars, the enchanted golden apple's quick hands (Stamina.hasteTicks), the
     * fight's ticks left (no edge is caught meanwhile, Stamina.combatTicks),
     * and the ticks left of the cooldowns (the tray), in the order of Stamina.cooldowns: the dash, the
     * whirlwind, the sword's turn, the attack out of a slide, the volley at the marked, the bow's G, the
     * shot out of a slide, the Legolas stance.
     */
    public record Bars(float energy, float shields, float maxEnergy, float maxShields, int freeTicks, int freeTotal, int hasteTicks,
                       int combatTicks, int[] cooldowns) {
        public static final int COOLDOWNS = 15;

        static void write(Bars m, FriendlyByteBuf b) {
            b.writeFloat(m.energy);
            b.writeFloat(m.shields);
            b.writeFloat(m.maxEnergy);
            b.writeFloat(m.maxShields);
            b.writeVarInt(m.freeTicks);
            b.writeVarInt(m.freeTotal);
            b.writeVarInt(m.hasteTicks);
            b.writeVarInt(Math.max(0, m.combatTicks));
            for (int i = 0; i < COOLDOWNS; i++) b.writeVarInt(Math.max(0, m.cooldowns[i]));
        }
        static Bars read(FriendlyByteBuf b) {
            float energy = b.readFloat(), shields = b.readFloat(), maxEnergy = b.readFloat(), maxShields = b.readFloat();
            int freeTicks = b.readVarInt(), freeTotal = b.readVarInt(), hasteTicks = b.readVarInt(), combatTicks = b.readVarInt();
            int[] cooldowns = new int[COOLDOWNS];
            for (int i = 0; i < COOLDOWNS; i++) cooldowns[i] = b.readVarInt();
            return new Bars(energy, shields, maxEnergy, maxShields, freeTicks, freeTotal, hasteTicks, combatTicks, cooldowns);
        }
        static void handle(Bars m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> dev.mcreload.moves.client.MovesClient.bars(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: the mark on this mob made or taken off (Marks.toggle); -1: all of them taken off. */
    public record Mark(int entity) {
        /**
         * Not a mob: the bow about to be let go (the client, just before vanilla's release) is the volley at the marked's:
         * the release looses no arrow of the bow's own (Bow.released).
         */
        public static final int RELEASE = -2;

        static void write(Mark m, FriendlyByteBuf b) { b.writeVarInt(m.entity + 1); }
        static Mark read(FriendlyByteBuf b) { return new Mark(b.readVarInt() - 1); }
        static void handle(Mark m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) ctx.get().enqueueWork(() -> {
                dev.mcreload.moves.Stamina s = dev.mcreload.moves.Stamina.of(p);
                if (m.entity == RELEASE) {
                    s.markedReleaseAt = p.tickCount;
                    return;
                }
                if (m.entity < 0) {
                    s.marks.clear();
                    return;
                }
                net.minecraft.world.entity.Entity e = p.level().getEntity(m.entity);
                if (e != null && dev.mcreload.moves.Marks.markable(p, e)) dev.mcreload.moves.Marks.toggle(p, s, e);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: the guard button is held (true) or let go (false). */
    public record GuardUp(boolean up) {
        static void write(GuardUp m, FriendlyByteBuf b) { b.writeBoolean(m.up); }
        static GuardUp read(FriendlyByteBuf b) { return new GuardUp(b.readBoolean()); }
        static void handle(GuardUp m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) ctx.get().enqueueWork(() -> dev.mcreload.moves.Guard.request(p, m.up));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: the fast run's key (Ctrl) is held (true) or let go (false). */
    public record Run(boolean on) {
        static void write(Run m, FriendlyByteBuf b) { b.writeBoolean(m.on); }
        static Run read(FriendlyByteBuf b) { return new Run(b.readBoolean()); }
        static void handle(Run m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) ctx.get().enqueueWork(() -> Stamina.of(p).runKey = m.on);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> client: player {@code entity} guards like this now (0: not at all). */
    public record GuardState(int entity, byte kind) {
        static void write(GuardState m, FriendlyByteBuf b) { b.writeVarInt(m.entity); b.writeByte(m.kind); }
        static GuardState read(FriendlyByteBuf b) { return new GuardState(b.readVarInt(), b.readByte()); }
        static void handle(GuardState m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> dev.mcreload.moves.client.GuardInput.remote(m.entity, dev.mcreload.moves.Guard.Kind.byId(m.kind))));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> client: a mob's pose in one of our moves (MobPose). */
    public record PoseMob(int entity, byte pose, int ticks, boolean turned) {
        static void write(PoseMob m, FriendlyByteBuf b) { b.writeVarInt(m.entity); b.writeByte(m.pose); b.writeVarInt(m.ticks); b.writeBoolean(m.turned); }
        static PoseMob read(FriendlyByteBuf b) { return new PoseMob(b.readVarInt(), b.readByte(), b.readVarInt(), b.readBoolean()); }
        static void handle(PoseMob m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> dev.mcreload.moves.client.MobPoses.set(m.entity, dev.mcreload.moves.MobPose.byId(m.pose), m.ticks, m.turned)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> client: entity {@code entity} stoops forward by {@code lean} degrees (the vault's hand on its back). */
    public record Stoop(int entity, byte delay, byte ticks, byte lean) {
        static void write(Stoop m, FriendlyByteBuf b) { b.writeVarInt(m.entity); b.writeByte(m.delay); b.writeByte(m.ticks); b.writeByte(m.lean); }
        static Stoop read(FriendlyByteBuf b) { return new Stoop(b.readVarInt(), b.readByte(), b.readByte(), b.readByte()); }
        static void handle(Stoop m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> dev.mcreload.moves.client.StoopFx.stoop(m.entity, m.delay, m.ticks, m.lean)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** What a thrown weapon is (empty: forget it), whose, what it is stuck in (-1: none), where on it and how turned. */
    public record ThrownInfo(int entity, int owner, net.minecraft.world.item.ItemStack item, int stuckIn, float ox, float oy, float oz,
                             float yaw, float pitch) {
        static ThrownInfo of(net.minecraft.world.entity.projectile.ThrownTrident t, dev.mcreload.moves.Thrown.State st) {
            return new ThrownInfo(t.getId(), st.owner, st.item, st.stuckIn, (float) st.offset.x, (float) st.offset.y, (float) st.offset.z,
                    st.yaw, st.pitch);
        }

        static void write(ThrownInfo m, FriendlyByteBuf b) {
            b.writeVarInt(m.entity);
            b.writeVarInt(m.owner + 1);
            b.writeItem(m.item);
            b.writeVarInt(m.stuckIn + 1);
            b.writeFloat(m.ox);
            b.writeFloat(m.oy);
            b.writeFloat(m.oz);
            b.writeFloat(m.yaw);
            b.writeFloat(m.pitch);
        }

        static ThrownInfo read(FriendlyByteBuf b) {
            return new ThrownInfo(b.readVarInt(), b.readVarInt() - 1, b.readItem(), b.readVarInt() - 1, b.readFloat(), b.readFloat(),
                    b.readFloat(), b.readFloat(), b.readFloat());
        }

        static void handle(ThrownInfo m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.mcreload.moves.client.ThrownClient.info(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    private MovesNet() {}
}
