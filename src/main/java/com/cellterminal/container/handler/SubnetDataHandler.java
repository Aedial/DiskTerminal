package com.cellterminal.container.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.fluids.util.IAEFluidTank;
import appeng.helpers.ICustomNameObject;
import appeng.helpers.IInterfaceHost;
import appeng.parts.misc.PartInterface;
import appeng.fluids.parts.PartFluidStorageBus;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.misc.TileInterface;
import appeng.util.helpers.ItemHandlerUtil;

import com.cellterminal.integration.subnet.SubnetScannerRegistry;
import com.cellterminal.network.PacketSubnetPartitionAction;


/**
 * Server-side handler for subnet data collection and management.
 * <p>
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
            hostTiles.add(hostTile);  // Must stay parallel with connectionParts
            isOutbound.add(true);
            connectionSides.add(null);  // Side is derived from the part
        }

        /**
         * Add an inbound connection (Interface on main <- Storage Bus on subnet).
         */
        public void addInboundConnection(Object part, TileEntity hostTile, EnumFacing side) {
            connectionParts.add(part);
            hostTiles.add(hostTile);  // Must stay parallel with connectionParts
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
     * @param slotLimit Maximum number of inventory item types to include per subnet
     * @return NBTTagList containing all subnet data
     */
    public static NBTTagList collectSubnets(IGrid grid, Map<Long, SubnetTracker> trackerMap, int playerId, int slotLimit) {
        NBTTagList subnetList = new NBTTagList();

        if (grid == null) return subnetList;

        // Delegate to registered scanners
        SubnetScannerRegistry.scanAll(grid, subnetList, trackerMap, playerId, slotLimit);

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
     * Handle subnet connection partition modification.
     * Finds the storage bus at (pos, side) within the subnet and modifies its config.
     *
     * @param trackerMap Current subnet trackers
     * @param subnetId The subnet ID containing the connection
     * @param pos The packed block position of the connection host tile
     * @param side The side ordinal of the storage bus
     * @param action The partition action to perform
     * @param partitionSlot The target slot index (-1 for bulk actions)
     * @param itemStack The item for ADD/TOGGLE actions
     * @return true if the partition was modified
     */
    public static boolean handleSubnetPartitionAction(Map<Long, SubnetTracker> trackerMap,
                                                       long subnetId, long pos, int side,
                                                       PacketSubnetPartitionAction.Action action,
                                                       int partitionSlot, ItemStack itemStack) {
        SubnetTracker tracker = trackerMap.get(subnetId);
        if (tracker == null) return false;

        // SET_ALL_FROM_SUBNET_INVENTORY requires special handling:
        // query the subnet's ME storage and fill the bus config from it
        if (action == PacketSubnetPartitionAction.Action.SET_ALL_FROM_SUBNET_INVENTORY) {
            return handleSetAllFromSubnetInventory(tracker, pos, side);
        }

        Object busPart = findBusForConnection(tracker, pos, side);
        if (busPart == null) return false;

        // Delegate to the appropriate handler based on bus type
        if (busPart instanceof PartStorageBus) {
            return handleItemBusSubnetPartition((PartStorageBus) busPart, action, partitionSlot, itemStack);
        } else if (busPart instanceof PartFluidStorageBus) {
            return StorageBusDataHandler.executeSubnetFluidPartitionAction(
                (PartFluidStorageBus) busPart, action, partitionSlot, itemStack);
        }

        return false;
    }

    private static boolean handleItemBusSubnetPartition(PartStorageBus bus,
                                                        PacketSubnetPartitionAction.Action action,
                                                        int partitionSlot, ItemStack itemStack) {
        IItemHandler configInv = bus.getInventoryByName("config");
        if (configInv == null) return false;

        StorageBusDataHandler.executeSubnetPartitionAction(configInv, configInv.getSlots(), action,
            partitionSlot, itemStack, bus);

        TileEntity hostTile = bus.getHost().getTile();
        if (hostTile != null) hostTile.markDirty();

        return true;
    }

    /**
     * Handle SET_ALL_FROM_SUBNET_INVENTORY: query the subnet's ME storage grid
     * and fill the storage bus config with item types found in the subnet's inventory.
     */
    private static boolean handleSetAllFromSubnetInventory(SubnetTracker tracker, long pos, int side) {
        Object busPart = findBusForConnection(tracker, pos, side);
        if (busPart == null) return false;

        IGrid subnetGrid = tracker.targetGrid;
        IStorageGrid storageGrid = subnetGrid.getCache(IStorageGrid.class);
        if (storageGrid == null) return false;

        if (busPart instanceof PartStorageBus) {
            return fillItemBusFromSubnetInventory((PartStorageBus) busPart, storageGrid);
        } else if (busPart instanceof PartFluidStorageBus) {
            return fillFluidBusFromSubnetInventory((PartFluidStorageBus) busPart, storageGrid);
        }

        return false;
    }

    /**
     * Fill an item storage bus config from the subnet's ME item inventory.
     */
    private static boolean fillItemBusFromSubnetInventory(PartStorageBus bus, IStorageGrid storageGrid) {
        IItemHandler configInv = bus.getInventoryByName("config");
        if (configInv == null) return false;

        int slotsToUse = configInv.getSlots();

        // Clear existing config
        for (int i = 0; i < slotsToUse; i++) {
            ItemHandlerUtil.setStackInSlot(configInv, i, ItemStack.EMPTY);
        }

        // Query the subnet's item inventory
        IItemStorageChannel itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        IMEMonitor<IAEItemStack> itemMonitor = storageGrid.getInventory(itemChannel);
        IItemList<IAEItemStack> items = itemMonitor.getStorageList();

        int slot = 0;
        for (IAEItemStack aeStack : items) {
            if (slot >= slotsToUse) break;
            if (aeStack.getStackSize() <= 0) continue;

            ItemStack configStack = aeStack.createItemStack();
            configStack.setCount(1);
            ItemHandlerUtil.setStackInSlot(configInv, slot++, configStack);
        }

        TileEntity hostTile = bus.getHost().getTile();
        if (hostTile != null) hostTile.markDirty();

        return true;
    }

    /**
     * Fill a fluid storage bus config from the subnet's ME fluid inventory.
     */
    private static boolean fillFluidBusFromSubnetInventory(PartFluidStorageBus bus, IStorageGrid storageGrid) {
        IFluidHandler configInv = bus.getFluidInventoryByName("config");
        if (!(configInv instanceof IAEFluidTank)) return false;

        IAEFluidTank aeConfig = (IAEFluidTank) configInv;
        int slotsToUse = aeConfig.getSlots();

        // Clear existing config
        for (int i = 0; i < slotsToUse; i++) aeConfig.setFluidInSlot(i, null);

        // Query the subnet's fluid inventory
        IFluidStorageChannel fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        IMEMonitor<IAEFluidStack> fluidMonitor = storageGrid.getInventory(fluidChannel);
        IItemList<IAEFluidStack> fluids = fluidMonitor.getStorageList();

        int slot = 0;
        for (IAEFluidStack aeFluid : fluids) {
            if (slot >= slotsToUse) break;
            if (aeFluid.getStackSize() <= 0) continue;

            // Normalize to a standard bucket-volume FluidStack for the config
            FluidStack configFluid = new FluidStack(aeFluid.getFluid(), Fluid.BUCKET_VOLUME);
            IAEFluidStack aeConfigFluid = fluidChannel.createStack(configFluid);
            aeConfig.setFluidInSlot(slot++, aeConfigFluid);
        }

        TileEntity hostTile = bus.getHost().getTile();
        if (hostTile != null) hostTile.markDirty();

        return true;
    }

    /**
     * Find the storage bus (item or fluid) for a connection described by (pos, side).
     * For outbound connections, the storage bus is the connection part itself.
     * For inbound connections, the storage bus is on the subnet side (offset from the interface).
     *
     * @return PartStorageBus or PartFluidStorageBus, or null if not found
     */
    private static Object findBusForConnection(SubnetTracker tracker, long pos, int side) {
        BlockPos targetPos = BlockPos.fromLong(pos);

        for (int i = 0; i < tracker.connectionParts.size(); i++) {
            Object part = tracker.connectionParts.get(i);
            boolean outbound = i < tracker.isOutbound.size() && tracker.isOutbound.get(i);

            if (outbound && part instanceof PartStorageBus) {
                PartStorageBus bus = (PartStorageBus) part;
                TileEntity hostTile = bus.getHost().getTile();

                if (hostTile != null && hostTile.getPos().equals(targetPos)
                    && bus.getSide().ordinal() == side) {
                    return bus;
                }
            } else if (outbound && part instanceof PartFluidStorageBus) {
                PartFluidStorageBus bus = (PartFluidStorageBus) part;
                TileEntity hostTile = bus.getHost().getTile();

                if (hostTile != null && hostTile.getPos().equals(targetPos)
                    && bus.getSide().ordinal() == side) {
                    return bus;
                }
            } else if (!outbound && part instanceof TileInterface) {
                // Inbound: full-block interface on the main network, storage bus is on subnet side
                TileInterface iface = (TileInterface) part;
                EnumFacing connSide = i < tracker.connectionSides.size()
                    ? tracker.connectionSides.get(i) : null;

                // The pos/side sent by the client match the interface tile + connection side
                if (!iface.getPos().equals(targetPos) || connSide == null) continue;
                if (connSide.ordinal() != side) continue;

                // Find the storage bus on the subnet side
                BlockPos remoteTilePos = iface.getPos().offset(connSide);
                TileEntity remoteTile = iface.getWorld().getTileEntity(remoteTilePos);
                if (!(remoteTile instanceof IPartHost)) continue;

                IPartHost partHost = (IPartHost) remoteTile;
                EnumFacing oppositeDir = connSide.getOpposite();

                for (AEPartLocation loc : AEPartLocation.SIDE_LOCATIONS) {
                    if (loc.getFacing() != oppositeDir) continue;

                    IPart remotePart = partHost.getPart(loc);

                    // Return whichever bus type is found on the remote side
                    if (remotePart instanceof PartStorageBus
                        || remotePart instanceof PartFluidStorageBus) {
                        return remotePart;
                    }
                }
            } else if (!outbound && part instanceof PartInterface) {
                // Inbound: cable-attached interface on the main network, storage bus is on subnet side
                PartInterface iface = (PartInterface) part;
                TileEntity ifaceTile = iface.getTileEntity();
                EnumFacing connSide = i < tracker.connectionSides.size()
                    ? tracker.connectionSides.get(i) : null;

                if (ifaceTile == null || !ifaceTile.getPos().equals(targetPos) || connSide == null) continue;
                if (connSide.ordinal() != side) continue;

                // Find the storage bus on the subnet side
                BlockPos remoteTilePos = ifaceTile.getPos().offset(connSide);
                TileEntity remoteTile = ifaceTile.getWorld().getTileEntity(remoteTilePos);
                if (!(remoteTile instanceof IPartHost)) continue;

                IPartHost partHost = (IPartHost) remoteTile;
                EnumFacing oppositeDir = connSide.getOpposite();

                for (AEPartLocation loc : AEPartLocation.SIDE_LOCATIONS) {
                    if (loc.getFacing() != oppositeDir) continue;

                    IPart remotePart = partHost.getPart(loc);

                    if (remotePart instanceof PartStorageBus
                        || remotePart instanceof PartFluidStorageBus) {
                        return remotePart;
                    }
                }
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
