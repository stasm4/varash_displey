package ua.varash.enhancedcampfire;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import ua.varash.enhancedcampfire.client.ClientSetup;
import ua.varash.enhancedcampfire.config.ClientConfig;

@Mod(EnhancedCampfireMod.MODID)
public final class EnhancedCampfireMod {
    public static final String MODID = "enhancedcampfire";

    public EnhancedCampfireMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.register(modBus);
        modBus.addListener(EnhancedCampfireMod::addToCreativeTabs);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Only touched on the physical client, so client classes never load on a dedicated server.
            ClientSetup.init(modBus);
        }
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModRegistry.CAMPFIRE_ITEM.get());
        }
    }
}
