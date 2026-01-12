package com.cellterminal.client;


/**
 * Placeholder for an empty cell slot in a storage device.
 * Used to show empty slots in the Cell Terminal GUI.
 */
public class EmptySlotInfo {

    private final long parentStorageId;
    private final int slot;

    public EmptySlotInfo(long parentStorageId, int slot) {
        this.parentStorageId = parentStorageId;
        this.slot = slot;
    }

    public long getParentStorageId() {
        return parentStorageId;
    }

    public int getSlot() {
        return slot;
    }
}
