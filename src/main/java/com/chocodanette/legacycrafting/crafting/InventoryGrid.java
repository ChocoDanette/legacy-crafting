package com.chocodanette.legacycrafting.crafting;

import com.chocodanette.legacycrafting.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Takes the 2x2 crafting grid out of the player's inventory: crafting happens in the world. Other mods' menus with their
 * own 2x2 grid (Curios' inventory, for example) are locked the same way, unless listed in {@code allowedCraftingMenus}.
 */
public final class InventoryGrid
{
    private InventoryGrid()
    {
    }

    /**
     * A slot that is neither drawn nor usable.
     */
    private static final class LockedSlot extends Slot
    {
        LockedSlot(final Slot slot)
        {
            super(slot.container, slot.getContainerSlot(), slot.x, slot.y);
            this.index = slot.index;
        }

        @Override
        public boolean isActive()
        {
            return false;
        }

        @Override
        public boolean mayPlace(final ItemStack stack)
        {
            return false;
        }

        @Override
        public boolean mayPickup(final Player player)
        {
            return false;
        }
    }

    /**
     * The inventory menu is made with the player, on both sides: lock its result and grid slots once the player
     * joins a level (also after respawning, which makes a new player).
     */
    @SubscribeEvent
    public static void onJoinLevel(final EntityJoinLevelEvent event)
    {
        if (!(event.getEntity() instanceof final Player player) || !Config.inventoryCraftingDisabled())
        {
            return;
        }
        final InventoryMenu menu = player.inventoryMenu;
        for (int index = InventoryMenu.RESULT_SLOT; index < InventoryMenu.CRAFT_SLOT_END; index++)
        {
            final Slot slot = menu.slots.get(index);
            if (!(slot instanceof LockedSlot))
            {
                menu.slots.set(index, new LockedSlot(slot));
            }
        }
    }

    /**
     * The 2x2 crafting grid of a menu other than the player's inventory, or null if it has none (or a bigger grid too,
     * like a crafting table) or is allowed by the config.
     */
    @Nullable
    public static CraftingContainer lockedGrid(final AbstractContainerMenu menu)
    {
        if (menu instanceof InventoryMenu || !Config.inventoryCraftingDisabled())
        {
            return null;
        }
        CraftingContainer grid = null;
        for (final Slot slot : menu.slots)
        {
            if (slot.container instanceof final CraftingContainer crafting)
            {
                if (crafting.getWidth() != 2 || crafting.getHeight() != 2)
                {
                    return null;
                }
                grid = crafting;
            }
        }
        return grid == null || isAllowed(menu) ? null : grid;
    }

    private static boolean isAllowed(final AbstractContainerMenu menu)
    {
        final List<? extends String> allowed = Config.allowedCraftingMenus();
        if (allowed.isEmpty())
        {
            return false;
        }
        if (allowed.contains(menu.getClass().getName()))
        {
            return true;
        }
        try
        {
            final ResourceLocation type = BuiltInRegistries.MENU.getKey(menu.getType());
            return type != null && allowed.contains(type.toString());
        }
        catch (final UnsupportedOperationException e)
        {
            // A menu without a type.
            return false;
        }
    }

    /**
     * Locks the slots of the grid and the result slots of a menu. Some menus make their slots again (Curios, on a page
     * change), so this runs every tick.
     */
    private static void lock(final AbstractContainerMenu menu, final CraftingContainer grid)
    {
        for (int index = 0; index < menu.slots.size(); index++)
        {
            final Slot slot = menu.slots.get(index);
            if (!(slot instanceof LockedSlot) && (slot instanceof ResultSlot || slot.container == grid))
            {
                menu.slots.set(index, new LockedSlot(slot));
            }
        }
    }

    /**
     * The recipe book and other mods can still move items into the grid: give them back.
     */
    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event)
    {
        final Player player = event.getEntity();
        if (!Config.inventoryCraftingDisabled())
        {
            return;
        }
        final CraftingContainer other = lockedGrid(player.containerMenu);
        if (other != null)
        {
            lock(player.containerMenu, other);
        }
        if (player.level().isClientSide())
        {
            return;
        }
        giveBack(player, player.inventoryMenu, player.inventoryMenu.getCraftSlots());
        if (other != null)
        {
            giveBack(player, player.containerMenu, other);
        }
    }

    private static void giveBack(final Player player, final AbstractContainerMenu menu, final CraftingContainer grid)
    {
        if (grid.isEmpty())
        {
            return;
        }
        for (int slot = 0; slot < grid.getContainerSize(); slot++)
        {
            final ItemStack stack = grid.removeItemNoUpdate(slot);
            if (!stack.isEmpty())
            {
                player.getInventory().placeItemBackInInventory(stack);
            }
        }
        menu.slotsChanged(grid);
    }
}
