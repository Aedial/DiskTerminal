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
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import appeng.api.implementations.items.IUpgradeModule;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.AppEngSlot;

import mezz.jei.api.gui.IGhostIngredientHandler;

import com.cellterminal.client.CellInfo;
import com.cellterminal.client.SubnetVisibility;
import com.cellterminal.client.TabStateManager;
import com.cellterminal.config.CellTerminalClientConfig;
import com.cellterminal.config.CellTerminalClientConfig.TerminalStyle;
import com.cellterminal.container.ContainerCellTerminalBase;
import com.cellterminal.client.CellContentRow;
import com.cellterminal.client.KeyBindings;
import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.client.StorageBusContentRow;
import com.cellterminal.client.StorageInfo;
import com.cellterminal.config.CellTerminalServerConfig;
import com.cellterminal.gui.buttons.*;
import com.cellterminal.gui.handler.TabManager;
import com.cellterminal.gui.handler.TabRenderingHandler;
import com.cellterminal.gui.handler.TerminalDataManager;
import com.cellterminal.gui.handler.TooltipHandler;
import com.cellterminal.gui.networktools.INetworkTool;
import com.cellterminal.gui.networktools.GuiToolConfirmationModal;
import com.cellterminal.gui.overlay.MessageHelper;
import com.cellterminal.gui.overlay.OverlayMessageRenderer;
import com.cellterminal.gui.widget.tab.AbstractTabWidget;
import com.cellterminal.gui.widget.tab.NetworkToolsTabWidget;
import com.cellterminal.gui.widget.tab.SubnetOverviewTabWidget;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketHighlightBlock;
import com.cellterminal.network.PacketSwitchNetwork;
import com.cellterminal.network.PacketSlotLimitChange;
import com.cellterminal.network.PacketTabChange;
import com.cellterminal.gui.rename.InlineRenameManager;


/**
 * Base GUI for Cell Terminal variants.
 * Contains shared functionality for displaying storage drives/chests with their cells.
 * Supports three tabs: Terminal (list view), Inventory (cell slots with contents), Partition (cell slots with partition).
 */
public abstract class GuiCellTerminalBase extends AEBaseGui implements IJEIGhostIngredients, NetworkToolsTabWidget.NetworkToolGuiContext, SubnetOverviewTabWidget.SubnetOverviewContext, TabManager.TabSwitchListener {

    // Layout constants
    protected static final int ROW_HEIGHT = GuiConstants.ROW_HEIGHT;
    protected static final int MIN_ROWS = GuiConstants.MIN_ROWS;
    protected static final int DEFAULT_ROWS = GuiConstants.DEFAULT_ROWS;

    // Magic height number for tall mode calculation (header + footer heights)
    private static final int MAGIC_HEIGHT_NUMBER = GuiConstants.MAGIC_HEIGHT_NUMBER;

    // Dynamic row count (computed based on terminal style)
    protected int rowsVisible = DEFAULT_ROWS;

    // Tab management (widget lifecycle, rendering, clicking, switching)
    protected TabManager tabManager;

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

    // Popup states
    protected PopupCellInventory inventoryPopup = null;
    protected PopupCellPartition partitionPopup = null;

    // Storage bus and temp area selection tracking (for quick-add keybinds)
    protected final Set<Long> selectedStorageBusIds = new HashSet<>();
    protected final Set<Integer> selectedTempCellSlots = new HashSet<>();

    // Modal search bar for expanded editing
    protected GuiModalSearchBar modalSearchBar = null;

    // Search field click handler (right-click clear, double-click modal)
    protected SearchFieldHandler searchFieldHandler = null;

    // Guard to prevent style button from being retriggered while mouse is still down
    private long lastStyleButtonClickTime = 0;
    private static final long STYLE_BUTTON_COOLDOWN = 100;  // ms

    // Whether we've restored the saved scroll after the first data update
    private boolean initialScrollRestored = false;

    // Subnet view state (network routing, kept here since it affects data updates)
    protected long currentNetworkId = 0;  // 0 = main network, >0 = subnet ID
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
        this.tabManager = new TabManager(config.getSelectedTab(), this);
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

        // Apply persisted filter states to data manager
        applyFiltersToDataManager();

        // Update scrollbar range for current tab
        updateScrollbarForCurrentTab();

        // Restore saved scroll position for the current tab (in-memory TabStateManager)
        TabStateManager.TabType initialTabType = TabStateManager.TabType.fromIndex(tabManager.getCurrentTab());
        int initialSavedScroll = TabStateManager.getInstance().getScrollPosition(initialTabType);
        scrollToLine(initialSavedScroll);

        // We'll also re-apply the saved scroll once when data arrives, in case
        // the initial scrollbar range was limited during initGui().
        this.initialScrollRestored = false;

        // Notify server of the current tab so it can start sending appropriate data
        // This is especially important for storage bus tabs which require server polling
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketTabChange(tabManager.getCurrentTab()));

        // Send current slot limit preferences to server
        CellTerminalClientConfig config = CellTerminalClientConfig.getInstance();
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketSlotLimitChange(
            config.getCellSlotLimit().getLimit(),
            config.getBusSlotLimit().getLimit(),
            config.getSubnetSlotLimit().getLimit()
        ));

        // If a subnet was previously being viewed, tell the server to switch to it
        // Also reset data manager to avoid showing stale data from a previous session
        if (this.currentNetworkId != 0) {
            this.awaitingNetworkSwitch = true;
            this.dataManager.resetForNetworkSwitch();
            CellTerminalNetwork.INSTANCE.sendToServer(new PacketSwitchNetwork(this.currentNetworkId));
        }
    }

    protected void initTabWidgets() {
        // Provide scroll access so TabManager can save/restore scroll positions on tab switch
        tabManager.setScrollAccessor(new TabManager.ScrollAccessor() {
            @Override
            public int getCurrentScroll() {
                return getScrollBar().getCurrentScroll();
            }

            @Override
            public void scrollToLine(int lineIndex) {
                GuiCellTerminalBase.this.scrollToLine(lineIndex);
            }
        });

        // Delegate widget creation and initialization to the TabManager
        tabManager.initWidgets(this.fontRenderer, this.itemRender,
            this.guiLeft, this.guiTop, this.rowsVisible, this);

        // Wire subnet context after widget init
        tabManager.getSubnetTab().setSubnetContext(this);
    }

    protected void initSubnetBackButton() {
        if (this.subnetBackButton != null) this.buttonList.remove(this.subnetBackButton);

        // Position the back button at the left side of the header, before the title
        int buttonX = this.guiLeft + 4;
        int buttonY = this.guiTop + 4;
        this.subnetBackButton = new GuiBackButton(3, buttonX, buttonY);
        this.subnetBackButton.setInOverviewMode(isInSubnetOverviewMode());
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
        nextButtonId = filterPanelManager.initButtons(this.buttonList, nextButtonId, tabManager.getCurrentTab());
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

        // Initialize modal search bar and search field handler
        this.modalSearchBar = new GuiModalSearchBar(this.fontRenderer, this.searchField, this::onSearchTextChanged);
        this.searchFieldHandler = new SearchFieldHandler(this.searchField, this.modalSearchBar);

        if (!existingSearch.isEmpty()) dataManager.setSearchFilter(existingSearch, getEffectiveSearchMode());
    }

    protected void updateSearchModeButtonVisibility() {
        if (this.searchModeButton == null) return;

        this.searchModeButton.visible = tabManager.isSearchModeButtonVisible();
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
     * Delegates to the TabManager.
     */
    protected SearchFilterMode getEffectiveSearchMode() {
        return tabManager.getEffectiveSearchMode(currentSearchMode);
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
        // TODO: we can assume that the popups are only open in the terminal tab, as outside click or Esc closes them
        if (tabManager.getCurrentTab() == GuiConstants.TAB_TERMINAL && inventoryPopup == null && partitionPopup == null) {
            CellInfo previewCell = tabManager.getTerminalWidget().getPreviewCell();
            int previewType = tabManager.getTerminalWidget().getPreviewType();

            if (previewCell != null) {
                int previewX = mouseX + 10, previewY = mouseY + 10;
                if (previewType == 1) new PopupCellInventory(this, previewCell, previewX, previewY).draw(mouseX, mouseY);
                else if (previewType == 2) new PopupCellPartition(this, previewCell, previewX, previewY).draw(mouseX, mouseY);
                else if (previewType == 3) this.drawHoveringText(Collections.singletonList(I18n.format("gui.cellterminal.eject_cell")), mouseX, mouseY);
            }
        }

        drawTooltips(mouseX, mouseY);

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
        AbstractTabWidget activeTab = tabManager.getActiveTab();
        if (activeTab != null) {
            // Get tooltip from hovered widget row
            List<String> widgetTooltip = activeTab.getTooltip(relMouseX, relMouseY);
            if (!widgetTooltip.isEmpty()) {
                this.drawHoveringText(widgetTooltip, mouseX, mouseY);

                return;
            }

            // Get hovered item stack for vanilla item tooltip
            ItemStack hoveredStack = activeTab.getHoveredItemStack(relMouseX, relMouseY);
            if (!hoveredStack.isEmpty()) {
                this.renderToolTip(hoveredStack, mouseX, mouseY);

                return;
            }
        }

        // Tab button tooltips (via TabManager)
        int hoveredTab = tabManager.getHoveredTab();
        if (hoveredTab >= 0 && inventoryPopup == null && partitionPopup == null) {
            String tabTooltip = tabManager.getTabTooltip(hoveredTab);
            if (!tabTooltip.isEmpty()) {
                this.drawHoveringText(Collections.singletonList(tabTooltip), mouseX, mouseY);

                return;
            }
        }

        // Legacy tooltip context for non-widget elements (buttons, search field)
        TooltipHandler.TooltipContext ctx = new TooltipHandler.TooltipContext();
        ctx.terminalStyleButton = terminalStyleButton;
        ctx.searchModeButton = searchModeButton;
        ctx.searchHelpButton = searchHelpButton;
        // ctx.subnetVisibilityButton = subnetVisibilityButton;
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

        // Reset priority field visibility before rendering (fields are re-registered by headers during draw)
        PriorityFieldManager.getInstance().resetVisibility();

        // Draw based on current tab using widgets
        AbstractTabWidget activeTab = tabManager.getActiveTab();
        boolean isSubnetTab = isInSubnetOverviewMode();
        if (activeTab != null) {
            activeTab.buildVisibleRows(tabManager.getActiveLines(dataManager), currentScroll);
            activeTab.draw(relMouseX, relMouseY);
        }

        // Priority fields and inline rename are only relevant for real tabs, not subnet overview
        if (!isSubnetTab) {
            // Draw priority fields (positioned by headers during their draw pass, rendered in absolute coords)
            PriorityFieldManager pfm = PriorityFieldManager.getInstance();
            pfm.drawFieldsRelative(guiLeft, guiTop);

            // Cleanup fields for storages/buses that no longer exist in the data
            Set<Long> activeIds = new HashSet<>(dataManager.getStorageMap().keySet());
            activeIds.addAll(dataManager.getStorageBusMap().keySet());
            pfm.cleanupStaleFields(activeIds);

            // Draw inline rename field overlay on top of everything else
            InlineRenameManager.getInstance().drawRenameField(this.fontRenderer);
        }

        // Draw controls help - delegate to tab controller
        drawControlsHelpForCurrentTab();
    }

    // Constants for controls help widget positioning
    // JEI buttons are at guiLeft - 18, with ~4px margin from screen edge
    // We position the panel to leave similar margins on both sides
    protected static final int CONTROLS_HELP_RIGHT_MARGIN = GuiConstants.CONTROLS_HELP_RIGHT_MARGIN;
    protected static final int CONTROLS_HELP_LEFT_MARGIN = GuiConstants.CONTROLS_HELP_LEFT_MARGIN;
    protected static final int CONTROLS_HELP_PADDING = GuiConstants.CONTROLS_HELP_PADDING;
    protected static final int CONTROLS_HELP_LINE_HEIGHT = GuiConstants.CONTROLS_HELP_LINE_HEIGHT;

    /**
     * Draw controls help for the current tab using the handler.
     * Works for both real tabs and subnet overlay (the active tab provides getHelpLines()).
     */
    protected void drawControlsHelpForCurrentTab() {
        int tabIndex = tabManager.getCurrentTab();

        TabRenderingHandler.ControlsHelpContext ctx = new TabRenderingHandler.ControlsHelpContext(
            this.guiLeft, this.guiTop, this.ySize, this.height, tabIndex, this.fontRenderer);

        // Get help lines from the active tab widget (works for subnet tab too)
        AbstractTabWidget activeTab = tabManager.getActiveTab();
        List<String> helpLines = activeTab != null ? activeTab.getHelpLines() : Collections.emptyList();

        TabRenderingHandler.ControlsHelpResult result = TabRenderingHandler.drawControlsHelpWidget(ctx, helpLines);
        tabManager.setCachedControlsHelp(result.wrappedLines, result.cachedTab);

        // Update filter button positions after controls help bounds are known
        updateFilterButtonPositions();
    }

    /**
     * Get the bounding rectangle for the controls help widget.
     * Uses cached wrapped lines from the last render for accurate sizing.
     * Used for JEI exclusion areas.
     */
    protected Rectangle getControlsHelpBounds() {
        List<String> wrappedLines = tabManager.getCachedControlsHelpLines();
        if (wrappedLines.isEmpty()) return new Rectangle(0, 0, 0, 0);

        int lineCount = wrappedLines.size();
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
        // TODO: move terminal style to the filterPanelManager
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

        tabManager.drawTabs(this.guiLeft, offsetX, offsetY, mouseX, mouseY, this.itemRender, this.mc);
        this.bindTexture("guis/bus.png");
        if (this.hasToolbox()) {
            this.drawTexturedModalRect(offsetX + this.xSize + 1, offsetY + this.ySize - 90, 178, 184 - 90, 68, 68);
        }
    }

    public boolean hasToolbox() {
        return ((ContainerCellTerminalBase) this.inventorySlots).hasToolbox();
    }

    @Override
    protected void handleMouseClick(Slot slot, int slotIdx, int mouseButton, ClickType clickType) {
        // Intercept shift-clicks on upgrade items in player inventory to insert into
        // the first visible cell/bus. Delegates to the active tab widget for tab-specific logic.
        if (clickType == ClickType.QUICK_MOVE && slot != null && slot.getHasStack()) {
            ItemStack slotStack = slot.getStack();

            if (slotStack.getItem() instanceof IUpgradeModule
                    && ((IUpgradeModule) slotStack.getItem()).getType(slotStack) != null) {
                // Check if upgrade insertion is enabled
                if (CellTerminalServerConfig.isInitialized()
                        && !CellTerminalServerConfig.getInstance().isUpgradeInsertEnabled()) {
                    MessageHelper.error("cellterminal.error.upgrade_insert_disabled");

                    return;
                }

                AbstractTabWidget activeTab = tabManager.getActiveTab();
                if (activeTab != null && activeTab.handleInventorySlotShiftClick(slotStack, slot.getSlotIndex())) {
                    return;
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

        // Handle inline rename: clicking outside the rename field saves and closes it
        // (does not consume the click, let it propagate to potentially start a new rename)
        InlineRenameManager.getInstance().handleClickOutside(mouseX - guiLeft, mouseY - guiTop);

        // Handle search field clicks (right-click clear, double-click modal, regular focus)
        if (this.searchFieldHandler != null && this.searchFieldHandler.handleClick(mouseX, mouseY, mouseButton)) return;

        // Handle priority field clicks (only visible in certain tabs)
        if (PriorityFieldManager.getInstance().handleClick(mouseX, mouseY, mouseButton)) return;

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

        // Handle tab header clicks via TabManager
        if (tabManager.handleClick(mouseX, mouseY, guiLeft, guiTop)) return;

        super.mouseClicked(mouseX, mouseY, mouseButton);

        // Delegate content area clicks to the active tab widget
        int relMouseX = mouseX - guiLeft;
        int relMouseY = mouseY - guiTop;
        AbstractTabWidget activeTab = tabManager.getActiveTab();
        if (activeTab != null) activeTab.handleClick(relMouseX, relMouseY, mouseButton);
    }

    // ---- TabManager.TabSwitchListener implementation ----

    @Override
    public void onPreSwitch(int oldTab) {
        // No special action needed here for tab transitions.
    }

    @Override
    public void onPostSwitch(int newTab) {
        getScrollBar().setRange(0, 0, 1);       // Reset scrollbar
        updateScrollbarForCurrentTab();         // Set new scrollbar range
        updateSearchModeButtonVisibility();     // Show/hide search mode button
        initFilterButtons();                    // Filter buttons differ per tab
        applyFiltersToDataManager();            // ^ which means we need to apply them
        onSearchTextChanged();                  // Then apply the search filter

        // Update back button appearance based on whether we're in subnet overview
        if (this.subnetBackButton != null) {
            this.subnetBackButton.setInOverviewMode(isInSubnetOverviewMode());
        }

        // Only notify server of real tab changes, not subnet overlay transitions
        if (newTab >= 0) {
            CellTerminalNetwork.INSTANCE.sendToServer(new PacketTabChange(newTab));
        }
    }

    /**
     * Handle upgrade insertion when the player is holding an upgrade item and left-clicks.
     * Delegates to the active tab widget for tab-specific logic.
     */
    protected boolean handleWidgetUpgradeClick(int mouseX, int mouseY) {
        ItemStack heldStack = mc.player.inventory.getItemStack();
        if (heldStack.isEmpty()) return false;
        if (!(heldStack.getItem() instanceof IUpgradeModule)) return false;

        // Distinguish real upgrades from storage components that also implement IUpgradeModule
        if (((IUpgradeModule) heldStack.getItem()).getType(heldStack) == null) return false;

        // Check if upgrade insertion is enabled
        if (CellTerminalServerConfig.isInitialized()
                && !CellTerminalServerConfig.getInstance().isUpgradeInsertEnabled()) {
            MessageHelper.error("cellterminal.error.upgrade_insert_disabled");

            return true;  // Consume click to prevent other handlers
        }

        AbstractTabWidget activeTab = tabManager.getActiveTab();
        if (activeTab == null) return false;

        boolean isShiftClick = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (isShiftClick) return activeTab.handleShiftUpgradeClick(heldStack);

        int relMouseX = mouseX - guiLeft;
        int relMouseY = mouseY - guiTop;
        Object hoveredData = activeTab.getDataForHoveredRow(relMouseX, relMouseY);

        // Don't intercept upgrade clicks on content/partition rows - the partition
        // slot click handler should take priority, allowing upgrades to be set as partition items.
        // Upgrade insertion is only supported via headers, cell icons, and terminal lines.
        if (hoveredData instanceof CellContentRow || hoveredData instanceof StorageBusContentRow) {
            return false;
        }

        return activeTab.handleUpgradeClick(hoveredData, heldStack, false);
    }

    public void rebuildAndUpdateScrollbar() {
        dataManager.rebuildLines();
        updateScrollbarForCurrentTab();
    }

    @Override
    protected void actionPerformed(GuiButton btn) throws IOException {
        if (btn == terminalStyleButton) {
            // TODO: maybe add the guard to actionPerformed (or click) as a whole
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
        // Handle inline rename keys (Esc cancels, Enter confirms, typing updates field)
        if (InlineRenameManager.getInstance().handleKey(typedChar, keyCode)) return;

        // Handle network tool confirmation modal (blocks all other input)
        if (networkToolModal != null) {
            if (networkToolModal.handleKeyTyped(keyCode)) return;
        }

        // Handle modal search bar keyboard
        if (modalSearchBar != null && modalSearchBar.isVisible()) {
            if (modalSearchBar.handleKeyTyped(typedChar, keyCode)) return;
        }

        // Handle priority field keyboard
        if (PriorityFieldManager.getInstance().handleKeyTyped(typedChar, keyCode)) return;

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

        // Delegate key handling to the active tab widget via TabManager
        if (tabManager.handleKey(keyCode)) return;

        // Toggle subnet overview: available everywhere and should not take priority over other handlers
        if (KeyBindings.SUBNET_OVERVIEW_TOGGLE.isActiveAndMatches(keyCode)) {
            handleSubnetBackButtonClick();
            return;
        }

        super.keyTyped(typedChar, keyCode);
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
            TabStateManager.TabType tabType = TabStateManager.TabType.fromIndex(tabManager.getCurrentTab());
            int saved = TabStateManager.getInstance().getScrollPosition(tabType);
            scrollToLine(saved);
            this.initialScrollRestored = true;
        }
    }

    // --- Subnet overview delegation ---
    // All subnet interaction logic now lives in SubnetOverviewTabWidget.
    // These methods implement SubnetOverviewContext and provide thin wrappers for the GUI.

    /**
     * Handle subnet list update from server.
     * Delegates to the subnet tab widget for parsing and display.
     */
    public void handleSubnetListUpdate(NBTTagCompound data) {
        tabManager.getSubnetTab().handleSubnetListUpdate(data);
        updateScrollbarForCurrentTab();
    }

    /**
     * Check if currently in subnet overview mode.
     */
    public boolean isInSubnetOverviewMode() {
        return TabStateManager.isSubnetTab(tabManager.getCurrentTab());
    }

    /**
     * Handle back button click - toggle subnet overview mode.
     */
    protected void handleSubnetBackButtonClick() {
        if (isInSubnetOverviewMode()) {
            // Exiting subnet overview: return to previous tab and refresh network data
            tabManager.switchToTab(tabManager.getPreviousRealTab());
            switchToNetwork(currentNetworkId);
        } else {
            // Entering subnet overview
            tabManager.switchToTab(TabStateManager.TabType.SUBNET_OVERVIEW.getIndex());
        }
    }

    // --- SubnetOverviewContext implementation ---

    @Override
    public void switchToNetwork(long networkId) {
        this.currentNetworkId = networkId;

        // Exit subnet overview if it was active (e.g. clicking a Load button in overview)
        if (isInSubnetOverviewMode()) tabManager.switchToTab(tabManager.getPreviousRealTab());


        // Reset data manager so the next update does a full rebuild with proper filters
        // instead of using snapshots from the old network context
        this.dataManager.resetForNetworkSwitch();

        // Update back button state - now we're in normal view, not overview
        if (this.subnetBackButton != null) this.subnetBackButton.setInOverviewMode(false);

        // Tell server to switch network context
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketSwitchNetwork(networkId));
    }

    @Override
    public void requestSubnetList() {
        CellTerminalNetwork.INSTANCE.sendToServer(new com.cellterminal.network.PacketSubnetListRequest());
    }

    @Override
    public void onGuiClosed() {
        // Persist the current scroll position for the active tab so it is restored when the GUI is reopened.
        tabManager.saveCurrentScrollPosition();

        super.onGuiClosed();
    }

    protected void updateScrollbarForCurrentTab() {
        List<Object> lines = tabManager.getActiveLines(dataManager);
        int lineCount = lines.size();

        // Use the tab widget's visible item count (accounts for non-standard row heights)
        int visibleItems = tabManager.getActiveVisibleItemCount();

        this.getScrollBar().setRange(0, Math.max(0, lineCount - visibleItems), 1);
    }

    // JEI Ghost Ingredient support

    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        if (partitionPopup != null) return partitionPopup.getGhostTargets();

        AbstractTabWidget activeTab = tabManager.getActiveTab();
        if (activeTab == null) return Collections.emptyList();

        return activeTab.getPhantomTargets(ingredient);
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
        CellTerminalNetwork.INSTANCE.sendToServer((IMessage) packet);
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
