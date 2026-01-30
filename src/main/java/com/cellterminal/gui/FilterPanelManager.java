package com.cellterminal.gui;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;

import com.cellterminal.client.CellFilter;
import com.cellterminal.client.CellFilter.State;
import com.cellterminal.client.SlotLimit;
import com.cellterminal.config.CellTerminalClientConfig;
import com.cellterminal.integration.ThaumicEnergisticsIntegration;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketSlotLimitChange;


/**
 * Manages the layout and positioning of filter buttons.
 * Handles dynamic placement based on available space and controls help widget.
 *
 * Positioning strategy:
 * - Buttons start below the terminal style button
 * - Stack vertically in a single column by default
 * - If not enough space, stack in 2 columns
 * - If still not enough space, arrange horizontally
 * - If still not enough space, push upwards from controls help widget
 */
public class FilterPanelManager {

    private static final int BUTTON_SIZE = GuiFilterButton.BUTTON_SIZE;
    private static final int BUTTON_SPACING = 2;
    private static final int BUTTON_WITH_SPACING = BUTTON_SIZE + BUTTON_SPACING;

    // Margin from edges
    private static final int TOP_MARGIN = 4;
    private static final int BOTTOM_MARGIN = 8;

    private final List<GuiFilterButton> filterButtons = new ArrayList<>();
    private final Map<CellFilter, GuiFilterButton> filterButtonMap = new EnumMap<>(CellFilter.class);

    private GuiSlotLimitButton slotLimitButton = null;

    private boolean forStorageBus = false;

    /**
     * Initialize filter buttons for the given tab.
     * Creates buttons based on which filters are applicable to the current tab.
     *
     * @param buttonList The GUI's button list to add buttons to
     * @param startButtonId Starting button ID
     * @param currentTab The current tab (0-4)
     * @return The next available button ID
     */
    public int initButtons(List<GuiButton> buttonList, int startButtonId, int currentTab) {
        // Remove old buttons from list
        buttonList.removeAll(filterButtons);
        filterButtons.clear();
        filterButtonMap.clear();

        // Remove old slot limit button
        if (slotLimitButton != null) buttonList.remove(slotLimitButton);

        this.forStorageBus = currentTab >= 3;
        CellTerminalClientConfig config = CellTerminalClientConfig.getInstance();

        int buttonId = startButtonId;

        // Create slot limit button first (only for tabs that show content)
        if (currentTab == GuiConstants.TAB_INVENTORY || currentTab == GuiConstants.TAB_STORAGE_BUS_INVENTORY) {
            SlotLimit limit = config.getSlotLimit(forStorageBus);
            slotLimitButton = new GuiSlotLimitButton(buttonId++, 0, 0, limit);
            buttonList.add(slotLimitButton);
        } else {
            slotLimitButton = null;
        }

        List<CellFilter> applicableFilters = getApplicableFilters(currentTab);

        for (CellFilter filter : applicableFilters) {
            State state = config.getFilterState(filter, forStorageBus);
            GuiFilterButton button = new GuiFilterButton(buttonId++, 0, 0, filter, state);
            filterButtons.add(button);
            filterButtonMap.put(filter, button);
            buttonList.add(button);
        }

        return buttonId;
    }

    /**
     * Get the list of filters applicable to the current tab.
     */
    private List<CellFilter> getApplicableFilters(int tab) {
        List<CellFilter> filters = new ArrayList<>();

        // Tabs 0-2 are cell tabs, 3-4 are storage bus tabs
        boolean isCellTab = tab <= 2;
        boolean isStorageBusTab = tab >= 3;

        // Cell type filters
        if (isCellTab || isStorageBusTab) {
            filters.add(CellFilter.ITEM_CELLS);
            filters.add(CellFilter.FLUID_CELLS);

            if (ThaumicEnergisticsIntegration.isModLoaded()) filters.add(CellFilter.ESSENTIA_CELLS);
        }

        // Content-based filters
        filters.add(CellFilter.HAS_ITEMS);
        filters.add(CellFilter.PARTITIONED);

        return filters;
    }

    /**
     * Update button positions based on available space.
     *
     * @param guiLeft GUI left edge X coordinate
     * @param guiTop GUI top edge Y coordinate
     * @param ySize GUI height
     * @param styleButtonY Y coordinate of the terminal style button (top)
     * @param styleButtonBottom Y coordinate of the terminal style button (bottom)
     * @param controlsHelpBounds Bounding rectangle of controls help widget (or empty if none)
     */
    public void updatePositions(int guiLeft, int guiTop, int ySize,
                                 int styleButtonY, int styleButtonBottom, Rectangle controlsHelpBounds) {
        // Count all buttons (slot limit + filters)
        int buttonCount = filterButtons.size();
        if (slotLimitButton != null) buttonCount++;

        if (buttonCount == 0) return;

        // Available space calculation
        int panelX = guiLeft - BUTTON_SIZE - 2;  // Same X as terminal style button
        int availableTop = styleButtonBottom + TOP_MARGIN;
        int availableBottom;

        if (controlsHelpBounds != null && controlsHelpBounds.height > 0) {
            // Controls help widget exists - don't overlap it
            availableBottom = controlsHelpBounds.y - BOTTOM_MARGIN;
        } else {
            // No controls help - use bottom of GUI area
            availableBottom = guiTop + ySize - BOTTOM_MARGIN;
        }

        int availableHeight = availableBottom - availableTop;

        // Determine layout strategy
        LayoutResult layout = calculateLayout(buttonCount, availableHeight, guiLeft);
        applyLayout(layout, panelX, availableTop, availableBottom, styleButtonY, guiLeft);
    }

    private static class LayoutResult {
        int columns;
        int rows;
        boolean horizontal;
        boolean pushUp;
        int requiredHeight;
    }

    private LayoutResult calculateLayout(int buttonCount, int availableHeight, int guiLeft) {
        LayoutResult result = new LayoutResult();

        // Strategy 1: Single column vertical
        int singleColHeight = buttonCount * BUTTON_WITH_SPACING - BUTTON_SPACING;
        if (singleColHeight <= availableHeight) {
            result.columns = 1;
            result.rows = buttonCount;
            result.horizontal = false;
            result.pushUp = false;
            result.requiredHeight = singleColHeight;

            return result;
        }

        // Strategy 2: Two columns vertical
        int twoColRows = (buttonCount + 1) / 2;
        int twoColHeight = twoColRows * BUTTON_WITH_SPACING - BUTTON_SPACING;
        if (twoColHeight <= availableHeight) {
            result.columns = 2;
            result.rows = twoColRows;
            result.horizontal = false;
            result.pushUp = false;
            result.requiredHeight = twoColHeight;

            return result;
        }

        // Strategy 3: Horizontal row (check if we have enough width)
        int horizontalWidth = buttonCount * BUTTON_WITH_SPACING - BUTTON_SPACING;
        int availableWidth = guiLeft - 4;  // Space to the left of GUI
        if (horizontalWidth <= availableWidth && BUTTON_SIZE <= availableHeight) {
            result.columns = buttonCount;
            result.rows = 1;
            result.horizontal = true;
            result.pushUp = false;
            result.requiredHeight = BUTTON_SIZE;

            return result;
        }

        // Strategy 4: Push upward (use two columns, start from bottom)
        result.columns = 2;
        result.rows = twoColRows;
        result.horizontal = false;
        result.pushUp = true;
        result.requiredHeight = twoColHeight;

        return result;
    }

    private void applyLayout(LayoutResult layout, int panelX, int availableTop,
                              int availableBottom, int styleButtonY, int guiLeft) {
        int startY;
        int startX;

        // Count total buttons for horizontal layout
        int totalButtonCount = filterButtons.size();
        if (slotLimitButton != null) totalButtonCount++;

        if (layout.pushUp) {
            // Start from bottom, going up
            startY = availableBottom - layout.requiredHeight;
            startX = panelX;

            // For 2 columns in pushUp mode, also adjust X
            if (layout.columns == 2) {
                startX = panelX - BUTTON_WITH_SPACING;
            }
        } else if (layout.horizontal) {
            // Horizontal layout - center or right-align
            int totalWidth = totalButtonCount * BUTTON_WITH_SPACING - BUTTON_SPACING;
            startX = guiLeft - totalWidth - 2;
            startY = availableTop;
        } else {
            // Normal vertical layout
            startX = panelX;
            startY = availableTop;

            // For 2 columns, adjust X to fit both columns
            if (layout.columns == 2) {
                startX = panelX - BUTTON_WITH_SPACING;
            }
        }

        // Style button forbidden zone (with margin)
        int styleButtonTop = styleButtonY - BUTTON_SPACING;
        int styleButtonBottom = styleButtonY + BUTTON_SIZE + BUTTON_SPACING;

        int index = 0;

        // Position slot limit button first (if present)
        if (slotLimitButton != null) {
            int[] pos = calculateButtonPosition(index, layout, startX, startY, guiLeft,
                                                 styleButtonTop, styleButtonBottom);
            slotLimitButton.x = pos[0];
            slotLimitButton.y = pos[1];
            index++;
        }

        // Position filter buttons
        for (GuiFilterButton button : filterButtons) {
            int[] pos = calculateButtonPosition(index, layout, startX, startY, guiLeft,
                                                 styleButtonTop, styleButtonBottom);
            button.x = pos[0];
            button.y = pos[1];
            index++;
        }
    }

    /**
     * Calculate the position for a button at the given index.
     * @return array of [x, y]
     */
    private int[] calculateButtonPosition(int index, LayoutResult layout, int startX, int startY, int guiLeft,
                                           int styleButtonTop, int styleButtonBottom) {
        int col, row;

        if (layout.horizontal) {
            col = index;
            row = 0;
        } else {
            col = index % layout.columns;
            row = index / layout.columns;
        }

        int buttonX, buttonY;

        if (layout.horizontal) {
            buttonX = startX + col * BUTTON_WITH_SPACING;
            buttonY = startY;
        } else {
            // For vertical layout with 2 columns, right column is col 0
            if (layout.columns == 2) {
                buttonX = startX + (1 - col) * BUTTON_WITH_SPACING;
            } else {
                buttonX = startX;
            }
            buttonY = startY + row * BUTTON_WITH_SPACING;
        }

        // Check if button overlaps with style button forbidden zone
        // If so, shift it above the style button
        if (buttonY >= styleButtonTop && buttonY < styleButtonBottom) {
            buttonY = styleButtonTop - BUTTON_SIZE;
        } else if (buttonY + BUTTON_SIZE > styleButtonTop && buttonY + BUTTON_SIZE <= styleButtonBottom) {
            buttonY = styleButtonTop - BUTTON_SIZE;
        }

        return new int[] { buttonX, buttonY };
    }

    /**
     * Get the bounding rectangle of the filter panel for JEI exclusion.
     */
    public Rectangle getBounds() {
        if (filterButtons.isEmpty() && slotLimitButton == null) return new Rectangle(0, 0, 0, 0);

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        // Include slot limit button
        if (slotLimitButton != null && slotLimitButton.visible) {
            minX = Math.min(minX, slotLimitButton.x);
            minY = Math.min(minY, slotLimitButton.y);
            maxX = Math.max(maxX, slotLimitButton.x + BUTTON_SIZE);
            maxY = Math.max(maxY, slotLimitButton.y + BUTTON_SIZE);
        }

        for (GuiFilterButton button : filterButtons) {
            if (!button.visible) continue;
            minX = Math.min(minX, button.x);
            minY = Math.min(minY, button.y);
            maxX = Math.max(maxX, button.x + BUTTON_SIZE);
            maxY = Math.max(maxY, button.y + BUTTON_SIZE);
        }

        if (minX == Integer.MAX_VALUE) return new Rectangle(0, 0, 0, 0);

        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * Get the filter state for a given filter type.
     */
    public State getFilterState(CellFilter filter) {
        GuiFilterButton button = filterButtonMap.get(filter);
        if (button != null) return button.getState();

        return State.SHOW_ALL;
    }

    /**
     * Get all current filter states as a map.
     */
    public Map<CellFilter, State> getAllFilterStates() {
        Map<CellFilter, State> states = new EnumMap<>(CellFilter.class);

        for (Map.Entry<CellFilter, GuiFilterButton> entry : filterButtonMap.entrySet()) {
            states.put(entry.getKey(), entry.getValue().getState());
        }

        return states;
    }

    /**
     * Handle a filter button click.
     * @return true if a filter button was clicked
     */
    public boolean handleClick(GuiFilterButton button) {
        if (!filterButtons.contains(button)) return false;

        State newState = button.cycleState();
        CellTerminalClientConfig.getInstance().setFilterState(button.getFilter(), newState, forStorageBus);

        return true;
    }

    /**
     * Get the filter button being hovered, if any.
     */
    public GuiFilterButton getHoveredButton(int mouseX, int mouseY) {
        for (GuiFilterButton button : filterButtons) {
            if (button.visible && mouseX >= button.x && mouseX < button.x + BUTTON_SIZE
                && mouseY >= button.y && mouseY < button.y + BUTTON_SIZE) {
                return button;
            }
        }

        return null;
    }

    /**
     * Get the slot limit button, if present.
     */
    public GuiSlotLimitButton getSlotLimitButton() {
        return slotLimitButton;
    }

    /**
     * Handle a slot limit button click.
     * @param button The clicked button
     * @return true if it was the slot limit button
     */
    public boolean handleSlotLimitClick(GuiSlotLimitButton button) {
        if (slotLimitButton != button) return false;

        SlotLimit newLimit = button.cycleLimit();
        CellTerminalClientConfig config = CellTerminalClientConfig.getInstance();

        if (forStorageBus) {
            config.setBusSlotLimit(newLimit);
        } else {
            config.setCellSlotLimit(newLimit);
        }

        // Send updated slot limits to server
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketSlotLimitChange(
            config.getCellSlotLimit().getLimit(),
            config.getBusSlotLimit().getLimit()
        ));

        return true;
    }

    /**
     * Get the current slot limit based on the current tab.
     */
    public SlotLimit getCurrentSlotLimit() {
        return CellTerminalClientConfig.getInstance().getSlotLimit(forStorageBus);
    }

    public List<GuiFilterButton> getButtons() {
        return filterButtons;
    }
}
