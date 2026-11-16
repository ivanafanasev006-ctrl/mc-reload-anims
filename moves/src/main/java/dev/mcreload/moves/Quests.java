package dev.mcreload.moves;

import dev.mcreload.moves.net.MovesNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The trainer's quests (author, 2026-11-16).
 *
 * <p>For moves: two at a time on each trainer, for two different moves; kill so many of two kinds of mob within a time by
 * the clock ("17 skeletons and 21 zombies in 2 hours"), the move's scroll the reward. Not done in time, it goes red: no
 * reward, it is put down and the trainer offers another in its place. Both his quests change for an emerald (the button
 * top right).</p>
 *
 * <p>For the bar's rank, a tab of their own and no time: the bronze line (32 gold ingots to begin it), then the brass
 * one (16 diamonds; not before bronze): rare things brought, monsters of several kinds killed, their spoils brought, and
 * at the end some levels of experience paid for the endurance scroll. The numbers past the author's are [default].</p>
 */
public final class Quests {
    /** Quests a player may hold at once. */
    public static final int HELD = 2;
    private static final String OFFERS = "kinetrix_combat_offers";

    /** The kinds of mob a move's quest asks for, and how many of each (from, to). [default] */
    private static final Object[][] MOBS = {{"minecraft:zombie", 12, 25}, {"minecraft:skeleton", 12, 25}, {"minecraft:spider", 10, 20},
            {"minecraft:creeper", 6, 12}, {"minecraft:enderman", 3, 6}, {"minecraft:witch", 2, 4}};

    /** A stage of a rank's line. */
    public record Stage(Kind kind, Map<String, Integer> what) {}

    public enum Kind { BRING, KILL, PAY }

    /** The bronze line: begun for 32 gold ingots (author). */
    public static final List<Stage> BRONZE = List.of(
            new Stage(Kind.BRING, ordered("minecraft:diamond", 3, "minecraft:golden_apple", 4, "minecraft:totem_of_undying", 1)),
            new Stage(Kind.KILL, ordered("minecraft:zombie", 20, "minecraft:skeleton", 15, "minecraft:creeper", 10, "minecraft:spider", 10,
                    "minecraft:pillager", 5)),
            new Stage(Kind.BRING, ordered("minecraft:gunpowder", 16, "minecraft:bone", 16, "minecraft:string", 8, "minecraft:blaze_rod", 4)),
            new Stage(Kind.PAY, ordered("levels", 12)));
    /** The brass line: begun for 16 diamonds (author), after bronze. */
    public static final List<Stage> BRASS = List.of(
            new Stage(Kind.BRING, ordered("minecraft:emerald", 64, "minecraft:totem_of_undying", 2, "minecraft:heart_of_the_sea", 1,
                    "minecraft:wither_skeleton_skull", 1)),
            new Stage(Kind.KILL, ordered("minecraft:zombie", 50, "minecraft:skeleton", 40, "minecraft:creeper", 25, "minecraft:spider", 20,
                    "minecraft:enderman", 15, "minecraft:pillager", 15, "minecraft:vindicator", 10, "minecraft:witch", 8, "minecraft:blaze", 10)),
            new Stage(Kind.BRING, ordered("minecraft:blaze_rod", 16, "minecraft:ender_pearl", 16, "minecraft:gunpowder", 32, "minecraft:ghast_tear", 4)),
            new Stage(Kind.PAY, ordered("levels", 15)));

    private static Map<String, Integer> ordered(Object... kv) {
        Map<String, Integer> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    public static List<Stage> line(int rankLine) {
        return rankLine <= 2 ? BRONZE : BRASS;
    }

    // ------------------------------------------------------------------ the trainer's offers

    /** His two offers, made now if he has none. */
    public static ListTag offers(Villager v, ServerPlayer p) {
        CompoundTag data = v.getPersistentData();
        ListTag l = data.getList(OFFERS, Tag.TAG_COMPOUND);
        if (l.size() < HELD) {
            l = new ListTag();
            for (int i = 0; i < HELD; i++) l.add(offer(p, v.getRandom(), l));
            data.put(OFFERS, l);
        }
        return l;
    }

    private static void reroll(Villager v, ServerPlayer p, int slot) {
        ListTag l = offers(v, p);
        if (slot < 0) {
            ListTag fresh = new ListTag();
            for (int i = 0; i < HELD; i++) fresh.add(offer(p, v.getRandom(), fresh));
            v.getPersistentData().put(OFFERS, fresh);
        } else {
            ListTag others = new ListTag();
            for (int i = 0; i < l.size(); i++) if (i != slot) others.add(l.get(i));
            l.set(slot, offer(p, v.getRandom(), others));
        }
    }

    /** A new offer: a move not among {@code others} (one this player has not learned, if there is one), two kinds of mob. */
    private static CompoundTag offer(ServerPlayer p, RandomSource r, ListTag others) {
        List<Skill> pool = new ArrayList<>();
        Progress pr = Stamina.of(p).progress;
        for (Skill k : Skill.values()) {
            boolean taken = false;
            for (Tag t : others) taken |= k.key.equals(((CompoundTag) t).getString("skill"));
            if (!taken && !pr.learned.contains(k)) pool.add(k);
        }
        if (pool.isEmpty()) for (Skill k : Skill.values()) {
            boolean taken = false;
            for (Tag t : others) taken |= k.key.equals(((CompoundTag) t).getString("skill"));
            if (!taken) pool.add(k);
        }
        CompoundTag o = new CompoundTag();
        o.putString("skill", pool.get(r.nextInt(pool.size())).key);
        int a = r.nextInt(MOBS.length), b = (a + 1 + r.nextInt(MOBS.length - 1)) % MOBS.length;
        ListTag goals = new ListTag();
        for (int i : new int[]{a, b}) {
            CompoundTag g = new CompoundTag();
            g.putString("mob", (String) MOBS[i][0]);
            g.putInt("need", (Integer) MOBS[i][1] + r.nextInt((Integer) MOBS[i][2] - (Integer) MOBS[i][1] + 1));
            goals.add(g);
        }
        o.put("goals", goals);
        o.putInt("minutes", MovesConfig.questMinutes());
        return o;
    }

    // ------------------------------------------------------------------ the player's quests

    public static boolean done(CompoundTag q) {
        for (Tag t : q.getList("goals", Tag.TAG_COMPOUND)) {
            CompoundTag g = (CompoundTag) t;
            if (g.getInt("have") < g.getInt("need")) return false;
        }
        return true;
    }

    public static boolean failed(CompoundTag q) {
        return !done(q) && System.currentTimeMillis() > q.getLong("until");
    }

    /** The kind a mob counts as: zombies (the husk, the drowned...) as zombies, strays as skeletons. */
    static String family(EntityType<?> type) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        String s = id == null ? "" : id.toString();
        return switch (s) {
            case "minecraft:husk", "minecraft:drowned", "minecraft:zombie_villager" -> "minecraft:zombie";
            case "minecraft:stray" -> "minecraft:skeleton";
            case "minecraft:cave_spider" -> "minecraft:spider";
            default -> s;
        };
    }

    @SubscribeEvent
    public static void killed(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p)) return;
        String kind = family(event.getEntity().getType());
        Progress pr = Stamina.of(p).progress;
        boolean changed = false;
        for (CompoundTag q : pr.quests) {
            if (failed(q)) continue;
            for (Tag t : q.getList("goals", Tag.TAG_COMPOUND)) {
                CompoundTag g = (CompoundTag) t;
                if (g.getString("mob").equals(kind) && g.getInt("have") < g.getInt("need")) {
                    g.putInt("have", g.getInt("have") + 1);
                    changed = true;
                    if (done(q)) p.displayClientMessage(Component.translatable("kinetrix_combat.quest.done",
                            Scrolls.name(Skill.byKey(q.getString("skill")))), false);
                }
            }
        }
        if (pr.rankActive() && line(pr.rankLine).get(pr.rankStage).kind() == Kind.KILL) {
            Integer need = line(pr.rankLine).get(pr.rankStage).what().get(kind);
            if (need != null && pr.rankKills.getInt(kind) < need) {
                pr.rankKills.putInt(kind, pr.rankKills.getInt(kind) + 1);
                changed = true;
            }
        }
        if (changed) MovesNet.trainer(p, null, false);
    }

    // ------------------------------------------------------------------ what the player asks of the trainer

    public enum Action { TAKE, CLAIM, DROP, REROLL, RANK_BEGIN, RANK_NEXT }

    public static void act(ServerPlayer p, Villager v, Action a, int arg) {
        Progress pr = Stamina.of(p).progress;
        ListTag offers = offers(v, p);
        switch (a) {
            case TAKE -> {
                if (arg < 0 || arg >= offers.size() || pr.quests.size() >= HELD) break;
                CompoundTag o = offers.getCompound(arg);
                for (CompoundTag q : pr.quests) if (q.getString("skill").equals(o.getString("skill"))) return;
                CompoundTag q = o.copy();
                q.putLong("until", System.currentTimeMillis() + o.getInt("minutes") * 60_000L);
                q.putUUID("trainer", v.getUUID());
                q.putInt("slot", arg);
                pr.quests.add(q);
                p.level().playSound(null, v.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
            }
            case CLAIM -> {
                if (arg < 0 || arg >= pr.quests.size()) break;
                CompoundTag q = pr.quests.get(arg);
                if (!done(q) || failed(q)) break;
                Skill k = Skill.byKey(q.getString("skill"));
                if (k != null) give(p, Scrolls.of(k));
                pr.quests.remove(arg);
                if (q.hasUUID("trainer") && q.getUUID("trainer").equals(v.getUUID())) reroll(v, p, q.getInt("slot"));
                p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.2f);
            }
            case DROP -> {
                if (arg < 0 || arg >= pr.quests.size()) break;
                CompoundTag q = pr.quests.remove(arg);
                if (q.hasUUID("trainer") && q.getUUID("trainer").equals(v.getUUID())) reroll(v, p, q.getInt("slot"));
            }
            case REROLL -> {
                if (!p.getAbilities().instabuild && !take(p, Items.EMERALD, 1)) {
                    p.displayClientMessage(Component.translatable("kinetrix_combat.quest.need_emerald"), true);
                    break;
                }
                reroll(v, p, -1);
                p.level().playSound(null, v.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1f, 1f);
            }
            case RANK_BEGIN -> {
                // the bronze line for 32 gold ingots, the brass one for 16 diamonds once bronze is read (author)
                boolean bronze = pr.rankLine == 0 && pr.rank == Progress.COPPER;
                boolean brass = (pr.rankLine == 0 || pr.rankLine == 2) && pr.rank == Progress.BRONZE;
                if (!bronze && !brass) break;
                Item price = bronze ? Items.GOLD_INGOT : Items.DIAMOND;
                int n = bronze ? MovesConfig.bronzeLinePrice() : MovesConfig.brassLinePrice();
                if (!p.getAbilities().instabuild && !take(p, price, n)) {
                    p.displayClientMessage(Component.translatable("kinetrix_combat.rank.need", n, new ItemStack(price).getHoverName()), true);
                    break;
                }
                pr.rankLine = bronze ? 1 : 3;
                pr.rankStage = 0;
                pr.rankKills = new CompoundTag();
                p.level().playSound(null, v.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
            }
            case RANK_NEXT -> {
                if (!pr.rankActive()) break;
                Stage st = line(pr.rankLine).get(pr.rankStage);
                switch (st.kind()) {
                    case BRING -> {
                        if (!p.getAbilities().instabuild && !hasAll(p, st.what())) {
                            p.displayClientMessage(Component.translatable("kinetrix_combat.rank.not_all"), true);
                            return;
                        }
                        if (!p.getAbilities().instabuild) st.what().forEach((id, n) -> take(p, item(id), n));
                    }
                    case KILL -> {
                        for (Map.Entry<String, Integer> e : st.what().entrySet()) {
                            if (pr.rankKills.getInt(e.getKey()) < e.getValue()) {
                                p.displayClientMessage(Component.translatable("kinetrix_combat.rank.not_all"), true);
                                return;
                            }
                        }
                    }
                    case PAY -> {
                        int levels = st.what().get("levels");
                        if (!p.getAbilities().instabuild && p.experienceLevel < levels) {
                            p.displayClientMessage(Component.translatable("kinetrix_combat.rank.need_levels", levels), true);
                            return;
                        }
                        if (!p.getAbilities().instabuild) p.giveExperienceLevels(-levels);
                        give(p, new ItemStack(pr.rankLine == 1 ? Scrolls.BRONZE.get() : Scrolls.BRASS.get()));
                    }
                }
                pr.rankKills = new CompoundTag();
                if (++pr.rankStage >= line(pr.rankLine).size()) {
                    pr.rankLine++;
                    pr.rankStage = 0;
                }
                p.level().playSound(null, v.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1.1f);
            }
        }
        MovesNet.trainer(p, v, false);
    }

    public static Item item(String id) {
        Item i = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return i == null ? Items.AIR : i;
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        for (ItemStack s : p.getInventory().offhand) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static boolean hasAll(ServerPlayer p, Map<String, Integer> what) {
        for (Map.Entry<String, Integer> e : what.entrySet()) if (count(p, item(e.getKey())) < e.getValue()) return false;
        return true;
    }

    /** Takes {@code n} of the item from the inventory if there are as many; returns whether it did. */
    private static boolean take(ServerPlayer p, Item item, int n) {
        if (count(p, item) < n) return false;
        int left = n;
        for (List<ItemStack> list : List.of(p.getInventory().items, p.getInventory().offhand)) {
            for (ItemStack s : list) {
                if (left <= 0) break;
                if (!s.is(item)) continue;
                int t = Math.min(left, s.getCount());
                s.shrink(t);
                left -= t;
            }
        }
        p.getInventory().setChanged();
        return true;
    }

    private static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    private Quests() {}
}
