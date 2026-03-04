package com.cellterminal.client;

import net.minecraft.item.ItemStack;


/**
 * Holds information about a cell stored in the temporary area.
 * This wraps a CellInfo (or null for empty slots) with the temp slot index.
 */
public class TempCellInfo {

    private final int tempSlotIndex;
    private final ItemStack cellStack;
    private final CellInfo cellInfo;

    /**
     * Create a temp cell info for an occupied slot.
     *
     * @param tempSlotIndex The index in the temp cell storage
     * @param cellStack The actual cell ItemStack
     * @param cellInfo The parsed cell information (may be null if cell couldn't be parsed)
     */
    public TempCellInfo(int tempSlotIndex, ItemStack cellStack, CellInfo cellInfo) {
        this.tempSlotIndex = tempSlotIndex;
        this.cellStack = cellStack;
        this.cellInfo = cellInfo;
    }

    /**
     * Create a temp cell info for an empty slot.
     *
     * @param tempSlotIndex The index in the temp cell storage
     */
    public TempCellInfo(int tempSlotIndex) {
        this.tempSlotIndex = tempSlotIndex;
        this.cellStack = ItemStack.EMPTY;
        this.cellInfo = null;
    }

    /**
     * Get the slot index in the temp storage.
     */
    public int getTempSlotIndex() {
        return tempSlotIndex;
    }

    /**
     * Get the cell ItemStack (may be empty for empty slots).
     */
    public ItemStack getCellStack() {
        return cellStack;
    }

    /**
     * Get the cell info (may be null for empty slots or if cell couldn't be parsed).
     */
    public CellInfo getCellInfo() {
        return cellInfo;
    }

    /**
     * Check if this slot has a valid cell.
     */
    public boolean hasCell() {
        return !cellStack.isEmpty() && cellInfo != null;
    }

    /**
     * Check if this slot is empty (used for showing the empty drop slot).
     */
    public boolean isEmpty() {
        return cellStack.isEmpty();
    }
}
