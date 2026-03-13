package com.cellterminal.integration.subnet;

import java.util.HashMap;
import java.util.LinkedHashMap;
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
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.IStorageMonitorableAccessor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AEPartLocation;
import appeng.capabilities.Capabilities;
import appeng.fluids.parts.PartFluidStorageBus;
import appeng.parts.misc.PartInterface;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.misc.TileInterface;

import com.cellterminal.container.handler.SubnetDataHandler.SubnetTracker;


/**
 * Scanner for detecting subnets connected via Storage Bus -> Interface pattern.
 * <p>
 * This scanner detects the "ME Passthrough" pattern where:
 * - A Storage Bus on the main network points at an Interface on a subnet
 * - The Storage Bus uses the IStorageMonitorableAccessor capability to access the subnet's storage
 * <p>
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
    public void scanSubnets(IGrid grid, NBTTagList out, Map<Long, SubnetTracker> trackerMap, int playerId, int slotLimit) {
        if (grid == null) return;

        // Temporary map to group connections by target subnet grid
        Map<IGrid, SubnetTracker> subnetsByGrid = new HashMap<>();

        // Scan outbound connections: Storage Bus on main -> Interface on subnet
        scanStorageBuses(grid, subnetsByGrid, playerId);
        scanFluidStorageBuses(grid, subnetsByGrid, playerId);

        // Scan inbound connections: Interface on main <- Storage Bus on subnet
        scanInboundConnections(grid, subnetsByGrid, playerId);

        // Convert to NBT and populate tracker map
        for (Map.Entry<IGrid, SubnetTracker> entry : subnetsByGrid.entrySet()) {
            IGrid subnetGrid = entry.getKey();
            SubnetTracker tracker = entry.getValue();

            NBTTagCompound subnetNbt = createSubnetNBT(subnetGrid, tracker, playerId, slotLimit);
            out.appendTag(subnetNbt);
            trackerMap.put(tracker.id, tracker);
        }
    }

    /**
     * Scan for inbound connections where a subnet's Storage Bus points at our Interface.
     * <p>
     * We scan all TileInterfaces on the main grid, then check adjacent tiles for
     * Storage Buses from other grids.
     */
    private void scanInboundConnections(IGrid mainGrid, Map<IGrid, SubnetTracker> subnetsByGrid, int playerId) {
        // Scan full-block TileInterface
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

        // Scan cable-attached PartInterface (only check the one side the part faces)
        for (IGridNode node : mainGrid.getMachines(PartInterface.class)) {
            if (!node.isActive()) continue;

            PartInterface iface = (PartInterface) node.getMachine();
            TileEntity ifaceTile = iface.getTileEntity();
            if (ifaceTile == null) continue;

            // PartInterface faces one specific direction — only check that side
            EnumFacing facing = iface.getSide().getFacing();
            if (facing == null) continue;

            World world = ifaceTile.getWorld();
            BlockPos adjacentPos = ifaceTile.getPos().offset(facing);
            TileEntity adjacentTile = world.getTileEntity(adjacentPos);
            if (adjacentTile == null) continue;

            IGrid remoteGrid = checkForRemoteStorageBus(adjacentTile, facing.getOpposite(), mainGrid);
            if (remoteGrid == null) continue;

            SubnetTracker tracker = getOrCreateTracker(subnetsByGrid, remoteGrid);
            tracker.addInboundConnection(iface, ifaceTile, facing);
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
    private NBTTagCompound createSubnetNBT(IGrid subnetGrid, SubnetTracker tracker, int playerId, int slotLimit) {
        // Use base class method for common fields
        NBTTagCompound nbt = createBaseSubnetNBT(subnetGrid, tracker, playerId);

        // Subnet inventory contents (queried from the subnet's ME storage)
        NBTTagList inventoryList = collectSubnetInventory(subnetGrid, slotLimit);
        nbt.setTag("inventory", inventoryList);

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
     * Collect item and fluid inventories from a subnet's ME storage grid.
     * This queries the subnet's IStorageGrid for all stored items and fluids,
     * aggregated across all storage devices (drives, chests, etc.) on the subnet.
     *
     * @param subnetGrid The subnet's grid to query
     * @param slotLimit Maximum number of item types to include
     * @return NBTTagList of inventory contents with "Cnt" counts
     */
    private NBTTagList collectSubnetInventory(IGrid subnetGrid, int slotLimit) {
        NBTTagList inventoryList = new NBTTagList();

        IStorageGrid storageGrid;
        try {
            storageGrid = subnetGrid.getCache(IStorageGrid.class);
        } catch (Exception e) {
            return inventoryList;
        }
        if (storageGrid == null) return inventoryList;

        int count = 0;

        // Collect item storage
        try {
            IItemStorageChannel itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            IMEMonitor<IAEItemStack> itemMonitor = storageGrid.getInventory(itemChannel);
            if (itemMonitor != null) {
                IItemList<IAEItemStack> storageList = itemMonitor.getStorageList();
                for (IAEItemStack aeStack : storageList) {
                    if (count >= slotLimit) break;
                    if (aeStack.getStackSize() <= 0) continue;

                    ItemStack stack = aeStack.createItemStack();
                    stack.setCount(1);
                    NBTTagCompound stackNbt = new NBTTagCompound();
                    stack.writeToNBT(stackNbt);
                    stackNbt.setLong("Cnt", aeStack.getStackSize());
                    inventoryList.appendTag(stackNbt);
                    count++;
                }
            }
        } catch (Exception e) {
            // Silently continue - some grids may not have item storage
        }

        // Collect fluid storage
        try {
            IFluidStorageChannel fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            IMEMonitor<IAEFluidStack> fluidMonitor = storageGrid.getInventory(fluidChannel);
            if (fluidMonitor != null) {
                IItemList<IAEFluidStack> fluidList = fluidMonitor.getStorageList();
                for (IAEFluidStack aeFluid : fluidList) {
                    if (count >= slotLimit) break;
                    if (aeFluid.getStackSize() <= 0) continue;

                    ItemStack fluidRep = aeFluid.asItemStackRepresentation();
                    if (fluidRep.isEmpty()) continue;

                    NBTTagCompound stackNbt = new NBTTagCompound();
                    fluidRep.writeToNBT(stackNbt);
                    stackNbt.setLong("Cnt", aeFluid.getStackSize());
                    inventoryList.appendTag(stackNbt);
                    count++;
                }
            }
        } catch (Exception e) {
            // Silently continue - some grids may not have fluid storage
        }

        return inventoryList;
    }

    /**
     * Create NBT for a single connection point.
     */
    private NBTTagCompound createConnectionNBT(Object part, TileEntity hostTile, IGrid subnetGrid, boolean isOutbound, EnumFacing connectionSide) {
        if (hostTile == null) return null;

        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong("pos", hostTile.getPos().toLong());
        nbt.setInteger("dim", hostTile.getWorld().provider.getDimension());
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

        } else if (part instanceof PartInterface) {
            // Inbound connection: PartInterface (cable-attached) on main <- Storage Bus on subnet
            PartInterface iface = (PartInterface) part;
            nbt.setInteger("side", connectionSide != null ? connectionSide.ordinal() : iface.getSide().ordinal());

            // Local icon (interface part)
            ItemStack localIcon = AEApi.instance().definitions().parts().iface().maybeStack(1).orElse(ItemStack.EMPTY);
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
     * Sends ALL slots (including empty) to preserve slot positions for editing.
     * The "filter" key is always sent for storage bus connections so the client shows partition rows.
     */
    private void addStorageBusFilter(NBTTagCompound nbt, PartStorageBus bus) {
        IItemHandler configInv = bus.getInventoryByName("config");
        if (configInv == null) return;

        NBTTagList filterList = new NBTTagList();
        int totalSlots = configInv.getSlots();

        // Send all slots including empties to preserve positions for partition editing
        for (int i = 0; i < totalSlots; i++) {
            ItemStack filterItem = configInv.getStackInSlot(i);
            NBTTagCompound itemNbt = new NBTTagCompound();
            filterItem.writeToNBT(itemNbt);
            filterList.appendTag(itemNbt);
        }

        // Always set the filter key so the client knows partition rows should be shown
        nbt.setTag("filter", filterList);
        nbt.setInteger("maxPartitionSlots", totalSlots);
    }

    /**
     * Add filter from the remote storage bus for inbound connections.
     * This finds the storage bus on the subnet that is pointing at our interface.
     * For inbound connections, the filter key is always sent so partition rows are shown.
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

            // Fluid storage bus: no item partition editing supported yet
            // FIXME: support fluid storage bus
            if (part instanceof PartFluidStorageBus) return;
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
