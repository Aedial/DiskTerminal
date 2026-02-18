package com.cellterminal.integration.subnet;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.IStorageMonitorableAccessor;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.capabilities.Capabilities;
import appeng.fluids.parts.PartFluidStorageBus;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.misc.TileInterface;

import com.cellterminal.container.handler.SubnetDataHandler;
import com.cellterminal.container.handler.SubnetDataHandler.SubnetTracker;


/**
 * Scanner for detecting subnets connected via Storage Bus -> Interface pattern.
 * 
 * This scanner detects the "ME Passthrough" pattern where:
 * - A Storage Bus on the main network points at an Interface on a subnet
 * - The Storage Bus uses the IStorageMonitorableAccessor capability to access the subnet's storage
 * 
 * The key insight is that when a Storage Bus connects to an Interface that has
 * STORAGE_MONITORABLE_ACCESSOR capability, it gains access to a DIFFERENT grid's storage.
 */
public class AE2SubnetScanner extends AbstractSubnetScanner {

    public static final AE2SubnetScanner INSTANCE = new AE2SubnetScanner();

    private AE2SubnetScanner() {}

    @Override
    public String getId() {
        return "appliedenergistics2";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void scanSubnets(IGrid grid, NBTTagList out, Map<Long, SubnetTracker> trackerMap, int playerId) {
        if (grid == null) return;

        // Temporary map to group connections by target subnet grid
        Map<IGrid, SubnetTracker> subnetsByGrid = new HashMap<>();

        // Scan outbound connections: Storage Bus on main -> Interface on subnet
        scanStorageBuses(grid, subnetsByGrid, playerId);
        scanFluidStorageBuses(grid, subnetsByGrid, playerId);

        // Scan inbound connections: Interface on main <- Storage Bus on subnet
        // This is the MORE COMMON pattern where subnets pull from main network
        scanInboundConnections(grid, subnetsByGrid, playerId);

        // Convert to NBT and populate tracker map
        for (Map.Entry<IGrid, SubnetTracker> entry : subnetsByGrid.entrySet()) {
            IGrid subnetGrid = entry.getKey();
            SubnetTracker tracker = entry.getValue();

            NBTTagCompound subnetNbt = createSubnetNBT(subnetGrid, tracker, playerId);
            out.appendTag(subnetNbt);
            trackerMap.put(tracker.id, tracker);
        }
    }

    /**
     * Scan for inbound connections where a subnet's Storage Bus points at our Interface.
     * This is the common "subnet pulling from main" pattern.
     * 
     * We scan all TileInterfaces on the main grid, then check adjacent tiles for
     * Storage Buses from other grids.
     */
    private void scanInboundConnections(IGrid mainGrid, Map<IGrid, SubnetTracker> subnetsByGrid, int playerId) {
        // Scan TileInterface blocks
        for (IGridNode node : mainGrid.getMachines(TileInterface.class)) {
            if (!node.isActive()) continue;

            TileInterface iface = (TileInterface) node.getMachine();
            TileEntity ifaceTile = iface.getTile();
            if (ifaceTile == null) continue;

            World world = ifaceTile.getWorld();
            BlockPos ifacePos = ifaceTile.getPos();

            // Check all adjacent tiles for Storage Buses from other grids
            for (EnumFacing facing : EnumFacing.values()) {
                BlockPos adjacentPos = ifacePos.offset(facing);
                TileEntity adjacentTile = world.getTileEntity(adjacentPos);
                if (adjacentTile == null) continue;

                // Check if adjacent tile is a cable bus with parts
                IGrid remoteGrid = checkForRemoteStorageBus(adjacentTile, facing.getOpposite(), mainGrid);
                if (remoteGrid == null) continue;

                // Found an inbound connection - subnet Storage Bus -> our Interface
                SubnetTracker tracker = getOrCreateTracker(subnetsByGrid, remoteGrid);

                // Add this connection as inbound (isOutbound = false)
                tracker.addInboundConnection(iface, ifaceTile, facing);
            }
        }
    }

    /**
     * Check if a tile has a Storage Bus on the given side that belongs to a different grid.
     * Returns the remote grid if found, null otherwise.
     */
    private IGrid checkForRemoteStorageBus(TileEntity tile, EnumFacing side, IGrid mainGrid) {
        // Check if tile is a cable bus host
        if (tile instanceof IPartHost) {
            IPartHost host = (IPartHost) tile;
            IPart part = host.getPart(AEPartLocation.fromFacing(side));

            if (part instanceof PartStorageBus) {
                PartStorageBus bus = (PartStorageBus) part;
                IGridNode busNode = bus.getGridNode();
                if (busNode != null && busNode.getGrid() != null && busNode.getGrid() != mainGrid) {
                    return busNode.getGrid();
                }
            } else if (part instanceof PartFluidStorageBus) {
                PartFluidStorageBus bus = (PartFluidStorageBus) part;
                IGridNode busNode = bus.getGridNode();
                if (busNode != null && busNode.getGrid() != null && busNode.getGrid() != mainGrid) {
                    return busNode.getGrid();
                }
            }
        }

        return null;
    }

    /**
     * Scan item storage buses for connections to subnets.
     */
    private void scanStorageBuses(IGrid mainGrid, Map<IGrid, SubnetTracker> subnetsByGrid, int playerId) {
        for (IGridNode node : mainGrid.getMachines(PartStorageBus.class)) {
            if (!node.isActive()) continue;

            PartStorageBus bus = (PartStorageBus) node.getMachine();
            TileEntity hostTile = bus.getHost().getTile();
            if (hostTile == null) continue;

            EnumFacing facing = bus.getSide().getFacing();
            BlockPos targetPos = hostTile.getPos().offset(facing);
            World world = hostTile.getWorld();
            TileEntity targetTile = world.getTileEntity(targetPos);
            if (targetTile == null) continue;

            // Check if target has STORAGE_MONITORABLE_ACCESSOR capability (indicates subnet connection)
            EnumFacing targetSide = facing.getOpposite();
            if (!targetTile.hasCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, targetSide)) continue;

            IStorageMonitorableAccessor accessor = targetTile.getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, targetSide);
            if (accessor == null) continue;

            // Get the remote grid from the target tile
            IGrid remoteGrid = getGridFromTile(targetTile);
            if (remoteGrid == null || remoteGrid == mainGrid) continue;

            // Create or get existing tracker for this subnet
            SubnetTracker tracker = getOrCreateTracker(subnetsByGrid, remoteGrid);

            // Add this connection point (outbound: Storage Bus on main -> Interface on subnet)
            tracker.addConnection(bus, hostTile);
        }
    }

    /**
     * Scan fluid storage buses for connections to subnets.
     */
    private void scanFluidStorageBuses(IGrid mainGrid, Map<IGrid, SubnetTracker> subnetsByGrid, int playerId) {
        for (IGridNode node : mainGrid.getMachines(PartFluidStorageBus.class)) {
            if (!node.isActive()) continue;

            PartFluidStorageBus bus = (PartFluidStorageBus) node.getMachine();
            TileEntity hostTile = bus.getHost().getTile();
            if (hostTile == null) continue;

            EnumFacing facing = bus.getSide().getFacing();
            BlockPos targetPos = hostTile.getPos().offset(facing);
            World world = hostTile.getWorld();
            TileEntity targetTile = world.getTileEntity(targetPos);
            if (targetTile == null) continue;

            // Check if target has STORAGE_MONITORABLE_ACCESSOR capability
            EnumFacing targetSide = facing.getOpposite();
            if (!targetTile.hasCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, targetSide)) continue;

            IStorageMonitorableAccessor accessor = targetTile.getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, targetSide);
            if (accessor == null) continue;

            // Get the remote grid from the target tile
            IGrid remoteGrid = getGridFromTile(targetTile);
            if (remoteGrid == null || remoteGrid == mainGrid) continue;

            // Create or get existing tracker for this subnet
            SubnetTracker tracker = getOrCreateTracker(subnetsByGrid, remoteGrid);

            // Add this connection point
            tracker.addConnection(bus, hostTile);
        }
    }

    /**
     * Create NBT data for a subnet.
     */
    private NBTTagCompound createSubnetNBT(IGrid subnetGrid, SubnetTracker tracker, int playerId) {
        // Use base class method for common fields
        NBTTagCompound nbt = createBaseSubnetNBT(subnetGrid, tracker, playerId);

        // Connection points
        NBTTagList connectionsList = new NBTTagList();
        for (int i = 0; i < tracker.connectionParts.size(); i++) {
            Object part = tracker.connectionParts.get(i);
            TileEntity hostTile = i < tracker.hostTiles.size() ? tracker.hostTiles.get(i) : null;
            boolean isOutbound = i < tracker.isOutbound.size() ? tracker.isOutbound.get(i) : true;
            EnumFacing side = i < tracker.connectionSides.size() ? tracker.connectionSides.get(i) : null;

            NBTTagCompound connNbt = createConnectionNBT(part, hostTile, subnetGrid, isOutbound, side);
            if (connNbt != null) connectionsList.appendTag(connNbt);
        }
        nbt.setTag("connections", connectionsList);

        return nbt;
    }

    /**
     * Create NBT for a single connection point.
     */
    private NBTTagCompound createConnectionNBT(Object part, TileEntity hostTile, IGrid subnetGrid, boolean isOutbound, EnumFacing connectionSide) {
        if (hostTile == null) return null;

        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong("pos", hostTile.getPos().toLong());
        nbt.setBoolean("outbound", isOutbound);

        if (part instanceof PartStorageBus) {
            PartStorageBus bus = (PartStorageBus) part;
            nbt.setInteger("side", bus.getSide().ordinal());

            // Local icon (storage bus)
            ItemStack localIcon = getPartItemStack(bus);
            if (!localIcon.isEmpty()) {
                NBTTagCompound iconNbt = new NBTTagCompound();
                localIcon.writeToNBT(iconNbt);
                nbt.setTag("localIcon", iconNbt);
            }

            // Remote icon (the interface it's pointing at)
            EnumFacing facing = bus.getSide().getFacing();
            BlockPos targetPos = hostTile.getPos().offset(facing);
            ItemStack remoteIcon = getBlockItemStack(hostTile.getWorld(), targetPos);
            if (!remoteIcon.isEmpty()) {
                NBTTagCompound iconNbt = new NBTTagCompound();
                remoteIcon.writeToNBT(iconNbt);
                nbt.setTag("remoteIcon", iconNbt);
            }

            // Filter from storage bus config
            addStorageBusFilter(nbt, bus);

        } else if (part instanceof PartFluidStorageBus) {
            PartFluidStorageBus bus = (PartFluidStorageBus) part;
            nbt.setInteger("side", bus.getSide().ordinal());

            // Local icon (fluid storage bus)
            ItemStack localIcon = getPartItemStack(bus);
            if (!localIcon.isEmpty()) {
                NBTTagCompound iconNbt = new NBTTagCompound();
                localIcon.writeToNBT(iconNbt);
                nbt.setTag("localIcon", iconNbt);
            }

            // Remote icon
            EnumFacing facing = bus.getSide().getFacing();
            BlockPos targetPos = hostTile.getPos().offset(facing);
            ItemStack remoteIcon = getBlockItemStack(hostTile.getWorld(), targetPos);
            if (!remoteIcon.isEmpty()) {
                NBTTagCompound iconNbt = new NBTTagCompound();
                remoteIcon.writeToNBT(iconNbt);
                nbt.setTag("remoteIcon", iconNbt);
            }

        } else if (part instanceof TileInterface) {
            // Inbound connection: Interface on main <- Storage Bus on subnet
            TileInterface iface = (TileInterface) part;
            nbt.setInteger("side", connectionSide != null ? connectionSide.ordinal() : 0);

            // Local icon (interface)
            ItemStack localIcon = AEApi.instance().definitions().blocks().iface().maybeStack(1).orElse(ItemStack.EMPTY);
            if (!localIcon.isEmpty()) {
                NBTTagCompound iconNbt = new NBTTagCompound();
                localIcon.writeToNBT(iconNbt);
                nbt.setTag("localIcon", iconNbt);
            }

            // Remote icon (the storage bus on subnet side)
            if (connectionSide != null) {
                BlockPos targetPos = hostTile.getPos().offset(connectionSide);
                ItemStack remoteIcon = getBlockItemStack(hostTile.getWorld(), targetPos);
                if (!remoteIcon.isEmpty()) {
                    NBTTagCompound iconNbt = new NBTTagCompound();
                    remoteIcon.writeToNBT(iconNbt);
                    nbt.setTag("remoteIcon", iconNbt);
                }
            }

            // For inbound, filter comes from the remote storage bus
            addInboundFilter(nbt, hostTile, connectionSide, subnetGrid);
        }

        return nbt;
    }

    /**
     * Add storage bus filter configuration to connection NBT.
     */
    private void addStorageBusFilter(NBTTagCompound nbt, PartStorageBus bus) {
        IItemHandler configInv = bus.getInventoryByName("config");
        if (configInv == null) return;

        NBTTagList filterList = new NBTTagList();
        int slotsToUse = 9;  // Only 9 slots for now since we don't want to overload the UI with too many filter items
        // TODO: increase?

        for (int i = 0; i < configInv.getSlots() && i < slotsToUse; i++) {
            ItemStack filterItem = configInv.getStackInSlot(i);
            if (filterItem.isEmpty()) continue;

            NBTTagCompound itemNbt = new NBTTagCompound();
            filterItem.writeToNBT(itemNbt);
            filterList.appendTag(itemNbt);
        }

        if (filterList.tagCount() > 0) nbt.setTag("filter", filterList);
    }

    /**
     * Add filter from the remote storage bus for inbound connections.
     * This finds the storage bus on the subnet that is pointing at our interface.
     */
    private void addInboundFilter(NBTTagCompound nbt, TileEntity hostTile, EnumFacing connectionSide, IGrid subnetGrid) {
        if (connectionSide == null || hostTile == null) return;

        // Get the tile on the subnet side
        BlockPos targetPos = hostTile.getPos().offset(connectionSide);
        TileEntity targetTile = hostTile.getWorld().getTileEntity(targetPos);
        if (targetTile == null) return;

        // Check if it's a part host with a storage bus pointing at us
        if (!(targetTile instanceof IPartHost)) return;

        IPartHost partHost = (IPartHost) targetTile;
        EnumFacing oppositeDir = connectionSide.getOpposite();

        for (AEPartLocation loc : AEPartLocation.SIDE_LOCATIONS) {
            if (loc.getFacing() != oppositeDir) continue;

            IPart part = partHost.getPart(loc);
            if (part instanceof PartStorageBus) {
                addStorageBusFilter(nbt, (PartStorageBus) part);
                return;
            }
        }
    }

    /**
     * Get an ItemStack representation of a part.
     */
    private ItemStack getPartItemStack(Object part) {
        if (part instanceof PartStorageBus) {
            return AEApi.instance().definitions().parts().storageBus().maybeStack(1).orElse(ItemStack.EMPTY);
        } else if (part instanceof PartFluidStorageBus) {
            return AEApi.instance().definitions().parts().fluidStorageBus().maybeStack(1).orElse(ItemStack.EMPTY);
        }

        return ItemStack.EMPTY;
    }

}
