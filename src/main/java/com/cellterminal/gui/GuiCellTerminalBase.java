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
import com.cellterminal.client.CellTerminalClientConfig;
import com.cellterminal.client.CellTerminalClientConfig.TerminalStyle;
import com.cellterminal.client.KeyBindings;
import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.client.StorageInfo;
import com.cellterminal.gui.handler.JeiGhostHandler;
import com.cellterminal.gui.handler.JeiGhostHandler.PartitionSlotTarget;
import com.cellterminal.gui.handler.JeiGhostHandler.StorageBusPartitionSlotTarget;
import com.cellterminal.gui.handler.StorageBusClickHandler;
import com.cellterminal.gui.handler.TabRenderingHandler;
import com.cellterminal.gui.handler.TerminalClickHandler;
import com.cellterminal.gui.handler.TerminalDataManager;
import com.cellterminal.gui.handler.TooltipHandler;
import com.cellterminal.gui.handler.UpgradeClickHandler;
import com.cellterminal.gui.overlay.OverlayMessageRenderer;
import com.cellterminal.gui.render.InventoryTabRenderer;
import com.cellterminal.gui.render.PartitionTabRenderer;
import com.cellterminal.gui.render.RenderContext;
import com.cellterminal.gui.render.StorageBusInventoryTabRenderer;
import com.cellterminal.gui.render.StorageBusPartitionTabRenderer;
import com.cellterminal.gui.render.TerminalTabRenderer;
import com.cellterminal.gui.tab.ITabController;
import com.cellterminal.gui.tab.PartitionTabController;
import com.cellterminal.gui.tab.StorageBusPartitionTabController;
import com.cellterminal.gui.tab.TabContext;
import com.cellterminal.gui.tab.TabControllerRegistry;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketExtractUpgrade;
import com.cellterminal.network.PacketPartitionAction;
import com.cellterminal.network.PacketStorageBusPartitionAction;
import com.cellterminal.network.PacketTabChange;
import com.cellterminal.network.PacketUpgradeCell;
import com.cellterminal.network.PacketUpgradeStorageBus;


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
    private static final int TAB_COUNT = 5;
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
    protected SearchFilterMode currentSearchMode = SearchFilterMode.MIXED;

    // Current tab
    protected int currentTab;

    // Tab icons (lazy initialized)
    protected ItemStack tabIconTerminal = null;
    protected ItemStack tabIconInventory = null;
    protected ItemStack tabIconPartition = null;
    protected ItemStack tabIconStorageBus = null;  // Storage bus icon for composite rendering

    // Popup states
    protected PopupCellInventory inventoryPopup = null;
    protected PopupCellPartition partitionPopup = null;
    protected CellInfo hoveredCell = null;
    protected int hoverType = 0; // 0=none, 1=inventory, 2=partition, 3=eject
    protected StorageInfo hoveredStorageLine = null; // Storage header being hovered

    // Priority field manager (inline editable fields)
    protected PriorityFieldManager priorityFieldManager = null;

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
    protected long lastSearchFieldClickTime = 0;
    protected static final long DOUBLE_CLICK_THRESHOLD = 500;  // ms

    // Hovered upgrade icon for tooltip and extraction
    protected RenderContext.UpgradeIconTarget hoveredUpgradeIcon = null;

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
        if (this.currentTab < 0 || this.currentTab >= TAB_COUNT) this.currentTab = TAB_TERMINAL;
        this.currentSearchMode = config.getSearchMode();
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

    protected abstract String getGuiTitle();

    @Override
    public void initGui() {
        // Recalculate rows based on screen size and terminal style
        this.rowsVisible = calculateRowsCount();
        this.ySize = MAGIC_HEIGHT_NUMBER + this.rowsVisible * ROW_HEIGHT;

        super.initGui();

        // Center GUI with appropriate spacing for tall mode
        int unusedSpace = this.height - this.ySize;
        if (unusedSpace < 0) {
            // GUI is larger than screen - push it up so bottom content is more visible
            this.guiTop = (int) Math.floor(unusedSpace / 3.8f);
        } else {
            // GUI fits on screen - position it to extend to the bottom with minimal margin
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
        initFilterButtons();
        initSearchField();
        initPriorityFieldManager();

        // Apply persisted filter states to data manager
        applyFiltersToDataManager();

        // Update scrollbar range for current tab
        updateScrollbarForCurrentTab();

        // Notify server of the current tab so it can start sending appropriate data
        // This is especially important for storage bus tabs which require server polling
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketTabChange(currentTab));
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

        // Render modal search bar on top of everything else
        if (modalSearchBar != null && modalSearchBar.isVisible()) modalSearchBar.draw(mouseX, mouseY);

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
        }

        // Update priority field positions based on visible storages
        if (priorityFieldManager != null) {
            for (RenderContext.VisibleStorageEntry entry : renderContext.visibleStorages) {
                PriorityFieldManager.PriorityField field = priorityFieldManager.getOrCreateField(
                    entry.storage, guiLeft, guiTop);
                priorityFieldManager.updateFieldPosition(field, entry.y, guiLeft, guiTop);
            }

            // Update storage bus priority field positions for tabs 4 and 5
            for (RenderContext.VisibleStorageBusEntry entry : renderContext.visibleStorageBuses) {
                PriorityFieldManager.StorageBusPriorityField field = priorityFieldManager.getOrCreateStorageBusField(
                    entry.storageBus, guiLeft, guiTop);
                priorityFieldManager.updateStorageBusFieldPosition(field, entry.y, guiLeft, guiTop);
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
        TabRenderingHandler.ControlsHelpContext ctx = new TabRenderingHandler.ControlsHelpContext(
            this.guiLeft, this.guiTop, this.ySize, this.height, currentTab, this.fontRenderer, style);

        TabRenderingHandler.ControlsHelpResult result = TabRenderingHandler.drawControlsHelpWidget(ctx);
        this.cachedControlsHelpLines = result.wrappedLines;
        this.cachedControlsHelpTab = result.cachedTab;

        // Update filter button positions after controls help bounds are known
        updateFilterButtonPositions();
    }

    /**
     * Get the bounding rectangle for the controls help widget.
     * Uses cached wrapped lines from the last render for accurate sizing.
     * Used for JEI exclusion areas.
     */
    protected Rectangle getControlsHelpBounds() {
        // Use cached lines if available and for the current tab
        if (cachedControlsHelpLines.isEmpty() || cachedControlsHelpTab != currentTab) {
            return new Rectangle(0, 0, 0, 0);
        }

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
            TAB_COUNT, TAB_WIDTH, TAB_HEIGHT, TAB_Y_OFFSET,
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
            default:
                return ItemStack.EMPTY;
        }
    }

    @Override
    protected void handleMouseClick(Slot slot, int slotIdx, int mouseButton, ClickType clickType) {
        // Intercept shift-clicks on upgrade items in player inventory to insert into cells
        if (clickType == ClickType.QUICK_MOVE && slot != null && slot.getHasStack()) {
            ItemStack slotStack = slot.getStack();

            if (slotStack.getItem() instanceof IUpgradeModule) {
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

        super.handleMouseClick(slot, slotIdx, mouseButton, clickType);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // Handle modal search bar clicks first
        if (modalSearchBar != null && modalSearchBar.isVisible()) {
            if (modalSearchBar.handleMouseClick(mouseX, mouseY, mouseButton)) return;
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

        if (clickHandler.handleTabClick(mouseX, mouseY, guiLeft, guiTop, currentTab, createClickCallback())) return;

        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (currentTab == TAB_TERMINAL) {
            clickHandler.handleTerminalTabClick(mouseX, mouseY, mouseButton, guiLeft, guiTop,
                rowsVisible, getScrollBar().getCurrentScroll(), dataManager.getLines(),
                dataManager.getStorageMap(), dataManager.getTerminalDimension(), createClickCallback());
        } else if (currentTab == TAB_INVENTORY || currentTab == TAB_PARTITION) {
            if (handleCellPartitionButtonClick(mouseButton)) return;

            clickHandler.handleCellTabClick(currentTab, hoveredCellCell, hoveredContentSlotIndex,
                hoveredPartitionCell, hoveredPartitionSlotIndex, hoveredCellStorage, hoveredCellSlotIndex,
                hoveredStorageLine, hoveredLineIndex, dataManager.getStorageMap(), dataManager.getTerminalDimension(), createClickCallback());
        } else if (currentTab == TAB_STORAGE_BUS_INVENTORY || currentTab == TAB_STORAGE_BUS_PARTITION) {
            handleStorageBusTabClick(mouseX, mouseY, mouseButton);
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

        StorageBusClickHandler.ClickContext ctx = StorageBusClickHandler.ClickContext.from(
            currentTab, hoveredStorageBus, hoveredStorageBusPartitionSlot, hoveredStorageBusContentSlot,
            hoveredClearButtonStorageBus, hoveredIOModeButtonStorageBus, hoveredPartitionAllButtonStorageBus,
            hoveredContentStack, selectedStorageBusIds, dataManager.getStorageBusMap());

        storageBusClickHandler.handleClick(ctx, mouseButton);
    }

    protected TerminalClickHandler.Callback createClickCallback() {
        return new TerminalClickHandler.Callback() {
            @Override
            public void onTabChanged(int tab) {
                currentTab = tab;
                getScrollBar().setRange(0, 0, 1);
                updateScrollbarForCurrentTab();
                updateSearchModeButtonVisibility();
                initFilterButtons();  // Reinitialize filter buttons for new tab
                applyFiltersToDataManager();
                onSearchTextChanged();  // Reapply filter with tab-specific mode

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
            terminalStyleButton.setStyle(CellTerminalClientConfig.getInstance().cycleTerminalStyle());
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

        // Handle filter button clicks
        if (btn instanceof GuiFilterButton) {
            GuiFilterButton filterBtn = (GuiFilterButton) btn;
            if (filterPanelManager.handleClick(filterBtn)) {
                applyFiltersToDataManager();
                rebuildAndUpdateScrollbar();

                return;
            }
        }

        super.actionPerformed(btn);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Handle modal search bar keyboard first
        if (modalSearchBar != null && modalSearchBar.isVisible()) {
            if (modalSearchBar.handleKeyTyped(typedChar, keyCode)) return;
        }

        // Handle priority field keyboard first
        if (priorityFieldManager != null && priorityFieldManager.handleKeyTyped(typedChar, keyCode)) {
            return;
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
        dataManager.processUpdate(data);
        updateScrollbarForCurrentTab();
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
}
