package com.cellterminal.gui.cells;

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
import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.render.RenderContext;


/**
 * Main renderer for cell-related GUI elements.
 *
 * This class coordinates the rendering of cells in the Inventory and Partition tabs.
 * It delegates to specialized sub-renderers for slots, tree lines, and content items.
 *
 * Usage:
 * <pre>
 * CellRenderer renderer = new CellRenderer(fontRenderer, itemRender);
 * // For inventory tab:
 * renderer.drawCellInventoryLine(cell, startIndex, isFirstRow, y, ...);
 * // For partition tab:
 * renderer.drawCellPartitionLine(cell, startIndex, isFirstRow, y, ...);
 * </pre>
 */
public class CellRenderer {

    private final FontRenderer fontRenderer;
    private final CellSlotRenderer slotRenderer;
    private final CellTreeRenderer treeRenderer;

    public CellRenderer(FontRenderer fontRenderer, RenderItem itemRender) {
        this.fontRenderer = fontRenderer;
        this.slotRenderer = new CellSlotRenderer(fontRenderer, itemRender);
        this.treeRenderer = new CellTreeRenderer();
    }

    /**
     * Get the slot renderer for custom slot drawing.
     */
    public CellSlotRenderer getSlotRenderer() {
        return slotRenderer;
    }

    /**
     * Get the tree renderer for custom tree line drawing.
     */
    public CellTreeRenderer getTreeRenderer() {
        return treeRenderer;
    }

    // ========================================
    // STORAGE HEADER RENDERING
    // ========================================

    /**
     * Draw a storage header line (ME Drive or Chest).
     *
     * @param storage The storage info
     * @param y Y position
     * @param lines All lines in the list (for checking next line)
     * @param lineIndex Current line index
     * @param ctx Render context for tracking visible storages
     */
    public void drawStorageHeader(StorageInfo storage, int y, List<Object> lines, int lineIndex, RenderContext ctx) {
        // Track this storage for priority field rendering
        ctx.visibleStorages.add(new RenderContext.VisibleStorageEntry(storage, y));

        // Draw vertical tree line connecting to cells below
        boolean hasCellsFollowing = lineIndex + 1 < lines.size()
            && (lines.get(lineIndex + 1) instanceof CellContentRow
                || lines.get(lineIndex + 1) instanceof EmptySlotInfo);

        if (hasCellsFollowing) {
            int lineX = GuiConstants.GUI_INDENT + 7;
            treeRenderer.drawStorageConnector(lineX, y);
        }

        // Draw block icon
        slotRenderer.renderItemStack(storage.getBlockItem(), GuiConstants.GUI_INDENT, y);

        // Draw name (truncated if needed)
        String name = storage.getName();
        if (name.length() > 20) name = name.substring(0, 18) + "...";
        fontRenderer.drawString(name, GuiConstants.GUI_INDENT + 20, y + 1, GuiConstants.COLOR_TEXT_NORMAL);

        // Draw location
        String location = storage.getLocationString();
        fontRenderer.drawString(location, GuiConstants.GUI_INDENT + 20, y + 9, GuiConstants.COLOR_TEXT_SECONDARY);
    }

    // ========================================
    // INVENTORY LINE RENDERING
    // ========================================

    /**
     * Draw a cell inventory line (cell slot + content items).
     *
     * @param cell The cell info
     * @param startIndex Starting content index for this row
     * @param isFirstRow Whether this is the first row for this cell
     * @param y Y position
     * @param mouseX Relative mouse X
     * @param mouseY Relative mouse Y
     * @param absMouseX Absolute mouse X (for tooltip positioning)
     * @param absMouseY Absolute mouse Y (for tooltip positioning)
     * @param isFirstInGroup Whether this is the first cell in the storage group
     * @param isLastInGroup Whether this is the last cell in the storage group
     * @param visibleTop Top of visible area
     * @param visibleBottom Bottom of visible area
     * @param isFirstVisibleRow Whether this is the first visible row
     * @param isLastVisibleRow Whether this is the last visible row
     * @param hasContentAbove Whether there's content above (scrolled out)
     * @param hasContentBelow Whether there's content below (scrolled out)
     * @param storageMap Map of storage IDs to StorageInfo
     * @param ctx Render context for hover tracking
     */
    public void drawCellInventoryLine(CellInfo cell, int startIndex, boolean isFirstRow,
                                       int y, int mouseX, int mouseY, int absMouseX, int absMouseY,
                                       boolean isFirstInGroup, boolean isLastInGroup,
                                       int visibleTop, int visibleBottom,
                                       boolean isFirstVisibleRow, boolean isLastVisibleRow,
                                       boolean hasContentAbove, boolean hasContentBelow,
                                       Map<Long, StorageInfo> storageMap, RenderContext ctx) {

        int lineX = GuiConstants.GUI_INDENT + 7;

        if (isFirstRow) {
            drawFirstInventoryRow(cell, y, mouseX, mouseY, absMouseX, absMouseY,
                                  lineX, isFirstInGroup, isLastInGroup, visibleTop, visibleBottom,
                                  isFirstVisibleRow, isLastVisibleRow, hasContentAbove, hasContentBelow,
                                  storageMap, ctx);
        // Continuation rows: only draw tree lines if not last in group
        } else if (!isLastInGroup) {
            treeRenderer.drawTreeLines(lineX, y, false, isFirstInGroup, false,
                visibleTop, visibleBottom, isFirstVisibleRow, isLastVisibleRow,
                hasContentAbove, hasContentBelow, false);
        }

        // Draw content item slots
        drawContentSlots(cell, startIndex, y, mouseX, mouseY, absMouseX, absMouseY, ctx);
    }

    private void drawFirstInventoryRow(CellInfo cell, int y, int mouseX, int mouseY,
                                        int absMouseX, int absMouseY, int lineX,
                                        boolean isFirstInGroup, boolean isLastInGroup,
                                        int visibleTop, int visibleBottom,
                                        boolean isFirstVisibleRow, boolean isLastVisibleRow,
                                        boolean hasContentAbove, boolean hasContentBelow,
                                        Map<Long, StorageInfo> storageMap, RenderContext ctx) {

        // Draw tree lines
        treeRenderer.drawTreeLines(lineX, y, true, isFirstInGroup, isLastInGroup,
            visibleTop, visibleBottom, isFirstVisibleRow, isLastVisibleRow,
            hasContentAbove, hasContentBelow, false);

        // Draw partition-all button
        drawPartitionAllButton(cell, lineX, y, mouseX, mouseY, ctx);

        // Draw upgrade icons with tracking for tooltips and extraction
        slotRenderer.drawCellUpgradeIcons(cell, 3, y, ctx, ctx.guiLeft, ctx.guiTop);

        // Draw cell slot
        drawCellSlot(cell, y, mouseX, mouseY, absMouseX, absMouseY, storageMap, ctx);
    }

    private void drawPartitionAllButton(CellInfo cell, int lineX, int y, int mouseX, int mouseY, RenderContext ctx) {
        int buttonX = lineX - 5;
        int buttonY = y + 4;
        int buttonSize = GuiConstants.SMALL_BUTTON_SIZE;

        boolean hovered = mouseX >= buttonX && mouseX < buttonX + buttonSize
            && mouseY >= buttonY && mouseY < buttonY + buttonSize;

        // Draw background to cover tree line
        Gui.drawRect(buttonX - 1, buttonY - 1, buttonX + buttonSize + 1, buttonY + buttonSize + 1, GuiConstants.COLOR_SLOT_BACKGROUND);

        // Draw button with green fill
        slotRenderer.drawSmallButton(buttonX, buttonY, hovered, GuiConstants.COLOR_BUTTON_GREEN);

        if (hovered) ctx.hoveredPartitionAllButtonCell = cell;
    }

    private void drawCellSlot(CellInfo cell, int y, int mouseX, int mouseY,
                               int absMouseX, int absMouseY, Map<Long, StorageInfo> storageMap,
                               RenderContext ctx) {

        int cellX = GuiConstants.CELL_INDENT;
        slotRenderer.drawSlotBackground(cellX, y);

        boolean cellHovered = mouseX >= cellX && mouseX < cellX + GuiConstants.MINI_SLOT_SIZE
            && mouseY >= y && mouseY < y + GuiConstants.MINI_SLOT_SIZE;
        if (cellHovered) {
            slotRenderer.drawSlotHoverHighlight(cellX, y);

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

        slotRenderer.renderItemStack(cell.getCellItem(), cellX, y);
    }

    private void drawContentSlots(CellInfo cell, int startIndex, int y,
                                   int mouseX, int mouseY, int absMouseX, int absMouseY,
                                   RenderContext ctx) {

        List<ItemStack> contents = cell.getContents();
        List<ItemStack> partition = cell.getPartition();
        int slotStartX = GuiConstants.CELL_INDENT + 20;

        for (int i = 0; i < GuiConstants.CELL_SLOTS_PER_ROW; i++) {
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
            if (mouseX >= slotX && mouseX < slotX + GuiConstants.MINI_SLOT_SIZE
                && mouseY >= y && mouseY < y + GuiConstants.MINI_SLOT_SIZE) {
                slotRenderer.drawSlotHoverHighlight(slotX, y);
                ctx.hoveredContentStack = stack;
                ctx.hoveredContentX = absMouseX;
                ctx.hoveredContentY = absMouseY;
                ctx.hoveredContentSlotIndex = contentIndex;
                ctx.hoveredCellCell = cell;
            }
        }
    }

    // ========================================
    // PARTITION LINE RENDERING
    // ========================================

    /**
     * Draw a cell partition line (cell slot + partition slots).
     *
     * @param cell The cell info
     * @param startIndex Starting partition index for this row
     * @param isFirstRow Whether this is the first row for this cell
     * @param y Y position
     * @param mouseX Relative mouse X
     * @param mouseY Relative mouse Y
     * @param absMouseX Absolute mouse X
     * @param absMouseY Absolute mouse Y
     * @param isFirstInGroup Whether first in storage group
     * @param isLastInGroup Whether last in storage group
     * @param visibleTop Top of visible area
     * @param visibleBottom Bottom of visible area
     * @param isFirstVisibleRow Whether first visible row
     * @param isLastVisibleRow Whether last visible row
     * @param hasContentAbove Whether content above
     * @param hasContentBelow Whether content below
     * @param storageMap Storage map
     * @param guiLeft GUI left offset (for JEI targets)
     * @param guiTop GUI top offset (for JEI targets)
     * @param ctx Render context
     */
    public void drawCellPartitionLine(CellInfo cell, int startIndex, boolean isFirstRow,
                                       int y, int mouseX, int mouseY, int absMouseX, int absMouseY,
                                       boolean isFirstInGroup, boolean isLastInGroup,
                                       int visibleTop, int visibleBottom,
                                       boolean isFirstVisibleRow, boolean isLastVisibleRow,
                                       boolean hasContentAbove, boolean hasContentBelow,
                                       Map<Long, StorageInfo> storageMap,
                                       int guiLeft, int guiTop, RenderContext ctx) {

        int lineX = GuiConstants.GUI_INDENT + 7;

        if (isFirstRow) {
            drawFirstPartitionRow(cell, y, mouseX, mouseY, absMouseX, absMouseY,
                                  lineX, isFirstInGroup, isLastInGroup, visibleTop, visibleBottom,
                                  isFirstVisibleRow, isLastVisibleRow, hasContentAbove, hasContentBelow,
                                  storageMap, ctx);
        } else {
            if (!isLastInGroup) {
                treeRenderer.drawTreeLines(lineX, y, false, isFirstInGroup, false,
                    visibleTop, visibleBottom, isFirstVisibleRow, isLastVisibleRow,
                    hasContentAbove, hasContentBelow, false);
            }
        }

        // Draw partition slots
        drawPartitionSlots(cell, startIndex, y, mouseX, mouseY, absMouseX, absMouseY, guiLeft, guiTop, ctx);
    }

    private void drawFirstPartitionRow(CellInfo cell, int y, int mouseX, int mouseY,
                                        int absMouseX, int absMouseY, int lineX,
                                        boolean isFirstInGroup, boolean isLastInGroup,
                                        int visibleTop, int visibleBottom,
                                        boolean isFirstVisibleRow, boolean isLastVisibleRow,
                                        boolean hasContentAbove, boolean hasContentBelow,
                                        Map<Long, StorageInfo> storageMap, RenderContext ctx) {

        treeRenderer.drawTreeLines(lineX, y, true, isFirstInGroup, isLastInGroup,
            visibleTop, visibleBottom, isFirstVisibleRow, isLastVisibleRow,
            hasContentAbove, hasContentBelow, false);

        // Draw clear partition button (red)
        drawClearPartitionButton(cell, lineX, y, mouseX, mouseY, ctx);

        // Draw upgrade icons with tracking for tooltips and extraction
        slotRenderer.drawCellUpgradeIcons(cell, 3, y, ctx, ctx.guiLeft, ctx.guiTop);

        // Draw cell slot
        drawCellSlot(cell, y, mouseX, mouseY, absMouseX, absMouseY, storageMap, ctx);
    }

    private void drawClearPartitionButton(CellInfo cell, int lineX, int y, int mouseX, int mouseY, RenderContext ctx) {
        int buttonX = lineX - 5;
        int buttonY = y + 4;
        int buttonSize = GuiConstants.SMALL_BUTTON_SIZE;

        boolean hovered = mouseX >= buttonX && mouseX < buttonX + buttonSize
            && mouseY >= buttonY && mouseY < buttonY + buttonSize;

        // Draw background to cover tree line
        Gui.drawRect(buttonX - 1, buttonY - 1, buttonX + buttonSize + 1, buttonY + buttonSize + 1, GuiConstants.COLOR_SLOT_BACKGROUND);

        // Draw button with red fill
        slotRenderer.drawSmallButton(buttonX, buttonY, hovered, GuiConstants.COLOR_BUTTON_RED);

        if (hovered) ctx.hoveredClearPartitionButtonCell = cell;
    }

    private void drawPartitionSlots(CellInfo cell, int startIndex, int y,
                                     int mouseX, int mouseY, int absMouseX, int absMouseY,
                                     int guiLeft, int guiTop, RenderContext ctx) {

        List<ItemStack> partition = cell.getPartition();
        int slotStartX = GuiConstants.CELL_INDENT + 20;

        for (int i = 0; i < GuiConstants.CELL_SLOTS_PER_ROW; i++) {
            int partitionIndex = startIndex + i;
            if (partitionIndex >= GuiConstants.MAX_CELL_PARTITION_SLOTS) break;

            int slotX = slotStartX + (i * GuiConstants.MINI_SLOT_SIZE);

            // Draw partition slot with amber tint
            slotRenderer.drawPartitionSlotBackground(slotX, y);

            // Register JEI ghost target
            ctx.partitionSlotTargets.add(new RenderContext.PartitionSlotTarget(
                cell, partitionIndex, guiLeft + slotX, guiTop + y,
                GuiConstants.MINI_SLOT_SIZE, GuiConstants.MINI_SLOT_SIZE));

            // Draw partition item if present
            ItemStack partItem = partitionIndex < partition.size() ? partition.get(partitionIndex) : ItemStack.EMPTY;
            if (!partItem.isEmpty()) slotRenderer.renderItemStack(partItem, slotX, y);

            // Check hover
            if (mouseX >= slotX && mouseX < slotX + GuiConstants.MINI_SLOT_SIZE
                && mouseY >= y && mouseY < y + GuiConstants.MINI_SLOT_SIZE) {
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

    // ========================================
    // EMPTY SLOT RENDERING
    // ========================================

    /**
     * Draw an empty cell slot line.
     */
    public void drawEmptySlotLine(EmptySlotInfo emptySlot, int y, int mouseX, int mouseY,
                                   boolean isFirstInGroup, boolean isLastInGroup,
                                   int visibleTop, int visibleBottom,
                                   boolean isFirstVisibleRow, boolean isLastVisibleRow,
                                   boolean hasContentAbove, boolean hasContentBelow,
                                   Map<Long, StorageInfo> storageMap, RenderContext ctx) {

        int lineX = GuiConstants.GUI_INDENT + 7;
        treeRenderer.drawTreeLines(lineX, y, true, isFirstInGroup, isLastInGroup,
            visibleTop, visibleBottom, isFirstVisibleRow, isLastVisibleRow,
            hasContentAbove, hasContentBelow, false);

        int slotX = GuiConstants.CELL_INDENT;
        slotRenderer.drawSlotBackground(slotX, y);

        boolean slotHovered = mouseX >= slotX && mouseX < slotX + GuiConstants.MINI_SLOT_SIZE
            && mouseY >= y && mouseY < y + GuiConstants.MINI_SLOT_SIZE;
        if (slotHovered) {
            slotRenderer.drawSlotHoverHighlight(slotX, y);

            StorageInfo storage = storageMap.get(emptySlot.getParentStorageId());
            if (storage != null) {
                ctx.hoveredCellStorage = storage;
                ctx.hoveredCellSlotIndex = emptySlot.getSlot();
            }
        }
    }
}
