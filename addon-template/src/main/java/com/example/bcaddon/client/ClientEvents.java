package com.example.bcaddon.client;

import com.example.bcaddon.BcAddon;
import com.example.bcaddon.combat.Heavy;
import com.example.bcaddon.net.GuardRequest;
import com.example.bcaddon.net.HeavyHold;
import com.example.bcaddon.net.Net;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus client events: our keys each tick. */
@Mod.EventBusSubscriber(modid = BcAddon.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean resourcesChecked;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (!resourcesChecked && mc.getOverlay() == null) {
            resourcesChecked = true;
            MoveLayer.logLoaded();
        }
        if (mc.player == null || mc.getConnection() == null) return;

        boolean heavy = ClientSetup.HEAVY.isDown() && mc.screen == null;
        if (heavy != Heavy.localHeld()) {
            Heavy.setLocal(heavy);
            Net.CHANNEL.sendToServer(new HeavyHold(heavy));
        }

        while (ClientSetup.GUARD.consumeClick()) {
            // Raising the guard interrupts a swing that is still winding up (Better Combat's client API).
            ((MinecraftClient_BetterCombat) mc).cancelUpswing();
            Net.CHANNEL.sendToServer(new GuardRequest());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Heavy.setLocal(false);
    }

    private ClientEvents() {}
}
