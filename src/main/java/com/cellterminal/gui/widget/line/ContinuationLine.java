package com.cellterminal.gui.widget.line;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import com.cellterminal.gui.GuiConstants;


/**
 * A continuation row for multi-row cells.
 *
 * When a cell has more items than fit in a single row, additional rows
 * are rendered using ContinuationLine. These rows have:
 * - Tree line connectors (vertical line up + horizontal branch, like all rows)
 * - Content/partition slots only (no cell slot, no cards, no button)
 *
 * The spec states: "nothing, as it's a continuation row. No Cell, no cards, no button."
 *
 * @see CellSlotsLine
 * @see SlotsLine
 */
public class ContinuationLine extends SlotsLine {

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
