package com.cellterminal.gui.render;

import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderItem;

import com.cellterminal.client.CellContentRow;
import com.cellterminal.client.EmptySlotInfo;
import com.cellterminal.client.StorageInfo;
import com.cellterminal.client.TabStateManager;
import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.cells.CellRenderer;


/**
 * Renderer for the Partition tab (Tab 2).
 * Displays cells with their partition configuration in a grid.
 * Partition slots have an orange/amber tint to differentiate from regular slots.
 * Supports JEI ghost ingredient drag-and-drop.
 *
 * <b>IMPORTANT:</b> This renderer should replicate the behavior of the
 * Partition Popup!
 *
 * This renderer delegates actual cell rendering to {@link CellRenderer}.
 * It handles the overall tab layout and line iteration.
 *
 * @see CellRenderer
 */
public class PartitionTabRenderer {

    private final CellRenderer cellRenderer;

    public PartitionTabRenderer(FontRenderer fontRenderer, RenderItem itemRender) {
        this.cellRenderer = new CellRenderer(fontRenderer, itemRender);
    }

    /**
     * Draw the partition tab content.
     *
     * @param partitionLines List of line objects (StorageInfo, CellContentRow, EmptySlotInfo)
     * @param currentScroll Current scroll position
     * @param rowsVisible Number of visible rows
     * @param relMouseX Mouse X relative to GUI
     * @param relMouseY Mouse Y relative to GUI
     * @param absMouseX Absolute mouse X (for tooltips)
     * @param absMouseY Absolute mouse Y (for tooltips)
     * @param storageMap Map of storage IDs to StorageInfo
     * @param guiLeft GUI left position (for JEI ghost targets)
     * @param guiTop GUI top position (for JEI ghost targets)
     * @param ctx Render context for hover tracking
     */
    public void draw(List<Object> partitionLines, int currentScroll, int rowsVisible,
                     int relMouseX, int relMouseY, int absMouseX, int absMouseY,
                     Map<Long, StorageInfo> storageMap, int guiLeft, int guiTop,
                     RenderContext ctx) {

        int y = GuiConstants.CONTENT_START_Y;
        int visibleTop = GuiConstants.CONTENT_START_Y;
        int visibleBottom = GuiConstants.CONTENT_START_Y + rowsVisible * GuiConstants.ROW_HEIGHT;
        int totalLines = partitionLines.size();

        for (int i = 0; i < rowsVisible && currentScroll + i < totalLines; i++) {
            Object line = partitionLines.get(currentScroll + i);
            int lineIndex = currentScroll + i;

            boolean isHovered = relMouseX >= GuiConstants.HOVER_LEFT_EDGE && relMouseX < GuiConstants.HOVER_RIGHT_EDGE
                && relMouseY >= y && relMouseY < y + GuiConstants.ROW_HEIGHT;

            // Draw hover highlight and track state
            if (isHovered) {
                handleLineHover(line, lineIndex, y, ctx);
            }

            // Draw separator line above storage entries
            if (line instanceof StorageInfo && i > 0) {
                Gui.drawRect(GuiConstants.GUI_INDENT, y - 1, GuiConstants.CONTENT_RIGHT_EDGE, y, GuiConstants.COLOR_SEPARATOR);
            }

            // Calculate tree line parameters
            LineContext lineCtx = buildLineContext(partitionLines, lineIndex, i, rowsVisible, totalLines);

            // Render the line
            if (line instanceof StorageInfo) {
                cellRenderer.drawStorageHeader((StorageInfo) line, y, partitionLines, lineIndex,
                    TabStateManager.TabType.PARTITION, ctx);
            } else if (line instanceof CellContentRow) {
                CellContentRow row = (CellContentRow) line;
                cellRenderer.drawCellPartitionLine(
                    row.getCell(), row.getStartIndex(), row.isFirstRow(),
                    y, relMouseX, relMouseY, absMouseX, absMouseY,
                    lineCtx.isFirstInGroup, lineCtx.isLastInGroup, visibleTop, visibleBottom,
                    lineCtx.isFirstVisibleRow, lineCtx.isLastVisibleRow,
                    lineCtx.hasContentAbove, lineCtx.hasContentBelow,
                    storageMap, guiLeft, guiTop, ctx);
            } else if (line instanceof EmptySlotInfo) {
                cellRenderer.drawEmptySlotLine(
                    (EmptySlotInfo) line, y, relMouseX, relMouseY,
                    lineCtx.isFirstInGroup, lineCtx.isLastInGroup, visibleTop, visibleBottom,
                    lineCtx.isFirstVisibleRow, lineCtx.isLastVisibleRow,
                    lineCtx.hasContentAbove, lineCtx.hasContentBelow,
                    storageMap, ctx);
            }

            y += GuiConstants.ROW_HEIGHT;
        }
    }

    private void handleLineHover(Object line, int lineIndex, int y, RenderContext ctx) {
        if (line instanceof CellContentRow) {
            ctx.hoveredLineIndex = lineIndex;
            ctx.hoveredPartitionCell = ((CellContentRow) line).getCell();
            Gui.drawRect(GuiConstants.GUI_INDENT, y - 1, GuiConstants.CONTENT_RIGHT_EDGE, y + GuiConstants.ROW_HEIGHT - 1, GuiConstants.COLOR_ROW_HOVER);
        } else if (line instanceof EmptySlotInfo) {
            ctx.hoveredLineIndex = lineIndex;
            Gui.drawRect(GuiConstants.GUI_INDENT, y - 1, GuiConstants.CONTENT_RIGHT_EDGE, y + GuiConstants.ROW_HEIGHT - 1, GuiConstants.COLOR_ROW_HOVER);
        } else if (line instanceof StorageInfo) {
            ctx.hoveredStorageLine = (StorageInfo) line;
            ctx.hoveredLineIndex = lineIndex;
            Gui.drawRect(GuiConstants.GUI_INDENT, y, GuiConstants.CONTENT_RIGHT_EDGE, y + GuiConstants.ROW_HEIGHT, GuiConstants.COLOR_STORAGE_HEADER_HOVER);
        }
    }

    private LineContext buildLineContext(List<Object> lines, int lineIndex, int visibleIndex,
                                          int rowsVisible, int totalLines) {
        LineContext ctx = new LineContext();
        ctx.isFirstInGroup = isFirstInStorageGroup(lines, lineIndex);
        ctx.isLastInGroup = isLastInStorageGroup(lines, lineIndex);
        ctx.isFirstVisibleRow = (visibleIndex == 0);
        ctx.isLastVisibleRow = (visibleIndex == rowsVisible - 1) || (lineIndex == totalLines - 1);
        ctx.hasContentAbove = (lineIndex > 0) && !ctx.isFirstInGroup;
        ctx.hasContentBelow = (lineIndex < totalLines - 1) && !ctx.isLastInGroup;

        return ctx;
    }

    /**
     * Check if the line at the given index is the first in its storage group.
     */
    private boolean isFirstInStorageGroup(List<Object> lines, int index) {
        if (index <= 0) return true;

        return lines.get(index - 1) instanceof StorageInfo;
    }

    /**
     * Check if the line at the given index is the last in its storage group.
     */
    private boolean isLastInStorageGroup(List<Object> lines, int index) {
        if (index >= lines.size() - 1) return true;

        // Look ahead to find if there are any more cells after all rows of current cell
        for (int i = index + 1; i < lines.size(); i++) {
            Object line = lines.get(i);
            if (line instanceof StorageInfo) return true;

            if (line instanceof CellContentRow) {
                CellContentRow row = (CellContentRow) line;
                if (row.isFirstRow()) return false;
            } else if (line instanceof EmptySlotInfo) {
                return false;
            }
        }

        return true;
    }

    /**
     * Context for line rendering parameters.
     */
    private static class LineContext {
        boolean isFirstInGroup;
        boolean isLastInGroup;
        boolean isFirstVisibleRow;
        boolean isLastVisibleRow;
        boolean hasContentAbove;
        boolean hasContentBelow;
    }
}
