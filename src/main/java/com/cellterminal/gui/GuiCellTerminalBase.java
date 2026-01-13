package com.cellterminal.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.AEApi;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.AppEngSlot;

import mezz.jei.api.gui.IGhostIngredientHandler;

import com.cellterminal.client.CellInfo;
import com.cellterminal.client.CellTerminalClientConfig;
import com.cellterminal.client.CellTerminalClientConfig.TerminalStyle;
import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.client.StorageInfo;
import com.cellterminal.gui.handler.JeiGhostHandler;
import com.cellterminal.gui.handler.JeiGhostHandler.PartitionSlotTarget;
import com.cellterminal.gui.handler.TerminalClickHandler;
import com.cellterminal.gui.handler.TerminalDataManager;
import com.cellterminal.gui.render.InventoryTabRenderer;
import com.cellterminal.gui.render.PartitionTabRenderer;
import com.cellterminal.gui.render.RenderContext;
import com.cellterminal.gui.render.TerminalTabRenderer;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketPartitionAction;


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
    private static final int TAB_COUNT = 3;
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

    // Popup states
    protected PopupCellInventory inventoryPopup = null;
    protected PopupCellPartition partitionPopup = null;
    protected CellInfo hoveredCell = null;
    protected int hoverType = 0; // 0=none, 1=inventory, 2=partition, 3=eject

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

        // Update scrollbar range for current tab
        updateScrollbarForCurrentTab();
    }

    protected void initRenderers() {
        this.terminalRenderer = new TerminalTabRenderer(this.fontRenderer, this.itemRender);
        this.inventoryRenderer = new InventoryTabRenderer(this.fontRenderer, this.itemRender);
        this.partitionRenderer = new PartitionTabRenderer(this.fontRenderer, this.itemRender);
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
     */
    protected SearchFilterMode getEffectiveSearchMode() {
        switch (currentTab) {
            case TAB_INVENTORY:
                return SearchFilterMode.INVENTORY;
            case TAB_PARTITION:
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
    }

    protected void repositionSlots() {
        for (Object obj : this.inventorySlots.inventorySlots) {
            if (obj instanceof AppEngSlot) {
                AppEngSlot slot = (AppEngSlot) obj;
                slot.yPos = this.ySize + slot.getY() - 81;
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

        int relMouseX = mouseX - offsetX;
        int relMouseY = mouseY - offsetY;
        final int currentScroll = this.getScrollBar().getCurrentScroll();

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
        }

        syncHoverStateFromContext();
    }

    protected void syncHoverStateFromContext() {
        this.hoveredCell = renderContext.hoveredCell;
        this.hoverType = renderContext.hoverType;
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

        this.partitionSlotTargets.clear();
        for (RenderContext.PartitionSlotTarget target : renderContext.partitionSlotTargets) {
            this.partitionSlotTargets.add(new PartitionSlotTarget(
                target.cell, target.slotIndex, target.x, target.y, target.width, target.height));
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

        for (int i = 0; i < 3; i++) {
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

            // Draw icon
            ItemStack icon = getTabIcon(i);
            if (!icon.isEmpty()) {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                RenderHelper.enableGUIStandardItemLighting();
                this.itemRender.renderItemIntoGUI(icon, tabX + 3, tabY + 3);
                RenderHelper.disableStandardItemLighting();
                GlStateManager.disableLighting();
            }
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
    }

    protected String getTabTooltip(int tab) {
        switch (tab) {
            case TAB_TERMINAL:
                return I18n.format("gui.cellterminal.tab.terminal.tooltip");
            case TAB_INVENTORY:
                return I18n.format("gui.cellterminal.tab.inventory.tooltip");
            case TAB_PARTITION:
                return I18n.format("gui.cellterminal.tab.partition.tooltip");
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
            default:
                return ItemStack.EMPTY;
        }
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

        // Handle popup clicks first
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
            clickHandler.handleCellTabClick(currentTab, hoveredCellCell, hoveredContentSlotIndex,
                hoveredPartitionCell, hoveredPartitionSlotIndex, hoveredCellStorage, hoveredCellSlotIndex,
                createClickCallback());
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

        // Handle search field keyboard input first
        if (this.searchField != null && this.searchField.textboxKeyTyped(typedChar, keyCode)) return;

        super.keyTyped(typedChar, keyCode);
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

    // JEI Ghost Ingredient support

    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        return JeiGhostHandler.getPhantomTargets(currentTab, partitionPopup, partitionSlotTargets,
            (cell, slotIndex, stack) -> onAddPartitionItem(cell, slotIndex, stack));
    }

    // Accessors for popups and renderers

    public Map<Long, StorageInfo> getStorageMap() {
        return dataManager.getStorageMap();
    }
}
