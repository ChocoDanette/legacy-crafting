package com.chocodanette.legacycrafting.client;

import com.chocodanette.legacycrafting.crafting.CraftingSurface;
import com.chocodanette.legacycrafting.crafting.SurfaceGrid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Preview of the craft of the items the player looks at on the ground: the result shows under the crosshair, and
 * when several recipes match, the mouse wheel chooses the one the craft key makes.
 */
public final class GroundPreview
{
    @Nullable
    private static BlockPos pos;
    @Nullable
    private static Direction facing;
    private static List<ItemStack> lastItems = List.of();
    private static List<RecipeHolder<CraftingRecipe>> recipes = List.of();
    private static List<ItemStack> results = List.of();
    private static int selected;

    private GroundPreview()
    {
    }

    /**
     * Finds the recipes of the targeted surface again when the target, the player's facing or the items changed.
     */
    public static void tick(final Minecraft minecraft)
    {
        if (minecraft.player == null || minecraft.level == null
              || !(minecraft.hitResult instanceof final BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
        {
            clear();
            return;
        }
        final CraftingSurface surface = CraftingSurface.findTarget(minecraft.level, hit.getBlockPos());
        if (surface == null)
        {
            clear();
            return;
        }
        final Direction direction = minecraft.player.getDirection();
        final List<ItemStack> items = new ArrayList<>(CraftingSurface.SLOTS);
        for (int slot = 0; slot < CraftingSurface.SLOTS; slot++)
        {
            items.add(surface.get(slot).copy());
        }
        if (surface.pos().equals(pos) && direction == facing && sameItems(items, lastItems))
        {
            return;
        }
        final ResourceLocation chosen = chosen().orElse(null);
        pos = surface.pos();
        facing = direction;
        lastItems = items;
        final SurfaceGrid grid = SurfaceGrid.of(surface, direction);
        recipes = grid.matchingRecipes(minecraft.level);
        results = new ArrayList<>(recipes.size());
        selected = 0;
        for (int index = 0; index < recipes.size(); index++)
        {
            results.add(result(minecraft, recipes.get(index), grid));
            if (recipes.get(index).id().equals(chosen))
            {
                selected = index;
            }
        }
    }

    private static ItemStack result(final Minecraft minecraft, final RecipeHolder<CraftingRecipe> recipe, final SurfaceGrid grid)
    {
        try
        {
            return recipe.value().assemble(grid.input(), minecraft.level.registryAccess());
        }
        catch (final RuntimeException e)
        {
            // Some recipes need the server to assemble their result.
            return recipe.value().getResultItem(minecraft.level.registryAccess());
        }
    }

    private static void clear()
    {
        pos = null;
        facing = null;
        lastItems = List.of();
        recipes = List.of();
        results = List.of();
        selected = 0;
    }

    /**
     * @return the recipe the craft key makes, if the player looks at items that match any.
     */
    public static Optional<ResourceLocation> chosen()
    {
        return recipes.isEmpty() ? Optional.empty() : Optional.of(recipes.get(selected).id());
    }

    /**
     * @return true if the wheel changed the chosen recipe, so that it does not also change the hotbar slot.
     */
    public static boolean scroll(final double delta)
    {
        if (recipes.size() < 2 || delta == 0)
        {
            return false;
        }
        selected = Math.floorMod(selected - (int) Math.signum(delta), recipes.size());
        return true;
    }

    public static void render(final GuiGraphics graphics, final Minecraft minecraft)
    {
        if (results.isEmpty())
        {
            return;
        }
        final ItemStack result = results.get(Math.min(selected, results.size() - 1));
        final int x = graphics.guiWidth() / 2 - 8;
        final int y = graphics.guiHeight() / 2 + 18;
        graphics.renderItem(result, x, y);
        graphics.renderItemDecorations(minecraft.font, result, x, y);
        if (recipes.size() > 1)
        {
            graphics.drawString(minecraft.font, "< " + (selected + 1) + "/" + recipes.size() + " >", x + 20, y + 4, 0xFFFFFF);
        }
    }

    private static boolean sameItems(final List<ItemStack> a, final List<ItemStack> b)
    {
        if (a.size() != b.size())
        {
            return false;
        }
        for (int slot = 0; slot < a.size(); slot++)
        {
            if (!ItemStack.isSameItemSameComponents(a.get(slot), b.get(slot)) || a.get(slot).getCount() != b.get(slot).getCount())
            {
                return false;
            }
        }
        return true;
    }
}
