package com.chocodanette.legacycrafting.client;

import com.chocodanette.legacycrafting.crafting.CraftingSurface;
import com.chocodanette.legacycrafting.ground.PlacedItemsBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Draws each item of a {@link PlacedItemsBlockEntity} lying flat in its quarter of the block.
 */
public class PlacedItemsRenderer implements BlockEntityRenderer<PlacedItemsBlockEntity>
{
    @Override
    public void render(@NotNull final PlacedItemsBlockEntity entity, final float partialTick, @NotNull final PoseStack pose,
      @NotNull final MultiBufferSource buffers, final int light, final int overlay)
    {
        for (int slot = 0; slot < CraftingSurface.SLOTS; slot++)
        {
            final ItemStack stack = entity.getInventory().getStackInSlot(slot);
            if (stack.isEmpty())
            {
                continue;
            }
            pose.pushPose();
            pose.translate(CraftingSurface.isWest(slot) ? 0.25 : 0.75, 0.03, CraftingSurface.isNorth(slot) ? 0.25 : 0.75);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(0.45f, 0.45f, 0.45f);
            Minecraft.getInstance().getItemRenderer()
              .renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, entity.getLevel(), (int) entity.getBlockPos().asLong() + slot);
            pose.popPose();
        }
    }
}
