package ua.varash.woodenbench;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ua.varash.woodenbench.block.BenchBlock;
import ua.varash.woodenbench.block.SeatBlockItem;
import ua.varash.woodenbench.block.SeatBoardBlock;
import ua.varash.woodenbench.entity.SeatEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static ua.varash.woodenbench.WoodenBenchMod.MODID;

public final class ModRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    public static final Map<Wood, RegistryObject<SeatBoardBlock>> SEAT_BOARDS = new EnumMap<>(Wood.class);
    public static final Map<Wood, RegistryObject<BenchBlock>> BENCHES = new EnumMap<>(Wood.class);
    private static final List<RegistryObject<? extends Item>> ALL_ITEMS = new ArrayList<>();

    /** Invisible, never-saved entity the player rides while sitting. */
    public static final RegistryObject<EntityType<SeatEntity>> SEAT = ENTITY_TYPES.register("seat",
            () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.1F)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build(new ResourceLocation(MODID, "seat").toString()));

    public static final RegistryObject<SoundEvent> SIT_SOUND = SOUND_EVENTS.register("block.seat.sit",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "block.seat.sit")));

    static {
        for (Wood wood : Wood.values()) {
            RegistryObject<SeatBoardBlock> board = BLOCKS.register(wood.id() + "_seat_board",
                    () -> new SeatBoardBlock(wood, properties(wood)));
            RegistryObject<BenchBlock> bench = BLOCKS.register(wood.id() + "_bench",
                    () -> new BenchBlock(wood, properties(wood)));
            SEAT_BOARDS.put(wood, board);
            BENCHES.put(wood, bench);
            ALL_ITEMS.add(ITEMS.register(wood.id() + "_seat_board",
                    () -> new SeatBlockItem(board.get(), wood, new Item.Properties())));
            ALL_ITEMS.add(ITEMS.register(wood.id() + "_bench",
                    () -> new SeatBlockItem(bench.get(), wood, new Item.Properties())));
        }
    }

    private ModRegistry() {
    }

    private static BlockBehaviour.Properties properties(Wood wood) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .mapColor(wood.color())
                .strength(1.5F, 3.0F)
                .sound(wood.sound())
                .noOcclusion()
                .pushReaction(PushReaction.NORMAL);
        return wood.flammable() ? properties.ignitedByLava() : properties;
    }

    public static List<RegistryObject<? extends Item>> allItems() {
        return Collections.unmodifiableList(ALL_ITEMS);
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        SOUND_EVENTS.register(modBus);
    }
}
