package com.chocodanette.legacycrafting.crafting;

import com.chocodanette.legacycrafting.LegacyCrafting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * A block holding four items laid out as a 2x2 grid on the ground: TFC's placed items (key V).
 * <p>
 * TFC does not expose this inventory as a capability, and the pack is built without TFC on the classpath: its block
 * entity is read through its public methods {@code getInventory()} and {@code holdingLargeItem()}.
 */
public final class CraftingSurface
{
    public static final TagKey<Block> SURFACES =
      TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(LegacyCrafting.MOD_ID, "crafting_surfaces"));

    public static final int SLOTS = 4;

    private final Level level;
    private final BlockPos pos;
    private final BlockEntity entity;
    private final IItemHandlerModifiable inventory;

    private CraftingSurface(final Level level, final BlockPos pos, final BlockEntity entity, final IItemHandlerModifiable inventory)
    {
        this.level = level;
        this.pos = pos;
        this.entity = entity;
        this.inventory = inventory;
    }

    /**
     * @return the surface at {@code pos}, or null if there is none or it holds a single large item.
     */
    @Nullable
    public static CraftingSurface find(final Level level, final BlockPos pos)
    {
        if (!level.isLoaded(pos) || !level.getBlockState(pos).is(SURFACES))
        {
            return null;
        }
        final BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null)
        {
            return null;
        }
        try
        {
            final Method largeItem = entity.getClass().getMethod("holdingLargeItem");
            if (Boolean.TRUE.equals(largeItem.invoke(entity)))
            {
                return null;
            }
            final Object inventory = entity.getClass().getMethod("getInventory").invoke(entity);
            if (inventory instanceof final IItemHandlerModifiable handler && handler.getSlots() >= SLOTS)
            {
                return new CraftingSurface(level, pos, entity, handler);
            }
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            LegacyCrafting.LOGGER.warn("Cannot read the items of {} at {}", entity.getType(), pos, e);
        }
        return null;
    }

    /**
     * @return the surface the player looks at: {@code target} itself, or the items lying on top of it.
     */
    @Nullable
    public static CraftingSurface findTarget(final Level level, final BlockPos target)
    {
        final CraftingSurface surface = find(level, target);
        return surface != null ? surface : find(level, target.above());
    }

    public BlockPos pos()
    {
        return pos;
    }

    public ItemStack get(final int slot)
    {
        return inventory.getStackInSlot(slot);
    }

    public void set(final int slot, final ItemStack stack)
    {
        inventory.setStackInSlot(slot, stack);
    }

    /**
     * TFC numbers the quarters of the block from the south-east one: slot 0 south-east, 1 south-west, 2 north-east,
     * 3 north-west.
     */
    public static boolean isWest(final int slot)
    {
        return (slot & 1) != 0;
    }

    public static boolean isNorth(final int slot)
    {
        return (slot & 2) != 0;
    }

    /**
     * The slot seen at {@code column}, {@code row} (0 or 1, from the top left) by a player looking {@code facing}.
     */
    public static int slotAt(final Direction facing, final int column, final int row)
    {
        final Direction right = facing.getClockWise();
        // Offsets of the quarter from the block's center, -1 or +1 along x and z.
        final int forward = row == 0 ? 1 : -1;
        final int side = column == 1 ? 1 : -1;
        final int x = facing.getStepX() * forward + right.getStepX() * side;
        final int z = facing.getStepZ() * forward + right.getStepZ() * side;
        return (x < 0 ? 1 : 0) + (z < 0 ? 2 : 0);
    }

    /**
     * Removes the block once the craft took all its items, as TFC does when the last item is picked up.
     */
    public void removeIfEmpty()
    {
        for (int slot = 0; slot < SLOTS; slot++)
        {
            if (!get(slot).isEmpty())
            {
                entity.setChanged();
                return;
            }
        }
        level.removeBlock(pos, false);
    }
}
