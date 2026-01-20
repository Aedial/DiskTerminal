package com.cellterminal.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;


/**
 * Renders overlay messages at the action bar position (above the hotbar).
 * Messages fade in/out and are colored based on their type.
 *
 * This renders on top of the GUI but uses screen coordinates, similar to
 * Minecraft's vanilla action bar overlay.
 */
public class OverlayMessageRenderer {

    // Vanilla Minecraft GUI colors - light and clear
    private static final int BACKGROUND_COLOR = 0x00000000;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final float BACKGROUND_OPACITY = 0.75f;

    private static OverlayMessage currentMessage = null;

    /**
     * Sets a new message to display, replacing any previous message.
     */
    public static void setMessage(String text, MessageType type) {
        currentMessage = new OverlayMessage(text, type);
    }

    /**
     * Clears the current message immediately.
     */
    public static void clear() {
        currentMessage = null;
    }

    /**
     * Returns the current message if present and not expired.
     */
    public static OverlayMessage getCurrentMessage() {
        if (currentMessage != null && currentMessage.isExpired()) currentMessage = null;

        return currentMessage;
    }

    /**
     * Renders the overlay message in screen coordinates.
     * Should be called after all GUI elements are drawn.
     *
     * @param screenWidth  The scaled screen width
     * @param screenHeight The scaled screen height
     */
    public static void render(int screenWidth, int screenHeight) {
        OverlayMessage message = getCurrentMessage();
        if (message == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fontRenderer = mc.fontRenderer;

        String text = message.getText();
        int textWidth = fontRenderer.getStringWidth(text);
        float alpha = message.getAlpha();

        if (alpha <= 0.01f) return;

        // Position: centered horizontally, above the hotbar (similar to action bar)
        // The vanilla action bar is at screenHeight - 59
        int x = (screenWidth - textWidth) / 2;
        int y = screenHeight - 59;

        // Get the base color and apply alpha
        int baseColor = message.getType().getColor();
        int colorWithAlpha = ((int) (alpha * 255) << 24) | (baseColor & 0x00FFFFFF);

        // Apply alpha to background and border colors
        int bgColor = ((int) (alpha * 255 * BACKGROUND_OPACITY) << 24) | (BACKGROUND_COLOR & 0x00FFFFFF);
        int borderColor = ((int) (alpha * 255 * BACKGROUND_OPACITY) << 24) | (BORDER_COLOR & 0x00FFFFFF);

        // Prepare GL state for overlay rendering
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        );
        // Reset color state to prevent previous render calls from tinting our colors
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.disableTexture2D();

        // Background rectangle with padding and border
        int padding = 4;
        int borderWidth = 1;
        int left = x - padding;
        int top = y - padding;
        int right = x + textWidth + padding;
        int bottom = y + fontRenderer.FONT_HEIGHT + padding;

        // Draw border + background
        Gui.drawRect(left - borderWidth, top - borderWidth, right + borderWidth, bottom + borderWidth, borderColor);
        Gui.drawRect(left, top, right, bottom, bgColor);

        // Re-enable blending and texture after Gui.drawRect (which disables them)
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();

        // Draw text with shadow for readability
        fontRenderer.drawStringWithShadow(text, x, y, colorWithAlpha);

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    /**
     * Convenience method that gets screen dimensions from Minecraft.
     */
    public static void render() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        render(resolution.getScaledWidth(), resolution.getScaledHeight());
    }
}
