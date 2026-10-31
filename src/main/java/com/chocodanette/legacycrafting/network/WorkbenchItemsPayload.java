package com.chocodanette.legacycrafting.network;

import com.chocodanette.legacycrafting.LegacyCrafting;
import com.chocodanette.legacycrafting.client.WorkbenchItemsRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The grid of a workbench, sent to the players who see it so that its items show on top of the block. An empty list
 * means the workbench holds nothing.
 */
public record WorkbenchItemsPayload(BlockPos pos, List<ItemStack> grid) implements CustomPacketPayload
{
    public static final Type<WorkbenchItemsPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath(LegacyCrafting.MOD_ID, "workbench_items"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WorkbenchItemsPayload> STREAM_CODEC =
      StreamCodec.composite(BlockPos.STREAM_CODEC, WorkbenchItemsPayload::pos,
        ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), WorkbenchItemsPayload::grid, WorkbenchItemsPayload::new);

    @NotNull
    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(final WorkbenchItemsPayload payload, final IPayloadContext context)
    {
        context.enqueueWork(() -> WorkbenchItemsRenderer.set(payload.pos, payload.grid));
    }
}
