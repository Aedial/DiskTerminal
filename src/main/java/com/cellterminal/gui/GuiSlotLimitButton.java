package com.cellterminal.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

import com.cellterminal.client.SlotLimit;


/**
 * Button for cycling through slot limit options (8, 32, 64, unlimited).
 * Controls how many content types are displayed per cell/storage bus.
 */
public class GuiSlotLimitButton extends GuiButton {

    public static final int BUTTON_SIZE = 16;

    private static final int COLOR_NORMAL = 0xFF8B8B8B;
    private static final int COLOR_HOVER = 0xFF707070;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    private SlotLimit currentLimit;

    public GuiSlotLimitButton(int buttonId, int x, int y, SlotLimit initialLimit) {
        super(buttonId, x, y, BUTTON_SIZE, BUTTON_SIZE, "");
        this.currentLimit = initialLimit;
    }

    public SlotLimit getLimit() {
        return currentLimit;
    }

    public void setLimit(SlotLimit limit) {
        this.currentLimit = limit;
    }

    /**
     * Cycle to the next limit and return the new value.
     */
    public SlotLimit cycleLimit() {
        this.currentLimit = this.currentLimit.next();

        return this.currentLimit;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        this.hovered = mouseX >= this.x && mouseY >= this.y
            && mouseX < this.x + this.width && mouseY < this.y + this.height;

        int bgColor = this.hovered ? COLOR_HOVER : COLOR_NORMAL;
        drawRect(this.x, this.y, this.x + this.width, this.y + this.height, bgColor);

        // Draw 3D border
        drawRect(this.x, this.y, this.x + this.width, this.y + 1, brightenColor(bgColor, 0.3f));
        drawRect(this.x, this.y, this.x + 1, this.y + this.height, brightenColor(bgColor, 0.3f));
        drawRect(this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, darkenColor(bgColor, 0.3f));
        drawRect(this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, darkenColor(bgColor, 0.3f));

        // Draw limit text centered
        FontRenderer fr = mc.fontRenderer;
        String text = currentLimit.getDisplayText();
        int textWidth = fr.getStringWidth(text);
        int textX = this.x + (this.width - textWidth) / 2;
        // The infinity glyph can be visually wider than normal digits; nudge right slightly to better center it
        if (currentLimit != null && currentLimit.getDisplayText() != null && currentLimit == SlotLimit.UNLIMITED) {
            textX += 1;
        }
        int textY = this.y + (this.height - 8) / 2;
        fr.drawString(text, textX, textY, COLOR_TEXT);
    }

    /**
     * Tooltip lines for the slot limit button.
     */
    public List<String> getTooltip() {
        String limitText = "";

        if (currentLimit != null) {
            switch (currentLimit) {
                case LIMIT_8:
                    limitText = I18n.format("gui.cellterminal.slot_limit.8");
                    break;
                case LIMIT_32:
                    limitText = I18n.format("gui.cellterminal.slot_limit.32");
                    break;
                case LIMIT_64:
                    limitText = I18n.format("gui.cellterminal.slot_limit.64");
                    break;
                case UNLIMITED:
                default:
                    limitText = I18n.format("gui.cellterminal.slot_limit.unlimited");
                    break;
            }
        }

        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format("gui.cellterminal.slot_limit", limitText));
        return tooltip;
    }

    private static int brightenColor(int color, float amount) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) + 255 * amount));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) + 255 * amount));
        int b = Math.min(255, (int) ((color & 0xFF) + 255 * amount));

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int darkenColor(int color, float amount) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * (1 - amount));
        int g = (int) (((color >> 8) & 0xFF) * (1 - amount));
        int b = (int) ((color & 0xFF) * (1 - amount));

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
