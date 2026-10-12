package ua.varash.enhancedcampfire.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import ua.varash.enhancedcampfire.ModRegistry;
import ua.varash.enhancedcampfire.client.fx.ParticleBudget;
import ua.varash.enhancedcampfire.client.fx.Wind;
import ua.varash.enhancedcampfire.client.particle.AshParticle;
import ua.varash.enhancedcampfire.client.particle.FireSmokeParticle;
import ua.varash.enhancedcampfire.client.particle.FlameLickParticle;
import ua.varash.enhancedcampfire.client.particle.SparkParticle;
import ua.varash.enhancedcampfire.client.render.EnhancedCampfireRenderer;

/** Client-only registration. Called from the mod constructor on the physical client only. */
public final class ClientSetup {
    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::registerRenderers);
        modBus.addListener(ClientSetup::registerParticles);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onLevelLoad);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onLevelUnload);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.CAMPFIRE_BE.get(), EnhancedCampfireRenderer::new);
    }

    private static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModRegistry.SPARK.get(), SparkParticle.Provider::new);
        event.registerSpriteSet(ModRegistry.BIG_SPARK.get(), SparkParticle.BigProvider::new);
        event.registerSpriteSet(ModRegistry.ASH.get(), AshParticle.Provider::new);
        event.registerSpriteSet(ModRegistry.FIRE_SMOKE.get(), FireSmokeParticle.Provider::new);
        event.registerSpriteSet(ModRegistry.STEAM.get(), FireSmokeParticle.SteamProvider::new);
        event.registerSpriteSet(ModRegistry.FLAME_LICK.get(), FlameLickParticle.Provider::new);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                Wind.update(mc.level);
            }
        }
    }

    private static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel().isClientSide()) {
            ParticleBudget.reset();
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ParticleBudget.reset();
        }
    }
}
