package ua.varash.woodenbench.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ua.varash.woodenbench.block.SeatBlock;
import ua.varash.woodenbench.entity.SeatEntity;
import ua.varash.woodenbench.network.Network;
import ua.varash.woodenbench.network.SitRequestPacket;

/**
 * Left click on a seat = sit down.
 *
 * <p>Forge fires {@link InputEvent.InteractionKeyMappingTriggered} for the attack key both when the click starts
 * and every tick while it is held, before any block breaking (survival or creative) begins.  When the crosshair
 * is on a seat and the player is not sneaking, the click is cancelled - so the seat never gets damaged - and one
 * sit request is sent per press.  Everywhere else the attack key behaves exactly as in vanilla.
 */
public final class ClientInput {
    /** True from a sit request until the attack key is released, so holding the button sends only one request. */
    private static boolean requestSentForThisPress;

    private ClientInput() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isSpectator() || player.isShiftKeyDown()) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof SeatBlock)) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        boolean alreadyHere = player.getVehicle() instanceof SeatEntity seat && seat.seatBlock().equals(pos);
        if (!requestSentForThisPress && !alreadyHere) {
            requestSentForThisPress = true;
            Network.CHANNEL.sendToServer(new SitRequestPacket(pos));
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !Minecraft.getInstance().options.keyAttack.isDown()) {
            requestSentForThisPress = false;
        }
    }
}
