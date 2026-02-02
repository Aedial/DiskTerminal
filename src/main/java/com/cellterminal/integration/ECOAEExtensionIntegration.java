package com.cellterminal.integration;

import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.items.IItemHandler;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.util.AEPartLocation;
import appeng.api.util.AECableType;
import appeng.tile.inventory.AppEngCellInventory;

import com.cellterminal.CellTerminal;
import com.cellterminal.container.handler.CellDataHandler;
import com.cellterminal.integration.storage.AbstractStorageScanner;
import com.cellterminal.integration.storage.IStorageScanner;


/**
 * Integration handler for ECOAEExtension mod (NovaEngineering).
 * Provides support for E-Storage Cell Drives in the Cell Terminal.
 *
 * E-Storage uses a multiblock structure where:
 * - EStorageMEChannel is the network-connected part
 * - EStorageController manages the multiblock
 * - EStorageCellDrive holds a single cell per drive
 *
 * Each EStorageCellDrive is presented as a single-slot storage.
 * TODO: Move to storage/
 */
public class ECOAEExtensionIntegration {

    private static final String MODID = "ecoaeextension";
    private static Boolean modLoaded = null;

    /**
     * Check if ECOAEExtension is loaded.
     */
    public static boolean isModLoaded() {
        if (modLoaded == null) modLoaded = Loader.isModLoaded(MODID);

        return modLoaded;
    }

    /**
     * Create the storage scanner for ECOAEExtension if available.
     *
     * @return scanner instance or null if mod not loaded
     */
    public static IStorageScanner createScanner() {
        if (!isModLoaded()) return null;

        return createScannerInternal();
    }

    @Optional.Method(modid = MODID)
    private static IStorageScanner createScannerInternal() {
        return new ECOAEStorageScanner();
    }

    /**
     * Storage scanner for ECOAEExtension's E-Storage Cell Drives.
     *
     * E-Storage drives have only 1 cell slot per drive (each drive holds one cell).
     */
    @Optional.InterfaceList({
        @Optional.Interface(iface = "com.cellterminal.integration.storage.IStorageScanner", modid = MODID)
    })
    private static class ECOAEStorageScanner extends AbstractStorageScanner {

        @Override
        public String getId() {
            return "ecoaeextension";
        }

        @Override
        public boolean isAvailable() {
            return ECOAEExtensionIntegration.isModLoaded();
        }

        @Override
        @Optional.Method(modid = MODID)
        public void scanStorages(IGrid grid, NBTTagList storageList, CellDataHandler.StorageTrackerCallback callback,
                                  int slotLimit) {
            try {
                scanEStorageChannels(grid, storageList, callback, slotLimit);
            } catch (Exception e) {
                CellTerminal.LOGGER.error("Error scanning ECOAEExtension E-Storage: {}", e.getMessage());
            }
        }

        @Optional.Method(modid = MODID)
        private void scanEStorageChannels(IGrid grid, NBTTagList storageList, CellDataHandler.StorageTrackerCallback callback,
                                           int slotLimit) {
            // EStorageMEChannel is the network-connected part
            for (IGridNode gn : grid.getMachines(github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageMEChannel.class)) {
                if (!gn.isActive()) continue;

                github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageMEChannel channel =
                    (github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageMEChannel) gn.getMachine();

                github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageController controller = channel.getController();
                if (controller == null) continue;

                int channelPriority = channel.getPriority();

                // Each drive in the multiblock is presented as a separate storage entry
                for (github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive drive : controller.getCellDrives()) {
                    NBTTagCompound driveData = createEStorageDriveData(drive, channelPriority, callback, slotLimit);
                    if (driveData != null) {
                        applyCapabilities(driveData);
                        storageList.appendTag(driveData);
                    }
                }
            }
        }

        @Optional.Method(modid = MODID)
        private NBTTagCompound createEStorageDriveData(Object driveObj, int channelPriority,
                                                        CellDataHandler.StorageTrackerCallback callback, int slotLimit) {
            github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive drive =
                (github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive) driveObj;

            TileEntity te = drive;
            if (te.getWorld() == null) return null;

            // Generate unique ID for this drive
            long id = te.getPos().toLong() ^ ((long) te.getWorld().provider.getDimension() << 48);

            // Register with callback for server-side tracking
            // Note: We use EStorageDriveWrapper to handle IChestOrDrive interface
            if (callback != null) callback.register(id, te, new EStorageDriveWrapper(drive));

            NBTTagCompound storageData = new NBTTagCompound();
            storageData.setLong("id", id);
            storageData.setLong("pos", te.getPos().toLong());
            storageData.setInteger("dim", te.getWorld().provider.getDimension());

            // Use localized name or default
            storageData.setString("name", "tile.ecoaeextension.estorage_cell_drive.name");

            // Priority from the channel
            storageData.setInteger("priority", channelPriority);

            // Get block item for display
            ItemStack blockItem = getBlockItem(te);
            if (!blockItem.isEmpty()) {
                NBTTagCompound blockNbt = new NBTTagCompound();
                blockItem.writeToNBT(blockNbt);
                storageData.setTag("blockItem", blockNbt);
            }

            // E-Storage drives have 1 slot
            storageData.setInteger("slotCount", 1);

            // Get cell data
            NBTTagList cellList = new NBTTagList();
            AppEngCellInventory driveInv = drive.getDriveInv();
            ItemStack cellStack = driveInv.getStackInSlot(0);

            if (!cellStack.isEmpty()) {
                int status = getCellStatusInternal(drive);
                NBTTagCompound cellData = CellDataHandler.createCellData(0, cellStack, status, slotLimit);
                cellList.appendTag(cellData);
            }

            storageData.setTag("cells", cellList);

            return storageData;
        }

        @Optional.Method(modid = MODID)
        private int getCellStatusInternal(Object driveObj) {
            github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive drive =
                (github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive) driveObj;

            github.kasuminova.ecoaeextension.common.estorage.ECellDriveWatcher<?> watcher = drive.getWatcher();
            if (watcher == null) return 0;

            // Get cell inventory handler status
            Object internal = watcher.getInternal();
            if (internal instanceof ICellInventoryHandler<?>) {
                ICellInventoryHandler<?> handler = (ICellInventoryHandler<?>) internal;
                ICellInventory<?> cellInv = handler.getCellInv();
                if (cellInv == null) return 1;  // Green - partial

                // Check capacity status
                if (cellInv.getStoredItemCount() == 0) return 4;  // Blue - empty
                if (cellInv.getFreeBytes() <= 0) return 3;  // Red - full bytes
                if (cellInv.getStoredItemTypes() >= cellInv.getTotalItemTypes()) return 2;  // Orange - full types

                return 1;  // Green - partial
            }

            return 1;  // Default to green
        }

        private ItemStack getBlockItem(TileEntity te) {
            if (te.getWorld() == null) return ItemStack.EMPTY;

            try {
                return te.getWorld().getBlockState(te.getPos()).getBlock()
                    .getPickBlock(te.getWorld().getBlockState(te.getPos()), null, te.getWorld(), te.getPos(), null);
            } catch (Exception e) {
                return ItemStack.EMPTY;
            }
        }
    }

    /**
     * Wrapper class to make EStorageCellDrive work with IChestOrDrive interface.
     * This allows the existing CellActionHandler to work with E-Storage drives.
     */
    @Optional.InterfaceList({
        @Optional.Interface(iface = "appeng.api.implementations.tiles.IChestOrDrive", modid = MODID)
    })
    public static class EStorageDriveWrapper implements IChestOrDrive {

        private final Object drive;  // EStorageCellDrive

        public EStorageDriveWrapper(Object drive) {
            this.drive = drive;
        }

        public Object getWrappedDrive() {
            return drive;
        }

        @Override
        public int getCellCount() {
            return 1;
        }

        @Override
        @Optional.Method(modid = MODID)
        public int getCellStatus(int slot) {
            if (slot != 0) return 0;

            github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive eDrive =
                (github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive) drive;

            github.kasuminova.ecoaeextension.common.estorage.ECellDriveWatcher<?> watcher = eDrive.getWatcher();
            if (watcher == null) return 0;

            Object internal = watcher.getInternal();
            if (internal instanceof ICellInventoryHandler<?>) {
                ICellInventoryHandler<?> handler = (ICellInventoryHandler<?>) internal;
                ICellInventory<?> cellInv = handler.getCellInv();
                if (cellInv == null) return 1;

                if (cellInv.getStoredItemCount() == 0) return 4;
                if (cellInv.getFreeBytes() <= 0) return 3;
                if (cellInv.getStoredItemTypes() >= cellInv.getTotalItemTypes()) return 2;

                return 1;
            }

            return 1;
        }

        @Override
        @Optional.Method(modid = MODID)
        public boolean isPowered() {
            github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive eDrive =
                (github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive) drive;

            github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageController controller = eDrive.getController();
            if (controller == null) return false;

            github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageMEChannel channel = controller.getChannel();

            return channel != null && channel.getProxy().isActive();
        }

        @Override
        public boolean isCellBlinking(int slot) {
            return false;
        }

        @Override
    	public IGridNode getActionableNode() {
            github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive eDrive =
                (github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive) drive;

            github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageController controller = eDrive.getController();
            if (controller == null) return null;

            github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageMEChannel channel = controller.getChannel();

            if (channel != null) channel.getActionableNode();

            return null;
        }

        @Override
        public int getPriority() {
            github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive eDrive =
                (github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive) drive;

            github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageController controller = eDrive.getController();
            if (controller == null) return 0;

            github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageMEChannel channel = controller.getChannel();

            return channel != null ? channel.getPriority() : 0;
        }

        @Override
        @Optional.Method(modid = MODID)
        public void saveChanges(ICellInventory<?> cellInventory) {
            github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive eDrive =
                (github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive) drive;

            eDrive.saveChanges(cellInventory);
        }

        // IGridHost methods
        @Override
        @Optional.Method(modid = MODID)
        public IGridNode getGridNode(AEPartLocation dir) {
            github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive eDrive =
                (github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive) drive;

            github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageController controller = eDrive.getController();
            if (controller == null) return null;

            github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageMEChannel channel = controller.getChannel();

            return channel != null ? channel.getGridNode(dir) : null;
        }

        @Override
        @Optional.Method(modid = MODID)
        public AECableType getCableConnectionType(AEPartLocation dir) {
            return AECableType.NONE;
        }

        @Override
        public void securityBreak() {
        }

        // IOrientable methods
        @Override
        public boolean canBeRotated() {
            return false;
        }

        @Override
        public EnumFacing getForward() {
            return EnumFacing.NORTH;
        }

        @Override
        public EnumFacing getUp() {
            return EnumFacing.UP;
        }

        @Override
        public void setOrientation(EnumFacing forward, EnumFacing up) {
        }

        // ICellContainer methods
        @Override
        @Optional.Method(modid = MODID)
        public List<IMEInventoryHandler> getCellArray(IStorageChannel<?> channel) {
            github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive eDrive =
                (github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive) drive;

            IMEInventoryHandler<?> handler = eDrive.getHandler(channel);

            return handler != null ? Collections.singletonList(handler) : Collections.emptyList();
        }

        @Override
        public void blinkCell(int slot) {
        }
    }

    /**
     * Get the cell inventory from an EStorageDriveWrapper.
     * Used by CellDataHandler for compatibility.
     */
    @Optional.Method(modid = MODID)
    public static IItemHandler getWrappedDriveInventory(EStorageDriveWrapper wrapper) {
        Object wrappedDrive = wrapper.getWrappedDrive();
        if (wrappedDrive instanceof github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive) {
            return ((github.kasuminova.ecoaeextension.common.tile.ecotech.estorage.EStorageCellDrive) wrappedDrive).getDriveInv();
        }

        return null;
    }
}
