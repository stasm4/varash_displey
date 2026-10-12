package ua.varash.woodenbench.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ua.varash.woodenbench.Wood;

/**
 * A rough-hewn board on two stumps, for a camp by the fire.  No backrest, so the sitter can face either side;
 * the side closest to where the player is looking is chosen, so the camera does not swing around.
 */
public class SeatBoardBlock extends SeatBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    /** Seat surface height (blocks). */
    public static final double SEAT_HEIGHT = 8.0D / 16.0D;

    private static final VoxelShape SHAPE_X = Shapes.or(
            Block.box(0.0D, 6.0D, 4.0D, 16.0D, 8.0D, 12.0D),
            Block.box(1.5D, 0.0D, 5.5D, 6.5D, 6.0D, 10.5D),
            Block.box(9.5D, 0.0D, 5.5D, 14.5D, 6.0D, 10.5D));
    private static final VoxelShape SHAPE_Z = Shapes.or(
            Block.box(4.0D, 6.0D, 0.0D, 12.0D, 8.0D, 16.0D),
            Block.box(5.5D, 0.0D, 1.5D, 10.5D, 6.0D, 6.5D),
            Block.box(5.5D, 0.0D, 9.5D, 10.5D, 6.0D, 14.5D));

    public SeatBoardBlock(Wood wood, Properties properties) {
        super(wood, properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // the board lies across the player's view, like a bench in front of them
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getClockWise().getAxis());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
    }

    @Override
    public Vec3 seatPosition(BlockState state, BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + SEAT_HEIGHT, pos.getZ() + 0.5D);
    }

    @Override
    public float seatYaw(BlockState state, float playerYaw) {
        // board along X -> sit facing south (0) or north (180); along Z -> west (90) or east (270)
        float a = state.getValue(AXIS) == Direction.Axis.X ? 0.0F : 90.0F;
        float b = a + 180.0F;
        return Math.abs(Mth.wrapDegrees(playerYaw - a)) <= Math.abs(Mth.wrapDegrees(playerYaw - b)) ? a : b;
    }

    @Override
    public float maxHeadTurn() {
        return 110.0F;
    }
}
