package ua.varash.enhancedcampfire.client.fx;

/**
 * Flame "tongue" emitters for the three log layouts of the 2x2 fire.  Coordinates are in blocks relative to the
 * master (north-west) block, so the fire centre is (1, 1).  Positions match the gaps between the logs of the
 * generated block models (tools/gen_campfire_assets.py); the model is not rotated, so neither are the emitters.
 */
public final class CampfireLayouts {
    /** Centre of the 2x2 fire, relative to the master block. */
    public static final float CENTER = 1.0F;
    /** Height of the ember bed surface the flames rise from (blocks). */
    public static final float BASE_Y = 1.5F / 16.0F;

    /** {x, z, height, width} in blocks. The first emitter is always the main, tallest tongue. */
    private static final float[][][] EMITTERS = {
            { // 0: big teepee - flames climb up through the leaning logs and burst out of the apex
                    {1.00F, 1.00F, 2.60F, 0.85F},
                    {0.80F, 0.88F, 2.00F, 0.65F}, {1.20F, 1.12F, 2.10F, 0.65F},
                    {0.88F, 1.22F, 1.80F, 0.60F}, {1.15F, 0.80F, 1.85F, 0.60F},
                    {0.66F, 1.05F, 1.30F, 0.45F}, {1.34F, 0.95F, 1.35F, 0.45F},
                    {1.02F, 0.64F, 1.20F, 0.42F}, {0.98F, 1.36F, 1.25F, 0.42F},
                    {0.74F, 0.72F, 0.90F, 0.36F}, {1.27F, 1.28F, 0.95F, 0.36F}},
            { // 1: log cabin - a tall column rising out of the cabin, smaller licks around the inner walls
                    {1.00F, 1.00F, 2.70F, 0.80F},
                    {0.84F, 0.92F, 2.10F, 0.62F}, {1.16F, 1.08F, 2.15F, 0.62F},
                    {0.90F, 1.18F, 1.90F, 0.58F}, {1.12F, 0.84F, 1.90F, 0.58F},
                    {0.70F, 0.75F, 1.40F, 0.44F}, {1.30F, 1.27F, 1.45F, 0.44F},
                    {0.72F, 1.28F, 1.30F, 0.42F}, {1.28F, 0.72F, 1.35F, 0.42F},
                    {1.00F, 0.68F, 1.10F, 0.38F}, {1.00F, 1.32F, 1.10F, 0.38F}},
            { // 2: star fire - a wide, lower fire in the centre plus licks along the burning spoke tips
                    {1.00F, 1.00F, 2.40F, 0.90F},
                    {0.86F, 0.90F, 1.90F, 0.66F}, {1.14F, 1.10F, 1.95F, 0.66F},
                    {0.90F, 1.14F, 1.70F, 0.60F}, {1.12F, 0.86F, 1.75F, 0.60F},
                    {0.66F, 1.00F, 1.10F, 0.42F}, {1.34F, 1.00F, 1.10F, 0.42F},
                    {1.00F, 0.66F, 1.05F, 0.42F}, {1.00F, 1.34F, 1.05F, 0.42F},
                    {0.76F, 0.76F, 0.85F, 0.36F}, {1.24F, 1.24F, 0.85F, 0.36F},
                    {1.24F, 0.76F, 0.80F, 0.34F}},
    };

    private CampfireLayouts() {
    }

    public static float[][] emitters(int variant) {
        return EMITTERS[Math.floorMod(variant, EMITTERS.length)];
    }
}
