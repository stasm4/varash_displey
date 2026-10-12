package ua.varash.enhancedcampfire.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

/**
 * A single, slowly changing, world-wide wind vector (client side only).  All campfires share it, so neighbouring
 * fires lean and smoke the same way.  Rain and thunder make it stronger and gustier.  Costs a few noise lookups
 * per tick in total.
 */
public final class Wind {
    private static float x;
    private static float z;
    private static long lastTick = Long.MIN_VALUE;

    private Wind() {
    }

    public static void update(ClientLevel level) {
        long tick = level.getGameTime();
        if (tick == lastTick) {
            return;
        }
        lastTick = tick;
        float t = (tick % 2_400_000L) / 20.0F;
        float angle = FireNoise.fbm(t * 0.004F, 911) * (float) (Math.PI * 3.0);
        float strength = 0.35F + 0.3F * FireNoise.fbm(t * 0.03F, 313);
        strength += Math.max(0.0F, FireNoise.fbm(t * 0.35F, 77)) * 0.45F; // gusts
        if (level.isThundering()) {
            strength *= 2.0F;
        } else if (level.isRaining()) {
            strength *= 1.4F;
        }
        strength = Math.max(0.05F, strength);
        x = Mth.cos(angle) * strength;
        z = Mth.sin(angle) * strength;
    }

    /** Wind along X, roughly in [-1.6, 1.6]. */
    public static float x() {
        return x;
    }

    /** Wind along Z, roughly in [-1.6, 1.6]. */
    public static float z() {
        return z;
    }
}
