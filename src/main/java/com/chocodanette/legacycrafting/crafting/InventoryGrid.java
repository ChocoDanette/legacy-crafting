package com.chocodanette.legacycrafting.crafting;

import com.chocodanette.legacycrafting.Config;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Takes the 2x2 crafting grid out of the player's inventory: crafting happens in the world.
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
     * The recipe book and other mods can still move items into the grid: give them back.
     */
    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event)
    {
        final Player player = event.getEntity();
        if (player.level().isClientSide() || !Config.inventoryCraftingDisabled())
        {
            return;
        }
        final CraftingContainer grid = player.inventoryMenu.getCraftSlots();
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
        player.inventoryMenu.slotsChanged(grid);
    }
}
