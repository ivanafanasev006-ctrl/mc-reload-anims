package com.example.bcaddon.client;

import com.example.bcaddon.BcAddon;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/** Mod-bus client setup: keys, our animation layer, the Better Combat client hooks. */
@Mod.EventBusSubscriber(modid = BcAddon.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    public static final String CATEGORY = "key.categories.bcaddon";
    public static final KeyMapping GUARD = new KeyMapping("key.bcaddon.guard", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping HEAVY = new KeyMapping("key.bcaddon.heavy", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(GUARD);
        event.register(HEAVY);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Setup events run on worker threads, and Better Combat's event lists are plain ArrayLists.
        event.enqueueWork(() -> {
            MoveLayer.register();
            ClientHooks.register();
        });
    }

    private ClientSetup() {}
}
