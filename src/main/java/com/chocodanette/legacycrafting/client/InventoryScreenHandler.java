package com.chocodanette.legacycrafting.client;

import com.chocodanette.legacycrafting.Config;
import com.chocodanette.legacycrafting.LegacyCrafting;
import com.chocodanette.legacycrafting.crafting.InventoryGrid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.RecipeBookType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;

/**
 * Hides the 2x2 crafting grid of the inventory screen (and of Curios' inventory screen, which copies it at the same
 * place): its "Crafting" title, grid, arrow and result, and the recipe book that would fill it. The slots themselves
 * are locked by {@code InventoryGrid}.
 * <p>
 * JEI makes the hidden arrow a "Show Recipes" area: clicks there are dropped before JEI sees them.
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

    /**
     * The vanilla inventory screen, or Curios' one.
     */
    private static boolean hasGrid(final Screen screen)
    {
        return screen instanceof InventoryScreen
          || screen instanceof final AbstractContainerScreen<?> container && InventoryGrid.isCuriosMenu(container.getMenu());
    }

    @SubscribeEvent
    public static void onInitPre(final ScreenEvent.Init.Pre event)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (hasGrid(event.getScreen()) && minecraft.player != null && Config.inventoryCraftingDisabled())
        {
            // Opened, the recipe book would push the inventory aside.
            minecraft.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
        }
    }

    @SubscribeEvent
    public static void onInitPost(final ScreenEvent.Init.Post event)
    {
        if (!(event.getScreen() instanceof final AbstractContainerScreen<?> screen) || !hasGrid(screen) || !Config.inventoryCraftingDisabled())
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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseClicked(final ScreenEvent.MouseButtonPressed.Pre event)
    {
        if (event.getScreen() instanceof final AbstractContainerScreen<?> screen && hasGrid(screen) && Config.inventoryCraftingDisabled())
        {
            final double x = event.getMouseX() - screen.getGuiLeft();
            final double y = event.getMouseY() - screen.getGuiTop();
            if (x >= LEFT && x < RIGHT && y >= TOP && y < BOTTOM)
            {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderForeground(final ContainerScreenEvent.Render.Foreground event)
    {
        if (hasGrid(event.getContainerScreen()) && Config.inventoryCraftingDisabled())
        {
            // Drawn after the labels, in coordinates relative to the inventory.
            event.getGuiGraphics().fill(LEFT, TOP, RIGHT, BOTTOM, BACKGROUND);
        }
    }
}
