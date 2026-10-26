package com.chocodanette.legacycrafting.workbench;

import com.chocodanette.legacycrafting.Config;
import com.chocodanette.legacycrafting.LegacyCrafting;
import com.chocodanette.legacycrafting.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

/**
 * Opens workbenches (vanilla crafting table, TFC workbenches) as {@link WorkbenchMenu}s that keep their items, and
 * drops those items when the workbench is destroyed.
 */
public final class Workbenches
{
    public static final TagKey<Block> WORKBENCHES =
      TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(LegacyCrafting.MOD_ID, "workbenches"));

    private Workbenches()
    {
    }

    @SubscribeEvent
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event)
    {
        final Player player = event.getEntity();
        final Level level = event.getLevel();
        final BlockPos pos = event.getPos();
        if (!Config.workbenchCraftingEnabled() || !level.getBlockState(pos).is(WORKBENCHES)
          || player.isSecondaryUseActive() && !player.getMainHandItem().isEmpty())
        {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
        if (player instanceof final ServerPlayer serverPlayer && level instanceof final ServerLevel serverLevel)
        {
            final WorkbenchContainer container = container(serverLevel, pos);
            final BlockState state = level.getBlockState(pos);
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new WorkbenchMenu(id, inventory, pos, container),
              state.getBlock().getName()), buffer -> buffer.writeBlockPos(pos));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event)
    {
        if (event.getEntity() instanceof final ServerPlayer player && player.containerMenu instanceof final WorkbenchMenu menu)
        {
            menu.tick(player);
        }
    }

    @SubscribeEvent
    public static void onBreak(final BlockEvent.BreakEvent event)
    {
        if (event.getLevel() instanceof final ServerLevel level && event.getState().is(WORKBENCHES))
        {
            dropItems(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onExplosion(final ExplosionEvent.Detonate event)
    {
        if (event.getLevel() instanceof final ServerLevel level)
        {
            for (final BlockPos pos : event.getAffectedBlocks())
            {
                if (level.getBlockState(pos).is(WORKBENCHES))
                {
                    dropItems(level, pos);
                }
            }
        }
    }

    /**
     * The items of the workbench at {@code pos}, saved with its chunk.
     */
    public static WorkbenchContainer container(final ServerLevel level, final BlockPos pos)
    {
        final LevelChunk chunk = level.getChunkAt(pos);
        final WorkbenchItems items = chunk.getData(ModMenus.WORKBENCH_ITEMS);
        return new WorkbenchContainer(items.get(pos), () -> chunk.setUnsaved(true));
    }

    private static void dropItems(final ServerLevel level, final BlockPos pos)
    {
        final LevelChunk chunk = level.getChunkAt(pos);
        if (!chunk.hasData(ModMenus.WORKBENCH_ITEMS))
        {
            return;
        }
        final List<ItemStack> items = chunk.getData(ModMenus.WORKBENCH_ITEMS).remove(pos);
        for (final ItemStack stack : items)
        {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
        if (!items.isEmpty())
        {
            chunk.setUnsaved(true);
        }
    }
}
