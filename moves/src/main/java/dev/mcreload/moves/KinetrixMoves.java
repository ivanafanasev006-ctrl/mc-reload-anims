package dev.mcreload.moves;

import com.mojang.logging.LogUtils;
import dev.mcreload.moves.net.MovesNet;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Entry point. Combat moves on Kinetrix animations: dodges and rolls on R, the sprint slide on C,
 * the energy they cost and the shields that blocks will spend (docs/combat-spec.md).
 *
 * <p>The server decides: it keeps every player's energy and shields, checks each move and makes
 * the player untouchable or low for its frames. The client plays the move at once (the player's
 * own movement is the client's in Minecraft), draws the bars and shows other players' moves.
 * Both ends need the mod; a client on a server without it simply has no moves.</p>
 */
@Mod(KinetrixMoves.MODID)
public final class KinetrixMoves {
    public static final String MODID = "kinetrix_moves";
    public static final Logger LOG = LogUtils.getLogger();

    public KinetrixMoves() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, MovesConfig.SPEC, MODID + "-server.toml");
        MovesNet.init();
        modBus.addListener(Stamina::registerCapability);
        MinecraftForge.EVENT_BUS.register(Stamina.Events.class);
        MinecraftForge.EVENT_BUS.register(MoveServer.class);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, HudConfig.SPEC, MODID + "-client.toml");
            // Only touched on the client, so these classes (and playerAnimator) never load on a server.
            dev.mcreload.moves.client.MovesClient.init(modBus);
        }
    }
}
