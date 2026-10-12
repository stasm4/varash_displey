package ua.varash.enhancedcampfire.client.fx;

import ua.varash.enhancedcampfire.config.ClientConfig;

/**
 * Global cap for this mod's particles.  Providers acquire a slot before creating a particle, particles release it
 * in {@code remove()}.  Reset whenever the client level changes (the particle engine is cleared then without
 * calling {@code remove()}).  Only touched on the client main thread.
 */
public final class ParticleBudget {
    private static int live;

    private ParticleBudget() {
    }

    public static boolean tryAcquire() {
        if (live >= ClientConfig.MAX_PARTICLES.get()) {
            return false;
        }
        live++;
        return true;
    }

    public static void release() {
        if (live > 0) {
            live--;
        }
    }

    public static void reset() {
        live = 0;
    }

    /** Fraction of the budget in use, used to thin out spawning before the hard cap is hit. */
    public static float load() {
        return live / (float) ClientConfig.MAX_PARTICLES.get();
    }
}
