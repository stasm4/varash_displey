package ua.varash.enhancedcampfire.client.fx;

import net.minecraft.core.Direction;

/**
 * Flame "tongue" emitters for the three log layouts.  Positions match the gaps between the logs of the
 * generated block models (tools/gen_campfire_assets.py), expressed for a campfire facing north and rotated
 * like the blockstate does for the other facings.
 */
public final class CampfireLayouts {
    /** Height of the ember bed surface the flames rise from (blocks). */
    public static final float BASE_Y = 1.25F / 16.0F;

    /** {x, z, height, width} in blocks, facing north. The first emitter is always the main, tallest tongue. */
    private static final float[][][] EMITTERS = {
            { // 0: teepee
                    {0.50F, 0.50F, 1.45F, 0.42F}, {0.42F, 0.45F, 1.15F, 0.34F}, {0.58F, 0.55F, 1.22F, 0.34F},
                    {0.45F, 0.60F, 1.00F, 0.30F}, {0.57F, 0.40F, 1.05F, 0.30F}, {0.36F, 0.56F, 0.70F, 0.24F},
                    {0.64F, 0.46F, 0.72F, 0.24F}},
            { // 1: log cabin
                    {0.50F, 0.50F, 1.50F, 0.40F}, {0.43F, 0.45F, 1.20F, 0.32F}, {0.57F, 0.56F, 1.25F, 0.32F},
                    {0.45F, 0.58F, 1.00F, 0.28F}, {0.56F, 0.42F, 1.00F, 0.28F}, {0.39F, 0.38F, 0.62F, 0.22F},
                    {0.61F, 0.63F, 0.65F, 0.22F}},
            { // 2: star fire
                    {0.50F, 0.50F, 1.40F, 0.40F}, {0.44F, 0.53F, 1.10F, 0.32F}, {0.56F, 0.47F, 1.15F, 0.32F},
                    {0.50F, 0.42F, 0.90F, 0.28F}, {0.50F, 0.60F, 0.92F, 0.28F}, {0.34F, 0.50F, 0.55F, 0.20F},
                    {0.63F, 0.37F, 0.55F, 0.20F}, {0.62F, 0.64F, 0.50F, 0.20F}},
    };

    /** [variant][facing index][emitter][x, z, height, width] */
    private static final float[][][][] ROTATED = new float[EMITTERS.length][4][][];

    static {
        for (int v = 0; v < EMITTERS.length; v++) {
            for (int f = 0; f < 4; f++) {
                float[][] src = EMITTERS[v];
                float[][] dst = new float[src.length][];
                for (int i = 0; i < src.length; i++) {
                    float x = src[i][0];
                    float z = src[i][1];
                    for (int k = 0; k < f; k++) { // 90 degrees clockwise seen from above, like blockstate "y": 90
                        float nx = 1.0F - z;
                        z = x;
                        x = nx;
                    }
                    dst[i] = new float[]{x, z, src[i][2], src[i][3]};
                }
                ROTATED[v][f] = dst;
            }
        }
    }

    private CampfireLayouts() {
    }

    public static float[][] emitters(int variant, Direction facing) {
        return ROTATED[Math.floorMod(variant, EMITTERS.length)][facingIndex(facing)];
    }

    /** north = 0, east = 1, south = 2, west = 3 - the number of clockwise quarter turns of the model. */
    public static int facingIndex(Direction facing) {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }
}
