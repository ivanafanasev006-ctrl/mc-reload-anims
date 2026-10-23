package dev.mcreload.anim.client.rig;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

public final class Weapons {
    /**
     * Swords, axes, tridents, plus any modded item that adds attack damage and is not a mining
     * tool (covers most modded swords, daggers, spears, scythes...).
     */
    public static boolean isMeleeWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem) return true;
        if (item instanceof DiggerItem) return false;
        try {
            return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE);
        } catch (Throwable t) {
            return false;
        }
    }

    /** A sword (or a modded blade like one): a melee weapon that is not an axe or a trident. */
    public static boolean isSword(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof AxeItem || item instanceof TridentItem) return false;
        return isMeleeWeapon(stack);
    }

    private Weapons() {}
}
