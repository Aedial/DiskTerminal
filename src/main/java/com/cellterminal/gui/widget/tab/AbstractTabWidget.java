package com.cellterminal.gui.widget.tab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import mezz.jei.api.gui.IGhostIngredientHandler;

import com.cellterminal.client.CellInfo;
import com.cellterminal.client.CellContentRow;
import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.client.StorageInfo;
import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.PriorityFieldManager;
import com.cellterminal.gui.handler.TerminalDataManager;
import com.cellterminal.gui.rename.Renameable;
import com.cellterminal.gui.widget.AbstractWidget;
import com.cellterminal.gui.widget.CardsDisplay;
import com.cellterminal.gui.widget.IWidget;
import com.cellterminal.gui.widget.line.AbstractLine;
import com.cellterminal.gui.widget.line.SlotsLine;
import com.cellterminal.gui.widget.header.AbstractHeader;


/**
 * Base class for all tab container widgets in the Cell Terminal GUI.
 *
 * A tab widget manages the visible window of rows in the scrollable content area.
 * Each frame, it builds a list of {@link IWidget} instances (headers and lines)
 * for the currently visible scroll region and draws them in order.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Translate data objects to header/line widgets for each visible row</li>
 *   <li>Propagate tree line cut-Y values between consecutive rows</li>
 *   <li>Set header connector state based on content below</li>
 *   <li>Delegate draw/click/tooltip/key events to visible row widgets</li>
 * </ul>
 *
 * <h3>Tree line propagation</h3>
 * After all visible row widgets are created, {@link #propagateTreeLines(List, int)}
 * iterates them and chains the cut-Y values:
 * <ol>
 *   <li>Headers expose {@link AbstractHeader#getConnectorY()} as the starting cut-Y</li>
 *   <li>Lines receive the previous row's cut-Y via
 *       {@link AbstractLine#setTreeLineParams(boolean, int)}</li>
 *   <li>Lines expose {@link AbstractLine#getTreeLineCutY()} for the next row</li>
 *   <li>The first visible row uses CONTENT_START_Y as lineAboveCutY when content
 *       exists above the scroll window</li>
 * </ol>
 *
 * <h3>Scroll integration</h3>
 * The tab does not own the scroll position — that is managed by the parent GUI.
 * The tab receives the scroll offset and visible row count, and builds widgets
 * accordingly, positioned at CONTENT_START_Y + i * ROW_HEIGHT.
 *
 * @see com.cellterminal.gui.widget.header.AbstractHeader
 * @see com.cellterminal.gui.widget.line.AbstractLine
 */
public abstract class AbstractTabWidget extends AbstractWidget {

    protected final FontRenderer fontRenderer;
    protected final RenderItem itemRender;

    /** Absolute GUI position offsets (needed for JEI targets and priority fields) */
    protected int guiLeft;
    protected int guiTop;

    /** Number of rows visible in the scroll window */
    protected int rowsVisible = GuiConstants.DEFAULT_ROWS;

    /**
     * Visible row widgets for the current frame.
     * Rebuilt each frame by {@link #buildVisibleRows(List, int)}.
     */
    protected final List<IWidget> visibleRows = new ArrayList<>();

    /**
     * Maps each visible row widget to its source data object (the lineData from buildVisibleRows).
     * Used by the parent GUI to identify what data is under the mouse for upgrade insertion,
     * inline rename, priority field positioning, etc.
     */
    protected final Map<IWidget, Object> widgetDataMap = new HashMap<>();

    protected AbstractTabWidget(FontRenderer fontRenderer, RenderItem itemRender) {
        super(0, GuiConstants.CONTENT_START_Y,
            GuiConstants.CONTENT_RIGHT_EDGE, GuiConstants.DEFAULT_ROWS * GuiConstants.ROW_HEIGHT);
        this.fontRenderer = fontRenderer;
        this.itemRender = itemRender;
    }

    // ---- Configuration ----

    public void setGuiOffsets(int guiLeft, int guiTop) {
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
    }

    public void setRowsVisible(int rowsVisible) {
        this.rowsVisible = rowsVisible;
        this.height = rowsVisible * GuiConstants.ROW_HEIGHT;
    }

    // ---- Row building ----

    /**
     * Build the list of visible row widgets for the current scroll window.
     * Called once per frame before draw. Subclasses implement
     * {@link #createRowWidget(Object, int)} to map line data → widget.
     *
     * @param lines All line data objects for this tab (from DataManager)
     * @param scrollOffset The current scroll position (index of first visible line)
     */
    public void buildVisibleRows(List<?> lines, int scrollOffset) {
        visibleRows.clear();
        widgetDataMap.clear();

        int end = Math.min(scrollOffset + rowsVisible, lines.size());
        for (int i = scrollOffset; i < end; i++) {
            int rowY = GuiConstants.CONTENT_START_Y + (i - scrollOffset) * GuiConstants.ROW_HEIGHT;
            Object lineData = lines.get(i);
            IWidget widget = createRowWidget(lineData, rowY, lines, i);
            if (widget != null) {
                visibleRows.add(widget);
                widgetDataMap.put(widget, lineData);
            }
        }

        propagateTreeLines(lines, scrollOffset);
    }

    /**
     * Create a widget for a single line data object.
     * Subclasses map their data types (StorageInfo, CellInfo, CellContentRow, etc.)
     * to the appropriate widget class, configuring suppliers and callbacks.
     *
     * @param lineData The data object for this row
     * @param y The Y position for this row (CONTENT_START_Y + offset * ROW_HEIGHT)
     * @param allLines All lines in the tab (for look-ahead when setting header state)
     * @param lineIndex Index of lineData in allLines
     * @return A configured widget, or null to skip this row
     */
    protected abstract IWidget createRowWidget(Object lineData, int y, List<?> allLines, int lineIndex);

    /**
     * Propagate tree line cut-Y values between consecutive visible rows.
     * Handles edge cases:
     * <ul>
     *   <li>First visible row with content above: lineAboveCutY = CONTENT_START_Y</li>
     *   <li>Header → first content line: lineAboveCutY = header's connectorY</li>
     *   <li>Content → content: lineAboveCutY = previous line's getTreeLineCutY()</li>
     * </ul>
     */
    protected void propagateTreeLines(List<?> allLines, int scrollOffset) {
        // Track the "cut Y" from the previous row.
        // If scrolled and previous (off-screen) row is in the same group,
        // the vertical line should start from the top of the visible area.
        int lastCutY = GuiConstants.CONTENT_START_Y;
        boolean hasContentAbove = scrollOffset > 0 && isContentLine(allLines, scrollOffset - 1);

        for (int i = 0; i < visibleRows.size(); i++) {
            IWidget widget = visibleRows.get(i);

            if (widget instanceof AbstractHeader) {
                AbstractHeader header = (AbstractHeader) widget;
                // Header's connector state: is the next visible line a content row?
                boolean contentBelow = hasContentBelow(allLines, scrollOffset + i);
                header.setDrawConnector(contentBelow);
                lastCutY = header.getConnectorY();

            } else if (widget instanceof AbstractLine) {
                AbstractLine line = (AbstractLine) widget;

                // For the first visible row that is a content line with content above,
                // draw tree line from top of visible area
                if (i == 0 && hasContentAbove) {
                    line.setTreeLineParams(true, GuiConstants.CONTENT_START_Y);
                } else {
                    line.setTreeLineParams(true, lastCutY);
                }

                lastCutY = line.getTreeLineCutY();
            }
        }
    }

    /**
     * Check whether a line at the given index is a content line (not a header).
     * Subclasses override to know their data types.
     */
    protected abstract boolean isContentLine(List<?> allLines, int index);

    /**
     * Check whether the line after the given index is a content line
     * (used to set header drawConnector).
     */
    protected boolean hasContentBelow(List<?> allLines, int currentIndex) {
        int nextIndex = currentIndex + 1;
        if (nextIndex >= allLines.size()) return false;

        return isContentLine(allLines, nextIndex);
    }

    // ---- Drawing ----

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        // All widgets in visibleRows are guaranteed non-null and visible
        for (IWidget widget : visibleRows) {
            widget.draw(mouseX, mouseY);
        }
    }

    // ---- Event handling ----

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible) return false;

        // Process clicks in reverse order (last drawn = on top = gets first click)
        for (int i = visibleRows.size() - 1; i >= 0; i--) {
            IWidget widget = visibleRows.get(i);
            if (!widget.isHovered(mouseX, mouseY)) continue;
            if (widget.handleClick(mouseX, mouseY, button)) return true;
        }

        return false;
    }

    @Override
    public boolean handleKey(char typedChar, int keyCode) {
        if (!visible) return false;

        // First let visible row widgets handle the key (e.g., inline editors)
        for (int i = visibleRows.size() - 1; i >= 0; i--) {
            IWidget widget = visibleRows.get(i);
            if (widget.handleKey(typedChar, keyCode)) return true;
        }

        // Then try tab-specific keybinds (quick-partition, add-to-bus, etc.)
        return handleTabKeyTyped(keyCode);
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible) return Collections.emptyList();

        for (int i = visibleRows.size() - 1; i >= 0; i--) {
            IWidget widget = visibleRows.get(i);
            if (!widget.isHovered(mouseX, mouseY)) continue;

            List<String> tooltip = widget.getTooltip(mouseX, mouseY);
            if (!tooltip.isEmpty()) return tooltip;
        }

        return Collections.emptyList();
    }

    @Override
    public ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        if (!visible) return ItemStack.EMPTY;

        for (int i = visibleRows.size() - 1; i >= 0; i--) {
            IWidget widget = visibleRows.get(i);
            if (!widget.isHovered(mouseX, mouseY)) continue;

            ItemStack stack = widget.getHoveredItemStack(mouseX, mouseY);
            if (!stack.isEmpty()) return stack;
        }

        return ItemStack.EMPTY;
    }

    // ---- Cards helper ----

    /**
     * Create a CardsDisplay for a cell's upgrade icons, positioned at the left margin.
     * Shared by TerminalTabWidget, CellContentTabWidget, and TempAreaTabWidget.
     *
     * @param cell The cell whose upgrades to display
     * @param rowY The Y position of the row this card display belongs to
     * @param cardClickCallback Click handler receiving (cell, upgradeSlotIndex), or null
     * @return A configured CardsDisplay, or null if no upgrades
     */
    protected CardsDisplay createCellCardsDisplay(CellInfo cell, int rowY,
                                                   BiConsumer<CellInfo, Integer> cardClickCallback) {
        List<ItemStack> upgrades = cell.getUpgrades();
        if (upgrades.isEmpty()) return null;

        List<CardsDisplay.CardEntry> entries = new ArrayList<>();
        for (int i = 0; i < upgrades.size(); i++) {
            entries.add(new CardsDisplay.CardEntry(upgrades.get(i), cell.getUpgradeSlotIndex(i)));
        }

        CardsDisplay cards = new CardsDisplay(
            GuiConstants.CARDS_X, rowY,
            () -> entries,
            fontRenderer, itemRender
        );

        if (cardClickCallback != null) {
            cards.setClickCallback(slotIndex -> cardClickCallback.accept(cell, slotIndex));
        }

        return cards;
    }

    // ---- JEI integration ----

    /**
     * Collect all JEI partition slot targets from visible SlotsLine widgets.
     * The parent GUI calls this to provide ghost ingredient targets.
     *
     * @return Unmodifiable list of partition slot targets
     */
    public List<SlotsLine.PartitionSlotTarget> getPartitionTargets() {
        List<SlotsLine.PartitionSlotTarget> targets = new ArrayList<>();

        for (IWidget widget : visibleRows) {
            if (widget instanceof SlotsLine) {
                targets.addAll(((SlotsLine) widget).getPartitionTargets());
            }
        }

        return Collections.unmodifiableList(targets);
    }

    /**
     * Get the list of visible row widgets. Useful for the parent GUI
     * to iterate for priority field positioning and other tasks.
     */
    public List<IWidget> getVisibleRows() {
        return Collections.unmodifiableList(visibleRows);
    }

    /**
     * Get the source data object for the widget under the mouse cursor.
     * Used by the parent GUI for upgrade insertion, inline rename, and
     * other interactions that need to know the original data context.
     *
     * @return The data object, or null if nothing is hovered
     */
    public Object getDataForHoveredRow(int mouseX, int mouseY) {
        for (int i = visibleRows.size() - 1; i >= 0; i--) {
            IWidget widget = visibleRows.get(i);
            if (widget.isHovered(mouseX, mouseY)) return widgetDataMap.get(widget);
        }

        return null;
    }

    /**
     * Get the data map from widgets to their source data objects.
     * Used for priority field positioning — the parent GUI iterates
     * visible rows and maps headers back to their StorageInfo/StorageBusInfo objects.
     */
    public Map<IWidget, Object> getWidgetDataMap() {
        return Collections.unmodifiableMap(widgetDataMap);
    }

    // ========================================================================
    // Tab controller responsibilities
    // ========================================================================

    /** The GUI context, set once during initialization via {@link #init(GuiContext)}. */
    protected GuiContext guiContext;

    /**
     * Initialize this tab widget with its GUI context.
     * Called once during {@code initTabWidgets()} in the parent GUI.
     * Subclasses should wire their internal callbacks in this method.
     */
    public void init(GuiContext context) {
        this.guiContext = context;
    }

    /**
     * Get the lines data for this tab from the data manager.
     * Each tab knows which line list it needs.
     */
    public abstract List<Object> getLines(TerminalDataManager dataManager);

    /**
     * Get the effective search mode when this tab is active.
     * Tabs that force a specific mode (e.g., Partition forces PARTITION)
     * override this to ignore the user-selected mode.
     *
     * @param userSelectedMode The mode the user has selected in the search button
     * @return The effective search filter mode for this tab
     */
    public SearchFilterMode getEffectiveSearchMode(SearchFilterMode userSelectedMode) {
        return userSelectedMode;
    }

    /**
     * Whether the search mode button should be visible when this tab is active.
     * Tabs that force a search mode should return false.
     */
    public boolean showSearchModeButton() {
        return true;
    }

    /**
     * Get the controls help text lines for this tab.
     * Displayed in the controls help widget to the left of the GUI.
     *
     * @return List of localized help text lines (empty strings for spacing)
     */
    public abstract List<String> getHelpLines();

    /**
     * Get the icon ItemStack to display on this tab's button.
     *
     * @return The tab icon, or ItemStack.EMPTY for no icon
     */
    public abstract ItemStack getTabIcon();

    /**
     * Get the tooltip text for this tab's button.
     *
     * @return The localized tooltip text
     */
    public abstract String getTabTooltip();

    /**
     * Handle a tab-specific keybind.
     * Called when no higher-priority handler (rename, priority, search, modals) consumed the key.
     *
     * @param keyCode The key code that was pressed
     * @return true if the key was handled
     */
    public boolean handleTabKeyTyped(int keyCode) {
        return false;
    }

    /**
     * Whether this tab requires server-side polling for data updates.
     */
    public boolean requiresServerPolling() {
        return false;
    }

    // ---- Rename support ----

    /**
     * Get the hovered renameable target for right-click rename.
     * Subclasses determine which data types are renameable and what exclusion zones exist
     * (e.g., don't rename when clicking on buttons).
     *
     * @param relMouseX Mouse X relative to GUI left edge
     * @param relMouseY Mouse Y relative to GUI top edge
     * @return The hovered Renameable, or null
     */
    public Renameable getHoveredRenameable(int relMouseX, int relMouseY) {
        Object hoveredData = getDataForHoveredRow(relMouseX, relMouseY);
        if (hoveredData == null) return null;

        return resolveRenameable(hoveredData, relMouseX);
    }

    /**
     * Resolve a data object to a Renameable target, applying exclusion zones.
     * Subclasses override to handle their specific data types and button areas.
     *
     * @param data The data object under the mouse
     * @param relMouseX Mouse X relative to GUI left edge (for button exclusion)
     * @return The Renameable, or null if clicking in a non-renameable zone
     */
    protected Renameable resolveRenameable(Object data, int relMouseX) {
        if (data instanceof StorageInfo && relMouseX < GuiConstants.BUTTON_PARTITION_X) {
            return (StorageInfo) data;
        }

        return null;
    }

    /**
     * Get the X position for the inline rename field for a given target.
     * Cells are more indented (on tree branch) than storages/buses.
     */
    public int getRenameFieldX(Renameable target) {
        if (target instanceof CellInfo) return GuiConstants.CELL_INDENT + 18 - 2;

        // StorageInfo and StorageBusInfo: name drawn at GUI_INDENT + 20
        return GuiConstants.GUI_INDENT + 20 - 2;
    }

    /**
     * Get the right edge for the inline rename field for a given target.
     * Subclasses override based on which buttons are present on that tab.
     */
    public int getRenameFieldRightEdge(Renameable target) {
        return GuiConstants.CONTENT_RIGHT_EDGE - PriorityFieldManager.FIELD_WIDTH - PriorityFieldManager.RIGHT_MARGIN - 4;
    }

    // ---- Upgrade support ----

    /**
     * Handle an upgrade item click on a hovered row.
     * Called when the player is holding an upgrade and left-clicks in the content area.
     * Subclasses override to handle their specific data types.
     *
     * @param hoveredData The data object under the mouse
     * @param heldStack The upgrade item the player is holding
     * @param isShiftClick Whether shift is held
     * @return true if the click was handled
     */
    public boolean handleUpgradeClick(Object hoveredData, ItemStack heldStack, boolean isShiftClick) {
        // Default: try cell-based upgrade insertion
        if (hoveredData instanceof CellInfo) {
            CellInfo cell = (CellInfo) hoveredData;
            if (!cell.canAcceptUpgrade(heldStack)) return false;

            StorageInfo storage = guiContext.getDataManager().getStorageMap().get(cell.getParentStorageId());
            if (storage == null) return false;

            guiContext.sendPacket(new com.cellterminal.network.PacketUpgradeCell(
                storage.getId(), cell.getSlot(), false));

            return true;
        }

        if (hoveredData instanceof StorageInfo) {
            StorageInfo storage = (StorageInfo) hoveredData;
            // Check if any cell could potentially accept (client-side heuristic)
            boolean anyCanAccept = storage.getCells().stream()
                .anyMatch(cell -> cell.canAcceptUpgrade(heldStack));
            if (!anyCanAccept) return false;

            // Let the server iterate through cells and find one that actually accepts
            // Use shiftClick=true so server handles the cell selection
            guiContext.sendPacket(new com.cellterminal.network.PacketUpgradeCell(
                storage.getId(), -1, true));

            return true;
        }

        if (hoveredData instanceof CellContentRow) {
            CellInfo cell = ((CellContentRow) hoveredData).getCell();
            if (cell != null && cell.canAcceptUpgrade(heldStack)) {
                StorageInfo storage = guiContext.getDataManager().getStorageMap().get(cell.getParentStorageId());
                if (storage != null) {
                    guiContext.sendPacket(new com.cellterminal.network.PacketUpgradeCell(
                        storage.getId(), cell.getSlot(), false));

                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Handle shift-click upgrade insertion (find first visible target).
     * Subclasses override for bus-based tabs.
     *
     * @param heldStack The upgrade item
     * @return true if handled
     */
    public boolean handleShiftUpgradeClick(ItemStack heldStack) {
        // Default: scan all visible cells for first that can accept
        List<Object> lines = getLines(guiContext.getDataManager());
        for (Object line : lines) {
            if (line instanceof CellInfo) {
                CellInfo cell = (CellInfo) line;
                if (cell.canAcceptUpgrade(heldStack)) {
                    StorageInfo storage = guiContext.getDataManager().getStorageMap().get(cell.getParentStorageId());
                    if (storage != null) {
                        guiContext.sendPacket(new com.cellterminal.network.PacketUpgradeCell(
                            storage.getId(), cell.getSlot(), false));

                        return true;
                    }
                }
            }
        }

        return false;
    }

    // ---- JEI ghost targets ----

    /**
     * Get JEI ghost ingredient targets for this tab.
     * Only partition-related tabs provide targets. Default returns empty list.
     */
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        return Collections.emptyList();
    }
}
