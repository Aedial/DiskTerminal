package com.cellterminal.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;

import com.cellterminal.client.SearchFilterMode;


/**
 * Button that cycles through search filter modes and displays a visual indicator.
 * - Inventory mode: Grey dot (like slots in Tab 2)
 * - Partition mode: Orange dot (like slots in Tab 3)
 * - Mixed mode: Diagonally split dot with both colors
 */
public class GuiSearchModeButton extends GuiButton {

    private static final int BUTTON_SIZE = 12;

    // Colors matching the tab 2 (inventory) and tab 3 (partition) slot colors
    private static final int COLOR_INVENTORY = 0xFF8B8B8B;  // Grey
    private static final int COLOR_PARTITION = 0xFFFF9933;  // Orange

    private SearchFilterMode currentMode;

    public GuiSearchModeButton(int buttonId, int x, int y, SearchFilterMode initialMode) {
        super(buttonId, x, y, BUTTON_SIZE, BUTTON_SIZE, "");
        this.currentMode = initialMode;
    }

    public void setMode(SearchFilterMode mode) {
        this.currentMode = mode;
    }

    public SearchFilterMode getMode() {
        return currentMode;
    }

    public SearchFilterMode cycleMode() {
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

        // Draw the mode indicator dot
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        drawModeIndicator();
    }

    // TODO: Optimize by using textures instead of per-pixel drawing
    private void drawModeIndicator() {
        int dotX = this.x + 3;
        int dotY = this.y + 3;
        int dotSize = 6;

        // Draw black outline first (1px border around the dot)
        drawRect(dotX - 1, dotY - 1, dotX + dotSize + 1, dotY, 0xFF000000);  // top
        drawRect(dotX - 1, dotY + dotSize, dotX + dotSize + 1, dotY + dotSize + 1, 0xFF000000);  // bottom
        drawRect(dotX - 1, dotY, dotX, dotY + dotSize, 0xFF000000);  // left
        drawRect(dotX + dotSize, dotY, dotX + dotSize + 1, dotY + dotSize, 0xFF000000);  // right

        switch (currentMode) {
            case INVENTORY:
                // Solid grey dot
                drawRect(dotX, dotY, dotX + dotSize, dotY + dotSize, COLOR_INVENTORY);
                break;

            case PARTITION:
                // Solid orange dot
                drawRect(dotX, dotY, dotX + dotSize, dotY + dotSize, COLOR_PARTITION);
                break;

            case MIXED:
                // Solid colors with gradient only on the diagonal
                // Orange (partition) on bottom-left, grey (inventory) on top-right
                // Diagonal pixels get interpolated color
                for (int row = 0; row < dotSize; row++) {
                    for (int col = 0; col < dotSize; col++) {
                        int color;
                        if (col > row) {
                            // Above diagonal (top-right): solid grey
                            color = COLOR_INVENTORY;
                        } else if (col < row) {
                            // Below diagonal (bottom-left): solid orange
                            color = COLOR_PARTITION;
                        } else {
                            // On the diagonal: interpolate from orange to grey
                            // t goes from 0 (top-left of diagonal) to 1 (bottom-right of diagonal)
                            float t = (float) row / (float) (dotSize - 1);
                            color = interpolateColor(COLOR_PARTITION, COLOR_INVENTORY, t);
                        }

                        drawRect(dotX + col, dotY + row, dotX + col + 1, dotY + row + 1, color);
                    }
                }
                break;
        }
    }

    /**
     * Interpolate between two ARGB colors.
     * @param color1 Starting color (at t=0)
     * @param color2 Ending color (at t=1)
     * @param t Interpolation factor (0 to 1)
     * @return Interpolated color
     */
    private int interpolateColor(int color1, int color2, float t) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Get the tooltip lines for this button.
     */
    public List<String> getTooltip() {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format("gui.cellterminal.search_mode"));

        String modeKey;
        switch (currentMode) {
            case INVENTORY:
                modeKey = "gui.cellterminal.search_mode.inventory";
                break;
            case PARTITION:
                modeKey = "gui.cellterminal.search_mode.partition";
                break;
            case MIXED:
            default:
                modeKey = "gui.cellterminal.search_mode.mixed";
                break;
        }
        tooltip.add("§7" + I18n.format(modeKey));

        return tooltip;
    }
}
