package com.cellterminal.gui.buttons;

import java.util.ArrayList;
import java.util.List;

import com.cellterminal.gui.GuiConstants;
import net.minecraft.client.resources.I18n;

import com.cellterminal.client.SearchFilterMode;


/**
 * Button that cycles through search filter modes and displays a visual indicator.
*/
public class GuiSearchModeButton extends GuiAtlasButton {

    private static final int SIZE = GuiConstants.SEARCH_MODE_BUTTON_SIZE;

    private SearchFilterMode currentMode;

    public GuiSearchModeButton(int buttonId, int x, int y, SearchFilterMode initialMode) {
        super(buttonId, x, y, SIZE);
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
    protected int getBackgroundTexX() {
        return GuiConstants.SEARCH_MODE_BUTTON_X + currentMode.ordinal() * SIZE;
    }

    @Override
    protected int getBackgroundTexY() {
        return GuiConstants.SEARCH_MODE_BUTTON_Y + (this.hovered ? SIZE : 0);
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
