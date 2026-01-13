package com.cellterminal.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import com.cellterminal.client.CellTerminalClientConfig.TerminalStyle;


/**
 * Button to toggle between terminal styles (small/tall).
 * Uses a custom texture or draws a simple icon.
 */
public class GuiTerminalStyleButton extends GuiButton {

    private static final int BUTTON_SIZE = 16;

    private TerminalStyle currentStyle;

    public GuiTerminalStyleButton(int buttonId, int x, int y, TerminalStyle initialStyle) {
        super(buttonId, x, y, BUTTON_SIZE, BUTTON_SIZE, "");
        this.currentStyle = initialStyle;
    }

    public void setStyle(TerminalStyle style) {
        this.currentStyle = style;
    }

    public TerminalStyle getStyle() {
        return currentStyle;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        this.hovered = mouseX >= this.x && mouseY >= this.y
                    && mouseX < this.x + this.width && mouseY < this.y + this.height;

        // Draw button background
        int bgColor = this.hovered ? 0xFF707070 : 0xFF8B8B8B;
        drawRect(this.x, this.y, this.x + this.width, this.y + this.height, bgColor);

        // Draw 3D border
        drawRect(this.x, this.y, this.x + this.width, this.y + 1, 0xFFFFFFFF);
        drawRect(this.x, this.y, this.x + 1, this.y + this.height, 0xFFFFFFFF);
        drawRect(this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, 0xFF555555);
        drawRect(this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, 0xFF555555);

        // Draw icon based on current style
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        drawStyleIcon(mc);
    }

    private void drawStyleIcon(Minecraft mc) {
        int iconX = this.x + 3;
        int iconY = this.y + 3;

        // Draw a simple representation of small vs tall
        if (currentStyle == TerminalStyle.SMALL) {
            // Small: draw a compact rectangle
            drawRect(iconX, iconY + 2, iconX + 10, iconY + 8, 0xFF404040);
            drawRect(iconX + 1, iconY + 3, iconX + 9, iconY + 7, 0xFFC6C6C6);
        } else {
            // Tall: draw an extended rectangle
            drawRect(iconX + 2, iconY, iconX + 8, iconY + 10, 0xFF404040);
            drawRect(iconX + 3, iconY + 1, iconX + 7, iconY + 9, 0xFFC6C6C6);
        }
    }

    /**
     * Get the tooltip lines for this button.
     */
    public List<String> getTooltip() {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format("gui.cellterminal.terminal_style"));

        String styleKey = currentStyle == TerminalStyle.SMALL
            ? "gui.cellterminal.terminal_style.small"
            : "gui.cellterminal.terminal_style.tall";
        tooltip.add("§7" + I18n.format(styleKey));

        return tooltip;
    }
}
