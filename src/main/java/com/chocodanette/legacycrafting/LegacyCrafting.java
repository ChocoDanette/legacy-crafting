package com.chocodanette.legacycrafting;

import com.chocodanette.legacycrafting.crafting.InWorldCrafting;
import com.chocodanette.legacycrafting.crafting.InventoryGrid;
import com.chocodanette.legacycrafting.network.CraftProgressPayload;
import com.chocodanette.legacycrafting.network.PlaceItemPayload;
import com.chocodanette.legacycrafting.network.StartCraftPayload;
import com.chocodanette.legacycrafting.network.WorkbenchCraftPayload;
import com.chocodanette.legacycrafting.workbench.Workbenches;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

/**
 * In-world crafting: items placed on the ground (with TerraFirmaCraft's placed items, or this mod's own without TFC)
 * are crafted with a key, over a few seconds, while the player stays close.
 */
@Mod(LegacyCrafting.MOD_ID)
public class LegacyCrafting
{
    public static final String MOD_ID = "legacycrafting";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LegacyCrafting(final IEventBus modBus, final ModContainer container)
    {
        ModAttributes.ATTRIBUTES.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.BLOCK_ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModMenus.ATTACHMENTS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        modBus.addListener(this::onRegisterPayloads);
        modBus.addListener(this::onAttributeModification);
        NeoForge.EVENT_BUS.register(InWorldCrafting.class);
        NeoForge.EVENT_BUS.register(InventoryGrid.class);
        NeoForge.EVENT_BUS.register(Workbenches.class);
    }

    private void onAttributeModification(final EntityAttributeModificationEvent event)
    {
        event.add(EntityType.PLAYER, ModAttributes.CRAFTING_TIME);
    }

    private void onRegisterPayloads(final RegisterPayloadHandlersEvent event)
    {
        event.registrar("1")
          .playToServer(StartCraftPayload.TYPE, StartCraftPayload.STREAM_CODEC, StartCraftPayload::handle)
          .playToServer(PlaceItemPayload.TYPE, PlaceItemPayload.STREAM_CODEC, PlaceItemPayload::handle)
          .playToServer(WorkbenchCraftPayload.TYPE, WorkbenchCraftPayload.STREAM_CODEC, WorkbenchCraftPayload::handle)
          .playToClient(CraftProgressPayload.TYPE, CraftProgressPayload.STREAM_CODEC, CraftProgressPayload::handle);
    }
}
