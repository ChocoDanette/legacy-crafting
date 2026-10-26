package com.chocodanette.legacycrafting;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class Config
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue BASE_SECONDS = BUILDER
      .comment("Time of every in-world craft, in seconds, before the time per ingredient is added.")
      .defineInRange("baseSeconds", 1.0, 0.0, 60.0);

    public static final ModConfigSpec.DoubleValue SECONDS_PER_INGREDIENT = BUILDER
      .comment("Time added for each ingredient of the craft, in seconds.")
      .defineInRange("secondsPerIngredient", 0.5, 0.0, 60.0);

    public static final ModConfigSpec.DoubleValue MIN_SECONDS = BUILDER
      .comment("Shortest craft, in seconds, before the player's crafting_time attribute is applied.")
      .defineInRange("minSeconds", 1.0, 0.0, 60.0);

    public static final ModConfigSpec.DoubleValue MAX_SECONDS = BUILDER
      .comment("Longest craft, in seconds, before the player's crafting_time attribute is applied.")
      .defineInRange("maxSeconds", 5.0, 0.0, 600.0);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> RECIPE_SECONDS = BUILDER
      .comment("Fixed times for some recipes, as \"recipe_id=seconds\", for example \"minecraft:stick=1.5\". They replace the",
        "computed time (the player's crafting_time attribute still applies).")
      .defineListAllowEmpty("recipeSeconds", List.of(), () -> "minecraft:stick=1.0", Config::isRecipeSeconds);

    public static final ModConfigSpec.DoubleValue MAX_DISTANCE = BUILDER
      .comment("The craft stops when the player is farther than this from the crafted items, in blocks.")
      .defineInRange("maxDistance", 3.0, 1.0, 16.0);

    public static final ModConfigSpec.BooleanValue DISABLE_INVENTORY_CRAFTING = BUILDER
      .comment("If true, the 2x2 crafting grid of the player's inventory is hidden and cannot be used.")
      .define("disableInventoryCrafting", true);

    public static final ModConfigSpec.BooleanValue WORKBENCH_CRAFTING = BUILDER
      .comment("If true, workbenches (tag legacycrafting:workbenches) keep their items, and their crafts take time like",
        "crafts on the ground.")
      .define("workbenchCrafting", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config()
    {
    }

    /**
     * The server config is synced to clients, but not loaded before a world is joined.
     */
    public static boolean inventoryCraftingDisabled()
    {
        return SPEC.isLoaded() && DISABLE_INVENTORY_CRAFTING.get();
    }

    public static boolean workbenchCraftingEnabled()
    {
        return SPEC.isLoaded() && WORKBENCH_CRAFTING.get();
    }

    private static boolean isRecipeSeconds(final Object value)
    {
        return value instanceof final String text && parseRecipeSeconds(text) != null;
    }

    /**
     * @return the seconds of {@code entry} ("recipe_id=seconds") if its recipe is {@code recipe}, else null.
     */
    public static Double secondsFor(final String entry, final ResourceLocation recipe)
    {
        final Object[] parsed = parseRecipeSeconds(entry);
        return parsed != null && parsed[0].equals(recipe) ? (Double) parsed[1] : null;
    }

    private static Object[] parseRecipeSeconds(final String entry)
    {
        final int equals = entry.lastIndexOf('=');
        if (equals <= 0)
        {
            return null;
        }
        final ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, equals).trim());
        try
        {
            final double seconds = Double.parseDouble(entry.substring(equals + 1).trim());
            return id != null && seconds >= 0 ? new Object[] {id, seconds} : null;
        }
        catch (final NumberFormatException e)
        {
            return null;
        }
    }
}
