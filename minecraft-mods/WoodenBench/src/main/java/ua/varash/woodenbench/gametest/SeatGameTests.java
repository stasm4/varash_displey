package ua.varash.woodenbench.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ua.varash.woodenbench.ModRegistry;
import ua.varash.woodenbench.Wood;
import ua.varash.woodenbench.WoodenBenchMod;
import ua.varash.woodenbench.block.BenchBlock;
import ua.varash.woodenbench.block.BenchShape;
import ua.varash.woodenbench.block.SeatBoardBlock;
import ua.varash.woodenbench.entity.SeatEntity;
import ua.varash.woodenbench.seat.SeatEvents;
import ua.varash.woodenbench.seat.SeatManager;
import ua.varash.woodenbench.seat.SeatManager.Result;

/**
 * Server-side automated tests ({@code ./gradlew runGameTestServer}) of the sitting mechanic and the bench blocks.
 * Mock players stand where a real player would and go through exactly the same validation as a real request.
 */
@GameTestHolder(WoodenBenchMod.MODID)
@PrefixGameTestTemplate(false)
public final class SeatGameTests {
    private static final BlockPos SEAT = new BlockPos(3, 1, 3);

    private SeatGameTests() {
    }

    private static BlockState bench(Direction facing) {
        return ModRegistry.BENCHES.get(Wood.OAK).get().defaultBlockState().setValue(BenchBlock.FACING, facing);
    }

    /** Places a block the way a player would: its shape is computed from its neighbours first. */
    private static void place(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, Block.updateFromNeighbourShapes(state, helper.getLevel(), helper.absolutePos(pos)));
    }

    /** A mock player standing on the floor {@code dz} blocks south of the seat, looking at it. */
    private static Player playerNear(GameTestHelper helper, int dz) {
        Player player = helper.makeMockPlayer();
        Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(SEAT.south(dz)));
        player.moveTo(feet.x, feet.y, feet.z, 180.0F, 30.0F);
        return player;
    }

    @GameTest(template = "platform")
    public static void benchesJoinIntoLongBench(GameTestHelper helper) {
        // facing north: the sitter's left is west (-x)
        place(helper, SEAT.west(), bench(Direction.NORTH));
        place(helper, SEAT, bench(Direction.NORTH));
        place(helper, SEAT.east(), bench(Direction.NORTH));
        helper.assertTrue(helper.getBlockState(SEAT.west()).getValue(BenchBlock.SHAPE) == BenchShape.LEFT, "west end must be LEFT");
        helper.assertTrue(helper.getBlockState(SEAT).getValue(BenchBlock.SHAPE) == BenchShape.MIDDLE, "centre must be MIDDLE");
        helper.assertTrue(helper.getBlockState(SEAT.east()).getValue(BenchBlock.SHAPE) == BenchShape.RIGHT, "east end must be RIGHT");
        helper.setBlock(SEAT, Blocks.AIR.defaultBlockState());
        helper.assertTrue(helper.getBlockState(SEAT.west()).getValue(BenchBlock.SHAPE) == BenchShape.SINGLE, "split bench must be SINGLE");
        place(helper, SEAT, bench(Direction.SOUTH));
        helper.assertTrue(helper.getBlockState(SEAT).getValue(BenchBlock.SHAPE) == BenchShape.SINGLE,
                "benches facing different ways must not join");
        helper.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void sitOccupyAndStandUp(GameTestHelper helper) {
        helper.setBlock(SEAT, bench(Direction.NORTH));
        Player first = playerNear(helper, 2);
        Result sat = SeatManager.trySit(first, helper.absolutePos(SEAT));
        helper.assertTrue(sat == Result.SAT, "first player must sit, got " + sat);
        helper.assertTrue(first.getVehicle() instanceof SeatEntity, "first player must ride a seat");
        SeatEntity seat = (SeatEntity) first.getVehicle();
        double surface = helper.absolutePos(SEAT).getY() + BenchBlock.SEAT_HEIGHT;
        helper.assertTrue(Math.abs(seat.getY() - surface) < 1.0E-3, "seat must be on the seat surface");
        helper.assertTrue(SeatManager.trySit(first, helper.absolutePos(SEAT)) == Result.ALREADY_SEATED, "no double sitting");

        Player second = playerNear(helper, 2);
        helper.assertTrue(SeatManager.trySit(second, helper.absolutePos(SEAT)) == Result.OCCUPIED, "one player per seat");
        helper.assertTrue(!second.isPassenger(), "second player must stay standing");

        first.stopRiding();
        helper.assertTrue(!first.isPassenger(), "player must be able to stand up");
        helper.assertTrue(helper.getLevel().noCollision(first, first.getBoundingBox()), "must stand up in free space");
        helper.assertTrue(first.position().y >= helper.absolutePos(SEAT).getY() - 0.01D, "must not stand up below the floor");
        // the empty seat entity disappears by itself
        helper.succeedWhen(() -> helper.assertTrue(SeatEntity.find(helper.getLevel(), helper.absolutePos(SEAT)) == null,
                "empty seat entity must be removed"));
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void breakingSeatStandsPlayerUp(GameTestHelper helper) {
        helper.setBlock(SEAT, ModRegistry.SEAT_BOARDS.get(Wood.SPRUCE).get().defaultBlockState());
        Player player = playerNear(helper, 2);
        Result sat = SeatManager.trySit(player, helper.absolutePos(SEAT));
        helper.assertTrue(sat == Result.SAT, "must sit on the seat board, got " + sat);
        double surface = helper.absolutePos(SEAT).getY() + SeatBoardBlock.SEAT_HEIGHT;
        helper.assertTrue(Math.abs(player.getVehicle().getY() - surface) < 1.0E-3, "seat on the board surface");
        helper.getLevel().destroyBlock(helper.absolutePos(SEAT), true);
        helper.assertTrue(!player.isPassenger(), "breaking the seat must stand the player up");
        helper.assertTrue(SeatEntity.find(helper.getLevel(), helper.absolutePos(SEAT)) == null, "seat entity must be gone");
        helper.assertItemEntityPresent(ModRegistry.SEAT_BOARDS.get(Wood.SPRUCE).get().asItem(), SEAT, 2.0D);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void cannotSitWithoutHeadroom(GameTestHelper helper) {
        helper.setBlock(SEAT, bench(Direction.NORTH));
        helper.setBlock(SEAT.above(), Blocks.STONE.defaultBlockState());
        Player player = playerNear(helper, 1);
        helper.assertTrue(SeatManager.trySit(player, helper.absolutePos(SEAT)) == Result.NO_HEADROOM, "needs room above");
        helper.assertTrue(!player.isPassenger(), "must not sit");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void cannotSitFromFarAway(GameTestHelper helper) {
        helper.setBlock(SEAT, bench(Direction.NORTH));
        Player player = helper.makeMockPlayer();
        BlockPos far = helper.absolutePos(SEAT).south(9);
        player.moveTo(far.getX() + 0.5D, far.getY(), far.getZ() + 0.5D, 180.0F, 0.0F);
        helper.assertTrue(SeatManager.trySit(player, helper.absolutePos(SEAT)) == Result.TOO_FAR, "must be within reach");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void cannotSitThroughAWall(GameTestHelper helper) {
        helper.setBlock(SEAT, bench(Direction.NORTH));
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x <= 6; x++) {
                helper.setBlock(new BlockPos(x, y, SEAT.getZ() + 1), Blocks.GLASS.defaultBlockState());
            }
        }
        Player player = playerNear(helper, 2);
        helper.assertTrue(SeatManager.trySit(player, helper.absolutePos(SEAT)) == Result.OBSTRUCTED, "glass wall must block");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void leftClickDoesNotBreakSeats(GameTestHelper helper) {
        helper.setBlock(SEAT, bench(Direction.EAST));
        Player player = playerNear(helper, 2);
        BlockState state = helper.getBlockState(SEAT);
        helper.assertTrue(SeatEvents.protectsFromBreaking(player, state), "plain left click must not break a seat");
        player.setShiftKeyDown(true);
        helper.assertTrue(!SeatEvents.protectsFromBreaking(player, state), "shift + left click must break it");
        helper.assertTrue(!SeatEvents.protectsFromBreaking(player, Blocks.OAK_PLANKS.defaultBlockState()),
                "other blocks must be unaffected");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void dataFilesAreLoaded(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        for (Wood wood : Wood.values()) {
            helper.assertTrue(recipes.byKey(new ResourceLocation(WoodenBenchMod.MODID, wood.id() + "_bench")).isPresent(),
                    "missing bench recipe for " + wood.id());
            helper.assertTrue(recipes.byKey(new ResourceLocation(WoodenBenchMod.MODID, wood.id() + "_seat_board")).isPresent(),
                    "missing seat board recipe for " + wood.id());
        }
        helper.succeed();
    }
}
