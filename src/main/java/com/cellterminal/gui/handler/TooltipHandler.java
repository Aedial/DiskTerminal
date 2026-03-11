package com.cellterminal.gui.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import com.cellterminal.config.CellTerminalServerConfig;
import com.cellterminal.gui.buttons.*;
import com.cellterminal.gui.PopupCellInventory;
import com.cellterminal.gui.PopupCellPartition;
import com.cellterminal.gui.PriorityFieldManager;
import com.cellterminal.gui.widget.tab.AbstractTabWidget;


/**
 * Handles tooltip rendering for GuiCellTerminalBase.
 */
public class TooltipHandler {

    /**
     * Context containing all the state needed for rendering tooltips.
     */
    public static class TooltipContext {
        // Tab state
        public int currentTab;
        public int hoveredTab = -1;

        // Popup state
        public PopupCellInventory inventoryPopup;
        public PopupCellPartition partitionPopup;

        // Widget state
        public GuiTerminalStyleButton terminalStyleButton;
        public GuiSearchModeButton searchModeButton;
        public GuiSearchHelpButton searchHelpButton;
        public GuiSubnetVisibilityButton subnetVisibilityButton;
        public PriorityFieldManager priorityFieldManager;
        public FilterPanelManager filterPanelManager;

        // Search error state
        public boolean hasSearchError = false;
        public List<String> searchErrorMessage = null;
        public int searchFieldX, searchFieldY, searchFieldWidth, searchFieldHeight;

        // Tab widgets for delegation
        public AbstractTabWidget[] tabWidgets = null;
    }

    /**
     * Callback interface for drawing tooltips.
     */
    public interface TooltipRenderer {
        void drawHoveringText(List<String> lines, int x, int y);
        List<String> getItemToolTip(ItemStack stack);
    }

    /**
     * Draw all tooltips for the current state.
     */
    public static void drawTooltips(TooltipContext ctx, TooltipRenderer renderer, int mouseX, int mouseY) {

        // Tab tooltips
        if (ctx.hoveredTab >= 0 && ctx.inventoryPopup == null && ctx.partitionPopup == null) {
            String tooltip = getTabTooltip(ctx.hoveredTab, ctx.tabWidgets);
            if (!tooltip.isEmpty()) {
                renderer.drawHoveringText(Collections.singletonList(tooltip), mouseX, mouseY);

                return;
            }
        }

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

        if (ctx.priorityFieldManager != null && ctx.priorityFieldManager.isMouseOverField(mouseX, mouseY)) {
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

    private static String getTabTooltip(int tab, AbstractTabWidget[] tabWidgets) {
        String baseTooltip;

        // Delegate to tab widget if available
        if (tabWidgets != null && tab >= 0 && tab < tabWidgets.length && tabWidgets[tab] != null) {
            baseTooltip = tabWidgets[tab].getTabTooltip();
        } else {
            return "";
        }

        // Add disabled notice if tab is disabled in server config
        if (CellTerminalServerConfig.isInitialized() && !CellTerminalServerConfig.getInstance().isTabEnabled(tab)) {
            return baseTooltip + " " + I18n.format("gui.cellterminal.tab.disabled");
        }

        return baseTooltip;
    }
}
