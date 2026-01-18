package com.cellterminal.client;


/**
 * Represents a row of storage bus contents or partitions for display in storage bus tabs.
 * Each StorageBusContentRow shows up to 9 items from a storage bus, starting at a given index.
 */
public class StorageBusContentRow {

    private final StorageBusInfo storageBus;
    private final int startIndex;
    private final boolean isFirstRow;

    /**
     * @param storageBus The storage bus this row belongs to
     * @param startIndex The starting index of items to display in this row (0, 9, 18, etc)
     * @param isFirstRow Whether this is the first row for this storage bus (shows bus info)
     */
    public StorageBusContentRow(StorageBusInfo storageBus, int startIndex, boolean isFirstRow) {
        this.storageBus = storageBus;
        this.startIndex = startIndex;
        this.isFirstRow = isFirstRow;
    }

    public StorageBusInfo getStorageBus() {
        return storageBus;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public boolean isFirstRow() {
        return isFirstRow;
    }
}
