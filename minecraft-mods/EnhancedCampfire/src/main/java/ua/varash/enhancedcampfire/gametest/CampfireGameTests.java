package ua.varash.enhancedcampfire.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ua.varash.enhancedcampfire.EnhancedCampfireMod;
import ua.varash.enhancedcampfire.ModRegistry;
import ua.varash.enhancedcampfire.block.CampfirePart;
import ua.varash.enhancedcampfire.block.EnhancedCampfireBlock;
import ua.varash.enhancedcampfire.block.EnhancedCampfireBlockEntity;

/**
 * Server-side automated tests ({@code ./gradlew runGameTestServer}) of the 2x2 campfire: multiblock placement,
 * synchronised lit state, removal without duplicated drops, cooking, dousing, smouldering, persistence, data files.
 */
@GameTestHolder(EnhancedCampfireMod.MODID)
@PrefixGameTestTemplate(false)
public final class CampfireGameTests {
    /** Master (north-west) block of the fire; the template floor is at relative y = 1. */
    private static final BlockPos POS = new BlockPos(3, 2, 3);

    private CampfireGameTests() {
    }

    private static EnhancedCampfireBlock block() {
        return ModRegistry.CAMPFIRE.get();
    }

    private static void place(GameTestHelper helper) {
        block().placeFire(helper.getLevel(), helper.absolutePos(POS), 0, Direction.NORTH);
    }

    private static BlockPos rel(CampfirePart part) {
        return part.at(POS);
    }

    private static EnhancedCampfireBlockEntity master(GameTestHelper helper) {
        return (EnhancedCampfireBlockEntity) helper.getBlockEntity(POS);
    }

    private static int countItems(GameTestHelper helper, Item item) {
        int n = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(POS)).inflate(4.0D))) {
            if (entity.getItem().is(item)) {
                n += entity.getItem().getCount();
            }
        }
        return n;
    }

    @GameTest(template = "platform")
    public static void placedFireIsACompleteLit2x2(GameTestHelper helper) {
        place(helper);
        for (CampfirePart part : CampfirePart.values()) {
            BlockState state = helper.getBlockState(rel(part));
            helper.assertTrue(state.is(block()) && state.getValue(EnhancedCampfireBlock.PART) == part, "missing part " + part);
            helper.assertTrue(state.getValue(CampfireBlock.LIT), part + " must be lit");
            helper.assertTrue(state.getLightEmission(helper.getLevel(), helper.absolutePos(rel(part))) == 15, part + " light 15");
            helper.assertTrue(CampfireBlock.isLitCampfire(state), "vanilla must recognise " + part + " as a lit campfire");
            boolean hasEntity = helper.getBlockEntity(rel(part)) instanceof EnhancedCampfireBlockEntity;
            helper.assertTrue(hasEntity == (part == CampfirePart.MASTER), "only the master has a block entity");
        }
        helper.assertTrue(helper.getBlockState(POS).is(BlockTags.CAMPFIRES), "must be in #minecraft:campfires");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void placementNeedsFourFreeBlocks(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(POS);
        helper.assertTrue(EnhancedCampfireBlock.canPlaceAt(helper.getLevel(), origin, null), "empty 2x2 must be placeable");
        helper.setBlock(rel(CampfirePart.SOUTH_EAST), Blocks.STONE.defaultBlockState());
        helper.assertTrue(!EnhancedCampfireBlock.canPlaceAt(helper.getLevel(), origin, null), "a blocked cell must prevent placement");
        helper.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void cooksRawFood(GameTestHelper helper) {
        place(helper);
        EnhancedCampfireBlockEntity campfire = master(helper);
        ItemStack beef = new ItemStack(Items.BEEF);
        helper.assertTrue(campfire.getCookableRecipe(beef).isPresent(), "raw beef must have a campfire recipe");
        helper.assertTrue(campfire.placeFood(null, beef, 40), "food must be accepted");
        helper.succeedWhen(() -> helper.assertItemEntityPresent(Items.COOKED_BEEF, POS, 3.0D));
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void dousingOnePartPutsOutTheWholeFire(GameTestHelper helper) {
        place(helper);
        helper.runAfterDelay(3, () -> {
            BlockPos part = rel(CampfirePart.SOUTH_EAST);
            BlockState state = helper.getBlockState(part);
            // exactly what a shovel / splash water bottle does in vanilla
            CampfireBlock.dowse(null, helper.getLevel(), helper.absolutePos(part), state);
            helper.setBlock(part, state.setValue(CampfireBlock.LIT, Boolean.FALSE));
        });
        helper.runAfterDelay(8, () -> {
            for (CampfirePart part : CampfirePart.values()) {
                helper.assertTrue(!helper.getBlockState(rel(part)).getValue(CampfireBlock.LIT), part + " must be out");
            }
            BlockState master = helper.getBlockState(POS);
            helper.assertTrue(master.getValue(EnhancedCampfireBlock.SMOULDERING), "embers must keep smouldering");
            helper.assertTrue(master.getLightEmission(helper.getLevel(), helper.absolutePos(POS)) == 6, "smouldering light 6");
            helper.assertTrue(CampfireBlock.canLight(helper.getBlockState(rel(CampfirePart.EAST))), "flint and steel must relight it");
            // relight another part, like flint and steel would
            helper.setBlock(rel(CampfirePart.EAST), helper.getBlockState(rel(CampfirePart.EAST)).setValue(CampfireBlock.LIT, Boolean.TRUE));
        });
        helper.runAfterDelay(14, () -> {
            for (CampfirePart part : CampfirePart.values()) {
                helper.assertTrue(helper.getBlockState(rel(part)).getValue(CampfireBlock.LIT), part + " must burn again");
            }
            helper.assertTrue(!helper.getBlockState(POS).getValue(EnhancedCampfireBlock.SMOULDERING), "relit fire must not smoulder");
            helper.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void waterDousesWithoutSmouldering(GameTestHelper helper) {
        place(helper);
        helper.runAfterDelay(3, () -> {
            BlockState state = helper.getBlockState(POS);
            boolean placed = ((CampfireBlock) state.getBlock()).placeLiquid(helper.getLevel(), helper.absolutePos(POS), state,
                    Fluids.WATER.getSource(false));
            helper.assertTrue(placed, "water must be placeable into the campfire");
        });
        helper.runAfterDelay(8, () -> {
            BlockState state = helper.getBlockState(POS);
            helper.assertTrue(state.getValue(CampfireBlock.WATERLOGGED), "must be waterlogged");
            for (CampfirePart part : CampfirePart.values()) {
                helper.assertTrue(!helper.getBlockState(rel(part)).getValue(CampfireBlock.LIT), "water must put out " + part);
            }
            helper.assertTrue(!state.getValue(EnhancedCampfireBlock.SMOULDERING), "drowned embers must not smoulder");
            helper.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void breakingAnyPartRemovesTheFireAndDropsOnce(GameTestHelper helper) {
        place(helper);
        master(helper).placeFood(null, new ItemStack(Items.PORKCHOP), 600);
        helper.getLevel().destroyBlock(helper.absolutePos(rel(CampfirePart.SOUTH_EAST)), true);
        helper.runAfterDelay(2, () -> {
            for (CampfirePart part : CampfirePart.values()) {
                helper.assertTrue(helper.getBlockState(rel(part)).isAir(), part + " must be removed with the fire");
            }
            helper.assertTrue(countItems(helper, Items.CHARCOAL) == 2, "exactly one set of charcoal, got " + countItems(helper, Items.CHARCOAL));
            helper.assertTrue(countItems(helper, Items.COBBLESTONE) == 3, "exactly one set of stones, got " + countItems(helper, Items.COBBLESTONE));
            helper.assertTrue(countItems(helper, Items.PORKCHOP) == 1, "the food on the fire must drop once");
            helper.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void breakingTheMasterRemovesTheWholeFire(GameTestHelper helper) {
        place(helper);
        helper.getLevel().destroyBlock(helper.absolutePos(POS), true);
        helper.runAfterDelay(2, () -> {
            for (CampfirePart part : CampfirePart.values()) {
                helper.assertTrue(helper.getBlockState(rel(part)).isAir(), part + " must be removed with the fire");
            }
            helper.assertTrue(countItems(helper, Items.CHARCOAL) == 2, "exactly one set of charcoal");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void contentsSurviveSaveAndLoad(GameTestHelper helper) {
        place(helper);
        EnhancedCampfireBlockEntity campfire = master(helper);
        campfire.placeFood(null, new ItemStack(Items.CHICKEN), 600);
        CompoundTag tag = campfire.saveWithoutMetadata();
        EnhancedCampfireBlockEntity copy = new EnhancedCampfireBlockEntity(helper.absolutePos(POS), helper.getBlockState(POS));
        copy.load(tag);
        helper.assertTrue(copy.getItems().get(0).is(Items.CHICKEN), "cooking item must persist");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void dataFilesAreLoaded(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        helper.assertTrue(recipes.byKey(new ResourceLocation(EnhancedCampfireMod.MODID, "enhanced_campfire")).isPresent(),
                "crafting recipe missing");
        helper.assertTrue(recipes.byKey(new ResourceLocation(EnhancedCampfireMod.MODID, "enhanced_campfire_from_campfire")).isPresent(),
                "upgrade recipe missing");
        BlockState state = block().defaultBlockState();
        helper.assertTrue(state.cycle(EnhancedCampfireBlock.VARIANT).getValue(EnhancedCampfireBlock.VARIANT) == 1,
                "layouts must cycle");
        helper.succeed();
    }
}
