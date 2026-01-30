package com.cellterminal.integration.storagebus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagList;

import appeng.api.networking.IGrid;

import com.cellterminal.CellTerminal;
import com.cellterminal.container.handler.StorageBusDataHandler.StorageBusTracker;

/**
 * Registry for storage bus scanners.
 */
public final class StorageBusScannerRegistry {

    private static final List<IStorageBusScanner> scanners = new ArrayList<>();

    private StorageBusScannerRegistry() {}

    public static void register(IStorageBusScanner scanner) {
        if (scanner == null) {
            CellTerminal.LOGGER.warn("Attempted to register null storage bus scanner");
            return;
        }
        scanners.add(scanner);
        CellTerminal.LOGGER.info("Registered storage bus scanner: {}", scanner.getId());
    }

    public static void scanAll(IGrid grid, NBTTagList out, Map<Long, StorageBusTracker> trackerMap) {
        for (IStorageBusScanner scanner : scanners) {
            if (!scanner.isAvailable()) continue;
            try {
                scanner.scanStorageBuses(grid, out, trackerMap);
            } catch (Exception e) {
                CellTerminal.LOGGER.error("Error scanning storage buses with {}: {}", scanner.getId(), e.getMessage());
            }
        }
    }

    public static boolean hasAnyScanners() {
        return scanners.stream().anyMatch(IStorageBusScanner::isAvailable);
    }

    public static List<IStorageBusScanner> getScanners() {
        return new ArrayList<>(scanners);
    }
}
