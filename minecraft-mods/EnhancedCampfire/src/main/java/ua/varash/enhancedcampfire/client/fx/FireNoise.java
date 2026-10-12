package ua.varash.enhancedcampfire.client.fx;

import net.minecraft.util.Mth;

/** Cheap, allocation-free 1D value noise used to animate flames, light flicker and wind. */
public final class FireNoise {
    private FireNoise() {
    }

    /** Hash an integer lattice point to [-1, 1]. */
    private static float hash(int n) {
        n = (n << 13) ^ n;
        n = n * (n * n * 15731 + 789221) + 1376312589;
        return 1.0F - (n & 0x7fffffff) / 1073741824.0F;
    }

    /** Smooth 1D value noise in [-1, 1]. */
    public static float noise(float x, int seed) {
        int i = Mth.floor(x);
        float f = x - i;
        float u = f * f * (3.0F - 2.0F * f);
        int s = seed * 7919;
        return Mth.lerp(u, hash(i + s), hash(i + 1 + s));
    }

    /** Three octaves of {@link #noise}, still roughly in [-1, 1]. */
    public static float fbm(float x, int seed) {
        return noise(x, seed) * 0.58F + noise(x * 2.13F + 3.1F, seed + 17) * 0.29F + noise(x * 4.37F + 7.7F, seed + 43) * 0.13F;
    }
}
