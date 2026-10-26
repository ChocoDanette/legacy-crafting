package com.chocodanette.legacycrafting.network;

import com.chocodanette.legacycrafting.LegacyCrafting;
import com.chocodanette.legacycrafting.client.CraftProgressOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Progress of the player's craft, from 0 to 1, or negative when no craft is running.
 */
public record CraftProgressPayload(float progress) implements CustomPacketPayload
{
    public static final Type<CraftProgressPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath(LegacyCrafting.MOD_ID, "craft_progress"));

    public static final StreamCodec<FriendlyByteBuf, CraftProgressPayload> STREAM_CODEC =
      StreamCodec.composite(ByteBufCodecs.FLOAT, CraftProgressPayload::progress, CraftProgressPayload::new);

    @NotNull
    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(final CraftProgressPayload payload, final IPayloadContext context)
    {
        context.enqueueWork(() -> CraftProgressOverlay.setProgress(payload.progress));
    }
}
