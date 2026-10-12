package ua.varash.woodenbench.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Per-world server rules ({@code saves/<world>/serverconfig/woodenbench-server.toml}), synced to clients. */
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue MAX_SIT_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue RIGHT_CLICK_SIT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("seats");
        MAX_SIT_DISTANCE = b.comment("Maximum distance (blocks) between the player's eyes and the seat to sit down.",
                        "Checked on the server together with line of sight.")
                .defineInRange("maxSitDistance", 5.0D, 1.5D, 8.0D);
        RIGHT_CLICK_SIT = b.comment("Also allow sitting down with right click on an empty hand.",
                        "Left click always works; Shift + left click breaks the seat.")
                .define("rightClickSit", false);
        b.pop();
        SPEC = b.build();
    }

    private ServerConfig() {
    }

    public static double maxSitDistance() {
        return SPEC.isLoaded() ? MAX_SIT_DISTANCE.get() : 5.0D;
    }

    public static boolean rightClickSit() {
        return SPEC.isLoaded() && RIGHT_CLICK_SIT.get();
    }
}
