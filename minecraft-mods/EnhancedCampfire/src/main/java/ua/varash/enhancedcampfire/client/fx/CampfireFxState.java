package ua.varash.enhancedcampfire.client.fx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import ua.varash.enhancedcampfire.block.EnhancedCampfireBlock;
import ua.varash.enhancedcampfire.block.EnhancedCampfireBlockEntity;

/**
 * Per-campfire client state: a stable seed (so every campfire animates differently), flare-up timing, sound
 * timers and the cached list of ground cells that receive the warm light pool.
 */
public final class CampfireFxState {
    /** Cells from -GLOW_RADIUS to 1 + GLOW_RADIUS around the master block are scanned (the fire is 2x2). */
    public static final int GLOW_RADIUS = 5;
    /** Reach of the light pool, measured from the fire centre (blocks). */
    public static final float GLOW_REACH = 5.6F;

    public final int seed;
    private long flareStart = Long.MIN_VALUE / 2;
    private int flareLength = 1;
    private float flareStrength;

    long nextCrackle = Long.MIN_VALUE;
    long nextRoar = Long.MIN_VALUE;

    private long glowCacheTick = Long.MIN_VALUE / 2;
    /** Packed (dx, dy, dz) triples of ground cells whose top surface receives the light pool. */
    private int[] glowCells = new int[0];

    private CampfireFxState(BlockPos pos) {
        long s = EnhancedCampfireBlock.positionHash(pos);
        this.seed = (int) (s ^ (s >>> 32)) & 0x7fffff;
    }

    public static CampfireFxState of(EnhancedCampfireBlockEntity campfire) {
        if (campfire.clientFx instanceof CampfireFxState state) {
            return state;
        }
        CampfireFxState state = new CampfireFxState(campfire.getBlockPos());
        campfire.clientFx = state;
        return state;
    }

    // ------------------------------------------------------------------ flare-ups

    void startFlare(long gameTime, RandomSource random) {
        flareStart = gameTime;
        flareLength = 25 + random.nextInt(40);
        flareStrength = 0.45F + random.nextFloat() * 0.55F;
    }

    /** 0 when calm, up to ~1 at the peak of a flare-up: fast rise, slow decay. */
    public float flare(long gameTime, float partialTick) {
        float t = (gameTime - flareStart + partialTick) / flareLength;
        if (t < 0.0F || t >= 1.0F) {
            return 0.0F;
        }
        float shape = t < 0.12F ? t / 0.12F : 1.0F - (t - 0.12F) / 0.88F;
        return flareStrength * shape * shape;
    }

    /** Overall "breathing" of the fire, ~0.8..1.1, plus flare boost. Shared by flames, light pool and sparks. */
    public float intensity(float timeSeconds, float flare) {
        return 0.92F + 0.10F * FireNoise.fbm(timeSeconds * 0.55F, seed)
                + 0.04F * FireNoise.noise(timeSeconds * 6.5F, seed + 3) + flare * 0.45F;
    }

    // ------------------------------------------------------------------ ground glow cells

    public int[] glowCells() {
        return glowCells;
    }

    /** Re-scans the surroundings every 2 seconds (staggered per campfire). ~200 cheap block lookups. */
    void refreshGlowCells(Level level, BlockPos pos, long gameTime) {
        if (gameTime - glowCacheTick < 40 + (seed & 7)) {
            return;
        }
        glowCacheTick = gameTime;
        int[] cells = new int[(GLOW_RADIUS * 2 + 2) * (GLOW_RADIUS * 2 + 2) * 3];
        int n = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        for (int dx = -GLOW_RADIUS; dx <= GLOW_RADIUS + 1; dx++) {
            for (int dz = -GLOW_RADIUS; dz <= GLOW_RADIUS + 1; dz++) {
                boolean underFire = (dx == 0 || dx == 1) && (dz == 0 || dz == 1);
                float cx = dx + 0.5F - CampfireLayouts.CENTER;
                float cz = dz + 0.5F - CampfireLayouts.CENTER;
                if (underFire || cx * cx + cz * cz > (GLOW_REACH + 0.5F) * (GLOW_REACH + 0.5F)) {
                    continue;
                }
                for (int dy = 1; dy >= -2; dy--) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    below.set(cursor.getX(), cursor.getY() - 1, cursor.getZ());
                    BlockState here = level.getBlockState(cursor);
                    if (here.isSolidRender(level, cursor)) {
                        continue;
                    }
                    BlockState ground = level.getBlockState(below);
                    if (ground.isFaceSturdy(level, below, Direction.UP)) {
                        cells[n++] = dx;
                        cells[n++] = dy;
                        cells[n++] = dz;
                        break;
                    }
                }
            }
        }
        int[] trimmed = new int[n];
        System.arraycopy(cells, 0, trimmed, 0, n);
        glowCells = trimmed;
    }
}
