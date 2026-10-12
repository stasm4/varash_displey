package ua.varash.woodenbench.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ua.varash.woodenbench.ModRegistry;
import ua.varash.woodenbench.block.SeatBlock;

import java.util.List;

/**
 * The invisible "chair" entity a sitting player rides.  It sits exactly on the seat surface, never moves, is never
 * saved (EntityType#noSave) and removes itself as soon as it has no rider or its seat block is gone.
 *
 * <p>Client side it also animates sitting down: the rider glides from where they stood into the seat over a few
 * ticks, and the body turns smoothly towards the seat direction; afterwards the head is limited to a natural
 * range around the seat direction (like in a boat).
 */
public class SeatEntity extends Entity {
    /**
     * Rider offset so that the thighs of the vanilla sitting pose rest on the seat surface: the rider's feet end up
     * 0.57 below the surface (player riding offset is -0.35).
     */
    private static final double RIDER_OFFSET = -0.21D;
    private static final int SIT_ANIMATION_TICKS = 6;

    @Nullable
    private BlockPos seatBlock;
    private int emptyTicks;

    // client-only animation state
    private int sitAnimation = SIT_ANIMATION_TICKS;
    private Vec3 sitFrom = Vec3.ZERO;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    public static SeatEntity create(Level level, BlockPos block, Vec3 surface, float yaw) {
        SeatEntity seat = new SeatEntity(ModRegistry.SEAT.get(), level);
        seat.seatBlock = block.immutable();
        seat.moveTo(surface.x, surface.y, surface.z, yaw, 0.0F);
        seat.setYHeadRot(yaw);
        return seat;
    }

    /** The seat entity standing on {@code pos}, if any. */
    @Nullable
    public static SeatEntity find(Level level, BlockPos pos) {
        List<SeatEntity> seats = level.getEntitiesOfClass(SeatEntity.class, new AABB(pos));
        for (SeatEntity seat : seats) {
            if (seat.isAlive() && pos.equals(seat.seatBlock())) {
                return seat;
            }
        }
        return null;
    }

    /** Stands up everybody sitting on {@code pos} (seat broken, replaced, pushed...). */
    public static void releaseAt(Level level, BlockPos pos) {
        for (SeatEntity seat : level.getEntitiesOfClass(SeatEntity.class, new AABB(pos).inflate(0.1D))) {
            if (pos.equals(seat.seatBlock())) {
                seat.ejectPassengers();
                seat.discard();
            }
        }
    }

    public BlockPos seatBlock() {
        return seatBlock != null ? seatBlock : blockPosition();
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (sitAnimation < SIT_ANIMATION_TICKS) {
                sitAnimation++;
            }
            return;
        }
        BlockState state = level().getBlockState(seatBlock());
        boolean seatGone = !(state.getBlock() instanceof SeatBlock);
        Entity rider = getFirstPassenger();
        boolean riderGone = rider == null || !rider.isAlive();
        emptyTicks = riderGone ? emptyTicks + 1 : 0;
        if (seatGone || emptyTicks > 1) {
            ejectPassengers();
            discard();
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    // ------------------------------------------------------------------ riding

    @Override
    public double getPassengersRidingOffset() {
        return RIDER_OFFSET;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (level().isClientSide) {
            sitAnimation = 0;
            Vec3 target = new Vec3(getX(), getY() + getPassengersRidingOffset() + passenger.getMyRidingOffset(), getZ());
            Vec3 from = passenger.position().subtract(target);
            sitFrom = from.lengthSqr() > 9.0D ? Vec3.ZERO : from; // ignore teleports and odd states
        }
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) {
            return;
        }
        double x = getX();
        double y = getY() + getPassengersRidingOffset() + passenger.getMyRidingOffset();
        double z = getZ();
        boolean animating = level().isClientSide && sitAnimation < SIT_ANIMATION_TICKS;
        if (animating) {
            float k = 1.0F - (float) sitAnimation / SIT_ANIMATION_TICKS;
            k = k * k; // ease out
            x += sitFrom.x * k;
            y += sitFrom.y * k;
            z += sitFrom.z * k;
        }
        move.accept(passenger, x, y, z);
        clampRotation(passenger, animating);
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        clampRotation(passenger, false);
    }

    /**
     * The body always faces the seat direction; the head may turn up to {@link SeatBlock#maxHeadTurn()}.  While
     * sitting down the view is eased into that range instead of snapping.
     */
    private void clampRotation(Entity passenger, boolean smooth) {
        float seatYaw = getYRot();
        passenger.setYBodyRot(seatYaw);
        float limit = level().getBlockState(seatBlock()).getBlock() instanceof SeatBlock seat ? seat.maxHeadTurn() : 110.0F;
        float diff = Mth.wrapDegrees(passenger.getYRot() - seatYaw);
        float clamped = Mth.clamp(diff, -limit, limit);
        if (clamped == diff) {
            return;
        }
        if (smooth) {
            float eased = diff + (clamped - diff) * 0.4F;
            passenger.setYRot(passenger.getYRot() + eased - diff);
        } else {
            passenger.yRotO += clamped - diff;
            passenger.setYRot(passenger.getYRot() + clamped - diff);
        }
        passenger.setYHeadRot(passenger.getYRot());
    }

    /**
     * Stand up next to the seat: in front first, then left, right and behind, at the same height or one block
     * up/down - only on a safe floor with room for the player and never into fire or lava (DismountHelper).  Only
     * the four neighbouring cells are considered, so the seat can never be used to pass through walls.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos base = seatBlock();
        Direction front = Direction.fromYRot(getYRot());
        Direction[] order = {front, front.getCounterClockWise(), front.getClockWise(), front.getOpposite()};
        for (Direction dir : order) {
            BlockPos side = base.relative(dir);
            for (int dy : new int[]{0, 1, -1}) {
                Vec3 spot = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), side.above(dy), true);
                if (spot != null) {
                    return spot;
                }
            }
        }
        // boxed in: stand up on the seat itself (it is not a full block, so there is room above it)
        return new Vec3(getX(), getY() + 0.01D, getZ());
    }
}
