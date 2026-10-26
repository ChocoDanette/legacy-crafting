package com.chocodanette.legacycrafting.client;

import com.chocodanette.legacycrafting.network.WorkbenchCraftPayload;
import com.chocodanette.legacycrafting.workbench.WorkbenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The window of a workbench: the vanilla crafting table layout, with a craft button and the progress of the craft
 * over the arrow.
 */
public class WorkbenchScreen extends AbstractContainerScreen<WorkbenchMenu>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");

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
    }

    private static void craft()
    {
        PacketDistributor.sendToServer(new WorkbenchCraftPayload());
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
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(final GuiGraphics graphics, final float partialTick, final int mouseX, final int mouseY)
    {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        final float progress = menu.progress();
        if (progress >= 0)
        {
            // Over the arrow, from left to right.
            final int x = leftPos + 89;
            final int y = topPos + 34;
            graphics.fill(x, y, x + Math.round(24 * Math.min(progress, 1f)), y + 17, 0x90E0B040);
        }
    }
}
