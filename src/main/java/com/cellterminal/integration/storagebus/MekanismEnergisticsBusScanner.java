package com.cellterminal.integration.storagebus;

import java.lang.reflect.Method;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridHost;
import appeng.api.util.AEPartLocation;

import com.cellterminal.client.StorageType;
import com.cellterminal.container.handler.StorageBusDataHandler;
import com.cellterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.cellterminal.integration.MekanismEnergisticsIntegration;


/**
 * Scanner for MekanismEnergistics gas storage buses.
 * Uses PartGasStorageBus's getHost().getTile() and getSide() to identify bus location.
 */
public class MekanismEnergisticsBusScanner extends AbstractStorageBusScanner {

    public static final MekanismEnergisticsBusScanner INSTANCE = new MekanismEnergisticsBusScanner();

    private MekanismEnergisticsBusScanner() {}

    @Override
    public String getId() {
        return "mekanismenergistics";
    }

    @Override
    public boolean isAvailable() {
        return MekanismEnergisticsIntegration.isModLoaded();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void scanStorageBuses(IGrid grid, NBTTagList out, Map<Long, StorageBusTracker> trackerMap) {
        if (!isAvailable() || grid == null) return;

        Class<?> gasStorageBusClass = MekanismEnergisticsIntegration.getGasStorageBusClass();
        if (gasStorageBusClass == null) return;

        for (IGridNode gn : grid.getMachines((Class<? extends IGridHost>) gasStorageBusClass)) {
            if (!gn.isActive()) continue;
            Object machine = gn.getMachine();
            if (machine == null) continue;

            TileEntity hostTile = getHostTileFromMachine(machine);
            if (hostTile == null) continue;

            int sideOrdinal = getSideOrdinalFromMachine(machine);
            long busId = StorageBusDataHandler.createBusId(hostTile, sideOrdinal, StorageType.GAS.ordinal());

            NBTTagCompound nbt = MekanismEnergisticsIntegration.tryCreateGasStorageBusData(machine, busId);
            if (nbt == null) continue;

            applyCapabilities(nbt);
            out.appendTag(nbt);
            trackerMap.put(busId, new StorageBusTracker(busId, machine, hostTile));
        }
    }

    private TileEntity getHostTileFromMachine(Object machine) {
        try {
            // PartGasStorageBus extends PartGasUpgradeable which extends PartUpgradeable
            // getHost().getTile() is the standard way to get the host tile entity
            Method getHostMethod = machine.getClass().getMethod("getHost");
            Object host = getHostMethod.invoke(machine);
            if (host == null) return null;

            Method getTileMethod = host.getClass().getMethod("getTile");
            Object result = getTileMethod.invoke(host);
            if (result instanceof TileEntity) return (TileEntity) result;
        } catch (Exception ignored) {}

        return null;
    }

    private int getSideOrdinalFromMachine(Object machine) {
        try {
            // PartGasStorageBus inherits getSide() from AEBasePart which returns AEPartLocation
            Method getSideMethod = machine.getClass().getMethod("getSide");
            Object result = getSideMethod.invoke(machine);
            if (result instanceof AEPartLocation) return ((AEPartLocation) result).ordinal();
        } catch (Exception ignored) {}

        return 0;
    }

    @Override
    public boolean supportsIOMode() {
        // Gas storage buses support IO mode (AccessRestriction setting)
        return true;
    }
}
