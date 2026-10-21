package dev.mcreload.anim.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.mcreload.anim.McReloadAnim;
import dev.mcreload.anim.client.fx.WeaponTrails;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.client.rig.RigRegistry;
import dev.mcreload.anim.client.selftest.SelfTest;
import dev.mcreload.anim.compat.ModCompat;
import dev.mcreload.anim.config.Cfg;
import dev.mcreload.anim.config.ClientConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.config.ModConfigEvent;

public final class ClientBootstrap {
    public static final KeyMapping TOGGLE = new KeyMapping(
            "key.kinetrix.toggle", KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, "key.categories.kinetrix");

    public static final KeyMapping EMOTES = new KeyMapping(
            "key.kinetrix.emotes", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            org.lwjgl.glfw.GLFW.GLFW_KEY_B, "key.categories.kinetrix");

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientBootstrap::onRegisterKeys);
        modBus.addListener(ClientBootstrap::onConfigLoad);
        modBus.addListener(ClientBootstrap::onConfigReload);
        MinecraftForge.EVENT_BUS.addListener(ClientBootstrap::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(WeaponTrails::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(dev.mcreload.anim.client.fx.IdleProps::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(dev.mcreload.anim.client.fx.SleepZzz::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(dev.mcreload.anim.client.fx.SoundCues::onPlaySound);

        ModCompat.init();
        if (SelfTest.enabled()) SelfTest.init();

        // Every client-side player (you and everyone you can see) gets its own rig: our
        // procedural layer at the bottom of the animation stack and the harmonizer on top.
        PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register(ClientBootstrap::attach);
        McReloadAnim.LOG.info("[kinetrix] Player animations registered with playerAnimator.");
    }

    private static void attach(AbstractClientPlayer player, AnimationStack stack) {
        PlayerRig rig = new PlayerRig(player);
        RigRegistry.put(player, rig);
        stack.addAnimLayer(PlayerRig.BASE_PRIORITY, rig.baseLayer());
        stack.addAnimLayer(PlayerRig.HARMONIZER_PRIORITY, rig.harmonizerLayer());
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(EMOTES);
    }

    private static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ClientConfig.SPEC) Cfg.refresh();
    }

    private static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ClientConfig.SPEC) Cfg.refresh();
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Cfg.refresh();
        while (EMOTES.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null) mc.setScreen(new EmoteScreen());
        }
        while (TOGGLE.consumeClick()) {
            boolean enabled = !ClientConfig.ENABLED.get();
            ClientConfig.ENABLED.set(enabled);
            ClientConfig.ENABLED.save();
            Cfg.refresh();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable(
                        enabled ? "message.kinetrix.enabled" : "message.kinetrix.disabled"), true);
            }
        }
    }

    private ClientBootstrap() {}
}
