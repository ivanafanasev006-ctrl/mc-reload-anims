package dev.mcreload.anim.compat;

import dev.mcreload.anim.McReloadAnim;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * ParCool animates its own parkour moves (vaults, wall runs, rolls, dodges, slides, clinging to a
 * ledge, its fast run...): it changes the player model at the end of setupAnim, or skips setupAnim
 * altogether for the move. While it plays one of them on a player, Kinetrix fades its own motion
 * out for that player instead of mixing the two (see MotionState#parkourW), and comes back when
 * the move is over. Read through reflection: ParCool is not needed to build or run Kinetrix.
 */
public final class ParCoolCompat {
    public static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("parcool");

    private static MethodHandle get, hasAnimator;
    private static boolean failed;

    /** ParCool is playing one of its own move animations on this player. */
    public static boolean animating(Player player) {
        if (!LOADED || failed) return false;
        try {
            if (get == null) {
                Class<?> c = Class.forName("com.alrex.parcool.common.capability.Animation");
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                hasAnimator = lookup.findVirtual(c, "hasAnimator", MethodType.methodType(boolean.class));
                get = lookup.findStatic(c, "get", MethodType.methodType(c, Player.class));
            }
            Object animation = get.invoke(player);
            return animation != null && (boolean) hasAnimator.invoke(animation);
        } catch (Throwable t) {
            failed = true;
            McReloadAnim.LOG.warn("[kinetrix] ParCool is installed but its animation state could not be read;"
                    + " Kinetrix will not step aside for its moves.", t);
            return false;
        }
    }

    private ParCoolCompat() {}
}
