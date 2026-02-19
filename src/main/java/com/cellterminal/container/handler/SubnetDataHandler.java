package com.cellterminal.container.handler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.helpers.ICustomNameObject;
import appeng.helpers.IInterfaceHost;
import appeng.parts.misc.PartInterface;
import appeng.tile.misc.TileInterface;

import com.cellterminal.integration.subnet.SubnetScannerRegistry;


/**
 * Server-side handler for subnet data collection and management.
 * 
 * A subnet is a separate ME grid that connects to the main network via
 * Storage Bus -> Interface pattern (ME Passthrough). This allows the main
 * network to access the subnet's storage without merging the grids.
 */
public class SubnetDataHandler {

    /**
     * Tracker for subnet instances on the server side.
     * Stores references to the subnet's grid and connection points.
     */
    public static class SubnetTracker {

        public final long id;
        public final IGrid targetGrid;
        public final List<Object> connectionParts;  // Storage Buses and Interfaces that connect to this subnet
        public final List<TileEntity> hostTiles;
        public final List<Boolean> isOutbound;  // Whether each connection is outbound (Storage Bus on main) vs inbound (Interface on main)
        public final List<EnumFacing> connectionSides;  // The facing of each connection

        public SubnetTracker(long id, IGrid targetGrid) {
            this.id = id;
            this.targetGrid = targetGrid;
            this.connectionParts = new ArrayList<>();
            this.hostTiles = new ArrayList<>();
            this.isOutbound = new ArrayList<>();
            this.connectionSides = new ArrayList<>();
        }

        /**
         * Add an outbound connection (Storage Bus on main -> Interface on subnet).
         */
        public void addConnection(Object part, TileEntity hostTile) {
            connectionParts.add(part);
            if (!hostTiles.contains(hostTile)) hostTiles.add(hostTile);
            isOutbound.add(true);
            connectionSides.add(null);  // Side is derived from the part
        }

        /**
         * Add an inbound connection (Interface on main <- Storage Bus on subnet).
         */
        public void addInboundConnection(Object part, TileEntity hostTile, EnumFacing side) {
            connectionParts.add(part);
            if (!hostTiles.contains(hostTile)) hostTiles.add(hostTile);
            isOutbound.add(false);
            connectionSides.add(side);
        }
    }

    /**
     * Collect all subnets connected to the given grid.
     * 
     * @param grid The main ME network grid to scan
     * @param trackerMap Map to populate with subnet trackers (keyed by subnet ID)
     * @param playerId The player ID for security permission checks
     * @return NBTTagList containing all subnet data
     */
    public static NBTTagList collectSubnets(IGrid grid, Map<Long, SubnetTracker> trackerMap, int playerId) {
        NBTTagList subnetList = new NBTTagList();

        if (grid == null) return subnetList;

        // Delegate to registered scanners
        SubnetScannerRegistry.scanAll(grid, subnetList, trackerMap, playerId);

        return subnetList;
    }

    /**
     * Create a unique subnet ID from the grid's identity.
     * Uses System.identityHashCode combined with primary position for stability.
     * 
     * @param grid The subnet's grid
     * @param primaryNode The primary grid node (for position-based ID component)
     * @return A unique long ID for this subnet
     */
    public static long createSubnetId(IGrid grid, IGridNode primaryNode) {
        if (grid == null) return 0;

        // Combine grid identity hash with primary position for stability
        long gridHash = System.identityHashCode(grid);

        if (primaryNode != null && primaryNode.getGridBlock() != null) {
            DimensionalCoord loc = primaryNode.getGridBlock().getLocation();
            if (loc != null) {
                gridHash ^= loc.getPos().toLong();
                gridHash ^= ((long) loc.getWorld().provider.getDimension()) << 48;
            }
        }

        return gridHash;
    }

    /**
     * Handle subnet action from client (rename, favorite toggle).
     * 
     * @param trackerMap Current subnet trackers
     * @param subnetId The subnet ID to modify
     * @param action The action to perform
     * @param data Action-specific data (e.g., new name)
     * @param player The player performing the action
     * @return true if the action was handled
     */
    public static boolean handleSubnetAction(Map<Long, SubnetTracker> trackerMap, long subnetId,
                                              SubnetAction action, NBTTagCompound data, EntityPlayer player) {
        SubnetTracker tracker = trackerMap.get(subnetId);
        if (tracker == null) return false;

        switch (action) {
            case RENAME:
                // Store custom name in the primary interface's NBT
                return handleRename(tracker, data.getString("name"));

            case TOGGLE_FAVORITE:
                // Store favorite state (player-specific or interface NBT)
                return handleToggleFavorite(tracker, data.getBoolean("favorite"));

            default:
                return false;
        }
    }

    private static boolean handleRename(SubnetTracker tracker, String newName) {
        IInterfaceHost interfaceHost = findPrimaryInterfaceHost(tracker.targetGrid);
        if (interfaceHost == null) return false;

        // Both TileInterface and PartInterface implement ICustomNameObject
        if (!(interfaceHost instanceof ICustomNameObject)) return false;

        ICustomNameObject nameable = (ICustomNameObject) interfaceHost;

        // Use AE2's built-in custom name mechanism (ICustomNameObject)
        // Empty string clears the name, non-empty sets it
        if (newName == null || newName.trim().isEmpty()) {
            nameable.setCustomName(null);
        } else {
            nameable.setCustomName(newName.trim());
        }

        // Mark host tile dirty so it saves
        TileEntity tile = interfaceHost.getTileEntity();
        if (tile != null) tile.markDirty();

        return true;
    }

    private static boolean handleToggleFavorite(SubnetTracker tracker, boolean favorite) {
        IInterfaceHost interfaceHost = findPrimaryInterfaceHost(tracker.targetGrid);
        if (interfaceHost == null) return false;

        // Get the tile entity that holds the interface (for both TileInterface and PartInterface)
        TileEntity tile = interfaceHost.getTileEntity();
        if (tile == null) return false;

        // Use Forge's getTileData() to store custom data in the tile's NBT
        // This is automatically persisted when the world saves
        // For PartInterface, this stores in the cable bus tile's data with a unique key
        String favoriteKey = getFavoriteKey(interfaceHost);
        NBTTagCompound tileData = tile.getTileData();

        if (favorite) {
            tileData.setBoolean(favoriteKey, true);
        } else {
            // Remove the key when not favorited to keep NBT clean
            tileData.removeTag(favoriteKey);
        }

        // Mark tile dirty so it saves
        tile.markDirty();

        return true;
    }

    /**
     * Get the favorite key for an interface host.
     * For TileInterface, uses "cellterminal.favorite".
     * For PartInterface, includes the side to disambiguate multiple interfaces on the same cable.
     */
    private static String getFavoriteKey(IInterfaceHost interfaceHost) {
        if (interfaceHost instanceof PartInterface) {
            PartInterface part = (PartInterface) interfaceHost;

            return "cellterminal.favorite." + part.getSide().ordinal();
        }

        return "cellterminal.favorite";
    }

    /**
     * Find the primary interface host (TileInterface or PartInterface) in a subnet's grid.
     * This is the interface where we store subnet metadata (name, favorite).
     * 
     * @param grid The subnet's grid
     * @return The primary IInterfaceHost, or null if not found
     */
    public static IInterfaceHost findPrimaryInterfaceHost(IGrid grid) {
        if (grid == null) return null;

        // First, try to find a TileInterface (block form - preferred)
        for (IGridNode node : grid.getMachines(TileInterface.class)) {
            if (node.getMachine() instanceof TileInterface) {
                return (IInterfaceHost) node.getMachine();
            }
        }

        // Fall back to PartInterface (cable-attached form)
        for (IGridNode node : grid.getMachines(PartInterface.class)) {
            if (node.getMachine() instanceof PartInterface) {
                return (IInterfaceHost) node.getMachine();
            }
        }

        return null;
    }

    /**
     * Actions that can be performed on subnets.
     */
    public enum SubnetAction {
        RENAME,
        TOGGLE_FAVORITE
    }
}
