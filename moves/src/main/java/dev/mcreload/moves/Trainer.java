package dev.mcreload.moves;

import com.google.common.collect.ImmutableSet;
import dev.mcreload.moves.net.MovesNet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The trainer (author, 2026-11-16): a villager's trade, his workstation the training dummy (made or found in a village: a
 * villager in a village's house may be one from the start), dressed in brown, his quests for moves and for the bar's
 * ranks instead of trades (Quests; the right button opens them).
 */
public final class Trainer {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, KinetrixMoves.MODID);
    public static final DeferredRegister<PoiType> POIS = DeferredRegister.create(ForgeRegistries.POI_TYPES, KinetrixMoves.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, KinetrixMoves.MODID);

    public static final RegistryObject<Block> DUMMY = BLOCKS.register("training_dummy", () -> new DummyBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Item> DUMMY_ITEM = Scrolls.ITEMS.register("training_dummy", () -> new BlockItem(DUMMY.get(), new Item.Properties()));
    public static final RegistryObject<PoiType> DUMMY_POI = POIS.register("training_dummy",
            () -> new PoiType(ImmutableSet.copyOf(DUMMY.get().getStateDefinition().getPossibleStates()), 1, 1));
    public static final RegistryObject<VillagerProfession> TRAINER = PROFESSIONS.register("trainer",
            () -> new VillagerProfession("trainer", h -> h.is(DUMMY_POI.getKey()), h -> h.is(DUMMY_POI.getKey()), ImmutableSet.of(), ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_WEAPONSMITH));

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        POIS.register(modBus);
        PROFESSIONS.register(modBus);
    }

    public static boolean is(Villager v) {
        return v.getVillagerData().getProfession() == TRAINER.get();
    }

    /** A villager of a village's house (made with the village): now and then the trainer, kept so with a little experience. */
    @SubscribeEvent
    public static void spawned(MobSpawnEvent.FinalizeSpawn event) {
        if (!(event.getEntity() instanceof Villager v) || event.getSpawnType() != MobSpawnType.STRUCTURE) return;
        if (v.getVillagerData().getProfession() != VillagerProfession.NONE || v.getRandom().nextFloat() >= MovesConfig.trainerChance()) return;
        v.setVillagerData(v.getVillagerData().setProfession(TRAINER.get()).setLevel(1));
        v.setVillagerXp(1);
    }

    /** The trainer's quests instead of trades. */
    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager v) || !is(v) || v.isBaby()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (event.getEntity() instanceof ServerPlayer p) {
            if (v.getVillagerXp() < 1) v.setVillagerXp(1);     // a trainer stays one without his dummy
            v.setTradingPlayer(null);
            MovesNet.trainer(p, v, true);
        }
    }

    /** The training dummy: a post on a stand, a stuffed body and head, its arms out; it faces the one who set it. */
    public static final class DummyBlock extends HorizontalDirectionalBlock {
        private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 2, 14), Block.box(7, 2, 7, 9, 7, 9),
                Block.box(4, 7, 5, 12, 13, 11), Block.box(5.5, 13, 5.5, 10.5, 16, 10.5));

        DummyBlock(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }
    }

    private Trainer() {}
}
