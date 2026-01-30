package com.cellterminal.integration.storagebus;

import java.util.Map;

import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.IGrid;

import com.cellterminal.container.handler.StorageBusDataHandler.StorageBusTracker;

/**
 * Interface for scanning and collecting storage buses from an ME network grid.
 * Implementations should be registered via {@link StorageBusScannerRegistry}.
 */
public interface IStorageBusScanner {

    /**
     * Unique identifier for this scanner.
     */
    String getId();

    /**
     * Whether this scanner can operate (e.g., required mod loaded).
     */
    boolean isAvailable();

    /**
     * Scan the grid for storage buses and append their NBT data to the provided list.
     * Also populate the tracker map with {busId -> tracker} entries for server-side actions.
     */
    void scanStorageBuses(IGrid grid, NBTTagList out, Map<Long, StorageBusTracker> trackerMap);

    /**
     * Whether buses scanned by this implementation support priority editing.
     */
    default boolean supportsPriority() { return true; }

    /**
     * Whether buses scanned by this implementation support IO mode (access restriction) changes.
     */
    default boolean supportsIOMode() { return true; }
}
