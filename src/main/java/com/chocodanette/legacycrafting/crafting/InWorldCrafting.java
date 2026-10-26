package com.chocodanette.legacycrafting.crafting;

import com.chocodanette.legacycrafting.Config;
import com.chocodanette.legacycrafting.ModAttributes;
import com.chocodanette.legacycrafting.network.CraftProgressPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Crafts the items laid on a {@link CraftingSurface}: the player presses the craft key, stays close for the time of the
 * recipe, and the result drops on the surface.
 */
public final class InWorldCrafting
{
    /**
     * Crafts in progress, by player.
     */
    private static final Map<UUID, Craft> CRAFTS = new HashMap<>();

    private InWorldCrafting()
    {
    }

    /**
     * A craft in progress: the items it started with, so that it stops if they change.
     */
    private record Craft(BlockPos pos, Direction facing, List<ItemStack> items, int totalTicks, int[] elapsed)
    {
    }

    /**
     * The 2x2 grid of a surface, as seen by a player looking {@code facing}, with the slot behind each cell.
     */
    private record Grid(CraftingInput.Positioned positioned, int[] slots)
    {
        static Grid of(final CraftingSurface surface, final Direction facing)
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
            return new Grid(CraftingInput.ofPositioned(2, 2, items), slots);
        }

        CraftingInput input()
        {
            return positioned.input();
        }

        /**
         * @return the surface slot under cell {@code index} of the trimmed input.
         */
        int slot(final int index)
        {
            final CraftingInput input = input();
            final int column = positioned.left() + index % input.width();
            final int row = positioned.top() + index / input.width();
            return slots[row * 2 + column];
        }
    }

    /**
     * The player pressed the craft key while looking at {@code target}.
     */
    public static void start(final ServerPlayer player, final BlockPos target)
    {
        final ServerLevel level = player.serverLevel();
        CraftingSurface surface = CraftingSurface.find(level, target);
        if (surface == null)
        {
            // Looking at the ground under the items.
            surface = CraftingSurface.find(level, target.above());
        }
        if (surface == null || !isClose(player, surface.pos()))
        {
            return;
        }
        final Craft current = CRAFTS.get(player.getUUID());
        if (current != null && current.pos.equals(surface.pos()))
        {
            return;
        }

        final Direction facing = player.getDirection();
        final Grid grid = Grid.of(surface, facing);
        final Optional<RecipeHolder<CraftingRecipe>> recipe = findRecipe(level, grid);
        if (recipe.isEmpty())
        {
            stop(player, Component.translatable("legacycrafting.message.no_recipe"));
            return;
        }

        final int ticks = Math.max(1, Mth.ceil(seconds(recipe.get(), grid.input().ingredientCount()) * 20
          * player.getAttributeValue(ModAttributes.CRAFTING_TIME)));
        CRAFTS.put(player.getUUID(), new Craft(surface.pos(), facing, snapshot(surface), ticks, new int[1]));
        PacketDistributor.sendToPlayer(player, new CraftProgressPayload(0f));
    }

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof final ServerPlayer player))
        {
            return;
        }
        final Craft craft = CRAFTS.get(player.getUUID());
        if (craft == null)
        {
            return;
        }
        final CraftingSurface surface = CraftingSurface.find(player.serverLevel(), craft.pos);
        if (!player.isAlive() || surface == null || !sameItems(snapshot(surface), craft.items))
        {
            stop(player, Component.translatable("legacycrafting.message.items_changed"));
            return;
        }
        if (!isClose(player, craft.pos))
        {
            stop(player, Component.translatable("legacycrafting.message.too_far"));
            return;
        }

        final int elapsed = ++craft.elapsed[0];
        if (elapsed % 10 == 0)
        {
            player.serverLevel().playSound(null, craft.pos, SoundEvents.WOOD_HIT, SoundSource.PLAYERS, 0.4f, 1.2f);
        }
        if (elapsed < craft.totalTicks)
        {
            if (elapsed % 2 == 0)
            {
                PacketDistributor.sendToPlayer(player, new CraftProgressPayload((float) elapsed / craft.totalTicks));
            }
            return;
        }

        CRAFTS.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new CraftProgressPayload(-1f));
        if (!finish(player, surface, craft.facing))
        {
            player.displayClientMessage(Component.translatable("legacycrafting.message.no_recipe"), true);
        }
    }

    @SubscribeEvent
    public static void onLogout(final PlayerEvent.PlayerLoggedOutEvent event)
    {
        CRAFTS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangeDimension(final PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof final ServerPlayer player && CRAFTS.containsKey(player.getUUID()))
        {
            stop(player, Component.translatable("legacycrafting.message.too_far"));
        }
    }

    /**
     * Consumes the ingredients, leaves the remainders (damaged tools, empty buckets) in place and drops the result.
     *
     * @return false if the items no longer make a recipe.
     */
    private static boolean finish(final ServerPlayer player, final CraftingSurface surface, final Direction facing)
    {
        final ServerLevel level = player.serverLevel();
        final Grid grid = Grid.of(surface, facing);
        final Optional<RecipeHolder<CraftingRecipe>> found = findRecipe(level, grid);
        if (found.isEmpty())
        {
            return false;
        }
        final CraftingRecipe recipe = found.get().value();
        final CraftingInput input = grid.input();
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
        final Vec3 drop = Vec3.atBottomCenterOf(surface.pos()).add(0, 0.25, 0);
        for (int index = 0; index < input.size(); index++)
        {
            if (input.getItem(index).isEmpty())
            {
                continue;
            }
            final int slot = grid.slot(index);
            final ItemStack stack = surface.get(slot).copy();
            used.add(stack.copyWithCount(1));
            stack.shrink(1);
            final ItemStack remainder = index < remainders.size() ? remainders.get(index) : ItemStack.EMPTY;
            if (stack.isEmpty())
            {
                surface.set(slot, remainder);
            }
            else
            {
                surface.set(slot, stack);
                spawn(level, drop, remainder);
            }
        }
        surface.removeIfEmpty();

        result.onCraftedBy(level, player, result.getCount());
        player.triggerRecipeCrafted(found.get(), used);
        EventHooks.firePlayerCraftingEvent(player, result, new SimpleContainer(used.toArray(ItemStack[]::new)));
        spawn(level, drop, result);
        level.playSound(null, surface.pos(), SoundEvents.VILLAGER_WORK_TOOLSMITH, SoundSource.PLAYERS, 0.8f, 1.0f);
        return true;
    }

    private static Optional<RecipeHolder<CraftingRecipe>> findRecipe(final ServerLevel level, final Grid grid)
    {
        if (grid.input().isEmpty())
        {
            return Optional.empty();
        }
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid.input(), level);
    }

    /**
     * The time of a recipe in seconds, before the player's crafting_time attribute.
     */
    private static double seconds(final RecipeHolder<CraftingRecipe> recipe, final int ingredients)
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

    private static boolean isClose(final ServerPlayer player, final BlockPos pos)
    {
        final double distance = Config.MAX_DISTANCE.get();
        return player.position().distanceToSqr(Vec3.atCenterOf(pos)) <= distance * distance;
    }

    private static List<ItemStack> snapshot(final CraftingSurface surface)
    {
        final List<ItemStack> items = new ArrayList<>(CraftingSurface.SLOTS);
        for (int slot = 0; slot < CraftingSurface.SLOTS; slot++)
        {
            items.add(surface.get(slot).copy());
        }
        return items;
    }

    /**
     * Compares items and counts only: TFC updates the heat and food data of items as they lie on the ground.
     */
    private static boolean sameItems(final List<ItemStack> a, final List<ItemStack> b)
    {
        for (int slot = 0; slot < CraftingSurface.SLOTS; slot++)
        {
            if (!ItemStack.isSameItem(a.get(slot), b.get(slot)) || a.get(slot).getCount() != b.get(slot).getCount())
            {
                return false;
            }
        }
        return true;
    }

    private static void stop(final ServerPlayer player, final Component reason)
    {
        if (CRAFTS.remove(player.getUUID()) != null)
        {
            PacketDistributor.sendToPlayer(player, new CraftProgressPayload(-1f));
        }
        player.displayClientMessage(reason, true);
    }

    private static void spawn(final ServerLevel level, final Vec3 pos, final ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return;
        }
        final ItemEntity entity = new ItemEntity(level, pos.x, pos.y, pos.z, stack, 0, 0.15, 0);
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
    }
}
