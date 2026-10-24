package dev.mcreload.anim.compat;

import net.minecraft.world.item.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Better Combat (read through reflection, no dependency): does it own the attacks of this item?
 * Then its attack animation (or a pack such as Malfu Combat Animation) is the one that plays, and
 * Kinetrix does not start its own sword/axe strike underneath it.
 */
public final class BetterCombatCompat {
    private static MethodHandle getAttributes, isTwoHanded;
    private static boolean lookedUp;

    public static boolean handles(ItemStack stack) {
        if (!ModCompat.betterCombat || stack == null || stack.isEmpty()) return false;
        try {
            if (!lookedUp) {
                lookedUp = true;
                Class<?> reg = Class.forName("net.bettercombat.logic.WeaponRegistry");
                Class<?> attrs = Class.forName("net.bettercombat.api.WeaponAttributes");
                getAttributes = MethodHandles.publicLookup().findStatic(reg, "getAttributes",
                        MethodType.methodType(attrs, ItemStack.class));
                try {
                    isTwoHanded = MethodHandles.publicLookup().findVirtual(attrs, "isTwoHanded", MethodType.methodType(boolean.class));
                } catch (Throwable t) {
                    isTwoHanded = null;
                }
            }
            return getAttributes != null && getAttributes.invoke(stack) != null;
        } catch (Throwable t) {
            getAttributes = null;
            return false;
        }
    }

    /**
     * A two-handed weapon by Better Combat's rules (nothing may be used in the other hand): it is
     * held with both hands all the time, so Kinetrix does not take a hand off it to fidget or
     * strike a guard.
     */
    public static boolean twoHanded(ItemStack stack) {
        if (!handles(stack) || isTwoHanded == null) return false;
        try {
            return (boolean) isTwoHanded.invoke(getAttributes.invoke(stack));
        } catch (Throwable t) {
            return false;
        }
    }

    private BetterCombatCompat() {}
}
