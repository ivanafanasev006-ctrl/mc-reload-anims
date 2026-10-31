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
    public static final String VERSION = "7";
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

    public static void deny(ServerPlayer p, Move move) {
        if (connected(p)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Deny((byte) move.ordinal()));
    }

    public static void stamina(ServerPlayer p, Stamina s) {
        if (connected(p)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new Bars(s.energy, s.shields, dev.mcreload.moves.MovesConfig.maxEnergy(), dev.mcreload.moves.MovesConfig.maxShields(),
                        s.freeTicks, s.freeTotal, new int[]{s.dashCooldown, s.whirlCooldown, s.spinCooldown, s.slideAttackCooldown,
                        s.markedCooldown, s.bowCooldown}));
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

    /** Server -> client: the player's own bars. */
    /**
     * The bars, and the ticks left of the cooldowns (the tray): the dash, the whirlwind, the sword's
     * turn, the attack out of a slide, the volley at the marked, the bow's G.
     */
    public record Bars(float energy, float shields, float maxEnergy, float maxShields, int freeTicks, int freeTotal, int[] cooldowns) {
        public static final int COOLDOWNS = 6;

        static void write(Bars m, FriendlyByteBuf b) {
            b.writeFloat(m.energy);
            b.writeFloat(m.shields);
            b.writeFloat(m.maxEnergy);
            b.writeFloat(m.maxShields);
            b.writeVarInt(m.freeTicks);
            b.writeVarInt(m.freeTotal);
            for (int i = 0; i < COOLDOWNS; i++) b.writeVarInt(Math.max(0, m.cooldowns[i]));
        }
        static Bars read(FriendlyByteBuf b) {
            float energy = b.readFloat(), shields = b.readFloat(), maxEnergy = b.readFloat(), maxShields = b.readFloat();
            int freeTicks = b.readVarInt(), freeTotal = b.readVarInt();
            int[] cooldowns = new int[COOLDOWNS];
            for (int i = 0; i < COOLDOWNS; i++) cooldowns[i] = b.readVarInt();
            return new Bars(energy, shields, maxEnergy, maxShields, freeTicks, freeTotal, cooldowns);
        }
        static void handle(Bars m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> dev.mcreload.moves.client.MovesClient.bars(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: the mark on this mob made or taken off (Marks.toggle); -1: all of them taken off. */
    public record Mark(int entity) {
        static void write(Mark m, FriendlyByteBuf b) { b.writeVarInt(m.entity + 1); }
        static Mark read(FriendlyByteBuf b) { return new Mark(b.readVarInt() - 1); }
        static void handle(Mark m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) ctx.get().enqueueWork(() -> {
                dev.mcreload.moves.Stamina s = dev.mcreload.moves.Stamina.of(p);
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

    private MovesNet() {}
}
