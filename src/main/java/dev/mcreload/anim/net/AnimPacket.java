package dev.mcreload.anim.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * One piece of animation state of a player. Client to server: "this is me"; server to the
 * clients that can see that player: "this is player {@code entityId}".
 *
 * <p>Kept tiny on purpose (a few bytes): input flags change a few times a second at most, the
 * rest are one-off events.</p>
 */
public record AnimPacket(int entityId, byte type, int value) {
    /** Held keys and what the player is aiming at, see {@link #BIT_FORWARD} etc. */
    public static final byte STATE = 0;
    /** Emote started (value = emote index) or stopped (-1). */
    public static final byte EMOTE = 1;
    /** Idle fidget started (value = fidget index). */
    public static final byte IDLE = 2;
    /** A special landing: forward roll, back roll, sword plant (value = kind). */
    public static final byte LAND = 3;
    public static final byte TYPES = 4;

    public static final int BIT_FORWARD = 1, BIT_BACK = 2, BIT_LEFT = 4, BIT_RIGHT = 8, BIT_JUMP = 16,
            BIT_SNEAK = 32, BIT_USE = 64, BIT_TARGET_BLOCK = 128, BIT_ATTACK = 256;

    public static void encode(AnimPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entityId);
        buf.writeByte(p.type);
        buf.writeVarInt(p.value + 1);
    }

    public static AnimPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        byte type = buf.readByte();
        int value = buf.readVarInt() - 1;
        return new AnimPacket(id, type, value);
    }

    /** Sane ranges only (a modified client must not be able to send garbage to everyone). */
    public boolean valid() {
        if (type < 0 || type >= TYPES) return false;
        return switch (type) {
            case STATE -> value >= 0 && value < 1 << 12;
            case EMOTE -> value >= -1 && value < 64;
            case IDLE -> value >= -1 && value < 64;
            case LAND -> value >= 0 && value < 8;
            default -> false;
        };
    }

    public static void handle(AnimPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        if (c.getDirection().getReceptionSide().isServer()) {
            KinetrixNet.relay(c.getSender(), p);
        } else {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.mcreload.anim.client.net.ClientSync.receive(p));
        }
    }
}
