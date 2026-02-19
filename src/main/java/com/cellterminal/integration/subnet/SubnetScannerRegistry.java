package com.cellterminal.integration.subnet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagList;

import appeng.api.networking.IGrid;

import com.cellterminal.CellTerminal;
import com.cellterminal.container.handler.SubnetDataHandler.SubnetTracker;


/**
 * Registry for subnet scanners.
 * Scanners detect and collect information about subnets connected to an ME network.
 */
public final class SubnetScannerRegistry {

    private static final List<ISubnetScanner> scanners = new ArrayList<>();

    private SubnetScannerRegistry() {}

    /**
     * Register a subnet scanner.
     * 
     * @param scanner The scanner to register
     */
    public static void register(ISubnetScanner scanner) {
        if (scanner == null) {
            CellTerminal.LOGGER.warn("Attempted to register null subnet scanner");
            return;
        }

        scanners.add(scanner);
        CellTerminal.LOGGER.info("Registered subnet scanner: {}", scanner.getId());
    }

    /**
     * Scan all subnets using all registered scanners.
     * 
     * @param grid The main ME network grid
     * @param out NBTTagList to append subnet data to
     * @param trackerMap Map to populate with subnet trackers
     * @param playerId The player ID for security permission checks
     */
    public static void scanAll(IGrid grid, NBTTagList out, Map<Long, SubnetTracker> trackerMap, int playerId) {
        for (ISubnetScanner scanner : scanners) {
            if (!scanner.isAvailable()) continue;

            try {
                scanner.scanSubnets(grid, out, trackerMap, playerId);
            } catch (Exception e) {
                CellTerminal.LOGGER.error("Error scanning subnets with {}: {}", scanner.getId(), e.getMessage());
            }
        }
    }

    /**
     * Check if any subnet scanners are available.
     */
    public static boolean hasAnyScanners() {
        return scanners.stream().anyMatch(ISubnetScanner::isAvailable);
    }

    /**
     * Get all registered scanners.
     */
    public static List<ISubnetScanner> getScanners() {
        return new ArrayList<>(scanners);
    }
}
