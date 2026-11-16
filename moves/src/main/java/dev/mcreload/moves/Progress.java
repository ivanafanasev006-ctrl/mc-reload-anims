package dev.mcreload.moves;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * What a player has learned (author, 2026-11-16), kept with the bars (Stamina) and through death: the skills read from
 * scrolls, the wheel of {@link Skill#WHEEL} of them (only those work), and the bar's rank: copper at the start (every
 * move and the run costing MovesConfig.copperCost times as much), bronze, then brass (as it was), each from a scroll the
 * trainer gives for his chain of tasks, one after another.
 */
public final class Progress {
    public static final int COPPER = 0, BRONZE = 1, BRASS = 2;
    /** The client self-test plays every move: everything usable, the brass bar. */
    private static final boolean ALL = Boolean.getBoolean("kinetrix_combat.selftest");

    public final EnumSet<Skill> learned = EnumSet.noneOf(Skill.class);
    public final Skill[] wheel = new Skill[Skill.WHEEL];
    public int rank = COPPER;
    /** Every skill usable (test players; not saved). */
    public boolean all;

    /** This skill works now: learned and on the wheel. */
    public boolean usable(Skill k) {
        return k == null || all || ALL || (learned.contains(k) && equipped(k));
    }

    public boolean equipped(Skill k) {
        for (Skill w : wheel) if (w == k) return true;
        return false;
    }

    /** Learns it (true if it was new) and puts it on the wheel's first free place, if there is one. */
    public boolean learn(Skill k) {
        if (!learned.add(k)) return false;
        for (int i = 0; i < wheel.length; i++) {
            if (wheel[i] == null) {
                wheel[i] = k;
                break;
            }
        }
        return true;
    }

    /** Puts a learned skill on a place of the wheel (null: empties it); off any other place it held. */
    public boolean equip(int slot, Skill k) {
        if (slot < 0 || slot >= wheel.length || (k != null && !learned.contains(k))) return false;
        if (k != null) for (int i = 0; i < wheel.length; i++) if (wheel[i] == k) wheel[i] = null;
        wheel[slot] = k;
        return true;
    }

    public void clearWheel() {
        java.util.Arrays.fill(wheel, null);
    }

    /** How much more every move and the run cost for this player: the bar's rank, a little more in heavy armour. */
    public float costFactor(Player p) {
        if (all || ALL) return 1f;
        float rankCost = switch (rank) {
            case COPPER -> MovesConfig.copperCost();
            case BRONZE -> MovesConfig.bronzeCost();
            default -> 1f;
        };
        // armour weighs a little (author: "even diamond not much, not critical"): its points out of 20
        float armour = Math.min(20, p.getArmorValue()) / 20f;
        return rankCost * (1f + MovesConfig.armourWeight() * armour);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        ListTag l = new ListTag();
        for (Skill k : learned) l.add(StringTag.valueOf(k.key));
        t.put("learned", l);
        ListTag w = new ListTag();
        for (Skill k : wheel) w.add(StringTag.valueOf(k == null ? "" : k.key));
        t.put("wheel", w);
        t.putInt("rank", rank);
        return t;
    }

    public void load(CompoundTag t) {
        learned.clear();
        for (Tag e : t.getList("learned", Tag.TAG_STRING)) {
            Skill k = Skill.byKey(e.getAsString());
            if (k != null) learned.add(k);
        }
        clearWheel();
        ListTag w = t.getList("wheel", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(w.size(), wheel.length); i++) {
            Skill k = Skill.byKey(w.getString(i));
            if (k != null && learned.contains(k)) wheel[i] = k;
        }
        rank = Math.max(COPPER, Math.min(BRASS, t.getInt("rank")));
    }

    /** Packed for the client: the learned skills as bits, the wheel as ids (-1 empty), the rank. */
    public long learnedBits() {
        long b = 0;
        for (Skill k : learned) b |= 1L << k.ordinal();
        return b;
    }

    public void setLearnedBits(long b) {
        learned.clear();
        for (Skill k : Skill.values()) if ((b & (1L << k.ordinal())) != 0) learned.add(k);
    }
}
