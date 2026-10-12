package ua.varash.enhancedcampfire.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import ua.varash.enhancedcampfire.ModRegistry;

import java.util.Optional;

/**
 * The enhanced campfire: a big 2x2 fire pit.
 *
 * <p>It is a real multiblock of four blocks of this type ({@link #PART}). The north-west {@link CampfirePart#MASTER}
 * block owns the block entity (cooking, smouldering) and renders the whole 32x32 px model; the other three blocks
 * have an empty model but occupy their space, collide, burn entities and give light. All four are placed together
 * (only if all four spots are free), are removed together (any missing sibling makes a part turn into air, like a
 * bed), and only the master has a loot table entry, so breaking the fire never duplicates items.
 *
 * <p>It extends the vanilla {@link CampfireBlock} and is in {@code #minecraft:campfires}, so vanilla interactions
 * keep working on every part: flint and steel, fire charges, burning arrows, shovels, splash water, waterlogging,
 * hay-bale signal fires, bees. The lit state is kept in sync between the parts through {@link #updateShape}.
 */
public class EnhancedCampfireBlock extends CampfireBlock {
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);
    public static final BooleanProperty SMOULDERING = BooleanProperty.create("smouldering");
    public static final EnumProperty<CampfirePart> PART = EnumProperty.create("part", CampfirePart.class);

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);

    public EnhancedCampfireBlock(Properties properties) {
        // no vanilla lava-pop particles (we have our own effects), 1 fire damage like the normal campfire
        super(false, 1, properties);
        registerDefaultState(defaultBlockState().setValue(VARIANT, 0).setValue(SMOULDERING, Boolean.FALSE)
                .setValue(PART, CampfirePart.MASTER));
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
        builder.add(VARIANT, SMOULDERING, PART);
    }

    // ------------------------------------------------------------------ placement

    /** Stable 64-bit hash of a block position (SplitMix64 finaliser). */
    public static long positionHash(BlockPos pos) {
        long z = pos.asLong() + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Master position for a placement click: the fire grows away from the player on both horizontal axes. */
    public static BlockPos originFor(BlockPos clicked, @Nullable Player player) {
        if (player == null) {
            return clicked;
        }
        Vec3 look = player.getLookAngle();
        return clicked.offset(look.x < 0.0D ? -1 : 0, 0, look.z < 0.0D ? -1 : 0);
    }

    /** True when all four blocks of a fire with its master at {@code origin} can be placed. */
    public static boolean canPlaceAt(LevelReader level, BlockPos origin, @Nullable BlockPos alreadyChecked) {
        for (CampfirePart part : CampfirePart.values()) {
            BlockPos pos = part.at(origin);
            if (pos.equals(alreadyChecked)) {
                continue;
            }
            if (!level.getWorldBorder().isWithinBounds(pos) || !level.getBlockState(pos).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    /** The state of one part of a new fire: water puts the whole fire out, hay under a part makes signal smoke. */
    public BlockState partState(LevelReader level, BlockPos origin, CampfirePart part, int variant, Direction facing) {
        boolean anyWater = false;
        for (CampfirePart p : CampfirePart.values()) {
            anyWater |= level.getFluidState(p.at(origin)).getType() == Fluids.WATER;
        }
        BlockPos pos = part.at(origin);
        return defaultBlockState()
                .setValue(PART, part)
                .setValue(VARIANT, variant)
                .setValue(FACING, facing)
                .setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER)
                .setValue(LIT, !anyWater)
                .setValue(SIGNAL_FIRE, level.getBlockState(pos.below()).is(Blocks.HAY_BLOCK));
    }

    /** Places a complete fire (all four blocks). Used by placement and by tests. */
    public void placeFire(Level level, BlockPos origin, int variant, Direction facing) {
        for (CampfirePart part : CampfirePart.values()) {
            level.setBlock(part.at(origin), partState(level, origin, part, variant, facing), Block.UPDATE_ALL);
        }
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        BlockPos origin = originFor(clicked, context.getPlayer());
        if (!canPlaceAt(level, origin, clicked)) {
            return null;
        }
        CampfirePart part = CampfirePart.byOffset(clicked.getX() - origin.getX(), clicked.getZ() - origin.getZ());
        // deterministic "random" layout from the fire's position: same on client and server, stable forever
        int variant = Math.floorMod((int) (positionHash(origin) >>> 20), 3);
        return partState(level, origin, part == null ? CampfirePart.MASTER : part, variant, context.getHorizontalDirection());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        CampfirePart placed = state.getValue(PART);
        BlockPos origin = placed.origin(pos);
        for (CampfirePart part : CampfirePart.values()) {
            if (part != placed) {
                level.setBlock(part.at(origin), partState(level, origin, part, state.getValue(VARIANT), state.getValue(FACING)),
                        Block.UPDATE_ALL);
            }
        }
    }

    // ------------------------------------------------------------------ keeping the four parts together

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                  BlockPos pos, BlockPos neighbourPos) {
        BlockState updated = super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
        if (!direction.getAxis().isHorizontal()) {
            return updated;
        }
        CampfirePart part = state.getValue(PART);
        CampfirePart expected = CampfirePart.byOffset(part.dx() + direction.getStepX(), part.dz() + direction.getStepZ());
        if (expected == null) {
            return updated; // that neighbour is outside this fire
        }
        if (!neighbour.is(this) || neighbour.getValue(PART) != expected) {
            // a sibling is gone: this part goes too (drops only come from the master's loot table)
            return Blocks.AIR.defaultBlockState();
        }
        // burn or go out together; a part standing in water cannot be relit by its neighbours
        boolean lit = neighbour.getValue(LIT);
        if (lit != updated.getValue(LIT) && !(lit && updated.getValue(WATERLOGGED))) {
            updated = updated.setValue(LIT, lit);
        }
        return updated;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            CampfirePart part = state.getValue(PART);
            BlockPos origin = part.origin(pos);
            BlockState master = level.getBlockState(origin);
            if (part != CampfirePart.MASTER && master.is(this) && master.getValue(PART) == CampfirePart.MASTER) {
                // Breaking any part breaks the fire: the master drops its loot as if the player had broken it
                // (so silk touch works from every part), then the remaining parts collapse without drops.
                if (!player.isCreative()) {
                    dropResources(master, level, origin, level.getBlockEntity(origin), player, player.getMainHandItem());
                }
                level.setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, origin, Block.getId(master));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof EnhancedCampfireBlockEntity campfire) {
            Containers.dropContents(level, pos, campfire.getItems());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        BlockPos origin = state.getValue(PART).origin(pos);
        if (!(level.getBlockEntity(origin) instanceof EnhancedCampfireBlockEntity campfire)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);

        // Sneak + right click with a stick re-stacks the logs into the next layout.
        if (player.isShiftKeyDown() && stack.is(Items.STICK)) {
            if (!level.isClientSide) {
                BlockState master = level.getBlockState(origin);
                level.setBlock(origin, master.cycle(VARIANT), Block.UPDATE_ALL);
                level.playSound(null, origin, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 0.85F);
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // All client effects (particles, sounds) are driven by the master block entity's client ticker.
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == CampfirePart.MASTER ? new EnhancedCampfireBlockEntity(pos, state) : null;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(PART) != CampfirePart.MASTER) {
            return null;
        }
        return createTickerHelper(type, ModRegistry.CAMPFIRE_BE.get(),
                level.isClientSide ? EnhancedCampfireBlockEntity::clientTick : EnhancedCampfireBlockEntity::serverTick);
    }
}
