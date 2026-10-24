package dev.mcreload.anim.client;

import dev.mcreload.anim.client.rig.MotionState;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;

/** What your player does while you are in a menu (MotionState.SCREEN_*). */
public final class ScreenPoses {
    /**
     * Your own inventory, the crafting table, the pause menu (and the menus behind it) and other
     * mods' menus: thinking it over. Chests, barrels, furnaces and any other container, modded
     * ones too: rummaging in it. Trading: talking with the villager. Typing in the chat, the
     * emote menu, writing and dying: nothing.
     */
    public static int of(Screen screen) {
        if (screen == null) return MotionState.SCREEN_NONE;
        if (screen instanceof MerchantScreen) return MotionState.SCREEN_TRADE;
        if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen
                || screen instanceof CraftingScreen) {
            return MotionState.SCREEN_THINK;
        }
        if (screen instanceof AbstractContainerScreen<?>) return MotionState.SCREEN_RUMMAGE;
        if (screen instanceof ChatScreen || screen instanceof EmoteScreen || screen instanceof DeathScreen
                || screen instanceof dev.mcreload.anim.client.gallery.GalleryScreen
                || screen instanceof BookEditScreen
                || screen instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
                || screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen
                || screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen
                || screen instanceof net.minecraft.client.gui.screens.ProgressScreen
                || screen instanceof net.minecraft.client.gui.screens.GenericDirtMessageScreen
                || screen instanceof net.minecraft.client.gui.screens.WinScreen) {
            return MotionState.SCREEN_NONE;
        }
        return MotionState.SCREEN_THINK;
    }

    private ScreenPoses() {}
}
