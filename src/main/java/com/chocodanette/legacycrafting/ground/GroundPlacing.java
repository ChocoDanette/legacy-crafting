package com.chocodanette.legacycrafting.ground;

import com.chocodanette.legacycrafting.Config;
import com.chocodanette.legacycrafting.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Places the held item on the ground, in the quarter the player looks at, when TerraFirmaCraft (which has its own
 * placed items on the same key) is not installed.
 */
public final class GroundPlacing
{
    private GroundPlacing()
    {
    }

    /**
     * @return true if this mod places items on the ground: TFC is absent and the server config allows it.
     */
    public static boolean enabled()
    {
        return !ModList.get().isLoaded("tfc") && Config.groundPlacingEnabled();
    }

    public static void place(final ServerPlayer player, final BlockPos clicked, final Direction face, final Vec3 location)
    {
        final ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!enabled() || held.isEmpty() || player.isSpectator() || !player.canInteractWithBlock(clicked, 1.0))
        {
            return;
        }
        final ServerLevel level = player.serverLevel();
        final BlockPos target;
        if (level.getBlockState(clicked).is(ModBlocks.PLACED_ITEMS))
        {
            target = clicked;
        }
        else
        {
            target = clicked.above();
            final BlockState above = level.getBlockState(target);
            if (face != Direction.UP || !above.canBeReplaced() || !above.getFluidState().isEmpty()
              || !ModBlocks.PLACED_ITEMS.get().defaultBlockState().canSurvive(level, target)
              || !level.mayInteract(player, target))
            {
                return;
            }
            level.setBlockAndUpdate(target, ModBlocks.PLACED_ITEMS.get().defaultBlockState());
        }
        if (!(level.getBlockEntity(target) instanceof final PlacedItemsBlockEntity entity))
        {
            return;
        }
        final int slot = PlacedItemsBlock.slotAt(target, location);
        if (!entity.getInventory().getStackInSlot(slot).isEmpty())
        {
            return;
        }
        entity.getInventory().setStackInSlot(slot, held.copyWithCount(1));
        if (!player.getAbilities().instabuild)
        {
            held.shrink(1);
        }
        level.playSound(null, target, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.5f, 1.2f);
    }
}
