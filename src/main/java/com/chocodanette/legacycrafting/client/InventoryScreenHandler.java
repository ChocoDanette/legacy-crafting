package com.chocodanette.legacycrafting.client;

import com.chocodanette.legacycrafting.Config;
import com.chocodanette.legacycrafting.LegacyCrafting;
import com.chocodanette.legacycrafting.crafting.InventoryGrid;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;

/**
 * The screen side of the locked 2x2 grid ({@code InventoryGrid} locks its slots, which then show empty). Nothing is
 * painted over the window, so resource packs and other mods' displays stay as they are.
 * <p>
 * The vanilla inventory and Curios' screen, which copies it, lose their recipe book button. JEI makes their arrow a
 * "Show all recipes" area: clicks there are dropped before JEI sees them (unless a widget is there), and its tooltip
 * is removed.
 */
@EventBusSubscriber(modid = LegacyCrafting.MOD_ID, value = Dist.CLIENT)
public final class InventoryScreenHandler
{
    /**
     * Area of the arrow, JEI's "Show Recipes" area.
     */
    private static final int ARROW_LEFT = 134;
    private static final int ARROW_TOP = 27;
    private static final int ARROW_RIGHT = 152;
    private static final int ARROW_BOTTOM = 44;
    private static final String SHOW_RECIPES = "jei.tooltip.show.recipes";
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
     * JEI turns the arrow into a "Show all recipes" area with its own tooltip: that tooltip only is removed, other
     * mods' tooltips stay.
     */
    @SubscribeEvent
    public static void onGatherTooltip(final RenderTooltipEvent.GatherComponents event)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof final AbstractContainerScreen<?> screen) || !hasVanillaLayout(screen) || !isHidden(screen))
        {
            return;
        }
        final double scale = (double) minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
        final double x = minecraft.mouseHandler.xpos() * scale - screen.getGuiLeft();
        final double y = minecraft.mouseHandler.ypos() * scale - screen.getGuiTop();
        if (x < ARROW_LEFT || x >= ARROW_RIGHT || y < ARROW_TOP || y >= ARROW_BOTTOM)
        {
            return;
        }
        // The list may not be editable: the tooltip is dropped only when it is JEI's line alone.
        final List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        if (!elements.isEmpty() && elements.stream().allMatch(InventoryScreenHandler::isShowRecipes))
        {
            event.setCanceled(true);
        }
    }

    private static boolean isShowRecipes(final Either<FormattedText, TooltipComponent> element)
    {
        return element.left()
          .filter(text -> text instanceof final Component component && component.getContents() instanceof final TranslatableContents contents
            && SHOW_RECIPES.equals(contents.getKey()))
          .isPresent();
    }
}
