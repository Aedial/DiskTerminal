package com.cellterminal.integration;

import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;

import com.cellterminal.CellTerminal;
import com.cellterminal.container.handler.CellDataHandler;
import com.cellterminal.integration.storage.IStorageScanner;


/**
 * Integration handler for CrazyAE2 mod.
 * Provides support for Improved Drive (35-slot drive) in the Cell Terminal.
 *
 * CrazyAE's TileImprovedDrive implements IChestOrDrive, so it works seamlessly
 * with the existing CellDataHandler infrastructure.
 */
public class CrazyAEIntegration {

    private static final String MODID = "crazyae";
    private static Boolean modLoaded = null;

    /**
     * Check if CrazyAE is loaded.
     */
    public static boolean isModLoaded() {
        if (modLoaded == null) modLoaded = Loader.isModLoaded(MODID);

        return modLoaded;
    }

    /**
     * Create the storage scanner for CrazyAE if available.
     *
     * @return scanner instance or null if mod not loaded
     */
    public static IStorageScanner createScanner() {
        if (!isModLoaded()) return null;

        return createScannerInternal();
    }

    @Optional.Method(modid = MODID)
    private static IStorageScanner createScannerInternal() {
        return new CrazyAEStorageScanner();
    }

    /**
     * Storage scanner for CrazyAE's Improved Drive.
     * Must be inner class to use @Optional.Method properly.
     */
    @Optional.InterfaceList({
        @Optional.Interface(iface = "com.cellterminal.integration.storage.IStorageScanner", modid = MODID)
    })
    private static class CrazyAEStorageScanner implements IStorageScanner {

        @Override
        public String getId() {
            return "crazyae";
        }

        @Override
        public boolean isAvailable() {
            return CrazyAEIntegration.isModLoaded();
        }

        @Override
        @Optional.Method(modid = MODID)
        public void scanStorages(IGrid grid, NBTTagList storageList, CellDataHandler.StorageTrackerCallback callback) {
            try {
                scanImprovedDrives(grid, storageList, callback);
            } catch (Exception e) {
                CellTerminal.LOGGER.error("Error scanning CrazyAE improved drives: {}", e.getMessage());
            }
        }

        @Optional.Method(modid = MODID)
        private void scanImprovedDrives(IGrid grid, NBTTagList storageList, CellDataHandler.StorageTrackerCallback callback) {
            // TileImprovedDrive implements IChestOrDrive, so we can use createStorageData directly
            for (IGridNode gn : grid.getMachines(dev.beecube31.crazyae2.common.tile.storage.TileImprovedDrive.class)) {
                if (!gn.isActive()) continue;

                dev.beecube31.crazyae2.common.tile.storage.TileImprovedDrive drive =
                    (dev.beecube31.crazyae2.common.tile.storage.TileImprovedDrive) gn.getMachine();

                storageList.appendTag(CellDataHandler.createStorageData(
                    drive,
                    "tile.crazyae.improved_drive.name",
                    callback
                ));
            }
        }
    }
}
