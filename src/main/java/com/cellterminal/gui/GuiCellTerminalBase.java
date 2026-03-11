package com.cellterminal.gui;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import appeng.api.AEApi;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.AppEngSlot;

import mezz.jei.api.gui.IGhostIngredientHandler;

import com.cellterminal.client.CellContentRow;
import com.cellterminal.client.CellInfo;
import com.cellterminal.client.SubnetConnectionRow;
import com.cellterminal.client.SubnetInfo;
import com.cellterminal.client.SubnetVisibility;
import com.cellterminal.client.TabStateManager;
import com.cellterminal.config.CellTerminalClientConfig;
import com.cellterminal.config.CellTerminalClientConfig.TerminalStyle;
import com.cellterminal.container.ContainerCellTerminalBase;
import com.cellterminal.client.KeyBindings;
import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.client.StorageBusContentRow;
import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.client.StorageInfo;
import com.cellterminal.config.CellTerminalServerConfig;
import com.cellterminal.gui.buttons.*;
import com.cellterminal.gui.handler.TabRenderingHandler;
import com.cellterminal.gui.handler.TerminalDataManager;
import com.cellterminal.gui.handler.TooltipHandler;
import com.cellterminal.gui.networktools.INetworkTool;
import com.cellterminal.gui.networktools.GuiToolConfirmationModal;
import com.cellterminal.gui.overlay.MessageHelper;
import com.cellterminal.gui.overlay.OverlayMessageRenderer;
import com.cellterminal.gui.subnet.SubnetOverviewRenderer;
import com.cellterminal.gui.widget.IWidget;
import com.cellterminal.gui.widget.line.SlotsLine;
import com.cellterminal.gui.widget.tab.AbstractTabWidget;
import com.cellterminal.gui.widget.tab.CellContentTabWidget;
import com.cellterminal.gui.widget.tab.NetworkToolsTabWidget;
import com.cellterminal.gui.widget.tab.StorageBusTabWidget;
import com.cellterminal.gui.widget.tab.TempAreaTabWidget;
import com.cellterminal.gui.widget.tab.TerminalTabWidget;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketHighlightBlock;
import com.cellterminal.network.PacketSubnetAction;
import com.cellterminal.network.PacketSwitchNetwork;
import com.cellterminal.network.PacketPartitionAction;
import com.cellterminal.network.PacketRenameAction;
import com.cellterminal.network.PacketSlotLimitChange;
import com.cellterminal.network.PacketStorageBusPartitionAction;
import com.cellterminal.network.PacketTabChange;
import com.cellterminal.network.PacketTempCellPartitionAction;
import com.cellterminal.network.PacketUpgradeCell;
import com.cellterminal.network.PacketUpgradeStorageBus;
import com.cellterminal.gui.rename.InlineRenameEditor;
import com.cellterminal.gui.rename.Renameable;


/**
 * Base GUI for Cell Terminal variants.
 * Contains shared functionality for displaying storage drives/chests with their cells.
 * Supports three tabs: Terminal (list view), Inventory (cell slots with contents), Partition (cell slots with partition).
 */
public abstract class GuiCellTerminalBase extends AEBaseGui implements IJEIGhostIngredients, NetworkToolsTabWidget.NetworkToolGuiContext {

    private static final int TAB_WIDTH = 22;
    private static final int TAB_HEIGHT = 22;
    private static final int TAB_Y_OFFSET = -22;

    // Layout constants
    protected static final int ROW_HEIGHT = GuiConstants.ROW_HEIGHT;
    protected static final int MIN_ROWS = GuiConstants.MIN_ROWS;
    protected static final int DEFAULT_ROWS = GuiConstants.DEFAULT_ROWS;

    // Magic height number for tall mode calculation (header + footer heights)
    private static final int MAGIC_HEIGHT_NUMBER = GuiConstants.MAGIC_HEIGHT_NUMBER;

    // Dynamic row count (computed based on terminal style)
    protected int rowsVisible = DEFAULT_ROWS;

    // Tab widgets (new widget-based architecture)
    protected TerminalTabWidget terminalTabWidget;
    protected CellContentTabWidget inventoryTabWidget;
    protected CellContentTabWidget partitionTabWidget;
    protected TempAreaTabWidget tempAreaTabWidget;
    protected StorageBusTabWidget storageBusInventoryTabWidget;
    protected StorageBusTabWidget storageBusPartitionTabWidget;
    protected NetworkToolsTabWidget networkToolsTabWidget;

    /** Indexed lookup for tab widgets (tabs 0-6). */
    protected AbstractTabWidget[] tabWidgets;

    // Legacy renderers (kept for tabs not yet migrated to widgets)
    protected SubnetOverviewRenderer subnetOverviewRenderer;

    // Handlers
    protected TerminalDataManager dataManager;

    // Terminal style button
    protected GuiTerminalStyleButton terminalStyleButton;

    // Filter panel manager
    protected FilterPanelManager filterPanelManager;
    protected int nextButtonId = 10;  // Starting button ID for filter buttons

    // Search field and mode button
    protected MEGuiTextField searchField;
    protected GuiSearchModeButton searchModeButton;
    protected GuiSearchHelpButton searchHelpButton;
    protected GuiBackButton subnetBackButton;
    protected GuiSubnetVisibilityButton subnetVisibilityButton;
    protected SearchFilterMode currentSearchMode = SearchFilterMode.MIXED;
    protected SubnetVisibility currentSubnetVisibility = SubnetVisibility.DONT_SHOW;

    // Current tab
    protected int currentTab;

    // Tab icons for composite rendering (lazy initialized)
    protected ItemStack tabIconInventory = null;
    protected ItemStack tabIconPartition = null;
    protected ItemStack tabIconStorageBus = null;

    // Popup states
    protected PopupCellInventory inventoryPopup = null;
    protected PopupCellPartition partitionPopup = null;

    // Priority field manager (inline editable fields)
    protected PriorityFieldManager priorityFieldManager = null;

    // Inline rename editor for storage, cell, and storage bus renaming
    protected InlineRenameEditor inlineRenameEditor = null;

    // Tab hover for tooltips
    protected int hoveredTab = -1;

    // Storage bus and temp area selection tracking (for quick-add keybinds)
    protected final Set<Long> selectedStorageBusIds = new HashSet<>();
    protected final Set<Integer> selectedTempCellSlots = new HashSet<>();

    // Modal search bar for expanded editing
    protected GuiModalSearchBar modalSearchBar = null;

    // Guard to prevent style button from being retriggered while mouse is still down
    private long lastStyleButtonClickTime = 0;
    private static final long STYLE_BUTTON_COOLDOWN = 100;  // ms

    protected long lastSearchFieldClickTime = 0;
    protected static final long DOUBLE_CLICK_THRESHOLD = 500;  // ms

    // Whether we've restored the saved scroll after the first data update
    private boolean initialScrollRestored = false;

    // Subnet view state
    protected final List<SubnetInfo> subnetList = new ArrayList<>();
    protected final List<Object> subnetLines = new ArrayList<>();  // Flattened list of SubnetInfo + SubnetConnectionRow
    protected boolean isInSubnetOverviewMode = false;
    protected long currentNetworkId = 0;  // 0 = main network, >0 = subnet ID
    protected int hoveredSubnetEntryIndex = -1;  // Index of hovered subnet in overview mode
    protected long lastSubnetClickTime = 0;  // For double-click detection
    protected long lastSubnetClickId = -1;  // Track which subnet was clicked for double-click
    // When true, we're waiting for a network switch response - ignore incoming data until confirmed
    protected boolean awaitingNetworkSwitch = false;

    public GuiCellTerminalBase(Container container) {
        super(container);

        this.xSize = GuiConstants.GUI_WIDTH;
        this.rowsVisible = calculateRowsCount();
        this.ySize = MAGIC_HEIGHT_NUMBER + this.rowsVisible * ROW_HEIGHT;
        this.setScrollBar(new GuiScrollbar());

        this.dataManager = new TerminalDataManager();
        this.filterPanelManager = new FilterPanelManager();

        // Load persisted settings
        CellTerminalClientConfig config = CellTerminalClientConfig.getInstance();
        this.currentTab = config.getSelectedTab();
        if (this.currentTab < 0 || this.currentTab > GuiConstants.LAST_TAB) this.currentTab = GuiConstants.TAB_TERMINAL;

        // If the persisted tab is disabled, redirect to the first enabled tab
        if (CellTerminalServerConfig.isInitialized() && !CellTerminalServerConfig.getInstance().isTabEnabled(this.currentTab)) {
            this.currentTab = findFirstEnabledTab();
        }
        this.currentSearchMode = config.getSearchMode();
        this.currentSubnetVisibility = config.getSubnetVisibility();

        // Subnet IDs are ephemeral (change between logins), so always start on the main network
        // FIXME: the Id is not restored when using Wireless Terminal
        this.currentNetworkId = 0;
    }

    /**
     * Calculate the number of rows to display based on terminal style and screen height.
     */
    protected int calculateRowsCount() {
        TerminalStyle style = CellTerminalClientConfig.getInstance().getTerminalStyle();

        if (style == TerminalStyle.SMALL) return DEFAULT_ROWS;

        // TALL mode: expand to fill available screen space
        // Use ScaledResolution to properly get the scaled screen height (handles auto GUI scale)
        ScaledResolution res = new ScaledResolution(Minecraft.getMinecraft());
        int screenHeight = res.getScaledHeight();

        // Leave some padding at top and bottom
        int availableHeight = screenHeight - 24;
        int extraSpace = availableHeight - MAGIC_HEIGHT_NUMBER;

        return Math.max(MIN_ROWS, extraSpace / ROW_HEIGHT);
    }

    /**
     * Find the first enabled tab, or fall back to TAB_TERMINAL if all are disabled.
     */
    protected int findFirstEnabledTab() {
        if (!CellTerminalServerConfig.isInitialized()) return GuiConstants.TAB_TERMINAL;

        CellTerminalServerConfig config = CellTerminalServerConfig.getInstance();

        for (int i = 0; i <= GuiConstants.LAST_TAB; i++) {
            if (config.isTabEnabled(i)) return i;
        }

        // Fallback to terminal tab even if disabled (should not happen in practice)
        return GuiConstants.TAB_TERMINAL;
    }

    protected abstract String getGuiTitle();

    @Override
    public void initGui() {
        // Reset button ID counter on each initGui call
        this.nextButtonId = 10;

        // Recalculate rows based on screen size and terminal style
        this.rowsVisible = calculateRowsCount();
        this.ySize = MAGIC_HEIGHT_NUMBER + this.rowsVisible * ROW_HEIGHT;

        super.initGui();

        // Center GUI with appropriate spacing based on terminal style
        TerminalStyle style = CellTerminalClientConfig.getInstance().getTerminalStyle();
        int unusedSpace = this.height - this.ySize;

        if (style == TerminalStyle.SMALL) {
            // Small mode: center vertically with tab space consideration
            int tabSpace = 24;
            this.guiTop = Math.max(tabSpace, (this.height - this.ySize) / 2);
        } else if (unusedSpace < 0) {
            // Tall mode: GUI is larger than screen - push it up so bottom content is more visible
            this.guiTop = (int) Math.floor(unusedSpace / 3.8f);
        } else {
            // Tall mode: GUI fits on screen - position it to extend to the bottom with minimal margin
            int bottomMargin = 4;
            this.guiTop = this.height - this.ySize - bottomMargin;

            // Ensure there's enough space at the top for tabs (TAB_HEIGHT = 22, plus buffer)
            int tabSpace = 24;
            if (this.guiTop < tabSpace) this.guiTop = tabSpace;
        }

        this.getScrollBar().setTop(18).setLeft(189).setHeight(this.rowsVisible * ROW_HEIGHT - 2);
        this.repositionSlots();
        initTabWidgets();
        initTerminalStyleButton();
        initSubnetBackButton();
        initFilterButtons();
        initSearchField();
        initPriorityFieldManager();

        // Apply persisted filter states to data manager
        applyFiltersToDataManager();

        // Update scrollbar range for current tab
        updateScrollbarForCurrentTab();

        // Restore saved scroll position for the current tab (in-memory TabStateManager)
        TabStateManager.TabType initialTabType = TabStateManager.TabType.fromIndex(this.currentTab);
        int initialSavedScroll = TabStateManager.getInstance().getScrollPosition(initialTabType);
        scrollToLine(initialSavedScroll);

        // We'll also re-apply the saved scroll once when data arrives, in case
        // the initial scrollbar range was limited during initGui().
        this.initialScrollRestored = false;

        // Notify server of the current tab so it can start sending appropriate data
        // This is especially important for storage bus tabs which require server polling
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketTabChange(currentTab));

        // Send current slot limit preferences to server
        CellTerminalClientConfig config = CellTerminalClientConfig.getInstance();
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketSlotLimitChange(
            config.getCellSlotLimit().getLimit(),
            config.getBusSlotLimit().getLimit()
        ));

        // If a subnet was previously being viewed, tell the server to switch to it
        // Also reset data manager to avoid showing stale data from a previous session
        if (this.currentNetworkId != 0) {
            this.awaitingNetworkSwitch = true;
            this.dataManager.resetForNetworkSwitch();
            CellTerminalNetwork.INSTANCE.sendToServer(new PacketSwitchNetwork(this.currentNetworkId));
        }
    }

    protected void initPriorityFieldManager() {
        this.priorityFieldManager = new PriorityFieldManager(this.fontRenderer);
    }

    protected void initTabWidgets() {
        // Legacy renderers (not yet converted to widgets)
        this.subnetOverviewRenderer = new SubnetOverviewRenderer(this.fontRenderer, this.itemRender);
        this.inlineRenameEditor = new InlineRenameEditor();

        // Create tab widgets
        this.terminalTabWidget = new TerminalTabWidget(this.fontRenderer, this.itemRender);
        this.inventoryTabWidget = new CellContentTabWidget(SlotsLine.SlotMode.CONTENT, this.fontRenderer, this.itemRender);
        this.partitionTabWidget = new CellContentTabWidget(SlotsLine.SlotMode.PARTITION, this.fontRenderer, this.itemRender);
        this.tempAreaTabWidget = new TempAreaTabWidget(this.fontRenderer, this.itemRender);
        this.storageBusInventoryTabWidget = new StorageBusTabWidget(SlotsLine.SlotMode.CONTENT, this.fontRenderer, this.itemRender);
        this.storageBusPartitionTabWidget = new StorageBusTabWidget(SlotsLine.SlotMode.PARTITION, this.fontRenderer, this.itemRender);
        this.networkToolsTabWidget = new NetworkToolsTabWidget(this.fontRenderer, this.itemRender);

        // Populate indexed lookup
        this.tabWidgets = new AbstractTabWidget[] {
            terminalTabWidget,              // TAB_TERMINAL (0)
            inventoryTabWidget,             // TAB_INVENTORY (1)
            partitionTabWidget,             // TAB_PARTITION (2)
            tempAreaTabWidget,              // TAB_TEMP_AREA (3)
            storageBusInventoryTabWidget,   // TAB_STORAGE_BUS_INVENTORY (4)
            storageBusPartitionTabWidget,   // TAB_STORAGE_BUS_PARTITION (5)
            networkToolsTabWidget           // TAB_NETWORK_TOOLS (6)
        };

        // Initialize all widgets with shared GUI context, offsets, and row count
        for (AbstractTabWidget widget : tabWidgets) {
            if (widget == null) continue;
            widget.setGuiOffsets(this.guiLeft, this.guiTop);
            widget.setRowsVisible(this.rowsVisible);
            widget.init(this);
        }
    }

    /**
     * Get the active tab widget for the current tab.
     */
    protected AbstractTabWidget getActiveTabWidget() {
        if (currentTab < 0 || currentTab >= tabWidgets.length) return null;

        return tabWidgets[currentTab];
    }

    /**
     * Get the lines data list from the data manager for the active tab widget.
     * Delegates to the widget's {@link AbstractTabWidget#getLines(TerminalDataManager)} method.
     */
    protected List<Object> getWidgetLines() {
        AbstractTabWidget activeWidget = getActiveTabWidget();
        if (activeWidget == null) return Collections.emptyList();

        return activeWidget.getLines(dataManager);
    }

    protected void initSubnetBackButton() {
        if (this.subnetBackButton != null) this.buttonList.remove(this.subnetBackButton);

        // Position the back button at the left side of the header, before the title
        int buttonX = this.guiLeft + 4;
        int buttonY = this.guiTop + 4;
        this.subnetBackButton = new GuiBackButton(3, buttonX, buttonY);
        this.subnetBackButton.setInOverviewMode(this.isInSubnetOverviewMode);
        this.buttonList.add(this.subnetBackButton);
    }

    protected void initTerminalStyleButton() {
        if (this.terminalStyleButton != null) this.buttonList.remove(this.terminalStyleButton);

        // Calculate button Y like in SMALL mode (for consistent positioning across style changes)
        int smallModeYSize = MAGIC_HEIGHT_NUMBER + DEFAULT_ROWS * ROW_HEIGHT;
        int buttonY = Math.max(8, (this.height - smallModeYSize) / 2 + 8);
        this.terminalStyleButton = new GuiTerminalStyleButton(0, this.guiLeft - 18, buttonY, CellTerminalClientConfig.getInstance().getTerminalStyle());
        this.buttonList.add(this.terminalStyleButton);
    }

    protected void initFilterButtons() {
        nextButtonId = filterPanelManager.initButtons(this.buttonList, nextButtonId, currentTab);
        updateFilterButtonPositions();
    }

    protected void updateFilterButtonPositions() {
        int styleButtonY = (this.terminalStyleButton != null) ? this.terminalStyleButton.y : this.guiTop + 8;
        int styleButtonBottom = styleButtonY + 16;  // BUTTON_SIZE = 16
        Rectangle controlsHelpBounds = getControlsHelpBounds();
        filterPanelManager.updatePositions(this.guiLeft, this.guiTop, this.ySize, styleButtonY, styleButtonBottom, controlsHelpBounds);
    }

    protected void applyFiltersToDataManager() {
        dataManager.updateFiltersQuiet(filterPanelManager.getAllFilterStates());
    }

    // TODO: delegate more to the search field itself
    protected void initSearchField() {
        // Search help button: positioned at the start of the search area
        int titleWidth = this.fontRenderer.getStringWidth(getGuiTitle());
        int helpButtonX = 22 + titleWidth + 4;
        int helpButtonY = 5;

        if (this.searchHelpButton != null) this.buttonList.remove(this.searchHelpButton);
        this.searchHelpButton = new GuiSearchHelpButton(2, this.guiLeft + helpButtonX, this.guiTop + helpButtonY);
        this.buttonList.add(this.searchHelpButton);

        // Search field: positioned after help button
        int searchX = helpButtonX + GuiSearchHelpButton.SIZE + 2;
        int searchY = 4;
        int availableWidth = 189 - searchX;

        String existingSearch = (this.searchField != null) ? this.searchField.getText() : CellTerminalClientConfig.getInstance().getSearchFilter();

        this.searchField = new MEGuiTextField(this.fontRenderer, this.guiLeft + searchX, this.guiTop + searchY, availableWidth, 12) {
            @Override
            public void onTextChange(String oldText) {
                onSearchTextChanged();
            }
        };
        this.searchField.setEnableBackgroundDrawing(true);
        this.searchField.setMaxStringLength(512);
        this.searchField.setText(existingSearch, true);

        if (this.searchModeButton != null) this.buttonList.remove(this.searchModeButton);

        // Search mode button: positioned above the scrollbar (top-right corner)
        this.searchModeButton = new GuiSearchModeButton(1, this.guiLeft + 189, this.guiTop + 5, currentSearchMode);
        this.buttonList.add(this.searchModeButton);
        updateSearchModeButtonVisibility();

        // Subnet visibility button: positioned next to search mode button
        // if (this.subnetVisibilityButton != null) this.buttonList.remove(this.subnetVisibilityButton);
        // FIXME: enable when it's working
        // this.subnetVisibilityButton = new GuiSubnetVisibilityButton(4, this.guiLeft + 189 - 14, this.guiTop + 4, currentSubnetVisibility);
        // this.buttonList.add(this.subnetVisibilityButton);

        // Initialize modal search bar
        this.modalSearchBar = new GuiModalSearchBar(this.fontRenderer, this.searchField, this::onSearchTextChanged);

        if (!existingSearch.isEmpty()) dataManager.setSearchFilter(existingSearch, getEffectiveSearchMode());
    }

    protected void updateSearchModeButtonVisibility() {
        if (this.searchModeButton == null) return;

        AbstractTabWidget activeWidget = getActiveTabWidget();
        this.searchModeButton.visible = (activeWidget != null && activeWidget.showSearchModeButton());
    }

    protected void onSearchTextChanged() {
        SearchFilterMode effectiveMode = getEffectiveSearchMode();
        dataManager.setSearchFilter(searchField.getText(), effectiveMode);
        updateScrollbarForCurrentTab();

        // Persist the search filter text
        CellTerminalClientConfig.getInstance().setSearchFilter(searchField.getText());
    }

    /**
     * Get the effective search mode based on current tab.
     * Delegates to the active tab widget.
     */
    protected SearchFilterMode getEffectiveSearchMode() {
        AbstractTabWidget activeWidget = getActiveTabWidget();
        if (activeWidget != null) return activeWidget.getEffectiveSearchMode(currentSearchMode);

        return currentSearchMode;
    }

    protected void repositionSlots() {
        for (Object obj : this.inventorySlots.inventorySlots) {
            if (obj instanceof AppEngSlot) {
                AppEngSlot slot = (AppEngSlot) obj;
                slot.yPos = this.ySize + slot.getY() - 82;
                slot.xPos = slot.getX() + 14;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw popups on top (including their JEI ghost targets)
        if (inventoryPopup != null) {
            inventoryPopup.draw(mouseX, mouseY);
            inventoryPopup.drawTooltip(mouseX, mouseY);
        }

        if (partitionPopup != null) {
            partitionPopup.draw(mouseX, mouseY);
            partitionPopup.drawTooltip(mouseX, mouseY);
        }

        // Draw hover preview from terminal tab widget
        if (currentTab == GuiConstants.TAB_TERMINAL && inventoryPopup == null && partitionPopup == null) {
            CellInfo previewCell = terminalTabWidget.getPreviewCell();
            int previewType = terminalTabWidget.getPreviewType();

            if (previewCell != null) {
                int previewX = mouseX + 10, previewY = mouseY + 10;
                if (previewType == 1) new PopupCellInventory(this, previewCell, previewX, previewY).draw(mouseX, mouseY);
                else if (previewType == 2) new PopupCellPartition(this, previewCell, previewX, previewY).draw(mouseX, mouseY);
                else if (previewType == 3) this.drawHoveringText(Collections.singletonList(I18n.format("gui.cellterminal.eject_cell")), mouseX, mouseY);
            }
        }

        drawTooltips(mouseX, mouseY);

        // Draw subnet overview tooltips
        if (this.isInSubnetOverviewMode && this.hoveredSubnetEntryIndex >= 0
                && this.hoveredSubnetEntryIndex < this.subnetLines.size()) {
            Object line = this.subnetLines.get(this.hoveredSubnetEntryIndex);

            // Handle filter item tooltips separately (for SubnetConnectionRow)
            if (line instanceof SubnetConnectionRow) {
                SubnetOverviewRenderer.HoverZone zone = this.subnetOverviewRenderer.getHoveredZone();
                if (zone == SubnetOverviewRenderer.HoverZone.FILTER_SLOT) {
                    ItemStack filterStack = this.subnetOverviewRenderer.getHoveredFilterStack();
                    if (!filterStack.isEmpty()) {
                        this.renderToolTip(filterStack, mouseX, mouseY);

                        // Skip regular tooltip
                        line = null;
                    }
                }
            }

            if (line != null) {
                List<String> tooltip = this.subnetOverviewRenderer.getTooltip(line);
                if (!tooltip.isEmpty()) this.drawHoveringText(tooltip, mouseX, mouseY);
            }
        }

        // Draw back button tooltip
        if (this.subnetBackButton != null && this.subnetBackButton.isMouseOver()) {
            List<String> tooltip = this.subnetBackButton.getTooltip();
            if (!tooltip.isEmpty()) this.drawHoveringText(tooltip, mouseX, mouseY);
        }

        // Render modal search bar on top of everything else
        if (modalSearchBar != null && modalSearchBar.isVisible()) modalSearchBar.draw(mouseX, mouseY);

        // Render network tool confirmation modal on top of everything else
        if (networkToolModal != null) networkToolModal.draw(mouseX, mouseY);

        // Render overlay messages last (on top of everything)
        OverlayMessageRenderer.render();
    }

    protected void drawTooltips(int mouseX, int mouseY) {
        int relMouseX = mouseX - guiLeft;
        int relMouseY = mouseY - guiTop;

        // Widget-based tooltips for tabs 0-5
        AbstractTabWidget activeWidget = getActiveTabWidget();
        if (activeWidget != null) {
            // Get tooltip from hovered widget row
            List<String> widgetTooltip = activeWidget.getTooltip(relMouseX, relMouseY);
            if (!widgetTooltip.isEmpty()) {
                this.drawHoveringText(widgetTooltip, mouseX, mouseY);

                return;
            }

            // Get hovered item stack for vanilla item tooltip
            ItemStack hoveredStack = activeWidget.getHoveredItemStack(relMouseX, relMouseY);
            if (!hoveredStack.isEmpty()) {
                this.renderToolTip(hoveredStack, mouseX, mouseY);

                return;
            }
        }

        // Legacy tooltip context for non-widget elements (buttons, search field, network tools)
        TooltipHandler.TooltipContext ctx = new TooltipHandler.TooltipContext();
        ctx.currentTab = currentTab;
        ctx.hoveredTab = hoveredTab;
        ctx.inventoryPopup = inventoryPopup;
        ctx.partitionPopup = partitionPopup;
        ctx.terminalStyleButton = terminalStyleButton;
        ctx.searchModeButton = searchModeButton;
        ctx.searchHelpButton = searchHelpButton;
        // ctx.subnetVisibilityButton = subnetVisibilityButton;
        ctx.priorityFieldManager = priorityFieldManager;
        ctx.filterPanelManager = filterPanelManager;

        // Search error state
        ctx.hasSearchError = dataManager.hasAdvancedSearchError();
        ctx.searchErrorMessage = dataManager.getAdvancedSearchError();
        if (searchField != null) {
            ctx.searchFieldX = searchField.x - 2;
            ctx.searchFieldY = searchField.y - 2;
            ctx.searchFieldWidth = searchField.width + 4;
            ctx.searchFieldHeight = searchField.height + 4;
        }

        // Tab widgets for tooltip delegation
        ctx.tabWidgets = tabWidgets;

        TooltipHandler.drawTooltips(ctx, new TooltipHandler.TooltipRenderer() {
            @Override
            public void drawHoveringText(List<String> lines, int x, int y) {
                GuiCellTerminalBase.this.drawHoveringText(lines, x, y);
            }

            @Override
            public List<String> getItemToolTip(ItemStack stack) {
                return GuiCellTerminalBase.this.getItemToolTip(stack);
            }
        }, mouseX, mouseY);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(getGuiTitle(), 22, 6, 0x404040);
        this.fontRenderer.drawString(I18n.format("container.inventory"), 22, this.ySize - 93, 0x404040);

        // Draw search field - translate back since field uses absolute coords but we're in translated context
        if (this.searchField != null) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(-this.guiLeft, -this.guiTop, 0);

            // Draw red border if there's a parse error
            if (dataManager.hasAdvancedSearchError()) {
                int fx = this.searchField.x - 3;
                int fy = this.searchField.y - 3;
                int fw = this.searchField.width + 6;
                int fh = this.searchField.height + 6;
                drawRect(fx, fy, fx + fw, fy + fh, 0xFFFF0000);
            }

            this.searchField.drawTextBox();
            GlStateManager.popMatrix();
        }

        int relMouseX = mouseX - offsetX;
        int relMouseY = mouseY - offsetY;
        final int currentScroll = this.getScrollBar().getCurrentScroll();

        // Reset priority field visibility before rendering
        if (priorityFieldManager != null) priorityFieldManager.resetVisibility();

        // Subnet overview mode takes over the content area (legacy renderer)
        if (this.isInSubnetOverviewMode) {
            drawSubnetOverviewContent(relMouseX, relMouseY, currentScroll);
            drawControlsHelpForCurrentTab();

            return;
        }

        // Draw based on current tab using widgets
        AbstractTabWidget activeWidget = getActiveTabWidget();
        if (activeWidget != null) {
            activeWidget.buildVisibleRows(getWidgetLines(), currentScroll);
            activeWidget.draw(relMouseX, relMouseY);
        }

        // Draw inline rename field overlay if editing
        if (inlineRenameEditor != null && inlineRenameEditor.isEditing()) {
            inlineRenameEditor.drawRenameField(this.fontRenderer);
        }

        // Update priority field positions from widget visible rows
        if (priorityFieldManager != null && activeWidget != null) {
            for (Map.Entry<IWidget, Object> entry : activeWidget.getWidgetDataMap().entrySet()) {
                Object data = entry.getValue();
                IWidget widget = entry.getKey();

                if (data instanceof StorageInfo) {
                    StorageInfo storage = (StorageInfo) data;
                    if (storage.supportsPriority()) {
                        PriorityFieldManager.PriorityField field = priorityFieldManager.getOrCreateField(
                            storage, guiLeft, guiTop);
                        priorityFieldManager.updateFieldPosition(field, widget.getY(), guiLeft, guiTop);
                    }
                } else if (data instanceof StorageBusInfo) {
                    StorageBusInfo bus = (StorageBusInfo) data;
                    if (bus.supportsPriority()) {
                        PriorityFieldManager.StorageBusPriorityField field = priorityFieldManager.getOrCreateStorageBusField(
                            bus, guiLeft, guiTop);
                        priorityFieldManager.updateStorageBusFieldPosition(field, widget.getY(), guiLeft, guiTop);
                    }
                }
            }

            // Draw priority fields (in GUI-relative context)
            priorityFieldManager.drawFieldsRelative(guiLeft, guiTop);

            // Cleanup stale fields
            priorityFieldManager.cleanupStaleFields(dataManager.getStorageMap());
            priorityFieldManager.cleanupStaleStorageBusFields(dataManager.getStorageBusMap());
        }

        // Draw controls help - delegate to tab controller
        drawControlsHelpForCurrentTab();
    }

    // Constants for controls help widget positioning
    // JEI buttons are at guiLeft - 18, with ~4px margin from screen edge
    // We position the panel to leave similar margins on both sides
    protected static final int CONTROLS_HELP_RIGHT_MARGIN = 4;  // Gap between panel and GUI
    protected static final int CONTROLS_HELP_LEFT_MARGIN = 4;  // Gap between screen edge and panel
    protected static final int CONTROLS_HELP_PADDING = 4;  // Inner padding
    protected static final int CONTROLS_HELP_LINE_HEIGHT = 10;  // Height per line

    // Cached wrapped lines for controls help (used for both rendering and exclusion area)
    protected List<String> cachedControlsHelpLines = new ArrayList<>();
    protected int cachedControlsHelpTab = -1;

    /**
     * Draw controls help for the current tab using the handler.
     */
    protected void drawControlsHelpForCurrentTab() {
        TerminalStyle style = CellTerminalClientConfig.getInstance().getTerminalStyle();

        // In subnet overview mode, show subnet-specific controls
        if (this.isInSubnetOverviewMode) {
            drawSubnetControlsHelp(style);
            updateFilterButtonPositions();
            return;
        }

        TabRenderingHandler.ControlsHelpContext ctx = new TabRenderingHandler.ControlsHelpContext(
            this.guiLeft, this.guiTop, this.ySize, this.height, currentTab, this.fontRenderer, style);

        // Get help lines from the active tab widget
        AbstractTabWidget activeWidget = getActiveTabWidget();
        List<String> helpLines = activeWidget != null ? activeWidget.getHelpLines() : Collections.emptyList();

        TabRenderingHandler.ControlsHelpResult result = TabRenderingHandler.drawControlsHelpWidget(ctx, helpLines);
        this.cachedControlsHelpLines = result.wrappedLines;
        this.cachedControlsHelpTab = result.cachedTab;

        // Update filter button positions after controls help bounds are known
        updateFilterButtonPositions();
    }

    /**
     * Draw controls help widget for subnet overview mode.
     */
    protected void drawSubnetControlsHelp(TerminalStyle style) {
        List<String> lines = new ArrayList<>();
        lines.add(I18n.format("cellterminal.subnet.controls.title"));
        lines.add("");
        lines.add(I18n.format("cellterminal.subnet.controls.click"));
        lines.add(I18n.format("cellterminal.subnet.controls.dblclick"));
        lines.add(I18n.format("cellterminal.subnet.controls.star"));
        lines.add(I18n.format("cellterminal.subnet.controls.rename"));
        lines.add(I18n.format("cellterminal.subnet.controls.esc"));

        // Calculate panel width
        int panelWidth = this.guiLeft - CONTROLS_HELP_RIGHT_MARGIN - CONTROLS_HELP_LEFT_MARGIN;
        if (panelWidth < 60) panelWidth = 60;
        int textWidth = panelWidth - (CONTROLS_HELP_PADDING * 2);

        // Wrap all lines
        List<String> wrappedLines = new ArrayList<>();
        for (String line : lines) {
            if (line.isEmpty()) {
                wrappedLines.add("");
            } else {
                wrappedLines.addAll(this.fontRenderer.listFormattedStringToWidth(line, textWidth));
            }
        }

        // Calculate positions
        int panelRight = -CONTROLS_HELP_RIGHT_MARGIN;
        int panelLeft = -this.guiLeft + CONTROLS_HELP_LEFT_MARGIN;
        int contentHeight = wrappedLines.size() * CONTROLS_HELP_LINE_HEIGHT;
        int panelHeight = contentHeight + (CONTROLS_HELP_PADDING * 2);

        // Position relative to screen bottom
        int bottomOffset = 28;
        int panelBottom = this.height - this.guiTop - bottomOffset;
        int panelTop = panelBottom - panelHeight;

        // Draw AE2-style panel background
        Gui.drawRect(panelLeft, panelTop, panelRight, panelBottom, 0xC0000000);

        // Border
        Gui.drawRect(panelLeft, panelTop, panelRight, panelTop + 1, 0xFF606060);
        Gui.drawRect(panelLeft, panelTop, panelLeft + 1, panelBottom, 0xFF606060);
        Gui.drawRect(panelLeft, panelBottom - 1, panelRight, panelBottom, 0xFF303030);
        Gui.drawRect(panelRight - 1, panelTop, panelRight, panelBottom, 0xFF303030);

        // Draw text
        int textX = panelLeft + CONTROLS_HELP_PADDING;
        int textY = panelTop + CONTROLS_HELP_PADDING;
        for (int i = 0; i < wrappedLines.size(); i++) {
            String line = wrappedLines.get(i);
            if (!line.isEmpty()) {
                this.fontRenderer.drawString(line, textX, textY + (i * CONTROLS_HELP_LINE_HEIGHT), 0xCCCCCC);
            }
        }

        this.cachedControlsHelpLines = wrappedLines;
        this.cachedControlsHelpTab = -1;  // Mark as subnet mode
    }

    /**
     * Draw the subnet overview content.
     * Shows a list of connected subnets with their info and connection details.
     */
    protected void drawSubnetOverviewContent(int relMouseX, int relMouseY, int currentScroll) {
        if (this.subnetOverviewRenderer == null) return;

        // Update scrollbar for subnet lines (headers + connection rows)
        int totalLines = this.subnetLines.size();
        int maxScroll = Math.max(0, totalLines - this.rowsVisible);
        this.getScrollBar().setRange(0, maxScroll, 1);

        // Draw the subnet list with connection rows
        this.hoveredSubnetEntryIndex = this.subnetOverviewRenderer.draw(
            this.subnetLines,
            currentScroll,
            this.rowsVisible,
            relMouseX,
            relMouseY,
            this.guiLeft,
            this.guiTop
        );

        // Draw rename field overlay if editing
        if (this.subnetOverviewRenderer.isEditing()) this.subnetOverviewRenderer.drawRenameField();
    }

    /**
     * Get the bounding rectangle for the controls help widget.
     * Uses cached wrapped lines from the last render for accurate sizing.
     * Used for JEI exclusion areas.
     */
    protected Rectangle getControlsHelpBounds() {
        // Use cached lines if available
        // For subnet overview mode, cachedControlsHelpTab is -1 and we're not in a tab, so check isInSubnetOverviewMode
        boolean isCacheValid = !cachedControlsHelpLines.isEmpty()
            && (cachedControlsHelpTab == currentTab || (cachedControlsHelpTab == -1 && isInSubnetOverviewMode));
        if (!isCacheValid) return new Rectangle(0, 0, 0, 0);

        int lineCount = cachedControlsHelpLines.size();
        int contentHeight = lineCount * CONTROLS_HELP_LINE_HEIGHT;
        int panelHeight = contentHeight + (CONTROLS_HELP_PADDING * 2);

        // Calculate panel width and position (same logic as in drawControlsHelpWidget)
        int panelWidth = this.guiLeft - CONTROLS_HELP_RIGHT_MARGIN - CONTROLS_HELP_LEFT_MARGIN;
        if (panelWidth < 60) panelWidth = 60;

        int panelLeft = -this.guiLeft + CONTROLS_HELP_LEFT_MARGIN;

        // Position relative to screen bottom
        // Leave margin for JEI bookmarks button at screen bottom
        int bottomOffset = 28;
        int panelBottom = this.height - this.guiTop - bottomOffset;
        int panelTop = panelBottom - panelHeight;

        return new Rectangle(
            this.guiLeft + panelLeft,
            this.guiTop + panelTop,
            panelWidth,
            panelHeight
        );
    }

    /**
     * Get JEI exclusion areas to prevent overlap with controls help widget and filter buttons.
     */
    @Override
    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> areas = new ArrayList<>();
        Rectangle controlsHelp = getControlsHelpBounds();

        if (controlsHelp.width > 0) areas.add(controlsHelp);

        // Add filter panel bounds
        Rectangle filterBounds = filterPanelManager.getBounds();
        if (filterBounds.width > 0) areas.add(filterBounds);

        // Add terminal style button bounds
        if (this.terminalStyleButton != null && this.terminalStyleButton.visible) {
            areas.add(new Rectangle(
                this.terminalStyleButton.x,
                this.terminalStyleButton.y,
                this.terminalStyleButton.width,
                this.terminalStyleButton.height
            ));
        }

        if (hasToolbox()) {
            areas.add(new Rectangle(
                this.guiLeft + this.xSize + 1, this.guiTop + this.ySize - 90, 68, 68
            ));
        }

        return areas;
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();

        this.bindTexture("guis/newinterfaceterminal.png");

        // Draw top section
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, 18);

        // Draw middle section (repeated rows)
        for (int i = 0; i < this.rowsVisible; i++) {
            this.drawTexturedModalRect(offsetX, offsetY + 18 + i * ROW_HEIGHT, 0, 52, this.xSize, ROW_HEIGHT);
        }

        // Draw upper border for the main area (as the texture has a gap)
        drawRect(offsetX + 21, offsetY + 17, offsetX + 182, offsetY + 18, 0xFF373737);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Draw bottom section
        int bottomY = offsetY + 18 + this.rowsVisible * ROW_HEIGHT;
        this.drawTexturedModalRect(offsetX, bottomY, 0, 158, this.xSize, 98);

        drawTabs(offsetX, offsetY, mouseX, mouseY);
        this.bindTexture("guis/bus.png");
        if (this.hasToolbox()) {
            this.drawTexturedModalRect(offsetX + this.xSize + 1, offsetY + this.ySize - 90, 178, 184 - 90, 68, 68);
        }
    }

    public boolean hasToolbox() {
        return ((ContainerCellTerminalBase) this.inventorySlots).hasToolbox();
    }

    protected void drawTabs(int offsetX, int offsetY, int mouseX, int mouseY) {
        TabRenderingHandler.TabRenderContext ctx = new TabRenderingHandler.TabRenderContext(
            this.guiLeft, offsetX, offsetY, mouseX, mouseY,
            TAB_WIDTH, TAB_HEIGHT, TAB_Y_OFFSET,
            currentTab, this.itemRender, this.mc);

        TabRenderingHandler.TabIconProvider iconProvider = new TabRenderingHandler.TabIconProvider() {
            @Override
            public ItemStack getTabIcon(int tab) {
                return GuiCellTerminalBase.this.getTabIcon(tab);
            }

            // Icons used for composite tab rendering
            // TODO: get proper icons for composite tabs

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

        this.hoveredTab = TabRenderingHandler.drawTabs(ctx, iconProvider, tabWidgets.length).hoveredTab;
    }

    protected ItemStack getTabIcon(int tab) {
        // Delegate to tab widget if available
        if (tab >= 0 && tab < tabWidgets.length && tabWidgets[tab] != null) {
            return tabWidgets[tab].getTabIcon();
        }

        return ItemStack.EMPTY;
    }

    @Override
    protected void handleMouseClick(Slot slot, int slotIdx, int mouseButton, ClickType clickType) {
        // Intercept shift-clicks on upgrade items in player inventory to insert into
        // the first visible cell/bus. This is a cross-cutting concern at the container slot level,
        // not handled by the tab widget's content area click path (which handles held-item clicks).
        if (clickType == ClickType.QUICK_MOVE && slot != null && slot.getHasStack()) {
            ItemStack slotStack = slot.getStack();

            // TODO: move logic to the tab widgets

            if (slotStack.getItem() instanceof IUpgradeModule) {
                // Check if upgrade insertion is enabled
                if (CellTerminalServerConfig.isInitialized()
                        && !CellTerminalServerConfig.getInstance().isUpgradeInsertEnabled()) {
                    MessageHelper.error("cellterminal.error.upgrade_insert_disabled");

                    return;
                }

                // On storage bus tabs, try to insert into storage buses
                if (currentTab == GuiConstants.TAB_STORAGE_BUS_INVENTORY || currentTab == GuiConstants.TAB_STORAGE_BUS_PARTITION) {
                    StorageBusInfo targetBus = findFirstVisibleStorageBusThatCanAcceptUpgrade(slotStack);

                    if (targetBus != null) {
                        CellTerminalNetwork.INSTANCE.sendToServer(new PacketUpgradeStorageBus(
                            targetBus.getId(),
                            true,
                            slot.getSlotIndex()
                        ));

                        return;
                    }
                } else {
                    // On cell tabs, try to insert into cells
                    CellInfo targetCell = findFirstVisibleCellThatCanAcceptUpgrade(slotStack);

                    if (targetCell != null) {
                        StorageInfo storage = dataManager.getStorageMap().get(targetCell.getParentStorageId());

                        if (storage != null) {
                            // Pass the slot index so server knows where to take the upgrade from
                            CellTerminalNetwork.INSTANCE.sendToServer(new PacketUpgradeCell(
                                storage.getId(),
                                targetCell.getSlot(),
                                true,
                                slot.getSlotIndex()
                            ));

                            return;
                        }
                    }
                }
            }
        }

        super.handleMouseClick(slot, slotIdx, mouseButton, clickType);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // Handle modal search bar clicks first
        if (modalSearchBar != null && modalSearchBar.isVisible()) {
            if (modalSearchBar.handleMouseClick(mouseX, mouseY, mouseButton)) return;
        }

        // Handle network tool confirmation modal first (blocks all other clicks)
        if (networkToolModal != null) {
            if (networkToolModal.handleClick(mouseX, mouseY, mouseButton)) return;

            // Click outside modal cancels it
            networkToolModal = null;

            return;
        }

        // Handle rename field - click outside saves and closes
        if (this.subnetOverviewRenderer != null && this.subnetOverviewRenderer.isEditing()) {
            SubnetOverviewRenderer.HoverZone zone = this.subnetOverviewRenderer.getHoveredZone();
            SubnetInfo hoveredSubnet = this.subnetOverviewRenderer.getHoveredSubnet();
            SubnetInfo editingSubnet = this.subnetOverviewRenderer.getEditingSubnet();

            // If clicking on a different zone or different subnet, save and close
            boolean isClickOnEditedName = zone == SubnetOverviewRenderer.HoverZone.NAME
                && hoveredSubnet != null
                && editingSubnet != null
                && hoveredSubnet.getId() == editingSubnet.getId();

            if (!isClickOnEditedName) {
                // Save the rename
                SubnetInfo subnet = this.subnetOverviewRenderer.getEditingSubnet();
                String newName = this.subnetOverviewRenderer.stopEditing();
                if (newName != null && subnet != null) sendSubnetRenamePacket(subnet, newName);
                // Continue processing the click
            }
        }

        // Handle inline rename editor - click outside saves and closes
        if (this.inlineRenameEditor != null && this.inlineRenameEditor.isEditing()) {
            Renameable editingTarget = this.inlineRenameEditor.getEditingTarget();
            Renameable hoveredTarget = getHoveredRenameable(mouseX - guiLeft, mouseY - guiTop);
            boolean isClickOnEditedTarget = hoveredTarget != null
                && editingTarget != null
                && hoveredTarget.getRenameTargetType() == editingTarget.getRenameTargetType()
                && hoveredTarget.getRenameId() == editingTarget.getRenameId()
                && hoveredTarget.getRenameSecondaryId() == editingTarget.getRenameSecondaryId();

            if (!isClickOnEditedTarget) {
                Renameable target = this.inlineRenameEditor.getEditingTarget();
                String newName = this.inlineRenameEditor.stopEditing();
                if (newName != null && target != null) sendRenamePacket(target, newName);
                // Continue processing the click
            }
        }

        // Handle subnet overview mode clicks
        if (this.isInSubnetOverviewMode && this.hoveredSubnetEntryIndex >= 0) {
            if (this.hoveredSubnetEntryIndex < this.subnetLines.size()) {
                Object line = this.subnetLines.get(this.hoveredSubnetEntryIndex);
                SubnetOverviewRenderer.HoverZone zone = this.subnetOverviewRenderer.getHoveredZone();

                // Get the subnet from either the header or connection row
                SubnetInfo subnet = null;
                if (line instanceof SubnetInfo) {
                    subnet = (SubnetInfo) line;
                } else if (line instanceof SubnetConnectionRow) {
                    subnet = ((SubnetConnectionRow) line).getSubnet();
                }

                if (subnet != null) {
                    handleSubnetEntryClick(subnet, zone, mouseButton);

                    return;
                }
            }
        }

        // Handle search field clicks
        if (this.searchField != null) {
            // Right-click clears the field and focuses it
            if (mouseButton == 1 && this.searchField.isMouseIn(mouseX, mouseY)) {
                this.searchField.setText("");
                this.searchField.setFocused(true);

                return;
            }

            // Left-click: check for double-click to open modal
            if (mouseButton == 0 && this.searchField.isMouseIn(mouseX, mouseY)) {
                long currentTime = System.currentTimeMillis();

                if (currentTime - lastSearchFieldClickTime < DOUBLE_CLICK_THRESHOLD) {
                    // Double-click: open modal search bar
                    if (modalSearchBar != null) modalSearchBar.open(this.searchField.y);
                    lastSearchFieldClickTime = 0;

                    return;
                }

                lastSearchFieldClickTime = currentTime;
            }

            this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        if (priorityFieldManager != null && priorityFieldManager.handleClick(mouseX, mouseY, mouseButton)) return;

        // Handle upgrade insertion (player holding upgrade + left-click on cell/bus)
        if (mouseButton == 0 && handleWidgetUpgradeClick(mouseX, mouseY)) return;

        if (inventoryPopup != null) {
            if (inventoryPopup.handleClick(mouseX, mouseY, mouseButton)) return;

            // Click outside popup closes it
            inventoryPopup = null;

            return;
        }

        if (partitionPopup != null) {
            if (partitionPopup.handleClick(mouseX, mouseY, mouseButton)) return;

            // Click outside popup closes it
            partitionPopup = null;

            return;
        }

        // Right-click on a renameable target starts inline rename
        if (mouseButton == 1 && !isInSubnetOverviewMode) {
            int relMouseX = mouseX - guiLeft;
            int relMouseY = mouseY - guiTop;
            Renameable target = getHoveredRenameable(relMouseX, relMouseY);

            if (target != null && target.isRenameable()
                    && relMouseY >= GuiConstants.CONTENT_START_Y
                    && relMouseY < GuiConstants.CONTENT_START_Y + rowsVisible * ROW_HEIGHT) {
                int row = (relMouseY - GuiConstants.CONTENT_START_Y) / ROW_HEIGHT;
                int rowY = GuiConstants.CONTENT_START_Y + row * ROW_HEIGHT;

                // Determine X position, Y offset, and right edge based on target type and current tab
                int renameX = getRenameFieldX(target);
                int renameYOffset = getRenameFieldYOffset(target);
                int renameRightEdge = getRenameFieldRightEdge(target);
                inlineRenameEditor.startEditing(target, rowY + renameYOffset, renameX, renameRightEdge);

                return;
            }
        }

        // Handle tab header clicks (inline from old TerminalClickHandler)
        if (handleTabHeaderClick(mouseX, mouseY)) return;

        super.mouseClicked(mouseX, mouseY, mouseButton);

        // Delegate content area clicks to the active tab widget
        int relMouseX = mouseX - guiLeft;
        int relMouseY = mouseY - guiTop;
        AbstractTabWidget activeWidget = getActiveTabWidget();

        if (activeWidget != null) {
            activeWidget.handleClick(relMouseX, relMouseY, mouseButton);
        }
    }

    // TODO: create a tabs handler class to encapsulate all tab-related logic (rendering, clicking, state management) instead of having it in the main GUI class

    /**
     * Handle clicks on tab headers at the top of the GUI.
     * Replaces TerminalClickHandler.handleTabClick().
     */
    protected boolean handleTabHeaderClick(int mouseX, int mouseY) {
        int tabY = guiTop + GuiConstants.TAB_Y_OFFSET;

        for (int i = 0; i <= GuiConstants.LAST_TAB; i++) {
            int tabX = guiLeft + 4 + (i * (GuiConstants.TAB_WIDTH + 2));

            if (mouseX >= tabX && mouseX < tabX + GuiConstants.TAB_WIDTH
                    && mouseY >= tabY && mouseY < tabY + GuiConstants.TAB_HEIGHT) {

                // Check if the tab is enabled in server config
                if (CellTerminalServerConfig.isInitialized() && !CellTerminalServerConfig.getInstance().isTabEnabled(i)) {
                    return true;  // Consume click but don't switch to disabled tab
                }

                if (currentTab != i) {
                    onTabChanged(i);
                    CellTerminalClientConfig.getInstance().setSelectedTab(i);
                }

                return true;
            }
        }

        return false;
    }

    /**
     * Handle tab change (shared logic extracted from old TerminalClickHandler.Callback).
     */
    protected void onTabChanged(int tab) {
        // Exit subnet overview mode when switching tabs
        if (isInSubnetOverviewMode) exitSubnetOverviewMode();

        // Cancel any active inline rename when switching tabs
        if (inlineRenameEditor != null && inlineRenameEditor.isEditing()) {
            inlineRenameEditor.cancelEditing();
        }

        // Save scroll position for current tab before switching
        TabStateManager.TabType oldTabType = TabStateManager.TabType.fromIndex(currentTab);
        TabStateManager.getInstance().setScrollPosition(oldTabType, getScrollBar().getCurrentScroll());

        currentTab = tab;
        getScrollBar().setRange(0, 0, 1);
        updateScrollbarForCurrentTab();
        updateSearchModeButtonVisibility();
        initFilterButtons();  // Reinitialize filter buttons for new tab
        applyFiltersToDataManager();
        onSearchTextChanged();  // Reapply filter with tab-specific mode

        // Restore scroll position for new tab
        TabStateManager.TabType newTabType = TabStateManager.TabType.fromIndex(tab);
        int savedScroll = TabStateManager.getInstance().getScrollPosition(newTabType);
        scrollToLine(savedScroll);

        // Notify server of tab change for polling optimization
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketTabChange(tab));
    }

    /**
     * Handle upgrade insertion when the player is holding an upgrade item and left-clicks.
     * Delegates to the active tab widget for tab-specific logic.
     */
    protected boolean handleWidgetUpgradeClick(int mouseX, int mouseY) {
        ItemStack heldStack = mc.player.inventory.getItemStack();
        if (heldStack.isEmpty()) return false;
        if (!(heldStack.getItem() instanceof IUpgradeModule)) return false;

        // Check if upgrade insertion is enabled
        if (CellTerminalServerConfig.isInitialized()
                && !CellTerminalServerConfig.getInstance().isUpgradeInsertEnabled()) {
            MessageHelper.error("cellterminal.error.upgrade_insert_disabled");

            return true;  // Consume click to prevent other handlers
        }

        AbstractTabWidget activeWidget = getActiveTabWidget();
        if (activeWidget == null) return false;

        boolean isShiftClick = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (isShiftClick) return activeWidget.handleShiftUpgradeClick(heldStack);

        int relMouseX = mouseX - guiLeft;
        int relMouseY = mouseY - guiTop;
        Object hoveredData = activeWidget.getDataForHoveredRow(relMouseX, relMouseY);

        return activeWidget.handleUpgradeClick(hoveredData, heldStack, false);
    }

    public void rebuildAndUpdateScrollbar() {
        dataManager.rebuildLines();
        updateScrollbarForCurrentTab();
    }

    @Override
    protected void actionPerformed(GuiButton btn) throws IOException {
        if (btn == terminalStyleButton) {
            // Guard against repeated clicks while mouse is still down after initGui recreates buttons
            long now = System.currentTimeMillis();
            if (now - lastStyleButtonClickTime < STYLE_BUTTON_COOLDOWN) return;

            lastStyleButtonClickTime = now;
            terminalStyleButton.setStyle(CellTerminalClientConfig.getInstance().cycleTerminalStyle());
            this.buttonList.clear();
            this.initGui();

            return;
        }

        if (btn == searchModeButton) {
            // Cycle search mode, persist it, and reapply filter
            currentSearchMode = searchModeButton.cycleMode();
            CellTerminalClientConfig.getInstance().setSearchMode(currentSearchMode);
            onSearchTextChanged();

            return;
        }

        if (btn == subnetBackButton) {
            handleSubnetBackButtonClick();

            return;
        }

        /*
        if (btn == subnetVisibilityButton) {
            // Cycle subnet visibility mode and persist it
            currentSubnetVisibility = subnetVisibilityButton.cycleMode();
            CellTerminalClientConfig.getInstance().setSubnetVisibility(currentSubnetVisibility);

            return;
        }
        */

        // Handle filter button clicks
        if (btn instanceof GuiFilterButton) {
            GuiFilterButton filterBtn = (GuiFilterButton) btn;
            if (filterPanelManager.handleClick(filterBtn)) {
                applyFiltersToDataManager();
                rebuildAndUpdateScrollbar();

                return;
            }
        }

        // Handle slot limit button clicks
        if (btn instanceof GuiSlotLimitButton) {
            GuiSlotLimitButton slotLimitBtn = (GuiSlotLimitButton) btn;
            if (filterPanelManager.handleSlotLimitClick(slotLimitBtn)) {
                rebuildAndUpdateScrollbar();

                return;
            }
        }

        super.actionPerformed(btn);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // TODO: handle rename, priority, and other stuff at a lower level

        // Handle subnet rename field first (blocks other input when editing)
        if (this.subnetOverviewRenderer != null && this.subnetOverviewRenderer.isEditing()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                this.subnetOverviewRenderer.cancelEditing();
                return;
            }

            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                // Confirm rename - get subnet before stopping (stopEditing clears it)
                SubnetInfo subnet = this.subnetOverviewRenderer.getEditingSubnet();
                String newName = this.subnetOverviewRenderer.stopEditing();
                if (newName != null && subnet != null) sendSubnetRenamePacket(subnet, newName);

                return;
            }

            if (this.subnetOverviewRenderer.handleKeyTyped(typedChar, keyCode)) return;
        }

        // Handle inline rename editor (storage, cell, storage bus) - blocks all other input when editing
        if (this.inlineRenameEditor != null && this.inlineRenameEditor.isEditing()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                this.inlineRenameEditor.cancelEditing();
                return;
            }

            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                Renameable target = this.inlineRenameEditor.getEditingTarget();
                String newName = this.inlineRenameEditor.stopEditing();
                if (newName != null && target != null) sendRenamePacket(target, newName);

                return;
            }

            if (this.inlineRenameEditor.handleKeyTyped(typedChar, keyCode)) return;
        }

        // Handle network tool confirmation modal (blocks all other input)
        if (networkToolModal != null) {
            if (networkToolModal.handleKeyTyped(keyCode)) return;
        }

        // Handle modal search bar keyboard
        if (modalSearchBar != null && modalSearchBar.isVisible()) {
            if (modalSearchBar.handleKeyTyped(typedChar, keyCode)) return;
        }

        // Handle priority field keyboard
        if (priorityFieldManager != null) {
            if (priorityFieldManager.handleKeyTyped(typedChar, keyCode)) return;
        }

        // Esc key should close modals
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (inventoryPopup != null) {
                inventoryPopup = null;
                return;
            }

            if (partitionPopup != null) {
                partitionPopup = null;
                return;
            }

            if (this.searchField != null && this.searchField.isFocused()) {
                this.searchField.setFocused(false);
                return;
            }
        }

        // Handle search field keyboard input
        if (this.searchField != null && this.searchField.textboxKeyTyped(typedChar, keyCode)) return;

        // Delegate keybind handling to the active tab controller
        if (handleTabKeyTyped(keyCode)) return;

        // Toggle subnet overview in last, as it's available everywhere
        // And should not take priority over other handlers
        if (KeyBindings.SUBNET_OVERVIEW_TOGGLE.isActiveAndMatches(keyCode)) {
            handleSubnetBackButtonClick();
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    /**
     * Delegate key press to the active tab widget.
     * @return true if the key was handled
     */
    protected boolean handleTabKeyTyped(int keyCode) {
        AbstractTabWidget activeWidget = getActiveTabWidget();
        if (activeWidget == null) return false;

        return activeWidget.handleTabKeyTyped(keyCode);
    }

    /**
     * Find the first visible cell that can accept the given upgrade.
     * Respects current tab's filtered and sorted line list.
     * @param upgradeStack The upgrade item to check compatibility with
     * @return The first CellInfo that can accept the upgrade, or null if none found
     */
    protected CellInfo findFirstVisibleCellThatCanAcceptUpgrade(ItemStack upgradeStack) {
        List<Object> lines = getWidgetLines();
        Set<Long> checkedCellIds = new HashSet<>();

        for (Object line : lines) {
            CellInfo cell = null;

            if (line instanceof CellInfo) {
                cell = (CellInfo) line;
            } else if (line instanceof CellContentRow) {
                cell = ((CellContentRow) line).getCell();
            }

            if (cell == null) continue;

            long cellId = cell.getParentStorageId() * 100 + cell.getSlot();
            if (!checkedCellIds.add(cellId)) continue;

            if (cell.canAcceptUpgrade(upgradeStack)) return cell;
        }

        return null;
    }

    /**
     * Find the first visible storage bus that can accept the given upgrade.
     * Only used on storage bus tabs.
     * @param upgradeStack The upgrade item to check compatibility with
     * @return The first StorageBusInfo that can accept the upgrade, or null if none found
     */
    protected StorageBusInfo findFirstVisibleStorageBusThatCanAcceptUpgrade(ItemStack upgradeStack) {
        List<Object> lines = getWidgetLines();
        Set<Long> checkedBusIds = new HashSet<>();

        for (Object line : lines) {
            StorageBusInfo bus = null;

            if (line instanceof StorageBusInfo) {
                bus = (StorageBusInfo) line;
            } else if (line instanceof StorageBusContentRow) {
                bus = ((StorageBusContentRow) line).getStorageBus();
            }

            if (bus == null) continue;
            if (!checkedBusIds.add(bus.getId())) continue;

            if (bus.canAcceptUpgrade(upgradeStack)) return bus;
        }

        return null;
    }

    /**
     * Scroll to a specific line index.
     */
    public void scrollToLine(int lineIndex) {
        int currentScroll = this.getScrollBar().getCurrentScroll();

        // wheel() clamps delta to -1/+1, so we need to call it multiple times
        // Positive delta in wheel() scrolls up (towards 0), negative scrolls down
        while (currentScroll != lineIndex) {
            int delta = currentScroll < lineIndex ? -1 : 1;
            this.getScrollBar().wheel(delta);
            int newScroll = this.getScrollBar().getCurrentScroll();
            if (newScroll == currentScroll) break;
            currentScroll = newScroll;
        }
    }

    public void postUpdate(NBTTagCompound data) {
        // Check if we're awaiting a network switch - verify the data is for the expected network
        if (data.hasKey("networkId")) {
            long incomingNetworkId = data.getLong("networkId");

            // If awaiting a switch, only accept data from the expected network
            if (this.awaitingNetworkSwitch) {
                // Data is from the old network - discard it
                if (incomingNetworkId != this.currentNetworkId) return;

                // Received data for the correct network - switch complete
                this.awaitingNetworkSwitch = false;
            }
        }

        dataManager.processUpdate(data);
        updateScrollbarForCurrentTab();

        // Restore saved scroll after the first update that provides line counts.
        if (!this.initialScrollRestored) {
            TabStateManager.TabType tabType = TabStateManager.TabType.fromIndex(this.currentTab);
            int saved = TabStateManager.getInstance().getScrollPosition(tabType);
            scrollToLine(saved);
            this.initialScrollRestored = true;
        }
    }

    // TODO: move subnet handling to a pseudo-tab widget to clean up GuiCellTerminalBase and separate concerns better

    /**
     * Handle subnet list update from server.
     * Called when the server sends updated subnet connection data.
     */
    public void handleSubnetListUpdate(NBTTagCompound data) {
        this.subnetList.clear();

        // Always add the main network as first entry
        this.subnetList.add(SubnetInfo.createMainNetwork());

        if (data.hasKey("subnets")) {
            net.minecraft.nbt.NBTTagList subnetNbtList = data.getTagList("subnets", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < subnetNbtList.tagCount(); i++) {
                NBTTagCompound subnetNbt = subnetNbtList.getCompoundTagAt(i);
                this.subnetList.add(new SubnetInfo(subnetNbt));
            }
        }

        // Sort subnets: main network first, then favorites, then by position
        this.subnetList.sort((a, b) -> {
            // Main network always first
            if (a.isMainNetwork()) return -1;
            if (b.isMainNetwork()) return 1;

            // Favorites next
            if (a.isFavorite() != b.isFavorite()) return a.isFavorite() ? -1 : 1;

            // Then by dimension
            if (a.getDimension() != b.getDimension()) {
                return Integer.compare(a.getDimension(), b.getDimension());
            }

            // Then by distance from origin
            double distA = a.getPrimaryPos().distanceSq(0, 0, 0);
            double distB = b.getPrimaryPos().distanceSq(0, 0, 0);

            return Double.compare(distA, distB);
        });

        // Build flattened line list for display (headers + connection rows)
        buildSubnetLines();
    }

    /**
     * Build the flattened subnet lines list from the sorted subnet list.
     * Each subnet becomes a header row, followed by connection rows showing filters.
     */
    private void buildSubnetLines() {
        this.subnetLines.clear();

        for (SubnetInfo subnet : this.subnetList) {
            // Add header row
            this.subnetLines.add(subnet);

            // Skip connection rows for main network
            if (subnet.isMainNetwork()) continue;

            // Add connection rows with filters
            List<SubnetConnectionRow> connectionRows = subnet.buildConnectionRows(9);
            this.subnetLines.addAll(connectionRows);
        }
    }

    /**
     * Get the list of subnets connected to the current network.
     */
    public List<SubnetInfo> getSubnetList() {
        return subnetList;
    }

    /**
     * Check if currently in subnet overview mode.
     */
    public boolean isInSubnetOverviewMode() {
        return isInSubnetOverviewMode;
    }

    /**
     * Enter subnet overview mode.
     */
    public void enterSubnetOverviewMode() {
        this.isInSubnetOverviewMode = true;

        // Update back button to show left arrow (back)
        if (this.subnetBackButton != null) this.subnetBackButton.setInOverviewMode(true);

        // If we already have subnet data from a previous visit, rebuild lines and update scrollbar
        // This prevents the flicker that occurs while waiting for server response
        if (!this.subnetList.isEmpty()) {
            buildSubnetLines();
            int totalLines = this.subnetLines.size();
            int maxScroll = Math.max(0, totalLines - this.rowsVisible);
            this.getScrollBar().setRange(0, maxScroll, 1);
        }

        // Request subnet list from server (will update with fresh data)
        CellTerminalNetwork.INSTANCE.sendToServer(new com.cellterminal.network.PacketSubnetListRequest());
    }

    /**
     * Exit subnet overview mode and return to normal terminal view.
     */
    public void exitSubnetOverviewMode() {
        this.isInSubnetOverviewMode = false;

        // Update back button to show right arrow (to overview)
        if (this.subnetBackButton != null) this.subnetBackButton.setInOverviewMode(false);
    }

    /**
     * Handle back button click - toggle overview.
     */
    protected void handleSubnetBackButtonClick() {
        if (this.isInSubnetOverviewMode) {
            // In overview mode - go back to the last viewed network (main or subnet)
            switchToNetwork(currentNetworkId);
        } else {
            enterSubnetOverviewMode();
        }
    }

    /**
     * Switch to viewing a different network (main or subnet).
     * @param networkId 0 for main network, subnet ID for subnets
     */
    public void switchToNetwork(long networkId) {
        this.currentNetworkId = networkId;
        this.isInSubnetOverviewMode = false;

        // Reset data manager so the next update does a full rebuild with proper filters
        // instead of using snapshots from the old network context
        this.dataManager.resetForNetworkSwitch();

        // Update back button state - now we're in normal view, not overview
        if (this.subnetBackButton != null) this.subnetBackButton.setInOverviewMode(false);

        // Tell server to switch network context
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketSwitchNetwork(networkId));
    }

    /**
     * Handle click on a subnet entry in the overview.
     */
    protected void handleSubnetEntryClick(SubnetInfo subnet, SubnetOverviewRenderer.HoverZone zone, int mouseButton) {
        if (subnet == null) return;

        switch (zone) {
            case STAR:
                // Toggle favorite (including main network)
                if (mouseButton == 0) {
                    boolean newFavorite = !subnet.isFavorite();
                    subnet.setFavorite(newFavorite);
                    CellTerminalNetwork.INSTANCE.sendToServer(PacketSubnetAction.toggleFavorite(subnet.getId(), newFavorite));
                    // Re-sort subnet list to reflect favorite change and rebuild display
                    this.subnetList.sort((a, b) -> {
                        if (a.isMainNetwork()) return -1;
                        if (b.isMainNetwork()) return 1;
                        if (a.isFavorite() != b.isFavorite()) return a.isFavorite() ? -1 : 1;
                        if (a.getDimension() != b.getDimension()) {
                            return Integer.compare(a.getDimension(), b.getDimension());
                        }
                        double distA = a.getPrimaryPos().distanceSq(0, 0, 0);
                        double distB = b.getPrimaryPos().distanceSq(0, 0, 0);

                        return Double.compare(distA, distB);
                    });
                    buildSubnetLines();
                    updateScrollbarForCurrentTab();
                }
                break;

            case NAME:
                if (mouseButton == 1 && !subnet.isMainNetwork()) {
                    // Right-click - start renaming
                    int rowY = this.subnetOverviewRenderer.getRowYForSubnet(subnet, this.subnetLines, getScrollBar().getCurrentScroll());
                    if (rowY >= 0) this.subnetOverviewRenderer.startEditing(subnet, rowY);
                } else if (mouseButton == 0 && !subnet.isMainNetwork()) {
                    // Left-click - double-click highlights in world
                    long currentTime = System.currentTimeMillis();
                    if (subnet.getId() == lastSubnetClickId
                        && currentTime - lastSubnetClickTime < DOUBLE_CLICK_THRESHOLD) {
                        highlightSubnetInWorld(subnet);
                        lastSubnetClickTime = 0;
                        lastSubnetClickId = -1;
                    } else {
                        lastSubnetClickTime = currentTime;
                        lastSubnetClickId = subnet.getId();
                    }
                }
                break;

            case LOAD_BUTTON:
                // Load button clicked - switch to this subnet (including main network)
                if (mouseButton == 0 && subnet.isAccessible() && subnet.hasPower()) {
                    switchToNetwork(subnet.getId());
                }
                break;

            case ENTRY:
            default:
                // Double-click on entry highlights in world (not for main network)
                if (mouseButton == 0 && !subnet.isMainNetwork()) {
                    long currentTime = System.currentTimeMillis();
                    if (subnet.getId() == lastSubnetClickId
                        && currentTime - lastSubnetClickTime < DOUBLE_CLICK_THRESHOLD) {
                        // Double-click - highlight in world
                        highlightSubnetInWorld(subnet);
                        lastSubnetClickTime = 0;
                        lastSubnetClickId = -1;
                    } else {
                        // First click - track for potential double-click
                        lastSubnetClickTime = currentTime;
                        lastSubnetClickId = subnet.getId();
                    }
                }
                break;
        }
    }

    /**
     * Highlight a subnet's primary position in the world.
     */
    protected void highlightSubnetInWorld(SubnetInfo subnet) {
        if (subnet == null || subnet.isMainNetwork()) return;

        // Check if in different dimension
        if (subnet.getDimension() != Minecraft.getMinecraft().player.dimension) {
            MessageHelper.error("cellterminal.error.different_dimension");

            return;
        }

        CellTerminalNetwork.INSTANCE.sendToServer(
            new PacketHighlightBlock(subnet.getPrimaryPos(), subnet.getDimension())
        );

        // Send green chat message with block name and coordinates
        MessageHelper.success("gui.cellterminal.highlighted",
            subnet.getPrimaryPos().getX(),
            subnet.getPrimaryPos().getY(),
            subnet.getPrimaryPos().getZ(),
            subnet.getDisplayName());
    }

    // TODO: handle renaming at a lower level (widget)

    /**
     * Send a packet to rename a subnet.
     */
    protected void sendSubnetRenamePacket(SubnetInfo subnet, String newName) {
        if (subnet == null || subnet.isMainNetwork()) return;

        CellTerminalNetwork.INSTANCE.sendToServer(PacketSubnetAction.rename(subnet.getId(), newName));

        // Update local name immediately for responsiveness
        subnet.setCustomName(newName.isEmpty() ? null : newName);
    }

    /**
     * Send a rename packet for any Renameable target (storage, cell, storage bus).
     */
    public void sendRenamePacket(Renameable target, String newName) {
        if (target == null) return;

        switch (target.getRenameTargetType()) {
            case STORAGE:
                CellTerminalNetwork.INSTANCE.sendToServer(
                    PacketRenameAction.renameStorage(target.getRenameId(), newName));
                break;
            case CELL:
                CellTerminalNetwork.INSTANCE.sendToServer(
                    PacketRenameAction.renameCell(target.getRenameId(), target.getRenameSecondaryId(), newName));
                break;
            case STORAGE_BUS:
                CellTerminalNetwork.INSTANCE.sendToServer(
                    PacketRenameAction.renameStorageBus(target.getRenameId(), newName));
                break;
            default:
                break;
        }

        // Update local name immediately for responsiveness
        target.setCustomName(newName.isEmpty() ? null : newName);
    }

    /**
     * Get the currently hovered renameable target, delegating to the active tab widget.
     */
    protected Renameable getHoveredRenameable(int relMouseX, int relMouseY) {
        AbstractTabWidget activeWidget = getActiveTabWidget();
        if (activeWidget == null) return null;

        return activeWidget.getHoveredRenameable(relMouseX, relMouseY);
    }

    /**
     * Get the X position for the inline rename field, delegating to the active tab widget.
     */
    protected int getRenameFieldX(Renameable target) {
        AbstractTabWidget activeWidget = getActiveTabWidget();
        if (activeWidget != null) return activeWidget.getRenameFieldX(target);

        // Fallback for non-widget tabs
        if (target instanceof CellInfo) return GuiConstants.CELL_INDENT + 18 - 2;

        return GuiConstants.GUI_INDENT + 20 - 2;
    }

    /**
     * Get the Y offset for the inline rename field, delegating to the active tab widget.
     * Most tabs return 0 (name at y+1). TempArea returns 4 (name at y+5).
     */
    protected int getRenameFieldYOffset(Renameable target) {
        AbstractTabWidget activeWidget = getActiveTabWidget();
        if (activeWidget != null) return activeWidget.getRenameFieldYOffset(target);

        return 0;
    }

    /**
     * Get the right edge for the inline rename field, delegating to the active tab widget.
     */
    protected int getRenameFieldRightEdge(Renameable target) {
        AbstractTabWidget activeWidget = getActiveTabWidget();
        if (activeWidget != null) return activeWidget.getRenameFieldRightEdge(target);

        return GuiConstants.CONTENT_RIGHT_EDGE - PriorityFieldManager.FIELD_WIDTH - PriorityFieldManager.RIGHT_MARGIN - 4;
    }

    @Override
    public void onGuiClosed() {
        // Persist the current scroll position for the active tab so it is restored when the GUI is reopened.
        TabStateManager.TabType tabType = TabStateManager.TabType.fromIndex(this.currentTab);
        TabStateManager.getInstance().setScrollPosition(tabType, this.getScrollBar().getCurrentScroll());

        super.onGuiClosed();
    }

    protected void updateScrollbarForCurrentTab() {
        List<Object> lines = getWidgetLines();
        int lineCount = lines.size();

        // Use the tab widget's visible item count (accounts for non-standard row heights)
        AbstractTabWidget activeWidget = getActiveTabWidget();
        int visibleItems = activeWidget != null ? activeWidget.getVisibleItemCount() : this.rowsVisible;

        this.getScrollBar().setRange(0, Math.max(0, lineCount - visibleItems), 1);
    }

    // Callbacks from popups
    // TODO: move these to the popups themselves

    public void onPartitionAllClicked(CellInfo cell) {
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketPartitionAction(
            cell.getParentStorageId(),
            cell.getSlot(),
            PacketPartitionAction.Action.SET_ALL_FROM_CONTENTS
        ));
    }

    public void onTogglePartitionItem(CellInfo cell, ItemStack stack) {
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketPartitionAction(
            cell.getParentStorageId(),
            cell.getSlot(),
            PacketPartitionAction.Action.TOGGLE_ITEM,
            stack
        ));
    }

    public void onRemovePartitionItem(CellInfo cell, int partitionSlot) {
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketPartitionAction(
            cell.getParentStorageId(),
            cell.getSlot(),
            PacketPartitionAction.Action.REMOVE_ITEM,
            partitionSlot
        ));
    }

    public void onAddPartitionItem(CellInfo cell, int partitionSlot, ItemStack stack) {
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketPartitionAction(
            cell.getParentStorageId(),
            cell.getSlot(),
            PacketPartitionAction.Action.ADD_ITEM,
            partitionSlot,
            stack
        ));
    }

    public void onAddStorageBusPartitionItem(StorageBusInfo storageBus, int partitionSlot, ItemStack stack) {
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketStorageBusPartitionAction(
            storageBus.getId(),
            PacketStorageBusPartitionAction.Action.ADD_ITEM,
            partitionSlot,
            stack
        ));
    }

    public void onAddTempCellPartitionItem(int tempSlotIndex, int partitionSlot, ItemStack stack) {
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketTempCellPartitionAction(
            tempSlotIndex,
            PacketTempCellPartitionAction.Action.ADD_ITEM,
            partitionSlot,
            stack
        ));
    }

    // JEI Ghost Ingredient support

    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        if (partitionPopup != null) return partitionPopup.getGhostTargets();

        AbstractTabWidget activeWidget = getActiveTabWidget();
        if (activeWidget == null) return Collections.emptyList();

        return activeWidget.getPhantomTargets(ingredient);
    }

    // Accessors for popups and renderers

    public Map<Long, StorageInfo> getStorageMap() {
        return dataManager.getStorageMap();
    }

    /**
     * Create a ToolContext for the Network Tools tab.
     */
    public INetworkTool.ToolContext createNetworkToolContext() {
        return new INetworkTool.ToolContext(
            dataManager.getStorageMap(),
            dataManager.getStorageBusMap(),
            filterPanelManager.getAllFilterStates(),
            getEffectiveSearchMode(),
            new INetworkTool.NetworkToolCallback() {
                @Override
                public void sendToolPacket(String toolId, byte[] data) {
                    // Handled by tools directly
                }

                @Override
                public void showError(String message) {
                    MessageHelper.error(message);
                }

                @Override
                public void showSuccess(String message) {
                    MessageHelper.success(message);
                }
            },
            dataManager.getSearchFilter(),
            dataManager.isUsingAdvancedSearch(),
            dataManager.getAdvancedMatcher());
    }

    // Network Tools modal support
    protected GuiToolConfirmationModal networkToolModal = null;

    /**
     * Show the confirmation modal for a network tool.
     */
    public void showNetworkToolConfirmation(INetworkTool tool) {
        INetworkTool.ToolContext ctx = createNetworkToolContext();
        String error = tool.getExecutionError(ctx);
        if (error != null) {
            MessageHelper.error(error);

            return;
        }

        networkToolModal = new GuiToolConfirmationModal(
            tool, ctx, this.fontRenderer, this.width, this.height,
            () -> {
                tool.execute(ctx);
                networkToolModal = null;
            },
            () -> networkToolModal = null);
    }

    // ---- GuiContext interface implementation ----

    @Override
    public TerminalDataManager getDataManager() {
        return dataManager;
    }

    @Override
    public ItemStack getHeldStack() {
        return mc.player.inventory.getItemStack();
    }

    @Override
    public boolean isShiftDown() {
        return isShiftKeyDown();
    }

    @Override
    public void sendPacket(Object packet) {
        CellTerminalNetwork.INSTANCE.sendToServer((net.minecraftforge.fml.common.network.simpleimpl.IMessage) packet);
    }

    @Override
    public void openInventoryPopup(CellInfo cell) {
        inventoryPopup = new PopupCellInventory(this, cell, 0, 0);
    }

    @Override
    public void openPartitionPopup(CellInfo cell) {
        partitionPopup = new PopupCellPartition(this, cell, 0, 0);
    }

    @Override
    public void startInlineRename(Renameable target, int rowY, int renameX, int renameRightEdge) {
        if (inlineRenameEditor != null) {
            inlineRenameEditor.startEditing(target, rowY, renameX, renameRightEdge);
        }
    }

    @Override
    public void showError(String translationKey, Object... args) {
        MessageHelper.error(translationKey, args);
    }

    @Override
    public void showSuccess(String translationKey, Object... args) {
        MessageHelper.success(translationKey, args);
    }

    @Override
    public void showWarning(String translationKey, Object... args) {
        MessageHelper.warning(translationKey, args);
    }

    @Override
    public void highlightInWorld(BlockPos pos, int dimension, String displayName) {
        if (pos == null || pos.equals(BlockPos.ORIGIN)) return;

        // Check if in different dimension
        if (dimension != Minecraft.getMinecraft().player.dimension) {
            MessageHelper.error("cellterminal.error.different_dimension");
            return;
        }

        CellTerminalNetwork.INSTANCE.sendToServer(
            new PacketHighlightBlock(pos, dimension)
        );

        // Send green chat message with block name and coordinates
        MessageHelper.success("gui.cellterminal.highlighted",
            pos.getX(), pos.getY(), pos.getZ(), displayName);
    }

    @Override
    public void highlightCellInWorld(CellInfo cell) {
        if (cell == null) return;

        // Find the parent storage to get its position
        StorageInfo storage = dataManager.findStorageForCell(cell);
        if (storage == null) return;

        // Use storage name (block name) since we're highlighting the block, not the cell
        highlightInWorld(storage.getPos(), storage.getDimension(), storage.getName());
    }

    @Override
    public Set<Long> getSelectedStorageBusIds() {
        return selectedStorageBusIds;
    }

    @Override
    public Set<Integer> getSelectedTempCellSlots() {
        return selectedTempCellSlots;
    }
}
