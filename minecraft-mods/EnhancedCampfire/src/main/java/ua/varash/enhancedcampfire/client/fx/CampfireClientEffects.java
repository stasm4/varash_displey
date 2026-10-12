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
 * Client tick of a campfire: spawns sparks, embers, ash, smoke, steam, triggers flare-ups and plays the
 * crackling soundscape.  Everything is rate based (expected particles per tick), scaled by distance, the client
 * config and the global particle budget, and skipped entirely beyond the configured effect distance.
 */
public final class CampfireClientEffects {
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
        double distSq = cam.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
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
        if (ClientConfig.FLARE_UPS.get() && fx.flare(gameTime, 0.0F) <= 0.0F && random.nextInt(700) == 0) {
            fx.startFlare(gameTime, random);
            sound(level, pos, ModRegistry.POP.get(), 1.0F, 0.75F + random.nextFloat() * 0.2F);
            sound(level, pos, ModRegistry.ROAR.get(), 0.7F, 0.9F + random.nextFloat() * 0.2F);
        }

        float[][] emitters = CampfireLayouts.emitters(state.getValue(EnhancedCampfireBlock.VARIANT),
                state.getValue(CampfireBlock.FACING));
        float scale = ClientConfig.FLAME_SCALE.get().floatValue();
        boolean rain = ClientConfig.RAIN_EFFECTS.get() && level.isRainingAt(pos.above());
        double sparkDensity = ClientConfig.SPARK_DENSITY.get();
        double smokeDensity = ClientConfig.SMOKE_DENSITY.get();
        float windX = Wind.x();
        float windZ = Wind.z();
        float peak = fx.flare(gameTime, 0.0F);

        // sparks and the occasional big spark
        int sparks = count(random, 0.40F * sparkDensity * mul * (1.0F + peak * 5.0F) * (rain ? 0.6F : 1.0F));
        for (int i = 0; i < sparks; i++) {
            float[] e = emitters[random.nextInt(emitters.length)];
            boolean big = random.nextInt(14) == 0;
            spawn(level, big ? ModRegistry.BIG_SPARK.get() : ModRegistry.SPARK.get(),
                    pos.getX() + e[0] + random.nextGaussian() * 0.07D,
                    pos.getY() + CampfireLayouts.BASE_Y + 0.25D + random.nextFloat() * e[2] * 0.55D * scale,
                    pos.getZ() + e[1] + random.nextGaussian() * 0.07D,
                    (random.nextFloat() - 0.5F) * 0.045D + windX * 0.006D,
                    0.06D + random.nextFloat() * (big ? 0.07D : 0.11D) + peak * 0.08D,
                    (random.nextFloat() - 0.5F) * 0.045D + windZ * 0.006D);
        }

        // small flame licks between the logs (complements the rendered tongues)
        int licks = count(random, 0.35F * mul);
        for (int i = 0; i < licks; i++) {
            float[] e = emitters[random.nextInt(emitters.length)];
            spawn(level, ModRegistry.FLAME_LICK.get(),
                    pos.getX() + e[0] + (random.nextFloat() - 0.5F) * 0.3D,
                    pos.getY() + CampfireLayouts.BASE_Y + 0.05D + random.nextFloat() * 0.25D,
                    pos.getZ() + e[1] + (random.nextFloat() - 0.5F) * 0.3D,
                    windX * 0.003D, 0.015D + random.nextFloat() * 0.025D, windZ * 0.003D);
        }

        // smoke: born dark and dense just above the flame tips, lightens and spreads as it rises (see FireSmokeParticle)
        int smoke = count(random, 0.30F * smokeDensity * mul * (rain ? 1.5F : 1.0F) * (1.0F + peak));
        for (int i = 0; i < smoke; i++) {
            spawn(level, ModRegistry.FIRE_SMOKE.get(),
                    pos.getX() + 0.5D + random.nextGaussian() * 0.12D,
                    pos.getY() + 0.75D * scale + 0.45D + random.nextFloat() * 0.45D * scale,
                    pos.getZ() + 0.5D + random.nextGaussian() * 0.12D,
                    windX * 0.008D, 0.035D + random.nextFloat() * 0.03D, windZ * 0.008D);
        }

        // rare ash flakes
        if (ClientConfig.ASH.get()) {
            int ash = count(random, 0.05F * mul * (1.0F + peak * 3.0F));
            for (int i = 0; i < ash; i++) {
                spawn(level, ModRegistry.ASH.get(),
                        pos.getX() + 0.5D + random.nextGaussian() * 0.15D,
                        pos.getY() + 0.6D + random.nextFloat() * 0.5D,
                        pos.getZ() + 0.5D + random.nextGaussian() * 0.15D,
                        (random.nextFloat() - 0.5F) * 0.02D, 0.05D + random.nextFloat() * 0.06D,
                        (random.nextFloat() - 0.5F) * 0.02D);
            }
        }

        // vanilla signal-fire column (hay bale under the campfire) stays exactly like vanilla
        if (state.getValue(CampfireBlock.SIGNAL_FIRE) && random.nextFloat() < 0.11F) {
            for (int i = 0; i < random.nextInt(2) + 2; ++i) {
                CampfireBlock.makeParticles(level, pos, true, false);
            }
        }

        // rain falling into the fire
        if (rain) {
            int steam = count(random, 0.35F * mul);
            for (int i = 0; i < steam; i++) {
                spawn(level, ModRegistry.STEAM.get(),
                        pos.getX() + 0.2D + random.nextFloat() * 0.6D,
                        pos.getY() + 0.35D + random.nextFloat() * 0.4D,
                        pos.getZ() + 0.2D + random.nextFloat() * 0.6D,
                        windX * 0.006D, 0.06D + random.nextFloat() * 0.04D, windZ * 0.006D);
            }
            if (ClientConfig.AMBIENT_SOUNDS.get() && random.nextInt(45) == 0) {
                sound(level, pos, ModRegistry.HISS.get(), 0.35F + random.nextFloat() * 0.3F, 0.8F + random.nextFloat() * 0.4F);
            }
        }

        if (!ClientConfig.AMBIENT_SOUNDS.get()) {
            return;
        }
        if (fx.nextCrackle == Long.MIN_VALUE) {
            fx.nextCrackle = gameTime + random.nextInt(40);
            fx.nextRoar = gameTime + random.nextInt(80);
        }
        if (gameTime >= fx.nextCrackle) {
            sound(level, pos, ModRegistry.CRACKLE.get(), 0.35F + random.nextFloat() * 0.4F, 0.85F + random.nextFloat() * 0.3F);
            fx.nextCrackle = gameTime + 8 + (long) (random.nextInt(36) / (1.0F + peak * 2.0F));
        }
        if (gameTime >= fx.nextRoar) {
            sound(level, pos, ModRegistry.ROAR.get(), (0.16F + random.nextFloat() * 0.1F) * (1.0F + peak * 2.0F),
                    0.82F + random.nextFloat() * 0.22F);
            fx.nextRoar = gameTime + 45 + random.nextInt(60);
        }
        if (random.nextInt(260) == 0) {
            sound(level, pos, ModRegistry.POP.get(), 0.55F + random.nextFloat() * 0.4F, 0.8F + random.nextFloat() * 0.45F);
            // a pop throws a little burst of sparks
            for (int i = 0; i < 3 + random.nextInt(4); i++) {
                spawn(level, i == 0 ? ModRegistry.BIG_SPARK.get() : ModRegistry.SPARK.get(),
                        pos.getX() + 0.5D + random.nextGaussian() * 0.1D, pos.getY() + 0.35D,
                        pos.getZ() + 0.5D + random.nextGaussian() * 0.1D,
                        random.nextGaussian() * 0.05D, 0.12D + random.nextFloat() * 0.1D, random.nextGaussian() * 0.05D);
            }
        }
    }

    private static void smouldering(Level level, BlockPos pos, CampfireFxState fx, RandomSource random, long gameTime,
                                    float mul) {
        int smoke = count(random, 0.12F * ClientConfig.SMOKE_DENSITY.get() * mul);
        for (int i = 0; i < smoke; i++) {
            spawn(level, ModRegistry.STEAM.get(), // thin, light, short-lived wisps
                    pos.getX() + 0.5D + random.nextGaussian() * 0.12D, pos.getY() + 0.3D,
                    pos.getZ() + 0.5D + random.nextGaussian() * 0.12D,
                    Wind.x() * 0.004D, 0.025D + random.nextFloat() * 0.02D, Wind.z() * 0.004D);
        }
        if (count(random, 0.02F * ClientConfig.SPARK_DENSITY.get() * mul) > 0) {
            spawn(level, ModRegistry.SPARK.get(), pos.getX() + 0.5D + random.nextGaussian() * 0.12D, pos.getY() + 0.15D,
                    pos.getZ() + 0.5D + random.nextGaussian() * 0.12D, 0.0D, 0.04D + random.nextFloat() * 0.04D, 0.0D);
        }
        if (ClientConfig.AMBIENT_SOUNDS.get()) {
            if (fx.nextCrackle == Long.MIN_VALUE || gameTime >= fx.nextCrackle) {
                if (fx.nextCrackle != Long.MIN_VALUE) {
                    sound(level, pos, ModRegistry.CRACKLE.get(), 0.15F + random.nextFloat() * 0.1F, 1.1F + random.nextFloat() * 0.2F);
                }
                fx.nextCrackle = gameTime + 40 + random.nextInt(100);
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

    private static void sound(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        float v = volume * ClientConfig.SOUND_VOLUME.get().floatValue();
        if (v <= 0.0F) {
            return;
        }
        level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, sound, SoundSource.BLOCKS, v, pitch, false);
    }
}
