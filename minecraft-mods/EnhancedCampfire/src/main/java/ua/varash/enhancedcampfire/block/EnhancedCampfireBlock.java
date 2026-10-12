package ua.varash.enhancedcampfire.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import ua.varash.enhancedcampfire.ModRegistry;

import java.util.Optional;

/**
 * The enhanced campfire.  It extends the vanilla {@link CampfireBlock} so every vanilla mechanic that checks for
 * a campfire (flint and steel, fire charges, burning arrows, shovels and splash water dousing it, waterlogging,
 * hay-bale signal fires, calming bees, entity fire damage) keeps working.  The block is also in the
 * {@code #minecraft:campfires} tag.  Only the block entity is replaced, because the vanilla one is hard-wired to
 * the vanilla block entity type.
 *
 * <p>Extra state: {@link #VARIANT} (one of three log layouts, chosen from the position when placed so it is stable
 * across reloads and identical on client and server) and {@link #SMOULDERING} (embers keep glowing for a minute
 * after the fire is put out).
 */
public class EnhancedCampfireBlock extends CampfireBlock {
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);
    public static final BooleanProperty SMOULDERING = BooleanProperty.create("smouldering");

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 7.0D, 16.0D);

    public EnhancedCampfireBlock(Properties properties) {
        // no vanilla lava-pop particles (we have our own effects), 1 fire damage like the normal campfire
        super(false, 1, properties);
        registerDefaultState(defaultBlockState().setValue(VARIANT, 0).setValue(SMOULDERING, Boolean.FALSE));
    }

    public static int lightLevel(BlockState state) {
        if (state.getValue(LIT)) {
            return 15;
        }
        return state.getValue(SMOULDERING) ? 6 : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VARIANT, SMOULDERING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        // deterministic "random" layout: same result on client prediction and server, stable forever
        int variant = Math.floorMod((int) (positionHash(context.getClickedPos()) >>> 20), 3);
        return state.setValue(VARIANT, variant);
    }

    /** Stable 64-bit hash of a block position (SplitMix64 finaliser). */
    public static long positionHash(BlockPos pos) {
        long z = pos.asLong() + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof EnhancedCampfireBlockEntity campfire)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);

        // Sneak + right click with a stick re-stacks the logs into the next layout.
        if (player.isShiftKeyDown() && stack.is(Items.STICK)) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.cycle(VARIANT), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 0.85F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        Optional<CampfireCookingRecipe> recipe = campfire.getCookableRecipe(stack);
        if (recipe.isPresent()) {
            if (!level.isClientSide && campfire.placeFood(player,
                    player.getAbilities().instabuild ? stack.copy() : stack, recipe.get().getCookingTime())) {
                player.awardStat(Stats.INTERACT_WITH_CAMPFIRE);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof EnhancedCampfireBlockEntity campfire) {
            Containers.dropContents(level, pos, campfire.getItems());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // All client effects (particles, sounds) are driven by the block entity's client ticker, which can keep
        // per-campfire state and respects the client config and distance limits.
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnhancedCampfireBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModRegistry.CAMPFIRE_BE.get(),
                level.isClientSide ? EnhancedCampfireBlockEntity::clientTick : EnhancedCampfireBlockEntity::serverTick);
    }
}
