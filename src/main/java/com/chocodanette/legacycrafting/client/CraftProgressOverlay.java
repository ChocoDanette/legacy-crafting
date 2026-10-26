package com.chocodanette.legacycrafting.client;

/**
 * The progress bar of the player's craft, drawn under the crosshair.
 */
public final class CraftProgressOverlay
{
    private static float progress = -1f;

    private CraftProgressOverlay()
    {
    }

    public static void setProgress(final float value)
    {
        progress = value;
    }

    public static float progress()
    {
        return progress;
    }
}
