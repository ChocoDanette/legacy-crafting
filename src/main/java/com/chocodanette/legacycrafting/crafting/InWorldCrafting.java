package com.chocodanette.legacycrafting.crafting;

import com.chocodanette.legacycrafting.Config;
import com.chocodanette.legacycrafting.network.CraftProgressPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Crafts the items laid on a {@link CraftingSurface}: the player presses the craft key, stays close for the time of the
 * recipe, and the result drops on the surface. While the key is held, the craft starts again with the same items taken
 * from the player's inventory.
 */
public final class InWorldCrafting
{
    /**
     * Crafts in progress, by player.
     */
    private static final Map<UUID, Craft> CRAFTS = new HashMap<>();

    /**
     * Players holding the craft key down.
     */
    private static final Set<UUID> HOLDING = new HashSet<>();

    private InWorldCrafting()
    {
    }

    /**
     * A craft in progress: the items it started with, so that it stops if they change.
     */
    private record Craft(BlockPos pos, Direction facing, ResourceLocation recipe, List<ItemStack> items, int totalTicks, int[] elapsed)
    {
    }

    public static void setHolding(final ServerPlayer player, final boolean held)
    {
        if (held)
        {
            HOLDING.add(player.getUUID());
        }
        else
        {
            HOLDING.remove(player.getUUID());
        }
    }

    /**
     * The player pressed the craft key while looking at {@code target}, having chosen {@code chosen} among the
     * matching recipes (or the first one if absent).
     */
    public static void start(final ServerPlayer player, final BlockPos target, final Optional<ResourceLocation> chosen)
    {
        final ServerLevel level = player.serverLevel();
        final CraftingSurface surface = CraftingSurface.findTarget(level, target);
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
        final SurfaceGrid grid = SurfaceGrid.of(surface, facing);
        final Optional<RecipeHolder<CraftingRecipe>> recipe = findRecipe(level, grid, chosen.orElse(null));
        if (recipe.isEmpty())
        {
            stop(player);
            return;
        }

        final int ticks = CraftTime.ticks(player, recipe.get(), grid.input().ingredientCount());
        CRAFTS.put(player.getUUID(), new Craft(surface.pos(), facing, recipe.get().id(), snapshot(surface), ticks, new int[1]));
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
            stop(player);
            return;
        }
        if (!isClose(player, craft.pos))
        {
            stop(player);
            return;
        }

        final int elapsed = ++craft.elapsed[0];
        if (elapsed % 10 == 1)
        {
            player.serverLevel().playSound(null, craft.pos, workSound(craft.items), SoundSource.PLAYERS, 0.5f,
              0.9f + player.getRandom().nextFloat() * 0.3f);
            player.swing(InteractionHand.MAIN_HAND, true);
        }
        if (elapsed % 4 == 0)
        {
            showWork(player.serverLevel(), craft);
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
        final boolean repeat = HOLDING.contains(player.getUUID());
        final Finish finish = finish(player, surface, craft.facing, craft.recipe, repeat);
        if (finish == Finish.FAILED)
        {
            PacketDistributor.sendToPlayer(player, new CraftProgressPayload(-1f));
            return;
        }
        final CraftingSurface next = finish == Finish.REFILLED ? CraftingSurface.find(player.serverLevel(), craft.pos) : null;
        if (next != null && findRecipe(player.serverLevel(), SurfaceGrid.of(next, craft.facing), craft.recipe)
          .filter(recipe -> recipe.id().equals(craft.recipe)).isPresent())
        {
            CRAFTS.put(player.getUUID(), new Craft(craft.pos, craft.facing, craft.recipe, snapshot(next), craft.totalTicks, new int[1]));
            PacketDistributor.sendToPlayer(player, new CraftProgressPayload(0f));
            return;
        }
        PacketDistributor.sendToPlayer(player, new CraftProgressPayload(-1f));
    }

    @SubscribeEvent
    public static void onLogout(final PlayerEvent.PlayerLoggedOutEvent event)
    {
        CRAFTS.remove(event.getEntity().getUUID());
        HOLDING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangeDimension(final PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof final ServerPlayer player && CRAFTS.containsKey(player.getUUID()))
        {
            stop(player);
        }
    }

    private enum Finish
    {
        FAILED,
        DONE,
        REFILLED
    }

    /**
     * Consumes the ingredients, leaves the remainders (damaged tools, empty buckets) in place and drops the result.
     * With {@code refill}, puts the same ingredients back from the player's inventory, before the surface can go away.
     */
    private static Finish finish(final ServerPlayer player, final CraftingSurface surface, final Direction facing,
      final ResourceLocation recipeId, final boolean refill)
    {
        final ServerLevel level = player.serverLevel();
        final SurfaceGrid grid = SurfaceGrid.of(surface, facing);
        final Optional<RecipeHolder<CraftingRecipe>> found = findRecipe(level, grid, recipeId);
        if (found.isEmpty() || !found.get().id().equals(recipeId))
        {
            return Finish.FAILED;
        }
        final CraftingRecipe recipe = found.get().value();
        final CraftingInput input = grid.input();
        final ItemStack result = recipe.assemble(input, level.registryAccess());
        if (result.isEmpty())
        {
            return Finish.FAILED;
        }

        // TFC damages tools and gives extra products to the crafting player.
        CommonHooks.setCraftingPlayer(player);
        final NonNullList<ItemStack> remainders = recipe.getRemainingItems(input);
        CommonHooks.setCraftingPlayer(null);

        final List<ItemStack> used = new ArrayList<>();
        final List<Integer> usedSlots = new ArrayList<>();
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
            usedSlots.add(slot);
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
        final boolean refilled = refill && refill(player, surface, usedSlots, used);
        surface.removeIfEmpty();

        result.onCraftedBy(level, player, result.getCount());
        player.triggerRecipeCrafted(found.get(), used);
        EventHooks.firePlayerCraftingEvent(player, result, new SimpleContainer(used.toArray(ItemStack[]::new)));
        spawn(level, drop, result);
        level.playSound(null, surface.pos(), SoundEvents.VILLAGER_WORK_TOOLSMITH, SoundSource.PLAYERS, 0.8f, 1.0f);
        return refilled ? Finish.REFILLED : Finish.DONE;
    }

    /**
     * Puts one item like each used ingredient, taken from the player's inventory, in each quarter the craft emptied.
     * Quarters still holding something (the rest of a stack, a damaged tool) are left as they are.
     *
     * @return false if the inventory lacks one of the items.
     */
    private static boolean refill(final ServerPlayer player, final CraftingSurface surface, final List<Integer> slots, final List<ItemStack> used)
    {
        final Inventory inventory = player.getInventory();
        for (int index = 0; index < slots.size(); index++)
        {
            final int slot = slots.get(index);
            if (!surface.get(slot).isEmpty())
            {
                continue;
            }
            final int found = findInInventory(inventory, used.get(index));
            if (found < 0)
            {
                return false;
            }
            surface.set(slot, inventory.removeItem(found, 1));
        }
        return true;
    }

    /**
     * Compares items only, like {@link #sameItems}: TFC food in the inventory has another creation date.
     */
    private static int findInInventory(final Inventory inventory, final ItemStack wanted)
    {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            if (ItemStack.isSameItem(inventory.getItem(slot), wanted))
            {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Wood sounds like the saw (an axe stripping a log), blocks like being hit, other items like rustling leather.
     */
    private static SoundEvent workSound(final List<ItemStack> items)
    {
        for (final ItemStack stack : items)
        {
            if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS))
            {
                return SoundEvents.AXE_STRIP;
            }
        }
        for (final ItemStack stack : items)
        {
            if (stack.getItem() instanceof final BlockItem block)
            {
                return block.getBlock().defaultBlockState().getSoundType().getHitSound();
            }
        }
        return SoundEvents.ARMOR_EQUIP_LEATHER.value();
    }

    /**
     * Bits of one of the ingredients fly off the surface.
     */
    private static void showWork(final ServerLevel level, final Craft craft)
    {
        final List<ItemStack> items = craft.items.stream().filter(stack -> !stack.isEmpty()).toList();
        if (items.isEmpty())
        {
            return;
        }
        final ItemStack shown = items.get(level.getRandom().nextInt(items.size())).copyWithCount(1);
        final Vec3 center = Vec3.atBottomCenterOf(craft.pos);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, shown), center.x, center.y + 0.15, center.z, 3, 0.2, 0.05, 0.2, 0.05);
    }

    /**
     * @return the recipe {@code id} if the grid matches it, else the first matching recipe.
     */
    private static Optional<RecipeHolder<CraftingRecipe>> findRecipe(final ServerLevel level, final SurfaceGrid grid,
      @Nullable final ResourceLocation id)
    {
        final List<RecipeHolder<CraftingRecipe>> recipes = grid.matchingRecipes(level);
        return recipes.stream().filter(recipe -> recipe.id().equals(id)).findFirst().or(() -> recipes.stream().findFirst());
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

    private static void stop(final ServerPlayer player)
    {
        if (CRAFTS.remove(player.getUUID()) != null)
        {
            PacketDistributor.sendToPlayer(player, new CraftProgressPayload(-1f));
        }
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
