package com.cellterminal.gui.render;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import com.cellterminal.client.CellContentRow;
import com.cellterminal.client.CellInfo;
import com.cellterminal.client.TempCellInfo;
import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.cells.CellRenderer;
import com.cellterminal.gui.cells.CellSlotRenderer;
import com.cellterminal.gui.cells.CellTreeRenderer;


/**
 * Renderer for the Temp Area tab (Tab 3).
 * Displays temporary cells with combined inventory and partition views.
 * <p>
 * Each temp cell is shown as:
 * - Header row with cell icon, name, and "Send" button
 * - Inventory rows showing cell contents (like Inventory tab) with tree lines and partition-all button
 * - Partition rows showing cell partition (like Partition tab) with tree lines and clear button
 * <p>
 * The renderer draws tree lines connecting sections and handles hover highlighting.
 */
public class TempAreaTabRenderer {

    // Maximum width for cell name before the Send button
    private static final int MAX_NAME_WIDTH = 120;

    // Send button color (light blue for better visibility)
    private static final int COLOR_SEND_BUTTON = 0xFF5599CC;
    private static final int COLOR_SEND_BUTTON_HOVER = 0xFF77BBEE;

    private final FontRenderer fontRenderer;
    private final CellRenderer cellRenderer;
    private final CellSlotRenderer slotRenderer;
    private final CellTreeRenderer treeRenderer;

    public TempAreaTabRenderer(FontRenderer fontRenderer, RenderItem itemRender) {
        this.fontRenderer = fontRenderer;
        this.cellRenderer = new CellRenderer(fontRenderer, itemRender);
        this.slotRenderer = cellRenderer.getSlotRenderer();
        this.treeRenderer = cellRenderer.getTreeRenderer();
    }

    /**
     * Draw the temp area tab content.
     *
     * @param tempAreaLines List of line objects (TempCellInfo, CellContentRow for inventory/partition)
     * @param currentScroll Current scroll position
     * @param rowsVisible Number of visible rows
     * @param relMouseX Mouse X relative to GUI
     * @param relMouseY Mouse Y relative to GUI
     * @param absMouseX Absolute mouse X (for tooltips)
     * @param absMouseY Absolute mouse Y (for tooltips)
     * @param guiLeft GUI left position
     * @param guiTop GUI top position
     * @param ctx Render context for hover tracking
     */
    public void draw(List<Object> tempAreaLines, int currentScroll, int rowsVisible,
                     int relMouseX, int relMouseY, int absMouseX, int absMouseY,
                     int guiLeft, int guiTop, RenderContext ctx) {

        int y = GuiConstants.CONTENT_START_Y;
        int visibleTop = GuiConstants.CONTENT_START_Y;
        int visibleBottom = GuiConstants.CONTENT_START_Y + rowsVisible * GuiConstants.ROW_HEIGHT;
        int totalLines = tempAreaLines.size();

        // No temp cells
        if (totalLines == 0) return;

        for (int i = 0; i < rowsVisible && currentScroll + i < totalLines; i++) {
            Object line = tempAreaLines.get(currentScroll + i);
            int lineIndex = currentScroll + i;

            boolean isHovered = relMouseX >= GuiConstants.HOVER_LEFT_EDGE && relMouseX < GuiConstants.HOVER_RIGHT_EDGE
                && relMouseY >= y && relMouseY < y + GuiConstants.ROW_HEIGHT;

            // Draw hover highlight and set hover state
            if (isHovered) handleLineHover(line, lineIndex, y, relMouseX, relMouseY, tempAreaLines, ctx);

            // Calculate tree line context
            LineContext lineCtx = buildLineContext(tempAreaLines, lineIndex, i, rowsVisible, totalLines);

            // Draw the line based on its type
            if (line instanceof TempCellInfo) {
                TempCellInfo tempCell = (TempCellInfo) line;

                // Draw selection background for selected temp cells (before hover)
                boolean isSelected = tempCell.hasCell()
                    && ctx.selectedTempCellSlots != null
                    && ctx.selectedTempCellSlots.contains(tempCell.getTempSlotIndex());
                if (isSelected) {
                    Gui.drawRect(GuiConstants.GUI_INDENT, y, GuiConstants.CONTENT_RIGHT_EDGE,
                        y + GuiConstants.ROW_HEIGHT, GuiConstants.COLOR_SELECTION);
                }

                // Draw connector line to content below if there is a content row following
                boolean hasContentFollowing = tempCell.hasCell()
                    && lineIndex + 1 < totalLines
                    && tempAreaLines.get(lineIndex + 1) instanceof CellContentRow;
                if (hasContentFollowing) drawStorageConnector(y);

                drawTempCellHeader(tempCell, y, relMouseX, relMouseY, ctx);
            } else if (line instanceof CellContentRow) {
                CellContentRow row = (CellContentRow) line;

                // Find the temp slot index for this row
                int tempSlotIndex = findParentTempSlotIndex(tempAreaLines, lineIndex);

                // Draw selection background for content rows belonging to selected temp cells
                boolean isSelected = tempSlotIndex >= 0
                    && ctx.selectedTempCellSlots != null
                    && ctx.selectedTempCellSlots.contains(tempSlotIndex);
                if (isSelected) {
                    Gui.drawRect(GuiConstants.GUI_INDENT, y, GuiConstants.CONTENT_RIGHT_EDGE,
                        y + GuiConstants.ROW_HEIGHT, GuiConstants.COLOR_SELECTION);
                }

                if (row.isPartitionRow()) {
                    // Partition view row - with tree lines, clear button, 9 slots
                    drawPartitionContentRow(row, y, relMouseX, relMouseY, absMouseX, absMouseY,
                        guiLeft, guiTop, lineCtx, visibleTop, visibleBottom, tempSlotIndex, ctx);
                } else {
                    // Inventory view row - with tree lines, partition-all button, 9 slots
                    drawInventoryContentRow(row, y, relMouseX, relMouseY, absMouseX, absMouseY,
                        lineCtx, visibleTop, visibleBottom, tempSlotIndex, ctx);
                }
            }

            y += GuiConstants.ROW_HEIGHT;
        }
    }

    /**
     * Draw vertical connector line from temp cell header to content rows.
     */
    private void drawStorageConnector(int y) {
        int lineX = GuiConstants.GUI_INDENT + 7;
        Gui.drawRect(lineX, y + GuiConstants.ROW_HEIGHT - 1,
            lineX + 1, y + GuiConstants.ROW_HEIGHT, GuiConstants.COLOR_TREE_LINE);
    }

    /**
     * Draw an inventory content row with tree lines and partition-all button.
     */
    private void drawInventoryContentRow(CellContentRow row, int y, int relMouseX, int relMouseY,
                                          int absMouseX, int absMouseY,
                                          LineContext lineCtx, int visibleTop, int visibleBottom,
                                          int tempSlotIndex, RenderContext ctx) {

        CellInfo cell = row.getCell();
        int lineX = GuiConstants.GUI_INDENT + 7;

        // Draw tree lines
        drawTreeLines(lineX, y, row.isFirstRow(), lineCtx.isFirstInGroup, lineCtx.isLastInGroup,
            visibleTop, visibleBottom, lineCtx.isFirstVisibleRow, lineCtx.isLastVisibleRow,
            lineCtx.hasContentAbove, lineCtx.hasContentBelow, false);

        // Draw partition-all button for first row (green)
        if (row.isFirstRow()) drawPartitionAllButton(cell, lineX, y, relMouseX, relMouseY, tempSlotIndex, ctx);

        // Draw inventory content slots (9 per row, like storage bus)
        drawInventoryContentSlots(cell, row.getStartIndex(), y, relMouseX, relMouseY,
            absMouseX, absMouseY, ctx);
    }

    /**
     * Draw a partition content row with tree lines and clear button.
     */
    private void drawPartitionContentRow(CellContentRow row, int y, int relMouseX, int relMouseY,
                                          int absMouseX, int absMouseY,
                                          int guiLeft, int guiTop,
                                          LineContext lineCtx, int visibleTop, int visibleBottom,
                                          int tempSlotIndex, RenderContext ctx) {

        CellInfo cell = row.getCell();
        int lineX = GuiConstants.GUI_INDENT + 7;

        // Draw tree lines
        drawTreeLines(lineX, y, row.isFirstRow(), lineCtx.isFirstInGroup, lineCtx.isLastInGroup,
            visibleTop, visibleBottom, lineCtx.isFirstVisibleRow, lineCtx.isLastVisibleRow,
            lineCtx.hasContentAbove, lineCtx.hasContentBelow, row.isFirstRow());

        // Draw clear partition button for first row (red)
        if (row.isFirstRow()) drawClearPartitionButton(cell, lineX, y, relMouseX, relMouseY, tempSlotIndex, ctx);

        // Draw partition slots (9 per row, like storage bus)
        drawPartitionSlots(cell, row.getStartIndex(), y, relMouseX, relMouseY,
            absMouseX, absMouseY, guiLeft, guiTop, tempSlotIndex, ctx);
    }

    /**
     * Draw tree lines connecting content rows.
     */
    private void drawTreeLines(int lineX, int y, boolean isFirstRow,
                                boolean isFirstInGroup, boolean isLastInGroup,
                                int visibleTop, int visibleBottom,
                                boolean isFirstVisibleRow, boolean isLastVisibleRow,
                                boolean hasContentAbove, boolean hasContentBelow,
                                boolean suppressTopAboveButton) {

        int lineTop = calculateLineTop(y, isFirstRow, isFirstInGroup, isFirstVisibleRow, hasContentAbove, visibleTop);

        // If requested, suppress drawing the small top segment
        if (suppressTopAboveButton) {
            int minTop = y + 6; // start below the clear button area (buttonY = y + 4)
            if (lineTop < minTop) lineTop = minTop;
        }
        int lineBottom = calculateLineBottom(y, isLastInGroup, isLastVisibleRow, hasContentBelow, visibleBottom);

        // Clamp lineTop to visible area
        if (lineTop < visibleTop) lineTop = visibleTop;

        // Vertical line
        Gui.drawRect(lineX, lineTop, lineX + 1, lineBottom, GuiConstants.COLOR_TREE_LINE);

        // Horizontal branch
        Gui.drawRect(lineX, y + 8, lineX + 10, y + 9, GuiConstants.COLOR_TREE_LINE);
    }

    private int calculateLineTop(int y, boolean isFirstRow, boolean isFirstInGroup,
                                  boolean isFirstVisibleRow, boolean hasContentAbove, int visibleTop) {
        if (isFirstRow) {
            if (isFirstInGroup) return y - 3;
            if (isFirstVisibleRow && hasContentAbove) return visibleTop;

            return y - 4;
        }

        return isFirstVisibleRow && hasContentAbove ? visibleTop : y - 4;
    }

    private int calculateLineBottom(int y, boolean isLastInGroup, boolean isLastVisibleRow,
                                     boolean hasContentBelow, int visibleBottom) {
        if (isLastInGroup) return y + 9;
        if (isLastVisibleRow && hasContentBelow) return visibleBottom;

        return y + GuiConstants.ROW_HEIGHT;
    }

    /**
     * Draw the partition-all button (green) on the tree line.
     */
    private void drawPartitionAllButton(CellInfo cell, int lineX, int y, int relMouseX, int relMouseY, int tempSlotIndex, RenderContext ctx) {
        int buttonX = lineX - 3;
        int buttonY = y + 4;
        int buttonSize = GuiConstants.SMALL_BUTTON_SIZE;

        boolean hovered = relMouseX >= buttonX && relMouseX < buttonX + buttonSize
            && relMouseY >= buttonY && relMouseY < buttonY + buttonSize;

        // Draw background to cover tree line
        Gui.drawRect(buttonX - 1, buttonY - 1, buttonX + buttonSize + 1, buttonY + buttonSize + 1, GuiConstants.COLOR_SLOT_BACKGROUND);

        // Draw button with green fill
        slotRenderer.drawSmallButton(buttonX, buttonY, hovered, GuiConstants.COLOR_BUTTON_GREEN);

        if (hovered) {
            ctx.hoveredPartitionAllButtonCell = cell;
            ctx.hoveredTempCellPartitionAllIndex = tempSlotIndex;
        }
    }

    /**
     * Draw the clear partition button (red) on the tree line.
     */
    private void drawClearPartitionButton(CellInfo cell, int lineX, int y, int relMouseX, int relMouseY, int tempSlotIndex, RenderContext ctx) {
        int buttonX = lineX - 3;
        int buttonY = y + 4;
        int buttonSize = GuiConstants.SMALL_BUTTON_SIZE;

        boolean hovered = relMouseX >= buttonX && relMouseX < buttonX + buttonSize
            && relMouseY >= buttonY && relMouseY < buttonY + buttonSize;

        // Draw background to cover tree line
        Gui.drawRect(buttonX - 1, buttonY - 1, buttonX + buttonSize + 1, buttonY + buttonSize + 1, GuiConstants.COLOR_SLOT_BACKGROUND);

        // Draw button with red fill
        slotRenderer.drawSmallButton(buttonX, buttonY, hovered, GuiConstants.COLOR_BUTTON_RED);

        if (hovered) {
            ctx.hoveredClearPartitionButtonCell = cell;
            ctx.hoveredTempCellClearPartitionIndex = tempSlotIndex;
        }
    }

    /**
     * Draw inventory content slots (9 per row).
     */
    private void drawInventoryContentSlots(CellInfo cell, int startIndex, int y,
                                            int relMouseX, int relMouseY,
                                            int absMouseX, int absMouseY, RenderContext ctx) {

        List<ItemStack> contents = cell.getContents();
        List<ItemStack> partition = cell.getPartition();
        int slotStartX = GuiConstants.CELL_INDENT + 4;

        for (int i = 0; i < GuiConstants.STORAGE_BUS_SLOTS_PER_ROW; i++) {
            int contentIndex = startIndex + i;
            int slotX = slotStartX + (i * GuiConstants.MINI_SLOT_SIZE);

            slotRenderer.drawSlotBackground(slotX, y);

            if (contentIndex >= contents.size()) continue;

            ItemStack stack = contents.get(contentIndex);
            if (stack.isEmpty()) continue;

            slotRenderer.renderItemStack(stack, slotX, y);

            // Draw partition indicator
            if (slotRenderer.isInPartition(stack, partition)) slotRenderer.drawPartitionIndicator(slotX, y);

            // Draw item count
            slotRenderer.drawItemCount(cell.getContentCount(contentIndex), slotX, y);

            // Check hover
            if (relMouseX >= slotX && relMouseX < slotX + GuiConstants.MINI_SLOT_SIZE
                && relMouseY >= y && relMouseY < y + GuiConstants.MINI_SLOT_SIZE) {
                slotRenderer.drawSlotHoverHighlight(slotX, y);
                ctx.hoveredContentStack = stack;
                ctx.hoveredContentX = absMouseX;
                ctx.hoveredContentY = absMouseY;
                ctx.hoveredContentSlotIndex = contentIndex;
                ctx.hoveredCellCell = cell;
            }
        }
    }

    /**
     * Draw partition slots (9 per row, with amber tint).
     */
    private void drawPartitionSlots(CellInfo cell, int startIndex, int y,
                                     int relMouseX, int relMouseY,
                                     int absMouseX, int absMouseY,
                                     int guiLeft, int guiTop, int tempSlotIndex, RenderContext ctx) {

        List<ItemStack> partition = cell.getPartition();
        int slotStartX = GuiConstants.CELL_INDENT + 4;

        for (int i = 0; i < GuiConstants.STORAGE_BUS_SLOTS_PER_ROW; i++) {
            int partitionIndex = startIndex + i;
            if (partitionIndex >= GuiConstants.MAX_CELL_PARTITION_SLOTS) break;

            int slotX = slotStartX + (i * GuiConstants.MINI_SLOT_SIZE);

            // Draw partition slot with amber tint
            slotRenderer.drawPartitionSlotBackground(slotX, y);

            // Register JEI ghost target for temp cells
            ctx.tempCellPartitionSlotTargets.add(new RenderContext.TempCellPartitionSlotTarget(
                cell, tempSlotIndex, partitionIndex, guiLeft + slotX, guiTop + y,
                GuiConstants.MINI_SLOT_SIZE, GuiConstants.MINI_SLOT_SIZE));

            // Draw partition item if present
            ItemStack partItem = partitionIndex < partition.size() ? partition.get(partitionIndex) : ItemStack.EMPTY;
            if (!partItem.isEmpty()) slotRenderer.renderItemStack(partItem, slotX, y);

            // Check hover
            if (relMouseX >= slotX && relMouseX < slotX + GuiConstants.MINI_SLOT_SIZE
                && relMouseY >= y && relMouseY < y + GuiConstants.MINI_SLOT_SIZE) {
                slotRenderer.drawSlotHoverHighlight(slotX, y);
                ctx.hoveredPartitionSlotIndex = partitionIndex;
                ctx.hoveredPartitionCell = cell;

                if (!partItem.isEmpty()) {
                    ctx.hoveredContentStack = partItem;
                    ctx.hoveredContentX = absMouseX;
                    ctx.hoveredContentY = absMouseY;
                }
            }
        }
    }

    /**
     * Draw a temp cell header row.
     */
    private void drawTempCellHeader(TempCellInfo tempCell, int y, int relMouseX, int relMouseY, RenderContext ctx) {
        CellInfo cell = tempCell.getCellInfo();
        boolean hasCell = !tempCell.getCellStack().isEmpty();

        // Draw cell slot background
        int slotX = GuiConstants.GUI_INDENT;
        int slotSize = GuiConstants.MINI_SLOT_SIZE;
        slotRenderer.drawSlotBackground(slotX, y);

        // Check if mouse is over the cell slot area (for insert/extract)
        boolean slotHovered = relMouseX >= slotX && relMouseX < slotX + slotSize
            && relMouseY >= y && relMouseY < y + slotSize;

        // Track slot hover for insert/extract clicks
        if (slotHovered) ctx.hoveredTempCellSlot = tempCell;

        // Draw hover highlight on slot if hovered
        if (slotHovered) slotRenderer.drawSlotHoverHighlight(slotX, y);

        // Draw cell item
        if (hasCell) slotRenderer.renderItemStack(tempCell.getCellStack(), slotX, y);

        // Draw cell name (or "Drop a cell here" for empty slots), truncated to fit before Send button
        String name;
        if (cell != null) {
            name = cell.getDisplayName();
        } else {
            name = I18n.format("gui.cellterminal.temp_area.drop_cell");
        }

        // Truncate name if it's too long
        int nameX = slotX + 20;
        String displayName = truncateName(name, MAX_NAME_WIDTH);
        fontRenderer.drawString(displayName, nameX, y + 5, hasCell ? GuiConstants.COLOR_TEXT_NORMAL : GuiConstants.COLOR_TEXT_PLACEHOLDER);

        // Only draw "Send" button for cells that actually have content
        if (!hasCell) return;

        // Draw "Send" button (right side) - light blue color for visibility
        int sendBtnX = 150;
        int sendBtnY = y + 2;
        int sendBtnW = 28;
        int sendBtnH = 12;

        boolean sendHovered = relMouseX >= sendBtnX && relMouseX < sendBtnX + sendBtnW
            && relMouseY >= sendBtnY && relMouseY < sendBtnY + sendBtnH;

        int btnColor = sendHovered ? COLOR_SEND_BUTTON_HOVER : COLOR_SEND_BUTTON;
        Gui.drawRect(sendBtnX, sendBtnY, sendBtnX + sendBtnW, sendBtnY + sendBtnH, btnColor);

        // Button 3D effect
        Gui.drawRect(sendBtnX, sendBtnY, sendBtnX + sendBtnW, sendBtnY + 1, GuiConstants.COLOR_BUTTON_HIGHLIGHT);
        Gui.drawRect(sendBtnX, sendBtnY, sendBtnX + 1, sendBtnY + sendBtnH, GuiConstants.COLOR_BUTTON_HIGHLIGHT);
        Gui.drawRect(sendBtnX, sendBtnY + sendBtnH - 1, sendBtnX + sendBtnW, sendBtnY + sendBtnH, GuiConstants.COLOR_BUTTON_SHADOW);
        Gui.drawRect(sendBtnX + sendBtnW - 1, sendBtnY, sendBtnX + sendBtnW, sendBtnY + sendBtnH, GuiConstants.COLOR_BUTTON_SHADOW);

        String sendText = I18n.format("gui.cellterminal.temp_area.send");
        int textX = sendBtnX + (sendBtnW - fontRenderer.getStringWidth(sendText)) / 2;
        // Use black text on light blue button for better contrast
        fontRenderer.drawString(sendText, textX, sendBtnY + 2, 0x000000);

        // Track send button hover
        if (sendHovered && tempCell.getCellInfo() != null) ctx.hoveredTempCellSendButton = tempCell;
    }

    /**
     * Truncate a string to fit within the given pixel width.
     */
    private String truncateName(String name, int maxWidth) {
        if (fontRenderer.getStringWidth(name) <= maxWidth) return name;

        String ellipsis = "...";
        int ellipsisWidth = fontRenderer.getStringWidth(ellipsis);

        // Binary search for the maximum length that fits
        int maxLen = name.length();
        while (maxLen > 0 && fontRenderer.getStringWidth(name.substring(0, maxLen)) + ellipsisWidth > maxWidth) {
            maxLen--;
        }

        return maxLen > 0 ? name.substring(0, maxLen) + ellipsis : ellipsis;
    }

    /**
     * Handle hover for a line, setting appropriate context state for selection.
     * For both TempCellInfo headers and CellContentRow content, we set hoveredTempCell
     * to enable selection by clicking anywhere on a temp cell's rows.
     */
    private void handleLineHover(Object line, int lineIndex, int y, int relMouseX, int relMouseY,
                                  List<Object> tempAreaLines, RenderContext ctx) {
        ctx.hoveredLineIndex = lineIndex;

        if (line instanceof TempCellInfo) {
            ctx.hoveredTempCell = (TempCellInfo) line;
            ctx.hoveredTempCellHeader = (TempCellInfo) line;  // Direct header hover - selection allowed
            Gui.drawRect(GuiConstants.GUI_INDENT, y, GuiConstants.CONTENT_RIGHT_EDGE, y + GuiConstants.ROW_HEIGHT, GuiConstants.COLOR_STORAGE_HEADER_HOVER);
        } else if (line instanceof CellContentRow) {
            CellContentRow row = (CellContentRow) line;
            ctx.hoveredCellCell = row.getCell();
            Gui.drawRect(GuiConstants.GUI_INDENT, y - 1, GuiConstants.CONTENT_RIGHT_EDGE, y + GuiConstants.ROW_HEIGHT - 1, GuiConstants.COLOR_ROW_HOVER);

            // Also find and set the parent TempCellInfo so clicking on content rows can select the cell
            int parentSlotIndex = findParentTempSlotIndex(tempAreaLines, lineIndex);
            if (parentSlotIndex >= 0) {
                TempCellInfo parentCell = findTempCellBySlot(tempAreaLines, parentSlotIndex);
                if (parentCell != null) ctx.hoveredTempCell = parentCell;
            }
        }
    }

    /**
     * Find TempCellInfo for a given slot index in the temp area lines.
     */
    private TempCellInfo findTempCellBySlot(List<Object> lines, int slotIndex) {
        for (Object line : lines) {
            if (line instanceof TempCellInfo) {
                TempCellInfo tempCell = (TempCellInfo) line;
                if (tempCell.getTempSlotIndex() == slotIndex) return tempCell;
            }
        }

        return null;
    }

    private LineContext buildLineContext(List<Object> lines, int lineIndex, int visibleIndex,
                                          int rowsVisible, int totalLines) {
        LineContext ctx = new LineContext();

        // Determine if this is first/last in section (inventory OR partition section, not whole group)
        ctx.isFirstInGroup = isFirstInSection(lines, lineIndex);
        ctx.isLastInGroup = isLastInSection(lines, lineIndex);
        ctx.isFirstVisibleRow = (visibleIndex == 0);
        ctx.isLastVisibleRow = (visibleIndex == rowsVisible - 1) || (lineIndex == totalLines - 1);
        ctx.hasContentAbove = (lineIndex > 0) && !ctx.isFirstInGroup;
        ctx.hasContentBelow = (lineIndex < totalLines - 1) && !ctx.isLastInGroup;

        return ctx;
    }

    /**
     * Check if the given line is the first content row in its section.
     * A "section" is either all inventory rows or all partition rows under a temp cell.
     * The first inventory row comes right after TempCellInfo.
     * The first partition row comes right after the last inventory row.
     */
    private boolean isFirstInSection(List<Object> lines, int index) {
        Object current = lines.get(index);
        if (!(current instanceof CellContentRow)) return false;

        CellContentRow currentRow = (CellContentRow) current;

        // Check previous line
        if (index <= 0) return true;

        Object previous = lines.get(index - 1);

        // If previous is a TempCellInfo header, this is the first content row
        if (previous instanceof TempCellInfo) return true;

        // If previous is a CellContentRow, check if we're transitioning from inventory to partition
        if (previous instanceof CellContentRow) {
            CellContentRow prevRow = (CellContentRow) previous;

            // If current is partition and previous is inventory, this is first in partition section
            if (currentRow.isPartitionRow() && !prevRow.isPartitionRow()) return true;
        }

        return false;
    }

    /**
     * Check if the given line is the last content row in its section.
     * The last inventory row comes right before the first partition row or next TempCellInfo.
     * The last partition row comes right before the next TempCellInfo.
     */
    private boolean isLastInSection(List<Object> lines, int index) {
        Object current = lines.get(index);
        if (!(current instanceof CellContentRow)) return false;

        CellContentRow currentRow = (CellContentRow) current;

        // Check next line
        if (index >= lines.size() - 1) return true;

        Object next = lines.get(index + 1);

        // If next is a TempCellInfo header, this is the last content row
        if (next instanceof TempCellInfo) return true;

        // If next is a CellContentRow, check if we're transitioning from inventory to partition
        if (next instanceof CellContentRow) {
            CellContentRow nextRow = (CellContentRow) next;

            // If current is inventory and next is partition, this is last in inventory section
            if (!currentRow.isPartitionRow() && nextRow.isPartitionRow()) return true;
        }

        return false;
    }

    /**
     * Find the temp slot index for a CellContentRow by looking back to find
     * the parent TempCellInfo.
     *
     * @param lines The list of temp area lines
     * @param lineIndex The current line index
     * @return The temp slot index, or -1 if not found
     */
    private int findParentTempSlotIndex(List<Object> lines, int lineIndex) {
        for (int i = lineIndex; i >= 0; i--) {
            Object line = lines.get(i);
            if (line instanceof TempCellInfo) return ((TempCellInfo) line).getTempSlotIndex();
        }

        return -1;
    }

    private static class LineContext {
        boolean isFirstInGroup;
        boolean isLastInGroup;
        boolean isFirstVisibleRow;
        boolean isLastVisibleRow;
        boolean hasContentAbove;
        boolean hasContentBelow;
    }
}
