package com.chocodanette.legacycrafting.client;

import com.chocodanette.legacycrafting.Config;
import com.chocodanette.legacycrafting.LegacyCrafting;
import com.chocodanette.legacycrafting.crafting.InventoryGrid;
import com.mojang.datafixers.util.Either;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
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
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;

/**
 * The screen side of the locked 2x2 grid ({@code InventoryGrid} locks its slots).
 * <p>
 * In the vanilla inventory and Curios' screen, which copies it, the grid, arrow, result and "Crafting" title are
 * covered by a patch sewn on with a red zigzag stitch, "Legacy Crafting" written across it. The patch is drawn right
 * after the window's texture, under widgets, slots, labels and tooltips; outside the stitch it lets the texture show,
 * so it looks sewn on whatever resource pack draws the window. The recipe book button is removed. JEI makes the arrow a
 * "Show all recipes" area: clicks there are dropped before JEI sees them (unless a widget is there), and its tooltip
 * is removed.
 * <p>
 * Other mods' screens, whose layout is unknown, keep their window as it is: the grid only shows empty and locked.
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
    /**
     * The patch, relative to the window, as small as it can be: the stitch goes up to 3 pixels in, so the cloth inside
     * it just covers the grid (x 97 to 132, y 17 to 52), the arrow and the result (to x 170, y 44).
     */
    private static final int PATCH_LEFT = 94;
    private static final int PATCH_TOP = 14;
    private static final int PATCH_RIGHT = 173;
    private static final int PATCH_BOTTOM = 55;
    private static final int PATCH_COLOR = 0xFFC6C6C6;
    private static final int THREAD_COLOR = 0xFFAA1E1E;
    private static final int TEXT_COLOR = 0x404040;
    private static final float TEXT_ANGLE = -12f;
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

    /**
     * The window draws its "Crafting" title over the patch: it gets an empty title instead (the title only serves
     * narration otherwise).
     */
    @SubscribeEvent
    public static void onInitTitle(final ScreenEvent.Init.Pre event)
    {
        if (event.getScreen() instanceof final AbstractContainerScreen<?> screen && hasVanillaLayout(screen) && isHidden(screen))
        {
            try
            {
                ObfuscationReflectionHelper.setPrivateValue(Screen.class, screen, Component.empty(), "title");
            }
            catch (final RuntimeException e)
            {
                LegacyCrafting.LOGGER.debug("Could not hide the crafting title of {}", screen.getClass().getName(), e);
            }
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
     * The zigzag of the stitch: 0, 1, 2, 1, 0... pixels into the patch.
     */
    private static int zigzag(final int step)
    {
        final int phase = step % 4;
        return phase <= 2 ? phase : 4 - phase;
    }

    /**
     * Draws the patch right after the window's texture. First of the listeners, so that other mods' backgrounds stay
     * on top.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderBackground(final ContainerScreenEvent.Render.Background event)
    {
        final AbstractContainerScreen<?> screen = event.getContainerScreen();
        if (!hasVanillaLayout(screen) || !isHidden(screen))
        {
            return;
        }
        final GuiGraphics graphics = event.getGuiGraphics();
        final int left = screen.getGuiLeft();
        final int top = screen.getGuiTop();
        // The cloth, inside the stitch only, in runs of pixels per row.
        for (int y = PATCH_TOP + 1; y < PATCH_BOTTOM; y++)
        {
            final int rowLeft = PATCH_LEFT + zigzag(y - PATCH_TOP);
            final int rowRight = PATCH_RIGHT - zigzag(y - PATCH_TOP);
            int start = -1;
            for (int x = rowLeft + 1; x <= rowRight; x++)
            {
                final boolean inside = x < rowRight
                  && y > PATCH_TOP + zigzag(x - PATCH_LEFT) && y < PATCH_BOTTOM - zigzag(x - PATCH_LEFT);
                if (inside && start < 0)
                {
                    start = x;
                }
                else if (!inside && start >= 0)
                {
                    graphics.fill(left + start, top + y, left + x, top + y + 1, PATCH_COLOR);
                    start = -1;
                }
            }
        }
        // The stitch.
        for (int x = PATCH_LEFT; x <= PATCH_RIGHT; x++)
        {
            final int offset = zigzag(x - PATCH_LEFT);
            pixel(graphics, left + x, top + PATCH_TOP + offset);
            pixel(graphics, left + x, top + PATCH_BOTTOM - offset);
        }
        for (int y = PATCH_TOP; y <= PATCH_BOTTOM; y++)
        {
            final int offset = zigzag(y - PATCH_TOP);
            pixel(graphics, left + PATCH_LEFT + offset, top + y);
            pixel(graphics, left + PATCH_RIGHT - offset, top + y);
        }
        // "Legacy Crafting" on two lines, tilted.
        final Font font = Minecraft.getInstance().font;
        final Component first = Component.translatable("legacycrafting.inventory.patch.line1");
        final Component second = Component.translatable("legacycrafting.inventory.patch.line2");
        graphics.pose().pushPose();
        graphics.pose().translate(left + (PATCH_LEFT + PATCH_RIGHT + 1) / 2f, top + (PATCH_TOP + PATCH_BOTTOM + 1) / 2f, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(TEXT_ANGLE));
        graphics.drawString(font, first, -font.width(first) / 2, -font.lineHeight, TEXT_COLOR, false);
        graphics.drawString(font, second, -font.width(second) / 2, 1, TEXT_COLOR, false);
        graphics.pose().popPose();
    }

    private static void pixel(final GuiGraphics graphics, final int x, final int y)
    {
        graphics.fill(x, y, x + 1, y + 1, THREAD_COLOR);
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
