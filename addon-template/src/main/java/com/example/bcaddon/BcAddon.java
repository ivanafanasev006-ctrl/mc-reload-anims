package com.example.bcaddon;

import com.example.bcaddon.net.Net;
import com.mojang.logging.LogUtils;
import net.bettercombat.logic.PlayerAttackHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Template of a Better Combat addon.
 *
 * <p>What is in it, one example of each way an addon hooks into Better Combat:</p>
 * <ul>
 *   <li>{@code combat.BcAttacks}: on the server, tell a Better Combat swing from any other damage and
 *       read which attack of the combo it is (the place for posture, stagger, finishers...).</li>
 *   <li>{@code combat.Guard}: a new action on its own key (a short parry window with the weapon),
 *       validated on the server and shown to everyone through our own animation layer.</li>
 *   <li>{@code combat.Heavy} + {@code mixin.PlayerAttackHelperMixin}: change the attack Better Combat
 *       performs (a held heavy attack: other animation, more damage, slower, longer reach).</li>
 *   <li>{@code data.WeaponExtras}: extra per-weapon data in its own folder, synced to clients.</li>
 *   <li>{@code client.ClientHooks}: Better Combat's client events and attack range extension.</li>
 * </ul>
 */
@Mod(BcAddon.MODID)
public final class BcAddon {
    public static final String MODID = "bcaddon";
    public static final Logger LOG = LogUtils.getLogger();

    public BcAddon() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AddonConfig.SPEC);
        Net.register();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup);
        if (ModList.get().isLoaded("kinetrix")) {
            LOG.info("[bcaddon] Kinetrix found: our animation layer is blended into its jointed body.");
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // Load the Better Combat class our mixin changes now: if a Better Combat update renamed the
        // method, the game stops here with a clear mixin error instead of at the first swing.
        LOG.info("[bcaddon] Hooked into {}", PlayerAttackHelper.class.getName());
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
