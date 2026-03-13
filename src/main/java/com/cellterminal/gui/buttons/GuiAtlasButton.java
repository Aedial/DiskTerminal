package com.cellterminal.gui.buttons;

import java.util.List;

import com.cellterminal.gui.GuiConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;


/**
 * Base class for all buttons that use atlas.png for background rendering.
 * <p>
 * Provides shared logic for:
 * <ul>
 *   <li>Visibility check and early return</li>
 *   <li>Hover detection from mouse coordinates</li>
 *   <li>Binding the atlas texture and setting up blend state</li>
 *   <li>Drawing the background from the atlas at the UV returned by subclasses</li>
 * </ul>
 *
 * Subclasses implement:
 * <ul>
 *   <li>{@link #getBackgroundTexX()}: atlas U for background (may depend on state)</li>
 *   <li>{@link #getBackgroundTexY()}: atlas V for background (typically offset by size when hovered)</li>
 *   <li>{@link #drawForeground(Minecraft)}: any overlay on top of the background</li>
 *   <li>{@link #getTooltip()}: tooltip lines for the button</li>
 * </ul>
 */
public abstract class GuiAtlasButton extends GuiButton {

    protected static final ResourceLocation ATLAS_TEXTURE =
        new ResourceLocation("cellterminal", "textures/guis/atlas.png");

    protected GuiAtlasButton(int buttonId, int x, int y, int size) {
        super(buttonId, x, y, size, size, "");
    }

    /**
     * Get the texture X coordinate in the atlas for the background.
     * Called every frame, may depend on button state.
     */
    protected abstract int getBackgroundTexX();

    /**
     * Get the texture Y coordinate in the atlas for the background.
     * Called every frame, typically returns baseY + (hovered ? size : 0).
     */
    protected abstract int getBackgroundTexY();

    /**
     * Draw any content on top of the atlas background.
     * Called after the background is drawn with the atlas still bound.
     * Default implementation does nothing.
     */
    protected void drawForeground(Minecraft mc) {
        // Override in subclasses for icons, text, state indicators, etc.
    }

    /**
     * Get tooltip lines for this button.
     */
    public abstract List<String> getTooltip();

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        this.hovered = mouseX >= this.x && mouseY >= this.y
            && mouseX < this.x + this.width && mouseY < this.y + this.height;

        mc.getTextureManager().bindTexture(ATLAS_TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();

        Gui.drawScaledCustomSizeModalRect(
                this.x, this.y,
                getBackgroundTexX(), getBackgroundTexY(),
                this.width, this.height, this.width, this.height,
                GuiConstants.ATLAS_WIDTH, GuiConstants.ATLAS_HEIGHT);

        drawForeground(mc);
    }
}
