package ua.varash.woodenbench.client;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import ua.varash.woodenbench.ModRegistry;

/** Client-only registration, called from the mod constructor on the physical client only. */
public final class ClientSetup {
    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::registerRenderers);
        MinecraftForge.EVENT_BUS.register(ClientInput.class);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // the seat entity is invisible: only the player sitting on it is drawn
        event.registerEntityRenderer(ModRegistry.SEAT.get(), NoopRenderer::new);
    }
}
