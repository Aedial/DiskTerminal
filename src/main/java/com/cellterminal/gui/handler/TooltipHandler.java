package com.cellterminal.gui.handler;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import com.cellterminal.client.CellInfo;
import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.gui.GuiSearchModeButton;
import com.cellterminal.gui.GuiTerminalStyleButton;
import com.cellterminal.gui.PopupCellInventory;
import com.cellterminal.gui.PopupCellPartition;
import com.cellterminal.gui.PriorityFieldManager;


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
        public PriorityFieldManager priorityFieldManager;
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
        // Terminal tab hover preview (not full tooltip)
        // This is handled separately in drawScreen

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

        if (ctx.priorityFieldManager != null && ctx.priorityFieldManager.isMouseOverField(mouseX, mouseY)) {
            renderer.drawHoveringText(Collections.singletonList(I18n.format("gui.cellterminal.priority.tooltip")), mouseX, mouseY);

            return;
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
        switch (tab) {
            case TAB_TERMINAL:
                return I18n.format("gui.cellterminal.tab.terminal.tooltip");
            case TAB_INVENTORY:
                return I18n.format("gui.cellterminal.tab.inventory.tooltip");
            case TAB_PARTITION:
                return I18n.format("gui.cellterminal.tab.partition.tooltip");
            case TAB_STORAGE_BUS_INVENTORY:
                return I18n.format("gui.cellterminal.tab.storage_bus_inventory.tooltip");
            case TAB_STORAGE_BUS_PARTITION:
                return I18n.format("gui.cellterminal.tab.storage_bus_partition.tooltip");
            default:
                return "";
        }
    }
}
