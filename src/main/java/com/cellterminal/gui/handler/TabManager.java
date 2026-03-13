package com.cellterminal.gui.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;

import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.client.TabStateManager;
import com.cellterminal.config.CellTerminalClientConfig;
import com.cellterminal.config.CellTerminalServerConfig;
import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.PriorityFieldManager;
import com.cellterminal.gui.rename.InlineRenameManager;
import com.cellterminal.gui.widget.line.SlotsLine;
import com.cellterminal.gui.widget.tab.AbstractTabWidget;
import com.cellterminal.gui.widget.tab.CellContentTabWidget;
import com.cellterminal.gui.widget.tab.GuiContext;
import com.cellterminal.gui.widget.tab.NetworkToolsTabWidget;
import com.cellterminal.gui.widget.tab.StorageBusTabWidget;
import com.cellterminal.gui.widget.tab.SubnetOverviewTabWidget;
import com.cellterminal.gui.widget.tab.TempAreaTabWidget;
import com.cellterminal.gui.widget.tab.TerminalTabWidget;


/**
 * Manages tab state, rendering, switching, and active widget access.
 * <p>
 * Centralizes all tab-related logic that was previously in GuiCellTerminalBase:
 * tab widget lifecycle, tab clicks, tab rendering, search mode delegation,
 * and tab tooltips.
 * <p>
 * The parent GUI communicates tab switch side effects via {@link TabSwitchListener}.
 */
public class TabManager {

    /**
     * Listener for tab switch events.
     * The GUI implements this to handle side effects of tab switching
     * (scrollbar updates, filter buttons, search reapplication, etc.).
     * <p>
     * Note: rename cancellation and scroll position save/restore are handled
     * internally by TabManager. The listener should NOT duplicate those.
     */
    public interface TabSwitchListener {
        /** Called before the tab index changes. Exit modes, close popups, etc. */
        void onPreSwitch(int oldTab);

        /** Called after the tab index changes. Update scrollbar, filters, search, etc. */
        void onPostSwitch(int newTab);
    }

    /**
     * Provides scroll position access for TabManager.
     * Decouples TabManager from the GUI's scrollbar implementation.
     */
    public interface ScrollAccessor {
        /** Get the current scroll position. */
        int getCurrentScroll();

        /** Scroll to a specific line index. */
        void scrollToLine(int lineIndex);
    }

    // ---- State ----

    private int currentTab;
    private int hoveredTab = -1;
    private AbstractTabWidget[] tabWidgets;
    private final TabSwitchListener listener;
    private ScrollAccessor scrollAccessor;

    // Typed widget reference for preview cell access (only TerminalTabWidget exposes getPreviewCell)
    private TerminalTabWidget terminalWidget;

    // Subnet overview pseudo-tab (separate from real tabs. No tab button, activated via tab index)
    private SubnetOverviewTabWidget subnetTab;
    private int previousRealTab = GuiConstants.TAB_TERMINAL;

    // Tab icons for composite rendering (lazy initialized)
    private ItemStack tabIconInventory;
    private ItemStack tabIconPartition;
    private ItemStack tabIconStorageBus;

    // Controls help widget cache (used for both rendering and JEI exclusion area calculation).
    // Stored here so the GUI can query the wrapped lines without maintaining its own tab-tracking logic.
    private List<String> cachedControlsHelpLines = new ArrayList<>();
    private int cachedControlsHelpTab = Integer.MIN_VALUE;

    /**
     * Create a new TabManager with the given initial tab and switch listener.
     *
     * @param initialTab The initially selected tab index (validated to be in range and enabled)
     * @param listener Callback for tab switch events
     */
    public TabManager(int initialTab, TabSwitchListener listener) {
        this.currentTab = validateTab(initialTab);
        this.listener = listener;
    }

    /**
     * Set the scroll accessor for scroll position save/restore during tab switches.
     * Must be called after the scrollbar is available (typically in initGui).
     */
    public void setScrollAccessor(ScrollAccessor scrollAccessor) {
        this.scrollAccessor = scrollAccessor;
    }

    /**
     * Validate and clamp a tab index to the valid range.
     * Also checks server-side tab enablement and falls back to the first enabled tab.
     */
    private int validateTab(int tab) {
        if (tab < 0 || tab > GuiConstants.LAST_TAB) tab = GuiConstants.TAB_TERMINAL;

        if (CellTerminalServerConfig.isInitialized() && !CellTerminalServerConfig.getInstance().isTabEnabled(tab)) {
            tab = findFirstEnabledTab();
        }

        return tab;
    }

    /**
     * Find the first enabled tab, or fall back to TAB_TERMINAL.
     */
    private int findFirstEnabledTab() {
        if (!CellTerminalServerConfig.isInitialized()) return GuiConstants.TAB_TERMINAL;

        CellTerminalServerConfig config = CellTerminalServerConfig.getInstance();

        for (int i = 0; i <= GuiConstants.LAST_TAB; i++) {
            if (config.isTabEnabled(i)) return i;
        }

        // Fallback to terminal tab even if disabled (should not happen in practice)
        return GuiConstants.TAB_TERMINAL;
    }

    // ========================================================================
    // Widget lifecycle
    // ========================================================================

    /**
     * Create and initialize all tab widgets.
     * Called during {@code initGui()} when the GUI is (re)initialized.
     *
     * @param fontRenderer Font renderer for text drawing
     * @param itemRender Item renderer for icon drawing
     * @param guiLeft GUI left edge absolute X
     * @param guiTop GUI top edge absolute Y
     * @param rowsVisible Number of visible rows in the scroll area
     * @param context GUI context for widget callbacks
     */
    public void initWidgets(FontRenderer fontRenderer, RenderItem itemRender,
                            int guiLeft, int guiTop, int rowsVisible,
                            GuiContext context) {
        // Create tab widgets
        this.terminalWidget = new TerminalTabWidget(fontRenderer, itemRender);
        CellContentTabWidget inventoryWidget = new CellContentTabWidget(SlotsLine.SlotMode.CONTENT, fontRenderer, itemRender);
        CellContentTabWidget partitionWidget = new CellContentTabWidget(SlotsLine.SlotMode.PARTITION, fontRenderer, itemRender);
        TempAreaTabWidget tempAreaWidget = new TempAreaTabWidget(fontRenderer, itemRender);
        StorageBusTabWidget storageBusInventoryWidget = new StorageBusTabWidget(SlotsLine.SlotMode.CONTENT, fontRenderer, itemRender);
        StorageBusTabWidget storageBusPartitionWidget = new StorageBusTabWidget(SlotsLine.SlotMode.PARTITION, fontRenderer, itemRender);
        NetworkToolsTabWidget networkToolsWidget = new NetworkToolsTabWidget(fontRenderer, itemRender);

        // Populate indexed lookup
        this.tabWidgets = new AbstractTabWidget[] {
            terminalWidget,              // TAB_TERMINAL (0)
            inventoryWidget,             // TAB_INVENTORY (1)
            partitionWidget,             // TAB_PARTITION (2)
            tempAreaWidget,              // TAB_TEMP_AREA (3)
            storageBusInventoryWidget,   // TAB_STORAGE_BUS_INVENTORY (4)
            storageBusPartitionWidget,   // TAB_STORAGE_BUS_PARTITION (5)
            networkToolsWidget           // TAB_NETWORK_TOOLS (6)
        };

        // Initialize all widgets with shared GUI context, offsets, and row count
        for (AbstractTabWidget widget : tabWidgets) {
            if (widget == null) continue;
            widget.setGuiOffsets(guiLeft, guiTop);
            widget.setRowsVisible(rowsVisible);
            widget.init(context);
        }

        // Create and initialize the subnet overview pseudo-tab
        this.subnetTab = new SubnetOverviewTabWidget(fontRenderer, itemRender);
        this.subnetTab.setGuiOffsets(guiLeft, guiTop);
        this.subnetTab.setRowsVisible(rowsVisible);
        this.subnetTab.init(context);
    }

    // ========================================================================
    // Getters
    // ========================================================================

    /** Get the index of the currently selected tab. */
    public int getCurrentTab() {
        return currentTab;
    }

    /** Get the index of the currently hovered tab (-1 if none). */
    public int getHoveredTab() {
        return hoveredTab;
    }

    /** Get the active tab widget. Returns the subnet tab when currentTab is the subnet overview index. */
    public AbstractTabWidget getActiveTab() {
        if (currentTab == TabStateManager.TabType.SUBNET_OVERVIEW.getIndex() && subnetTab != null) return subnetTab;
        if (tabWidgets == null || currentTab < 0 || currentTab >= tabWidgets.length) return null;

        return tabWidgets[currentTab];
    }

    /** Get all tab widgets (indexed by tab constants). */
    public AbstractTabWidget[] getWidgets() {
        return tabWidgets;
    }

    /** Get the terminal tab widget for preview cell access. */
    public TerminalTabWidget getTerminalWidget() {
        return terminalWidget;
    }

    /** Get the number of tabs. */
    public int getTabCount() {
        return tabWidgets != null ? tabWidgets.length : 0;
    }

    /**
     * Get the data lines for the active tab widget.
     *
     * @param dataManager The terminal data manager
     * @return The active widget's line list, or empty list if no active widget
     */
    public List<Object> getActiveLines(TerminalDataManager dataManager) {
        AbstractTabWidget tab = getActiveTab();
        if (tab == null) return Collections.emptyList();

        return tab.getLines(dataManager);
    }

    /**
     * Get the visible item count for the active widget.
     * Accounts for non-standard row heights (e.g., NetworkTools at 36px per tool).
     */
    public int getActiveVisibleItemCount() {
        AbstractTabWidget tab = getActiveTab();

        return tab != null ? tab.getVisibleItemCount() : GuiConstants.DEFAULT_ROWS;
    }

    // ========================================================================
    // Tab switching
    // ========================================================================

    /**
     * Handle a click on the tab header area.
     * Checks if the click is on a tab and switches to it if valid.
     *
     * @return true if a tab was clicked (even if disabled), consuming the click
     */
    public boolean handleClick(int mouseX, int mouseY, int guiLeft, int guiTop) {
        int tabY = guiTop + GuiConstants.TAB_Y_OFFSET;

        // Click is outside tab header vertical bounds
        if (mouseY < tabY || mouseY >= tabY + GuiConstants.TAB_HEIGHT) return false;

        for (int i = 0; i <= GuiConstants.LAST_TAB; i++) {
            int tabX = guiLeft + 4 + (i * (GuiConstants.TAB_WIDTH + 2));

            if (mouseX >= tabX && mouseX < tabX + GuiConstants.TAB_WIDTH) {
                // Consume click but don't switch to disabled tab
                if (CellTerminalServerConfig.isInitialized()
                        && !CellTerminalServerConfig.getInstance().isTabEnabled(i)) {
                    return true;
                }

                if (currentTab != i) switchToTab(i);

                return true;
            }
        }

        return false;
    }

    /**
     * Switch to a different tab, firing pre/post switch callbacks.
     * Also persists the selected tab to client config (skipped for subnet pseudo-tab -1).
     * Use -1 to switch to the subnet overview pseudo-tab.
     *
     * @param newTab The tab index to switch to (-1 for subnet overview, 0+ for real tabs)
     */
    public void switchToTab(int newTab) {
        if (newTab == currentTab) return;

        int oldTab = currentTab;

        // Cancel editing in case the keybind switched subnets overview
        InlineRenameManager.getInstance().cancelEditing();

        // Save any priority field edits
        PriorityFieldManager.getInstance().unfocusAll();

        // Save scroll position for outgoing tab
        if (scrollAccessor != null) {
            TabStateManager.TabType oldTabType = TabStateManager.TabType.fromIndex(oldTab);
            TabStateManager.getInstance().setScrollPosition(oldTabType, scrollAccessor.getCurrentScroll());
        }

        listener.onPreSwitch(oldTab);

        // Remember the last real tab
        if (oldTab >= 0) previousRealTab = oldTab;

        currentTab = newTab;

        // Only persist real tab selections
        if (newTab >= 0) CellTerminalClientConfig.getInstance().setSelectedTab(newTab);

        // Notify subnet tab it's being shown
        if (newTab == TabStateManager.TabType.SUBNET_OVERVIEW.getIndex() && subnetTab != null) {
            subnetTab.onEnterOverview();
        }

        listener.onPostSwitch(newTab);

        // Restore scroll position for incoming tab
        if (scrollAccessor != null) {
            TabStateManager.TabType newTabType = TabStateManager.TabType.fromIndex(newTab);
            int savedScroll = TabStateManager.getInstance().getScrollPosition(newTabType);
            scrollAccessor.scrollToLine(savedScroll);
        }
    }

    /**
     * Save the current scroll position for the active tab.
     * Call when the GUI is closing to persist scroll state.
     */
    public void saveCurrentScrollPosition() {
        if (scrollAccessor == null) return;

        TabStateManager.TabType tabType = TabStateManager.TabType.fromIndex(currentTab);
        TabStateManager.getInstance().setScrollPosition(tabType, scrollAccessor.getCurrentScroll());
    }

    // ========================================================================
    // Subnet overview
    // ========================================================================

    /** Get the subnet overview pseudo-tab widget. */
    public SubnetOverviewTabWidget getSubnetTab() {
        return subnetTab;
    }

    /**
     * Get the last real tab that was active before switching to subnet overview.
     * Used by the GUI to return to the previous tab when exiting subnet mode.
     */
    public int getPreviousRealTab() {
        return previousRealTab;
    }

    // ========================================================================
    // Event delegation
    // ========================================================================

    /**
     * Delegate a key press to the active tab widget for tab-specific keybinds.
     *
     * @return true if the key was handled
     */
    public boolean handleKey(int keyCode) {
        AbstractTabWidget tab = getActiveTab();
        if (tab == null) return false;

        return tab.handleTabKeyTyped(keyCode);
    }

    /**
     * Get the effective search mode for the active tab.
     * Some tabs force a specific mode regardless of user selection.
     *
     * @param userSelectedMode The mode selected by the user in the search button
     * @return The effective mode for the current tab
     */
    public SearchFilterMode getEffectiveSearchMode(SearchFilterMode userSelectedMode) {
        AbstractTabWidget tab = getActiveTab();
        if (tab != null) return tab.getEffectiveSearchMode(userSelectedMode);

        return userSelectedMode;
    }

    /**
     * Whether the search mode button should be visible for the active tab.
     * Tabs that force a search mode return false.
     */
    public boolean isSearchModeButtonVisible() {
        AbstractTabWidget tab = getActiveTab();

        return tab != null && tab.showSearchModeButton();
    }

    /**
     * Get the tooltip for a tab button.
     * Includes a disabled notice if the tab is disabled in server config.
     *
     * @param tab The tab index
     * @return The localized tooltip text, or empty string if invalid
     */
    public String getTabTooltip(int tab) {
        if (tabWidgets == null || tab < 0 || tab >= tabWidgets.length || tabWidgets[tab] == null) return "";

        String baseTooltip = tabWidgets[tab].getTabTooltip();

        if (CellTerminalServerConfig.isInitialized() && !CellTerminalServerConfig.getInstance().isTabEnabled(tab)) {
            return baseTooltip + " " + I18n.format("gui.cellterminal.tab.disabled");
        }

        return baseTooltip;
    }

    // ========================================================================
    // Controls help cache
    // ========================================================================

    /**
     * Update the cached controls help wrapped lines.
     * Called by the GUI after rendering the controls help widget each frame.
     */
    public void setCachedControlsHelp(List<String> wrappedLines, int tabIndex) {
        this.cachedControlsHelpLines = wrappedLines;
        this.cachedControlsHelpTab = tabIndex;
    }

    /**
     * Get the cached controls help wrapped lines for JEI exclusion area sizing.
     * Returns empty list if the cache is stale (tab changed since last render).
     */
    public List<String> getCachedControlsHelpLines() {
        if (cachedControlsHelpLines.isEmpty() || cachedControlsHelpTab != currentTab) {
            return Collections.emptyList();
        }

        return cachedControlsHelpLines;
    }

    // ========================================================================
    // Tab rendering
    // ========================================================================

    /**
     * Draw all tab buttons in the background layer.
     * Updates {@link #hoveredTab} based on mouse position.
     */
    public void drawTabs(int guiLeft, int offsetX, int offsetY, int mouseX, int mouseY,
                         RenderItem itemRender, Minecraft mc) {
        TabRenderingHandler.TabRenderContext ctx = new TabRenderingHandler.TabRenderContext(
            guiLeft, offsetX, offsetY, mouseX, mouseY,
            GuiConstants.TAB_WIDTH, GuiConstants.TAB_HEIGHT, GuiConstants.TAB_Y_OFFSET,
            currentTab, itemRender, mc);

        TabRenderingHandler.TabIconProvider iconProvider = new TabRenderingHandler.TabIconProvider() {
            @Override
            public ItemStack getTabIcon(int tab) {
                return TabManager.this.getTabIconInternal(tab);
            }

            @Override
            public ItemStack getStorageBusIcon() {
                if (tabIconStorageBus == null) {
                    tabIconStorageBus = AEApi.instance().definitions().parts().storageBus()
                        .maybeStack(1).orElse(ItemStack.EMPTY);
                }

                return tabIconStorageBus;
            }

            @Override
            public ItemStack getInventoryIcon() {
                if (tabIconInventory == null) {
                    tabIconInventory = AEApi.instance().definitions().blocks().chest()
                        .maybeStack(1).orElse(ItemStack.EMPTY);
                }

                return tabIconInventory;
            }

            @Override
            public ItemStack getPartitionIcon() {
                if (tabIconPartition == null) {
                    tabIconPartition = AEApi.instance().definitions().blocks().cellWorkbench()
                        .maybeStack(1).orElse(ItemStack.EMPTY);
                }

                return tabIconPartition;
            }
        };

        this.hoveredTab = TabRenderingHandler.drawTabs(ctx, iconProvider, getTabCount()).hoveredTab;
    }

    /**
     * Get the tab icon for a specific tab by delegating to the widget.
     */
    private ItemStack getTabIconInternal(int tab) {
        if (tabWidgets != null && tab >= 0 && tab < tabWidgets.length && tabWidgets[tab] != null) {
            return tabWidgets[tab].getTabIcon();
        }

        return ItemStack.EMPTY;
    }
}
