package dev.mcreload.anim.client.fx;

import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.client.rig.RigRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.client.event.sound.PlaySoundEvent;

/**
 * Some moments are not visible on the client except through their sound: a shield absorbing a
 * hit, a totem saving someone, an explosion going off. We listen for those sounds and hand the
 * event to the rig of the player it happened to (the nearest one / everyone in the blast).
 */
public final class SoundCues {
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getOriginalSound();
        if (sound == null) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        ResourceLocation id = sound.getLocation();
        double x = sound.getX(), y = sound.getY(), z = sound.getZ();

        if (id.equals(SoundEvents.SHIELD_BLOCK.getLocation())) {
            PlayerRig rig = nearest(level, x, y, z, 2.5, true);
            if (rig != null) rig.motion.onShieldBlock();
        } else if (id.equals(SoundEvents.TOTEM_USE.getLocation())) {
            PlayerRig rig = nearest(level, x, y, z, 2.5, false);
            if (rig != null) rig.motion.onTotem();
        } else if (id.equals(SoundEvents.GENERIC_EXPLODE.getLocation())) {
            for (AbstractClientPlayer p : level.players()) {
                PlayerRig rig = RigRegistry.get(p);
                if (rig == null) continue;
                rig.motion.onExplosion(Math.sqrt(p.distanceToSqr(x, y, z)));
            }
        }
    }

    private static PlayerRig nearest(ClientLevel level, double x, double y, double z, double max, boolean mustBlock) {
        PlayerRig best = null;
        double bestD = max * max;
        for (AbstractClientPlayer p : level.players()) {
            if (mustBlock && !p.isBlocking()) continue;
            double d = p.distanceToSqr(x, y, z);
            if (d <= bestD) {
                PlayerRig rig = RigRegistry.get(p);
                if (rig != null) {
                    best = rig;
                    bestD = d;
                }
            }
        }
        return best;
    }

    private SoundCues() {}
}
