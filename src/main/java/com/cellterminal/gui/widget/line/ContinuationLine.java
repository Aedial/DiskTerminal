package com.cellterminal.gui.widget.line;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import com.cellterminal.gui.GuiConstants;


/**
 * A continuation row for multi-row cells.
 * <p>
 * When a cell has more items than fit in a single row, additional rows
 * are rendered using ContinuationLine. These rows have:
 * - Tree line connectors (vertical line only, no horizontal branch)
 * - Content/partition slots only (no cell slot, no cards, no button)
 * <p>
 * Unlike first rows which draw a horizontal branch from the tree line to the
 * cell slot or junction button, continuation rows only draw the vertical
 * connector since they have no junction element to point at.
 * <p>
 * The spec states: "nothing, as it's a continuation row. No Cell, no cards, no button."
 *
 * @see CellSlotsLine
 * @see SlotsLine
 */
public class ContinuationLine extends SlotsLine {

    /**
     * Whether to draw the horizontal branch from the tree line to the row content.
     * Default is true, matching standard tree rendering. Tabs where continuation rows
     * have no junction element (e.g. CellContentTab, TempArea) set this to false
     * so the horizontal branch doesn't point at empty space.
     */
    private boolean drawHorizontalBranch = true;

    /**
     * @param y Y position relative to GUI
     * @param slotsPerRow Number of slots per row
     * @param slotsXOffset X offset where slots start
     * @param mode Content or Partition
     * @param startIndex Starting data index for this row
     * @param fontRenderer Font renderer
     * @param itemRender Item renderer
     */
    public ContinuationLine(int y, int slotsPerRow, int slotsXOffset, SlotMode mode,
                             int startIndex, FontRenderer fontRenderer, RenderItem itemRender) {
        super(y, slotsPerRow, slotsXOffset, mode, startIndex, fontRenderer, itemRender);

        // Continuation rows are never the "first row" for a cell
        this.isFirstRow = false;
    }

    /**
     * Set whether to draw the horizontal branch at the tree junction.
     * Tabs with no junction element on continuation rows (CellContentTab, TempArea)
     * should call this with false. Tabs like StorageBus that have elements at the
     * junction should leave the default (true).
     */
    public void setDrawHorizontalBranch(boolean drawHorizontalBranch) {
        this.drawHorizontalBranch = drawHorizontalBranch;
    }

    /**
     * Override tree line drawing to optionally skip the horizontal branch.
     * When drawHorizontalBranch is false, only the vertical connector is drawn
     * since there is no junction element for the branch to point at.
     */
    @Override
    protected void drawTreeLines(int mouseX, int mouseY) {
        if (!drawTreeLine) return;

        if (!drawHorizontalBranch) {
            // Only draw the vertical connector through this row (no horizontal branch, no button)
            int verticalEndY = y + 9;  // Same as default getTreeLineCutY() for button-less lines
            if (lineAboveCutY < verticalEndY) {
                Gui.drawRect(TREE_LINE_X, lineAboveCutY, TREE_LINE_X + 1, verticalEndY, GuiConstants.COLOR_TREE_LINE);
            }

            return;
        }

        // Default: draw both vertical and horizontal connectors
        super.drawTreeLines(mouseX, mouseY);
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        // Draw selection background first (below everything else)
        boolean isSelected = selectedSupplier != null && selectedSupplier.get();
        if (isSelected) {
            Gui.drawRect(GuiConstants.GUI_INDENT, y, GuiConstants.CONTENT_RIGHT_EDGE,
                y + GuiConstants.ROW_HEIGHT, GuiConstants.COLOR_SELECTION);
        }

        // Draw tree lines (vertical up + horizontal branch, like all rows)
        drawTreeLines(mouseX, mouseY);

        // Reset hover state and draw slots
        hoveredSlotIndex = -1;
        hoveredStack = ItemStack.EMPTY;
        partitionTargets.clear();

        if (mode == SlotMode.CONTENT) {
            drawContentSlots(mouseX, mouseY);
        } else {
            drawPartitionSlots(mouseX, mouseY);
        }
    }
}
