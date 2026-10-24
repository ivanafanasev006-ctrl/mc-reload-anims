package com.example.bcaddon.net;

import com.example.bcaddon.combat.Guard;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: the guard key was pressed. The server decides whether the guard goes up. */
public record GuardRequest() {
    public void encode(FriendlyByteBuf buf) {}

    public static GuardRequest decode(FriendlyByteBuf buf) {
        return new GuardRequest();
    }

    public static void handle(GuardRequest msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) Guard.request(player);
    }
}
