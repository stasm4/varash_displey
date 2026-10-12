package ua.varash.enhancedcampfire.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import ua.varash.enhancedcampfire.ModRegistry;
import ua.varash.enhancedcampfire.block.EnhancedCampfireBlock;
import ua.varash.enhancedcampfire.block.EnhancedCampfireBlockEntity;
import ua.varash.enhancedcampfire.config.ClientConfig;

/**
 * Client tick of a campfire (runs on the master block of the 2x2 fire): spawns sparks, embers, ash, smoke and
 * steam, triggers flare-ups and drives the soundscape.  Everything is rate based (expected particles per tick),
 * scaled by distance, the client config and the global particle budget, and skipped beyond the effect distance.
 *
 * <p>Sound design: a quiet, warm "fire bed" loop (long clips with soft fades, overlapping slightly) plus sparse, soft
 * crackle clips and very rare gentle pops.  All clips are low-passed and peak-limited (see tools/gen_sounds.py).
 * Global cooldowns make sure several fires close to each other never stack the same sounds.
 */
public final class CampfireClientEffects {
    /** Minimum ticks between any two bed / crackle / pop clips, across all campfires. */
    private static final int BED_GAP = 110;
    private static final int CRACKLE_GAP = 26;
    private static final int POP_GAP = 400;

    private static long lastBed = Long.MIN_VALUE / 2;
    private static long lastCrackle = Long.MIN_VALUE / 2;
    private static long lastPop = Long.MIN_VALUE / 2;
    private static long lastHiss = Long.MIN_VALUE / 2;

    private CampfireClientEffects() {
    }

    public static void tick(Level level, BlockPos pos, BlockState state, EnhancedCampfireBlockEntity campfire) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        boolean lit = state.getValue(CampfireBlock.LIT);
        boolean smouldering = state.getValue(EnhancedCampfireBlock.SMOULDERING);
        if (!lit && !smouldering) {
            return;
        }
        CampfireFxState fx = CampfireFxState.of(campfire);
        long gameTime = level.getGameTime();
        if (lit && ClientConfig.GROUND_GLOW.get()) {
            fx.refreshGlowCells(level, pos, gameTime);
        }

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double distSq = cam.distanceToSqr(pos.getX() + CampfireLayouts.CENTER, pos.getY() + 0.8D, pos.getZ() + CampfireLayouts.CENTER);
        double maxDist = ClientConfig.EFFECT_DISTANCE.get();
        if (distSq > maxDist * maxDist) {
            return;
        }
        RandomSource random = level.random;
        // distance level of detail and soft budget: thin out spawning well before the hard cap is reached
        float lod = distSq < 400 ? 1.0F : distSq < 1296 ? 0.6F : 0.35F;
        float budget = Math.max(0.1F, Math.min(1.0F, 1.3F - ParticleBudget.load()));
        float mul = lod * budget;

        if (lit) {
            burning(level, pos, state, fx, random, gameTime, mul);
        } else {
            smouldering(level, pos, fx, random, gameTime, mul);
        }
    }

    private static void burning(Level level, BlockPos pos, BlockState state, CampfireFxState fx, RandomSource random,
                                long gameTime, float mul) {
        if (ClientConfig.FLARE_UPS.get() && fx.flare(gameTime, 0.0F) <= 0.0F && random.nextInt(900) == 0) {
            fx.startFlare(gameTime, random);
            if (gameTime - lastPop > POP_GAP / 2) {
                lastPop = gameTime;
                sound(level, pos, ModRegistry.POP.get(), 0.32F, 0.85F + random.nextFloat() * 0.1F);
            }
            sound(level, pos, ModRegistry.ROAR.get(), 0.42F, 0.95F + random.nextFloat() * 0.08F);
        }

        float[][] emitters = CampfireLayouts.emitters(state.getValue(EnhancedCampfireBlock.VARIANT));
        float scale = ClientConfig.FLAME_SCALE.get().floatValue();
        boolean rain = ClientConfig.RAIN_EFFECTS.get() && (level.isRainingAt(pos.above())
                || level.isRainingAt(pos.offset(1, 1, 1)));
        double sparkDensity = ClientConfig.SPARK_DENSITY.get();
        double smokeDensity = ClientConfig.SMOKE_DENSITY.get();
        float windX = Wind.x();
        float windZ = Wind.z();
        float peak = fx.flare(gameTime, 0.0F);
        double cx = pos.getX() + CampfireLayouts.CENTER;
        double cz = pos.getZ() + CampfireLayouts.CENTER;

        // sparks (the big fire throws more of them, and higher) and the occasional big spark
        int sparks = count(random, 0.75F * sparkDensity * mul * (1.0F + peak * 5.0F) * (rain ? 0.6F : 1.0F));
        for (int i = 0; i < sparks; i++) {
            float[] e = emitters[random.nextInt(emitters.length)];
            boolean big = random.nextInt(10) == 0;
            spawn(level, big ? ModRegistry.BIG_SPARK.get() : ModRegistry.SPARK.get(),
                    pos.getX() + e[0] + random.nextGaussian() * 0.1D,
                    pos.getY() + CampfireLayouts.BASE_Y + 0.35D + random.nextFloat() * e[2] * 0.6D * scale,
                    pos.getZ() + e[1] + random.nextGaussian() * 0.1D,
                    (random.nextFloat() - 0.5F) * 0.05D + windX * 0.007D,
                    0.08D + random.nextFloat() * (big ? 0.08D : 0.13D) + peak * 0.08D,
                    (random.nextFloat() - 0.5F) * 0.05D + windZ * 0.007D);
        }

        // small flame licks between the logs (complements the rendered tongues)
        int licks = count(random, 0.7F * mul);
        for (int i = 0; i < licks; i++) {
            float[] e = emitters[random.nextInt(emitters.length)];
            spawn(level, ModRegistry.FLAME_LICK.get(),
                    pos.getX() + e[0] + (random.nextFloat() - 0.5F) * 0.45D,
                    pos.getY() + CampfireLayouts.BASE_Y + 0.05D + random.nextFloat() * 0.4D,
                    pos.getZ() + e[1] + (random.nextFloat() - 0.5F) * 0.45D,
                    windX * 0.003D, 0.02D + random.nextFloat() * 0.03D, windZ * 0.003D);
        }

        // smoke: born dark and dense just above the flame tips, lightens and spreads as it rises (FireSmokeParticle)
        int smoke = count(random, 0.5F * smokeDensity * mul * (rain ? 1.5F : 1.0F) * (1.0F + peak));
        for (int i = 0; i < smoke; i++) {
            spawn(level, ModRegistry.FIRE_SMOKE.get(),
                    cx + random.nextGaussian() * 0.25D,
                    pos.getY() + 1.5D * scale + random.nextFloat() * 0.8D * scale,
                    cz + random.nextGaussian() * 0.25D,
                    windX * 0.008D, 0.04D + random.nextFloat() * 0.03D, windZ * 0.008D);
        }

        // ash flakes
        if (ClientConfig.ASH.get()) {
            int ash = count(random, 0.08F * mul * (1.0F + peak * 3.0F));
            for (int i = 0; i < ash; i++) {
                spawn(level, ModRegistry.ASH.get(),
                        cx + random.nextGaussian() * 0.3D,
                        pos.getY() + 0.9D + random.nextFloat() * 0.8D,
                        cz + random.nextGaussian() * 0.3D,
                        (random.nextFloat() - 0.5F) * 0.025D, 0.06D + random.nextFloat() * 0.07D,
                        (random.nextFloat() - 0.5F) * 0.025D);
            }
        }

        // vanilla signal-fire column (hay bale under the master block) stays exactly like vanilla
        if (state.getValue(CampfireBlock.SIGNAL_FIRE) && random.nextFloat() < 0.11F) {
            for (int i = 0; i < random.nextInt(2) + 2; ++i) {
                CampfireBlock.makeParticles(level, pos, true, false);
            }
        }

        // rain falling into the fire
        if (rain) {
            int steam = count(random, 0.5F * mul);
            for (int i = 0; i < steam; i++) {
                spawn(level, ModRegistry.STEAM.get(),
                        pos.getX() + 0.3D + random.nextFloat() * 1.4D,
                        pos.getY() + 0.4D + random.nextFloat() * 0.6D,
                        pos.getZ() + 0.3D + random.nextFloat() * 1.4D,
                        windX * 0.006D, 0.06D + random.nextFloat() * 0.04D, windZ * 0.006D);
            }
            if (ClientConfig.AMBIENT_SOUNDS.get() && gameTime - lastHiss > 70 && random.nextInt(60) == 0) {
                lastHiss = gameTime;
                sound(level, pos, ModRegistry.HISS.get(), 0.22F + random.nextFloat() * 0.1F, 0.92F + random.nextFloat() * 0.12F);
            }
        }

        if (ClientConfig.AMBIENT_SOUNDS.get()) {
            soundscape(level, pos, fx, random, gameTime, peak);
        }
    }

    /**
     * Calm, non-repetitive soundscape: one warm bed clip every ~6-7 s (clips are 8 s long with soft fades, so they
     * overlap into a continuous murmur), a soft crackle clip every ~2-4 s and a gentle pop roughly once a minute.
     * The static "last played" times are shared by all campfires, so a group of fires sounds like one big fire.
     */
    private static void soundscape(Level level, BlockPos pos, CampfireFxState fx, RandomSource random, long gameTime, float peak) {
        if (fx.nextCrackle == Long.MIN_VALUE) {
            fx.nextCrackle = gameTime + random.nextInt(40);
            fx.nextRoar = gameTime + random.nextInt(60);
        }
        if (gameTime >= fx.nextRoar) {
            fx.nextRoar = gameTime + 125 + random.nextInt(25);
            if (gameTime - lastBed >= BED_GAP) {
                lastBed = gameTime;
                sound(level, pos, ModRegistry.ROAR.get(), (0.5F + random.nextFloat() * 0.08F) * (1.0F + peak * 0.5F),
                        0.96F + random.nextFloat() * 0.08F);
            }
        }
        if (gameTime >= fx.nextCrackle) {
            fx.nextCrackle = gameTime + 45 + random.nextInt(40);
            if (gameTime - lastCrackle >= CRACKLE_GAP) {
                lastCrackle = gameTime;
                sound(level, pos, ModRegistry.CRACKLE.get(), 0.45F + random.nextFloat() * 0.15F, 0.94F + random.nextFloat() * 0.12F);
            }
        }
        if (gameTime - lastPop >= POP_GAP && random.nextInt(1200) == 0) {
            lastPop = gameTime;
            sound(level, pos, ModRegistry.POP.get(), 0.28F + random.nextFloat() * 0.1F, 0.88F + random.nextFloat() * 0.12F);
            // a pop throws a little burst of sparks
            for (int i = 0; i < 4 + random.nextInt(4); i++) {
                spawn(level, i == 0 ? ModRegistry.BIG_SPARK.get() : ModRegistry.SPARK.get(),
                        pos.getX() + CampfireLayouts.CENTER + random.nextGaussian() * 0.15D, pos.getY() + 0.45D,
                        pos.getZ() + CampfireLayouts.CENTER + random.nextGaussian() * 0.15D,
                        random.nextGaussian() * 0.05D, 0.13D + random.nextFloat() * 0.1D, random.nextGaussian() * 0.05D);
            }
        }
    }

    private static void smouldering(Level level, BlockPos pos, CampfireFxState fx, RandomSource random, long gameTime,
                                    float mul) {
        double cx = pos.getX() + CampfireLayouts.CENTER;
        double cz = pos.getZ() + CampfireLayouts.CENTER;
        int smoke = count(random, 0.18F * ClientConfig.SMOKE_DENSITY.get() * mul);
        for (int i = 0; i < smoke; i++) {
            spawn(level, ModRegistry.STEAM.get(), // thin, light, short-lived wisps
                    cx + random.nextGaussian() * 0.25D, pos.getY() + 0.3D, cz + random.nextGaussian() * 0.25D,
                    Wind.x() * 0.004D, 0.025D + random.nextFloat() * 0.02D, Wind.z() * 0.004D);
        }
        if (count(random, 0.03F * ClientConfig.SPARK_DENSITY.get() * mul) > 0) {
            spawn(level, ModRegistry.SPARK.get(), cx + random.nextGaussian() * 0.25D, pos.getY() + 0.15D,
                    cz + random.nextGaussian() * 0.25D, 0.0D, 0.04D + random.nextFloat() * 0.04D, 0.0D);
        }
        if (ClientConfig.AMBIENT_SOUNDS.get()) {
            if (fx.nextCrackle == Long.MIN_VALUE || gameTime >= fx.nextCrackle) {
                if (fx.nextCrackle != Long.MIN_VALUE && gameTime - lastCrackle >= CRACKLE_GAP) {
                    lastCrackle = gameTime;
                    sound(level, pos, ModRegistry.CRACKLE.get(), 0.18F + random.nextFloat() * 0.08F, 1.0F + random.nextFloat() * 0.1F);
                }
                fx.nextCrackle = gameTime + 80 + random.nextInt(120);
            }
        }
    }

    private static int count(RandomSource random, double rate) {
        if (rate <= 0.0D) {
            return 0;
        }
        int n = (int) rate;
        if (random.nextDouble() < rate - n) {
            n++;
        }
        return n;
    }

    private static void spawn(Level level, ParticleOptions type, double x, double y, double z, double vx, double vy, double vz) {
        level.addParticle(type, x, y, z, vx, vy, vz);
    }

    /** Positional sound at the fire centre (distance attenuation comes from sounds.json). */
    private static void sound(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        float v = volume * ClientConfig.SOUND_VOLUME.get().floatValue();
        if (v <= 0.0F) {
            return;
        }
        level.playLocalSound(pos.getX() + CampfireLayouts.CENTER, pos.getY() + 0.6D, pos.getZ() + CampfireLayouts.CENTER,
                sound, SoundSource.BLOCKS, Math.min(v, 1.0F), pitch, false);
    }
}
