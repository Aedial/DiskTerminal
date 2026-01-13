package com.cellterminal.gui.render;

import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import com.cellterminal.client.CellContentRow;
import com.cellterminal.client.CellInfo;
import com.cellterminal.client.EmptySlotInfo;
import com.cellterminal.client.StorageInfo;


/**
 * Renderer for the Inventory tab (Tab 1).
 * Displays cells as expandable rows with their contents shown in a grid.
 * Content items show "P" indicator if they're in the cell's partition.
 *
 * <b>IMPORTANT:</b> This renderer should replicate the behavior of the
 * Inventory Popup!
 */
public class InventoryTabRenderer extends CellTerminalRenderer {

    public InventoryTabRenderer(FontRenderer fontRenderer, RenderItem itemRender) {
        super(fontRenderer, itemRender);
    }

    /**
     * Draw the inventory tab content.
     */
    public void draw(List<Object> inventoryLines, int currentScroll, int rowsVisible,
                     int relMouseX, int relMouseY, int absMouseX, int absMouseY,
                     Map<Long, StorageInfo> storageMap, RenderContext ctx) {
        int y = 18;
        int visibleTop = 18;
        int visibleBottom = 18 + rowsVisible * ROW_HEIGHT;
        int totalLines = inventoryLines.size();

        for (int i = 0; i < rowsVisible && currentScroll + i < totalLines; i++) {
            Object line = inventoryLines.get(currentScroll + i);
            int lineIndex = currentScroll + i;

            boolean isHovered = relMouseX >= 4 && relMouseX < 185
                && relMouseY >= y && relMouseY < y + ROW_HEIGHT;

            // Draw hover background
            if (isHovered && (line instanceof CellContentRow || line instanceof EmptySlotInfo)) {
                ctx.hoveredLineIndex = lineIndex;
                Gui.drawRect(GUI_INDENT, y - 1, 180, y + ROW_HEIGHT - 1, 0x50CCCCCC);
            }

            // Draw separator line above storage entries
            if (line instanceof StorageInfo && i > 0) {
                Gui.drawRect(GUI_INDENT, y - 1, 180, y, 0xFF606060);
            }

            // Tree line parameters
            boolean isFirstInGroup = isFirstInStorageGroup(inventoryLines, lineIndex);
            boolean isLastInGroup = isLastInStorageGroup(inventoryLines, lineIndex);
            boolean isFirstVisibleRow = (i == 0);
            boolean isLastVisibleRow = (i == rowsVisible - 1) || (currentScroll + i == totalLines - 1);
            boolean hasContentAbove = (lineIndex > 0) && !isFirstInGroup;
            boolean hasContentBelow = (lineIndex < totalLines - 1) && !isLastInGroup;

            if (line instanceof StorageInfo) {
                drawStorageLineSimple((StorageInfo) line, y, inventoryLines, lineIndex);
            } else if (line instanceof CellContentRow) {
                CellContentRow row = (CellContentRow) line;
                drawCellInventoryLine(row.getCell(), row.getStartIndex(), row.isFirstRow(),
                    y, relMouseX, relMouseY, absMouseX, absMouseY,
                    isFirstInGroup, isLastInGroup, visibleTop, visibleBottom,
                    isFirstVisibleRow, isLastVisibleRow, hasContentAbove, hasContentBelow,
                    storageMap, ctx);
            } else if (line instanceof EmptySlotInfo) {
                drawEmptySlotLine((EmptySlotInfo) line, y, relMouseX, relMouseY,
                    isFirstInGroup, isLastInGroup, visibleTop, visibleBottom,
                    isFirstVisibleRow, isLastVisibleRow, hasContentAbove, hasContentBelow,
                    storageMap, ctx);
            }

            y += ROW_HEIGHT;
        }
    }

    private void drawStorageLineSimple(StorageInfo storage, int y, List<Object> lines, int lineIndex) {
        // Draw vertical tree line connecting to cells below (only if there are cells following)
        boolean hasCellsFollowing = lineIndex + 1 < lines.size()
            && (lines.get(lineIndex + 1) instanceof CellContentRow || lines.get(lineIndex + 1) instanceof EmptySlotInfo);

        if (hasCellsFollowing) {
            int lineX = GUI_INDENT + 7;
            Gui.drawRect(lineX, y + ROW_HEIGHT - 1, lineX + 1, y + ROW_HEIGHT, 0xFF808080);
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

    private void drawCellInventoryLine(CellInfo cell, int startIndex, boolean isFirstRow,
            int y, int mouseX, int mouseY, int absMouseX, int absMouseY,
            boolean isFirstInGroup, boolean isLastInGroup, int visibleTop, int visibleBottom,
            boolean isFirstVisibleRow, boolean isLastVisibleRow,
            boolean hasContentAbove, boolean hasContentBelow,
            Map<Long, StorageInfo> storageMap, RenderContext ctx) {

        int lineX = GUI_INDENT + 7;

        if (isFirstRow) {
            drawTreeLines(lineX, y, true, isFirstInGroup, isLastInGroup,
                visibleTop, visibleBottom, isFirstVisibleRow, isLastVisibleRow,
                hasContentAbove, hasContentBelow);

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
                    hasContentAbove, hasContentBelow);
            }
        }

        // Draw content item slots for this row
        List<ItemStack> contents = cell.getContents();
        List<ItemStack> partition = cell.getPartition();
        int slotStartX = CELL_INDENT + 20;

        for (int i = 0; i < SLOTS_PER_ROW; i++) {
            int contentIndex = startIndex + i;
            int slotX = slotStartX + (i * 16);
            int slotY = y;

            // Draw mini slot background
            drawSlotBackground(slotX, slotY);

            if (contentIndex < contents.size() && !contents.get(contentIndex).isEmpty()) {
                ItemStack stack = contents.get(contentIndex);
                renderItemStack(stack, slotX, slotY);

                // Draw "P" indicator if this item is in partition
                if (isInPartition(stack, partition)) {
                    GlStateManager.disableLighting();
                    GlStateManager.disableDepth();
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(0.5f, 0.5f, 0.5f);
                    fontRenderer.drawStringWithShadow("P", (slotX + 1) * 2, (slotY + 1) * 2, 0xFF55FF55);
                    GlStateManager.popMatrix();
                    GlStateManager.enableDepth();
                }

                // Draw item count
                String countStr = formatItemCount(cell.getContentCount(contentIndex));
                int countWidth = fontRenderer.getStringWidth(countStr);
                GlStateManager.disableDepth();
                GlStateManager.pushMatrix();
                GlStateManager.scale(0.5f, 0.5f, 0.5f);
                fontRenderer.drawStringWithShadow(countStr, (slotX + 15) * 2 - countWidth, (slotY + 11) * 2, 0xFFFFFF);
                GlStateManager.popMatrix();
                GlStateManager.enableDepth();

                // Check hover for tooltip
                if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                    Gui.drawRect(slotX + 1, slotY + 1, slotX + 15, slotY + 15, 0x80FFFFFF);
                    ctx.hoveredContentStack = stack;
                    ctx.hoveredContentX = absMouseX;
                    ctx.hoveredContentY = absMouseY;
                    ctx.hoveredContentSlotIndex = contentIndex;
                    ctx.hoveredCellCell = cell;
                }
            }
        }
    }

    private void drawEmptySlotLine(EmptySlotInfo emptySlot, int y, int mouseX, int mouseY,
            boolean isFirstInGroup, boolean isLastInGroup, int visibleTop, int visibleBottom,
            boolean isFirstVisibleRow, boolean isLastVisibleRow,
            boolean hasContentAbove, boolean hasContentBelow,
            Map<Long, StorageInfo> storageMap, RenderContext ctx) {

        int lineX = GUI_INDENT + 7;
        drawTreeLines(lineX, y, true, isFirstInGroup, isLastInGroup,
            visibleTop, visibleBottom, isFirstVisibleRow, isLastVisibleRow,
            hasContentAbove, hasContentBelow);

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
