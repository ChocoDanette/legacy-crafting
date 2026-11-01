package com.chocodanette.legacycrafting.workbench;

import com.chocodanette.legacycrafting.ModMenus;
import com.chocodanette.legacycrafting.crafting.CraftTime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A workbench that keeps its items: the player lays a 3x3 recipe, then starts the craft, which takes a few seconds
 * while the window stays open.
 */
public class WorkbenchMenu extends AbstractContainerMenu
{
    public static final int GRID_SLOTS = 9;
    public static final int RESULT_SLOT = 9;
    private static final int PLAYER_SLOTS_START = WorkbenchItems.SIZE;
    private static final int PLAYER_SLOTS_END = PLAYER_SLOTS_START + 36;
    private static final double MAX_DISTANCE_SQ = 8 * 8;

    private final Container container;
    private final BlockPos pos;
    /**
     * Most items a series of crafts makes.
     */
    public static final int MAX_ITEMS = 64;

    /**
     * Ticks elapsed and total ticks of the craft in progress, and crafts left including this one, synced to the
     * screen. Total is 0 when idle.
     */
    private final ContainerData progress;
    private List<ItemStack> craftItems = List.of();
    @Nullable
    private ResourceLocation craftRecipe;

    /**
     * Client side.
     */
    public WorkbenchMenu(final int id, final Inventory inventory, final RegistryFriendlyByteBuf buffer)
    {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainer(WorkbenchItems.SIZE));
    }

    public WorkbenchMenu(final int id, final Inventory inventory, final BlockPos pos, final Container container)
    {
        super(ModMenus.WORKBENCH.get(), id);
        this.container = container;
        this.pos = pos;
        this.progress = new SimpleContainerData(3);

        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 3; column++)
            {
                addSlot(new Slot(container, row * 3 + column, 30 + column * 18, 17 + row * 18));
            }
        }
        addSlot(new Slot(container, RESULT_SLOT, 124, 35)
        {
            @Override
            public boolean mayPlace(final ItemStack stack)
            {
                return false;
            }
        });
        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 9; column++)
            {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++)
        {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
        addDataSlots(progress);
    }

    /**
     * @return the progress of the craft from 0 to 1, or a negative value when no craft is running.
     */
    public float progress()
    {
        final int total = progress.get(1);
        return total <= 0 ? -1f : (float) progress.get(0) / total;
    }

    /**
     * @return the crafts left including the one in progress, or 0 when no craft is running.
     */
    public int remaining()
    {
        return progress.get(1) <= 0 ? 0 : progress.get(2);
    }

    /**
     * How many times the grid can make its recipe in a row: the smallest stack of the grid, tools aside (they come back
     * damaged after each craft), or 1 if the grid holds only tools.
     */
    public static int crafts(final List<ItemStack> grid)
    {
        int crafts = Integer.MAX_VALUE;
        for (final ItemStack stack : grid)
        {
            if (!stack.isEmpty() && !stack.isDamageableItem())
            {
                crafts = Math.min(crafts, stack.getCount());
            }
        }
        return crafts == Integer.MAX_VALUE ? 1 : crafts;
    }

    /**
     * The player scrolled over the quantity button: one craft more ({@code delta} > 0) or less. One more takes an
     * item from the player's inventory for each cell below the new count; one less gives back the items above it, so
     * the grid always holds what the crafts will use.
     */
    public void changeQuantity(final ServerPlayer player, final Optional<ResourceLocation> chosen, final int delta)
    {
        if (progress.get(1) > 0 || delta == 0)
        {
            return;
        }
        final ServerLevel level = player.serverLevel();
        final Optional<RecipeHolder<CraftingRecipe>> recipe = findRecipe(level, chosen.orElse(null));
        if (recipe.isEmpty())
        {
            return;
        }
        final int perCraft = Math.max(1, recipe.get().value().assemble(grid().input(), level.registryAccess()).getCount());
        final int target = crafts(gridItems()) + Integer.signum(delta);
        if (target < 1 || target * perCraft > MAX_ITEMS)
        {
            return;
        }
        final Inventory inventory = player.getInventory();
        if (delta < 0)
        {
            for (int slot = 0; slot < GRID_SLOTS; slot++)
            {
                final ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty() && !stack.isDamageableItem() && stack.getCount() > target)
                {
                    give(player, container.removeItem(slot, stack.getCount() - target));
                }
            }
            return;
        }
        // One more: every cell below the count must get its item, or nothing changes.
        final List<Integer> cells = new ArrayList<>();
        for (int slot = 0; slot < GRID_SLOTS; slot++)
        {
            final ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && !stack.isDamageableItem() && stack.getCount() < target)
            {
                if (target > Math.min(stack.getMaxStackSize(), container.getMaxStackSize(stack)))
                {
                    return;
                }
                cells.add(slot);
            }
        }
        final int[] taken = new int[inventory.items.size()];
        final int[] sources = new int[cells.size()];
        for (int index = 0; index < cells.size(); index++)
        {
            final int found = findSameInInventory(inventory, container.getItem(cells.get(index)), taken);
            if (found < 0)
            {
                return;
            }
            taken[found]++;
            sources[index] = found;
        }
        for (int index = 0; index < cells.size(); index++)
        {
            inventory.removeItem(sources[index], 1);
            final ItemStack stack = container.getItem(cells.get(index)).copy();
            stack.grow(1);
            container.setItem(cells.get(index), stack);
        }
    }

    /**
     * @return a slot of the inventory holding the same item as {@code wanted}, components included, with items left
     * once {@code taken} of them are set aside, or -1.
     */
    private static int findSameInInventory(final Inventory inventory, final ItemStack wanted, final int[] taken)
    {
        for (int slot = 0; slot < inventory.items.size(); slot++)
        {
            if (inventory.getItem(slot).getCount() > taken[slot] && ItemStack.isSameItemSameComponents(inventory.getItem(slot), wanted))
            {
                return slot;
            }
        }
        return -1;
    }

    /**
     * The player pressed the craft button, having chosen {@code chosen} among the matching recipes (or the first one
     * if absent), to craft it as many times as the grid allows.
     */
    public void startCraft(final ServerPlayer player, final Optional<ResourceLocation> chosen)
    {
        if (progress.get(1) > 0)
        {
            return;
        }
        final Optional<RecipeHolder<CraftingRecipe>> recipe = findRecipe(player.serverLevel(), chosen.orElse(null));
        if (recipe.isEmpty())
        {
            return;
        }
        progress.set(2, crafts(gridItems()));
        begin(player, recipe.get());
    }

    private void begin(final ServerPlayer player, final RecipeHolder<CraftingRecipe> recipe)
    {
        craftItems = gridItems();
        craftRecipe = recipe.id();
        progress.set(0, 0);
        progress.set(1, CraftTime.ticks(player, recipe, grid().input().ingredientCount()));
    }

    /**
     * Advances the craft in progress, every tick while the window is open.
     */
    public void tick(final ServerPlayer player)
    {
        final int total = progress.get(1);
        if (total <= 0)
        {
            return;
        }
        if (!sameItems(gridItems(), craftItems))
        {
            stopCraft();
            return;
        }
        final int elapsed = progress.get(0) + 1;
        progress.set(0, elapsed);
        if (elapsed % 10 == 0)
        {
            player.serverLevel().playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.PLAYERS, 0.4f, 1.2f);
        }
        if (elapsed >= total)
        {
            final ResourceLocation recipe = craftRecipe;
            final List<ItemStack> before = craftItems;
            final int left = progress.get(2) - 1;
            stopCraft();
            if (finish(player, recipe) && left > 0 && refill(player, before))
            {
                final Optional<RecipeHolder<CraftingRecipe>> next = findRecipe(player.serverLevel(), recipe);
                if (next.isPresent() && next.get().id().equals(recipe))
                {
                    progress.set(2, left);
                    begin(player, next.get());
                }
            }
        }
    }

    private void stopCraft()
    {
        progress.set(0, 0);
        progress.set(1, 0);
        progress.set(2, 0);
        craftItems = List.of();
        craftRecipe = null;
    }

    public CraftingInput.Positioned grid()
    {
        final List<ItemStack> items = new ArrayList<>(GRID_SLOTS);
        for (int slot = 0; slot < GRID_SLOTS; slot++)
        {
            items.add(container.getItem(slot).copyWithCount(1));
        }
        return CraftingInput.ofPositioned(3, 3, items);
    }

    /**
     * @return every recipe the grid matches, in the same order on both sides.
     */
    public List<RecipeHolder<CraftingRecipe>> matchingRecipes(final Level level)
    {
        final CraftingInput input = grid().input();
        return input.isEmpty() ? List.of() : level.getRecipeManager().getRecipesFor(RecipeType.CRAFTING, input, level);
    }

    /**
     * @return the recipe {@code id} if the grid matches it, else the first matching recipe.
     */
    private Optional<RecipeHolder<CraftingRecipe>> findRecipe(final ServerLevel level, @Nullable final ResourceLocation id)
    {
        final List<RecipeHolder<CraftingRecipe>> recipes = matchingRecipes(level);
        return recipes.stream().filter(recipe -> recipe.id().equals(id)).findFirst().or(() -> recipes.stream().findFirst());
    }

    /**
     * Consumes one of each ingredient, puts the remainders back in the grid and the result in the result slot.
     *
     * @return false if the items no longer make a recipe.
     */
    private boolean finish(final ServerPlayer player, @Nullable final ResourceLocation recipeId)
    {
        final ServerLevel level = player.serverLevel();
        final Optional<RecipeHolder<CraftingRecipe>> found = findRecipe(level, recipeId);
        if (found.isEmpty() || !found.get().id().equals(recipeId))
        {
            return false;
        }
        final CraftingRecipe recipe = found.get().value();
        final CraftingInput.Positioned positioned = grid();
        final CraftingInput input = positioned.input();
        final ItemStack result = recipe.assemble(input, level.registryAccess());
        if (result.isEmpty())
        {
            return false;
        }

        // TFC damages tools and gives extra products to the crafting player.
        CommonHooks.setCraftingPlayer(player);
        final NonNullList<ItemStack> remainders = recipe.getRemainingItems(input);
        CommonHooks.setCraftingPlayer(null);

        final List<ItemStack> used = new ArrayList<>();
        for (int index = 0; index < input.size(); index++)
        {
            if (input.getItem(index).isEmpty())
            {
                continue;
            }
            final int slot = (positioned.top() + index / input.width()) * 3 + positioned.left() + index % input.width();
            final ItemStack stack = container.getItem(slot).copy();
            used.add(stack.copyWithCount(1));
            stack.shrink(1);
            final ItemStack remainder = index < remainders.size() ? remainders.get(index) : ItemStack.EMPTY;
            if (stack.isEmpty())
            {
                container.setItem(slot, remainder);
            }
            else
            {
                container.setItem(slot, stack);
                give(player, remainder);
            }
        }

        result.onCraftedBy(level, player, result.getCount());
        player.triggerRecipeCrafted(found.get(), used);
        EventHooks.firePlayerCraftingEvent(player, result, new SimpleContainer(used.toArray(ItemStack[]::new)));
        final ItemStack output = container.getItem(RESULT_SLOT);
        if (output.isEmpty())
        {
            container.setItem(RESULT_SLOT, result);
        }
        else if (ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize())
        {
            output.grow(result.getCount());
            container.setChanged();
        }
        else
        {
            give(player, result);
        }
        level.playSound(null, pos, SoundEvents.VILLAGER_WORK_TOOLSMITH, SoundSource.PLAYERS, 0.8f, 1.0f);
        return true;
    }

    /**
     * Puts one item like the one it held before the craft, taken from the player's inventory, in each grid cell the
     * craft emptied. Cells still holding something (the rest of a stack, a damaged tool) are left as they are.
     *
     * @return false if the inventory lacks one of the items.
     */
    private boolean refill(final ServerPlayer player, final List<ItemStack> before)
    {
        final Inventory inventory = player.getInventory();
        for (int slot = 0; slot < GRID_SLOTS; slot++)
        {
            if (before.get(slot).isEmpty() || !container.getItem(slot).isEmpty())
            {
                continue;
            }
            final int found = findInInventory(inventory, before.get(slot));
            if (found < 0)
            {
                return false;
            }
            container.setItem(slot, inventory.removeItem(found, 1));
        }
        return true;
    }

    private static int findInInventory(final Inventory inventory, final ItemStack wanted)
    {
        for (int slot = 0; slot < inventory.items.size(); slot++)
        {
            if (ItemStack.isSameItem(inventory.getItem(slot), wanted))
            {
                return slot;
            }
        }
        return -1;
    }

    private static void give(final Player player, final ItemStack stack)
    {
        if (!stack.isEmpty())
        {
            player.getInventory().placeItemBackInInventory(stack);
        }
    }

    private List<ItemStack> gridItems()
    {
        final List<ItemStack> items = new ArrayList<>(GRID_SLOTS);
        for (int slot = 0; slot < GRID_SLOTS; slot++)
        {
            items.add(container.getItem(slot).copy());
        }
        return items;
    }

    private static boolean sameItems(final List<ItemStack> a, final List<ItemStack> b)
    {
        if (a.size() != b.size())
        {
            return false;
        }
        for (int slot = 0; slot < a.size(); slot++)
        {
            if (!ItemStack.isSameItem(a.get(slot), b.get(slot)) || a.get(slot).getCount() != b.get(slot).getCount())
            {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index)
    {
        final Slot slot = slots.get(index);
        if (!slot.hasItem())
        {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = slot.getItem();
        final ItemStack copy = stack.copy();
        if (index < PLAYER_SLOTS_START)
        {
            if (!moveItemStackTo(stack, PLAYER_SLOTS_START, PLAYER_SLOTS_END, true))
            {
                return ItemStack.EMPTY;
            }
        }
        else if (!moveItemStackTo(stack, 0, GRID_SLOTS, false))
        {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty())
        {
            slot.setByPlayer(ItemStack.EMPTY);
        }
        else
        {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(final Player player)
    {
        return player.level().getBlockState(pos).is(Workbenches.WORKBENCHES)
          && player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DISTANCE_SQ;
    }
}
