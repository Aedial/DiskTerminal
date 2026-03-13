package com.cellterminal.gui.buttons;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.I18n;

import com.cellterminal.gui.GuiConstants;


/**
 * A small button used to navigate to/from subnet overview mode.
 * When in normal view (main or subnet), shows a left arrow to enter overview.
 * When in overview mode, shows a right arrow to go back.
 */
public class GuiBackButton extends GuiAtlasButton {

    private static final int SIZE = GuiConstants.SUBNET_BUTTON_SIZE;

    private boolean isInOverviewMode;

    public GuiBackButton(int buttonId, int x, int y) {
        super(buttonId, x, y, SIZE);
        this.isInOverviewMode = false;
    }

    /**
     * Update the button state based on whether we're in subnet overview mode.
     */
    public void setInOverviewMode(boolean inOverview) {
        this.isInOverviewMode = inOverview;
    }

    @Override
    protected int getBackgroundTexX() {
        return GuiConstants.SUBNET_BUTTON_X + (isInOverviewMode ? SIZE : 0);
    }

    @Override
    protected int getBackgroundTexY() {
        return GuiConstants.SUBNET_BUTTON_Y + (this.hovered ? SIZE : 0);
    }

    /**
     * Get the tooltip for this button.
     */
    public List<String> getTooltip() {
        List<String> tooltip = new ArrayList<>();

        if (isInOverviewMode) {
            tooltip.add(I18n.format("cellterminal.subnet.back"));
            tooltip.add("");
            tooltip.add(I18n.format("cellterminal.subnet.back.desc"));
        } else {
            tooltip.add(I18n.format("cellterminal.subnet.overview"));
            tooltip.add("");
            tooltip.add(I18n.format("cellterminal.subnet.overview.desc"));
        }

        return tooltip;
    }
}
