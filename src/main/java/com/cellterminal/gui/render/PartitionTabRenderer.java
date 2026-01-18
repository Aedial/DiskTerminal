package com.cellterminal.gui.render;

import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import com.cellterminal.client.CellContentRow;
import com.cellterminal.client.CellInfo;
import com.cellterminal.client.EmptySlotInfo;
import com.cellterminal.client.StorageInfo;


/**
 * Renderer for the Partition tab (Tab 2).
 * Displays cells with their partition configuration in a grid.
 * Partition slots have an orange/amber tint to differentiate from regular slots.
 * Supports JEI ghost ingredient drag-and-drop.
 *
 * <b>IMPORTANT:</b> This renderer should replicate the behavior of the
 * Partition Popup!
 */
public class PartitionTabRenderer extends CellTerminalRenderer {

    private static final int MAX_PARTITION_SLOTS = 63;

    public PartitionTabRenderer(FontRenderer fontRenderer, RenderItem itemRender) {
        super(fontRenderer, itemRender);
    }

    /**
     * Draw the partition tab content.
     */
    public void draw(List<Object> partitionLines, int currentScroll, int rowsVisible,
                     int relMouseX, int relMouseY, int absMouseX, int absMouseY,
                     Map<Long, StorageInfo> storageMap, int guiLeft, int guiTop,
                     RenderContext ctx) {
        int y = 18;
        int visibleTop = 18;
        int visibleBottom = 18 + rowsVisible * ROW_HEIGHT;
        int totalLines = partitionLines.size();

        for (int i = 0; i < rowsVisible && currentScroll + i < totalLines; i++) {
            Object line = partitionLines.get(currentScroll + i);
            int lineIndex = currentScroll + i;

            boolean isHovered = relMouseX >= 4 && relMouseX < 185
                && relMouseY >= y && relMouseY < y + ROW_HEIGHT;

            // Track hover state based on line type
            if (isHovered) {
                if (line instanceof CellContentRow) {
                    ctx.hoveredLineIndex = lineIndex;
                    // Also set hoveredPartitionCell for double-click storage lookup
                    ctx.hoveredPartitionCell = ((CellContentRow) line).getCell();
                    Gui.drawRect(GUI_INDENT, y - 1, 180, y + ROW_HEIGHT - 1, 0x50CCCCCC);
                } else if (line instanceof EmptySlotInfo) {
                    ctx.hoveredLineIndex = lineIndex;
                    Gui.drawRect(GUI_INDENT, y - 1, 180, y + ROW_HEIGHT - 1, 0x50CCCCCC);
                } else if (line instanceof StorageInfo) {
                    ctx.hoveredStorageLine = (StorageInfo) line;
                    ctx.hoveredLineIndex = lineIndex;
                    // Draw hover highlight for storage header (for double-click feedback)
                    Gui.drawRect(GUI_INDENT, y, 180, y + ROW_HEIGHT, 0x30FFFFFF);
                }
            }

            // Draw separator line above storage entries
            if (line instanceof StorageInfo && i > 0) {
                Gui.drawRect(GUI_INDENT, y - 1, 180, y, 0xFF606060);
            }

            // Tree line parameters
            boolean isFirstInGroup = isFirstInStorageGroup(partitionLines, lineIndex);
            boolean isLastInGroup = isLastInStorageGroup(partitionLines, lineIndex);
            boolean isFirstVisibleRow = (i == 0);
            boolean isLastVisibleRow = (i == rowsVisible - 1) || (currentScroll + i == totalLines - 1);
            boolean hasContentAbove = (lineIndex > 0) && !isFirstInGroup;
            boolean hasContentBelow = (lineIndex < totalLines - 1) && !isLastInGroup;

            if (line instanceof StorageInfo) {
                drawStorageLineSimple((StorageInfo) line, y, partitionLines, lineIndex, ctx);
            } else if (line instanceof CellContentRow) {
                CellContentRow row = (CellContentRow) line;
                drawCellPartitionLine(row.getCell(), row.getStartIndex(), row.isFirstRow(),
                    y, relMouseX, relMouseY, absMouseX, absMouseY,
                    isFirstInGroup, isLastInGroup, visibleTop, visibleBottom,
                    isFirstVisibleRow, isLastVisibleRow, hasContentAbove, hasContentBelow,
                    storageMap, guiLeft, guiTop, ctx);
            } else if (line instanceof EmptySlotInfo) {
                drawEmptySlotLine((EmptySlotInfo) line, y, relMouseX, relMouseY,
                    isFirstInGroup, isLastInGroup, visibleTop, visibleBottom,
                    isFirstVisibleRow, isLastVisibleRow, hasContentAbove, hasContentBelow,
                    storageMap, ctx);
            }

            y += ROW_HEIGHT;
        }
    }

    private void drawStorageLineSimple(StorageInfo storage, int y, List<Object> lines, int lineIndex, RenderContext ctx) {
        // Track this storage for priority field rendering
        ctx.visibleStorages.add(new RenderContext.VisibleStorageEntry(storage, y));

        // Draw vertical tree line connecting to cells below (only if there are cells following)
        boolean hasCellsFollowing = lineIndex + 1 < lines.size()
            && (lines.get(lineIndex + 1) instanceof CellContentRow || lines.get(lineIndex + 1) instanceof EmptySlotInfo);

        if (hasCellsFollowing) {
            int lineX = GUI_INDENT + 7;
            Gui.drawRect(lineX, y + ROW_HEIGHT - 1, lineX + 1, y + ROW_HEIGHT, 0xFF808080);
        }

        // Draw block icon
        renderItemStack(storage.getBlockItem(), GUI_INDENT, y);

        // Draw name
        String name = storage.getName();
        if (name.length() > 12) name = name.substring(0, 10) + "...";
        fontRenderer.drawString(name, GUI_INDENT + 20, y + 1, 0x404040);

        String location = storage.getLocationString();
        fontRenderer.drawString(location, GUI_INDENT + 20, y + 9, 0x808080);
    }

    private void drawCellPartitionLine(CellInfo cell, int startIndex, boolean isFirstRow,
            int y, int mouseX, int mouseY, int absMouseX, int absMouseY,
            boolean isFirstInGroup, boolean isLastInGroup, int visibleTop, int visibleBottom,
            boolean isFirstVisibleRow, boolean isLastVisibleRow,
            boolean hasContentAbove, boolean hasContentBelow,
            Map<Long, StorageInfo> storageMap, int guiLeft, int guiTop,
            RenderContext ctx) {

        int lineX = GUI_INDENT + 7;

        if (isFirstRow) {
            drawTreeLines(lineX, y, true, isFirstInGroup, isLastInGroup,
                visibleTop, visibleBottom, isFirstVisibleRow, isLastVisibleRow,
                hasContentAbove, hasContentBelow, false);

            // TODO: should we push the slot a bit right to place the button better?
            // Draw clear-partition button on tree line (red dot)
            int buttonX = lineX - 5;
            int buttonY = y + 4;
            int buttonSize = 8;

            boolean clearPartitionHovered = mouseX >= buttonX && mouseX < buttonX + buttonSize
                && mouseY >= buttonY && mouseY < buttonY + buttonSize;

            // Draw background that covers tree line area first
            Gui.drawRect(buttonX - 1, buttonY - 1, buttonX + buttonSize + 1, buttonY + buttonSize + 1, 0xFF8B8B8B);

            // Draw small button with red dot
            int btnColor = clearPartitionHovered ? 0xFF707070 : 0xFF8B8B8B;
            Gui.drawRect(buttonX, buttonY, buttonX + buttonSize, buttonY + buttonSize, btnColor);
            Gui.drawRect(buttonX, buttonY, buttonX + buttonSize, buttonY + 1, 0xFFFFFFFF);
            Gui.drawRect(buttonX, buttonY, buttonX + 1, buttonY + buttonSize, 0xFFFFFFFF);
            Gui.drawRect(buttonX, buttonY + buttonSize - 1, buttonX + buttonSize, buttonY + buttonSize, 0xFF555555);
            Gui.drawRect(buttonX + buttonSize - 1, buttonY, buttonX + buttonSize, buttonY + buttonSize, 0xFF555555);

            // Draw red dot inside button
            Gui.drawRect(buttonX + 1, buttonY + 1, buttonX + buttonSize - 1, buttonY + buttonSize - 1, 0xFFCC3333);

            if (clearPartitionHovered) ctx.hoveredClearPartitionButtonCell = cell;

            // Draw upgrade icons to the left of the cell slot
            drawCellUpgradeIcons(cell, 3, y);

            // Draw cell slot background
            drawSlotBackground(CELL_INDENT, y);

            // Check if mouse is over cell slot
            boolean cellHovered = mouseX >= CELL_INDENT && mouseX < CELL_INDENT + 16
                    && mouseY >= y && mouseY < y + 16;

            if (cellHovered) {
                Gui.drawRect(CELL_INDENT + 1, y + 1, CELL_INDENT + 15, y + 15, 0x80FFFFFF);
                StorageInfo storage = storageMap.get(cell.getParentStorageId());
                if (storage != null) {
                    ctx.hoveredCellStorage = storage;
                    ctx.hoveredCellCell = cell;
                    ctx.hoveredCellSlotIndex = cell.getSlot();
                    ctx.hoveredContentStack = cell.getCellItem();
                    ctx.hoveredContentX = absMouseX;
                    ctx.hoveredContentY = absMouseY;
                }
            }

            // Draw cell icon
            renderItemStack(cell.getCellItem(), CELL_INDENT, y);
        } else {
            // Continuation rows: only draw tree lines if there are MORE cells after this one
            // If this cell is the last in the group, no tree lines for continuation rows
            if (!isLastInGroup) {
                drawTreeLines(lineX, y, false, isFirstInGroup, false,
                    visibleTop, visibleBottom, isFirstVisibleRow, isLastVisibleRow,
                    hasContentAbove, hasContentBelow, false);
            }
        }

        // Draw partition item slots for this row
        List<ItemStack> partitions = cell.getPartition();
        int slotStartX = CELL_INDENT + 20;

        for (int i = 0; i < SLOTS_PER_ROW; i++) {
            int partitionIndex = startIndex + i;

            // Stop if we've reached the maximum partition slots
            if (partitionIndex >= MAX_PARTITION_SLOTS) break;

            int slotX = slotStartX + (i * 16);
            int slotY = y;

            // Draw partition slot background (orange/amber tint)
            drawPartitionSlotBackground(slotX, slotY);

            // Track slot position for JEI ghost ingredients
            int absSlotX = guiLeft + slotX;
            int absSlotY = guiTop + slotY;
            ctx.partitionSlotTargets.add(new RenderContext.PartitionSlotTarget(
                cell, partitionIndex, absSlotX, absSlotY, 16, 16));

            boolean slotHovered = mouseX >= slotX && mouseX < slotX + 16
                    && mouseY >= slotY && mouseY < slotY + 16;

            if (partitionIndex < partitions.size() && !partitions.get(partitionIndex).isEmpty()) {
                ItemStack stack = partitions.get(partitionIndex);
                renderItemStack(stack, slotX, slotY);

                if (slotHovered) {
                    Gui.drawRect(slotX + 1, slotY + 1, slotX + 15, slotY + 15, 0x80FFFFFF);
                    ctx.hoveredContentStack = stack;
                    ctx.hoveredContentX = absMouseX;
                    ctx.hoveredContentY = absMouseY;
                    ctx.hoveredPartitionSlotIndex = partitionIndex;
                    ctx.hoveredPartitionCell = cell;
                }
            } else if (slotHovered) {
                // Empty slot hover
                Gui.drawRect(slotX + 1, slotY + 1, slotX + 15, slotY + 15, 0x40FFFFFF);
                ctx.hoveredPartitionSlotIndex = partitionIndex;
                ctx.hoveredPartitionCell = cell;
            }
        }
    }

    /**
     * Draw a partition slot background with orange/amber tint.
     */
    private void drawPartitionSlotBackground(int x, int y) {
        Gui.drawRect(x, y, x + 16, y + 16, 0xFF9B8B7B);
        Gui.drawRect(x, y, x + 15, y + 1, 0xFF473737);
        Gui.drawRect(x, y, x + 1, y + 15, 0xFF473737);
        Gui.drawRect(x + 1, y + 15, x + 16, y + 16, 0xFFFFEEDD);
        Gui.drawRect(x + 15, y + 1, x + 16, y + 15, 0xFFFFEEDD);
    }

    private void drawEmptySlotLine(EmptySlotInfo emptySlot, int y, int mouseX, int mouseY,
            boolean isFirstInGroup, boolean isLastInGroup, int visibleTop, int visibleBottom,
            boolean isFirstVisibleRow, boolean isLastVisibleRow,
            boolean hasContentAbove, boolean hasContentBelow,
            Map<Long, StorageInfo> storageMap, RenderContext ctx) {

        int lineX = GUI_INDENT + 7;
        drawTreeLines(lineX, y, true, isFirstInGroup, isLastInGroup,
            visibleTop, visibleBottom, isFirstVisibleRow, isLastVisibleRow,
            hasContentAbove, hasContentBelow, false);

        // Draw empty slot with proper slot background
        drawSlotBackground(CELL_INDENT, y);

        // Check if mouse is over slot
        boolean slotHovered = mouseX >= CELL_INDENT && mouseX < CELL_INDENT + 16
                && mouseY >= y && mouseY < y + 16;

        if (slotHovered) {
            Gui.drawRect(CELL_INDENT + 1, y + 1, CELL_INDENT + 15, y + 15, 0x80FFFFFF);
            StorageInfo storage = storageMap.get(emptySlot.getParentStorageId());
            if (storage != null) {
                ctx.hoveredCellStorage = storage;
                ctx.hoveredCellSlotIndex = emptySlot.getSlot();
            }
        }
    }
}
