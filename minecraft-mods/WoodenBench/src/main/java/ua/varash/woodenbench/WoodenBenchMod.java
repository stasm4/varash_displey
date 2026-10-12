package ua.varash.woodenbench;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import ua.varash.woodenbench.client.ClientSetup;
import ua.varash.woodenbench.config.ServerConfig;
import ua.varash.woodenbench.network.Network;
import ua.varash.woodenbench.seat.SeatEvents;

@Mod(WoodenBenchMod.MODID)
public final class WoodenBenchMod {
    public static final String MODID = "woodenbench";

    public WoodenBenchMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.register(modBus);
        modBus.addListener(WoodenBenchMod::addToCreativeTabs);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        Network.register();
        MinecraftForge.EVENT_BUS.register(SeatEvents.class);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(modBus);
        }
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            ModRegistry.allItems().forEach(item -> event.accept(item.get()));
        }
    }
}
