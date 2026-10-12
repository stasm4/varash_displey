package ua.varash.woodenbench.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import ua.varash.woodenbench.WoodenBenchMod;

public final class Network {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(WoodenBenchMod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Network() {
    }

    public static void register() {
        CHANNEL.messageBuilder(SitRequestPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SitRequestPacket::encode)
                .decoder(SitRequestPacket::decode)
                .consumerMainThread(SitRequestPacket::handle)
                .add();
    }
}
