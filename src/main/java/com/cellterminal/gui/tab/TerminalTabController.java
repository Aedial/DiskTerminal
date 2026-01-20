package com.cellterminal.gui.tab;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.I18n;

import com.cellterminal.client.SearchFilterMode;


/**
 * Tab controller for the Terminal tab (Tab 0).
 * This tab displays a tree-style list of storages with expandable cells.
 */
public class TerminalTabController implements ITabController {

    public static final int TAB_INDEX = 0;

    @Override
    public int getTabIndex() {
        return TAB_INDEX;
    }

    @Override
    public SearchFilterMode getEffectiveSearchMode(SearchFilterMode userSelectedMode) {
        // Terminal tab respects the user's selected search mode
        return userSelectedMode;
    }

    @Override
    public boolean showSearchModeButton() {
        // Only Terminal tab shows the search mode button
        return true;
    }

    @Override
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();
        lines.add(I18n.format("gui.cellterminal.controls.double_click_storage_cell"));

        return lines;
    }

    @Override
    public boolean handleClick(TabClickContext context) {
        // Terminal tab click handling is done by TerminalClickHandler
        // This controller just provides the structure
        return false;
    }

    @Override
    public boolean handleKeyTyped(int keyCode, TabContext context) {
        // Terminal tab has no special keybinds
        return false;
    }

    @Override
    public boolean requiresServerPolling() {
        return false;
    }

    @Override
    public List<Object> getLines(TabContext context) {
        return context.getTerminalLines();
    }

    @Override
    public int getLineCount(TabContext context) {
        return context.getTerminalLines().size();
    }
}
