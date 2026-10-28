package dev.mcreload.moves.client;

import dev.mcreload.moves.Guard;
import dev.mcreload.moves.MovesConfig;
import dev.mcreload.moves.Stamina;
import dev.mcreload.moves.net.MovesNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * The sword block on the player's side: right mouse button with a sword raises the guard (when
 * the click is not taken by a chest, a door, a villager...), holding it keeps the guard up,
 * letting go, attacking or dodging lowers it. The server decides what it blocks (Guard).
 */
public final class GuardInput {
    /** The right-click went to the sword this tick (nothing else took it). */
    private static boolean clicked;
    /** The server lowered our guard (no shields left...): not again until the button is let go. */
    private static boolean waitRelease;

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, true, GuardInput::rightClick);
        MinecraftForge.EVENT_BUS.addListener(GuardInput::clientTick);
        MinecraftForge.EVENT_BUS.addListener(GuardInput::movementInput);
    }

    private static void rightClick(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide && event.getHand() == InteractionHand.MAIN_HAND
                && event.getEntity() == Minecraft.getInstance().player) {
            clicked = true;
        }
    }

    private static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        boolean click = clicked;
        clicked = false;
        if (p == null) return;
        Stamina s = Stamina.of(p);
        boolean held = mc.options.keyUse.isDown() && mc.screen == null;
        if (!held) waitRelease = false;
        if (s.guard == Guard.Kind.NONE) {
            if (click && held && !waitRelease && MovesClient.serverHasMod() && Guard.allowed(p, s)) raise(p, s);
            return;
        }
        // Up: keep it while the button is held and the hands still allow it; a hit lowers it.
        if (!held || mc.options.keyAttack.isDown() || !Guard.allowed(p, s) || Guard.kindFor(p) != s.guard) lowerLocal(p, s, true);
        else p.setSprinting(false);
    }

    private static void raise(LocalPlayer p, Stamina s) {
        s.guard = Guard.kindFor(p);
        s.guardTick = 0;
        MoveAnimator.playGuard(p, s.guard);
        MovesNet.CHANNEL.sendToServer(new MovesNet.GuardUp(true));
    }

    /** Lowers our own guard (tell: let the server know). Also used when a dodge starts. */
    static void lowerLocal(LocalPlayer p, Stamina s, boolean tell) {
        if (s.guard == Guard.Kind.NONE) return;
        s.guard = Guard.Kind.NONE;
        s.guardTick = 0;
        MoveAnimator.stop(p, 3);
        if (tell) MovesNet.CHANNEL.sendToServer(new MovesNet.GuardUp(false));
    }

    /** Guarding, the player walks slowly (like using an item) and does not sprint. */
    private static void movementInput(MovementInputUpdateEvent event) {
        Stamina s = Stamina.of(event.getEntity());
        if (s.guard == Guard.Kind.NONE) return;
        Input in = event.getInput();
        float k = MovesConfig.guardMoveFactor();
        in.forwardImpulse *= k;
        in.leftImpulse *= k;
    }

    /** The server says how a player guards now (for us: it may have lowered our guard). */
    public static void remote(int entityId, Guard.Kind kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(entityId);
        if (!(e instanceof AbstractClientPlayer p)) return;
        Stamina s = Stamina.of(p);
        if (p == mc.player) {
            if (kind == Guard.Kind.NONE && s.guard != Guard.Kind.NONE) {
                lowerLocal(mc.player, s, false);
                waitRelease = true;
            }
            return;
        }
        if (kind == s.guard) return;
        s.guard = kind;
        s.guardTick = 0;
        if (kind == Guard.Kind.NONE) MoveAnimator.stop(p, 3);
        else MoveAnimator.playGuard(p, kind);
    }

    private GuardInput() {}
}
