package com.chocodanette.legacycrafting.workbench;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The items of a workbench, kept by its chunk.
 */
public final class WorkbenchContainer implements Container
{
    private final NonNullList<ItemStack> items;
    private final Runnable onChange;

    public WorkbenchContainer(final NonNullList<ItemStack> items, final Runnable onChange)
    {
        this.items = items;
        this.onChange = onChange;
    }

    @Override
    public int getContainerSize()
    {
        return items.size();
    }

    @Override
    public boolean isEmpty()
    {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(final int slot)
    {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(final int slot, final int amount)
    {
        final ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty())
        {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(final int slot)
    {
        final ItemStack removed = ContainerHelper.takeItem(items, slot);
        setChanged();
        return removed;
    }

    @Override
    public void setItem(final int slot, final ItemStack stack)
    {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public void setChanged()
    {
        onChange.run();
    }

    @Override
    public boolean stillValid(final Player player)
    {
        return true;
    }

    @Override
    public void clearContent()
    {
        items.clear();
        setChanged();
    }
}
