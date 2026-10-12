package ua.varash.woodenbench.seat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ua.varash.woodenbench.block.SeatBlock;
import ua.varash.woodenbench.entity.SeatEntity;

/** Common (both sides) game event handlers. */
public final class SeatEvents {
    private SeatEvents() {
    }

    /**
     * A plain left click on a seat means "sit down", so it must never start breaking it - on either side.  The
     * client cancels the click earlier and sends a sit request; this server-side guard covers creative instant
     * breaking and any client that still sends a dig packet.  Shift + left click breaks the seat normally.
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (protectsFromBreaking(event.getEntity(), event.getLevel().getBlockState(event.getPos()))) {
            event.setCanceled(true);
        }
    }

    public static boolean protectsFromBreaking(Player player, BlockState state) {
        return state.getBlock() instanceof SeatBlock && !player.isShiftKeyDown();
    }

    /** Players leaving the game stand up first, so they log back in standing next to the seat. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().getVehicle() instanceof SeatEntity) {
            event.getEntity().stopRiding();
        }
    }

    /** Changing dimension (portal, command) always ends sitting. */
    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity().getVehicle() instanceof SeatEntity) {
            event.getEntity().stopRiding();
        }
    }
}
