package com.cellterminal.gui.tab;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.I18n;

import com.cellterminal.client.SearchFilterMode;


/**
 * Tab controller for the Inventory tab (Tab 1).
 * This tab displays cells with their contents in a grid format.
 * Content items show "P" indicator if they're in the cell's partition.
 */
public class InventoryTabController implements ITabController {

    public static final int TAB_INDEX = 1;

    @Override
    public int getTabIndex() {
        return TAB_INDEX;
    }

    @Override
    public SearchFilterMode getEffectiveSearchMode(SearchFilterMode userSelectedMode) {
        // Inventory tab forces INVENTORY mode for searching by contents
        return SearchFilterMode.INVENTORY;
    }

    @Override
    public boolean showSearchModeButton() {
        // Inventory tab hides the search mode button (mode is forced)
        return false;
    }

    @Override
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();

        lines.add(I18n.format("gui.cellterminal.controls.partition_indicator"));
        lines.add(I18n.format("gui.cellterminal.controls.click_partition_toggle"));
        lines.add(I18n.format("gui.cellterminal.controls.double_click_storage"));
        lines.add(I18n.format("gui.cellterminal.right_click_rename"));

        return lines;
    }

    @Override
    public boolean handleClick(TabClickContext context) {
        // Handle partition-all button click
        if (context.hoveredPartitionAllButtonCell != null && context.isLeftClick()) {
            // Partition all action is handled in GuiCellTerminalBase for now
            // as it requires sending a network packet
            return false;  // Let the GUI handle this
        }

        // Cell tab click handling is done by TerminalClickHandler
        return false;
    }

    @Override
    public boolean handleKeyTyped(int keyCode, TabContext context) {
        // Inventory tab has no special keybinds
        return false;
    }

    @Override
    public boolean requiresServerPolling() {
        return false;
    }

    @Override
    public List<Object> getLines(TabContext context) {
        return context.getInventoryLines();
    }

    @Override
    public int getLineCount(TabContext context) {
        return context.getInventoryLines().size();
    }
}
