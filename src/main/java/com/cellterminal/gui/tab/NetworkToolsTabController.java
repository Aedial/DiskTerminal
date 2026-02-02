package com.cellterminal.gui.tab;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.I18n;

import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.gui.networktools.INetworkTool;
import com.cellterminal.gui.networktools.NetworkToolRegistry;


/**
 * Tab controller for the Network Tools tab (Tab 5).
 * This tab displays a list of batch operation tools that can affect the entire network.
 */
public class NetworkToolsTabController implements ITabController {

    public static final int TAB_INDEX = 5;

    @Override
    public int getTabIndex() {
        return TAB_INDEX;
    }

    @Override
    public SearchFilterMode getEffectiveSearchMode(SearchFilterMode userSelectedMode) {
        // Network tools tab respects the user's selected search mode
        return userSelectedMode;
    }

    @Override
    public boolean showSearchModeButton() {
        return true;
    }

    @Override
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();
        lines.add("§c" + I18n.format("gui.cellterminal.networktools.warning.caution"));
        lines.add(I18n.format("gui.cellterminal.networktools.warning.irreversible"));
        lines.add("");
        lines.add(I18n.format("gui.cellterminal.networktools.help.read_tooltip"));

        return lines;
    }

    @Override
    public boolean handleClick(TabClickContext context) {
        // Click handling is done by the GUI/renderer for this tab
        return false;
    }

    @Override
    public boolean handleKeyTyped(int keyCode, TabContext context) {
        // No special keybinds for Network Tools tab
        return false;
    }

    @Override
    public boolean requiresServerPolling() {
        // Need server data for accurate tool previews
        return false;
    }

    @Override
    public List<Object> getLines(TabContext context) {
        // Return tools as lines for rendering
        List<Object> lines = new ArrayList<>();
        for (INetworkTool tool : NetworkToolRegistry.getAllTools()) lines.add(tool);

        return lines;
    }

    @Override
    public int getLineCount(TabContext context) {
        return NetworkToolRegistry.getToolCount();
    }
}
