package com.chocodanette.legacycrafting.workbench;

import com.chocodanette.legacycrafting.ModMenus;
import com.chocodanette.legacycrafting.crafting.CraftTime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;

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
     * Ticks elapsed and total ticks of the craft in progress, synced to the screen. Total is 0 when idle.
     */
    private final ContainerData progress;
    private List<ItemStack> craftItems = List.of();

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
        this.progress = new SimpleContainerData(2);

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
     * The player pressed the craft button.
     */
    public void startCraft(final ServerPlayer player)
    {
        if (progress.get(1) > 0)
        {
            return;
        }
        final Optional<RecipeHolder<CraftingRecipe>> recipe = findRecipe(player.serverLevel());
        if (recipe.isEmpty())
        {
            player.displayClientMessage(Component.translatable("legacycrafting.message.no_recipe"), true);
            return;
        }
        craftItems = gridItems();
        progress.set(0, 0);
        progress.set(1, CraftTime.ticks(player, recipe.get(), grid().input().ingredientCount()));
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
            player.displayClientMessage(Component.translatable("legacycrafting.message.items_changed"), true);
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
            stopCraft();
            if (!finish(player))
            {
                player.displayClientMessage(Component.translatable("legacycrafting.message.no_recipe"), true);
            }
        }
    }

    private void stopCraft()
    {
        progress.set(0, 0);
        progress.set(1, 0);
        craftItems = List.of();
    }

    private CraftingInput.Positioned grid()
    {
        final List<ItemStack> items = new ArrayList<>(GRID_SLOTS);
        for (int slot = 0; slot < GRID_SLOTS; slot++)
        {
            items.add(container.getItem(slot).copyWithCount(1));
        }
        return CraftingInput.ofPositioned(3, 3, items);
    }

    private Optional<RecipeHolder<CraftingRecipe>> findRecipe(final ServerLevel level)
    {
        final CraftingInput input = grid().input();
        return input.isEmpty() ? Optional.empty() : level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
    }

    /**
     * Consumes one of each ingredient, puts the remainders back in the grid and the result in the result slot.
     *
     * @return false if the items no longer make a recipe.
     */
    private boolean finish(final ServerPlayer player)
    {
        final ServerLevel level = player.serverLevel();
        final Optional<RecipeHolder<CraftingRecipe>> found = findRecipe(level);
        if (found.isEmpty())
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
