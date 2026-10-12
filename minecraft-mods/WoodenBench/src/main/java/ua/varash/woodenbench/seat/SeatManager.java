package ua.varash.woodenbench.seat;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ua.varash.woodenbench.ModRegistry;
import ua.varash.woodenbench.block.SeatBlock;
import ua.varash.woodenbench.config.ServerConfig;
import ua.varash.woodenbench.entity.SeatEntity;

/**
 * Server-authoritative sitting.  Every request (left click packet or right click) is validated here: the seat
 * must exist and be loaded, be within reach and in line of sight, be free, and have head room above it.
 */
public final class SeatManager {
    public enum Result {
        SAT(null),
        ALREADY_SEATED(null),
        INVALID(null),
        TOO_FAR("message.woodenbench.too_far"),
        OBSTRUCTED("message.woodenbench.obstructed"),
        OCCUPIED("message.woodenbench.occupied"),
        NO_HEADROOM("message.woodenbench.no_headroom");

        private final String message;

        Result(String message) {
            this.message = message;
        }
    }

    private SeatManager() {
    }

    /** Validates and seats the player; failures are shown in the action bar. */
    public static Result sitAndReport(ServerPlayer player, BlockPos pos) {
        Result result = trySit(player, pos);
        if (result.message != null) {
            player.displayClientMessage(Component.translatable(result.message), true);
        }
        return result;
    }

    public static Result trySit(Player player, BlockPos pos) {
        Level level = player.level();
        if (level.isClientSide || player.isSpectator() || !player.isAlive() || player.isSleeping()) {
            return Result.INVALID;
        }
        if (!level.isLoaded(pos)) {
            return Result.INVALID;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SeatBlock seatBlock)) {
            return Result.INVALID;
        }
        Vec3 surface = seatBlock.seatPosition(state, pos);

        // reach
        Vec3 eye = player.getEyePosition();
        double max = ServerConfig.maxSitDistance();
        if (eye.distanceToSqr(surface) > max * max) {
            return Result.TOO_FAR;
        }
        // one sitter per seat block
        SeatEntity existing = SeatEntity.find(level, pos);
        if (existing != null && existing.isVehicle()) {
            return existing.hasPassenger(player) ? Result.ALREADY_SEATED : Result.OCCUPIED;
        }
        // room for the upper body above the seat (also stops sitting into ceilings / suffocation)
        // (narrower than the player so the bench's own backrest does not count)
        AABB body = new AABB(surface.x - 0.2D, surface.y + 0.02D, surface.z - 0.2D,
                surface.x + 0.2D, surface.y + 1.25D, surface.z + 0.2D);
        if (!level.noCollision(body)) {
            return Result.NO_HEADROOM;
        }

        // line of sight to the seat surface (no sitting down through walls or windows)
        Vec3 aim = surface.add(0.0D, 0.05D, 0.0D);
        BlockHitResult hit = level.clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(pos)) {
            return Result.OBSTRUCTED;
        }
        if (player.isPassenger()) {
            player.stopRiding();
        }
        SeatEntity seat = existing != null ? existing : SeatEntity.create(level, pos, surface, seatBlock.seatYaw(state, player.getYRot()));
        if (existing == null && !level.addFreshEntity(seat)) {
            return Result.INVALID;
        }
        if (!player.startRiding(seat, true)) {
            if (existing == null) {
                seat.discard();
            }
            return Result.INVALID;
        }
        level.playSound(null, pos, ModRegistry.SIT_SOUND.get(), SoundSource.BLOCKS, 0.6F, 0.9F + level.random.nextFloat() * 0.2F);
        return Result.SAT;
    }
}
