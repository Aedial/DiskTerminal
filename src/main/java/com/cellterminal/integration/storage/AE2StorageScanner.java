package com.cellterminal.integration.storage;

import net.minecraft.nbt.NBTTagList;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;

import com.cellterminal.container.handler.CellDataHandler;


/**
 * Default storage scanner for vanilla AE2 storage devices.
 * Scans TileDrive (ME Drive) and TileChest (ME Chest) from the network.
 *
 * AE2 drives support priority and have standard 63-slot partition limits.
 * Storage buses from AE2 support IO mode switching (read/write/both).
 */
public class AE2StorageScanner extends AbstractStorageScanner {

    public static final AE2StorageScanner INSTANCE = new AE2StorageScanner();

    private AE2StorageScanner() {}

    @Override
    public String getId() {
        return "appliedenergistics2";
    }

    @Override
    public boolean isAvailable() {
        return true;  // Always available - AE2 is a required dependency
    }

    @Override
    public void scanStorages(IGrid grid, NBTTagList storageList, CellDataHandler.StorageTrackerCallback callback,
                              int slotLimit) {
        // Scan ME Drives
        for (IGridNode gn : grid.getMachines(TileDrive.class)) {
            if (!gn.isActive()) continue;

            storageList.appendTag(CellDataHandler.createStorageData(
                (TileDrive) gn.getMachine(),
                "tile.appliedenergistics2.drive.name",
                callback,
                slotLimit
            ));
        }

        // Scan ME Chests
        for (IGridNode gn : grid.getMachines(TileChest.class)) {
            if (!gn.isActive()) continue;

            storageList.appendTag(CellDataHandler.createStorageData(
                (TileChest) gn.getMachine(),
                "tile.appliedenergistics2.chest.name",
                callback,
                slotLimit
            ));
        }
    }
}
