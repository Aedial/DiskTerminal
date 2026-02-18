package com.cellterminal.gui.subnet;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;


/**
 * A small button used to navigate to/from subnet overview mode.
 * When in normal view (main or subnet), shows a left arrow to enter overview.
 * When in overview mode, shows a right arrow to go back.
 */
public class GuiBackButton extends GuiButton {

    public static final int BUTTON_SIZE = 12;

    private boolean isInOverviewMode;

    public GuiBackButton(int buttonId, int x, int y) {
        super(buttonId, x, y, BUTTON_SIZE, BUTTON_SIZE, "");
        this.isInOverviewMode = false;
    }

    /**
     * Update the button state based on whether we're in subnet overview mode.
     */
    public void setInOverviewMode(boolean inOverview) {
        this.isInOverviewMode = inOverview;
    }


    public boolean isInOverviewMode() {
        return isInOverviewMode;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        this.hovered = mouseX >= this.x && mouseY >= this.y
            && mouseX < this.x + this.width && mouseY < this.y + this.height;

        // Draw button background (matching GuiSearchHelpButton)
        int bgColor = this.hovered ? 0xFF505050 : 0xFF606060;
        drawRect(this.x, this.y, this.x + this.width, this.y + this.height, bgColor);

        // Draw border (matching GuiSearchHelpButton)
        drawRect(this.x, this.y, this.x + this.width, this.y + 1, 0xFF808080);
        drawRect(this.x, this.y, this.x + 1, this.y + this.height, 0xFF808080);
        drawRect(this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, 0xFF303030);
        drawRect(this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, 0xFF303030);

        // Draw arrow icon
        int iconColor = this.hovered ? 0xFFFFFF00 : 0xFFCCCCCC;
        if (isInOverviewMode) {
            // Right arrow when in overview (return to main/subnet view)
            drawRightArrow(mc, iconColor);
        } else {
            // Left arrow when in normal view (enter subnet overview)
            drawLeftArrow(mc, iconColor);
        }
    }

    private void drawLeftArrow(Minecraft mc, int color) {
        // Draw a simple left arrow: <
        int cx = this.x + this.width / 2 - mc.fontRenderer.getStringWidth("\u25C0") / 2;  // Center the arrow
        int cy = this.y + this.height / 2 - mc.fontRenderer.FONT_HEIGHT / 2 - 4;

        GlStateManager.pushMatrix();
        GlStateManager.scale(2.0f, 2.0f, 1.0f);  // Scale up for better visibility
        mc.fontRenderer.drawString("\u25C0", cx / 2, cy / 2, color);  // ◀
        GlStateManager.popMatrix();
    }

    private void drawRightArrow(Minecraft mc, int color) {
        // Draw a simple right arrow: >
        int cx = this.x + this.width / 2 - mc.fontRenderer.getStringWidth("\u25B6") / 2;  // Center the arrow
        int cy = this.y + this.height / 2 - mc.fontRenderer.FONT_HEIGHT / 2 - 4;

        GlStateManager.pushMatrix();
        GlStateManager.scale(2.0f, 2.0f, 1.0f);  // Scale up for better visibility
        mc.fontRenderer.drawString("\u25B6", cx / 2, cy / 2, color);  // ▶
        GlStateManager.popMatrix();
    }

    /**
     * Get the tooltip for this button.
     */
    public List<String> getTooltip() {
        List<String> tooltip = new ArrayList<>();

        if (isInOverviewMode) {
            tooltip.add(I18n.format("cellterminal.subnet.back"));
        } else {
            tooltip.add(I18n.format("cellterminal.subnet.overview"));
        }

        return tooltip;
    }
}
