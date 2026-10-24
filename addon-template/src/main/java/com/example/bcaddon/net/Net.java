package com.example.bcaddon.net;

import com.example.bcaddon.BcAddon;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Our own channel. Better Combat's packets are not extended: an addon sends its own next to them
 * (on the same connection, so they arrive in the order they were sent).
 */
public final class Net {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            BcAddon.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(GuardRequest.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GuardRequest::encode).decoder(GuardRequest::decode).consumerMainThread(GuardRequest::handle).add();
        CHANNEL.messageBuilder(HeavyHold.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HeavyHold::encode).decoder(HeavyHold::decode).consumerMainThread(HeavyHold::handle).add();
        CHANNEL.messageBuilder(PlayMove.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlayMove::encode).decoder(PlayMove::decode).consumerMainThread(PlayMove::handle).add();
        CHANNEL.messageBuilder(SyncExtras.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncExtras::encode).decoder(SyncExtras::decode).consumerMainThread(SyncExtras::handle).add();
    }

    private Net() {}
}
