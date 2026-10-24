package com.example.bcaddon.combat;

import com.example.bcaddon.AddonConfig;
import com.example.bcaddon.BcAddon;
import com.example.bcaddon.data.WeaponExtras;
import com.example.bcaddon.net.Net;
import com.example.bcaddon.net.PlayMove;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Weapon guard: a key raises the weapon for a few ticks; a melee hit from the front in that window
 * is parried (no damage, the attacker is pushed back and staggered).
 *
 * <p>Only without a shield in use: blocking and parrying with shields belongs to vanilla and to
 * Shield Expansion (which the Otherworld pack has), so the two never fight over the same hit.</p>
 */
public final class Guard {
    public static final ResourceLocation ANIMATION = BcAddon.id("guard");

    private record State(long windowEnd, long cooldownEnd) {}

    private static final Map<UUID, State> STATE = new ConcurrentHashMap<>();

    public static void request(ServerPlayer player) {
        ItemStack weapon = player.getMainHandItem();
        if (WeaponRegistry.getAttributes(weapon) == null || player.isUsingItem() || player.isSpectator()) return;
        long now = player.level().getGameTime();
        State old = STATE.get(player.getUUID());
        if (old != null && now < old.cooldownEnd()) return;
        WeaponExtras extras = WeaponExtras.of(weapon);
        int window = extras != null ? extras.guardWindow() : AddonConfig.GUARD_TICKS.get();
        if (window <= 0) return;
        STATE.put(player.getUUID(), new State(now + window, now + AddonConfig.GUARD_COOLDOWN.get()));
        Net.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new PlayMove(player.getId(), ANIMATION));
    }

    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isBlocking()) return;
        State state = STATE.get(player.getUUID());
        if (state == null || player.level().getGameTime() >= state.windowEnd()) return;
        DamageSource source = event.getSource();
        if (!(source.getDirectEntity() instanceof LivingEntity attacker) || source.getEntity() != attacker
                || source.is(DamageTypeTags.BYPASSES_SHIELD)) return;
        Vec3 look = player.getViewVector(1f).multiply(1, 0, 1).normalize();
        Vec3 toAttacker = attacker.position().subtract(player.position()).multiply(1, 0, 1).normalize();
        if (look.dot(toAttacker) < 0.3) return; // from the side or behind

        event.setCanceled(true);
        STATE.put(player.getUUID(), new State(0, state.cooldownEnd())); // one parry per guard
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 1.5f);
        attacker.knockback(0.6, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
        attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
        attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 0));
    }

    public static void forget(UUID player) {
        STATE.remove(player);
    }

    private Guard() {}
}
