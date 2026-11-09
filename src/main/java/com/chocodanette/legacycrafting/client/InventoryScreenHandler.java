package com.chocodanette.legacycrafting.client;

import com.chocodanette.legacycrafting.Config;
import com.chocodanette.legacycrafting.LegacyCrafting;
import com.chocodanette.legacycrafting.crafting.InventoryGrid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.RecipeBookType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;

/**
 * Hides the 2x2 crafting grid of the inventory screen, and of other mods' screens with one (locked by
 * {@code InventoryGrid}). Only the grid is hidden: it is painted over on the background layer, under everything other
 * mods draw, widgets and tooltips included.
 * <p>
 * The vanilla inventory and Curios' screen, which copies it, also lose their "Crafting" title, arrow and recipe book
 * button. JEI makes their arrow a "Show Recipes" area: clicks there are dropped before JEI sees them, unless a widget
 * is there.
 */
@EventBusSubscriber(modid = LegacyCrafting.MOD_ID, value = Dist.CLIENT)
public final class InventoryScreenHandler
{
    /**
     * Area of the grid, arrow and result in the vanilla inventory texture, under the title.
     */
    private static final int LEFT = 96;
    private static final int TOP = 16;
    private static final int RIGHT = 173;
    private static final int BOTTOM = 55;
    /**
     * Area of the arrow, JEI's "Show Recipes" area.
     */
    private static final int ARROW_LEFT = 134;
    private static final int ARROW_TOP = 27;
    private static final int ARROW_RIGHT = 152;
    private static final int ARROW_BOTTOM = 44;
    private static final int BACKGROUND = 0xFFC6C6C6;
    private static final String CURIOS_SCREEN = "top.theillusivec4.curios.client.gui.CuriosScreen";

    private InventoryScreenHandler()
    {
    }

    /**
     * The vanilla inventory screen, or Curios' one: the vanilla layout.
     */
    private static boolean hasVanillaLayout(final AbstractContainerScreen<?> screen)
    {
        return screen instanceof InventoryScreen || screen.getClass().getName().equals(CURIOS_SCREEN);
    }

    private static boolean isHidden(final AbstractContainerScreen<?> screen)
    {
        return Config.inventoryCraftingDisabled()
          && (screen instanceof InventoryScreen || InventoryGrid.lockedGrid(screen.getMenu()) != null);
    }

    @SubscribeEvent
    public static void onInitPre(final ScreenEvent.Init.Pre event)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (event.getScreen() instanceof final AbstractContainerScreen<?> screen && hasVanillaLayout(screen) && isHidden(screen)
              && minecraft.player != null)
        {
            // Opened, the recipe book would push the inventory aside.
            minecraft.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
        }
    }

    @SubscribeEvent
    public static void onInitPost(final ScreenEvent.Init.Post event)
    {
        if (!(event.getScreen() instanceof final AbstractContainerScreen<?> screen) || !hasVanillaLayout(screen) || !isHidden(screen))
        {
            return;
        }
        // The recipe book button, where the vanilla layout puts it.
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
        if (event.getScreen() instanceof final AbstractContainerScreen<?> screen && hasVanillaLayout(screen) && isHidden(screen)
              && screen.getChildAt(event.getMouseX(), event.getMouseY()).isEmpty())
        {
            final double x = event.getMouseX() - screen.getGuiLeft();
            final double y = event.getMouseY() - screen.getGuiTop();
            if (x >= ARROW_LEFT && x < ARROW_RIGHT && y >= ARROW_TOP && y < ARROW_BOTTOM)
            {
                event.setCanceled(true);
            }
        }
    }

    /**
     * Painted right after the window's texture, before any widget, slot or label: in screen coordinates. First of the
     * listeners, so that other mods' backgrounds stay on top.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderBackground(final ContainerScreenEvent.Render.Background event)
    {
        final AbstractContainerScreen<?> screen = event.getContainerScreen();
        if (!isHidden(screen))
        {
            return;
        }
        final GuiGraphics graphics = event.getGuiGraphics();
        final int left = screen.getGuiLeft();
        final int top = screen.getGuiTop();
        if (hasVanillaLayout(screen))
        {
            graphics.fill(left + LEFT, top + TOP, left + RIGHT, top + BOTTOM, BACKGROUND);
            return;
        }
        // Any other screen: only the frames of the grid's and result's slots.
        final CraftingContainer grid = InventoryGrid.lockedGrid(screen.getMenu());
        for (final Slot slot : screen.getMenu().slots)
        {
            if (slot.container == grid || slot.container instanceof ResultContainer)
            {
                graphics.fill(left + slot.x - 1, top + slot.y - 1, left + slot.x + 17, top + slot.y + 17, BACKGROUND);
            }
        }
    }

    /**
     * The "Crafting" title of the vanilla layout, drawn by the window itself: painted over its own text only.
     */
    @SubscribeEvent
    public static void onRenderForeground(final ContainerScreenEvent.Render.Foreground event)
    {
        final AbstractContainerScreen<?> screen = event.getContainerScreen();
        if (hasVanillaLayout(screen) && isHidden(screen))
        {
            // In coordinates relative to the window; the title sits at (97, 6 to 8).
            final int width = Minecraft.getInstance().font.width(screen.getTitle());
            event.getGuiGraphics().fill(LEFT, 5, 98 + width, 17, BACKGROUND);
        }
    }
}
