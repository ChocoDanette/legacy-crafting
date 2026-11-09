package com.chocodanette.legacycrafting.crafting;

import com.chocodanette.legacycrafting.Config;
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

/**
 * Takes the 2x2 crafting grid out of the player's inventory: crafting happens in the world. Curios' inventory screen
 * has its own copy of the grid, locked the same way.
 */
public final class InventoryGrid
{
    /**
     * Curios' inventory menu, found by name so that Curios stays optional.
     */
    private static final String CURIOS_MENU = "top.theillusivec4.curios.common.inventory.container.CuriosContainer";

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

    public static boolean isCuriosMenu(final AbstractContainerMenu menu)
    {
        return menu.getClass().getName().equals(CURIOS_MENU);
    }

    /**
     * Locks the crafting grid and result slots of an open Curios menu, on both sides. Curios makes its slots again when
     * the page changes, so this runs every tick.
     *
     * @return the grid of the menu, or null if it has none.
     */
    @Nullable
    private static CraftingContainer lockCurios(final AbstractContainerMenu menu)
    {
        CraftingContainer grid = null;
        for (int index = 0; index < menu.slots.size(); index++)
        {
            final Slot slot = menu.slots.get(index);
            if (slot instanceof LockedSlot)
            {
                if (slot.container instanceof final CraftingContainer crafting)
                {
                    grid = crafting;
                }
                continue;
            }
            if (slot instanceof ResultSlot || slot.container instanceof CraftingContainer)
            {
                menu.slots.set(index, new LockedSlot(slot));
                if (slot.container instanceof final CraftingContainer crafting)
                {
                    grid = crafting;
                }
            }
        }
        return grid;
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
        final CraftingContainer curiosGrid = isCuriosMenu(player.containerMenu) ? lockCurios(player.containerMenu) : null;
        if (player.level().isClientSide())
        {
            return;
        }
        giveBack(player, player.inventoryMenu, player.inventoryMenu.getCraftSlots());
        if (curiosGrid != null)
        {
            giveBack(player, player.containerMenu, curiosGrid);
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
