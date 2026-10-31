package com.chocodanette.legacycrafting.client;

import com.chocodanette.legacycrafting.LegacyCrafting;
import com.chocodanette.legacycrafting.workbench.WorkbenchMenu;
import com.chocodanette.legacycrafting.workbench.Workbenches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the grid items of the workbenches around the player lying on top of the blocks, three by three, the top row of
 * the grid to the north.
 */
@EventBusSubscriber(modid = LegacyCrafting.MOD_ID, value = Dist.CLIENT)
public final class WorkbenchItemsRenderer
{
    private static final double MAX_DISTANCE_SQ = 48 * 48;

    /**
     * Grids of the workbenches sent by the server, by position.
     */
    private static final Map<Long, List<ItemStack>> GRIDS = new HashMap<>();

    private WorkbenchItemsRenderer()
    {
    }

    public static void set(final BlockPos pos, final List<ItemStack> grid)
    {
        if (grid.stream().allMatch(ItemStack::isEmpty))
        {
            GRIDS.remove(pos.asLong());
        }
        else
        {
            GRIDS.put(pos.asLong(), List.copyOf(grid));
        }
    }

    @SubscribeEvent
    public static void onLogout(final ClientPlayerNetworkEvent.LoggingOut event)
    {
        GRIDS.clear();
    }

    @SubscribeEvent
    public static void onClone(final ClientPlayerNetworkEvent.Clone event)
    {
        // A new level (dimension change or respawn): the server sends the grids of its chunks again.
        GRIDS.clear();
    }

    @SubscribeEvent
    public static void onRenderLevel(final RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || GRIDS.isEmpty())
        {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null)
        {
            return;
        }
        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack pose = event.getPoseStack();
        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        GRIDS.entrySet().removeIf(entry -> !level.isLoaded(BlockPos.of(entry.getKey()))
          || !level.getBlockState(BlockPos.of(entry.getKey())).is(Workbenches.WORKBENCHES));
        for (final Map.Entry<Long, List<ItemStack>> entry : GRIDS.entrySet())
        {
            final BlockPos pos = BlockPos.of(entry.getKey());
            if (camera.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQ)
            {
                continue;
            }
            final int light = LevelRenderer.getLightColor(level, pos.above());
            final List<ItemStack> grid = entry.getValue();
            for (int slot = 0; slot < Math.min(WorkbenchMenu.GRID_SLOTS, grid.size()); slot++)
            {
                final ItemStack stack = grid.get(slot);
                if (stack.isEmpty())
                {
                    continue;
                }
                final int column = slot % 3;
                final int row = slot / 3;
                pose.pushPose();
                pose.translate(pos.getX() - camera.x + 0.1875 + column * 0.3125, pos.getY() + 1.01 - camera.y,
                  pos.getZ() - camera.z + 0.1875 + row * 0.3125);
                pose.mulPose(Axis.XP.rotationDegrees(90));
                pose.scale(0.25f, 0.25f, 0.25f);
                minecraft.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light,
                  OverlayTexture.NO_OVERLAY, pose, buffers, level, (int) entry.getKey().longValue() + slot);
                pose.popPose();
            }
        }
        buffers.endBatch();
    }
}
