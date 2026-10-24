package com.example.bcaddon.net;

import com.example.bcaddon.data.WeaponExtras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

/** Server to client: the weapon_extras files, sent on login and after /reload. */
public record SyncExtras(Map<ResourceLocation, String> files) {
    private static final int MAX_FILE = 1 << 18;

    public void encode(FriendlyByteBuf buf) {
        buf.writeMap(files, FriendlyByteBuf::writeResourceLocation, (b, text) -> b.writeUtf(text, MAX_FILE));
    }

    public static SyncExtras decode(FriendlyByteBuf buf) {
        return new SyncExtras(buf.readMap(FriendlyByteBuf::readResourceLocation, b -> b.readUtf(MAX_FILE)));
    }

    public static void handle(SyncExtras msg, Supplier<NetworkEvent.Context> ctx) {
        WeaponExtras.load(msg.files());
    }
}
