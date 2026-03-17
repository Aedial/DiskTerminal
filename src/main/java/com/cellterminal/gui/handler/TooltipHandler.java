package com.cellterminal.gui.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.resources.I18n;

import com.cellterminal.gui.buttons.*;
import com.cellterminal.gui.PriorityFieldManager;


/**
 * Handles tooltip rendering for GuiCellTerminalBase.
 */
public class TooltipHandler {

    /**
     * Context containing all the state needed for rendering tooltips.
     */
    public static class TooltipContext {
        // Widget state
        public GuiTerminalStyleButton terminalStyleButton;
        public GuiSearchModeButton searchModeButton;
        public GuiSearchHelpButton searchHelpButton;
        public GuiSubnetVisibilityButton subnetVisibilityButton;
        public FilterPanelManager filterPanelManager;

        // Search error state
        public boolean hasSearchError = false;
        public List<String> searchErrorMessage = null;
        public int searchFieldX, searchFieldY, searchFieldWidth, searchFieldHeight;
    }

    /**
     * Callback interface for drawing tooltips.
     */
    public interface TooltipRenderer {
        void drawHoveringText(List<String> lines, int x, int y);
    }

    /**
     * Draw all tooltips for the current state.
     */
    public static void drawTooltips(TooltipContext ctx, TooltipRenderer renderer, int mouseX, int mouseY) {

        // Widget tooltips
        if (ctx.terminalStyleButton != null && ctx.terminalStyleButton.isMouseOver()) {
            renderer.drawHoveringText(ctx.terminalStyleButton.getTooltip(), mouseX, mouseY);

            return;
        }

        if (ctx.searchModeButton != null && ctx.searchModeButton.visible && ctx.searchModeButton.isMouseOver()) {
            renderer.drawHoveringText(ctx.searchModeButton.getTooltip(), mouseX, mouseY);

            return;
        }

        if (ctx.searchHelpButton != null && ctx.searchHelpButton.visible && ctx.searchHelpButton.isMouseOver()) {
            renderer.drawHoveringText(ctx.searchHelpButton.getTooltip(), mouseX, mouseY);

            return;
        }

        if (ctx.subnetVisibilityButton != null && ctx.subnetVisibilityButton.visible && ctx.subnetVisibilityButton.isMouseOver()) {
            renderer.drawHoveringText(ctx.subnetVisibilityButton.getTooltip(), mouseX, mouseY);

            return;
        }

        // Search error tooltip - show when hovering over search field with error
        if (ctx.hasSearchError && ctx.searchErrorMessage != null) {
            boolean hoveringSearchField = mouseX >= ctx.searchFieldX && mouseX < ctx.searchFieldX + ctx.searchFieldWidth
                && mouseY >= ctx.searchFieldY && mouseY < ctx.searchFieldY + ctx.searchFieldHeight;
            if (hoveringSearchField) {
                List<String> errorTooltip = new ArrayList<>();
                errorTooltip.add("§c" + I18n.format("gui.cellterminal.search_error"));
                for (String line : ctx.searchErrorMessage) errorTooltip.add("§c- " + line);
                renderer.drawHoveringText(errorTooltip, mouseX, mouseY);

                return;
            }
        }

        if (PriorityFieldManager.getInstance().isMouseOverField(mouseX, mouseY)) {
            renderer.drawHoveringText(Collections.singletonList(I18n.format("gui.cellterminal.priority.tooltip")), mouseX, mouseY);

            return;
        }

        // Filter button tooltips
        if (ctx.filterPanelManager != null) {
            GuiFilterButton hoveredFilter = ctx.filterPanelManager.getHoveredButton(mouseX, mouseY);
            if (hoveredFilter != null) {
                renderer.drawHoveringText(hoveredFilter.getTooltip(), mouseX, mouseY);

                return;
            }

            // Slot limit button tooltip
            GuiSlotLimitButton slotBtn = ctx.filterPanelManager.getSlotLimitButton();
            if (slotBtn != null && slotBtn.visible
                    && mouseX >= slotBtn.x && mouseX < slotBtn.x + slotBtn.width
                    && mouseY >= slotBtn.y && mouseY < slotBtn.y + slotBtn.height) {
                renderer.drawHoveringText(slotBtn.getTooltip(), mouseX, mouseY);

            }
        }
    }
}
