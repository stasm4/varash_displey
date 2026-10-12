package ua.varash.enhancedcampfire.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import ua.varash.enhancedcampfire.ModRegistry;
import ua.varash.enhancedcampfire.client.fx.CampfireClientEffects;

import java.util.Optional;

/**
 * Cooking logic mirrors the vanilla campfire block entity exactly (4 slots, same recipes, same timing, cooling
 * when unlit).  In addition the server tracks the lit -> unlit transition to let the embers smoulder for a while.
 */
public class EnhancedCampfireBlockEntity extends BlockEntity implements Clearable {
    public static final int SLOTS = 4;
    public static final int SMOULDER_TICKS = 20 * 60;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final int[] cookingProgress = new int[SLOTS];
    private final int[] cookingTime = new int[SLOTS];
    private final RecipeManager.CachedCheck<Container, CampfireCookingRecipe> quickCheck =
            RecipeManager.createCheck(RecipeType.CAMPFIRE_COOKING);

    private boolean wasLit;
    private int smoulderTicks;

    /**
     * Client-only effect state (flare-ups, sound timers, ground-glow cache). Typed as Object so that this common
     * class never references client classes in a field signature.
     */
    @Nullable
    public Object clientFx;

    public EnhancedCampfireBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.CAMPFIRE_BE.get(), pos, state);
        this.wasLit = state.getValue(CampfireBlock.LIT);
    }

    // ------------------------------------------------------------------ ticking

    public static void serverTick(Level level, BlockPos pos, BlockState state, EnhancedCampfireBlockEntity campfire) {
        boolean lit = state.getValue(CampfireBlock.LIT);
        if (lit) {
            campfire.cookTick(level, pos, state);
        } else {
            campfire.cooldownTick(level, pos, state);
        }
        campfire.smoulderTick(level, pos, state, lit);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, EnhancedCampfireBlockEntity campfire) {
        CampfireClientEffects.tick(level, pos, state, campfire);
    }

    private void smoulderTick(Level level, BlockPos pos, BlockState state, boolean lit) {
        boolean smouldering = state.getValue(EnhancedCampfireBlock.SMOULDERING);
        boolean drowned = state.getValue(CampfireBlock.WATERLOGGED);
        BlockState target = state;
        if (lit) {
            smoulderTicks = 0;
            target = state.setValue(EnhancedCampfireBlock.SMOULDERING, Boolean.FALSE);
        } else if (wasLit) {
            // the fire was just put out (shovel, splash water, waterlogging, redstone-free vanilla means...)
            smoulderTicks = drowned ? 0 : SMOULDER_TICKS;
            target = state.setValue(EnhancedCampfireBlock.SMOULDERING, smoulderTicks > 0);
        } else if (smouldering) {
            if (drowned || smoulderTicks <= 1) {
                smoulderTicks = 0;
                target = state.setValue(EnhancedCampfireBlock.SMOULDERING, Boolean.FALSE);
            } else {
                smoulderTicks--;
            }
        }
        if (wasLit != lit) {
            wasLit = lit;
            setChanged();
        }
        if (target != state) {
            level.setBlock(pos, target, Block.UPDATE_ALL);
        }
    }

    private void cookTick(Level level, BlockPos pos, BlockState state) {
        boolean changed = false;
        for (int i = 0; i < items.size(); ++i) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            changed = true;
            cookingProgress[i]++;
            if (cookingProgress[i] >= cookingTime[i]) {
                Container container = new SimpleContainer(stack);
                ItemStack result = quickCheck.getRecipeFor(container, level)
                        .map(recipe -> recipe.assemble(container, level.registryAccess()))
                        .orElse(stack);
                if (result.isItemEnabled(level.enabledFeatures())) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), result);
                    items.set(i, ItemStack.EMPTY);
                    level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
                    level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
                }
            }
        }
        if (changed) {
            setChanged(level, pos, state);
        }
    }

    private void cooldownTick(Level level, BlockPos pos, BlockState state) {
        boolean changed = false;
        for (int i = 0; i < items.size(); ++i) {
            if (cookingProgress[i] > 0) {
                changed = true;
                cookingProgress[i] = Mth.clamp(cookingProgress[i] - 2, 0, cookingTime[i]);
            }
        }
        if (changed) {
            setChanged(level, pos, state);
        }
    }

    // ------------------------------------------------------------------ food

    public NonNullList<ItemStack> getItems() {
        return items;
    }

    public Optional<CampfireCookingRecipe> getCookableRecipe(ItemStack stack) {
        if (level == null || items.stream().noneMatch(ItemStack::isEmpty)) {
            return Optional.empty();
        }
        return quickCheck.getRecipeFor(new SimpleContainer(stack), level);
    }

    public boolean placeFood(@Nullable Entity entity, ItemStack stack, int time) {
        for (int i = 0; i < items.size(); ++i) {
            if (items.get(i).isEmpty()) {
                cookingTime[i] = time;
                cookingProgress[i] = 0;
                items.set(i, stack.split(1));
                if (level != null) {
                    level.gameEvent(GameEvent.BLOCK_CHANGE, getBlockPos(), GameEvent.Context.of(entity, getBlockState()));
                }
                markUpdated();
                return true;
            }
        }
        return false;
    }

    public int getSmoulderTicks() {
        return smoulderTicks;
    }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // ------------------------------------------------------------------ persistence & sync

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        if (tag.contains("CookingTimes", Tag.TAG_INT_ARRAY)) {
            int[] stored = tag.getIntArray("CookingTimes");
            System.arraycopy(stored, 0, cookingProgress, 0, Math.min(cookingProgress.length, stored.length));
        }
        if (tag.contains("CookingTotalTimes", Tag.TAG_INT_ARRAY)) {
            int[] stored = tag.getIntArray("CookingTotalTimes");
            System.arraycopy(stored, 0, cookingTime, 0, Math.min(cookingTime.length, stored.length));
        }
        if (tag.contains("WasLit")) {
            wasLit = tag.getBoolean("WasLit");
        }
        smoulderTicks = tag.getInt("Smoulder");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items, true);
        tag.putIntArray("CookingTimes", cookingProgress);
        tag.putIntArray("CookingTotalTimes", cookingTime);
        tag.putBoolean("WasLit", wasLit);
        tag.putInt("Smoulder", smoulderTicks);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Only the items are needed by clients (to render food); all visual effects are computed client-side. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, items, true);
        return tag;
    }

    @Override
    public AABB getRenderBoundingBox() {
        // flames rise ~2 blocks; the warm light pool on the ground reaches 3 blocks around
        BlockPos pos = getBlockPos();
        return new AABB(pos.getX() - 3, pos.getY() - 1, pos.getZ() - 3, pos.getX() + 4, pos.getY() + 3, pos.getZ() + 4);
    }
}
