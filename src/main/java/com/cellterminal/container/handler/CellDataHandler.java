package com.cellterminal.container.handler;

import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.helpers.IPriorityHost;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;

import com.cellterminal.integration.ECOAEExtensionIntegration;
import com.cellterminal.integration.ThaumicEnergisticsIntegration;


/**
 * Handles cell and storage data generation for NBT serialization.
 * Extracts data generation logic from ContainerCellTerminalBase.
 */
public class CellDataHandler {

    /**
     * Create NBT data for a storage device (ME Drive or ME Chest).
     * @param storage The storage device
     * @param defaultName The default localization key for the storage name
     * @param trackerCallback Callback to register the storage tracker
     * @return NBT data for the storage
     */
    public static NBTTagCompound createStorageData(IChestOrDrive storage, String defaultName,
                                                    StorageTrackerCallback trackerCallback) {
        return createStorageData(storage, defaultName, trackerCallback, Integer.MAX_VALUE);
    }

    /**
     * Create NBT data for a storage device (ME Drive or ME Chest).
     * @param storage The storage device
     * @param defaultName The default localization key for the storage name
     * @param trackerCallback Callback to register the storage tracker
     * @param slotLimit Maximum number of item types to include per cell
     * @return NBT data for the storage
     */
    public static NBTTagCompound createStorageData(IChestOrDrive storage, String defaultName,
                                                    StorageTrackerCallback trackerCallback, int slotLimit) {
        TileEntity te = (TileEntity) storage;
        long id = te.getPos().toLong() ^ ((long) te.getWorld().provider.getDimension() << 48);

        if (trackerCallback != null) trackerCallback.register(id, te, storage);

        NBTTagCompound storageData = new NBTTagCompound();
        storageData.setLong("id", id);
        storageData.setLong("pos", te.getPos().toLong());
        storageData.setInteger("dim", te.getWorld().provider.getDimension());

        String name = getStorageName(storage, defaultName);
        storageData.setString("name", name);

        if (te instanceof IPriorityHost) {
            storageData.setInteger("priority", ((IPriorityHost) te).getPriority());
        }

        ItemStack blockItem = getBlockItem(te);
        if (!blockItem.isEmpty()) {
            NBTTagCompound blockNbt = new NBTTagCompound();
            blockItem.writeToNBT(blockNbt);
            storageData.setTag("blockItem", blockNbt);
        }

        storageData.setInteger("slotCount", storage.getCellCount());

        NBTTagList cellList = new NBTTagList();
        IItemHandler cellInventory = getCellInventory(storage);

        if (cellInventory != null) {
            for (int slot = 0; slot < storage.getCellCount(); slot++) {
                ItemStack cellStack = cellInventory.getStackInSlot(slot);
                if (cellStack.isEmpty()) continue;

                NBTTagCompound cellData = createCellData(slot, cellStack, storage.getCellStatus(slot), slotLimit);
                cellList.appendTag(cellData);
            }
        }

        storageData.setTag("cells", cellList);

        return storageData;
    }

    /**
     * Create NBT data for a single cell.
     */
    public static NBTTagCompound createCellData(int slot, ItemStack cellStack, int status) {
        return createCellData(slot, cellStack, status, Integer.MAX_VALUE);
    }

    /**
     * Create NBT data for a single cell.
     * @param slot The slot index in the storage device
     * @param cellStack The cell ItemStack
     * @param status The cell status
     * @param slotLimit Maximum number of item types to include
     * @return NBT data for the cell
     */
    public static NBTTagCompound createCellData(int slot, ItemStack cellStack, int status, int slotLimit) {
        NBTTagCompound cellData = new NBTTagCompound();
        cellData.setInteger("slot", slot);
        cellData.setInteger("status", status);

        NBTTagCompound cellNbt = new NBTTagCompound();
        cellStack.writeToNBT(cellNbt);
        cellData.setTag("cellItem", cellNbt);

        ICellHandler cellHandler = AEApi.instance().registries().cell().getHandler(cellStack);
        if (cellHandler == null) return cellData;

        // Try each channel type in order
        if (tryPopulateItemCell(cellData, cellHandler, cellStack, slotLimit)) return cellData;
        if (tryPopulateFluidCell(cellData, cellHandler, cellStack, slotLimit)) return cellData;
        tryPopulateEssentiaCell(cellData, cellHandler, cellStack, slotLimit);

        return cellData;
    }

    private static boolean tryPopulateItemCell(NBTTagCompound cellData, ICellHandler cellHandler,
                                                ItemStack cellStack, int slotLimit) {
        IStorageChannel<IAEItemStack> channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        ICellInventoryHandler<IAEItemStack> handler = cellHandler.getCellInventory(cellStack, null, channel);
        if (handler == null) return false;

        ICellInventory<IAEItemStack> cellInv = handler.getCellInv();

        // If cellInv is null (e.g., VoidCells), fall back to ICellWorkbenchItem for config/upgrades
        if (cellInv == null) {
            if (!(cellStack.getItem() instanceof ICellWorkbenchItem)) return false;

            ICellWorkbenchItem workbenchItem = (ICellWorkbenchItem) cellStack.getItem();
            IItemHandler configInv = workbenchItem.getConfigInventory(cellStack);
            IItemHandler upgradesInv = workbenchItem.getUpgradesInventory(cellStack);

            // Only populate if we have at least config or upgrades
            if (configInv == null && upgradesInv == null) return false;

            populateConfigInventory(cellData, configInv);
            populateCellUpgrades(cellData, upgradesInv);

            return true;
        }

        cellData.setBoolean("isItem", true);
        populateCellStats(cellData, cellInv);
        populateConfigInventory(cellData, cellInv.getConfigInventory());
        populateItemContents(cellData, cellInv, channel, slotLimit);
        populateCellUpgrades(cellData, cellInv.getUpgradesInventory());

        return true;
    }

    private static boolean tryPopulateFluidCell(NBTTagCompound cellData, ICellHandler cellHandler,
                                                 ItemStack cellStack, int slotLimit) {
        IStorageChannel<IAEFluidStack> channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        ICellInventoryHandler<IAEFluidStack> handler = cellHandler.getCellInventory(cellStack, null, channel);
        if (handler == null) return false;

        ICellInventory<IAEFluidStack> cellInv = handler.getCellInv();

        // If cellInv is null (e.g., VoidCells), fall back to ICellWorkbenchItem for config/upgrades
        if (cellInv == null) {
            if (!(cellStack.getItem() instanceof ICellWorkbenchItem)) return false;

            ICellWorkbenchItem workbenchItem = (ICellWorkbenchItem) cellStack.getItem();
            IItemHandler configInv = workbenchItem.getConfigInventory(cellStack);
            IItemHandler upgradesInv = workbenchItem.getUpgradesInventory(cellStack);

            // Only populate if we have at least config or upgrades
            if (configInv == null && upgradesInv == null) return false;

            cellData.setBoolean("isFluid", true);
            populateConfigInventory(cellData, configInv);
            populateCellUpgrades(cellData, upgradesInv);

            return true;
        }

        cellData.setBoolean("isFluid", true);
        populateCellStats(cellData, cellInv);
        populateConfigInventory(cellData, cellInv.getConfigInventory());
        populateFluidContents(cellData, cellInv, channel, slotLimit);
        populateCellUpgrades(cellData, cellInv.getUpgradesInventory());

        return true;
    }

    private static void tryPopulateEssentiaCell(NBTTagCompound cellData, ICellHandler cellHandler,
                                                 ItemStack cellStack, int slotLimit) {
        NBTTagCompound essentiaData = ThaumicEnergisticsIntegration.tryPopulateEssentiaCell(
            cellHandler, cellStack, slotLimit);
        if (essentiaData != null) {
            for (String key : essentiaData.getKeySet()) {
                cellData.setTag(key, essentiaData.getTag(key));
            }
        }
    }

    private static void populateCellStats(NBTTagCompound cellData, ICellInventory<?> cellInv) {
        cellData.setLong("usedBytes", cellInv.getUsedBytes());
        cellData.setLong("totalBytes", cellInv.getTotalBytes());
        cellData.setLong("usedTypes", cellInv.getStoredItemTypes());
        cellData.setLong("totalTypes", cellInv.getTotalItemTypes());
        cellData.setLong("storedItemCount", cellInv.getStoredItemCount());
    }

    private static void populateConfigInventory(NBTTagCompound cellData, IItemHandler configInv) {
        if (configInv == null) return;

        NBTTagList partitionList = new NBTTagList();
        for (int i = 0; i < configInv.getSlots(); i++) {
            ItemStack partItem = configInv.getStackInSlot(i);
            NBTTagCompound partNbt = new NBTTagCompound();
            partNbt.setInteger("slot", i);
            if (!partItem.isEmpty()) partItem.writeToNBT(partNbt);
            partitionList.appendTag(partNbt);
        }

        cellData.setTag("partition", partitionList);
    }

    private static void populateItemContents(NBTTagCompound cellData, ICellInventory<IAEItemStack> cellInv,
                                              IStorageChannel<IAEItemStack> channel, int slotLimit) {
        IItemList<IAEItemStack> contents = cellInv.getAvailableItems(channel.createList());
        NBTTagList contentsList = new NBTTagList();
        int count = 0;

        for (IAEItemStack stack : contents) {
            if (count >= slotLimit) break;

            NBTTagCompound stackNbt = new NBTTagCompound();
            stack.writeToNBT(stackNbt);
            contentsList.appendTag(stackNbt);
            count++;
        }

        cellData.setTag("contents", contentsList);
    }

    private static void populateFluidContents(NBTTagCompound cellData, ICellInventory<IAEFluidStack> cellInv,
                                               IStorageChannel<IAEFluidStack> channel, int slotLimit) {
        IItemList<IAEFluidStack> contents = cellInv.getAvailableItems(channel.createList());
        NBTTagList contentsList = new NBTTagList();
        int count = 0;

        for (IAEFluidStack stack : contents) {
            if (count >= slotLimit) break;

            ItemStack itemRep = stack.asItemStackRepresentation();
            if (itemRep.isEmpty()) continue;

            NBTTagCompound stackNbt = new NBTTagCompound();
            itemRep.writeToNBT(stackNbt);
            stackNbt.setLong("fluidAmount", stack.getStackSize());
            contentsList.appendTag(stackNbt);
            count++;
        }

        cellData.setTag("contents", contentsList);
    }

    private static void populateCellUpgrades(NBTTagCompound cellData, IItemHandler upgradesInv) {
        if (upgradesInv == null) return;

        NBTTagList upgradeList = new NBTTagList();
        boolean hasSticky = false, hasFuzzy = false, hasInverter = false;

        for (int i = 0; i < upgradesInv.getSlots(); i++) {
            ItemStack upgrade = upgradesInv.getStackInSlot(i);
            if (upgrade.isEmpty()) continue;

            NBTTagCompound upgradeNbt = new NBTTagCompound();
            upgrade.writeToNBT(upgradeNbt);
            upgradeNbt.setInteger("slot", i);
            upgradeList.appendTag(upgradeNbt);

            if (upgrade.getItem() instanceof IUpgradeModule) {
                Upgrades type = ((IUpgradeModule) upgrade.getItem()).getType(upgrade);
                if (type == Upgrades.STICKY) hasSticky = true;
                else if (type == Upgrades.FUZZY) hasFuzzy = true;
                else if (type == Upgrades.INVERTER) hasInverter = true;
            }
        }

        cellData.setTag("upgrades", upgradeList);
        cellData.setBoolean("hasSticky", hasSticky);
        cellData.setBoolean("hasFuzzy", hasFuzzy);
        cellData.setBoolean("hasInverter", hasInverter);
    }

    private static String getStorageName(IChestOrDrive storage, String defaultName) {
        IItemHandler cellInv = getCellInventory(storage);
        if (cellInv != null && storage instanceof AENetworkInvTile) {
            AENetworkInvTile networkTile = (AENetworkInvTile) storage;
            if (networkTile.hasCustomInventoryName()) return networkTile.getCustomInventoryName();
        }

        return defaultName;
    }

    public static IItemHandler getCellInventory(IChestOrDrive storage) {
        // Modular handling of Storage Drive-like tiles via AENetworkInvTile
        if (storage instanceof AENetworkInvTile) return ((AENetworkInvTile) storage).getInternalInventory();

        if (storage instanceof ECOAEExtensionIntegration.EStorageDriveWrapper) {
             return getEStorageDriveInventory((ECOAEExtensionIntegration.EStorageDriveWrapper) storage);
        }

        return null;
    }

    /**
     * Get cell inventory from ECOAEExtension's wrapped drive.
     */
    private static IItemHandler getEStorageDriveInventory(
        ECOAEExtensionIntegration.EStorageDriveWrapper wrapper) {
        if (!ECOAEExtensionIntegration.isModLoaded()) return null;

        return ECOAEExtensionIntegration.getWrappedDriveInventory(wrapper);
    }

    private static ItemStack getBlockItem(TileEntity te) {
        if (te.getWorld() == null) return ItemStack.EMPTY;

        return te.getWorld().getBlockState(te.getPos()).getBlock()
            .getPickBlock(te.getWorld().getBlockState(te.getPos()), null, te.getWorld(), te.getPos(), null);
    }

    /**
     * Callback interface for registering storage trackers.
     */
    @FunctionalInterface
    public interface StorageTrackerCallback {
        void register(long id, TileEntity tile, IChestOrDrive storage);
    }
}
