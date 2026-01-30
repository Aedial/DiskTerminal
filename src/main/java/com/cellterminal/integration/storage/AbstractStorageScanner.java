package com.cellterminal.integration.storage;

import net.minecraft.nbt.NBTTagList;

import appeng.api.networking.IGrid;

import com.cellterminal.container.handler.CellDataHandler;


/**
 * Abstract base class for storage scanners providing sane defaults.
 * Implementations can override methods to customize behavior for specific storage types.
 *
 * Default behavior:
 * - Priority is supported (drives have priority)
 * - Uses standard AE2 cell slot limits (63 types max)
 * - Standard slot count per row (8 for cells)
 */
public abstract class AbstractStorageScanner implements IStorageScanner {


    /**
     * Maximum number of config/partition slots.
     * This is the standard AE2 limit for both cells and storage buses.
     */
    public static final int MAX_PARTITION_SLOTS = 63;

    /**
     * Number of content slots per row for cell displays.
     */
    public static final int CELL_SLOTS_PER_ROW = 8;

    @Override
    public abstract String getId();

    @Override
    public abstract boolean isAvailable();

    @Override
    public abstract void scanStorages(IGrid grid, NBTTagList storageList, CellDataHandler.StorageTrackerCallback callback, int slotLimit);

    /**
     * Check if drives from this scanner support priority.
     * Default: true (most drives support priority).
     *
     * @return true if priority editing should be shown for this storage type
     */
    public boolean supportsPriority() {
        return true;
    }
}
