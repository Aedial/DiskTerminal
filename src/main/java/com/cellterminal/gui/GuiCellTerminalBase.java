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
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentString;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.AEApi;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.AppEngSlot;
import appeng.fluids.items.FluidDummyItem;

import mezz.jei.api.gui.IGhostIngredientHandler;

import com.cellterminal.client.CellContentRow;
import com.cellterminal.client.CellInfo;
import com.cellterminal.client.CellTerminalClientConfig;
import com.cellterminal.client.CellTerminalClientConfig.TerminalStyle;
import com.cellterminal.client.KeyBindings;
import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.client.StorageInfo;
import com.cellterminal.integration.ThaumicEnergisticsIntegration;
import com.cellterminal.gui.handler.JeiGhostHandler;
import com.cellterminal.gui.handler.JeiGhostHandler.PartitionSlotTarget;
import com.cellterminal.gui.handler.JeiGhostHandler.StorageBusPartitionSlotTarget;
import com.cellterminal.gui.handler.QuickPartitionHandler;
import com.cellterminal.gui.handler.TerminalClickHandler;
import com.cellterminal.gui.handler.TerminalDataManager;
import com.cellterminal.gui.render.InventoryTabRenderer;
import com.cellterminal.gui.render.PartitionTabRenderer;
import com.cellterminal.gui.render.RenderContext;
import com.cellterminal.gui.render.StorageBusInventoryTabRenderer;
import com.cellterminal.gui.render.StorageBusPartitionTabRenderer;
import com.cellterminal.gui.render.TerminalTabRenderer;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketHighlightBlock;
import com.cellterminal.network.PacketPartitionAction;
import com.cellterminal.network.PacketStorageBusIOMode;
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

    // Terminal style button
    protected GuiTerminalStyleButton terminalStyleButton;

    // Search field and mode button
    protected MEGuiTextField searchField;
    protected GuiSearchModeButton searchModeButton;
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

    // Double-click tracking for storage bus highlight
    private long lastClickedStorageBusId = -1;
    private long lastClickTimeStorageBus = 0;

    public GuiCellTerminalBase(Container container) {
        super(container);

        this.xSize = 208;
        this.rowsVisible = calculateRowsCount();
        this.ySize = MAGIC_HEIGHT_NUMBER + this.rowsVisible * ROW_HEIGHT;

        GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);

        // Initialize render context
        this.renderContext = new RenderContext();
        this.dataManager = new TerminalDataManager();
        this.clickHandler = new TerminalClickHandler();

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
        net.minecraft.client.gui.ScaledResolution res = new net.minecraft.client.gui.ScaledResolution(Minecraft.getMinecraft());
        int screenHeight = res.getScaledHeight();

        // Leave some padding at top and bottom
        int availableHeight = screenHeight - 40;
        int extraSpace = availableHeight - MAGIC_HEIGHT_NUMBER;

        return Math.max(MIN_ROWS, extraSpace / ROW_HEIGHT);
    }

    protected abstract String getGuiTitle();

    @Override
    public void initGui() {
        // Recalculate rows based on screen size and terminal style
        this.rowsVisible = calculateRowsCount();
        this.ySize = MAGIC_HEIGHT_NUMBER + this.rowsVisible * ROW_HEIGHT;

        // Center GUI with appropriate offset for tall mode
        int unusedSpace = this.height - this.ySize;
        this.guiTop = (int) Math.floor(unusedSpace / (unusedSpace < 0 ? 3.8f : 2.0f));

        super.initGui();

        this.getScrollBar().setTop(18).setLeft(189).setHeight(this.rowsVisible * ROW_HEIGHT - 2);
        this.repositionSlots();
        initTabIcons();
        initRenderers();
        initTerminalStyleButton();
        initSearchField();
        initPriorityFieldManager();

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
        TerminalStyle currentStyle = CellTerminalClientConfig.getInstance().getTerminalStyle();

        // Remove any existing terminal style button before adding a new one
        if (this.terminalStyleButton != null) this.buttonList.remove(this.terminalStyleButton);

        // Calculate button Y as if in SMALL mode (for consistent positioning across style changes)
        int smallModeYSize = MAGIC_HEIGHT_NUMBER + DEFAULT_ROWS * ROW_HEIGHT;
        int smallModeGuiTop = (this.height - smallModeYSize) / 2;
        int buttonY = Math.max(8, smallModeGuiTop + 8);

        this.terminalStyleButton = new GuiTerminalStyleButton(0, this.guiLeft - 18, buttonY, currentStyle);
        this.buttonList.add(this.terminalStyleButton);
    }

    protected void initSearchField() {
        // Search field: positioned after title, extending close to the scrollbar/button area
        int titleWidth = this.fontRenderer.getStringWidth(getGuiTitle());
        int searchX = 22 + titleWidth + 4;
        int searchY = 4;
        // Search field extends to x=189
        int availableWidth = 189 - searchX;

        // Preserve existing search text if reinitializing, otherwise load from config
        String existingSearch;
        if (this.searchField != null) {
            existingSearch = this.searchField.getText();
        } else {
            existingSearch = CellTerminalClientConfig.getInstance().getSearchFilter();
        }

        // Use absolute coordinates so mouseClicked works correctly
        this.searchField = new MEGuiTextField(this.fontRenderer, this.guiLeft + searchX, this.guiTop + searchY, availableWidth, 12) {
            @Override
            public void onTextChange(String oldText) {
                onSearchTextChanged();
            }
        };
        this.searchField.setEnableBackgroundDrawing(true);
        this.searchField.setMaxStringLength(50);

        // Set text without triggering onTextChange to avoid redundant rebuilds during init
        this.searchField.setText(existingSearch, true);

        // Remove any existing search mode button before adding a new one
        if (this.searchModeButton != null) this.buttonList.remove(this.searchModeButton);

        // Search mode button: positioned above the scrollbar (top-right corner)
        int buttonX = this.guiLeft + 189;
        int buttonY = this.guiTop + 4;
        this.searchModeButton = new GuiSearchModeButton(1, buttonX, buttonY, currentSearchMode);
        this.buttonList.add(this.searchModeButton);

        // Update button visibility based on current tab
        updateSearchModeButtonVisibility();

        // Apply filter if there's persisted search text (single call, not via setText callback)
        if (!existingSearch.isEmpty()) dataManager.setSearchFilter(existingSearch, getEffectiveSearchMode());
    }

    protected void updateSearchModeButtonVisibility() {
        if (this.searchModeButton == null) return;

        // Hide button on Tab 2 (Inventory) and Tab 3 (Partition)
        this.searchModeButton.visible = (currentTab == TAB_TERMINAL);
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
     * Tab 2 forces INVENTORY mode, Tab 3 forces PARTITION mode.
     * Storage bus tabs also force their respective modes.
     */
    protected SearchFilterMode getEffectiveSearchMode() {
        switch (currentTab) {
            case TAB_INVENTORY:
            case TAB_STORAGE_BUS_INVENTORY:
                return SearchFilterMode.INVENTORY;
            case TAB_PARTITION:
            case TAB_STORAGE_BUS_PARTITION:
                return SearchFilterMode.PARTITION;
            default:
                return currentSearchMode;
        }
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

        // Draw hover preview if hovering over button
        if (currentTab == TAB_TERMINAL && hoveredCell != null && inventoryPopup == null && partitionPopup == null) {
            int previewX = mouseX + 10;
            int previewY = mouseY + 10;

            if (hoverType == 1) {
                new PopupCellInventory(this, hoveredCell, previewX, previewY).draw(mouseX, mouseY);
            } else if (hoverType == 2) {
                new PopupCellPartition(this, hoveredCell, previewX, previewY).draw(mouseX, mouseY);
            } else if (hoverType == 3) {
                this.drawHoveringText(Collections.singletonList(I18n.format("gui.cellterminal.eject_cell")), mouseX, mouseY);
            }
        }

        // Draw tooltip for tab 2/3 content items
        if ((currentTab == TAB_INVENTORY || currentTab == TAB_PARTITION) && !hoveredContentStack.isEmpty()) {
            this.drawHoveringText(this.getItemToolTip(hoveredContentStack), hoveredContentX, hoveredContentY);
        }

        // Draw tooltip for tab 4/5 content items
        if ((currentTab == TAB_STORAGE_BUS_INVENTORY || currentTab == TAB_STORAGE_BUS_PARTITION) && !hoveredContentStack.isEmpty()) {
            this.drawHoveringText(this.getItemToolTip(hoveredContentStack), hoveredContentX, hoveredContentY);
        }

        // Draw tab tooltips
        if (hoveredTab >= 0 && inventoryPopup == null && partitionPopup == null) {
            String tooltip = getTabTooltip(hoveredTab);
            if (!tooltip.isEmpty()) this.drawHoveringText(Collections.singletonList(tooltip), mouseX, mouseY);
        }

        // Draw terminal style button tooltip
        if (terminalStyleButton != null && terminalStyleButton.isMouseOver()) {
            this.drawHoveringText(terminalStyleButton.getTooltip(), mouseX, mouseY);
        }

        // Draw search mode button tooltip
        if (searchModeButton != null && searchModeButton.visible && searchModeButton.isMouseOver()) {
            this.drawHoveringText(searchModeButton.getTooltip(), mouseX, mouseY);
        }

        // Draw priority field tooltip
        if (priorityFieldManager != null && priorityFieldManager.isMouseOverField(mouseX, mouseY)) {
            this.drawHoveringText(Collections.singletonList(I18n.format("gui.cellterminal.priority.tooltip")), mouseX, mouseY);
        }

        // Draw Clear button tooltip (Tab 5)
        if (hoveredClearButtonStorageBus != null) {
            this.drawHoveringText(Collections.singletonList(I18n.format("gui.cellterminal.storagebus.clear")), mouseX, mouseY);
        }

        // Draw IO Mode button tooltip (Tab 4)
        if (hoveredIOModeButtonStorageBus != null) {
            if (hoveredIOModeButtonStorageBus.supportsIOMode()) {
                String currentMode = hoveredIOModeButtonStorageBus.getIOModeDisplayName();
                this.drawHoveringText(Collections.singletonList(
                    I18n.format("gui.cellterminal.storagebus.iomode.current", currentMode)), mouseX, mouseY);
            } else {
                // Essentia buses don't support IO mode
                this.drawHoveringText(Collections.singletonList(
                    I18n.format("gui.cellterminal.storagebus.iomode.unsupported")), mouseX, mouseY);
            }
        }

        // Draw Partition All button tooltip (Tab 2 - Cell, Tab 4 - Storage Bus)
        if (hoveredPartitionAllButtonCell != null) {
            this.drawHoveringText(Collections.singletonList(I18n.format("gui.cellterminal.cell.partitionall")), mouseX, mouseY);
        }

        if (hoveredPartitionAllButtonStorageBus != null) {
            this.drawHoveringText(Collections.singletonList(I18n.format("gui.cellterminal.storagebus.partitionall")), mouseX, mouseY);
        }

        // Draw Clear Partition button tooltip (Tab 3 - Cell)
        if (hoveredClearPartitionButtonCell != null) {
            this.drawHoveringText(Collections.singletonList(I18n.format("gui.cellterminal.cell.clearpartition")), mouseX, mouseY);
        }
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(getGuiTitle(), 22, 6, 0x404040);
        this.fontRenderer.drawString(I18n.format("container.inventory"), 22, this.ySize - 93, 0x404040);

        // Draw search field - translate back since field uses absolute coords but we're in translated context
        if (this.searchField != null) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(-this.guiLeft, -this.guiTop, 0);
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

        // Draw controls help based on current tab
        switch (currentTab) {
            case TAB_TERMINAL:
                drawTerminalControlsHelp();
                break;
            case TAB_INVENTORY:
                drawInventoryControlsHelp();
                break;
            case TAB_PARTITION:
                drawPartitionControlsHelp();
                break;
            case TAB_STORAGE_BUS_INVENTORY:
                drawStorageBusInventoryControlsHelp();
                break;
            case TAB_STORAGE_BUS_PARTITION:
                drawStorageBusPartitionControlsHelp();
                break;
        }
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
     * Draw controls help for Terminal tab (tab 1).
     */
    protected void drawTerminalControlsHelp() {
        List<String> lines = new ArrayList<>();
        lines.add(I18n.format("gui.cellterminal.controls.double_click_storage_cell"));
        drawControlsHelpWidget(lines);
    }

    /**
     * Draw controls help for Inventory tab (tab 2).
     */
    protected void drawInventoryControlsHelp() {
        List<String> lines = new ArrayList<>();

        lines.add(I18n.format("gui.cellterminal.controls.partition_indicator"));
        lines.add(I18n.format("gui.cellterminal.controls.click_partition_toggle"));
        lines.add(I18n.format("gui.cellterminal.controls.double_click_storage"));

        drawControlsHelpWidget(lines);
    }

    /**
     * Draw controls help for Partition tab (tab 3).
     */
    protected void drawPartitionControlsHelp() {
        List<String> lines = new ArrayList<>();

        // Note about what keybinds target
        lines.add(I18n.format("gui.cellterminal.controls.keybind_targets"));

        lines.add("");  // spacing line

        // Keybind instructions
        String notSet = I18n.format("gui.cellterminal.controls.key_not_set");

        String autoKey = KeyBindings.QUICK_PARTITION_AUTO.isBound()
            ? KeyBindings.QUICK_PARTITION_AUTO.getDisplayName() : notSet;
        lines.add(I18n.format("gui.cellterminal.controls.key_auto", autoKey));

        // Auto type warning if set
        if (!autoKey.equals(notSet)) lines.add(I18n.format("gui.cellterminal.controls.auto_warning"));

        lines.add("");

        String itemKey = KeyBindings.QUICK_PARTITION_ITEM.isBound()
            ? KeyBindings.QUICK_PARTITION_ITEM.getDisplayName() : notSet;
        lines.add(I18n.format("gui.cellterminal.controls.key_item", itemKey));

        String fluidKey = KeyBindings.QUICK_PARTITION_FLUID.isBound()
            ? KeyBindings.QUICK_PARTITION_FLUID.getDisplayName() : notSet;
        lines.add(I18n.format("gui.cellterminal.controls.key_fluid", fluidKey));

        String essentiaKey = KeyBindings.QUICK_PARTITION_ESSENTIA.isBound()
            ? KeyBindings.QUICK_PARTITION_ESSENTIA.getDisplayName() : notSet;
        lines.add(I18n.format("gui.cellterminal.controls.key_essentia", essentiaKey));

        // Essentia cells warning if set and Thaumic Energistics not loaded
        if (!essentiaKey.equals(notSet) && !ThaumicEnergisticsIntegration.isModLoaded()) {
            lines.add(I18n.format("gui.cellterminal.controls.essentia_warning"));
        }

        lines.add("");

        lines.add(I18n.format("gui.cellterminal.controls.jei_drag"));
        lines.add(I18n.format("gui.cellterminal.controls.click_to_remove"));
        lines.add(I18n.format("gui.cellterminal.controls.double_click_storage"));

        drawControlsHelpWidget(lines);
    }

    /**
     * Draw controls help for Storage Bus Inventory tab (tab 4).
     */
    protected void drawStorageBusInventoryControlsHelp() {
        List<String> lines = new ArrayList<>();

        lines.add(I18n.format("gui.cellterminal.controls.filter_indicator"));
        lines.add(I18n.format("gui.cellterminal.controls.click_to_remove"));
        lines.add(I18n.format("gui.cellterminal.controls.double_click_storage"));

        drawControlsHelpWidget(lines);
    }

    /**
     * Draw controls help for Storage Bus Partition tab (tab 5).
     */
    protected void drawStorageBusPartitionControlsHelp() {
        List<String> lines = new ArrayList<>();

        lines.add(I18n.format("gui.cellterminal.controls.storage_bus_add_key",
            KeyBindings.ADD_TO_STORAGE_BUS.getDisplayName()));

        lines.add(I18n.format("gui.cellterminal.controls.storage_bus_capacity"));

        lines.add("");  // spacing line

        lines.add(I18n.format("gui.cellterminal.controls.jei_drag"));
        lines.add(I18n.format("gui.cellterminal.controls.click_to_remove"));
        lines.add(I18n.format("gui.cellterminal.controls.double_click_storage"));

        drawControlsHelpWidget(lines);
    }

    /**
     * Draw the controls help widget with the given lines.
     * Lines will be wrapped to fit the available width.
     */
    protected void drawControlsHelpWidget(List<String> lines) {
        if (lines.isEmpty()) return;

        // Calculate panel width based on available space (guiLeft minus margins on both sides)
        int panelWidth = this.guiLeft - CONTROLS_HELP_RIGHT_MARGIN - CONTROLS_HELP_LEFT_MARGIN;
        if (panelWidth < 60) panelWidth = 60;  // Minimum width

        // Calculate available text width (panel width minus padding on both sides)
        int textWidth = panelWidth - (CONTROLS_HELP_PADDING * 2);

        // Wrap all lines and cache them
        List<String> wrappedLines = new ArrayList<>();
        for (String line : lines) {
            if (line.isEmpty()) {
                wrappedLines.add("");  // Preserve spacing lines
            } else {
                wrappedLines.addAll(fontRenderer.listFormattedStringToWidth(line, textWidth));
            }
        }

        // Cache the wrapped lines for exclusion area calculation
        cachedControlsHelpLines = wrappedLines;
        cachedControlsHelpTab = currentTab;

        // Calculate positions
        // Panel right edge has small gap from GUI, left edge has small gap from screen edge
        int panelRight = -CONTROLS_HELP_RIGHT_MARGIN;
        int panelLeft = -this.guiLeft + CONTROLS_HELP_LEFT_MARGIN;
        int contentHeight = wrappedLines.size() * CONTROLS_HELP_LINE_HEIGHT;
        int panelHeight = contentHeight + (CONTROLS_HELP_PADDING * 2);

        // Position panel at bottom of GUI
        // In tall mode, move up slightly to allow chat to be visible
        TerminalStyle style = CellTerminalClientConfig.getInstance().getTerminalStyle();
        int bottomOffset = (style == TerminalStyle.TALL) ? 30 : 8;
        int panelBottom = this.ySize - bottomOffset;
        int panelTop = panelBottom - panelHeight;

        // Draw AE2-style panel background
        // Main background (dark semi-transparent)
        drawRect(panelLeft, panelTop, panelRight, panelBottom, 0xC0000000);

        // Border - AE2 style with subtle highlight/shadow
        // Top edge (lighter)
        drawRect(panelLeft, panelTop, panelRight, panelTop + 1, 0xFF606060);
        // Left edge (lighter)
        drawRect(panelLeft, panelTop, panelLeft + 1, panelBottom, 0xFF606060);
        // Bottom edge (darker)
        drawRect(panelLeft, panelBottom - 1, panelRight, panelBottom, 0xFF303030);
        // Right edge (darker)
        drawRect(panelRight - 1, panelTop, panelRight, panelBottom, 0xFF303030);

        // Draw each wrapped line
        int textX = panelLeft + CONTROLS_HELP_PADDING;
        int textY = panelTop + CONTROLS_HELP_PADDING;
        for (int i = 0; i < wrappedLines.size(); i++) {
            String line = wrappedLines.get(i);
            if (!line.isEmpty()) {
                fontRenderer.drawString(line, textX, textY + (i * CONTROLS_HELP_LINE_HEIGHT), 0xCCCCCC);
            }
        }
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

        // In tall mode, move up slightly to allow chat to be visible
        TerminalStyle style = CellTerminalClientConfig.getInstance().getTerminalStyle();
        int bottomOffset = (style == TerminalStyle.TALL) ? 30 : 8;
        int panelBottom = this.ySize - bottomOffset;
        int panelTop = panelBottom - panelHeight;

        return new Rectangle(
            this.guiLeft + panelLeft,
            this.guiTop + panelTop,
            panelWidth,
            panelHeight
        );
    }

    /**
     * Get JEI exclusion areas to prevent overlap with controls help widget.
     */
    @Override
    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> areas = new ArrayList<>();
        Rectangle controlsHelp = getControlsHelpBounds();

        if (controlsHelp.width > 0) areas.add(controlsHelp);

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
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();

        int tabY = offsetY + TAB_Y_OFFSET;
        hoveredTab = -1;

        for (int i = 0; i < TAB_COUNT; i++) {
            int tabX = offsetX + 4 + (i * (TAB_WIDTH + 2));
            boolean isSelected = (i == currentTab);
            boolean isHovered = mouseX >= tabX && mouseX < tabX + TAB_WIDTH
                && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;

            if (isHovered) hoveredTab = i;

            // Tab background
            int bgColor = isSelected ? 0xFFC6C6C6 : (isHovered ? 0xFFA0A0A0 : 0xFF8B8B8B);
            drawRect(tabX, tabY, tabX + TAB_WIDTH, tabY + TAB_HEIGHT, bgColor);

            // Tab border (3D effect)
            drawRect(tabX, tabY, tabX + TAB_WIDTH, tabY + 1, 0xFFFFFFFF);  // top
            drawRect(tabX, tabY, tabX + 1, tabY + TAB_HEIGHT, 0xFFFFFFFF);  // left
            drawRect(tabX + TAB_WIDTH - 1, tabY, tabX + TAB_WIDTH, tabY + TAB_HEIGHT, 0xFF555555);  // right

            // If selected, remove bottom border to connect with main GUI
            if (isSelected) {
                drawRect(tabX + 1, tabY + TAB_HEIGHT - 1, tabX + TAB_WIDTH - 1, tabY + TAB_HEIGHT, 0xFFC6C6C6);
            } else {
                drawRect(tabX, tabY + TAB_HEIGHT - 1, tabX + TAB_WIDTH, tabY + TAB_HEIGHT, 0xFF555555);  // bottom
            }

            // Draw icon (composite for storage bus tabs)
            if (i == TAB_STORAGE_BUS_INVENTORY || i == TAB_STORAGE_BUS_PARTITION) {
                drawCompositeTabIcon(tabX + 3, tabY + 3, i);
            } else {
                ItemStack icon = getTabIcon(i);
                if (!icon.isEmpty()) {
                    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                    RenderHelper.enableGUIStandardItemLighting();
                    this.itemRender.renderItemIntoGUI(icon, tabX + 3, tabY + 3);
                    RenderHelper.disableStandardItemLighting();
                    GlStateManager.disableLighting();
                }
            }
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
    }

    /**
     * Draw a composite icon for storage bus tabs using diagonal cut view.
     * Shows top-left half of one icon and bottom-right half of the storage bus icon,
     * each offset so exactly half of each item shows with diagonal separation.
     */
    protected void drawCompositeTabIcon(int x, int y, int tab) {
        ItemStack topLeftIcon = (tab == TAB_STORAGE_BUS_INVENTORY) ? tabIconInventory : tabIconPartition;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        int scaleFactor = new ScaledResolution(mc).getScaleFactor();

        // Offset for each icon to show exactly half
        // Top-left icon: offset to the right/down so its top-left half fills the top-left triangle
        // Bottom-right icon: offset to the left/up so its bottom-right half fills the bottom-right triangle
        int offset = 4;  // Pixels to offset each icon

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        RenderHelper.enableGUIStandardItemLighting();

        // Top-left icon: draw offset to top-left, scissor to top-left triangle
        if (!topLeftIcon.isEmpty()) {
            for (int row = 0; row < 16; row++) {
                // Width decreases as we go down (diagonal from top-right to bottom-left)
                int stripWidth = Math.max(0, 15 - row - 1);  // Leave 1px gap for diagonal
                if (stripWidth <= 0) continue;

                int scissorX = x * scaleFactor;
                int scissorY = (mc.displayHeight) - ((y + row + 1) * scaleFactor);
                int scissorWidth = stripWidth * scaleFactor;
                int scissorHeight = 1 * scaleFactor;

                GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
                // Draw icon offset so its center aligns with the center of the top-left triangle
                this.itemRender.renderItemIntoGUI(topLeftIcon, x - offset, y - offset);
            }
        }

        // Bottom-right icon: draw offset to bottom-right, scissor to bottom-right triangle
        if (!tabIconStorageBus.isEmpty()) {
            for (int row = 0; row < 16; row++) {
                // Width increases as we go down, starting from right side
                int clipStart = Math.min(16, 17 - row);  // Leave 1px gap for diagonal
                int stripWidth = 16 - clipStart;
                if (stripWidth <= 0) continue;

                int scissorX = (x + clipStart) * scaleFactor;
                int scissorY = (mc.displayHeight) - ((y + row + 1) * scaleFactor);
                int scissorWidth = stripWidth * scaleFactor;
                int scissorHeight = 1 * scaleFactor;

                GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
                // Draw icon offset so its center aligns with the center of the bottom-right triangle
                this.itemRender.renderItemIntoGUI(tabIconStorageBus, x + offset, y + offset);
            }
        }
        RenderHelper.disableStandardItemLighting();

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.disableLighting();

        // Draw diagonal separator line (45 degrees, from top-right to bottom-left)
        GlStateManager.disableTexture2D();
        GlStateManager.color(0.3f, 0.3f, 0.3f, 1.0f);
        GL11.glLineWidth(1.5f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x + 15, y + 1);
        GL11.glVertex2f(x + 1, y + 15);
        GL11.glEnd();
        GL11.glLineWidth(1.0f);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    protected String getTabTooltip(int tab) {
        switch (tab) {
            case TAB_TERMINAL:
                return I18n.format("gui.cellterminal.tab.terminal.tooltip");
            case TAB_INVENTORY:
                return I18n.format("gui.cellterminal.tab.inventory.tooltip");
            case TAB_PARTITION:
                return I18n.format("gui.cellterminal.tab.partition.tooltip");
            case TAB_STORAGE_BUS_INVENTORY:
                return I18n.format("gui.cellterminal.tab.storage_bus_inventory.tooltip");
            case TAB_STORAGE_BUS_PARTITION:
                return I18n.format("gui.cellterminal.tab.storage_bus_partition.tooltip");
            default:
                return "";
        }
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
        // Handle search field clicks
        if (this.searchField != null) {
            // Right-click clears the field and focuses it
            if (mouseButton == 1 && this.searchField.isMouseIn(mouseX, mouseY)) {
                this.searchField.setText("");
                this.searchField.setFocused(true);

                return;
            }

            this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        // Handle priority field clicks
        if (priorityFieldManager != null && priorityFieldManager.handleClick(mouseX, mouseY, mouseButton)) {
            return;
        }

        // Handle upgrade clicks when holding an upgrade item
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
            // Handle partition-all button in Tab 2 (Inventory)
            if (currentTab == TAB_INVENTORY && hoveredPartitionAllButtonCell != null && mouseButton == 0) {
                CellTerminalNetwork.INSTANCE.sendToServer(
                    new PacketPartitionAction(
                        hoveredPartitionAllButtonCell.getParentStorageId(),
                        hoveredPartitionAllButtonCell.getSlot(),
                        PacketPartitionAction.Action.SET_ALL_FROM_CONTENTS
                    )
                );

                return;
            }

            // Handle clear-partition button in Tab 3 (Partition)
            if (currentTab == TAB_PARTITION && hoveredClearPartitionButtonCell != null && mouseButton == 0) {
                CellTerminalNetwork.INSTANCE.sendToServer(
                    new PacketPartitionAction(
                        hoveredClearPartitionButtonCell.getParentStorageId(),
                        hoveredClearPartitionButtonCell.getSlot(),
                        PacketPartitionAction.Action.CLEAR_ALL
                    )
                );

                return;
            }

            clickHandler.handleCellTabClick(currentTab, hoveredCellCell, hoveredContentSlotIndex,
                hoveredPartitionCell, hoveredPartitionSlotIndex, hoveredCellStorage, hoveredCellSlotIndex,
                hoveredStorageLine, hoveredLineIndex, dataManager.getStorageMap(), dataManager.getTerminalDimension(), createClickCallback());
        } else if (currentTab == TAB_STORAGE_BUS_INVENTORY || currentTab == TAB_STORAGE_BUS_PARTITION) {
            handleStorageBusTabClick(mouseX, mouseY, mouseButton);
        }
    }

    /**
     * Handle click events on storage bus tabs.
     */
    protected void handleStorageBusTabClick(int mouseX, int mouseY, int mouseButton) {
        // Handle upgrade clicks when holding an upgrade item on storage bus header
        if (mouseButton == 0 && handleStorageBusUpgradeClick()) return;

        long now = System.currentTimeMillis();
        boolean wasDoubleClick = false;

        // Double-click detection for highlighting storage bus block in-world
        if (hoveredStorageBus != null && mouseButton == 0) {
            long currentBusId = hoveredStorageBus.getId();

            if (currentBusId == lastClickedStorageBusId && now - lastClickTimeStorageBus < 400) {
                // Double-click detected - highlight the storage bus
                CellTerminalNetwork.INSTANCE.sendToServer(
                    new PacketHighlightBlock(hoveredStorageBus.getPos(), hoveredStorageBus.getDimension())
                );
                lastClickedStorageBusId = -1;
                wasDoubleClick = true;
                // Don't return - still update time tracking, but skip selection toggle
            } else {
                lastClickedStorageBusId = currentBusId;
            }

            lastClickTimeStorageBus = now;
        }

        // Tab 4: Storage Bus Inventory tab handling
        if (currentTab == TAB_STORAGE_BUS_INVENTORY) {
            // Check if clicking IO Mode button (only send if bus supports IO mode)
            if (hoveredIOModeButtonStorageBus != null && mouseButton == 0
                    && hoveredIOModeButtonStorageBus.supportsIOMode()) {
                CellTerminalNetwork.INSTANCE.sendToServer(
                    new PacketStorageBusIOMode(hoveredIOModeButtonStorageBus.getId())
                );

                return;
            }

            // Check if clicking Partition All button
            if (hoveredPartitionAllButtonStorageBus != null && mouseButton == 0) {
                CellTerminalNetwork.INSTANCE.sendToServer(
                    new PacketStorageBusPartitionAction(
                        hoveredPartitionAllButtonStorageBus.getId(),
                        PacketStorageBusPartitionAction.Action.PARTITION_ALL
                    )
                );

                return;
            }

            // Clicking content item toggles partition
            if (hoveredStorageBusContentSlot >= 0 && hoveredStorageBus != null) {
                if (mouseButton == 0 && !hoveredContentStack.isEmpty()) {
                    // Left click on content item: toggle partition
                    CellTerminalNetwork.INSTANCE.sendToServer(
                        new PacketStorageBusPartitionAction(
                            hoveredStorageBus.getId(),
                            PacketStorageBusPartitionAction.Action.TOGGLE_ITEM,
                            -1,  // slot not used for toggle
                            hoveredContentStack
                        )
                    );
                }

                return;
            }
        }

        // Tab 5: Storage Bus Partition tab handling
        if (currentTab == TAB_STORAGE_BUS_PARTITION) {
            // Check if clicking IO Mode button (only send if bus supports IO mode)
            if (hoveredIOModeButtonStorageBus != null && mouseButton == 0
                    && hoveredIOModeButtonStorageBus.supportsIOMode()) {
                CellTerminalNetwork.INSTANCE.sendToServer(
                    new PacketStorageBusIOMode(hoveredIOModeButtonStorageBus.getId())
                );

                return;
            }

            // Check if clicking Clear button
            if (hoveredClearButtonStorageBus != null && mouseButton == 0) {
                CellTerminalNetwork.INSTANCE.sendToServer(
                    new PacketStorageBusPartitionAction(
                        hoveredClearButtonStorageBus.getId(),
                        PacketStorageBusPartitionAction.Action.CLEAR_ALL
                    )
                );

                return;
            }

            // Check if clicking on a header row to select/deselect a storage bus
            if (hoveredStorageBus != null && hoveredStorageBusPartitionSlot < 0 && !wasDoubleClick) {
                // Clicking on header (not on a slot) toggles selection (multi-select)
                if (mouseButton == 0) {
                    long busId = hoveredStorageBus.getId();
                    if (selectedStorageBusIds.contains(busId)) {
                        selectedStorageBusIds.remove(busId);  // Deselect
                    } else {
                        // Validate that new selection is same type as existing selection
                        if (!selectedStorageBusIds.isEmpty()) {
                            Map<Long, StorageBusInfo> busMap = dataManager.getStorageBusMap();
                            StorageBusInfo existingBus = null;

                            for (Long existingId : selectedStorageBusIds) {
                                existingBus = busMap.get(existingId);
                                if (existingBus != null) break;
                            }

                            if (existingBus != null) {
                                boolean sameType = (hoveredStorageBus.isFluid() == existingBus.isFluid())
                                    && (hoveredStorageBus.isEssentia() == existingBus.isEssentia());

                                if (!sameType) {
                                    mc.player.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                                        "cellterminal.error.mixed_bus_selection"));

                                    return;
                                }
                            }
                        }

                        selectedStorageBusIds.add(busId);  // Add to selection
                    }
                }

                return;
            }

            // Normal partition slot handling
            if (hoveredStorageBusPartitionSlot >= 0 && hoveredStorageBus != null) {
                ItemStack heldStack = mc.player.inventory.getItemStack();

                if (mouseButton == 0) {
                    // Left click: add item if holding, otherwise clear slot
                    if (!heldStack.isEmpty()) {
                        // Validate item compatibility with storage bus type
                        ItemStack stackToSend = heldStack;

                        if (hoveredStorageBus.isFluid()) {
                            // Fluid storage bus - must contain a fluid or be a FluidDummyItem
                            if (!(heldStack.getItem() instanceof FluidDummyItem)) {
                                FluidStack fluid = net.minecraftforge.fluids.FluidUtil.getFluidContained(heldStack);
                                if (fluid == null) {
                                    mc.player.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                                        "cellterminal.error.fluid_bus_item"));

                                    return;
                                }
                            }
                        } else if (hoveredStorageBus.isEssentia()) {
                            // Essentia storage bus - validate through integration
                            ItemStack essentiaRep = com.cellterminal.integration.ThaumicEnergisticsIntegration
                                .tryConvertEssentiaContainerToAspect(heldStack);
                            if (essentiaRep.isEmpty()) {
                                mc.player.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                                    "cellterminal.error.essentia_bus_item"));

                                return;
                            }
                            stackToSend = essentiaRep;
                        }

                        CellTerminalNetwork.INSTANCE.sendToServer(
                            new PacketStorageBusPartitionAction(
                                hoveredStorageBus.getId(),
                                PacketStorageBusPartitionAction.Action.ADD_ITEM,
                                hoveredStorageBusPartitionSlot,
                                stackToSend
                            )
                        );
                    } else {
                        // Left click without item: clear slot
                        CellTerminalNetwork.INSTANCE.sendToServer(
                            new PacketStorageBusPartitionAction(
                                hoveredStorageBus.getId(),
                                PacketStorageBusPartitionAction.Action.REMOVE_ITEM,
                                hoveredStorageBusPartitionSlot,
                                ItemStack.EMPTY
                            )
                        );
                    }
                }
            }
        }
    }

    protected TerminalClickHandler.Callback createClickCallback() {
        return new TerminalClickHandler.Callback() {
            @Override
            public void onTabChanged(int tab) {
                currentTab = tab;
                getScrollBar().setRange(0, 0, 1);
                updateScrollbarForCurrentTab();
                updateSearchModeButtonVisibility();
                onSearchTextChanged();  // Reapply filter with tab-specific mode

                // Notify server of tab change for polling optimization
                CellTerminalNetwork.INSTANCE.sendToServer(new PacketTabChange(tab));
            }

            @Override
            public void onStorageToggle(StorageInfo storage) {
                dataManager.rebuildLines();
                updateScrollbarForCurrentTab();
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

    @Override
    protected void actionPerformed(GuiButton btn) throws IOException {
        if (btn == terminalStyleButton) {
            // Cycle to next terminal style and reinitialize GUI
            TerminalStyle newStyle = CellTerminalClientConfig.getInstance().cycleTerminalStyle();
            terminalStyleButton.setStyle(newStyle);

            // Reinitialize GUI to apply new dimensions
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

        super.actionPerformed(btn);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
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

            // If search field is focused, unfocus it
            if (this.searchField != null && this.searchField.isFocused()) {
                this.searchField.setFocused(false);

                return;
            }
        }

        // Handle quick partition keybinds (only in partition tab, not when search is focused)
        if (currentTab == TAB_PARTITION && (this.searchField == null || !this.searchField.isFocused())) {
            if (handleQuickPartitionKeybind(keyCode)) return;
        }

        // Handle add to storage bus keybind (only in Tab 5, not when search is focused)
        if (currentTab == TAB_STORAGE_BUS_PARTITION && (this.searchField == null || !this.searchField.isFocused())) {
            if (handleAddToStorageBusKeybind(keyCode)) return;
        }

        // Handle search field keyboard input first
        if (this.searchField != null && this.searchField.textboxKeyTyped(typedChar, keyCode)) return;

        super.keyTyped(typedChar, keyCode);
    }

    /**
     * Handle quick partition keybinds.
     * @return true if a keybind was handled
     */
    protected boolean handleQuickPartitionKeybind(int keyCode) {
        KeyBindings matchedKey = null;
        QuickPartitionHandler.PartitionType type = null;

        if (KeyBindings.QUICK_PARTITION_AUTO.isActiveAndMatches(keyCode)) {
            matchedKey = KeyBindings.QUICK_PARTITION_AUTO;
            type = QuickPartitionHandler.PartitionType.AUTO;
        } else if (KeyBindings.QUICK_PARTITION_ITEM.isActiveAndMatches(keyCode)) {
            matchedKey = KeyBindings.QUICK_PARTITION_ITEM;
            type = QuickPartitionHandler.PartitionType.ITEM;
        } else if (KeyBindings.QUICK_PARTITION_FLUID.isActiveAndMatches(keyCode)) {
            matchedKey = KeyBindings.QUICK_PARTITION_FLUID;
            type = QuickPartitionHandler.PartitionType.FLUID;
        } else if (KeyBindings.QUICK_PARTITION_ESSENTIA.isActiveAndMatches(keyCode)) {
            matchedKey = KeyBindings.QUICK_PARTITION_ESSENTIA;
            type = QuickPartitionHandler.PartitionType.ESSENTIA;
        }

        if (matchedKey == null || type == null) return false;

        QuickPartitionHandler.QuickPartitionResult result = QuickPartitionHandler.attemptQuickPartition(
            type, dataManager.getPartitionLines(), dataManager.getStorageMap());

        // Display result message
        if (Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(result.message));
        }

        // Scroll to the cell if successful
        if (result.success && result.scrollToLine >= 0) scrollToLine(result.scrollToLine);

        return true;
    }

    /**
     * Handle add to storage bus keybind.
     * Adds the item currently under the cursor to all selected storage bus partitions.
     * Supports inventory slots, JEI ingredients, and bookmarks.
     * @return true if a keybind was handled
     */
    protected boolean handleAddToStorageBusKeybind(int keyCode) {
        if (!KeyBindings.ADD_TO_STORAGE_BUS.isActiveAndMatches(keyCode)) return false;

        if (selectedStorageBusIds.isEmpty()) {
            if (Minecraft.getMinecraft().player != null) {
                Minecraft.getMinecraft().player.sendMessage(
                    new TextComponentString(I18n.format("cellterminal.storage_bus.no_selection"))
                );
            }

            return true;
        }

        // Try to get item from inventory slot first
        ItemStack stack = ItemStack.EMPTY;
        Slot hoveredSlot = getSlotUnderMouse();

        if (hoveredSlot != null && hoveredSlot.getHasStack()) stack = hoveredSlot.getStack();

        // If no inventory item, try JEI/bookmark
        if (stack.isEmpty()) {
            QuickPartitionHandler.HoveredIngredient jeiItem = QuickPartitionHandler.getHoveredIngredient();
            if (jeiItem != null && !jeiItem.stack.isEmpty()) stack = jeiItem.stack;
        }

        if (stack.isEmpty()) {
            if (Minecraft.getMinecraft().player != null) {
                Minecraft.getMinecraft().player.sendMessage(
                    new TextComponentString(I18n.format("cellterminal.storage_bus.no_item"))
                );
            }

            return true;
        }

        // TODO: bring to parity with drag and drop behavior (in term of error messages)
        // Add to all selected storage buses
        int successCount = 0;
        int invalidItemCount = 0;
        int noSlotCount = 0;
        Map<Long, StorageBusInfo> busMap = dataManager.getStorageBusMap();

        for (Long busId : selectedStorageBusIds) {
            StorageBusInfo storageBus = busMap.get(busId);
            if (storageBus == null) continue;

            // Convert the item for non-item bus types first to check validity
            ItemStack stackToSend = stack;
            boolean validForBusType = true;

            if (storageBus.isFluid()) {
                // For fluid buses, need FluidDummyItem or fluid container
                if (!(stack.getItem() instanceof FluidDummyItem)) {
                    FluidStack fluid = net.minecraftforge.fluids.FluidUtil.getFluidContained(stack);
                    // Can't use this item on fluid bus
                    if (fluid == null) {
                        invalidItemCount++;
                        validForBusType = false;
                    }
                }
            } else if (storageBus.isEssentia()) {
                // For essentia buses, need ItemDummyAspect or essentia container
                ItemStack essentiaRep = ThaumicEnergisticsIntegration.tryConvertEssentiaContainerToAspect(stack);
                // Can't use this item on essentia bus
                if (essentiaRep.isEmpty()) {
                    invalidItemCount++;
                    validForBusType = false;
                } else {
                    stackToSend = essentiaRep;
                }
            }

            if (!validForBusType) continue;

            // Find first empty slot in this storage bus
            List<ItemStack> partition = storageBus.getPartition();
            int availableSlots = storageBus.getAvailableConfigSlots();
            int targetSlot = -1;

            for (int i = 0; i < availableSlots; i++) {
                if (i >= partition.size() || partition.get(i).isEmpty()) {
                    targetSlot = i;
                    break;
                }
            }

            if (targetSlot < 0) {
                noSlotCount++;
                continue;
            }

            CellTerminalNetwork.INSTANCE.sendToServer(
                new PacketStorageBusPartitionAction(
                    busId,
                    PacketStorageBusPartitionAction.Action.ADD_ITEM,
                    targetSlot,
                    stackToSend
                )
            );
            successCount++;
        }

        if (successCount == 0 && Minecraft.getMinecraft().player != null) {
            // Show appropriate error message based on what failed
            if (invalidItemCount > 0 && noSlotCount == 0) {
                Minecraft.getMinecraft().player.sendMessage(
                    new TextComponentString(I18n.format("cellterminal.storage_bus.invalid_item"))
                );
            } else if (noSlotCount > 0 && invalidItemCount == 0) {
                Minecraft.getMinecraft().player.sendMessage(
                    new TextComponentString(I18n.format("cellterminal.storage_bus.partition_full"))
                );
            } else {
                // Mixed: some were invalid, some were full
                Minecraft.getMinecraft().player.sendMessage(
                    new TextComponentString(I18n.format("cellterminal.storage_bus.partition_full"))
                );
            }
        }

        return true;
    }

    /**
     * Handle upgrade click when player is holding an upgrade item.
     * Regular click on cell: upgrade that cell
     * Regular click on storage header: upgrade first cell in that storage
     * Shift click: upgrade first visible cell that can accept this upgrade
     * @return true if an upgrade click was handled
     */
    protected boolean handleUpgradeClick(int mouseX, int mouseY) {
        // Check if player is holding an upgrade
        ItemStack heldStack = Minecraft.getMinecraft().player.inventory.getItemStack();
        if (heldStack.isEmpty()) return false;
        if (!(heldStack.getItem() instanceof IUpgradeModule)) return false;

        boolean isShiftClick = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        if (isShiftClick) {
            // Shift-click: upgrade first visible cell that can accept this upgrade
            CellInfo targetCell = findFirstVisibleCellThatCanAcceptUpgrade(heldStack);
            if (targetCell == null) return false;

            StorageInfo storage = dataManager.getStorageMap().get(targetCell.getParentStorageId());
            if (storage == null) return false;

            CellTerminalNetwork.INSTANCE.sendToServer(new PacketUpgradeCell(
                storage.getId(),
                targetCell.getSlot(),
                true
            ));

            return true;
        }

        // Regular click: check if hovering a cell directly
        if (hoveredCellCell != null && hoveredCellStorage != null) {
            if (!hoveredCellCell.canAcceptUpgrade(heldStack)) return false;

            CellTerminalNetwork.INSTANCE.sendToServer(new PacketUpgradeCell(
                hoveredCellStorage.getId(),
                hoveredCellSlotIndex,
                false
            ));

            return true;
        }

        // Regular click on storage header: upgrade first cell in that storage
        if (hoveredStorageLine != null) {
            CellInfo targetCell = findFirstCellInStorageThatCanAcceptUpgrade(hoveredStorageLine, heldStack);
            if (targetCell == null) return false;

            CellTerminalNetwork.INSTANCE.sendToServer(new PacketUpgradeCell(
                hoveredStorageLine.getId(),
                targetCell.getSlot(),
                false
            ));

            return true;
        }

        return false;
    }

    /**
     * Handle upgrade click on storage bus headers when player is holding an upgrade item.
     * @return true if an upgrade click was handled
     */
    protected boolean handleStorageBusUpgradeClick() {
        if (hoveredStorageBus == null) return false;

        ItemStack heldStack = Minecraft.getMinecraft().player.inventory.getItemStack();
        if (heldStack.isEmpty()) return false;
        if (!(heldStack.getItem() instanceof IUpgradeModule)) return false;

        CellTerminalNetwork.INSTANCE.sendToServer(new PacketUpgradeStorageBus(hoveredStorageBus.getId()));

        return true;
    }

    /**
     * Find the first visible cell that can accept the given upgrade.
     * Respects current tab's filtered and sorted line list.
     * Checks both compatibility (upgrade type supported by cell) and available space.
     * @param upgradeStack The upgrade item to check compatibility with
     * @return The first CellInfo that can accept the upgrade, or null if none found
     */
    protected CellInfo findFirstVisibleCellThatCanAcceptUpgrade(ItemStack upgradeStack) {
        List<Object> lines;

        switch (currentTab) {
            case TAB_TERMINAL:
                lines = dataManager.getLines();
                break;
            case TAB_INVENTORY:
                lines = dataManager.getInventoryLines();
                break;
            case TAB_PARTITION:
                lines = dataManager.getPartitionLines();
                break;
            default:
                return null;
        }

        // Track cells we've already checked to avoid duplicate checks
        java.util.Set<Long> checkedCellIds = new java.util.HashSet<>();

        for (Object line : lines) {
            CellInfo cell = null;

            if (line instanceof CellInfo) {
                cell = (CellInfo) line;
            } else if (line instanceof CellContentRow) {
                // Tab 2/3 use CellContentRow which wraps CellInfo
                cell = ((CellContentRow) line).getCell();
            }

            if (cell == null) continue;

            // Skip if we've already checked this cell (Tab 2/3 have multiple rows per cell)
            long cellId = cell.getParentStorageId() * 100 + cell.getSlot();
            if (checkedCellIds.contains(cellId)) continue;
            checkedCellIds.add(cellId);

            if (cell.canAcceptUpgrade(upgradeStack)) return cell;
        }

        return null;
    }

    /**
     * Find the first cell in a specific storage that can accept the given upgrade.
     * @param storage The storage to search in
     * @param upgradeStack The upgrade item to check compatibility with
     * @return The first CellInfo that can accept the upgrade, or null if none found
     */
    protected CellInfo findFirstCellInStorageThatCanAcceptUpgrade(StorageInfo storage, ItemStack upgradeStack) {
        for (CellInfo cell : storage.getCells()) {
            if (cell.canAcceptUpgrade(upgradeStack)) return cell;
        }

        return null;
    }

    /**
     * Scroll to a specific line index.
     */
    protected void scrollToLine(int lineIndex) {
        int currentScroll = this.getScrollBar().getCurrentScroll();

        // wheel() clamps delta to -1/+1, so we need to call it multiple times
        // Positive delta in wheel() scrolls up (towards 0), negative scrolls down
        while (currentScroll < lineIndex) {
            this.getScrollBar().wheel(-1);
            int newScroll = this.getScrollBar().getCurrentScroll();

            if (newScroll == currentScroll) break; // Can't scroll further

            currentScroll = newScroll;
        }

        while (currentScroll > lineIndex) {
            this.getScrollBar().wheel(1);
            int newScroll = this.getScrollBar().getCurrentScroll();

            if (newScroll == currentScroll) break; // Can't scroll further

            currentScroll = newScroll;
        }
    }

    public void postUpdate(NBTTagCompound data) {
        dataManager.processUpdate(data);
        updateScrollbarForCurrentTab();
    }

    protected void updateScrollbarForCurrentTab() {
        int lineCount = dataManager.getLineCount(currentTab);
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
