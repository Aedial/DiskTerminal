package com.cellterminal.gui.render;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderItem;

import com.cellterminal.client.CellInfo;
import com.cellterminal.client.StorageInfo;
import com.cellterminal.client.TabStateManager;
import com.cellterminal.gui.GuiConstants;


/**
 * Renderer for the Terminal tab (Tab 0).
 * Displays storage devices as expandable tree nodes with cells listed below.
 * Each cell shows name, usage bar, and action buttons (Eject, Inventory, Partition).
 */
public class TerminalTabRenderer extends CellTerminalRenderer {

    public TerminalTabRenderer(FontRenderer fontRenderer, RenderItem itemRender) {
        super(fontRenderer, itemRender);
    }

    /**
     * Draw the terminal tab content.
     * @param lines The list of lines (StorageInfo or CellInfo objects)
     * @param currentScroll Current scroll position
     * @param rowsVisible Number of visible rows
     * @param relMouseX Mouse X relative to GUI
     * @param relMouseY Mouse Y relative to GUI
     * @param ctx Render context for hover state tracking
     */
    public void draw(List<Object> lines, int currentScroll, int rowsVisible,
                     int relMouseX, int relMouseY, RenderContext ctx) {
        int y = GuiConstants.CONTENT_START_Y;
        int visibleTop = GuiConstants.CONTENT_START_Y;
        int visibleBottom = GuiConstants.CONTENT_START_Y + rowsVisible * ROW_HEIGHT;
        int totalLines = lines.size();

        for (int i = 0; i < rowsVisible && currentScroll + i < totalLines; i++) {
            Object line = lines.get(currentScroll + i);
            int lineIndex = currentScroll + i;

            boolean isHovered = relMouseX >= GuiConstants.HOVER_LEFT_EDGE && relMouseX < GuiConstants.HOVER_RIGHT_EDGE
                && relMouseY >= y && relMouseY < y + ROW_HEIGHT;

            // Track hover state based on line type
            if (isHovered) {
                if (line instanceof CellInfo) {
                    ctx.hoveredLineIndex = lineIndex;
                    ctx.hoveredCellCell = (CellInfo) line;
                    ctx.hoveredCellStorage = ctx.storageMap.get(((CellInfo) line).getParentStorageId());
                    ctx.hoveredCellSlotIndex = ((CellInfo) line).getSlot();
                    // Draw hover background for cell lines
                    Gui.drawRect(GUI_INDENT, y - 1, GuiConstants.CONTENT_RIGHT_EDGE, y + ROW_HEIGHT - 1, GuiConstants.COLOR_ROW_HOVER);
                } else if (line instanceof StorageInfo) {
                    ctx.hoveredStorageLine = (StorageInfo) line;
                    ctx.hoveredLineIndex = lineIndex;
                }
            }

            // Draw separator line above storage entries (except first one)
            if (line instanceof StorageInfo && i > 0) {
                Gui.drawRect(GUI_INDENT, y - 1, GuiConstants.CONTENT_RIGHT_EDGE, y, GuiConstants.COLOR_SEPARATOR);
            }

            // Tree line parameters
            boolean isFirstInGroup = isFirstInStorageGroup(lines, lineIndex);
            boolean isLastInGroup = isLastInStorageGroup(lines, lineIndex);
            boolean isFirstVisibleRow = (i == 0);
            boolean isLastVisibleRow = (i == rowsVisible - 1) || (currentScroll + i == totalLines - 1);
            boolean hasContentAbove = (lineIndex > 0) && !isFirstInGroup;
            boolean hasContentBelow = (lineIndex < totalLines - 1) && !isLastInGroup;

            if (line instanceof StorageInfo) {
                drawStorageLine((StorageInfo) line, y, lines, lineIndex, ctx);
            } else if (line instanceof CellInfo) {
                drawCellLine((CellInfo) line, y, relMouseX, relMouseY,
                    isFirstInGroup, isLastInGroup, visibleTop, visibleBottom,
                    isFirstVisibleRow, isLastVisibleRow, hasContentAbove, hasContentBelow, ctx);
            }

            y += ROW_HEIGHT;
        }
    }

    private void drawStorageLine(StorageInfo storage, int y, List<Object> lines, int lineIndex, RenderContext ctx) {
        // Track this storage for priority field rendering
        ctx.visibleStorages.add(new RenderContext.VisibleStorageEntry(storage, y));

        // Determine expansion state from TabStateManager so it's remembered per-tab
        boolean isExpanded = TabStateManager.getInstance().isExpanded(TabStateManager.TabType.TERMINAL, storage.getId());

        // Draw expand/collapse indicator
        String expandIcon = isExpanded ? "[-]" : "[+]";
        fontRenderer.drawString(expandIcon, 167, y + 1, 0x606060);

        // Draw vertical tree line connecting to cells below (if expanded and has cells following)
        // Terminal tab has no button covering the junction, so extend the line further up
        // to properly connect with the cell's tree line (which starts at y - 3 of the cell row)
        boolean hasCellsFollowing = isExpanded
            && lineIndex + 1 < lines.size()
            && lines.get(lineIndex + 1) instanceof CellInfo;

        if (hasCellsFollowing) {
            int lineX = GUI_INDENT + 7;
            Gui.drawRect(lineX, y + ROW_HEIGHT - 4, lineX + 1, y + ROW_HEIGHT, 0xFF808080);
        }

        // Draw block icon
        renderItemStack(storage.getBlockItem(), GUI_INDENT, y);

        // Draw name and location
        String name = storage.getName();
        if (name.length() > 20) name = name.substring(0, 18) + "...";
        fontRenderer.drawString(name, GUI_INDENT + 20, y + 1, 0x404040);

        String location = storage.getLocationString();
        fontRenderer.drawString(location, GUI_INDENT + 20, y + 9, 0x808080);
    }

    private void drawCellLine(CellInfo cell, int y, int mouseX, int mouseY,
            boolean isFirstInGroup, boolean isLastInGroup, int visibleTop, int visibleBottom,
            boolean isFirstVisibleRow, boolean isLastVisibleRow,
            boolean hasContentAbove, boolean hasContentBelow, RenderContext ctx) {

        int lineX = GUI_INDENT + 7;
        drawTreeLines(lineX, y, true, isFirstInGroup, isLastInGroup,
            visibleTop, visibleBottom, isFirstVisibleRow, isLastVisibleRow,
            hasContentAbove, hasContentBelow, false);

        // Terminal tab has no buttons covering the tree line, so fill the gap between cells
        // The base drawTreeLines leaves a gap for buttons - draw an additional segment to connect
        if (!isFirstInGroup && !isFirstVisibleRow) {
            // Fill gap between previous cell's bottom and this cell's lineTop
            Gui.drawRect(lineX, y - ROW_HEIGHT + 9, lineX + 1, y - 3, 0xFF808080);
        }

        // Extend horizontal branch to reach the cell icon (covering the gap left by no button)
        Gui.drawRect(lineX + 10, y + 8, CELL_INDENT, y + 9, 0xFF808080);

        // Draw upgrade icons to the left of the cell icon (with tracking)
        drawCellUpgradeIcons(cell, 3, y, ctx);

        // Draw cell icon
        renderItemStack(cell.getCellItem(), CELL_INDENT, y);

        // Draw cell name
        String name = cell.getDisplayName();
        int decorLength = getDecorationLength(name);
        if (name.length() - decorLength > 16) name = name.substring(0, 14 + decorLength) + "...";
        fontRenderer.drawString(name, CELL_INDENT + 18, y + 1, 0x404040);

        // Draw usage bar
        int barX = CELL_INDENT + 18;
        int barY = y + 10;
        int barWidth = 80;
        int barHeight = 4;

        // TODO: add tooltip showing full name and usage details
        Gui.drawRect(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555);
        int filledWidth = (int) (barWidth * cell.getByteUsagePercent());
        int fillColor = getUsageColor(cell.getByteUsagePercent());
        if (filledWidth > 0) {
            Gui.drawRect(barX, barY, barX + filledWidth, barY + barHeight, fillColor);
        }

        // Check button hover states
        boolean ejectHovered = mouseX >= GuiConstants.BUTTON_EJECT_X
            && mouseX < GuiConstants.BUTTON_EJECT_X + GuiConstants.BUTTON_SIZE
            && mouseY >= y + 1 && mouseY < y + 1 + GuiConstants.BUTTON_SIZE;
        boolean invHovered = mouseX >= GuiConstants.BUTTON_INVENTORY_X
            && mouseX < GuiConstants.BUTTON_INVENTORY_X + GuiConstants.BUTTON_SIZE
            && mouseY >= y + 1 && mouseY < y + 1 + GuiConstants.BUTTON_SIZE;
        boolean partHovered = mouseX >= GuiConstants.BUTTON_PARTITION_X
            && mouseX < GuiConstants.BUTTON_PARTITION_X + GuiConstants.BUTTON_SIZE
            && mouseY >= y + 1 && mouseY < y + 1 + GuiConstants.BUTTON_SIZE;

        // Draw buttons
        drawButton(GuiConstants.BUTTON_EJECT_X, y + 1, GuiConstants.BUTTON_SIZE, "E", ejectHovered);
        drawButton(GuiConstants.BUTTON_INVENTORY_X, y + 1, GuiConstants.BUTTON_SIZE, "I", invHovered);
        drawButton(GuiConstants.BUTTON_PARTITION_X, y + 1, GuiConstants.BUTTON_SIZE, "P", partHovered);

        // Track hover state
        if (ejectHovered) {
            ctx.hoveredCell = cell;
            ctx.hoverType = 3;
        } else if (invHovered) {
            ctx.hoveredCell = cell;
            ctx.hoverType = 1;
        } else if (partHovered) {
            ctx.hoveredCell = cell;
            ctx.hoverType = 2;
        }
    }
}
