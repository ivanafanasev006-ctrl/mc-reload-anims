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
    /** Better Combat Particle (Malfu) draws its own slash effects for Better Combat attacks. */
    public static boolean bcParticle;
    /** DragN's Livestock Overhaul: big, wide horses (riding pose: see compat.wideMounts). */
    public static boolean livestockOverhaul;

    public static void init() {
        ModList mods = ModList.get();
        betterCombat = mods.isLoaded("bettercombat");
        ironsSpells = mods.isLoaded(IronsSpellsCompat.MODID);
        emotecraft = mods.isLoaded("emotecraft");
        bcParticle = mods.isLoaded("bc_particle") || mods.isLoaded("bettercombatparticle");
        livestockOverhaul = mods.isLoaded("dragnlivestock");

        if (betterCombat) McReloadAnim.LOG.info("[kinetrix] Better Combat detected: attack animations will be blended.");
        if (emotecraft) McReloadAnim.LOG.info("[kinetrix] Emotecraft detected: emotes will be blended.");
        if (livestockOverhaul) McReloadAnim.LOG.info("[kinetrix] DragN's Livestock Overhaul detected: wide-mount riding pose for its horses.");
        IronsSpellsCompat.init();
    }

    private ModCompat() {}
}
