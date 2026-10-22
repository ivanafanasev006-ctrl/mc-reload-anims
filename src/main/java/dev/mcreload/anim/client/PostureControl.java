package dev.mcreload.anim.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mcreload.anim.client.net.ClientSync;
import dev.mcreload.anim.compat.PersonalityCompat;
import dev.mcreload.anim.config.Cfg;
import dev.mcreload.anim.net.AnimPacket;
import dev.mcreload.anim.net.Posture;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Your own crawl (C) and sit (X).
 *
 * <p>Crawling forces the vanilla crawl pose on (one block high, fits through one-block gaps); the
 * server must agree, so it needs Kinetrix there. Sitting works anywhere: with Kinetrix on the
 * server the hitbox and the eyes go down too and everyone sees you sit.</p>
 */
public final class PostureControl {
    public static final KeyMapping CRAWL = key("key.kinetrix.crawl", GLFW.GLFW_KEY_C);
    public static final KeyMapping SIT = key("key.kinetrix.sit", GLFW.GLFW_KEY_X);

    private static KeyMapping key(String name, int code) {
        // Personality has the same keys: leave ours unbound then (they can still be set in Controls).
        return PersonalityCompat.LOADED
                ? new KeyMapping(name, KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, "key.categories.kinetrix")
                : new KeyMapping(name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, "key.categories.kinetrix");
    }

    private static LocalPlayer lastPlayer;
    /** Toggle mode: crawl wanted. */
    private static boolean crawlToggled;
    /** We forced the crawl pose on. */
    private static boolean crawling;
    /** 0 = standing, 1..3 = sitting in that style. */
    private static int sit;
    private static boolean sitShared;
    /** Sit pressed a moment ago while it could not start yet (landing from a hop): sit when possible. */
    private static int sitQueued;
    /** Presses made by the self-test (it cannot type on the keyboard). */
    private static boolean testCrawl, testSit;

    /** Self-test: press the crawl or the sit key once. */
    public static void testPress(boolean crawl) {
        if (crawl) testCrawl = true; else testSit = true;
    }
    private static final java.util.Random RNG = new java.util.Random();

    /** Your sitting style (0 = not sitting), for your own rig. */
    public static int localSit() {
        return sit;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p != lastPlayer) {
            // New world, respawn or other dimension: a fresh player entity, nothing carries over.
            lastPlayer = p;
            crawlToggled = false;
            crawling = false;
            sit = 0;
            sitShared = false;
        }
        boolean crawlPressed = false, sitPressed = false;
        while (CRAWL.consumeClick()) crawlPressed = true;
        while (SIT.consumeClick()) sitPressed = true;
        // Vanilla binds C and X too (saving / loading hotbars), and a key shared by two bindings
        // may reach only one of them: read the keyboard as well.
        boolean crawlDown = held(mc, CRAWL), sitDown = held(mc, SIT);
        if (crawlDown && !crawlWasDown) crawlPressed = true;
        if (sitDown && !sitWasDown) sitPressed = true;
        crawlWasDown = crawlDown;
        sitWasDown = sitDown;
        if (testCrawl) { crawlPressed = true; testCrawl = false; }
        if (testSit) { sitPressed = true; testSit = false; }
        if (p == null) return;
        boolean server = ClientSync.serverHasKinetrix();

        // ---- crawl
        if (Cfg.toggleCrawl) {
            if (crawlPressed) crawlToggled = !crawlToggled;
        } else {
            crawlToggled = crawlDown;
        }
        boolean wantCrawl = crawlToggled;
        if (wantCrawl && !server) {
            if (crawlPressed || !crawling) {
                p.displayClientMessage(Component.translatable("message.kinetrix.crawl_needs_server"), true);
            }
            wantCrawl = crawlToggled = false;
        }
        if (wantCrawl && !Posture.canCrawl(p)) wantCrawl = crawlToggled = false;
        // Jumping gets you back up (if there is room; otherwise vanilla keeps you crawling anyway).
        if (wantCrawl && crawling && Cfg.toggleCrawl && p.input != null && p.input.jumping) wantCrawl = crawlToggled = false;
        if (wantCrawl && sit > 0) setSit(p, 0, server);
        if (wantCrawl != crawling) {
            crawling = wantCrawl;
            if (wantCrawl) p.setForcedPose(Pose.SWIMMING);
            else if (p.getForcedPose() == Pose.SWIMMING) p.setForcedPose(null);
            ClientSync.posture(AnimPacket.CRAWL, wantCrawl ? 1 : 0);
        }

        // ---- sit
        boolean wantSit;
        if (Cfg.toggleSit) wantSit = sitPressed ? sit == 0 : sit > 0;
        else wantSit = sitDown;
        if (sitQueued > 0) {
            sitQueued = moving(p) ? 0 : sitQueued - 1;
            if (sitQueued > 0 && sit == 0 && !sitPressed) wantSit = true;
        }
        if (crawling || p.isVisuallyCrawling()) wantSit = false;
        if (wantSit && sit == 0) {
            double h = p.getDeltaMovement().horizontalDistanceSqr();
            boolean able = Posture.canSit(p) && h <= 0.003 && !moving(p);
            // Pressed in the air (a little hop, just teleported): sit as soon as we land.
            if (!able && sitPressed && Cfg.toggleSit && !p.onGround() && !moving(p)) sitQueued = 12;
            if (!able) wantSit = false;
            else sitQueued = 0;
        }
        if (sit > 0 && (moving(p) || !Posture.canKeepSitting(p) || p.isShiftKeyDown())) wantSit = false;
        if (wantSit && sit == 0) setSit(p, 1 + RNG.nextInt(Posture.SIT_STYLES), server);
        else if (!wantSit && sit > 0) setSit(p, 0, server);
        // The server learnt about Kinetrix late (or we joined while sitting): share it now.
        if (sit > 0 && server && !sitShared) setSit(p, sit, true);
    }

    private static boolean crawlWasDown, sitWasDown;

    /** The key is held: through its binding, or read straight from the keyboard (no screen open). */
    private static boolean held(Minecraft mc, KeyMapping km) {
        if (km.isDown()) return true;
        if (mc.screen != null || km.isUnbound()) return false;
        InputConstants.Key k = km.getKey();
        if (k.getType() != InputConstants.Type.KEYSYM || k.getValue() < 0) return false;
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), k.getValue());
    }

    private static boolean moving(LocalPlayer p) {
        return p.input != null && (Math.abs(p.input.forwardImpulse) > 0.05f || Math.abs(p.input.leftImpulse) > 0.05f
                || p.input.jumping);
    }

    private static void setSit(LocalPlayer p, int style, boolean server) {
        sit = style;
        // The hitbox only changes when the server changes it too (otherwise movement would be refused).
        boolean changed = style > 0 && server ? Posture.CLIENT_SIT.add(p.getUUID()) : Posture.CLIENT_SIT.remove(p.getUUID());
        if (changed) p.refreshDimensions();
        if (server) ClientSync.posture(AnimPacket.SIT, style);
        sitShared = server;
    }

    /** The server ended a crawl or stood us up (or refused). */
    public static void serverReset(byte type) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (type == AnimPacket.CRAWL && crawling) {
            crawling = crawlToggled = false;
            if (p.getForcedPose() == Pose.SWIMMING) p.setForcedPose(null);
        } else if (type == AnimPacket.SIT && sit > 0) {
            sit = 0;
            if (Posture.CLIENT_SIT.remove(p.getUUID())) p.refreshDimensions();
        }
    }

    private PostureControl() {}
}
