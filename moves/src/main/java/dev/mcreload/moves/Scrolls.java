package dev.mcreload.moves;

import dev.mcreload.moves.net.MovesNet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Set;

/**
 * The scrolls (author, 2026-11-16): a move written in one (read with the right button: learned, on the wheel if there is
 * room), found in dungeons and village chests and given by the trainer; its seal turquoise. The bar's rank scrolls, apart
 * from them and shining like enchanted things (author, 2026-11-16: "1 gives copper, 2 bronze, 3 brass, shimmering"):
 * copper (the bar back to copper), bronze, then brass, the trainer's for his chains of tasks. All of them in the creative
 * tab.
 */
public final class Scrolls {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, KinetrixMoves.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, KinetrixMoves.MODID);

    public static final RegistryObject<Item> MOVE = ITEMS.register("move_scroll",
            () -> new MoveScroll(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> COPPER = ITEMS.register("copper_scroll",
            () -> new RankScroll(Progress.COPPER, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> BRONZE = ITEMS.register("bronze_scroll",
            () -> new RankScroll(Progress.BRONZE, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> BRASS = ITEMS.register("brass_scroll",
            () -> new RankScroll(Progress.BRASS, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.kinetrix_combat"))
            .icon(() -> of(Skill.WHIRL))
            .displayItems((params, out) -> {
                for (Skill k : Skill.values()) out.accept(of(k));
                out.accept(COPPER.get());
                out.accept(BRONZE.get());
                out.accept(BRASS.get());
                out.accept(Trainer.DUMMY_ITEM.get());
            }).build());

    /** Chests a move's scroll may be found in: the dungeons' (author: "in dungeons and village chests"). */
    private static final Set<String> DUNGEONS = Set.of("chests/simple_dungeon", "chests/abandoned_mineshaft", "chests/stronghold_corridor",
            "chests/stronghold_crossing", "chests/stronghold_library", "chests/desert_pyramid", "chests/jungle_temple",
            "chests/pillager_outpost", "chests/woodland_mansion", "chests/ancient_city", "chests/underwater_ruin_big");

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    /** A scroll of this move. */
    public static ItemStack of(Skill k) {
        ItemStack s = new ItemStack(MOVE.get());
        s.getOrCreateTag().putString("skill", k.key);
        return s;
    }

    public static Skill skill(ItemStack s) {
        return s.hasTag() ? Skill.byKey(s.getTag().getString("skill")) : null;
    }

    public static Component name(Skill k) {
        return Component.translatable("kinetrix_combat.settings.move." + k.key);
    }

    @SubscribeEvent
    public static void loot(LootTableLoadEvent event) {
        ResourceLocation id = event.getName();
        if (!"minecraft".equals(id.getNamespace())) return;
        String path = id.getPath();
        float chance = DUNGEONS.contains(path) ? MovesConfig.scrollChanceDungeon()
                : path.startsWith("chests/village/") ? MovesConfig.scrollChanceVillage() : 0f;
        if (chance <= 0f) return;
        LootPool.Builder pool = LootPool.lootPool().name("kinetrix_combat_scrolls").setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(chance));
        for (Skill k : Skill.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("skill", k.key);
            pool.add(LootItem.lootTableItem(MOVE.get()).apply(SetNbtFunction.setTag(t)));
        }
        event.getTable().addPool(pool.build());
    }

    /** A move's scroll: read, the move is learned (once), put on the wheel if there is a free place. */
    public static final class MoveScroll extends Item {
        MoveScroll(Properties p) {
            super(p);
        }

        @Override
        public Component getName(ItemStack stack) {
            Skill k = skill(stack);
            return k == null ? super.getName(stack) : Component.translatable("item.kinetrix_combat.move_scroll.of", name(k));
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
            Skill k = skill(stack);
            if (k == null) return;
            lines.add(Component.translatable("kinetrix_combat.weapon." + k.weapon.name().toLowerCase()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("item.kinetrix_combat.move_scroll.read").withStyle(ChatFormatting.DARK_GRAY));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
            ItemStack stack = p.getItemInHand(hand);
            Skill k = skill(stack);
            if (k == null) return InteractionResultHolder.fail(stack);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            Progress pr = Stamina.of(p).progress;
            if (!pr.learn(k)) {
                p.displayClientMessage(Component.translatable("kinetrix_combat.scroll.known", name(k)), true);
                return InteractionResultHolder.fail(stack);
            }
            p.displayClientMessage(Component.translatable(pr.equipped(k) ? "kinetrix_combat.scroll.learned" : "kinetrix_combat.scroll.learned_full",
                    name(k)), false);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.4f);
            if (!p.getAbilities().instabuild) stack.shrink(1);
            if (p instanceof ServerPlayer sp) MovesNet.progress(sp);
            return InteractionResultHolder.consume(stack);
        }
    }

    /** The bar's rank scroll: copper to bronze, bronze to brass, one after another; the copper one takes it back to copper. */
    public static final class RankScroll extends Item {
        final int rank;

        RankScroll(int rank, Properties p) {
            super(p);
            this.rank = rank;
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.translatable("item.kinetrix_combat.rank_scroll.tip." + rank).withStyle(ChatFormatting.GRAY));
        }

        /** Shines like an enchanted thing. */
        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
            ItemStack stack = p.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            Progress pr = Stamina.of(p).progress;
            if (rank == Progress.COPPER ? pr.rank == Progress.COPPER : pr.rank >= rank) {
                p.displayClientMessage(Component.translatable("kinetrix_combat.rank.already"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (rank == Progress.COPPER) {
                // back to copper: the trainer's lines from the start too
                pr.rankLine = 0;
                pr.rankStage = 0;
                pr.rankKills = new CompoundTag();
            } else if (pr.rank != rank - 1) {
                p.displayClientMessage(Component.translatable("kinetrix_combat.rank.bronze_first"), true);
                return InteractionResultHolder.fail(stack);
            }
            pr.rank = rank;
            p.displayClientMessage(Component.translatable("kinetrix_combat.rank.up." + rank), false);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 0.8f);
            if (!p.getAbilities().instabuild) stack.shrink(1);
            if (p instanceof ServerPlayer sp) MovesNet.progress(sp);
            return InteractionResultHolder.consume(stack);
        }
    }

    private Scrolls() {}
}
