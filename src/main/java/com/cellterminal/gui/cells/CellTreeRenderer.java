package com.cellterminal.gui.cells;

import net.minecraft.client.gui.Gui;

import com.cellterminal.gui.GuiConstants;


/**
 * Handles rendering of tree lines connecting cells to their storage parent.
 *
 * Tree lines visually group cells under their parent storage (Drive/Chest)
 * and help users understand the hierarchy.
 */
public class CellTreeRenderer {

    /**
     * Draw tree lines connecting cells to their storage parent.
     *
     * @param lineX X position of the vertical line
     * @param y Y position of this row
     * @param isFirstRow Whether this is the first row for this cell
     * @param isFirstInGroup Whether this is the first cell in the storage group
     * @param isLastInGroup Whether this is the last cell in the storage group
     * @param visibleTop Top Y of the visible area
     * @param visibleBottom Bottom Y of the visible area
     * @param isFirstVisibleRow Whether this is the first visible row
     * @param isLastVisibleRow Whether this is the last visible row
     * @param hasContentAbove Whether there's content above that's scrolled out
     * @param hasContentBelow Whether there's content below that's scrolled out
     * @param allBranches Whether to draw a horizontal branch for every row (not just first)
     */
    public void drawTreeLines(int lineX, int y, boolean isFirstRow, boolean isFirstInGroup,
                              boolean isLastInGroup, int visibleTop, int visibleBottom,
                              boolean isFirstVisibleRow, boolean isLastVisibleRow,
                              boolean hasContentAbove, boolean hasContentBelow, boolean allBranches) {

        int lineTop = calculateLineTop(y, isFirstRow, isFirstInGroup, isFirstVisibleRow,
                                        hasContentAbove, visibleTop);
        int lineBottom = calculateLineBottom(y, isLastInGroup, isLastVisibleRow,
                                              hasContentBelow, visibleBottom);

        // Clamp lineTop to never go above visibleTop to prevent leak above GUI
        if (lineTop < visibleTop) lineTop = visibleTop;

        // Vertical line
        Gui.drawRect(lineX, lineTop, lineX + 1, lineBottom, GuiConstants.COLOR_TREE_LINE);

        // Horizontal branch (only on first row unless all branches are enabled)
        if (allBranches || isFirstRow) Gui.drawRect(lineX, y + 8, lineX + 10, y + 9, GuiConstants.COLOR_TREE_LINE);
    }

    /**
     * Draw a vertical connector line from storage header to first cell.
     */
    public void drawStorageConnector(int lineX, int storageY) {
        Gui.drawRect(lineX, storageY + GuiConstants.ROW_HEIGHT - 1,
                     lineX + 1, storageY + GuiConstants.ROW_HEIGHT, GuiConstants.COLOR_TREE_LINE);
    }

    private int calculateLineTop(int y, boolean isFirstRow, boolean isFirstInGroup,
                                  boolean isFirstVisibleRow, boolean hasContentAbove, int visibleTop) {
        if (isFirstRow) {
            // First row in group (right after header) - extend up to connect with header's segment
            if (isFirstInGroup) return y - 3;

            // First visible row with content above scrolled out
            if (isFirstVisibleRow && hasContentAbove) return visibleTop;

            // Connect to row above but don't extend too high
            return y - 4;
        }

        // Continuation row
        return isFirstVisibleRow && hasContentAbove ? visibleTop : y - 4;
    }

    private int calculateLineBottom(int y, boolean isLastInGroup, boolean isLastVisibleRow,
                                     boolean hasContentBelow, int visibleBottom) {
        if (isLastInGroup) return y + 9;
        if (isLastVisibleRow && hasContentBelow) return visibleBottom;

        return y + GuiConstants.ROW_HEIGHT;
    }
}
