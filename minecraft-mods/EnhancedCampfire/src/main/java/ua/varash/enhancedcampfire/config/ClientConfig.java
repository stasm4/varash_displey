package ua.varash.enhancedcampfire.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client-side visual/audio settings, stored in {@code config/enhancedcampfire-client.toml}.
 * Defaults are tuned for a good look on an average PC.
 */
public final class ClientConfig {
    public enum Quality { LOW, MEDIUM, HIGH }

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.EnumValue<Quality> FLAME_QUALITY;
    public static final ForgeConfigSpec.DoubleValue FLAME_SCALE;
    public static final ForgeConfigSpec.DoubleValue FLAME_INTENSITY;
    public static final ForgeConfigSpec.BooleanValue FLARE_UPS;

    public static final ForgeConfigSpec.DoubleValue SPARK_DENSITY;
    public static final ForgeConfigSpec.DoubleValue SMOKE_DENSITY;
    public static final ForgeConfigSpec.BooleanValue ASH;
    public static final ForgeConfigSpec.BooleanValue RAIN_EFFECTS;
    public static final ForgeConfigSpec.IntValue MAX_PARTICLES;

    public static final ForgeConfigSpec.BooleanValue GROUND_GLOW;
    public static final ForgeConfigSpec.DoubleValue GLOW_INTENSITY;

    public static final ForgeConfigSpec.BooleanValue AMBIENT_SOUNDS;
    public static final ForgeConfigSpec.DoubleValue SOUND_VOLUME;

    public static final ForgeConfigSpec.IntValue EFFECT_DISTANCE;
    public static final ForgeConfigSpec.IntValue FLAME_RENDER_DISTANCE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Procedural flame rendering").push("flames");
        FLAME_QUALITY = b.comment("LOW: one flame layer, no frame blending. MEDIUM: two layers. HIGH: three layers with",
                        "smooth frame blending (default). Distant campfires automatically drop to a lower level.")
                .defineEnum("quality", Quality.HIGH);
        FLAME_SCALE = b.comment("Size multiplier of the flames.").defineInRange("scale", 1.0D, 0.4D, 1.6D);
        FLAME_INTENSITY = b.comment("Brightness/opacity multiplier of the flames.").defineInRange("intensity", 1.0D, 0.3D, 1.5D);
        FLARE_UPS = b.comment("Occasional short, strong flare-ups of the fire with a burst of sparks.").define("flareUps", true);
        b.pop();

        b.comment("Particles").push("particles");
        SPARK_DENSITY = b.comment("Multiplier for sparks and embers (0 disables them).").defineInRange("sparkDensity", 1.0D, 0.0D, 2.5D);
        SMOKE_DENSITY = b.comment("Multiplier for smoke (0 disables it).").defineInRange("smokeDensity", 1.0D, 0.0D, 2.5D);
        ASH = b.comment("Rare dark ash flakes drifting out of the fire.").define("ash", true);
        RAIN_EFFECTS = b.comment("Steam and hissing when rain falls into the fire.").define("rainEffects", true);
        MAX_PARTICLES = b.comment("Hard cap of simultaneously alive particles from all campfires together.")
                .defineInRange("maxParticles", 600, 50, 4000);
        b.pop();

        b.comment("Light").push("light");
        GROUND_GLOW = b.comment("Flickering warm light pool drawn on the ground around a burning campfire.",
                "(Minecraft's block light itself is static; this is a visual overlay.)").define("groundGlow", true);
        GLOW_INTENSITY = b.comment("Strength of the light pool and ember glow.").defineInRange("glowIntensity", 1.0D, 0.0D, 2.0D);
        b.pop();

        b.comment("Sound").push("sound");
        AMBIENT_SOUNDS = b.comment("Crackling, popping, roaring and hissing sounds.").define("ambientSounds", true);
        SOUND_VOLUME = b.comment("Volume multiplier for the campfire sounds.").defineInRange("volume", 1.0D, 0.0D, 2.0D);
        b.pop();

        b.comment("Performance").push("performance");
        EFFECT_DISTANCE = b.comment("Particles and sounds are only produced for campfires closer than this (blocks).")
                .defineInRange("effectDistance", 48, 8, 128);
        FLAME_RENDER_DISTANCE = b.comment("Flames are rendered up to this distance (blocks).")
                .defineInRange("flameRenderDistance", 96, 16, 256);
        b.pop();

        SPEC = b.build();
    }

    private ClientConfig() {
    }
}
