package com.chocodanette.legacycrafting.workbench;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The items left in the workbenches of a chunk: workbenches have no block entity, so the chunk keeps them.
 */
public final class WorkbenchItems
{
    /**
     * Nine grid slots, then the result slot.
     */
    public static final int SIZE = 10;

    private record Entry(long pos, List<ItemStack> items)
    {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
          Codec.LONG.fieldOf("pos").forGetter(Entry::pos),
          ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items").forGetter(Entry::items)
        ).apply(instance, Entry::new));
    }

    public static final Codec<WorkbenchItems> CODEC = Entry.CODEC.listOf().xmap(WorkbenchItems::fromEntries, WorkbenchItems::toEntries);

    private final Map<Long, NonNullList<ItemStack>> benches = new HashMap<>();

    private static WorkbenchItems fromEntries(final List<Entry> entries)
    {
        final WorkbenchItems items = new WorkbenchItems();
        for (final Entry entry : entries)
        {
            final NonNullList<ItemStack> stacks = NonNullList.withSize(SIZE, ItemStack.EMPTY);
            for (int slot = 0; slot < Math.min(SIZE, entry.items.size()); slot++)
            {
                stacks.set(slot, entry.items.get(slot));
            }
            items.benches.put(entry.pos, stacks);
        }
        return items;
    }

    private List<Entry> toEntries()
    {
        final List<Entry> entries = new ArrayList<>();
        benches.forEach((pos, stacks) -> {
            if (stacks.stream().anyMatch(stack -> !stack.isEmpty()))
            {
                entries.add(new Entry(pos, List.copyOf(stacks)));
            }
        });
        return entries;
    }

    /**
     * @return the items of the workbench at {@code pos}, shared by everyone who opens it.
     */
    public NonNullList<ItemStack> get(final BlockPos pos)
    {
        return benches.computeIfAbsent(pos.asLong(), key -> NonNullList.withSize(SIZE, ItemStack.EMPTY));
    }

    /**
     * Forgets the workbench at {@code pos}.
     *
     * @return its items, or an empty list.
     */
    public List<ItemStack> remove(final BlockPos pos)
    {
        final NonNullList<ItemStack> stacks = benches.remove(pos.asLong());
        return stacks == null ? List.of() : stacks;
    }
}
