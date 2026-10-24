package com.example.bcaddon.combat;

import com.example.bcaddon.BcAddon;
import com.example.bcaddon.data.WeaponExtras;
import com.example.bcaddon.data.WeaponExtrasLoader;
import com.example.bcaddon.net.Net;
import com.example.bcaddon.net.SyncExtras;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Forge events on both sides (the handlers return early where they do not apply). */
@Mod.EventBusSubscriber(modid = BcAddon.MODID)
public final class CommonEvents {

    @SubscribeEvent
    public static void onReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new WeaponExtrasLoader());
    }

    /** On login (one player) and after /reload (everyone). */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        SyncExtras packet = new SyncExtras(WeaponExtras.files());
        ServerPlayer player = event.getPlayer();
        if (player != null) Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        else Net.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    /** Login, respawn and dimension change all give a fresh or re-added player entity. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            BcAttacks.resetCombo(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Guard.forget(event.getEntity().getUUID());
        Heavy.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        Guard.onAttacked(event);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        BcAttacks.onHurt(event);
    }

    private CommonEvents() {}
}
