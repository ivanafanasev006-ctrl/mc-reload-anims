package dev.mcreload.moves;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * The fast run (author, 2026-10-29, fifth message): Ctrl held while sprinting, the player runs at
 * 7 blocks a second (vanilla's sprint is 5.6; almost as with a potion of Swiftness I) and it eats a
 * hundredth of the energy bar a second, the bar not refilling meanwhile. With no energy left it is
 * the vanilla sprint again. The client says whether its key is held (MovesNet.Run); the server
 * gives the speed (an attribute, so the client moves with it too) and takes the energy.
 */
public final class FastRun {
    private static final UUID ID = UUID.fromString("7c1f3a52-3d0e-4f7b-9c55-1a2b6e4d8f01");
    /** Vanilla's sprint on flat ground, blocks a second (movement speed 0.13 on stone, grass, dirt). */
    public static final double VANILLA_SPRINT = 5.612;

    /** Every server tick, after the bars were refilled. */
    static void serverTick(ServerPlayer p, Stamina s) {
        boolean on = s.runKey && p.isSprinting() && !p.isPassenger() && !p.isInWater() && !p.isSwimming()
                && !p.isFallFlying() && !p.getAbilities().flying && s.hasEnergy(p);
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        AttributeModifier now = speed.getModifier(ID);
        double amount = MovesConfig.fastRunSpeed() / VANILLA_SPRINT - 1.0;
        if (on) {
            if (now == null || now.getAmount() != amount) {
                if (now != null) speed.removeModifier(ID);
                speed.addTransientModifier(new AttributeModifier(ID, "Kinetrix Moves fast run", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            // a move carries the player itself: the run eats energy only while it is the run
            if (s.move == null) s.spend(MovesConfig.maxEnergy() * MovesConfig.fastRunBarPerSecond() / 20f, p);
        } else if (now != null) {
            speed.removeModifier(ID);
        }
        s.fastRunning = on;
    }

    private FastRun() {}
}
