package com.example.bcaddon.net;

import com.example.bcaddon.combat.Heavy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: the heavy attack key went down or up. */
public record HeavyHold(boolean held) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(held);
    }

    public static HeavyHold decode(FriendlyByteBuf buf) {
        return new HeavyHold(buf.readBoolean());
    }

    public static void handle(HeavyHold msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) Heavy.setServer(player, msg.held());
    }
}
