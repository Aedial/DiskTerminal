package com.cellterminal.integration.storage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagList;

import appeng.api.networking.IGrid;

import com.cellterminal.CellTerminal;
import com.cellterminal.container.handler.CellDataHandler;


/**
 * Registry for storage scanners.
 * Allows mods to register their own storage device types for scanning.
 *
 * Built-in scanners for vanilla AE2 (TileDrive, TileChest) are registered automatically.
 * Optional mod integrations (CrazyAE, ECOAEExtension, etc.) register their scanners
 * during post-init if the mods are present.
 */
public final class StorageScannerRegistry {

    private static final List<IStorageScanner> scanners = new ArrayList<>();

    private StorageScannerRegistry() {}

    /**
     * Register a storage scanner.
     * Scanners are called in registration order.
     *
     * @param scanner the scanner to register
     */
    public static void register(IStorageScanner scanner) {
        if (scanner == null) {
            CellTerminal.LOGGER.warn("Attempted to register null storage scanner");

            return;
        }

        scanners.add(scanner);
        CellTerminal.LOGGER.info("Registered storage scanner: {}", scanner.getId());
    }

    /**
     * Scan all storage devices from all registered scanners.
     *
     * @param grid the ME network grid to scan
     * @param storageList the list to append storage data to
     * @param callback callback to register storage trackers
     */
    public static void scanAllStorages(IGrid grid, NBTTagList storageList, CellDataHandler.StorageTrackerCallback callback) {
        for (IStorageScanner scanner : scanners) {
            if (!scanner.isAvailable()) continue;

            try {
                scanner.scanStorages(grid, storageList, callback);
            } catch (Exception e) {
                CellTerminal.LOGGER.error("Error scanning storage with {}: {}", scanner.getId(), e.getMessage());
            }
        }
    }

    /**
     * Get all registered scanners (for debugging).
     *
     * @return copy of the scanner list
     */
    public static List<IStorageScanner> getScanners() {
        return new ArrayList<>(scanners);
    }

    /**
     * Check if any scanners are registered.
     *
     * @return true if at least one scanner is available
     */
    public static boolean hasAnyScanners() {
        return scanners.stream().anyMatch(IStorageScanner::isAvailable);
    }
}
