package com.chocodanette.legacycrafting.client;

import com.chocodanette.legacycrafting.network.WorkbenchCraftPayload;
import com.chocodanette.legacycrafting.workbench.WorkbenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The window of a workbench: the vanilla crafting table layout, with a craft button, a side panel setting how many
 * items to craft (in steps of the recipe's result), the
 * progress of the craft filling the arrow, and a preview of the result above it. When the grid matches several recipes,
 * arrows pick the one to craft.
 */
public class WorkbenchScreen extends AbstractContainerScreen<WorkbenchMenu>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final ResourceLocation PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
    private static final int PANEL_WIDTH = 32;
    private static final int PANEL_HEIGHT = 82;
    private static final int MAX_ITEMS = 64;
    private static final int PREVIEW_X = 93;
    private static final int PREVIEW_Y = 16;

    private List<RecipeHolder<CraftingRecipe>> recipes = List.of();
    private List<ItemStack> results = List.of();
    private List<ItemStack> lastGrid = List.of();
    private int selected;
    /**
     * How many times to craft the recipe in a row.
     */
    private int crafts = 1;
    private Button previous;
    private Button next;

    public WorkbenchScreen(final WorkbenchMenu menu, final Inventory inventory, final Component title)
    {
        super(menu, inventory, title);
    }

    @Override
    protected void init()
    {
        super.init();
        titleLabelX = 29;
        addRenderableWidget(Button.builder(Component.translatable("legacycrafting.button.craft"), button -> craft())
          .bounds(leftPos + 86, topPos + 59, 54, 14)
          .build());
        // Side panel, right of the window: ++, +, the quantity, -, --.
        final int x = panelX() + 4;
        addRenderableWidget(Button.builder(Component.literal("++"), button -> crafts = maxCrafts())
          .bounds(x, topPos + 4, 24, 14)
          .tooltip(Tooltip.create(Component.translatable("legacycrafting.button.max")))
          .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> crafts = Math.min(maxCrafts(), crafts + 1))
          .bounds(x, topPos + 20, 24, 14)
          .build());
        addRenderableWidget(Button.builder(Component.literal("-"), button -> crafts = Math.max(1, crafts - 1))
          .bounds(x, topPos + 48, 24, 14)
          .build());
        addRenderableWidget(Button.builder(Component.literal("--"), button -> crafts = 1)
          .bounds(x, topPos + 64, 24, 14)
          .tooltip(Tooltip.create(Component.translatable("legacycrafting.button.min")))
          .build());
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> select(selected - 1))
          .bounds(leftPos + PREVIEW_X - 9, topPos + PREVIEW_Y + 2, 8, 12)
          .build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> select(selected + 1))
          .bounds(leftPos + PREVIEW_X + 17, topPos + PREVIEW_Y + 2, 8, 12)
          .build());
        lastGrid = List.of();
        updatePreview();
    }

    private void craft()
    {
        final Optional<ResourceLocation> recipe = recipes.isEmpty() ? Optional.empty() : Optional.of(recipes.get(selected).id());
        PacketDistributor.sendToServer(new WorkbenchCraftPayload(recipe, Math.min(crafts, maxCrafts())));
    }

    private int panelX()
    {
        return leftPos + imageWidth + 2;
    }

    /**
     * Items made by one craft of the chosen recipe.
     */
    private int perCraft()
    {
        return Math.max(1, preview().getCount());
    }

    /**
     * How many times the recipe can be made in a row, for at most 64 items: from the grid and the player's inventory,
     * which refills the grid between crafts, each ingredient's items divided by the cells it fills. Tools are not
     * counted, as they come back damaged after each craft.
     */
    private int maxCrafts()
    {
        final List<ItemStack> kinds = new ArrayList<>();
        final List<Integer> cells = new ArrayList<>();
        for (int slot = 0; slot < WorkbenchMenu.GRID_SLOTS; slot++)
        {
            final ItemStack stack = menu.getSlot(slot).getItem();
            if (stack.isEmpty() || stack.isDamageableItem())
            {
                continue;
            }
            final int kind = indexOf(kinds, stack);
            if (kind < 0)
            {
                kinds.add(stack);
                cells.add(1);
            }
            else
            {
                cells.set(kind, cells.get(kind) + 1);
            }
        }
        int max = Math.min(WorkbenchMenu.MAX_QUANTITY, MAX_ITEMS / perCraft());
        for (int kind = 0; kind < kinds.size(); kind++)
        {
            int available = 0;
            for (int slot = 0; slot < menu.slots.size(); slot++)
            {
                final ItemStack stack = menu.getSlot(slot).getItem();
                if (slot != WorkbenchMenu.RESULT_SLOT && ItemStack.isSameItem(stack, kinds.get(kind)))
                {
                    available += stack.getCount();
                }
            }
            max = Math.min(max, available / cells.get(kind));
        }
        return Math.max(1, max);
    }

    private static int indexOf(final List<ItemStack> kinds, final ItemStack stack)
    {
        for (int index = 0; index < kinds.size(); index++)
        {
            if (ItemStack.isSameItem(kinds.get(index), stack))
            {
                return index;
            }
        }
        return -1;
    }

    private void select(final int index)
    {
        if (!recipes.isEmpty())
        {
            selected = Math.floorMod(index, recipes.size());
        }
    }

    @Override
    protected void containerTick()
    {
        super.containerTick();
        updatePreview();
    }

    /**
     * Finds the recipes of the grid again when its items changed, keeping the chosen recipe if it still matches.
     */
    private void updatePreview()
    {
        final List<ItemStack> grid = new ArrayList<>(WorkbenchMenu.GRID_SLOTS);
        for (int slot = 0; slot < WorkbenchMenu.GRID_SLOTS; slot++)
        {
            grid.add(menu.getSlot(slot).getItem().copy());
        }
        if (!sameItems(grid, lastGrid) && minecraft != null && minecraft.level != null)
        {
            lastGrid = grid;
            final ResourceLocation chosen = recipes.isEmpty() ? null : recipes.get(selected).id();
            recipes = menu.matchingRecipes(minecraft.level);
            results = new ArrayList<>(recipes.size());
            selected = 0;
            for (int index = 0; index < recipes.size(); index++)
            {
                results.add(result(recipes.get(index)));
                if (recipes.get(index).id().equals(chosen))
                {
                    selected = index;
                }
            }
        }
        previous.visible = next.visible = recipes.size() > 1;
        crafts = Math.clamp(crafts, 1, maxCrafts());
    }

    private ItemStack result(final RecipeHolder<CraftingRecipe> recipe)
    {
        try
        {
            return recipe.value().assemble(menu.grid().input(), minecraft.level.registryAccess());
        }
        catch (final RuntimeException e)
        {
            // Some recipes need the server to assemble their result.
            return recipe.value().getResultItem(minecraft.level.registryAccess());
        }
    }

    private static boolean sameItems(final List<ItemStack> a, final List<ItemStack> b)
    {
        if (a.size() != b.size())
        {
            return false;
        }
        for (int slot = 0; slot < a.size(); slot++)
        {
            if (!ItemStack.isSameItemSameComponents(a.get(slot), b.get(slot)) || a.get(slot).getCount() != b.get(slot).getCount())
            {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers)
    {
        if (ClientCrafting.CRAFT.matches(keyCode, scanCode))
        {
            craft();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        if (isOverPreview(mouseX, mouseY) && recipes.size() > 1)
        {
            select(selected - (int) Math.signum(scrollY));
            return true;
        }
        if (isOverPanel(mouseX, mouseY))
        {
            crafts = Math.clamp(crafts + (int) Math.signum(scrollY), 1, maxCrafts());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * The side panel in the look of the window, with the number of items to craft (or left to craft) between its buttons.
     */
    private void renderPanel(final GuiGraphics graphics)
    {
        final int x = panelX();
        final int y = topPos;
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + PANEL_WIDTH - 1, y + PANEL_HEIGHT - 1, 0xFFC6C6C6);
        graphics.fill(x + 1, y + 1, x + PANEL_WIDTH - 2, y + 2, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + 2, y + PANEL_HEIGHT - 2, 0xFFFFFFFF);
        graphics.fill(x + 2, y + PANEL_HEIGHT - 2, x + PANEL_WIDTH - 1, y + PANEL_HEIGHT - 1, 0xFF555555);
        graphics.fill(x + PANEL_WIDTH - 2, y + 2, x + PANEL_WIDTH - 1, y + PANEL_HEIGHT - 1, 0xFF555555);
        final int craftsShown = menu.remaining() > 0 ? menu.remaining() : crafts;
        final String text = String.valueOf(craftsShown * perCraft());
        graphics.drawString(font, text, x + (PANEL_WIDTH - font.width(text)) / 2, y + 37, 0x404040, false);
    }

    @Override
    protected boolean hasClickedOutside(final double mouseX, final double mouseY, final int left, final int top, final int button)
    {
        return super.hasClickedOutside(mouseX, mouseY, left, top, button) && !isOverPanel(mouseX, mouseY);
    }

    private boolean isOverPanel(final double mouseX, final double mouseY)
    {
        return mouseX >= panelX() && mouseX < panelX() + PANEL_WIDTH && mouseY >= topPos && mouseY < topPos + PANEL_HEIGHT;
    }

    private boolean isOverPreview(final double mouseX, final double mouseY)
    {
        final double x = mouseX - leftPos - PREVIEW_X;
        final double y = mouseY - topPos - PREVIEW_Y;
        return x >= 0 && x < 16 && y >= 0 && y < 16;
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);
        final ItemStack preview = preview();
        if (!preview.isEmpty() && isOverPreview(mouseX, mouseY) && menu.getCarried().isEmpty())
        {
            final List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(preview));
            if (recipes.size() > 1)
            {
                lines.add(Component.translatable("legacycrafting.preview.choice", selected + 1, recipes.size()));
            }
            graphics.renderTooltip(font, lines, preview.getTooltipImage(), preview, mouseX, mouseY);
        }
        else
        {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    private ItemStack preview()
    {
        return results.isEmpty() ? ItemStack.EMPTY : results.get(Math.min(selected, results.size() - 1));
    }

    @Override
    protected void renderBg(final GuiGraphics graphics, final float partialTick, final int mouseX, final int mouseY)
    {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        final float progress = menu.progress();
        if (progress >= 0)
        {
            // Fills the arrow from left to right, like a furnace.
            final int width = Mth.ceil(24 * Math.min(progress, 1f));
            graphics.blitSprite(PROGRESS_SPRITE, 24, 16, 0, 0, leftPos + 89, topPos + 34, width, 16);
        }
        renderPanel(graphics);
        final ItemStack preview = preview();
        if (!preview.isEmpty())
        {
            graphics.renderItem(preview, leftPos + PREVIEW_X, topPos + PREVIEW_Y);
            graphics.renderItemDecorations(font, preview, leftPos + PREVIEW_X, topPos + PREVIEW_Y);
        }
    }
}
