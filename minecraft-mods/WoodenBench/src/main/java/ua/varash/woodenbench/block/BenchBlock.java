package ua.varash.woodenbench.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ua.varash.woodenbench.Wood;

/**
 * A slatted bench with backrest.  FACING is the direction the sitter looks.  Benches standing side by side with
 * the same facing join into one long bench (legs and armrests only at the outer ends); each block is one seat.
 */
public class BenchBlock extends SeatBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<BenchShape> SHAPE = EnumProperty.create("shape", BenchShape.class);
    /** Seat surface height (blocks). */
    public static final double SEAT_HEIGHT = 8.5D / 16.0D;

    /** Index: Direction#get2DDataValue (south, west, north, east). */
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            // modelled facing north: seat z 2..13.5, backrest on the south side z 13..15
            SHAPES[facing.get2DDataValue()] = Shapes.or(
                    rotated(facing, 0.0D, 6.0D, 2.0D, 16.0D, 8.5D, 13.5D),
                    rotated(facing, 0.0D, 8.5D, 12.75D, 16.0D, 16.0D, 15.0D),
                    rotated(facing, 1.0D, 0.0D, 2.5D, 15.0D, 6.0D, 14.75D));
        }
    }

    public BenchBlock(Wood wood, Properties properties) {
        super(wood, properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHAPE, BenchShape.SINGLE));
    }

    /** A box given for a north-facing bench, rotated clockwise (seen from above) to {@code facing}. */
    private static VoxelShape rotated(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (facing) {
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // the bench faces the player placing it, like a chair pulled up to you
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return withShape(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                  BlockPos pos, BlockPos neighbourPos) {
        if (direction.getAxis().isHorizontal()) {
            return withShape(state, level, pos);
        }
        return state;
    }

    private static BlockState withShape(BlockState state, LevelAccessor level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        Direction left = facing.getCounterClockWise(); // the sitter's left hand
        boolean l = connects(level.getBlockState(pos.relative(left)), facing);
        boolean r = connects(level.getBlockState(pos.relative(left.getOpposite())), facing);
        return state.setValue(SHAPE, BenchShape.of(l, r));
    }

    private static boolean connects(BlockState other, Direction facing) {
        return other.getBlock() instanceof BenchBlock && other.getValue(FACING) == facing;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public Vec3 seatPosition(BlockState state, BlockPos pos) {
        // a little behind the block centre, so the sitter leans towards the backrest
        Direction back = state.getValue(FACING).getOpposite();
        return new Vec3(pos.getX() + 0.5D + back.getStepX() * 0.06D, pos.getY() + SEAT_HEIGHT,
                pos.getZ() + 0.5D + back.getStepZ() * 0.06D);
    }

    @Override
    public float seatYaw(BlockState state, float playerYaw) {
        return state.getValue(FACING).toYRot();
    }

    @Override
    public float maxHeadTurn() {
        return 100.0F;
    }
}
