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

import appeng.api.AEApi;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.AppEngSlot;

import mezz.jei.api.gui.IGhostIngredientHandler;

import com.cellterminal.client.CellContentRow;
import com.cellterminal.client.CellFilter;
import com.cellterminal.client.CellInfo;
import com.cellterminal.client.SubnetConnectionRow;
import com.cellterminal.client.SubnetInfo;
import com.cellterminal.client.SubnetVisibility;
import com.cellterminal.client.TabStateManager;
import com.cellterminal.config.CellTerminalClientConfig;
import com.cellterminal.config.CellTerminalClientConfig.TerminalStyle;
import com.cellterminal.client.KeyBindings;
import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.client.StorageInfo;
import com.cellterminal.config.CellTerminalServerConfig;
import com.cellterminal.gui.handler.JeiGhostHandler;
import com.cellterminal.gui.handler.JeiGhostHandler.PartitionSlotTarget;
import com.cellterminal.gui.handler.JeiGhostHandler.StorageBusPartitionSlotTarget;
import com.cellterminal.gui.handler.StorageBusClickHandler;
import com.cellterminal.gui.handler.TabRenderingHandler;
import com.cellterminal.gui.handler.TerminalClickHandler;
import com.cellterminal.gui.handler.TerminalDataManager;
import com.cellterminal.gui.handler.TooltipHandler;
import com.cellterminal.gui.handler.UpgradeClickHandler;
import com.cellterminal.gui.networktools.INetworkTool;
import com.cellterminal.gui.networktools.GuiToolConfirmationModal;
import com.cellterminal.gui.overlay.MessageHelper;
import com.cellterminal.gui.overlay.OverlayMessageRenderer;
import com.cellterminal.gui.render.InventoryTabRenderer;
import com.cellterminal.gui.render.NetworkToolsTabRenderer;
import com.cellterminal.gui.render.PartitionTabRenderer;
import com.cellterminal.gui.render.RenderContext;
import com.cellterminal.gui.render.StorageBusInventoryTabRenderer;
import com.cellterminal.gui.render.StorageBusPartitionTabRenderer;
import com.cellterminal.gui.render.TerminalTabRenderer;
import com.cellterminal.gui.tab.ITabController;
import com.cellterminal.gui.tab.PartitionTabController;
import com.cellterminal.gui.tab.StorageBusPartitionTabController;
import com.cellterminal.gui.tab.TabContext;
import com.cellterminal.gui.subnet.GuiBackButton;
import com.cellterminal.gui.subnet.SubnetOverviewRenderer;
import com.cellterminal.gui.tab.TabControllerRegistry;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketExtractUpgrade;
import com.cellterminal.network.PacketHighlightBlock;
import com.cellterminal.network.PacketSubnetAction;
import com.cellterminal.network.PacketSubnetListRequest;
import com.cellterminal.network.PacketSwitchNetwork;
import com.cellterminal.network.PacketPartitionAction;
import com.cellterminal.network.PacketRenameAction;
import com.cellterminal.network.PacketSlotLimitChange;
import com.cellterminal.network.PacketStorageBusPartitionAction;
import com.cellterminal.network.PacketTabChange;
import com.cellterminal.network.PacketUpgradeCell;
import com.cellterminal.network.PacketUpgradeStorageBus;
import com.cellterminal.gui.rename.InlineRenameEditor;
import com.cellterminal.gui.rename.Renameable;
import com.cellterminal.gui.rename.RenameTargetType;


/**
 * Base GUI for Cell Terminal variants.
 * Contains shared functionality for displaying storage drives/chests with their cells.
 * Supports three tabs: Terminal (list view), Inventory (cell slots with contents), Partition (cell slots with partition).
 */
public abstract class GuiCellTerminalBase extends AEBaseGui implements IJEIGhostIngredients {

    // Tab constants
    public static final int TAB_TERMINAL = 0;
    public static final int TAB_INVENTORY = 1;
    public static final int TAB_PARTITION = 2;
    public static final int TAB_STORAGE_BUS_INVENTORY = 3;
    public static final int TAB_STORAGE_BUS_PARTITION = 4;
    public static final int TAB_NETWORK_TOOLS = 5;
    private static final int TAB_WIDTH = 22;
    private static final int TAB_HEIGHT = 22;
    private static final int TAB_Y_OFFSET = -22;

    // Layout constants
    protected static final int ROW_HEIGHT = 18;
    protected static final int MIN_ROWS = 6;
    protected static final int DEFAULT_ROWS = 8;

    // Magic height number for tall mode calculation (header + footer heights)
    private static final int MAGIC_HEIGHT_NUMBER = 18 + 98;

    // Dynamic row count (computed based on terminal style)
    protected int rowsVisible = DEFAULT_ROWS;

    // Tab renderers
    protected TerminalTabRenderer terminalRenderer;
    protected InventoryTabRenderer inventoryRenderer;
    protected PartitionTabRenderer partitionRenderer;
    protected StorageBusInventoryTabRenderer storageBusInventoryRenderer;
    protected StorageBusPartitionTabRenderer storageBusPartitionRenderer;
    protected NetworkToolsTabRenderer networkToolsRenderer;
    protected SubnetOverviewRenderer subnetOverviewRenderer;
    protected RenderContext renderContext;

    // Handlers
    protected TerminalDataManager dataManager;
    protected TerminalClickHandler clickHandler;
    protected StorageBusClickHandler storageBusClickHandler;

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

    // Tab icons (lazy initialized)
    protected ItemStack tabIconTerminal = null;
    protected ItemStack tabIconInventory = null;
    protected ItemStack tabIconPartition = null;
    protected ItemStack tabIconStorageBus = null;  // Storage bus icon for composite rendering
    protected ItemStack tabIconNetworkTool = null;  // Network Tools tab icon

    // Popup states
    protected PopupCellInventory inventoryPopup = null;
    protected PopupCellPartition partitionPopup = null;
    protected CellInfo hoveredCell = null;
    protected int hoverType = 0; // 0=none, 1=inventory, 2=partition, 3=eject
    protected StorageInfo hoveredStorageLine = null; // Storage header being hovered

    // Priority field manager (inline editable fields)
    protected PriorityFieldManager priorityFieldManager = null;

    // Inline rename editor for storage, cell, and storage bus renaming
    protected InlineRenameEditor inlineRenameEditor = null;

    // Tab hover for tooltips
    protected int hoveredTab = -1;

    // Hover tracking for background highlight
    protected int hoveredLineIndex = -1;

    // Tab 2/3 hover state
    protected CellInfo hoveredCellCell = null;
    protected StorageInfo hoveredCellStorage = null;
    protected int hoveredCellSlotIndex = -1;
    protected ItemStack hoveredContentStack = ItemStack.EMPTY;
    protected int hoveredContentX = 0;
    protected int hoveredContentY = 0;
    protected int hoveredContentSlotIndex = -1;

    // Partition slot tracking for JEI ghost ingredients and click handling
    protected int hoveredPartitionSlotIndex = -1;
    protected CellInfo hoveredPartitionCell = null;
    protected final List<JeiGhostHandler.PartitionSlotTarget> partitionSlotTargets = new ArrayList<>();

    // Cell partition button tracking
    protected CellInfo hoveredPartitionAllButtonCell = null;  // Tab 2: partition all button
    protected CellInfo hoveredClearPartitionButtonCell = null;  // Tab 3: clear partition button

    // Storage bus hover and selection tracking
    protected StorageBusInfo hoveredStorageBus = null;
    protected int hoveredStorageBusPartitionSlot = -1;
    protected int hoveredStorageBusContentSlot = -1;
    protected StorageBusInfo hoveredClearButtonStorageBus = null;
    protected StorageBusInfo hoveredIOModeButtonStorageBus = null;
    protected StorageBusInfo hoveredPartitionAllButtonStorageBus = null;  // Tab 4: partition all button
    protected final Set<Long> selectedStorageBusIds = new HashSet<>();  // For Tab 5 - multi-selection of buses for keybind
    protected final List<JeiGhostHandler.StorageBusPartitionSlotTarget> storageBusPartitionSlotTargets = new ArrayList<>();

    // Modal search bar for expanded editing
    protected GuiModalSearchBar modalSearchBar = null;

    // Guard to prevent style button from being retriggered while mouse is still down
    private long lastStyleButtonClickTime = 0;
    private static final long STYLE_BUTTON_COOLDOWN = 100;  // ms

    protected long lastSearchFieldClickTime = 0;
    protected static final long DOUBLE_CLICK_THRESHOLD = 500;  // ms

    // Hovered upgrade icon for tooltip and extraction
    protected RenderContext.UpgradeIconTarget hoveredUpgradeIcon = null;

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

        this.xSize = 208;
        this.rowsVisible = calculateRowsCount();
        this.ySize = MAGIC_HEIGHT_NUMBER + this.rowsVisible * ROW_HEIGHT;
        this.setScrollBar(new GuiScrollbar());

        this.renderContext = new RenderContext();
        this.dataManager = new TerminalDataManager();
        this.clickHandler = new TerminalClickHandler();
        this.storageBusClickHandler = new StorageBusClickHandler();
        this.filterPanelManager = new FilterPanelManager();

        // Load persisted settings
        CellTerminalClientConfig config = CellTerminalClientConfig.getInstance();
        this.currentTab = config.getSelectedTab();
        if (this.currentTab < 0 || this.currentTab >= TabControllerRegistry.getTabCount()) this.currentTab = TAB_TERMINAL;

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
        if (!CellTerminalServerConfig.isInitialized()) return TAB_TERMINAL;

        CellTerminalServerConfig config = CellTerminalServerConfig.getInstance();

        for (int i = 0; i < TabControllerRegistry.getTabCount(); i++) {
            if (config.isTabEnabled(i)) return i;
        }

        // Fallback to terminal tab even if disabled (should not happen in practice)
        return TAB_TERMINAL;
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
        initTabIcons();
        initRenderers();
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

    protected void initRenderers() {
        this.terminalRenderer = new TerminalTabRenderer(this.fontRenderer, this.itemRender);
        this.inventoryRenderer = new InventoryTabRenderer(this.fontRenderer, this.itemRender);
        this.partitionRenderer = new PartitionTabRenderer(this.fontRenderer, this.itemRender);
        this.storageBusInventoryRenderer = new StorageBusInventoryTabRenderer(this.fontRenderer, this.itemRender);
        this.storageBusPartitionRenderer = new StorageBusPartitionTabRenderer(this.fontRenderer, this.itemRender);
        this.networkToolsRenderer = new NetworkToolsTabRenderer(this.fontRenderer, this.itemRender);
        this.subnetOverviewRenderer = new SubnetOverviewRenderer(this.fontRenderer, this.itemRender);
        this.inlineRenameEditor = new InlineRenameEditor();
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

        // Calculate button Y as if in SMALL mode (for consistent positioning across style changes)
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

    protected void initSearchField() {
        // Search help button: positioned at the start of the search area
        int titleWidth = this.fontRenderer.getStringWidth(getGuiTitle());
        int helpButtonX = 22 + titleWidth + 4;
        int helpButtonY = 5;

        if (this.searchHelpButton != null) this.buttonList.remove(this.searchHelpButton);
        this.searchHelpButton = new GuiSearchHelpButton(2, this.guiLeft + helpButtonX, this.guiTop + helpButtonY);
        this.buttonList.add(this.searchHelpButton);

        // Search field: positioned after help button
        int searchX = helpButtonX + GuiSearchHelpButton.BUTTON_SIZE + 2;
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
        this.searchModeButton = new GuiSearchModeButton(1, this.guiLeft + 189, this.guiTop + 4, currentSearchMode);
        this.buttonList.add(this.searchModeButton);
        updateSearchModeButtonVisibility();

        // Subnet visibility button: positioned next to search mode button
        if (this.subnetVisibilityButton != null) this.buttonList.remove(this.subnetVisibilityButton);
        this.subnetVisibilityButton = new GuiSubnetVisibilityButton(4, this.guiLeft + 189 - 14, this.guiTop + 4, currentSubnetVisibility);
        this.buttonList.add(this.subnetVisibilityButton);

        // Initialize modal search bar
        this.modalSearchBar = new GuiModalSearchBar(this.fontRenderer, this.searchField, this::onSearchTextChanged);

        if (!existingSearch.isEmpty()) dataManager.setSearchFilter(existingSearch, getEffectiveSearchMode());
    }

    protected void updateSearchModeButtonVisibility() {
        if (this.searchModeButton == null) return;

        // Delegate to tab controller to determine visibility
        ITabController controller = TabControllerRegistry.getController(currentTab);
        this.searchModeButton.visible = (controller != null && controller.showSearchModeButton());
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
     * Delegates to the active tab controller.
     */
    protected SearchFilterMode getEffectiveSearchMode() {
        ITabController controller = TabControllerRegistry.getController(currentTab);
        if (controller != null) {
            return controller.getEffectiveSearchMode(currentSearchMode);
        }

        return currentSearchMode;
    }

    protected void initTabIcons() {
        // Terminal icon - use the interface terminal part
        tabIconTerminal = AEApi.instance().definitions().parts().interfaceTerminal()
            .maybeStack(1).orElse(ItemStack.EMPTY);

        // Inventory icon - use ME Chest
        tabIconInventory = AEApi.instance().definitions().blocks().chest()
            .maybeStack(1).orElse(ItemStack.EMPTY);

        // Partition icon - use Cell Workbench
        tabIconPartition = AEApi.instance().definitions().blocks().cellWorkbench()
            .maybeStack(1).orElse(ItemStack.EMPTY);

        // Storage bus icon (used for composite rendering)
        tabIconStorageBus = AEApi.instance().definitions().parts().storageBus()
            .maybeStack(1).orElse(ItemStack.EMPTY);

        // Network Tools icon
        tabIconNetworkTool = AEApi.instance().definitions().items().networkTool()
            .maybeStack(1).orElse(ItemStack.EMPTY);
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

        // Draw hover preview
        if (currentTab == TAB_TERMINAL && hoveredCell != null && inventoryPopup == null && partitionPopup == null) {
            int previewX = mouseX + 10, previewY = mouseY + 10;
            if (hoverType == 1) new PopupCellInventory(this, hoveredCell, previewX, previewY).draw(mouseX, mouseY);
            else if (hoverType == 2) new PopupCellPartition(this, hoveredCell, previewX, previewY).draw(mouseX, mouseY);
            else if (hoverType == 3) this.drawHoveringText(Collections.singletonList(I18n.format("gui.cellterminal.eject_cell")), mouseX, mouseY);
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
        TooltipHandler.TooltipContext ctx = new TooltipHandler.TooltipContext();
        ctx.currentTab = currentTab;
        ctx.hoveredTab = hoveredTab;
        ctx.hoveredCell = hoveredCell;
        ctx.hoverType = hoverType;
        ctx.hoveredContentStack = hoveredContentStack;
        ctx.hoveredContentX = hoveredContentX;
        ctx.hoveredContentY = hoveredContentY;
        ctx.hoveredClearButtonStorageBus = hoveredClearButtonStorageBus;
        ctx.hoveredIOModeButtonStorageBus = hoveredIOModeButtonStorageBus;
        ctx.hoveredPartitionAllButtonStorageBus = hoveredPartitionAllButtonStorageBus;
        ctx.hoveredPartitionAllButtonCell = hoveredPartitionAllButtonCell;
        ctx.hoveredClearPartitionButtonCell = hoveredClearPartitionButtonCell;
        ctx.inventoryPopup = inventoryPopup;
        ctx.partitionPopup = partitionPopup;
        ctx.terminalStyleButton = terminalStyleButton;
        ctx.searchModeButton = searchModeButton;
        ctx.searchHelpButton = searchHelpButton;
        ctx.subnetVisibilityButton = subnetVisibilityButton;
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

        // Upgrade icon hover state
        ctx.hoveredUpgradeIcon = hoveredUpgradeIcon;

        // Network tools hover state
        RenderContext renderCtx = getRenderContext();
        ctx.hoveredNetworkTool = renderCtx.hoveredNetworkTool;
        ctx.hoveredNetworkToolHelpButton = renderCtx.hoveredNetworkToolHelpButton;
        ctx.hoveredNetworkToolPreview = renderCtx.hoveredNetworkToolPreview;

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

        // Reset render context hover states
        renderContext.resetHoverState();
        renderContext.storageMap = dataManager.getStorageMap();
        renderContext.rowsVisible = this.rowsVisible;
        renderContext.guiLeft = this.guiLeft;
        renderContext.guiTop = this.guiTop;
        renderContext.selectedStorageBusIds = this.selectedStorageBusIds;

        int relMouseX = mouseX - offsetX;
        int relMouseY = mouseY - offsetY;
        final int currentScroll = this.getScrollBar().getCurrentScroll();

        // Reset priority field visibility before rendering
        if (priorityFieldManager != null) priorityFieldManager.resetVisibility();

        // Subnet overview mode takes over the content area
        if (this.isInSubnetOverviewMode) {
            drawSubnetOverviewContent(relMouseX, relMouseY, currentScroll);
            syncHoverStateFromContext(relMouseX, relMouseY);
            drawControlsHelpForCurrentTab();

            return;
        }

        // Draw based on current tab using renderers
        switch (currentTab) {
            case TAB_TERMINAL:
                terminalRenderer.draw(dataManager.getLines(), currentScroll, rowsVisible, relMouseX, relMouseY, renderContext);
                break;
            case TAB_INVENTORY:
                inventoryRenderer.draw(dataManager.getInventoryLines(), currentScroll, rowsVisible,
                    relMouseX, relMouseY, mouseX, mouseY, dataManager.getStorageMap(), renderContext);
                break;
            case TAB_PARTITION:
                partitionRenderer.draw(dataManager.getPartitionLines(), currentScroll, rowsVisible,
                    relMouseX, relMouseY, mouseX, mouseY, dataManager.getStorageMap(),
                    this.guiLeft, this.guiTop, renderContext);
                break;
            case TAB_STORAGE_BUS_INVENTORY:
                storageBusInventoryRenderer.draw(dataManager.getStorageBusInventoryLines(), currentScroll, rowsVisible,
                    relMouseX, relMouseY, mouseX, mouseY, renderContext);
                break;
            case TAB_STORAGE_BUS_PARTITION:
                storageBusPartitionRenderer.draw(dataManager.getStorageBusPartitionLines(), currentScroll, rowsVisible,
                    relMouseX, relMouseY, mouseX, mouseY,
                    this.guiLeft, this.guiTop, renderContext);
                break;
            case TAB_NETWORK_TOOLS:
                networkToolsRenderer.draw(currentScroll, rowsVisible,
                    relMouseX, relMouseY, createNetworkToolContext(), renderContext);
                break;
        }

        // Draw inline rename field overlay if editing
        if (inlineRenameEditor != null && inlineRenameEditor.isEditing()) {
            inlineRenameEditor.drawRenameField(this.fontRenderer);
        }

        // Update priority field positions based on visible storages
        if (priorityFieldManager != null) {
            for (RenderContext.VisibleStorageEntry entry : renderContext.visibleStorages) {
                // Only show priority field if the storage type supports it
                if (entry.storage.supportsPriority()) {
                    PriorityFieldManager.PriorityField field = priorityFieldManager.getOrCreateField(
                        entry.storage, guiLeft, guiTop);
                    priorityFieldManager.updateFieldPosition(field, entry.y, guiLeft, guiTop);
                }
            }

            // Update storage bus priority field positions for tabs 4 and 5
            for (RenderContext.VisibleStorageBusEntry entry : renderContext.visibleStorageBuses) {
                if (entry.storageBus.supportsPriority()) {
                    PriorityFieldManager.StorageBusPriorityField field = priorityFieldManager.getOrCreateStorageBusField(
                        entry.storageBus, guiLeft, guiTop);
                    priorityFieldManager.updateStorageBusFieldPosition(field, entry.y, guiLeft, guiTop);
                }
            }

            // Draw priority fields (in GUI-relative context)
            priorityFieldManager.drawFieldsRelative(guiLeft, guiTop);

            // Cleanup stale fields
            priorityFieldManager.cleanupStaleFields(dataManager.getStorageMap());
            priorityFieldManager.cleanupStaleStorageBusFields(dataManager.getStorageBusMap());
        }

        syncHoverStateFromContext(relMouseX, relMouseY);

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

        TabRenderingHandler.ControlsHelpResult result = TabRenderingHandler.drawControlsHelpWidget(ctx);
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

        int panelRight = -CONTROLS_HELP_RIGHT_MARGIN;
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

        return areas;
    }

    protected void syncHoverStateFromContext(int relMouseX, int relMouseY) {
        this.hoveredCell = renderContext.hoveredCell;
        this.hoverType = renderContext.hoverType;
        this.hoveredStorageLine = renderContext.hoveredStorageLine;
        this.hoveredLineIndex = renderContext.hoveredLineIndex;
        this.hoveredContentStack = renderContext.hoveredContentStack;
        this.hoveredCellCell = renderContext.hoveredCellCell;
        this.hoveredCellStorage = renderContext.hoveredCellStorage;
        this.hoveredCellSlotIndex = renderContext.hoveredCellSlotIndex;
        this.hoveredContentSlotIndex = renderContext.hoveredContentSlotIndex;
        this.hoveredContentX = renderContext.hoveredContentX;
        this.hoveredContentY = renderContext.hoveredContentY;
        this.hoveredPartitionSlotIndex = renderContext.hoveredPartitionSlotIndex;
        this.hoveredPartitionCell = renderContext.hoveredPartitionCell;

        // Storage bus tab hover state
        this.hoveredStorageBus = renderContext.hoveredStorageBus;
        this.hoveredStorageBusPartitionSlot = renderContext.hoveredStorageBusPartitionSlot;
        this.hoveredStorageBusContentSlot = renderContext.hoveredStorageBusContentSlot;
        this.hoveredClearButtonStorageBus = renderContext.hoveredClearButtonStorageBus;
        this.hoveredIOModeButtonStorageBus = renderContext.hoveredIOModeButtonStorageBus;
        this.hoveredPartitionAllButtonStorageBus = renderContext.hoveredPartitionAllButtonStorageBus;

        // Cell partition button hover state
        this.hoveredPartitionAllButtonCell = renderContext.hoveredPartitionAllButtonCell;
        this.hoveredClearPartitionButtonCell = renderContext.hoveredClearPartitionButtonCell;

        this.partitionSlotTargets.clear();
        for (RenderContext.PartitionSlotTarget target : renderContext.partitionSlotTargets) {
            this.partitionSlotTargets.add(new PartitionSlotTarget(
                target.cell, target.slotIndex, target.x, target.y, target.width, target.height));
        }

        // Storage bus partition slot targets for JEI
        this.storageBusPartitionSlotTargets.clear();
        for (RenderContext.StorageBusPartitionSlotTarget target : renderContext.storageBusPartitionSlotTargets) {
            this.storageBusPartitionSlotTargets.add(new StorageBusPartitionSlotTarget(
                target.storageBus, target.slotIndex, target.x, target.y, target.width, target.height));
        }

        // Detect hovered upgrade icon (absolute mouse coords)
        int absMouseX = relMouseX + this.guiLeft;
        int absMouseY = relMouseY + this.guiTop;
        this.hoveredUpgradeIcon = null;

        for (RenderContext.UpgradeIconTarget target : renderContext.upgradeIconTargets) {
            if (target.isMouseOver(absMouseX, absMouseY)) {
                this.hoveredUpgradeIcon = target;
                break;
            }
        }
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

            @Override
            public ItemStack getStorageBusIcon() {
                return tabIconStorageBus;
            }

            @Override
            public ItemStack getInventoryIcon() {
                return tabIconInventory;
            }

            @Override
            public ItemStack getPartitionIcon() {
                return tabIconPartition;
            }
        };

        this.hoveredTab = TabRenderingHandler.drawTabs(ctx, iconProvider).hoveredTab;
    }

    protected ItemStack getTabIcon(int tab) {
        switch (tab) {
            case TAB_TERMINAL:
                return tabIconTerminal;
            case TAB_INVENTORY:
                return tabIconInventory;
            case TAB_PARTITION:
                return tabIconPartition;
            case TAB_STORAGE_BUS_INVENTORY:
            case TAB_STORAGE_BUS_PARTITION:
                // These use composite icons, but return storage bus as fallback
                return tabIconStorageBus;
            case TAB_NETWORK_TOOLS:
                return tabIconNetworkTool;
            default:
                return ItemStack.EMPTY;
        }
    }

    @Override
    protected void handleMouseClick(Slot slot, int slotIdx, int mouseButton, ClickType clickType) {
        // Intercept shift-clicks on upgrade items in player inventory to insert into cells or storage buses
        if (clickType == ClickType.QUICK_MOVE && slot != null && slot.getHasStack()) {
            ItemStack slotStack = slot.getStack();

            if (slotStack.getItem() instanceof IUpgradeModule) {
                // Check if upgrade insertion is enabled
                if (CellTerminalServerConfig.isInitialized()
                        && !CellTerminalServerConfig.getInstance().isUpgradeInsertEnabled()) {
                    MessageHelper.error("cellterminal.error.upgrade_insert_disabled");

                    return;
                }

                // On storage bus tabs, try to insert into storage buses
                if (currentTab == TAB_STORAGE_BUS_INVENTORY || currentTab == TAB_STORAGE_BUS_PARTITION) {
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
            Renameable hoveredTarget = getHoveredRenameable(mouseX - guiLeft);
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

        // Handle upgrade icon clicks (extraction)
        if (mouseButton == 0 && handleUpgradeIconClick()) return;

        if (mouseButton == 0 && handleUpgradeClick(mouseX, mouseY)) return;

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
            Renameable target = getHoveredRenameable(relMouseX);

            if (target != null && target.isRenameable()
                    && relMouseY >= GuiConstants.CONTENT_START_Y
                    && relMouseY < GuiConstants.CONTENT_START_Y + rowsVisible * ROW_HEIGHT) {
                int row = (relMouseY - GuiConstants.CONTENT_START_Y) / ROW_HEIGHT;
                int rowY = GuiConstants.CONTENT_START_Y + row * ROW_HEIGHT;

                // Determine X position and right edge based on target type and current tab
                int renameX = getRenameFieldX(target);
                int renameRightEdge = getRenameFieldRightEdge(target);
                inlineRenameEditor.startEditing(target, rowY, renameX, renameRightEdge);

                return;
            }
        }

        if (clickHandler.handleTabClick(mouseX, mouseY, guiLeft, guiTop, currentTab, createClickCallback())) return;

        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (currentTab == TAB_TERMINAL) {
            clickHandler.handleTerminalTabClick(mouseX, mouseY, mouseButton, guiLeft, guiTop,
                rowsVisible, getScrollBar().getCurrentScroll(), dataManager.getLines(),
                dataManager.getStorageMap(), dataManager.getTerminalDimension(), createClickCallback());
        } else if (currentTab == TAB_INVENTORY || currentTab == TAB_PARTITION) {
            if (handleCellPartitionButtonClick(mouseButton)) return;

            int relMouseX = mouseX - guiLeft;
            clickHandler.handleCellTabClick(currentTab, relMouseX, hoveredCellCell, hoveredContentSlotIndex,
                hoveredPartitionCell, hoveredPartitionSlotIndex, hoveredCellStorage, hoveredCellSlotIndex,
                hoveredStorageLine, hoveredLineIndex, dataManager.getStorageMap(), dataManager.getTerminalDimension(), createClickCallback());
        } else if (currentTab == TAB_STORAGE_BUS_INVENTORY || currentTab == TAB_STORAGE_BUS_PARTITION) {
            handleStorageBusTabClick(mouseX, mouseY, mouseButton);
        } else if (currentTab == TAB_NETWORK_TOOLS) {
            handleNetworkToolsTabClick(mouseButton);
        }
    }

    /**
     * Handle clicks on the Network Tools tab.
     */
    protected void handleNetworkToolsTabClick(int mouseButton) {
        if (mouseButton != 0) return;

        // Handle launch button click
        RenderContext ctx = getRenderContext();
        if (ctx.hoveredNetworkToolLaunchButton != null) {
            showNetworkToolConfirmation(ctx.hoveredNetworkToolLaunchButton);
        }
    }

    /**
     * Handle partition-all and clear-partition button clicks in Tabs 2 and 3.
     */
    protected boolean handleCellPartitionButtonClick(int mouseButton) {
        if (mouseButton != 0) return false;

        if (currentTab == TAB_INVENTORY && hoveredPartitionAllButtonCell != null) {
            CellTerminalNetwork.INSTANCE.sendToServer(new PacketPartitionAction(
                hoveredPartitionAllButtonCell.getParentStorageId(), hoveredPartitionAllButtonCell.getSlot(),
                PacketPartitionAction.Action.SET_ALL_FROM_CONTENTS));
            return true;
        }

        if (currentTab == TAB_PARTITION && hoveredClearPartitionButtonCell != null) {
            CellTerminalNetwork.INSTANCE.sendToServer(new PacketPartitionAction(
                hoveredClearPartitionButtonCell.getParentStorageId(), hoveredClearPartitionButtonCell.getSlot(),
                PacketPartitionAction.Action.CLEAR_ALL));
            return true;
        }

        return false;
    }

    /**
     * Handle click events on storage bus tabs.
     */
    protected void handleStorageBusTabClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && handleStorageBusUpgradeClick()) return;

        int relMouseX = mouseX - guiLeft;
        StorageBusClickHandler.ClickContext ctx = StorageBusClickHandler.ClickContext.from(
            currentTab, relMouseX, hoveredStorageBus, hoveredStorageBusPartitionSlot, hoveredStorageBusContentSlot,
            hoveredClearButtonStorageBus, hoveredIOModeButtonStorageBus, hoveredPartitionAllButtonStorageBus,
            hoveredContentStack, selectedStorageBusIds, dataManager.getStorageBusMap());

        if (storageBusClickHandler.handleClick(ctx, mouseButton)) {
            rebuildAndUpdateScrollbar();
        }
    }

    protected TerminalClickHandler.Callback createClickCallback() {
        return new TerminalClickHandler.Callback() {
            @Override
            public void onTabChanged(int tab) {
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

            @Override
            public void onStorageToggle(StorageInfo storage) {
                rebuildAndUpdateScrollbar();
            }

            @Override
            public void openInventoryPopup(CellInfo cell, int mouseX, int mouseY) {
                inventoryPopup = new PopupCellInventory(GuiCellTerminalBase.this, cell, mouseX, mouseY);
            }

            @Override
            public void openPartitionPopup(CellInfo cell, int mouseX, int mouseY) {
                partitionPopup = new PopupCellPartition(GuiCellTerminalBase.this, cell, mouseX, mouseY);
            }

            @Override
            public void onTogglePartitionItem(CellInfo cell, ItemStack stack) {
                GuiCellTerminalBase.this.onTogglePartitionItem(cell, stack);
            }

            @Override
            public void onAddPartitionItem(CellInfo cell, int slotIndex, ItemStack stack) {
                GuiCellTerminalBase.this.onAddPartitionItem(cell, slotIndex, stack);
            }

            @Override
            public void onRemovePartitionItem(CellInfo cell, int slotIndex) {
                GuiCellTerminalBase.this.onRemovePartitionItem(cell, slotIndex);
            }
        };
    }

    protected void rebuildAndUpdateScrollbar() {
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

        if (btn == subnetVisibilityButton) {
            // Cycle subnet visibility mode and persist it
            currentSubnetVisibility = subnetVisibilityButton.cycleMode();
            CellTerminalClientConfig.getInstance().setSubnetVisibility(currentSubnetVisibility);

            return;
        }

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

        // Handle network tool confirmation modal first (blocks all other input)
        if (networkToolModal != null) {
            if (networkToolModal.handleKeyTyped(keyCode)) return;
        }

        // Handle modal search bar keyboard first
        if (modalSearchBar != null && modalSearchBar.isVisible()) {
            if (modalSearchBar.handleKeyTyped(typedChar, keyCode)) return;
        }

        // Handle priority field keyboard first
        if (priorityFieldManager != null) {
            if (priorityFieldManager.handleKeyTyped(typedChar, keyCode)) return;
        }

        // Esc key should close modals first
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

        // Backspace key toggles subnet overview
        if (keyCode == Keyboard.KEY_BACK) {
            handleSubnetBackButtonClick();
            return;
        }

        // Delegate keybind handling to the active tab controller (only when search is not focused)
        if (this.searchField == null || !this.searchField.isFocused()) {
            if (handleTabKeyTyped(keyCode)) return;
        }

        // Handle search field keyboard input first
        if (this.searchField != null && this.searchField.textboxKeyTyped(typedChar, keyCode)) return;

        super.keyTyped(typedChar, keyCode);
    }

    /**
     * Delegate key press to the active tab controller.
     * @return true if the key was handled
     */
    protected boolean handleTabKeyTyped(int keyCode) {
        ITabController controller = TabControllerRegistry.getController(currentTab);
        if (controller == null) return false;

        TabContext tabContext = createTabContext();

        // Special handling for Partition tab (needs scrollToLine callback)
        if (currentTab == TAB_PARTITION) {
            return ((PartitionTabController) controller).handleKeyTyped(keyCode, tabContext);
        }

        // Special handling for Storage Bus Partition tab (needs access to selected bus IDs)
        if (currentTab == TAB_STORAGE_BUS_PARTITION) {
            if (!KeyBindings.ADD_TO_STORAGE_BUS.isActiveAndMatches(keyCode)) return false;

            return StorageBusPartitionTabController.handleAddToStorageBusKeybind(
                selectedStorageBusIds, getSlotUnderMouse(), dataManager.getStorageBusMap());
        }

        return controller.handleKeyTyped(keyCode, tabContext);
    }

    /**
     * Create a TabContext for the current GUI state.
     */
    protected TabContext createTabContext() {
        return new TabContext(dataManager, new TabContext.TabContextCallback() {
            @Override public void onStorageToggle(StorageInfo storage) {
                rebuildAndUpdateScrollbar();
            }

            @Override public void openInventoryPopup(CellInfo cell, int mouseX, int mouseY) {
                inventoryPopup = new PopupCellInventory(GuiCellTerminalBase.this, cell, mouseX, mouseY);
            }

            @Override public void openPartitionPopup(CellInfo cell, int mouseX, int mouseY) {
                partitionPopup = new PopupCellPartition(GuiCellTerminalBase.this, cell, mouseX, mouseY);
            }

            @Override public void onTogglePartitionItem(CellInfo cell, ItemStack stack) {
                GuiCellTerminalBase.this.onTogglePartitionItem(cell, stack);
            }

            @Override public void onAddPartitionItem(CellInfo cell, int slotIndex, ItemStack stack) {
                GuiCellTerminalBase.this.onAddPartitionItem(cell, slotIndex, stack);
            }

            @Override public void onRemovePartitionItem(CellInfo cell, int slotIndex) {
                GuiCellTerminalBase.this.onRemovePartitionItem(cell, slotIndex);
            }

            @Override public void scrollToLine(int lineIndex) {
                GuiCellTerminalBase.this.scrollToLine(lineIndex);
            }
        }, dataManager.getTerminalDimension());
    }

    /**
     * Handle upgrade icon click to extract upgrades from cells or storage buses.
     * @return true if an upgrade icon click was handled
     */
    protected boolean handleUpgradeIconClick() {
        if (hoveredUpgradeIcon == null) return false;

        // Check if upgrade extraction is enabled
        if (CellTerminalServerConfig.isInitialized()
                && !CellTerminalServerConfig.getInstance().isUpgradeExtractEnabled()) {
            MessageHelper.error("cellterminal.error.upgrade_extract_disabled");

            return true;  // Consume click to prevent other handlers
        }

        boolean toInventory = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        if (hoveredUpgradeIcon.cell != null) {
            CellTerminalNetwork.INSTANCE.sendToServer(PacketExtractUpgrade.forCell(
                hoveredUpgradeIcon.cell.getParentStorageId(),
                hoveredUpgradeIcon.cell.getSlot(),
                hoveredUpgradeIcon.upgradeIndex,
                toInventory
            ));
        } else if (hoveredUpgradeIcon.storageBus != null) {
            CellTerminalNetwork.INSTANCE.sendToServer(PacketExtractUpgrade.forStorageBus(
                hoveredUpgradeIcon.storageBus.getId(),
                hoveredUpgradeIcon.upgradeIndex,
                toInventory
            ));
        } else {
            return false;
        }

        return true;
    }

    /**
     * Handle upgrade click when player is holding an upgrade item.
     */
    protected boolean handleUpgradeClick(int mouseX, int mouseY) {
        UpgradeClickHandler.UpgradeClickContext ctx = new UpgradeClickHandler.UpgradeClickContext(
            currentTab, hoveredCellCell, hoveredCellStorage, hoveredCellSlotIndex,
            hoveredStorageLine, hoveredStorageBus, dataManager);

        return UpgradeClickHandler.handleUpgradeClick(ctx);
    }

    /**
     * Handle upgrade click on storage bus headers when player is holding an upgrade item.
     * @return true if an upgrade click was handled
     */
    protected boolean handleStorageBusUpgradeClick() {
        return UpgradeClickHandler.handleStorageBusUpgradeClick(hoveredStorageBus);
    }

    /**
     * Find the first visible cell that can accept the given upgrade.
     * Respects current tab's filtered and sorted line list.
     * Checks both compatibility (upgrade type supported by cell) and available space.
     * @param upgradeStack The upgrade item to check compatibility with
     * @return The first CellInfo that can accept the upgrade, or null if none found
     */
    protected CellInfo findFirstVisibleCellThatCanAcceptUpgrade(ItemStack upgradeStack) {
        UpgradeClickHandler.UpgradeClickContext ctx = new UpgradeClickHandler.UpgradeClickContext(
            currentTab, hoveredCellCell, hoveredCellStorage, hoveredCellSlotIndex,
            hoveredStorageLine, hoveredStorageBus, dataManager);

        return UpgradeClickHandler.findFirstVisibleCellThatCanAcceptUpgrade(ctx, upgradeStack);
    }

    /**
     * Find the first cell in a specific storage that can accept the given upgrade.
     * @param storage The storage to search in
     * @param upgradeStack The upgrade item to check compatibility with
     * @return The first CellInfo that can accept the upgrade, or null if none found
     */
    protected CellInfo findFirstCellInStorageThatCanAcceptUpgrade(StorageInfo storage, ItemStack upgradeStack) {
        return UpgradeClickHandler.findFirstCellInStorageThatCanAcceptUpgrade(storage, upgradeStack);
    }

    /**
     * Find the first visible storage bus that can accept the given upgrade.
     * Only used on storage bus tabs.
     * @param upgradeStack The upgrade item to check compatibility with
     * @return The first StorageBusInfo that can accept the upgrade, or null if none found
     */
    protected StorageBusInfo findFirstVisibleStorageBusThatCanAcceptUpgrade(ItemStack upgradeStack) {
        UpgradeClickHandler.UpgradeClickContext ctx = new UpgradeClickHandler.UpgradeClickContext(
            currentTab, hoveredCellCell, hoveredCellStorage, hoveredCellSlotIndex,
            hoveredStorageLine, hoveredStorageBus, dataManager);

        return UpgradeClickHandler.findFirstVisibleStorageBusThatCanAcceptUpgrade(ctx, upgradeStack);
    }

    /**
     * Scroll to a specific line index.
     */
    protected void scrollToLine(int lineIndex) {
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
    protected void sendRenamePacket(Renameable target, String newName) {
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
     * Get the currently hovered renameable target based on the current tab and hover state.
     * Returns null if nothing renameable is hovered, or if the click position overlaps buttons.
     *
     * @param relMouseX Mouse X relative to GUI left edge
     * @return The hovered Renameable, or null
     */
    protected Renameable getHoveredRenameable(int relMouseX) {
        // Storage header is renameable on tabs 0-2 (exclude expand button area at x >= 165)
        if (hoveredStorageLine != null && relMouseX < GuiConstants.BUTTON_PARTITION_X) return hoveredStorageLine;

        // On terminal tab, cells show name text — rename if not in button area (E/I/P at x >= 135)
        if (currentTab == TAB_TERMINAL && hoveredCellCell != null && relMouseX < GuiConstants.BUTTON_EJECT_X) return hoveredCellCell;

        // On inventory/partition tabs, cells are icon+content rows — rename if not on content/partition slots
        if ((currentTab == TAB_INVENTORY || currentTab == TAB_PARTITION)
                && hoveredCellCell != null
                && hoveredContentSlotIndex < 0
                && hoveredPartitionSlotIndex < 0) {
            return hoveredCellCell;
        }

        // Storage bus is renameable on tabs 3-4 (exclude IO mode button at x >= 115)
        if (hoveredStorageBus != null && relMouseX < GuiConstants.BUTTON_IO_MODE_X) return hoveredStorageBus;

        return null;
    }

    /**
     * Get the X position for the inline rename field based on the target type.
     * The field has 2px internal padding, so we offset by -2 to align the text
     * with the original name position.
     * Cells are further indented (on a tree branch), while storages and buses start at the icon + 20.
     */
    protected int getRenameFieldX(Renameable target) {
        // Subtract 2 so the text inside the field (at x + 2) aligns with where the name was drawn
        if (target instanceof CellInfo) return GuiConstants.CELL_INDENT + 18 - 2;

        // StorageInfo and StorageBusInfo: name drawn at GUI_INDENT + 20
        return GuiConstants.GUI_INDENT + 20 - 2;
    }

    /**
     * Get the right edge for the inline rename field based on the target type and current tab.
     * Each tab has different buttons at the right side, so the field must stop before them.
     */
    protected int getRenameFieldRightEdge(Renameable target) {
        // Storage buses (tabs 3-4) have IO mode button at BUTTON_IO_MODE_X (115)
        if (target instanceof StorageBusInfo) return GuiConstants.BUTTON_IO_MODE_X - 4;

        // Cells on terminal tab (tab 0) have E/I/P buttons starting at BUTTON_EJECT_X (135)
        if (target instanceof CellInfo && currentTab == TAB_TERMINAL) return GuiConstants.BUTTON_EJECT_X - 4;

        // Storage headers and cells on tabs 1-2 have priority field at CONTENT_RIGHT_EDGE - FIELD_WIDTH - RIGHT_MARGIN
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
        ITabController controller = TabControllerRegistry.getController(currentTab);
        int lineCount = (controller != null)
            ? controller.getLineCount(createTabContext())
            : dataManager.getLineCount(currentTab);
        this.getScrollBar().setRange(0, Math.max(0, lineCount - this.rowsVisible), 1);
    }

    // Callbacks from popups

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

    // JEI Ghost Ingredient support

    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        return JeiGhostHandler.getPhantomTargets(currentTab, partitionPopup, partitionSlotTargets,
            storageBusPartitionSlotTargets,
            (cell, slotIndex, stack) -> onAddPartitionItem(cell, slotIndex, stack),
            (storageBus, slotIndex, stack) -> onAddStorageBusPartitionItem(storageBus, slotIndex, stack));
    }

    // Accessors for popups and renderers

    public Map<Long, StorageInfo> getStorageMap() {
        return dataManager.getStorageMap();
    }

    public RenderContext getRenderContext() {
        return renderContext;
    }

    /**
     * Create a ToolContext for the Network Tools tab.
     */
    protected INetworkTool.ToolContext createNetworkToolContext() {
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
}
