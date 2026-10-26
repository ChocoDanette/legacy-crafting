package com.chocodanette.legacycrafting.crafting;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * The 2x2 grid of a surface, as seen by a player looking {@code facing}, with the slot behind each cell. Used on both
 * sides: the client previews the recipes, the server crafts them.
 */
public record SurfaceGrid(CraftingInput.Positioned positioned, int[] slots)
{
    public static SurfaceGrid of(final CraftingSurface surface, final Direction facing)
    {
        final List<ItemStack> items = new ArrayList<>(CraftingSurface.SLOTS);
        final int[] slots = new int[CraftingSurface.SLOTS];
        for (int row = 0; row < 2; row++)
        {
            for (int column = 0; column < 2; column++)
            {
                final int slot = CraftingSurface.slotAt(facing, column, row);
                slots[row * 2 + column] = slot;
                items.add(surface.get(slot).copyWithCount(1));
            }
        }
        return new SurfaceGrid(CraftingInput.ofPositioned(2, 2, items), slots);
    }

    public CraftingInput input()
    {
        return positioned.input();
    }

    /**
     * @return the surface slot under cell {@code index} of the trimmed input.
     */
    public int slot(final int index)
    {
        final CraftingInput input = input();
        final int column = positioned.left() + index % input.width();
        final int row = positioned.top() + index / input.width();
        return slots[row * 2 + column];
    }

    /**
     * @return every recipe the grid matches, in the same order on both sides.
     */
    public List<RecipeHolder<CraftingRecipe>> matchingRecipes(final Level level)
    {
        return input().isEmpty() ? List.of() : level.getRecipeManager().getRecipesFor(RecipeType.CRAFTING, input(), level);
    }
}
