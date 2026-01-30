package com.cellterminal.integration.storagebus;

import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.util.AEPartLocation;
import appeng.fluids.parts.PartFluidStorageBus;
import appeng.parts.misc.PartStorageBus;

import com.cellterminal.container.handler.StorageBusDataHandler;
import com.cellterminal.container.handler.StorageBusDataHandler.StorageBusTracker;

/**
 * Scanner for vanilla AE2 item and fluid storage buses.
 */
public class AE2StorageBusScanner extends AbstractStorageBusScanner {

    public static final AE2StorageBusScanner INSTANCE = new AE2StorageBusScanner();

    private AE2StorageBusScanner() {}

    @Override
    public String getId() {
        return "appliedenergistics2";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void scanStorageBuses(IGrid grid, NBTTagList out, Map<Long, StorageBusTracker> trackerMap) {
        if (grid == null) return;

        // Item storage buses
        for (IGridNode gn : grid.getMachines(PartStorageBus.class)) {
            if (!gn.isActive()) continue;
            PartStorageBus bus = (PartStorageBus) gn.getMachine();
            TileEntity hostTile = bus.getHost().getTile();
            if (hostTile == null) continue;

            long busId = StorageBusDataHandler.createBusId(hostTile, bus.getSide().ordinal(), 0);
            NBTTagCompound nbt = StorageBusDataHandler.createItemStorageBusData(bus, busId);
            applyCapabilities(nbt);
            applySlotParameters(nbt);
            out.appendTag(nbt);
            trackerMap.put(busId, new StorageBusTracker(busId, bus, hostTile));
        }

        // Fluid storage buses
        for (IGridNode gn : grid.getMachines(PartFluidStorageBus.class)) {
            if (!gn.isActive()) continue;
            PartFluidStorageBus bus = (PartFluidStorageBus) gn.getMachine();
            TileEntity hostTile = bus.getHost().getTile();
            if (hostTile == null) continue;

            long busId = StorageBusDataHandler.createBusId(hostTile, bus.getSide().ordinal(), 1);
            NBTTagCompound nbt = StorageBusDataHandler.createFluidStorageBusData(bus, busId);
            applyCapabilities(nbt);
            applySlotParameters(nbt);
            out.appendTag(nbt);
            trackerMap.put(busId, new StorageBusTracker(busId, bus, hostTile));
        }
    }

    @Override
    public boolean supportsPriority() {
        return true;
    }

    @Override
    public boolean supportsIOMode() {
        return true;
    }
}
