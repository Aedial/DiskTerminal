package com.cellterminal.gui.tab;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.I18n;

import com.cellterminal.client.SearchFilterMode;


/**
 * Tab controller for the Storage Bus Inventory tab (Tab 3).
 * This tab displays storage buses with their connected inventory contents.
 */
public class StorageBusInventoryTabController implements ITabController {

    public static final int TAB_INDEX = 3;

    @Override
    public int getTabIndex() {
        return TAB_INDEX;
    }

    @Override
    public SearchFilterMode getEffectiveSearchMode(SearchFilterMode userSelectedMode) {
        // Storage Bus Inventory tab forces INVENTORY mode
        return SearchFilterMode.INVENTORY;
    }

    @Override
    public boolean showSearchModeButton() {
        // Storage bus tabs hide the search mode button
        return false;
    }

    @Override
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();

        lines.add(I18n.format("gui.cellterminal.controls.filter_indicator"));
        lines.add(I18n.format("gui.cellterminal.controls.click_to_remove"));
        lines.add(I18n.format("gui.cellterminal.controls.double_click_storage"));

        return lines;
    }

    @Override
    public boolean handleClick(TabClickContext context) {
        // Storage bus click handling is done in GuiCellTerminalBase.handleStorageBusTabClick
        return false;
    }

    @Override
    public boolean handleKeyTyped(int keyCode, TabContext context) {
        // Storage Bus Inventory tab has no special keybinds
        return false;
    }

    @Override
    public boolean requiresServerPolling() {
        // Storage bus tabs require server polling
        return true;
    }

    @Override
    public List<Object> getLines(TabContext context) {
        return context.getStorageBusInventoryLines();
    }

    @Override
    public int getLineCount(TabContext context) {
        return context.getStorageBusInventoryLines().size();
    }
}
