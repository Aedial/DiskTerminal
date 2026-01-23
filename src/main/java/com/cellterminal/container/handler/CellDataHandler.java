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
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.helpers.IPriorityHost;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;

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

                NBTTagCompound cellData = createCellData(slot, cellStack, storage.getCellStatus(slot));
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
        NBTTagCompound cellData = new NBTTagCompound();
        cellData.setInteger("slot", slot);
        cellData.setInteger("status", status);

        NBTTagCompound cellNbt = new NBTTagCompound();
        cellStack.writeToNBT(cellNbt);
        cellData.setTag("cellItem", cellNbt);

        ICellHandler cellHandler = AEApi.instance().registries().cell().getHandler(cellStack);
        if (cellHandler == null) return cellData;

        // Try each channel type in order
        if (tryPopulateItemCell(cellData, cellHandler, cellStack)) return cellData;
        if (tryPopulateFluidCell(cellData, cellHandler, cellStack)) return cellData;
        tryPopulateEssentiaCell(cellData, cellHandler, cellStack);

        return cellData;
    }

    private static boolean tryPopulateItemCell(NBTTagCompound cellData, ICellHandler cellHandler, ItemStack cellStack) {
        IStorageChannel<IAEItemStack> channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        ICellInventoryHandler<IAEItemStack> handler = cellHandler.getCellInventory(cellStack, null, channel);
        if (handler == null) return false;

        ICellInventory<IAEItemStack> cellInv = handler.getCellInv();
        if (cellInv == null) return false;

        populateCellStats(cellData, cellInv);
        populateConfigInventory(cellData, cellInv.getConfigInventory());
        populateItemContents(cellData, cellInv, channel);
        populateCellUpgrades(cellData, cellInv);

        return true;
    }

    private static boolean tryPopulateFluidCell(NBTTagCompound cellData, ICellHandler cellHandler, ItemStack cellStack) {
        IStorageChannel<IAEFluidStack> channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        ICellInventoryHandler<IAEFluidStack> handler = cellHandler.getCellInventory(cellStack, null, channel);
        if (handler == null) return false;

        ICellInventory<IAEFluidStack> cellInv = handler.getCellInv();
        if (cellInv == null) return false;

        cellData.setBoolean("isFluid", true);
        populateCellStats(cellData, cellInv);
        populateConfigInventory(cellData, cellInv.getConfigInventory());
        populateFluidContents(cellData, cellInv, channel);
        populateCellUpgrades(cellData, cellInv);

        return true;
    }

    private static void tryPopulateEssentiaCell(NBTTagCompound cellData, ICellHandler cellHandler, ItemStack cellStack) {
        NBTTagCompound essentiaData = ThaumicEnergisticsIntegration.tryPopulateEssentiaCell(cellHandler, cellStack);
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
                                              IStorageChannel<IAEItemStack> channel) {
        IItemList<IAEItemStack> contents = cellInv.getAvailableItems(channel.createList());
        NBTTagList contentsList = new NBTTagList();
        int count = 0;

        for (IAEItemStack stack : contents) {
            if (count >= 63) break;
            NBTTagCompound stackNbt = new NBTTagCompound();
            stack.writeToNBT(stackNbt);
            contentsList.appendTag(stackNbt);
            count++;
        }

        cellData.setTag("contents", contentsList);
    }

    private static void populateFluidContents(NBTTagCompound cellData, ICellInventory<IAEFluidStack> cellInv,
                                               IStorageChannel<IAEFluidStack> channel) {
        IItemList<IAEFluidStack> contents = cellInv.getAvailableItems(channel.createList());
        NBTTagList contentsList = new NBTTagList();
        int count = 0;

        for (IAEFluidStack stack : contents) {
            if (count >= 63) break;
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

    private static void populateCellUpgrades(NBTTagCompound cellData, ICellInventory<?> cellInv) {
        IItemHandler upgradesInv = cellInv.getUpgradesInventory();
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
        if (storage instanceof TileDrive) {
            TileDrive drive = (TileDrive) storage;
            if (drive.hasCustomInventoryName()) return drive.getCustomInventoryName();
        } else if (storage instanceof TileChest) {
            TileChest chest = (TileChest) storage;
            if (chest.hasCustomInventoryName()) return chest.getCustomInventoryName();
        }

        return defaultName;
    }

    public static IItemHandler getCellInventory(IChestOrDrive storage) {
        if (storage instanceof TileDrive) return ((TileDrive) storage).getInternalInventory();
        if (storage instanceof TileChest) return ((TileChest) storage).getInternalInventory();

        return null;
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
