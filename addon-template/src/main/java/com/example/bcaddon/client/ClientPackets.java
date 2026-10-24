package com.example.bcaddon.client;

import com.example.bcaddon.net.PlayMove;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

/** Client handlers of server packets (kept apart so the server never loads client classes). */
public final class ClientPackets {

    public static void playMove(PlayMove msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(msg.entityId()) instanceof AbstractClientPlayer player) {
            MoveLayer.play(player, msg.animation());
        }
    }

    private ClientPackets() {}
}
