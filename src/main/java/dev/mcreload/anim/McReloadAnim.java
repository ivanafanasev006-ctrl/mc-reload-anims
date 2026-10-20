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
 * Entry point. Everything this mod does is client side: it only changes how players are drawn.
 * The mod is therefore safe to have on a server (it does nothing there) and is not required
 * on the server for clients to use it.
 */
@Mod(McReloadAnim.MODID)
public final class McReloadAnim {
    public static final String MODID = "mcreload";
    public static final Logger LOG = LogUtils.getLogger();

    public McReloadAnim() {
        // Client-only mod: never block joining a server that does not have it.
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, fromServer) -> true));

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, MODID + "-client.toml");
            // Only touched on the client, so the class (and playerAnimator) is never loaded on a server.
            dev.mcreload.anim.client.ClientBootstrap.init(modBus);
        }
    }
}
