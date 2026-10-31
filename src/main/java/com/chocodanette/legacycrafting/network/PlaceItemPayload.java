package com.chocodanette.legacycrafting.network;

import com.chocodanette.legacycrafting.LegacyCrafting;
import com.chocodanette.legacycrafting.ground.GroundPlacing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Sent by the place key: put the held item on the ground where the player looks.
 */
public record PlaceItemPayload(BlockPos target, Direction face, double x, double y, double z) implements CustomPacketPayload
{
    public static final Type<PlaceItemPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath(LegacyCrafting.MOD_ID, "place_item"));

    public static final StreamCodec<FriendlyByteBuf, PlaceItemPayload> STREAM_CODEC =
      StreamCodec.composite(BlockPos.STREAM_CODEC, PlaceItemPayload::target, Direction.STREAM_CODEC, PlaceItemPayload::face,
        ByteBufCodecs.DOUBLE, PlaceItemPayload::x, ByteBufCodecs.DOUBLE, PlaceItemPayload::y, ByteBufCodecs.DOUBLE, PlaceItemPayload::z,
        PlaceItemPayload::new);

    @NotNull
    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(final PlaceItemPayload payload, final IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (context.player() instanceof final ServerPlayer player)
            {
                GroundPlacing.place(player, payload.target, payload.face, new Vec3(payload.x, payload.y, payload.z));
            }
        });
    }
}
