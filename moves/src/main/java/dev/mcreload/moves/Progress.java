package dev.mcreload.moves;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * What a player has learned (author, 2026-11-16), kept with the bars (Stamina) and through death: the skills read from
 * scrolls, the wheel of {@link Skill#WHEEL} of them and the row of {@link Skill#PERKS} perks above it and the square of
 * {@link Skill#ULTS} ultimates (only those work; the places are numbered on together: the wheel's 0 to 5, the perks' 6 to
 * 15, the ultimates' 16 to 19), and the bar's rank: copper at the start (every
 * move and the run costing MovesConfig.copperCost times as much), bronze, then brass (as it was), each from a scroll the
 * trainer gives for his chain of tasks, one after another.
 */
public final class Progress {
    public static final int COPPER = 0, BRONZE = 1, BRASS = 2;
    /** The client self-test plays every move: everything usable, the brass bar. */
    private static final boolean ALL = Boolean.getBoolean("kinetrix_combat.selftest");

    public final EnumSet<Skill> learned = EnumSet.noneOf(Skill.class);
    public final Skill[] wheel = new Skill[Skill.WHEEL];
    public final Skill[] perks = new Skill[Skill.PERKS];
    public final Skill[] ults = new Skill[Skill.ULTS];
    public int rank = COPPER;
    /** Every skill usable (test players; not saved). */
    public boolean all;
    /** The trainer's quests for moves this player holds (Quests: the move, the mobs to kill and how many so far, by when). */
    public final java.util.List<CompoundTag> quests = new java.util.ArrayList<>();
    /**
     * The rank's line of tasks (Quests): 0 none begun, 1 the bronze line under way, 2 bronze given, 3 the brass line under
     * way, 4 brass given; its stage and the kills counted in a killing stage.
     */
    public int rankLine, rankStage;
    public CompoundTag rankKills = new CompoundTag();

    public boolean rankActive() {
        return rankLine == 1 || rankLine == 3;
    }

    /** This skill works now: learned and on the wheel (a perk: in the perks' row). */
    public boolean usable(Skill k) {
        return k == null || all || ALL || (learned.contains(k) && equipped(k));
    }

    /** The row a skill goes in: the wheel, the perks' or the ultimates' square. */
    public Skill[] row(Skill k) {
        return row(k.place);
    }

    public Skill[] row(Skill.Place place) {
        return switch (place) {
            case WHEEL -> wheel;
            case PERK -> perks;
            case ULT -> ults;
        };
    }

    /** The kind of place {@code slot} is (the wheel's 0 to 5, the perks' 6 to 15, the ultimates' 16 to 19). */
    public static Skill.Place placeOf(int slot) {
        return slot < Skill.WHEEL ? Skill.Place.WHEEL : slot < Skill.WHEEL + Skill.PERKS ? Skill.Place.PERK : Skill.Place.ULT;
    }

    /** The first place of a kind. */
    public static int firstOf(Skill.Place place) {
        return switch (place) {
            case WHEEL -> 0;
            case PERK -> Skill.WHEEL;
            case ULT -> Skill.WHEEL + Skill.PERKS;
        };
    }

    public boolean equipped(Skill k) {
        for (Skill w : row(k)) if (w == k) return true;
        return false;
    }

    /** All the places, the wheel's, the perks', the ultimates'. */
    public static final int PLACES = Skill.WHEEL + Skill.PERKS + Skill.ULTS;

    /** What is on place {@code slot}. */
    public Skill at(int slot) {
        Skill.Place place = placeOf(slot);
        return row(place)[slot - firstOf(place)];
    }

    /** Sets place {@code slot} as it is (the client, from the server). */
    public void set(int slot, Skill k) {
        if (slot < 0 || slot >= PLACES) return;
        Skill.Place place = placeOf(slot);
        row(place)[slot - firstOf(place)] = k;
    }

    /** Learns it (true if it was new) and puts it on its row's first free place, if there is one. */
    public boolean learn(Skill k) {
        if (!learned.add(k)) return false;
        Skill[] row = row(k);
        for (int i = 0; i < row.length; i++) {
            if (row[i] == null) {
                row[i] = k;
                break;
            }
        }
        return true;
    }

    /**
     * Puts a learned skill on a place (null: empties it), off any other place it held: a move on the wheel's places, a
     * perk on the perks'.
     */
    public boolean equip(int slot, Skill k) {
        if (slot < 0 || slot >= PLACES || (k != null && !learned.contains(k))) return false;
        Skill.Place place = placeOf(slot);
        if (k != null && k.place != place) return false;
        Skill[] row = row(place);
        if (k != null) for (int i = 0; i < row.length; i++) if (row[i] == k) row[i] = null;
        row[slot - firstOf(place)] = k;
        return true;
    }

    /** Empties the wheel and the perks. */
    public void clearWheel() {
        java.util.Arrays.fill(wheel, null);
        java.util.Arrays.fill(perks, null);
        java.util.Arrays.fill(ults, null);
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
        ListTag pk = new ListTag();
        for (Skill k : perks) pk.add(StringTag.valueOf(k == null ? "" : k.key));
        t.put("perks", pk);
        ListTag ul = new ListTag();
        for (Skill k : ults) ul.add(StringTag.valueOf(k == null ? "" : k.key));
        t.put("ults", ul);
        t.putInt("rank", rank);
        ListTag q = new ListTag();
        q.addAll(quests);
        t.put("quests", q);
        t.putInt("rankLine", rankLine);
        t.putInt("rankStage", rankStage);
        t.put("rankKills", rankKills);
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
            if (k != null && learned.contains(k) && k.place == Skill.Place.WHEEL) wheel[i] = k;
        }
        ListTag pk = t.getList("perks", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(pk.size(), perks.length); i++) {
            Skill k = Skill.byKey(pk.getString(i));
            if (k != null && learned.contains(k) && k.perk) perks[i] = k;
        }
        ListTag ul = t.getList("ults", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(ul.size(), ults.length); i++) {
            Skill k = Skill.byKey(ul.getString(i));
            if (k != null && learned.contains(k) && k.ult) ults[i] = k;
        }
        // a perk that was on the wheel (before 0.13), an ultimate that was (before 0.17): into its own row
        for (int i = 0; i < w.size(); i++) {
            Skill k = Skill.byKey(w.getString(i));
            if (k == null || k.place == Skill.Place.WHEEL || !learned.contains(k) || equipped(k)) continue;
            Skill[] row = row(k);
            for (int j = 0; j < row.length; j++) {
                if (row[j] == null) {
                    row[j] = k;
                    break;
                }
            }
        }
        rank = Math.max(COPPER, Math.min(BRASS, t.getInt("rank")));
        quests.clear();
        for (Tag e : t.getList("quests", Tag.TAG_COMPOUND)) quests.add((CompoundTag) e);
        rankLine = t.getInt("rankLine");
        rankStage = t.getInt("rankStage");
        rankKills = t.getCompound("rankKills");
    }

    /** Packed for the client: the learned skills as bits, the places as ids (-1 empty), the rank. */
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
