package com.cellterminal.integration.subnet;

import java.util.Map;

import net.minecraft.nbt.NBTTagList;

import appeng.api.networking.IGrid;

import com.cellterminal.container.handler.SubnetDataHandler.SubnetTracker;


/**
 * Interface for scanning and collecting subnet connections from an ME network grid.
 * 
 * A subnet is a separate ME grid that connects to the main network via Storage Bus -> Interface
 * pattern (commonly called "ME Passthrough"). This allows the main network to access the subnet's
 * storage without merging the grids.
 * 
 * Implementations should be registered via {@link SubnetScannerRegistry}.
 */
public interface ISubnetScanner {

    /**
     * Unique identifier for this scanner.
     */
    String getId();

    /**
     * Whether this scanner can operate (e.g., required mod loaded).
     */
    boolean isAvailable();

    /**
     * Scan the grid for subnet connections and append their NBT data to the provided list.
     * Also populate the tracker map with {subnetId -> tracker} entries for server-side actions.
     * 
     * @param grid The ME network grid to scan
     * @param out NBTTagList to append subnet connection data to
     * @param trackerMap Map to populate with subnet trackers for server-side operations
     * @param playerId The player ID for security permission checks
     */
    void scanSubnets(IGrid grid, NBTTagList out, Map<Long, SubnetTracker> trackerMap, int playerId);
}
