package ua.varash.woodenbench.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ua.varash.woodenbench.seat.SeatManager;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Client -> server: "I left-clicked this seat, let me sit on it". Everything is re-validated on the server. */
public record SitRequestPacket(BlockPos pos) {
    /** Simple per-player rate limit (server main thread only). */
    private static final Map<UUID, Long> LAST_REQUEST = new WeakHashMap<>();

    public static void encode(SitRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
    }

    public static SitRequestPacket decode(FriendlyByteBuf buf) {
        return new SitRequestPacket(buf.readBlockPos());
    }

    public static void handle(SitRequestPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = LAST_REQUEST.get(player.getUUID());
        if (last != null && now - last < 5 && now >= last) {
            return;
        }
        LAST_REQUEST.put(player.getUUID(), now);
        SeatManager.sitAndReport(player, packet.pos);
    }
}
