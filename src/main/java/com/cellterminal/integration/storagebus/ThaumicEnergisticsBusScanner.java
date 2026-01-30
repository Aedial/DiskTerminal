package com.cellterminal.integration.storagebus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridHost;
import appeng.api.util.AEPartLocation;

import com.cellterminal.container.handler.StorageBusDataHandler;
import com.cellterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.cellterminal.integration.ThaumicEnergisticsIntegration;

/**
 * Scanner for Thaumic Energistics essentia storage buses.
 * TODO: integrate TC integration more cleanly and avoid reflection where possible.
 */
public class ThaumicEnergisticsBusScanner extends AbstractStorageBusScanner {

    public static final ThaumicEnergisticsBusScanner INSTANCE = new ThaumicEnergisticsBusScanner();

    private ThaumicEnergisticsBusScanner() {}

    @Override
    public String getId() {
        return "thaumicenergistics";
    }

    @Override
    public boolean isAvailable() {
        return ThaumicEnergisticsIntegration.isModLoaded();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void scanStorageBuses(IGrid grid, NBTTagList out, Map<Long, StorageBusTracker> trackerMap) {
        if (!isAvailable() || grid == null) return;

        Class<?> essentiaStorageBusClass = ThaumicEnergisticsIntegration.getEssentiaStorageBusClass();
        if (essentiaStorageBusClass == null) return;

        for (IGridNode gn : grid.getMachines((Class<? extends IGridHost>) essentiaStorageBusClass)) {
            if (!gn.isActive()) continue;
            Object machine = gn.getMachine();
            if (machine == null) continue;

            TileEntity hostTile = getHostTileFromMachine(machine);
            if (hostTile == null) continue;

            int sideOrdinal = getSideOrdinalFromMachine(machine);
            long busId = StorageBusDataHandler.createBusId(hostTile, sideOrdinal, 2);

            NBTTagCompound nbt = ThaumicEnergisticsIntegration.tryCreateEssentiaStorageBusData(machine, busId);
            if (nbt == null) continue;

            applyCapabilities(nbt);
            out.appendTag(nbt);
            trackerMap.put(busId, new StorageBusTracker(busId, machine, hostTile));
        }
    }

    private TileEntity getHostTileFromMachine(Object machine) {
        try {
            Method getTileMethod = machine.getClass().getMethod("getTile");
            Object result = getTileMethod.invoke(machine);
            if (result instanceof TileEntity) return (TileEntity) result;
        } catch (Exception ignored) {}

        return null;
    }

    private int getSideOrdinalFromMachine(Object machine) {
        try {
            Field sideField = machine.getClass().getField("side");
            Object result = sideField.get(machine);
            if (result instanceof AEPartLocation) return ((AEPartLocation) result).ordinal();
        } catch (Exception ignored) {}

        return 0;
    }

    @Override
    public boolean supportsIOMode() {
        return false;
    }
}
