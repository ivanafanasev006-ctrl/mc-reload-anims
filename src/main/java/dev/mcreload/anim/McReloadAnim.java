package dev.mcreload.anim;

import com.mojang.logging.LogUtils;
import dev.mcreload.anim.config.ClientConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;

/**
 * Entry point. Almost everything this mod does is client side: it only changes how players are
 * drawn. On a server (or the host of a LAN game) it also passes animation sync messages between
 * players (emotes, rolls, keys held); it is never required there, and clients without it can join.
 */
@Mod(McReloadAnim.MODID)
public final class McReloadAnim {
    public static final String MODID = "kinetrix";
    public static final Logger LOG = LogUtils.getLogger();

    public McReloadAnim() {
        // Client-only mod: never block joining a server that does not have it.
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, fromServer) -> true));

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // Multiplayer sync channel (both sides; optional on both ends).
        dev.mcreload.anim.net.KinetrixNet.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, MODID + "-client.toml");
            // Only touched on the client, so the class (and playerAnimator) is never loaded on a server.
            dev.mcreload.anim.client.ClientBootstrap.init(modBus);
        }
    }
}
