package com.chocodanette.legacycrafting.network;

import com.chocodanette.legacycrafting.LegacyCrafting;
import com.chocodanette.legacycrafting.crafting.InWorldCrafting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Sent by the craft key: craft the items laid on the targeted block.
 */
public record StartCraftPayload(BlockPos target) implements CustomPacketPayload
{
    public static final Type<StartCraftPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath(LegacyCrafting.MOD_ID, "start_craft"));

    public static final StreamCodec<FriendlyByteBuf, StartCraftPayload> STREAM_CODEC =
      StreamCodec.composite(BlockPos.STREAM_CODEC, StartCraftPayload::target, StartCraftPayload::new);

    @NotNull
    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(final StartCraftPayload payload, final IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (context.player() instanceof final ServerPlayer player)
            {
                InWorldCrafting.start(player, payload.target);
            }
        });
    }
}
