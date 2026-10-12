package ua.varash.enhancedcampfire;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ua.varash.enhancedcampfire.block.EnhancedCampfireBlock;
import ua.varash.enhancedcampfire.block.EnhancedCampfireBlockEntity;

import static ua.varash.enhancedcampfire.EnhancedCampfireMod.MODID;

public final class ModRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    private static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MODID);
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    public static final RegistryObject<EnhancedCampfireBlock> CAMPFIRE = BLOCKS.register("enhanced_campfire",
            () -> new EnhancedCampfireBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PODZOL)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.0F)
                    .sound(SoundType.WOOD)
                    .lightLevel(EnhancedCampfireBlock::lightLevel)
                    .noOcclusion()
                    .ignitedByLava()
                    .pushReaction(PushReaction.BLOCK)));

    public static final RegistryObject<Item> CAMPFIRE_ITEM = ITEMS.register("enhanced_campfire",
            () -> new BlockItem(CAMPFIRE.get(), new Item.Properties()));

    @SuppressWarnings("DataFlowIssue") // a null data-fixer type is the standard for modded block entities
    public static final RegistryObject<BlockEntityType<EnhancedCampfireBlockEntity>> CAMPFIRE_BE =
            BLOCK_ENTITY_TYPES.register("enhanced_campfire",
                    () -> BlockEntityType.Builder.of(EnhancedCampfireBlockEntity::new, CAMPFIRE.get()).build(null));

    public static final RegistryObject<SimpleParticleType> SPARK = particle("spark");
    public static final RegistryObject<SimpleParticleType> BIG_SPARK = particle("big_spark");
    public static final RegistryObject<SimpleParticleType> ASH = particle("ash");
    public static final RegistryObject<SimpleParticleType> FIRE_SMOKE = particle("fire_smoke");
    public static final RegistryObject<SimpleParticleType> STEAM = particle("steam");
    public static final RegistryObject<SimpleParticleType> FLAME_LICK = particle("flame_lick");

    public static final RegistryObject<SoundEvent> CRACKLE = sound("block.enhanced_campfire.crackle");
    public static final RegistryObject<SoundEvent> POP = sound("block.enhanced_campfire.pop");
    public static final RegistryObject<SoundEvent> HISS = sound("block.enhanced_campfire.hiss");
    public static final RegistryObject<SoundEvent> ROAR = sound("block.enhanced_campfire.roar");

    private ModRegistry() {
    }

    private static RegistryObject<SimpleParticleType> particle(String name) {
        return PARTICLE_TYPES.register(name, () -> new SimpleParticleType(false));
    }

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, name)));
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        PARTICLE_TYPES.register(modBus);
        SOUND_EVENTS.register(modBus);
    }
}
