package com.chocodanette.legacycrafting;

import com.chocodanette.legacycrafting.crafting.InWorldCrafting;
import com.chocodanette.legacycrafting.crafting.InventoryGrid;
import com.chocodanette.legacycrafting.network.CraftProgressPayload;
import com.chocodanette.legacycrafting.network.StartCraftPayload;
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
 * In-world crafting for the TerraFirmaCraft Legacy modpack: items placed on the ground are crafted with a key, over a
 * few seconds, while the player stays close.
 */
@Mod(LegacyCrafting.MOD_ID)
public class LegacyCrafting
{
    public static final String MOD_ID = "legacycrafting";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LegacyCrafting(final IEventBus modBus, final ModContainer container)
    {
        ModAttributes.ATTRIBUTES.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        modBus.addListener(this::onRegisterPayloads);
        modBus.addListener(this::onAttributeModification);
        NeoForge.EVENT_BUS.register(InWorldCrafting.class);
        NeoForge.EVENT_BUS.register(InventoryGrid.class);
    }

    private void onAttributeModification(final EntityAttributeModificationEvent event)
    {
        event.add(EntityType.PLAYER, ModAttributes.CRAFTING_TIME);
    }

    private void onRegisterPayloads(final RegisterPayloadHandlersEvent event)
    {
        event.registrar("1")
          .playToServer(StartCraftPayload.TYPE, StartCraftPayload.STREAM_CODEC, StartCraftPayload::handle)
          .playToClient(CraftProgressPayload.TYPE, CraftProgressPayload.STREAM_CODEC, CraftProgressPayload::handle);
    }
}
