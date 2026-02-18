package com.cellterminal.gui.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import com.cellterminal.client.CellInfo;
import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.config.CellTerminalServerConfig;
import com.cellterminal.gui.FilterPanelManager;
import com.cellterminal.gui.GuiFilterButton;
import com.cellterminal.gui.GuiSlotLimitButton;
import com.cellterminal.gui.GuiSearchHelpButton;
import com.cellterminal.gui.GuiSearchModeButton;
import com.cellterminal.gui.GuiSubnetVisibilityButton;
import com.cellterminal.gui.GuiTerminalStyleButton;
import com.cellterminal.gui.PopupCellInventory;
import com.cellterminal.gui.PopupCellPartition;
import com.cellterminal.gui.PriorityFieldManager;
import com.cellterminal.gui.networktools.INetworkTool;
import com.cellterminal.gui.render.RenderContext;


/**
 * Handles tooltip rendering for GuiCellTerminalBase.
 */
public class TooltipHandler {

    // Tab constants
    public static final int TAB_TERMINAL = 0;
    public static final int TAB_INVENTORY = 1;
    public static final int TAB_PARTITION = 2;
    public static final int TAB_STORAGE_BUS_INVENTORY = 3;
    public static final int TAB_STORAGE_BUS_PARTITION = 4;
    public static final int TAB_NETWORK_TOOLS = 5;

    /**
     * Context containing all the state needed for rendering tooltips.
     */
    public static class TooltipContext {
        // Tab state
        public int currentTab;
        public int hoveredTab = -1;

        // Hover state
        public CellInfo hoveredCell;
        public int hoverType = 0;
        public ItemStack hoveredContentStack = ItemStack.EMPTY;
        public int hoveredContentX;
        public int hoveredContentY;

        // Storage bus state
        public StorageBusInfo hoveredClearButtonStorageBus;
        public StorageBusInfo hoveredIOModeButtonStorageBus;
        public StorageBusInfo hoveredPartitionAllButtonStorageBus;

        // Cell partition button state
        public CellInfo hoveredPartitionAllButtonCell;
        public CellInfo hoveredClearPartitionButtonCell;

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

        // Upgrade icon hover state
        public RenderContext.UpgradeIconTarget hoveredUpgradeIcon = null;

        // Network tools hover state
        public INetworkTool hoveredNetworkTool = null;
        public INetworkTool hoveredNetworkToolHelpButton = null;
        public INetworkTool.ToolPreviewInfo hoveredNetworkToolPreview = null;
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

        // Network tools help button tooltip
        if (ctx.hoveredNetworkToolHelpButton != null) {
            List<String> tooltip = new ArrayList<>();
            tooltip.add("§e" + ctx.hoveredNetworkToolHelpButton.getName());
            tooltip.add("");
            for (String line : ctx.hoveredNetworkToolHelpButton.getHelpLines()) tooltip.add("§7" + line);

            renderer.drawHoveringText(tooltip, mouseX, mouseY);

            return;
        }

        // Network tools preview tooltip - show tooltip lines from the preview
        if (ctx.hoveredNetworkToolPreview != null) {
            List<String> tooltipLines = ctx.hoveredNetworkToolPreview.getTooltipLines();
            if (tooltipLines != null && !tooltipLines.isEmpty()) {
                List<String> lines = new ArrayList<>();
                lines.add("§e" + ctx.hoveredNetworkTool.getName());
                lines.add("");
                lines.addAll(tooltipLines);
                renderer.drawHoveringText(lines, mouseX, mouseY);

                return;
            }
        }

        // Upgrade icon tooltips
        if (ctx.hoveredUpgradeIcon != null) {
            List<String> tooltip = new ArrayList<>();
            tooltip.add("§6" + ctx.hoveredUpgradeIcon.upgrade.getDisplayName());
            tooltip.add("");
            tooltip.add("§b" + I18n.format("gui.cellterminal.upgrade.click_extract"));
            tooltip.add("§b" + I18n.format("gui.cellterminal.upgrade.shift_click_inventory"));
            renderer.drawHoveringText(tooltip, mouseX, mouseY);

            return;
        }

        // Content item tooltips
        if ((ctx.currentTab == TAB_INVENTORY || ctx.currentTab == TAB_PARTITION
                || ctx.currentTab == TAB_STORAGE_BUS_INVENTORY || ctx.currentTab == TAB_STORAGE_BUS_PARTITION)
                && !ctx.hoveredContentStack.isEmpty()) {
            renderer.drawHoveringText(renderer.getItemToolTip(ctx.hoveredContentStack), ctx.hoveredContentX, ctx.hoveredContentY);

            return;
        }

        // Tab tooltips
        if (ctx.hoveredTab >= 0 && ctx.inventoryPopup == null && ctx.partitionPopup == null) {
            String tooltip = getTabTooltip(ctx.hoveredTab);
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

                return;
            }
        }

        // Storage bus button tooltips
        if (ctx.hoveredClearButtonStorageBus != null) {
            renderer.drawHoveringText(Collections.singletonList(I18n.format("gui.cellterminal.storagebus.clear")), mouseX, mouseY);

            return;
        }

        if (ctx.hoveredIOModeButtonStorageBus != null) {
            if (ctx.hoveredIOModeButtonStorageBus.supportsIOMode()) {
                String currentMode = ctx.hoveredIOModeButtonStorageBus.getIOModeDisplayName();
                renderer.drawHoveringText(Collections.singletonList(
                    I18n.format("gui.cellterminal.storagebus.iomode.current", currentMode)), mouseX, mouseY);
            } else {
                renderer.drawHoveringText(Collections.singletonList(
                    I18n.format("gui.cellterminal.storagebus.iomode.unsupported")), mouseX, mouseY);
            }

            return;
        }

        // Partition button tooltips
        if (ctx.hoveredPartitionAllButtonCell != null) {
            renderer.drawHoveringText(Collections.singletonList(I18n.format("gui.cellterminal.cell.partitionall")), mouseX, mouseY);

            return;
        }

        if (ctx.hoveredPartitionAllButtonStorageBus != null) {
            renderer.drawHoveringText(Collections.singletonList(I18n.format("gui.cellterminal.storagebus.partitionall")), mouseX, mouseY);

            return;
        }

        if (ctx.hoveredClearPartitionButtonCell != null) {
            renderer.drawHoveringText(Collections.singletonList(I18n.format("gui.cellterminal.cell.clearpartition")), mouseX, mouseY);

            return;
        }
    }

    private static String getTabTooltip(int tab) {
        String baseTooltip;

        switch (tab) {
            case TAB_TERMINAL:
                baseTooltip = I18n.format("gui.cellterminal.tab.terminal.tooltip");
                break;
            case TAB_INVENTORY:
                baseTooltip = I18n.format("gui.cellterminal.tab.inventory.tooltip");
                break;
            case TAB_PARTITION:
                baseTooltip = I18n.format("gui.cellterminal.tab.partition.tooltip");
                break;
            case TAB_STORAGE_BUS_INVENTORY:
                baseTooltip = I18n.format("gui.cellterminal.tab.storage_bus_inventory.tooltip");
                break;
            case TAB_STORAGE_BUS_PARTITION:
                baseTooltip = I18n.format("gui.cellterminal.tab.storage_bus_partition.tooltip");
                break;
            case TAB_NETWORK_TOOLS:
                baseTooltip = I18n.format("gui.cellterminal.tab.network_tools.tooltip");
                break;
            default:
                return "";
        }

        // Add disabled notice if tab is disabled in server config
        if (CellTerminalServerConfig.isInitialized() && !CellTerminalServerConfig.getInstance().isTabEnabled(tab)) {
            return baseTooltip + " " + I18n.format("gui.cellterminal.tab.disabled");
        }

        return baseTooltip;
    }
}
