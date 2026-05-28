package com.cellterminal.integration.subnet;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.fml.common.Loader;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;

import com.cells.api.ISubnetProxy;
import com.cellterminal.container.handler.SubnetDataHandler.SubnetTracker;
import com.cellterminal.integration.CellsIntegration;


/**
 * Scanner for CELLS subnet proxies exposed through the public CELLS API.
 */
public class CellsSubnetScanner extends AbstractSubnetScanner {

    public static final CellsSubnetScanner INSTANCE = new CellsSubnetScanner();

    private CellsSubnetScanner() {}

    @Override
    public String getId() {
        return "cells";
    }

    @Override
    public boolean isAvailable() {
        return Loader.isModLoaded("cells");
    }

    @Override
    public void scanSubnets(IGrid grid, NBTTagList out, Map<Long, SubnetTracker> trackerMap, int playerId, int slotLimit) {
        if (grid == null) return;

        Map<IGrid, SubnetTracker> subnetsByGrid = new HashMap<>();

        for (IGridNode node : grid.getNodes()) {
            if (!node.isActive()) continue;

            Object machine = node.getMachine();
            if (!(machine instanceof ISubnetProxy)) continue;

            ISubnetProxy proxy = (ISubnetProxy) machine;
            Object targetGrid = proxy.getTargetGrid();
            if (!(targetGrid instanceof IGrid)) continue;

            IGrid subnetGrid = (IGrid) targetGrid;
            if (subnetGrid == null || subnetGrid == grid) continue;

            TileEntity hostTile = CellsIntegration.getHostTile(machine);
            if (hostTile == null || hostTile.getWorld() == null) continue;

            SubnetTracker tracker = getOrCreateTracker(subnetsByGrid, subnetGrid);
            // CELLS proxies expose the remote grid into the current grid, which is
            // the inverse of DiskTerminal's AE2 outbound label semantics.
            boolean outbound = !proxy.isOutboundConnection();
            if (outbound) {
                tracker.addConnection(proxy, hostTile);
            } else {
                tracker.addInboundConnection(proxy, hostTile, proxy.getPrimaryFacing());
            }
        }

        for (Map.Entry<IGrid, SubnetTracker> entry : subnetsByGrid.entrySet()) {
            IGrid subnetGrid = entry.getKey();
            SubnetTracker tracker = entry.getValue();

            NBTTagCompound subnetNbt = createSubnetNBT(subnetGrid, tracker, playerId, slotLimit);
            out.appendTag(subnetNbt);
            trackerMap.put(tracker.id, tracker);
        }
    }

    private NBTTagCompound createSubnetNBT(IGrid subnetGrid, SubnetTracker tracker, int playerId, int slotLimit) {
        NBTTagCompound nbt = createBaseSubnetNBT(subnetGrid, tracker, playerId);

        nbt.setTag("inventory", CellsIntegration.createPreviewNBT(
            CellsIntegration.collectGridPreviewEntries(subnetGrid, slotLimit), slotLimit, null));

        NBTTagList connectionsList = new NBTTagList();
        for (int index = 0; index < tracker.connectionParts.size(); index++) {
            Object connection = tracker.connectionParts.get(index);
            if (!(connection instanceof ISubnetProxy)) continue;

            TileEntity hostTile = index < tracker.hostTiles.size() ? tracker.hostTiles.get(index) : null;
            boolean outbound = index < tracker.isOutbound.size() && tracker.isOutbound.get(index);
            EnumFacing connectionSide = index < tracker.connectionSides.size() ? tracker.connectionSides.get(index) : null;

            NBTTagCompound connectionNbt = createConnectionNBT(
                (ISubnetProxy) connection,
                hostTile,
                outbound,
                connectionSide,
                slotLimit
            );
            if (connectionNbt != null) connectionsList.appendTag(connectionNbt);
        }

        nbt.setTag("connections", connectionsList);
        return nbt;
    }

    private NBTTagCompound createConnectionNBT(ISubnetProxy proxy,
                                               TileEntity hostTile,
                                               boolean outbound,
                                               EnumFacing connectionSide,
                                               int slotLimit) {
        if (hostTile == null || hostTile.getWorld() == null) return null;

        NBTTagCompound nbt = new NBTTagCompound();
        EnumFacing facing = connectionSide != null ? connectionSide : proxy.getPrimaryFacing();

        nbt.setLong("pos", hostTile.getPos().toLong());
        nbt.setInteger("dim", hostTile.getWorld().provider.getDimension());
        nbt.setInteger("side", facing.ordinal());
        nbt.setBoolean("outbound", outbound);
        nbt.setBoolean("usesSubnetInventory", outbound);
        nbt.setTag("filter", CellsIntegration.createFilterNBT(proxy));
        nbt.setInteger("maxPartitionSlots", Math.max(0, proxy.getFilterSlots()));

        if (!outbound) {
            nbt.setTag("content", CellsIntegration.createPreviewNBT(
                CellsIntegration.collectGridPreviewEntries((IGrid) proxy.getTargetGrid(), slotLimit),
                slotLimit,
                null
            ));
        }

        ItemStack localIcon = CellsIntegration.getHostDisplayStack(proxy);
        if (localIcon.isEmpty()) localIcon = CellsIntegration.getTileDisplayStack(hostTile);

        if (!localIcon.isEmpty()) {
            NBTTagCompound iconNbt = new NBTTagCompound();
            localIcon.writeToNBT(iconNbt);
            nbt.setTag("localIcon", iconNbt);
        }

        ItemStack remoteIcon = proxy.getRemoteDisplayStack();
        if (remoteIcon.isEmpty()) {
            remoteIcon = AEApi.instance().definitions().parts().iface().maybeStack(1).orElse(ItemStack.EMPTY);
        }
        if (remoteIcon.isEmpty()) remoteIcon = AEApi.instance().definitions().blocks().iface().maybeStack(1).orElse(ItemStack.EMPTY);

        if (!remoteIcon.isEmpty()) {
            NBTTagCompound iconNbt = new NBTTagCompound();
            remoteIcon.writeToNBT(iconNbt);
            nbt.setTag("remoteIcon", iconNbt);
        }

        return nbt;
    }
}