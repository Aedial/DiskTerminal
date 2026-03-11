package com.cellterminal.gui.widget.tab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;

import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.handler.TerminalDataManager;
import com.cellterminal.gui.networktools.INetworkTool;
import com.cellterminal.gui.networktools.NetworkToolRegistry;
import com.cellterminal.gui.widget.IWidget;
import com.cellterminal.gui.widget.NetworkToolRowWidget;


/**
 * Tab widget for the Network Tools tab (Tab 6).
 * <p>
 * Displays a scrollable list of network tools, each rendered as an independent
 * {@link NetworkToolRowWidget}. Each row is 36px tall (2x the standard 18px row height),
 * so this tab overrides scroll calculations accordingly.
 * <p>
 * Tools come from {@link NetworkToolRegistry} rather than the data manager.
 * The tool context (filters, storages) is fresh-fetched from the GUI context each frame.
 */
public class NetworkToolsTabWidget extends AbstractTabWidget {

    /** Cached tab icon (lazy initialized) */
    private ItemStack tabIcon = null;

    public NetworkToolsTabWidget(FontRenderer fontRenderer, RenderItem itemRender) {
        super(fontRenderer, itemRender);
    }

    // ---- Scroll calculation ----

    @Override
    public int getVisibleItemCount() {
        int contentHeight = rowsVisible * GuiConstants.ROW_HEIGHT;

        return contentHeight / NetworkToolRowWidget.ROW_HEIGHT;
    }

    // ---- Row building ----

    /**
     * Build visible tool rows for the current scroll window.
     * Overrides the standard row building to use TOOL_ROW_HEIGHT spacing.
     */
    @Override
    public void buildVisibleRows(List<?> lines, int scrollOffset) {
        visibleRows.clear();
        widgetDataMap.clear();

        int visibleCount = getVisibleItemCount();
        int end = Math.min(scrollOffset + visibleCount + 1, lines.size());

        for (int i = scrollOffset; i < end; i++) {
            int rowY = GuiConstants.CONTENT_START_Y
                + (i - scrollOffset) * NetworkToolRowWidget.ROW_HEIGHT;
            Object lineData = lines.get(i);
            IWidget widget = createRowWidget(lineData, rowY, lines, i);
            if (widget != null) {
                visibleRows.add(widget);
                widgetDataMap.put(widget, lineData);
            }
        }

        // No tree line propagation needed for tool rows
    }

    @Override
    protected IWidget createRowWidget(Object lineData, int y, List<?> allLines, int lineIndex) {
        if (!(lineData instanceof INetworkTool)) return null;

        INetworkTool tool = (INetworkTool) lineData;
        NetworkToolRowWidget row = new NetworkToolRowWidget(tool, y, fontRenderer, itemRender);

        // Wire up context supplier — lazily fetches tool context each frame
        row.setContextSupplier(() -> guiContext != null
            ? ((NetworkToolGuiContext) guiContext).createNetworkToolContext()
            : null);

        // Wire up run button click → show confirmation modal
        row.setOnRunClicked(() -> {
            if (guiContext != null) {
                ((NetworkToolGuiContext) guiContext).showNetworkToolConfirmation(tool);
            }
        });

        return row;
    }

    @Override
    protected boolean isContentLine(List<?> allLines, int index) {
        // All lines are tools (no headers/content distinction)
        return true;
    }

    // ---- Tab controller methods ----

    @Override
    @SuppressWarnings("unchecked")
    public List<Object> getLines(TerminalDataManager dataManager) {
        List<INetworkTool> tools = NetworkToolRegistry.getAllTools();

        return (List<Object>) (List<?>) tools;
    }

    @Override
    public boolean showSearchModeButton() {
        return true;
    }

    @Override
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();

        lines.add(I18n.format("gui.cellterminal.networktools.warning.caution"));
        lines.add(I18n.format("gui.cellterminal.networktools.warning.irreversible"));
        lines.add("");
        lines.add(I18n.format("gui.cellterminal.networktools.help.read_tooltip"));

        return lines;
    }

    @Override
    public ItemStack getTabIcon() {
        if (tabIcon == null) {
            tabIcon = AEApi.instance().definitions().items().networkTool()
                .maybeStack(1).orElse(ItemStack.EMPTY);
        }

        return tabIcon;
    }

    @Override
    public String getTabTooltip() {
        return I18n.format("gui.cellterminal.tab.network_tools.tooltip");
    }

    /**
     * Extended GUI context interface for network tools.
     * The parent GUI (GuiCellTerminalBase) implements this to provide
     * tool-specific callbacks that the tab widget needs.
     */
    public interface NetworkToolGuiContext extends GuiContext {
        /** Create a fresh ToolContext for tool preview and execution. */
        INetworkTool.ToolContext createNetworkToolContext();

        /** Show the confirmation modal for a network tool. */
        void showNetworkToolConfirmation(INetworkTool tool);
    }
}
