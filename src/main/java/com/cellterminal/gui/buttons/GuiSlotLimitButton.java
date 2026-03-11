package com.cellterminal.gui.buttons;

import java.util.ArrayList;
import java.util.List;

import com.cellterminal.gui.GuiConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;

import com.cellterminal.client.SlotLimit;


/**
 * Button for cycling through slot limit options (8, 32, 64, unlimited).
 * Controls how many content types are displayed per cell/storage bus.
 */
public class GuiSlotLimitButton extends GuiAtlasButton {

    private static final int SIZE = GuiConstants.TERMINAL_SIDE_BUTTON_SIZE;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    private SlotLimit currentLimit;

    public GuiSlotLimitButton(int buttonId, int x, int y, SlotLimit initialLimit) {
        super(buttonId, x, y, SIZE);
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
    protected int getBackgroundTexX() {
        return GuiConstants.TERMINAL_STYLE_BUTTON_X;
    }

    @Override
    protected int getBackgroundTexY() {
        return GuiConstants.TERMINAL_STYLE_BUTTON_Y + (this.hovered ? SIZE : 0);
    }

    @Override
    protected void drawForeground(Minecraft mc) {
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
}
