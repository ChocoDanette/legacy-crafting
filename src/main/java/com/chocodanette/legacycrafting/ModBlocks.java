package com.chocodanette.legacycrafting;

import com.chocodanette.legacycrafting.ground.PlacedItemsBlock;
import com.chocodanette.legacycrafting.ground.PlacedItemsBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks
{
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LegacyCrafting.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
      DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LegacyCrafting.MOD_ID);

    /**
     * Items placed on the ground, four per block, when TerraFirmaCraft (and its own placed items) is not installed.
     */
    public static final DeferredBlock<PlacedItemsBlock> PLACED_ITEMS = BLOCKS.register("placed_items",
      () -> new PlacedItemsBlock(BlockBehaviour.Properties.of()
        .instabreak()
        .noCollission()
        .noOcclusion()
        .sound(SoundType.WOOD)
        .pushReaction(PushReaction.DESTROY)));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlacedItemsBlockEntity>> PLACED_ITEMS_ENTITY =
      BLOCK_ENTITIES.register("placed_items", () -> BlockEntityType.Builder.of(PlacedItemsBlockEntity::new, PLACED_ITEMS.get()).build(null));

    private ModBlocks()
    {
    }
}
