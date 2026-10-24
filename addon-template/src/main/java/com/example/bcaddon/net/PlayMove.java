package com.example.bcaddon.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to clients: play this animation (assets/.../player_animation) on that player's addon layer. */
public record PlayMove(int entityId, ResourceLocation animation) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeResourceLocation(animation);
    }

    public static PlayMove decode(FriendlyByteBuf buf) {
        return new PlayMove(buf.readVarInt(), buf.readResourceLocation());
    }

    public static void handle(PlayMove msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.bcaddon.client.ClientPackets.playMove(msg));
    }
}
