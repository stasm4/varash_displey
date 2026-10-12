package ua.varash.woodenbench.autotest;

import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import ua.varash.woodenbench.ModRegistry;
import ua.varash.woodenbench.Wood;
import ua.varash.woodenbench.WoodenBenchMod;
import ua.varash.woodenbench.block.BenchBlock;
import ua.varash.woodenbench.block.SeatBlock;
import ua.varash.woodenbench.block.SeatBoardBlock;
import ua.varash.woodenbench.entity.SeatEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * DEVELOPMENT ONLY (source set "clienttest", run "clientAutotest", never packaged into the mod jar).
 * Drives a real client through the real input pipeline: looks at a bench, presses the attack key (left click),
 * verifies the player sits and the bench is intact, holds Shift to stand up, then Shift + left click breaks the
 * bench.  Takes screenshots of the benches and of a sitting player, writes autotest-result.txt and quits.
 */
@Mod.EventBusSubscriber(modid = WoodenBenchMod.MODID, value = Dist.CLIENT)
public final class BenchClientAutoTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ENABLED = Boolean.getBoolean(WoodenBenchMod.MODID + ".autotest");
    private static final List<String> FAILURES = new ArrayList<>();
    private static final List<String> PASSES = new ArrayList<>();

    private static boolean worldRequested;
    private static int ticks = -1;
    private static BlockPos origin = BlockPos.ZERO;
    private static BlockPos target = BlockPos.ZERO;

    private BenchClientAutoTest() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ENABLED || worldRequested || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        worldRequested = true;
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> {
            LevelSettings settings = new LevelSettings("autotest-bench", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                    new GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("autotest-bench-" + System.currentTimeMillis(), settings,
                    new WorldOptions(20261012L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                            .createWorldDimensions());
        });
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || mc.level == null || server == null) {
            return;
        }
        ticks++;
        try {
            step(mc, server);
        } catch (Throwable t) {
            LOG.error("[autotest] crashed", t);
            fail("exception: " + t);
            finish(mc);
        }
    }

    private static void step(Minecraft mc, MinecraftServer server) {
        switch (ticks) {
            case 40 -> server.execute(() -> buildScene(server));
            case 60 -> {
                mc.options.hideGui = true;
                teleport(server, new Vec3(origin.getX() + 0.5, origin.getY() + 1.2, origin.getZ() - 4.5), 0.0F, 18.0F);
            }
            case 70 -> checkModels(mc);
            case 110 -> shot(mc, "benches_overview");
            case 115 -> teleport(server, new Vec3(origin.getX() + 4.5, origin.getY() + 1.0, origin.getZ() - 2.5), 50.0F, 22.0F);
            case 160 -> shot(mc, "benches_angle");
            case 165 -> {
                // stand in front of the middle bench (which faces north, i.e. towards -z) and look at its seat
                target = origin;
                Vec3 eye = new Vec3(origin.getX() + 0.5, origin.getY() + 1.62, origin.getZ() - 1.6);
                lookAt(server, eye, Vec3.atCenterOf(target).add(0, 0.05, 0));
            }
            case 175 -> {
                check(mc.level.getBlockState(target).getBlock() instanceof BenchBlock, "crosshair target is a bench");
                KeyMapping.click(mc.options.keyAttack.getKey()); // a real left click through vanilla input handling
            }
            case 195 -> {
                check(mc.player.getVehicle() instanceof SeatEntity, "left click on a bench sits the player down");
                check(mc.level.getBlockState(target).getBlock() instanceof BenchBlock, "left click did not break the bench");
                check(mc.level.getBlockState(target).getBlock() instanceof SeatBlock, "bench still present after sitting");
                if (mc.player.getVehicle() != null) {
                    double expectedFeet = target.getY() + BenchBlock.SEAT_HEIGHT - 0.56D;
                    check(Math.abs(mc.player.getY() - expectedFeet) < 0.05D, "sitting height matches the seat surface");
                }
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            case 240 -> shot(mc, "bench_sitting_front");
            case 241 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 280 -> shot(mc, "bench_sitting_back");
            case 281 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 320 -> shot(mc, "bench_sitting_pov");
            case 321 -> mc.options.keyShift.setDown(true);
            case 330 -> {
                mc.options.keyShift.setDown(false);
                check(!mc.player.isPassenger(), "Shift stands the player up");
                check(mc.level.noCollision(mc.player, mc.player.getBoundingBox()), "player stands up in free space");
            }
            case 340 -> {
                // board seat: sit, then break the board under the player (server side) -> must stand up
                target = origin.offset(0, 0, 4);
                Vec3 eye = new Vec3(target.getX() + 0.5, target.getY() + 1.62, target.getZ() - 1.6);
                lookAt(server, eye, Vec3.atCenterOf(target).add(0, 0.02, 0));
            }
            case 350 -> KeyMapping.click(mc.options.keyAttack.getKey());
            case 370 -> {
                check(mc.player.getVehicle() instanceof SeatEntity, "left click on a seat board sits the player down");
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            case 410 -> {
                shot(mc, "seat_board_sitting");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server.execute(() -> server.overworld().destroyBlock(target, true));
            }
            case 420 -> check(!mc.player.isPassenger(), "breaking the seat stands the player up");
            case 425 -> {
                target = origin.east();
                Vec3 eye = new Vec3(target.getX() + 0.5, target.getY() + 1.62, target.getZ() - 1.6);
                lookAt(server, eye, Vec3.atCenterOf(target).add(0, 0.05, 0));
            }
            case 435 -> mc.options.keyShift.setDown(true);
            case 440 -> KeyMapping.click(mc.options.keyAttack.getKey()); // creative: shift + left click breaks instantly
            case 455 -> {
                mc.options.keyShift.setDown(false);
                check(mc.level.getBlockState(target).isAir(), "Shift + left click breaks the bench");
            }
            case 460 -> finish(mc);
            default -> {
            }
        }
    }

    private static void buildScene(MinecraftServer server) {
        ServerLevel level = server.overworld();
        server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        server.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.setDayTime(5000L);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        origin = new BlockPos(0, y, 0);
        // a long oak bench (3 pieces) facing north, i.e. towards the camera at -z
        for (int dx = -1; dx <= 1; dx++) {
            place(level, origin.offset(dx, 0, 0), ModRegistry.BENCHES.get(Wood.OAK).get().defaultBlockState()
                    .setValue(BenchBlock.FACING, Direction.NORTH));
        }
        // single benches of other woods
        Wood[] woods = {Wood.SPRUCE, Wood.BIRCH, Wood.CHERRY, Wood.CRIMSON, Wood.WARPED, Wood.DARK_OAK};
        for (int i = 0; i < woods.length; i++) {
            place(level, origin.offset(-6 + i * 2 + (i >= 3 ? 7 : 0), 0, 1), ModRegistry.BENCHES.get(woods[i]).get()
                    .defaultBlockState().setValue(BenchBlock.FACING, Direction.NORTH));
        }
        // seat boards around a "fire pit"
        place(level, origin.offset(0, 0, 4), ModRegistry.SEAT_BOARDS.get(Wood.SPRUCE).get().defaultBlockState()
                .setValue(SeatBoardBlock.AXIS, Direction.Axis.X));
        place(level, origin.offset(-2, 0, 6), ModRegistry.SEAT_BOARDS.get(Wood.OAK).get().defaultBlockState()
                .setValue(SeatBoardBlock.AXIS, Direction.Axis.Z));
        place(level, origin.offset(2, 0, 6), ModRegistry.SEAT_BOARDS.get(Wood.BIRCH).get().defaultBlockState()
                .setValue(SeatBoardBlock.AXIS, Direction.Axis.Z));
        place(level, origin.offset(0, 0, 6), Blocks.CAMPFIRE.defaultBlockState());
        LOG.info("[autotest] scene built at {}", origin);
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, Block.updateFromNeighbourShapes(state, level, pos), 3);
    }

    private static void lookAt(MinecraftServer server, Vec3 eye, Vec3 at) {
        Vec3 d = at.subtract(eye);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0D);
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        teleport(server, eye.subtract(0, 1.62, 0), yaw, pitch);
    }

    private static void teleport(MinecraftServer server, Vec3 feet, float yaw, float pitch) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.connection.teleport(feet.x, feet.y, feet.z, yaw, pitch);
        });
    }

    private static void checkModels(Minecraft mc) {
        BakedModel missing = mc.getModelManager().getMissingModel();
        int states = 0;
        for (Wood wood : Wood.values()) {
            for (Block block : new Block[]{ModRegistry.BENCHES.get(wood).get(), ModRegistry.SEAT_BOARDS.get(wood).get()}) {
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    states++;
                    if (mc.getBlockRenderer().getBlockModel(state) == missing) {
                        fail("missing block model for " + state);
                        return;
                    }
                }
            }
        }
        pass("all " + states + " block states have models");
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> LOG.info("[autotest] {}", msg.getString()));
        pass("screenshot " + name);
    }

    private static void check(boolean ok, String what) {
        if (ok) {
            PASSES.add(what);
            LOG.info("[autotest] PASS {}", what);
        } else {
            fail(what);
        }
    }

    private static void pass(String what) {
        PASSES.add(what);
        LOG.info("[autotest] PASS {}", what);
    }

    private static void fail(String what) {
        FAILURES.add(what);
        LOG.error("[autotest] FAIL {}", what);
    }

    private static void finish(Minecraft mc) {
        StringBuilder sb = new StringBuilder(FAILURES.isEmpty() ? "RESULT: PASS\n" : "RESULT: FAIL\n");
        PASSES.forEach(p -> sb.append("PASS ").append(p).append('\n'));
        FAILURES.forEach(f -> sb.append("FAIL ").append(f).append('\n'));
        try {
            Files.writeString(Path.of(mc.gameDirectory.getAbsolutePath(), "autotest-result.txt"), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("[autotest] could not write result", e);
        }
        LOG.info("[autotest] done:\n{}", sb);
        mc.stop();
    }
}
