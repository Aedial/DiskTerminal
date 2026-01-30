package com.cellterminal.integration.storage;

import net.minecraft.nbt.NBTTagList;

import appeng.api.networking.IGrid;

import com.cellterminal.container.handler.CellDataHandler;


/**
 * Interface for scanning and collecting storage devices from an ME network grid.
 * Implementations should be registered via {@link StorageScannerRegistry}.
 *
 * Each scanner is responsible for a specific type of storage device (e.g., ME Drive, Improved Drive).
 */
public interface IStorageScanner {

    /**
     * Get the unique identifier for this scanner.
     * Used for logging and debugging purposes.
     *
     * @return unique scanner ID
     */
    String getId();

    /**
     * Check if this scanner is available (i.e., the required mod is loaded).
     * This method should cache the result for performance.
     *
     * @return true if the scanner can be used
     */
    boolean isAvailable();

    /**
     * Scan the grid for storage devices of this scanner's type and append their data
     * to the provided NBTTagList.
     *
     * @param grid the ME network grid to scan
     * @param storageList the list to append storage data to
     * @param callback callback to register storage trackers for server-side operations
     */
    default void scanStorages(IGrid grid, NBTTagList storageList, CellDataHandler.StorageTrackerCallback callback) {
        scanStorages(grid, storageList, callback, Integer.MAX_VALUE);
    }

    /**
     * Scan the grid for storage devices of this scanner's type and append their data
     * to the provided NBTTagList, with a limit on the number of types per cell.
     *
     * @param grid the ME network grid to scan
     * @param storageList the list to append storage data to
     * @param callback callback to register storage trackers for server-side operations
     * @param slotLimit maximum number of item types to include per cell
     */
    void scanStorages(IGrid grid, NBTTagList storageList, CellDataHandler.StorageTrackerCallback callback, int slotLimit);
}
