package com.chocodanette.legacycrafting.client;

import com.chocodanette.legacycrafting.Config;
import com.chocodanette.legacycrafting.LegacyCrafting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.RecipeBookType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;

/**
 * Hides the 2x2 crafting grid of the inventory screen: its "Crafting" title, grid, arrow and result, and the recipe
 * book that would fill it. The slots themselves are locked by {@code InventoryGrid}.
 */
@EventBusSubscriber(modid = LegacyCrafting.MOD_ID, value = Dist.CLIENT)
public final class InventoryScreenHandler
{
    /**
     * Area of the grid in the vanilla inventory texture, from the title down to the bottom of the grid.
     */
    private static final int LEFT = 96;
    private static final int TOP = 4;
    private static final int RIGHT = 173;
    private static final int BOTTOM = 55;
    private static final int BACKGROUND = 0xFFC6C6C6;

    private InventoryScreenHandler()
    {
    }

    @SubscribeEvent
    public static void onInitPre(final ScreenEvent.Init.Pre event)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (event.getScreen() instanceof InventoryScreen && minecraft.player != null && Config.inventoryCraftingDisabled())
        {
            // Opened, the recipe book would push the inventory aside.
            minecraft.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
        }
    }

    @SubscribeEvent
    public static void onInitPost(final ScreenEvent.Init.Post event)
    {
        if (!(event.getScreen() instanceof final InventoryScreen screen) || !Config.inventoryCraftingDisabled())
        {
            return;
        }
        final int x = screen.getGuiLeft() + 104;
        final int y = screen.height / 2 - 22;
        for (final GuiEventListener listener : List.copyOf(event.getListenersList()))
        {
            if (listener instanceof final ImageButton button && button.getX() == x && button.getY() == y)
            {
                event.removeListener(button);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderForeground(final ContainerScreenEvent.Render.Foreground event)
    {
        if (event.getContainerScreen() instanceof InventoryScreen && Config.inventoryCraftingDisabled())
        {
            // Drawn after the labels, in coordinates relative to the inventory.
            event.getGuiGraphics().fill(LEFT, TOP, RIGHT, BOTTOM, BACKGROUND);
        }
    }
}
