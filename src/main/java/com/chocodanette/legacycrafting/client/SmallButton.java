package com.chocodanette.legacycrafting.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * A button too small for the normal font: its label is drawn smaller, centered.
 */
public class SmallButton extends Button
{
    private static final float TEXT_SCALE = 0.75f;

    public SmallButton(final int x, final int y, final int width, final int height, final Component label, final OnPress onPress)
    {
        super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderString(@NotNull final GuiGraphics graphics, @NotNull final Font font, final int color)
    {
        graphics.pose().pushPose();
        graphics.pose().translate(getX() + width / 2f, getY() + (height - font.lineHeight * TEXT_SCALE) / 2f + 1, 0);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1);
        graphics.drawCenteredString(font, getMessage(), 0, 0, color);
        graphics.pose().popPose();
    }
}
