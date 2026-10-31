package com.chocodanette.legacycrafting.ground;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Up to four items lying on the ground, one per quarter of the block. Placed with the place key, picked up with a
 * right click on their quarter; the block goes away with its last item.
 */
public class PlacedItemsBlock extends BaseEntityBlock
{
    public static final MapCodec<PlacedItemsBlock> CODEC = simpleCodec(PlacedItemsBlock::new);

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public PlacedItemsBlock(final Properties properties)
    {
        super(properties);
    }

    /**
     * The quarter of the block under {@code location}, numbered like TFC's placed items.
     */
    public static int slotAt(final BlockPos pos, final Vec3 location)
    {
        final boolean west = location.x - pos.getX() < 0.5;
        final boolean north = location.z - pos.getZ() < 0.5;
        return (west ? 1 : 0) + (north ? 2 : 0);
    }

    @NotNull
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec()
    {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull final BlockPos pos, @NotNull final BlockState state)
    {
        return new PlacedItemsBlockEntity(pos, state);
    }

    @NotNull
    @Override
    protected RenderShape getRenderShape(@NotNull final BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @NotNull
    @Override
    protected VoxelShape getShape(@NotNull final BlockState state, @NotNull final BlockGetter level, @NotNull final BlockPos pos,
      @NotNull final CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(@NotNull final BlockState state, final LevelReader level, final BlockPos pos)
    {
        final BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @NotNull
    @Override
    protected BlockState updateShape(@NotNull final BlockState state, @NotNull final Direction direction, @NotNull final BlockState neighbor,
      @NotNull final LevelAccessor level, @NotNull final BlockPos pos, @NotNull final BlockPos neighborPos)
    {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @NotNull
    @Override
    protected ItemInteractionResult useItemOn(@NotNull final ItemStack stack, @NotNull final BlockState state, @NotNull final Level level,
      @NotNull final BlockPos pos, @NotNull final Player player, @NotNull final InteractionHand hand, @NotNull final BlockHitResult hit)
    {
        return pickUp(level, pos, player, hit) ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @NotNull
    @Override
    protected InteractionResult useWithoutItem(@NotNull final BlockState state, @NotNull final Level level, @NotNull final BlockPos pos,
      @NotNull final Player player, @NotNull final BlockHitResult hit)
    {
        return pickUp(level, pos, player, hit) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }

    /**
     * Gives the player the item of the quarter they clicked, and removes the block once it is empty.
     */
    private static boolean pickUp(final Level level, final BlockPos pos, final Player player, final BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof final PlacedItemsBlockEntity entity))
        {
            return false;
        }
        final int slot = slotAt(pos, hit.getLocation());
        if (entity.getInventory().getStackInSlot(slot).isEmpty())
        {
            return false;
        }
        if (!level.isClientSide)
        {
            final ItemStack taken = entity.getInventory().extractItem(slot, 1, false);
            if (!player.addItem(taken))
            {
                player.drop(taken, false);
            }
            if (entity.isEmpty())
            {
                level.removeBlock(pos, false);
            }
        }
        return true;
    }

    @Override
    protected void onRemove(@NotNull final BlockState state, @NotNull final Level level, @NotNull final BlockPos pos,
      @NotNull final BlockState newState, final boolean movedByPiston)
    {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof final PlacedItemsBlockEntity entity)
        {
            for (int slot = 0; slot < entity.getInventory().getSlots(); slot++)
            {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5,
                  entity.getInventory().getStackInSlot(slot));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
