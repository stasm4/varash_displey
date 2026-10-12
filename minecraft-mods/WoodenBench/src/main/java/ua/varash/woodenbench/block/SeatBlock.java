package ua.varash.woodenbench.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ua.varash.woodenbench.Wood;
import ua.varash.woodenbench.config.ServerConfig;
import ua.varash.woodenbench.entity.SeatEntity;
import ua.varash.woodenbench.seat.SeatManager;

/**
 * Something a player can sit on.  Subclasses describe where the seat surface is and which way the sitter faces.
 * Sitting is started by left click (see {@code SeatEvents} / {@code ClientInput}); this class also handles the
 * optional right click and makes sure nobody stays seated on a seat that no longer exists.
 */
public abstract class SeatBlock extends Block {
    private final Wood wood;

    protected SeatBlock(Wood wood, Properties properties) {
        super(properties);
        this.wood = wood;
    }

    public Wood wood() {
        return wood;
    }

    /** World position of the centre of the sitting surface. */
    public abstract Vec3 seatPosition(BlockState state, BlockPos pos);

    /** Yaw the sitter's body faces, given the yaw the player is looking at when sitting down. */
    public abstract float seatYaw(BlockState state, float playerYaw);

    /** How far (degrees) the sitter may turn the head away from {@link #seatYaw}. */
    public abstract float maxHeadTurn();

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty() || player.isShiftKeyDown()
                || !ServerConfig.rightClickSit()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            SeatManager.sitAndReport(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            SeatEntity.releaseAt(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return wood.flammable() ? 20 : 0;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return wood.flammable() ? 5 : 0;
    }
}
