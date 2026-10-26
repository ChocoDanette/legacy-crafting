package com.chocodanette.legacycrafting.client;

import com.chocodanette.legacycrafting.LegacyCrafting;
import com.chocodanette.legacycrafting.ModMenus;
import com.chocodanette.legacycrafting.network.StartCraftPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ClientCrafting
{
    public static final KeyMapping CRAFT = new KeyMapping("key.legacycrafting.craft", KeyConflictContext.IN_GAME,
      InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.legacycrafting");

    private static final int BAR_WIDTH = 40;

    private ClientCrafting()
    {
    }

    @EventBusSubscriber(modid = LegacyCrafting.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents
    {
        @SubscribeEvent
        public static void onRegisterKeys(final RegisterKeyMappingsEvent event)
        {
            event.register(CRAFT);
        }

        @SubscribeEvent
        public static void onRegisterScreens(final RegisterMenuScreensEvent event)
        {
            event.register(ModMenus.WORKBENCH.get(), WorkbenchScreen::new);
        }

        @SubscribeEvent
        public static void onRegisterLayers(final RegisterGuiLayersEvent event)
        {
            event.registerAbove(VanillaGuiLayers.CROSSHAIR,
              ResourceLocation.fromNamespaceAndPath(LegacyCrafting.MOD_ID, "craft_progress"), (graphics, delta) -> renderProgress(graphics));
        }
    }

    @EventBusSubscriber(modid = LegacyCrafting.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents
    {
        @SubscribeEvent
        public static void onClientTick(final ClientTickEvent.Post event)
        {
            final Minecraft minecraft = Minecraft.getInstance();
            while (CRAFT.consumeClick())
            {
                if (minecraft.player != null && minecraft.hitResult instanceof final BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK)
                {
                    PacketDistributor.sendToServer(new StartCraftPayload(hit.getBlockPos()));
                }
            }
        }

        @SubscribeEvent
        public static void onLogout(final ClientPlayerNetworkEvent.LoggingOut event)
        {
            CraftProgressOverlay.setProgress(-1f);
        }
    }

    private static void renderProgress(final GuiGraphics graphics)
    {
        final float progress = CraftProgressOverlay.progress();
        final Minecraft minecraft = Minecraft.getInstance();
        if (progress < 0 || minecraft.options.hideGui)
        {
            return;
        }
        final int x = (graphics.guiWidth() - BAR_WIDTH) / 2;
        final int y = graphics.guiHeight() / 2 + 12;
        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + 4, 0xA0000000);
        graphics.fill(x, y, x + Math.round(BAR_WIDTH * Math.min(progress, 1f)), y + 3, 0xFFE0B040);
    }
}
