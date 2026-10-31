package com.chocodanette.legacycrafting.network;

import com.chocodanette.legacycrafting.LegacyCrafting;
import com.chocodanette.legacycrafting.workbench.WorkbenchMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Sent by the craft button of an open workbench, with the recipe chosen in the preview and how many times to craft it.
 */
public record WorkbenchCraftPayload(Optional<ResourceLocation> recipe, int quantity) implements CustomPacketPayload
{
    public static final Type<WorkbenchCraftPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath(LegacyCrafting.MOD_ID, "workbench_craft"));

    public static final StreamCodec<FriendlyByteBuf, WorkbenchCraftPayload> STREAM_CODEC = StreamCodec.composite(
      ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs::optional), WorkbenchCraftPayload::recipe,
      ByteBufCodecs.VAR_INT, WorkbenchCraftPayload::quantity, WorkbenchCraftPayload::new);

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
                menu.startCraft(player, payload.recipe, payload.quantity);
            }
        });
    }
}
