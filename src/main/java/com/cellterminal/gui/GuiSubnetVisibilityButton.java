package com.cellterminal.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;

import com.cellterminal.client.SubnetVisibility;


/**
 * Button that cycles through subnet visibility modes and displays a visual indicator.
 * - Don't Show: No network icon (X or empty)
 * - Show Favorites: Star icon
 * - Show All: Globe/network icon
 */
public class GuiSubnetVisibilityButton extends GuiButton {

    private static final int BUTTON_SIZE = 12;

    // Colors
    private static final int COLOR_DISABLED = 0xFF808080;  // Grey for "don't show"
    private static final int COLOR_FAVORITES = 0xFFFFD700;  // Gold for favorites
    private static final int COLOR_ALL = 0xFF4CAF50;       // Green for show all

    private SubnetVisibility currentMode;

    public GuiSubnetVisibilityButton(int buttonId, int x, int y, SubnetVisibility initialMode) {
        super(buttonId, x, y, BUTTON_SIZE, BUTTON_SIZE, "");
        this.currentMode = initialMode;
    }

    public void setMode(SubnetVisibility mode) {
        this.currentMode = mode;
    }

    public SubnetVisibility getMode() {
        return currentMode;
    }

    public SubnetVisibility cycleMode() {
        this.currentMode = this.currentMode.next();

        return this.currentMode;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        this.hovered = mouseX >= this.x && mouseY >= this.y
            && mouseX < this.x + this.width && mouseY < this.y + this.height;

        // Draw button background
        int bgColor = this.hovered ? 0xFF707070 : 0xFF8B8B8B;
        drawRect(this.x, this.y, this.x + this.width, this.y + this.height, bgColor);

        // Draw outline
        drawRect(this.x, this.y, this.x + this.width, this.y + 1, 0xFFFFFFFF);
        drawRect(this.x, this.y, this.x + 1, this.y + this.height, 0xFFFFFFFF);
        drawRect(this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, 0xFF555555);
        drawRect(this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, 0xFF555555);

        // Draw the mode indicator
        drawModeIndicator(mc);
    }

    private void drawModeIndicator(Minecraft mc) {
        int centerX = this.x + BUTTON_SIZE / 2;
        int centerY = this.y + BUTTON_SIZE / 2;

        switch (currentMode) {
            case DONT_SHOW:
                // Draw an X
                int xSize = 4;
                int xColor = COLOR_DISABLED;
                // Top-left to bottom-right diagonal
                for (int i = 0; i < xSize; i++) {
                    drawRect(centerX - 2 + i, centerY - 2 + i, centerX - 1 + i, centerY - 1 + i, xColor);
                }
                // Top-right to bottom-left diagonal
                for (int i = 0; i < xSize; i++) {
                    drawRect(centerX + 1 - i, centerY - 2 + i, centerX + 2 - i, centerY - 1 + i, xColor);
                }
                break;

            case SHOW_FAVORITES:
                // Draw a small star
                mc.fontRenderer.drawString("★", this.x + 3, this.y + 2, COLOR_FAVORITES);
                break;

            case SHOW_ALL:
                // Draw a small network/globe icon (circle with lines)
                int color = COLOR_ALL;
                // Simple circle representation
                drawRect(centerX - 2, centerY - 3, centerX + 2, centerY - 2, color);  // top
                drawRect(centerX - 3, centerY - 2, centerX - 2, centerY + 2, color);  // left
                drawRect(centerX + 2, centerY - 2, centerX + 3, centerY + 2, color);  // right
                drawRect(centerX - 2, centerY + 2, centerX + 2, centerY + 3, color);  // bottom
                // Horizontal line through middle
                drawRect(centerX - 2, centerY, centerX + 2, centerY + 1, color);
                break;
        }
    }

    /**
     * Get the tooltip lines for this button.
     */
    public List<String> getTooltip() {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format("gui.cellterminal.subnet_visibility"));
        tooltip.add("§7" + I18n.format(currentMode.getTranslationKey()));
        tooltip.add("");
        tooltip.add("§e" + I18n.format("gui.cellterminal.click_to_cycle"));

        return tooltip;
    }
}
