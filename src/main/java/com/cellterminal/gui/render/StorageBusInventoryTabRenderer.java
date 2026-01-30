package com.cellterminal.gui.render;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderItem;

import com.cellterminal.client.StorageBusContentRow;
import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.client.TabStateManager;
import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.storagebus.StorageBusRenderer;


/**
 * Renderer for the Storage Bus Inventory tab (Tab 4).
 * Displays storage buses with a header row followed by content rows in a tree structure.
 * Content items show "P" indicator if they're in the storage bus's partition.
 *
 * This renderer delegates actual storage bus rendering to {@link StorageBusRenderer}.
 * It handles the overall tab layout and line iteration.
 *
 * @see StorageBusRenderer
 */
public class StorageBusInventoryTabRenderer {

    private final StorageBusRenderer storageBusRenderer;

    public StorageBusInventoryTabRenderer(FontRenderer fontRenderer, RenderItem itemRender) {
        this.storageBusRenderer = new StorageBusRenderer(fontRenderer, itemRender);
    }

    /**
     * Draw the storage bus inventory tab content.
     *
     * @param inventoryLines List of line objects (StorageBusInfo, StorageBusContentRow)
     * @param currentScroll Current scroll position
     * @param rowsVisible Number of visible rows
     * @param relMouseX Mouse X relative to GUI
     * @param relMouseY Mouse Y relative to GUI
     * @param absMouseX Absolute mouse X (for tooltips)
     * @param absMouseY Absolute mouse Y (for tooltips)
     * @param ctx Render context for hover tracking
     */
    public void draw(List<Object> inventoryLines, int currentScroll, int rowsVisible,
                     int relMouseX, int relMouseY, int absMouseX, int absMouseY,
                     RenderContext ctx) {

        int y = GuiConstants.CONTENT_START_Y;
        int totalLines = inventoryLines.size();

        for (int i = 0; i < rowsVisible && currentScroll + i < totalLines; i++) {
            Object line = inventoryLines.get(currentScroll + i);
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

                storageBusRenderer.drawStorageBusHeader(storageBus, y, inventoryLines, lineIndex,
                    TabStateManager.TabType.STORAGE_BUS_INVENTORY,
                    relMouseX, relMouseY, absMouseX, absMouseY, ctx);

            } else if (line instanceof StorageBusContentRow) {
                StorageBusContentRow row = (StorageBusContentRow) line;

                if (isHovered) {
                    ctx.hoveredLineIndex = lineIndex;
                    Gui.drawRect(GuiConstants.GUI_INDENT, y - 1, GuiConstants.CONTENT_RIGHT_EDGE, y + GuiConstants.ROW_HEIGHT - 1,
                        GuiConstants.COLOR_ROW_HOVER);
                }

                LineContext lineCtx = buildLineContext(inventoryLines, lineIndex, i, rowsVisible, totalLines);

                storageBusRenderer.drawStorageBusInventoryLine(
                    row.getStorageBus(), row.getStartIndex(),
                    y, relMouseX, relMouseY, absMouseX, absMouseY,
                    lineCtx.isFirstInGroup, lineCtx.isLastInGroup,
                    lineCtx.isFirstVisibleRow, lineCtx.isLastVisibleRow,
                    lineCtx.hasContentAbove, lineCtx.hasContentBelow, ctx);
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
