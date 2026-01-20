package com.cellterminal.gui.render;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderItem;

import com.cellterminal.client.StorageBusContentRow;
import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.storagebus.StorageBusRenderer;


/**
 * Renderer for the Storage Bus Partition tab (Tab 5).
 * Displays storage buses with a header row followed by partition rows in a tree structure.
 * Partition slots have an orange/amber tint and support JEI ghost ingredient drag-and-drop.
 *
 * This renderer delegates actual storage bus rendering to {@link StorageBusRenderer}.
 * It handles the overall tab layout and line iteration.
 *
 * @see StorageBusRenderer
 */
public class StorageBusPartitionTabRenderer {

    private final StorageBusRenderer storageBusRenderer;

    public StorageBusPartitionTabRenderer(FontRenderer fontRenderer, RenderItem itemRender) {
        this.storageBusRenderer = new StorageBusRenderer(fontRenderer, itemRender);
    }

    /**
     * Draw the storage bus partition tab content.
     *
     * @param partitionLines List of line objects (StorageBusInfo, StorageBusContentRow)
     * @param currentScroll Current scroll position
     * @param rowsVisible Number of visible rows
     * @param relMouseX Mouse X relative to GUI
     * @param relMouseY Mouse Y relative to GUI
     * @param absMouseX Absolute mouse X (for tooltips)
     * @param absMouseY Absolute mouse Y (for tooltips)
     * @param guiLeft GUI left position (for JEI ghost targets)
     * @param guiTop GUI top position (for JEI ghost targets)
     * @param ctx Render context for hover tracking
     */
    public void draw(List<Object> partitionLines, int currentScroll, int rowsVisible,
                     int relMouseX, int relMouseY, int absMouseX, int absMouseY,
                     int guiLeft, int guiTop, RenderContext ctx) {

        int y = GuiConstants.CONTENT_START_Y;
        int visibleTop = GuiConstants.CONTENT_START_Y;
        int visibleBottom = GuiConstants.CONTENT_START_Y + rowsVisible * GuiConstants.ROW_HEIGHT;
        int totalLines = partitionLines.size();

        for (int i = 0; i < rowsVisible && currentScroll + i < totalLines; i++) {
            Object line = partitionLines.get(currentScroll + i);
            int lineIndex = currentScroll + i;

            boolean isHovered = relMouseX >= GuiConstants.HOVER_LEFT_EDGE && relMouseX < GuiConstants.HOVER_RIGHT_EDGE
                && relMouseY >= y && relMouseY < y + GuiConstants.ROW_HEIGHT;

            if (line instanceof StorageBusInfo) {
                StorageBusInfo storageBus = (StorageBusInfo) line;

                if (isHovered) ctx.hoveredLineIndex = lineIndex;

                // Draw separator line above header (except for first)
                if (lineIndex > 0) {
                    Gui.drawRect(GuiConstants.GUI_INDENT, y - 1, GuiConstants.CONTENT_RIGHT_EDGE, y, GuiConstants.COLOR_SEPARATOR);
                }

                storageBusRenderer.drawStorageBusPartitionHeader(storageBus, y, partitionLines, lineIndex,
                    relMouseX, relMouseY, absMouseX, absMouseY, guiLeft, guiTop, ctx);

            } else if (line instanceof StorageBusContentRow) {
                StorageBusContentRow row = (StorageBusContentRow) line;
                StorageBusInfo storageBus = row.getStorageBus();

                // Draw selection background if this bus is selected
                boolean isSelected = ctx.selectedStorageBusIds != null
                    && ctx.selectedStorageBusIds.contains(storageBus.getId());

                if (isSelected) {
                    Gui.drawRect(GuiConstants.GUI_INDENT, y, GuiConstants.CONTENT_RIGHT_EDGE, y + GuiConstants.ROW_HEIGHT,
                        GuiConstants.COLOR_SELECTION);
                }

                if (isHovered) {
                    ctx.hoveredLineIndex = lineIndex;
                    Gui.drawRect(GuiConstants.GUI_INDENT, y - 1, GuiConstants.CONTENT_RIGHT_EDGE, y + GuiConstants.ROW_HEIGHT - 1,
                        GuiConstants.COLOR_ROW_HOVER);
                }

                LineContext lineCtx = buildLineContext(partitionLines, lineIndex, i, rowsVisible, totalLines);

                storageBusRenderer.drawStorageBusPartitionLine(
                    storageBus, row.getStartIndex(),
                    y, relMouseX, relMouseY, absMouseX, absMouseY,
                    lineCtx.isFirstInGroup, lineCtx.isLastInGroup, visibleTop, visibleBottom,
                    lineCtx.isFirstVisibleRow, lineCtx.isLastVisibleRow,
                    lineCtx.hasContentAbove, lineCtx.hasContentBelow,
                    guiLeft, guiTop, ctx);
            }

            y += GuiConstants.ROW_HEIGHT;
        }
    }

    private LineContext buildLineContext(List<Object> lines, int lineIndex, int visibleIndex,
                                          int rowsVisible, int totalLines) {
        LineContext ctx = new LineContext();
        ctx.isFirstInGroup = isFirstContentRowOfStorageBus(lines, lineIndex);
        ctx.isLastInGroup = isLastContentRowOfStorageBus(lines, lineIndex);
        ctx.isFirstVisibleRow = (visibleIndex == 0);
        ctx.isLastVisibleRow = (visibleIndex == rowsVisible - 1) || (lineIndex == totalLines - 1);
        ctx.hasContentAbove = hasContentRowAbove(lines, lineIndex);
        ctx.hasContentBelow = hasContentRowBelow(lines, lineIndex);

        return ctx;
    }

    /**
     * Check if the given line is the first content row of a storage bus.
     */
    private boolean isFirstContentRowOfStorageBus(List<Object> lines, int lineIndex) {
        if (!(lines.get(lineIndex) instanceof StorageBusContentRow)) return false;
        if (lineIndex == 0) return true;

        return lines.get(lineIndex - 1) instanceof StorageBusInfo;
    }

    /**
     * Check if the given line is the last content row of a storage bus.
     */
    private boolean isLastContentRowOfStorageBus(List<Object> lines, int lineIndex) {
        if (!(lines.get(lineIndex) instanceof StorageBusContentRow)) return false;
        if (lineIndex >= lines.size() - 1) return true;

        return lines.get(lineIndex + 1) instanceof StorageBusInfo;
    }

    /**
     * Check if there's a content row above this line within the same storage bus.
     */
    private boolean hasContentRowAbove(List<Object> lines, int lineIndex) {
        if (lineIndex == 0) return false;

        return lines.get(lineIndex - 1) instanceof StorageBusContentRow;
    }

    /**
     * Check if there's a content row below this line within the same storage bus.
     */
    private boolean hasContentRowBelow(List<Object> lines, int lineIndex) {
        if (lineIndex >= lines.size() - 1) return false;

        return lines.get(lineIndex + 1) instanceof StorageBusContentRow;
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
