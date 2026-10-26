package com.chocodanette.legacycrafting.crafting;

import com.chocodanette.legacycrafting.Config;
import com.chocodanette.legacycrafting.ModAttributes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * How long a craft takes, on the ground or at a workbench.
 */
public final class CraftTime
{
    private CraftTime()
    {
    }

    /**
     * @return the ticks {@code player} needs to craft {@code recipe} from {@code ingredients} items.
     */
    public static int ticks(final Player player, final RecipeHolder<?> recipe, final int ingredients)
    {
        return Math.max(1, Mth.ceil(seconds(recipe, ingredients) * 20 * player.getAttributeValue(ModAttributes.CRAFTING_TIME)));
    }

    /**
     * The time of a recipe in seconds, before the player's crafting_time attribute.
     */
    private static double seconds(final RecipeHolder<?> recipe, final int ingredients)
    {
        for (final String entry : Config.RECIPE_SECONDS.get())
        {
            final Double seconds = Config.secondsFor(entry, recipe.id());
            if (seconds != null)
            {
                return seconds;
            }
        }
        final double computed = Config.BASE_SECONDS.get() + Config.SECONDS_PER_INGREDIENT.get() * ingredients;
        return Mth.clamp(computed, Config.MIN_SECONDS.get(), Math.max(Config.MIN_SECONDS.get(), Config.MAX_SECONDS.get()));
    }
}
