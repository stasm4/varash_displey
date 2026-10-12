package ua.varash.woodenbench.block;

import net.minecraft.util.StringRepresentable;

/**
 * Which piece of a (possibly long) bench a block is, seen by the person sitting on it.
 * LEFT is the left end (legs on the left side only), RIGHT the right end, MIDDLE has no legs.
 */
public enum BenchShape implements StringRepresentable {
    SINGLE("single"),
    LEFT("left"),
    MIDDLE("middle"),
    RIGHT("right");

    private final String name;

    BenchShape(String name) {
        this.name = name;
    }

    public static BenchShape of(boolean neighbourLeft, boolean neighbourRight) {
        if (neighbourLeft && neighbourRight) {
            return MIDDLE;
        }
        if (neighbourLeft) {
            return RIGHT;
        }
        return neighbourRight ? LEFT : SINGLE;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
