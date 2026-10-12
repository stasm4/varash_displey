package ua.varash.enhancedcampfire.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ua.varash.enhancedcampfire.EnhancedCampfireMod;
import ua.varash.enhancedcampfire.ModRegistry;
import ua.varash.enhancedcampfire.block.EnhancedCampfireBlock;
import ua.varash.enhancedcampfire.block.EnhancedCampfireBlockEntity;

/**
 * Server-side automated tests ({@code ./gradlew runGameTestServer}).  They check the vanilla-compatible gameplay
 * of the campfire: light, tags, cooking, dousing, smouldering, drops, persistence and data files.
 */
@GameTestHolder(EnhancedCampfireMod.MODID)
@PrefixGameTestTemplate(false)
public final class CampfireGameTests {
    private static final BlockPos POS = new BlockPos(3, 1, 3);

    private CampfireGameTests() {
    }

    private static BlockState place(GameTestHelper helper) {
        helper.setBlock(POS, ModRegistry.CAMPFIRE.get().defaultBlockState());
        return helper.getBlockState(POS);
    }

    @GameTest(template = "platform")
    public static void placedCampfireIsLitBrightAndVanillaCompatible(GameTestHelper helper) {
        BlockState state = place(helper);
        helper.assertTrue(state.getValue(CampfireBlock.LIT), "a new campfire must be lit");
        helper.assertTrue(state.getLightEmission(helper.getLevel(), helper.absolutePos(POS)) == 15, "lit campfire must emit light 15");
        helper.assertTrue(state.is(BlockTags.CAMPFIRES), "must be in #minecraft:campfires (bees, flint and steel, potions)");
        helper.assertTrue(CampfireBlock.isLitCampfire(state), "vanilla must recognise it as a lit campfire");
        helper.assertTrue(helper.getBlockEntity(POS) instanceof EnhancedCampfireBlockEntity, "block entity missing");
        helper.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void cooksRawFood(GameTestHelper helper) {
        place(helper);
        EnhancedCampfireBlockEntity campfire = helper.getBlockEntity(POS);
        ItemStack beef = new ItemStack(Items.BEEF);
        helper.assertTrue(campfire.getCookableRecipe(beef).isPresent(), "raw beef must have a campfire recipe");
        helper.assertTrue(campfire.placeFood(null, beef, 40), "food must be accepted");
        helper.succeedWhen(() -> helper.assertItemEntityPresent(Items.COOKED_BEEF, POS, 2.0D));
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void extinguishedCampfireSmouldersThenRelights(GameTestHelper helper) {
        place(helper);
        helper.runAfterDelay(3, () -> {
            BlockState state = helper.getBlockState(POS);
            // exactly what a shovel / splash water bottle does in vanilla
            CampfireBlock.dowse(null, helper.getLevel(), helper.absolutePos(POS), state);
            helper.setBlock(POS, state.setValue(CampfireBlock.LIT, Boolean.FALSE));
        });
        helper.runAfterDelay(8, () -> {
            BlockState state = helper.getBlockState(POS);
            helper.assertTrue(!state.getValue(CampfireBlock.LIT), "must be extinguished");
            helper.assertTrue(state.getValue(EnhancedCampfireBlock.SMOULDERING), "embers must keep smouldering");
            helper.assertTrue(state.getLightEmission(helper.getLevel(), helper.absolutePos(POS)) == 6, "smouldering light 6");
            helper.assertTrue(CampfireBlock.canLight(state), "flint and steel must be able to relight it");
            helper.setBlock(POS, state.setValue(CampfireBlock.LIT, Boolean.TRUE));
        });
        helper.runAfterDelay(12, () -> {
            BlockState state = helper.getBlockState(POS);
            helper.assertTrue(state.getValue(CampfireBlock.LIT) && !state.getValue(EnhancedCampfireBlock.SMOULDERING),
                    "relit campfire must not be smouldering");
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
            helper.assertTrue(!state.getValue(CampfireBlock.LIT), "water must put the fire out");
            helper.assertTrue(!state.getValue(EnhancedCampfireBlock.SMOULDERING), "drowned embers must not smoulder");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void breakingDropsFoodAndCharcoal(GameTestHelper helper) {
        place(helper);
        EnhancedCampfireBlockEntity campfire = helper.getBlockEntity(POS);
        campfire.placeFood(null, new ItemStack(Items.PORKCHOP), 600);
        helper.getLevel().destroyBlock(helper.absolutePos(POS), true);
        helper.assertItemEntityPresent(Items.PORKCHOP, POS, 2.0D);
        helper.assertItemEntityPresent(Items.CHARCOAL, POS, 2.0D);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void contentsSurviveSaveAndLoad(GameTestHelper helper) {
        BlockState state = place(helper);
        EnhancedCampfireBlockEntity campfire = helper.getBlockEntity(POS);
        campfire.placeFood(null, new ItemStack(Items.CHICKEN), 600);
        CompoundTag tag = campfire.saveWithoutMetadata();
        EnhancedCampfireBlockEntity copy = new EnhancedCampfireBlockEntity(helper.absolutePos(POS), state);
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
        BlockState state = ModRegistry.CAMPFIRE.get().defaultBlockState();
        helper.assertTrue(state.cycle(EnhancedCampfireBlock.VARIANT).getValue(EnhancedCampfireBlock.VARIANT) == 1,
                "layouts must cycle");
        helper.succeed();
    }
}
