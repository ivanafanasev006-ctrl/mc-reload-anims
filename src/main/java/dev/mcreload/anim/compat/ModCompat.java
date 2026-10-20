package dev.mcreload.anim.compat;

import dev.mcreload.anim.McReloadAnim;
import net.minecraftforge.fml.ModList;

/**
 * Detection of animation mods we blend with. Better Combat, Emotecraft and the like need no
 * special code: they all drive the same playerAnimator stack, and the harmonizer layer
 * detects and cross-fades whatever they do, body part by body part.
 */
public final class ModCompat {
    public static boolean betterCombat;
    public static boolean ironsSpells;
    public static boolean emotecraft;

    public static void init() {
        ModList mods = ModList.get();
        betterCombat = mods.isLoaded("bettercombat");
        ironsSpells = mods.isLoaded(IronsSpellsCompat.MODID);
        emotecraft = mods.isLoaded("emotecraft");

        if (betterCombat) McReloadAnim.LOG.info("[mcreload] Better Combat detected: attack animations will be blended.");
        if (emotecraft) McReloadAnim.LOG.info("[mcreload] Emotecraft detected: emotes will be blended.");
        IronsSpellsCompat.init();
    }

    private ModCompat() {}
}
