package com.cellterminal.gui.cells;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.cellterminal.client.CellInfo;
import com.cellterminal.client.StorageInfo;
import com.cellterminal.gui.render.RenderContext;


/**
 * Tracks hover state for cell-related GUI elements.
 *
 * This class encapsulates all the hover tracking for cells in the
 * Inventory and Partition tabs. It is updated during rendering and
 * consumed by click handlers and tooltip rendering.
 */
public class CellHoverState {

    // Hovered cell (for cell slot hover)
    private CellInfo hoveredCell = null;
    private StorageInfo hoveredCellStorage = null;
    private int hoveredCellSlotIndex = -1;

    // Hovered content item within a cell
    private ItemStack hoveredContentStack = ItemStack.EMPTY;
    private int hoveredContentSlotIndex = -1;
    private int hoveredContentX = 0;
    private int hoveredContentY = 0;

    // Hovered partition slot
    private CellInfo hoveredPartitionCell = null;
    private int hoveredPartitionSlotIndex = -1;

    // Hovered action buttons
    private CellInfo hoveredPartitionAllButtonCell = null;
    private CellInfo hoveredClearPartitionButtonCell = null;

    // Hovered storage line (header row)
    private StorageInfo hoveredStorageLine = null;
    private int hoveredLineIndex = -1;

    // JEI ghost ingredient targets
    private final List<RenderContext.PartitionSlotTarget> partitionSlotTargets;

    public CellHoverState(List<RenderContext.PartitionSlotTarget> partitionSlotTargets) {
        this.partitionSlotTargets = partitionSlotTargets;
    }

    /**
     * Reset all hover state at the beginning of a render cycle.
     */
    public void reset() {
        hoveredCell = null;
        hoveredCellStorage = null;
        hoveredCellSlotIndex = -1;
        hoveredContentStack = ItemStack.EMPTY;
        hoveredContentSlotIndex = -1;
        hoveredContentX = 0;
        hoveredContentY = 0;
        hoveredPartitionCell = null;
        hoveredPartitionSlotIndex = -1;
        hoveredPartitionAllButtonCell = null;
        hoveredClearPartitionButtonCell = null;
        hoveredStorageLine = null;
        hoveredLineIndex = -1;
        partitionSlotTargets.clear();
    }

    // ========================================
    // CELL SLOT HOVER
    // ========================================

    public void setHoveredCell(CellInfo cell, StorageInfo storage, int slotIndex) {
        this.hoveredCell = cell;
        this.hoveredCellStorage = storage;
        this.hoveredCellSlotIndex = slotIndex;
    }

    public CellInfo getHoveredCell() {
        return hoveredCell;
    }

    public StorageInfo getHoveredCellStorage() {
        return hoveredCellStorage;
    }

    public int getHoveredCellSlotIndex() {
        return hoveredCellSlotIndex;
    }

    // ========================================
    // CONTENT HOVER
    // ========================================

    public void setHoveredContent(ItemStack stack, int slotIndex, int absX, int absY) {
        this.hoveredContentStack = stack;
        this.hoveredContentSlotIndex = slotIndex;
        this.hoveredContentX = absX;
        this.hoveredContentY = absY;
    }

    public ItemStack getHoveredContentStack() {
        return hoveredContentStack;
    }

    public int getHoveredContentSlotIndex() {
        return hoveredContentSlotIndex;
    }

    public int getHoveredContentX() {
        return hoveredContentX;
    }

    public int getHoveredContentY() {
        return hoveredContentY;
    }

    // ========================================
    // PARTITION SLOT HOVER
    // ========================================

    public void setHoveredPartitionSlot(CellInfo cell, int slotIndex) {
        this.hoveredPartitionCell = cell;
        this.hoveredPartitionSlotIndex = slotIndex;
    }

    public CellInfo getHoveredPartitionCell() {
        return hoveredPartitionCell;
    }

    public int getHoveredPartitionSlotIndex() {
        return hoveredPartitionSlotIndex;
    }

    // ========================================
    // BUTTON HOVER
    // ========================================

    public void setHoveredPartitionAllButton(CellInfo cell) {
        this.hoveredPartitionAllButtonCell = cell;
    }

    public CellInfo getHoveredPartitionAllButtonCell() {
        return hoveredPartitionAllButtonCell;
    }

    public void setHoveredClearPartitionButton(CellInfo cell) {
        this.hoveredClearPartitionButtonCell = cell;
    }

    public CellInfo getHoveredClearPartitionButtonCell() {
        return hoveredClearPartitionButtonCell;
    }

    // ========================================
    // STORAGE LINE HOVER
    // ========================================

    public void setHoveredStorageLine(StorageInfo storage, int lineIndex) {
        this.hoveredStorageLine = storage;
        this.hoveredLineIndex = lineIndex;
    }

    public StorageInfo getHoveredStorageLine() {
        return hoveredStorageLine;
    }

    public int getHoveredLineIndex() {
        return hoveredLineIndex;
    }

    // ========================================
    // JEI GHOST TARGETS
    // ========================================

    public void addPartitionSlotTarget(CellInfo cell, int slotIndex, int x, int y, int width, int height) {
        partitionSlotTargets.add(new RenderContext.PartitionSlotTarget(cell, slotIndex, x, y, width, height));
    }

    public List<RenderContext.PartitionSlotTarget> getPartitionSlotTargets() {
        return partitionSlotTargets;
    }

    /**
     * Copy hover state to a RenderContext for compatibility with existing code.
     */
    public void copyToRenderContext(RenderContext ctx) {
        ctx.hoveredCellCell = hoveredCell;
        ctx.hoveredCellStorage = hoveredCellStorage;
        ctx.hoveredCellSlotIndex = hoveredCellSlotIndex;
        ctx.hoveredContentStack = hoveredContentStack;
        ctx.hoveredContentSlotIndex = hoveredContentSlotIndex;
        ctx.hoveredContentX = hoveredContentX;
        ctx.hoveredContentY = hoveredContentY;
        ctx.hoveredPartitionCell = hoveredPartitionCell;
        ctx.hoveredPartitionSlotIndex = hoveredPartitionSlotIndex;
        ctx.hoveredPartitionAllButtonCell = hoveredPartitionAllButtonCell;
        ctx.hoveredClearPartitionButtonCell = hoveredClearPartitionButtonCell;
        ctx.hoveredStorageLine = hoveredStorageLine;
        ctx.hoveredLineIndex = hoveredLineIndex;
    }
}
