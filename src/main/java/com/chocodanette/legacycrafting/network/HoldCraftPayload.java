package com.chocodanette.legacycrafting.network;

import com.chocodanette.legacycrafting.LegacyCrafting;
import com.chocodanette.legacycrafting.crafting.InWorldCrafting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Sent when the craft key is pressed or released: while it is held, a finished craft starts again with items from
 * the player's inventory.
 */
public record HoldCraftPayload(boolean held) implements CustomPacketPayload
{
    public static final Type<HoldCraftPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath(LegacyCrafting.MOD_ID, "hold_craft"));

    public static final StreamCodec<FriendlyByteBuf, HoldCraftPayload> STREAM_CODEC =
      StreamCodec.composite(ByteBufCodecs.BOOL, HoldCraftPayload::held, HoldCraftPayload::new);

    @NotNull
    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(final HoldCraftPayload payload, final IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (context.player() instanceof final ServerPlayer player)
            {
                InWorldCrafting.setHolding(player, payload.held);
            }
        });
    }
}
