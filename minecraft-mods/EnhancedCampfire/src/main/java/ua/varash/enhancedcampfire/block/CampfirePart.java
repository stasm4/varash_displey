package ua.varash.enhancedcampfire.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;

/**
 * The four blocks of the 2x2 campfire. MASTER is the north-west block: it owns the block entity (food, smouldering)
 * and renders the whole fire; the other three only occupy space, collide and burn entities.
 */
public enum CampfirePart implements StringRepresentable {
    MASTER("master", 0, 0),
    EAST("east", 1, 0),
    SOUTH("south", 0, 1),
    SOUTH_EAST("south_east", 1, 1);

    private final String name;
    private final int dx;
    private final int dz;

    CampfirePart(String name, int dx, int dz) {
        this.name = name;
        this.dx = dx;
        this.dz = dz;
    }

    public int dx() {
        return dx;
    }

    public int dz() {
        return dz;
    }

    /** Position of the master block, given this part's position. */
    public BlockPos origin(BlockPos pos) {
        return pos.offset(-dx, 0, -dz);
    }

    /** Position of this part for a fire whose master is at {@code origin}. */
    public BlockPos at(BlockPos origin) {
        return origin.offset(dx, 0, dz);
    }

    /** The part at offset (dx, dz) from the master, or null when outside the 2x2 footprint. */
    public static CampfirePart byOffset(int dx, int dz) {
        for (CampfirePart part : values()) {
            if (part.dx == dx && part.dz == dz) {
                return part;
            }
        }
        return null;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
