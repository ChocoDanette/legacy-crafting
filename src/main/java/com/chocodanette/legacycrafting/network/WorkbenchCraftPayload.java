package com.chocodanette.legacycrafting.network;

import com.chocodanette.legacycrafting.LegacyCrafting;
import com.chocodanette.legacycrafting.workbench.WorkbenchMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Sent by the craft button of an open workbench.
 */
public record WorkbenchCraftPayload() implements CustomPacketPayload
{
    public static final Type<WorkbenchCraftPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath(LegacyCrafting.MOD_ID, "workbench_craft"));

    public static final StreamCodec<FriendlyByteBuf, WorkbenchCraftPayload> STREAM_CODEC = StreamCodec.unit(new WorkbenchCraftPayload());

    @NotNull
    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(final WorkbenchCraftPayload payload, final IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (context.player() instanceof final ServerPlayer player && player.containerMenu instanceof final WorkbenchMenu menu
                  && menu.stillValid(player))
            {
                menu.startCraft(player);
            }
        });
    }
}
