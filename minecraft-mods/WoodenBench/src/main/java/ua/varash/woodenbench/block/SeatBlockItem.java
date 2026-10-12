package ua.varash.woodenbench.block;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import ua.varash.woodenbench.Wood;

import java.util.List;

/** Block item with a usage hint, burnable in furnaces like other wooden furniture (not the nether woods). */
public class SeatBlockItem extends BlockItem {
    private final Wood wood;

    public SeatBlockItem(Block block, Wood wood, Properties properties) {
        super(block, properties);
        this.wood = wood;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.woodenbench.seat").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.woodenbench.break").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
        return wood.flammable() ? 300 : 0;
    }
}
