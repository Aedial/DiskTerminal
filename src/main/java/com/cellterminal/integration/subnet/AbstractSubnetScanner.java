package com.cellterminal.integration.subnet;

import java.util.Map;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.helpers.ICustomNameObject;
import appeng.helpers.IInterfaceHost;
import appeng.parts.misc.PartInterface;
import appeng.tile.misc.TileInterface;

import com.cellterminal.container.handler.SubnetDataHandler;
import com.cellterminal.container.handler.SubnetDataHandler.SubnetTracker;


/**
 * Abstract base class for subnet scanners providing common utility methods.
 * 
 * This class provides shared functionality for all subnet scanner implementations:
 * - Grid extraction from tile entities
 * - Primary node detection
 * - Security and power checking
 * - NBT serialization helpers
 * - ItemStack extraction for block representations
 * 
 * Implementations should override {@link #scanSubnets} to provide mod-specific scanning logic.
 */
public abstract class AbstractSubnetScanner implements ISubnetScanner {

    /**
     * Get the ME grid from a tile entity (if it's part of one).
     * Handles both TileInterface blocks and PartInterface parts on cables.
     * 
     * @param tile The tile entity to extract grid from
     * @return The grid, or null if not found
     */
    protected IGrid getGridFromTile(TileEntity tile) {
        if (tile == null) return null;

        // Handle TileInterface
        if (tile instanceof TileInterface) {
            TileInterface iface = (TileInterface) tile;
            IGridNode node = iface.getGridNode(AEPartLocation.INTERNAL);

            if (node != null && node.getGrid() != null) return node.getGrid();
        }

        // Handle part interfaces on cable buses (PartInterface attached to cables)
        if (tile instanceof IPartHost) {
            IPartHost host = (IPartHost) tile;

            // Check all sides for a PartInterface
            for (AEPartLocation loc : AEPartLocation.values()) {
                IPart part = host.getPart(loc);

                if (part instanceof PartInterface) {
                    IGridNode node = part.getGridNode();

                    if (node != null && node.getGrid() != null) return node.getGrid();
                }
            }
        }

        return null;
    }

    /**
     * Find the "primary" node of a grid for consistent ID generation.
     * Uses the first interface node found, or any node if no interfaces.
     * 
     * @param grid The grid to find primary node for
     * @return The primary node, or null if grid is empty
     */
    protected IGridNode findPrimaryNode(IGrid grid) {
        if (grid == null) return null;

        // Prefer interface nodes for consistent identification
        for (IGridNode node : grid.getMachines(TileInterface.class)) return node;

        // Fall back to any node
        for (IGridNode node : grid.getNodes()) return node;

        return null;
    }

    /**
     * Check if the subnet has a security station.
     * 
     * @param grid The subnet grid to check
     * @return true if a security station is active on the grid
     */
    protected boolean checkHasSecurity(IGrid grid) {
        try {
            ISecurityGrid securityGrid = grid.getCache(ISecurityGrid.class);
            return securityGrid != null && securityGrid.isAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if the player can access the subnet (has security permissions).
     * Checks if the player has BUILD permission on the subnet's security grid.
     * 
     * @param grid The subnet grid to check
     * @param playerId The player ID to check permissions for
     * @return true if accessible (no security or has permission)
     */
    protected boolean checkIsAccessible(IGrid grid, int playerId) {
        try {
            ISecurityGrid securityGrid = grid.getCache(ISecurityGrid.class);

            // No security = accessible to everyone
            if (securityGrid == null || !securityGrid.isAvailable()) return true;

            // Check BUILD permission - required to interact with network devices
            return securityGrid.hasPermission(playerId, SecurityPermissions.BUILD);
        } catch (Exception e) {
            // If we can't check permissions, assume accessible
            return true;
        }
    }

    /**
     * Check if the subnet has power.
     * 
     * @param grid The subnet grid to check
     * @return true if the grid has power
     */
    protected boolean checkHasPower(IGrid grid) {
        try {
            IEnergyGrid energyGrid = grid.getCache(IEnergyGrid.class);
            return energyGrid != null && energyGrid.isNetworkPowered();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get or create a SubnetTracker for the given grid in the map.
     * 
     * @param subnetsByGrid The map of grids to trackers
     * @param grid The grid to get/create tracker for
     * @return The tracker for this grid
     */
    protected SubnetTracker getOrCreateTracker(Map<IGrid, SubnetTracker> subnetsByGrid, IGrid grid) {
        return subnetsByGrid.computeIfAbsent(grid, g -> {
            IGridNode primaryNode = findPrimaryNode(g);
            long id = SubnetDataHandler.createSubnetId(g, primaryNode);
            return new SubnetTracker(id, g);
        });
    }

    /**
     * Get an ItemStack representation of a block at the given position.
     * Uses the block's pick block method for accurate representation.
     * 
     * @param world The world
     * @param pos The block position
     * @return ItemStack representing the block, or EMPTY if not possible
     */
    protected ItemStack getBlockItemStack(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        try {
            return state.getBlock().getPickBlock(
                state,
                new RayTraceResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), EnumFacing.UP, pos),
                world,
                pos,
                null
            );
        } catch (Exception e) {
            return new ItemStack(state.getBlock(), 1, state.getBlock().getMetaFromState(state));
        }
    }

    /**
     * Get the position and dimension from a grid node.
     * 
     * @param node The grid node
     * @return An array of [BlockPos, dimension] or [ORIGIN, 0] if not found
     */
    protected Object[] getNodeLocation(IGridNode node) {
        BlockPos pos = BlockPos.ORIGIN;
        int dimension = 0;

        if (node != null && node.getGridBlock() != null) {
            DimensionalCoord loc = node.getGridBlock().getLocation();
            if (loc != null) {
                pos = loc.getPos();
                dimension = loc.getWorld().provider.getDimension();
            }
        }

        return new Object[] { pos, dimension };
    }

    /**
     * Create base NBT data for a subnet (common fields).
     * Subclasses can extend this with connection-specific data.
     * 
     * @param subnetGrid The subnet's grid
     * @param tracker The subnet tracker
     * @param playerId The player ID for permission checks
     * @return NBT compound with common subnet data
     */
    protected NBTTagCompound createBaseSubnetNBT(IGrid subnetGrid, SubnetTracker tracker, int playerId) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong("id", tracker.id);

        // Find primary interface host for this subnet (supports both TileInterface and PartInterface)
        IInterfaceHost interfaceHost = SubnetDataHandler.findPrimaryInterfaceHost(subnetGrid);
        IGridNode primaryNode = findPrimaryNode(subnetGrid);
        Object[] location = getNodeLocation(primaryNode);
        BlockPos primaryPos = (BlockPos) location[0];
        int dimension = (Integer) location[1];

        nbt.setLong("primaryPos", primaryPos.toLong());
        nbt.setInteger("dim", dimension);

        // Default name uses localization key - actual formatting done client-side
        nbt.setInteger("posX", primaryPos.getX());
        nbt.setInteger("posY", primaryPos.getY());
        nbt.setInteger("posZ", primaryPos.getZ());

        // Read custom name from primary interface (AE2's ICustomNameObject)
        if (interfaceHost instanceof ICustomNameObject) {
            ICustomNameObject nameable = (ICustomNameObject) interfaceHost;
            if (nameable.hasCustomInventoryName()) {
                String customName = nameable.getCustomInventoryName();
                if (customName != null && !customName.isEmpty()) nbt.setString("customName", customName);
            }
        }

        // Read favorite state from interface host's tile data (Forge's getTileData)
        if (interfaceHost != null) {
            TileEntity tile = interfaceHost.getTileEntity();
            if (tile != null) {
                NBTTagCompound tileData = tile.getTileData();
                // Use side-specific key for PartInterface (multiple interfaces on same cable)
                String favoriteKey = (interfaceHost instanceof PartInterface)
                    ? "cellterminal.favorite." + ((PartInterface) interfaceHost).getSide().ordinal()
                    : "cellterminal.favorite";

                if (tileData.getBoolean(favoriteKey)) nbt.setBoolean("favorite", true);
            }
        }

        // Security and access
        boolean hasSecurity = checkHasSecurity(subnetGrid);
        boolean isAccessible = checkIsAccessible(subnetGrid, playerId);
        boolean hasPower = checkHasPower(subnetGrid);

        nbt.setBoolean("hasSecurity", hasSecurity);
        nbt.setBoolean("accessible", isAccessible);
        nbt.setBoolean("hasPower", hasPower);

        return nbt;
    }
}
