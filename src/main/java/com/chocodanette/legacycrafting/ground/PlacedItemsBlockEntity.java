package com.chocodanette.legacycrafting.ground;

import com.chocodanette.legacycrafting.ModBlocks;
import com.chocodanette.legacycrafting.crafting.CraftingSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

/**
 * The four items of a {@link PlacedItemsBlock}, numbered like TFC's placed items: slot 0 south-east, 1 south-west,
 * 2 north-east, 3 north-west.
 */
public class PlacedItemsBlockEntity extends BlockEntity
{
    private final ItemStackHandler inventory = new ItemStackHandler(CraftingSurface.SLOTS)
    {
        @Override
        public int getSlotLimit(final int slot)
        {
            return 1;
        }

        @Override
        protected void onContentsChanged(final int slot)
        {
            setChanged();
            if (level != null && !level.isClientSide)
            {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    };

    public PlacedItemsBlockEntity(final BlockPos pos, final BlockState state)
    {
        super(ModBlocks.PLACED_ITEMS_ENTITY.get(), pos, state);
    }

    /**
     * Read by {@link CraftingSurface}, which reads TFC's placed items through the same method.
     */
    public ItemStackHandler getInventory()
    {
        return inventory;
    }

    /**
     * Read by {@link CraftingSurface}, like TFC's: this block never holds a single large item.
     */
    public boolean holdingLargeItem()
    {
        return false;
    }

    public boolean isEmpty()
    {
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            if (!inventory.getStackInSlot(slot).isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void saveAdditional(@NotNull final CompoundTag tag, @NotNull final HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(@NotNull final CompoundTag tag, @NotNull final HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag(@NotNull final HolderLookup.Provider registries)
    {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
