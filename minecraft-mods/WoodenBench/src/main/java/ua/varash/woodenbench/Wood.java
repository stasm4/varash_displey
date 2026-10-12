package ua.varash.woodenbench;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/** The wood types every seat is made from. Nether woods are fire-proof, like their planks. */
public enum Wood {
    OAK("oak", MapColor.WOOD, SoundType.WOOD, true),
    SPRUCE("spruce", MapColor.PODZOL, SoundType.WOOD, true),
    BIRCH("birch", MapColor.SAND, SoundType.WOOD, true),
    JUNGLE("jungle", MapColor.DIRT, SoundType.WOOD, true),
    ACACIA("acacia", MapColor.COLOR_ORANGE, SoundType.WOOD, true),
    DARK_OAK("dark_oak", MapColor.COLOR_BROWN, SoundType.WOOD, true),
    MANGROVE("mangrove", MapColor.COLOR_RED, SoundType.WOOD, true),
    CHERRY("cherry", MapColor.TERRACOTTA_WHITE, SoundType.CHERRY_WOOD, true),
    CRIMSON("crimson", MapColor.CRIMSON_STEM, SoundType.NETHER_WOOD, false),
    WARPED("warped", MapColor.WARPED_STEM, SoundType.NETHER_WOOD, false);

    private final String id;
    private final MapColor color;
    private final SoundType sound;
    private final boolean flammable;

    Wood(String id, MapColor color, SoundType sound, boolean flammable) {
        this.id = id;
        this.color = color;
        this.sound = sound;
        this.flammable = flammable;
    }

    public String id() {
        return id;
    }

    public MapColor color() {
        return color;
    }

    public SoundType sound() {
        return sound;
    }

    public boolean flammable() {
        return flammable;
    }
}
