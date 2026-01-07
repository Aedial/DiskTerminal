package com.diskterminal.container;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.parts.IPart;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.AEBaseContainer;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;
import appeng.util.Platform;

import com.diskterminal.network.DiskTerminalNetwork;
import com.diskterminal.network.PacketDiskTerminalUpdate;
import com.diskterminal.network.PacketPartitionAction;


/**
 * Base container for Disk Terminal variants.
 * Contains shared functionality for scanning ME network storage and managing disk partitions.
 */
public abstract class ContainerDiskTerminalBase extends AEBaseContainer {

    protected static long autoBase = Long.MIN_VALUE;

    protected final Map<TileEntity, StorageTracker> trackers = new HashMap<>();
    protected final Map<Long, StorageTracker> byId = new HashMap<>();
    protected IGrid grid;
    protected NBTTagCompound pendingData = new NBTTagCompound();
    protected boolean needsFullRefresh = true;

    public ContainerDiskTerminalBase(InventoryPlayer ip, IPart part) {
        super(ip, null, part);
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isClient()) return;
        super.detectAndSendChanges();

        if (!canSendUpdates()) return;

        if (needsFullRefresh) {
            this.regenStorageList();
            needsFullRefresh = false;
        }

        if (!this.pendingData.isEmpty()) {
            DiskTerminalNetwork.INSTANCE.sendTo(
                new PacketDiskTerminalUpdate(this.pendingData),
                (EntityPlayerMP) this.getPlayerInv().player
            );

            this.pendingData = new NBTTagCompound();
        }
    }

    /**
     * Check if the container is in a valid state to send updates.
     * Subclasses can override to add additional checks (e.g., power, range).
     */
    protected boolean canSendUpdates() {
        if (this.grid == null) return false;

        final IActionHost host = this.getActionHost();
        if (host == null) return false;

        final IGridNode agn = host.getActionableNode();

        return agn != null && agn.isActive();
    }

    protected void regenStorageList() {
        this.trackers.clear();
        this.byId.clear();

        NBTTagCompound data = new NBTTagCompound();
        NBTTagList storageList = new NBTTagList();

        if (this.grid == null) {
            data.setTag("storages", storageList);
            this.pendingData = data;

            return;
        }

        // Scan ME Drives
        for (IGridNode gn : this.grid.getMachines(TileDrive.class)) {
            if (!gn.isActive()) continue;

            TileDrive drive = (TileDrive) gn.getMachine();
            NBTTagCompound storageData = createStorageData(drive, "ME Drive");
            storageList.appendTag(storageData);
        }

        // Scan ME Chests
        for (IGridNode gn : this.grid.getMachines(TileChest.class)) {
            if (!gn.isActive()) continue;

            TileChest chest = (TileChest) gn.getMachine();
            NBTTagCompound storageData = createStorageData(chest, "ME Chest");
            storageList.appendTag(storageData);
        }

        data.setTag("storages", storageList);
        this.pendingData = data;
    }

    protected NBTTagCompound createStorageData(IChestOrDrive storage, String defaultName) {
        TileEntity te = (TileEntity) storage;
        long id = autoBase++;

        StorageTracker tracker = new StorageTracker(id, te, storage);
        this.trackers.put(te, tracker);
        this.byId.put(id, tracker);

        NBTTagCompound storageData = new NBTTagCompound();
        storageData.setLong("id", id);
        storageData.setLong("pos", te.getPos().toLong());
        storageData.setInteger("dim", te.getWorld().provider.getDimension());

        String name = defaultName;
        if (storage instanceof TileDrive) {
            TileDrive drive = (TileDrive) storage;
            if (drive.hasCustomInventoryName()) name = drive.getCustomInventoryName();
        } else if (storage instanceof TileChest) {
            TileChest chest = (TileChest) storage;
            if (chest.hasCustomInventoryName()) name = chest.getCustomInventoryName();
        }
        storageData.setString("name", name);

        // Get block item for rendering
        ItemStack blockItem = getBlockItem(te);
        if (!blockItem.isEmpty()) {
            NBTTagCompound blockNbt = new NBTTagCompound();
            blockItem.writeToNBT(blockNbt);
            storageData.setTag("blockItem", blockNbt);
        }

        // Collect disk information
        NBTTagList diskList = new NBTTagList();
        IItemHandler cellInventory = getCellInventory(storage);

        if (cellInventory != null) {
            for (int slot = 0; slot < storage.getCellCount(); slot++) {
                ItemStack cellStack = cellInventory.getStackInSlot(slot);

                if (cellStack.isEmpty()) continue;

                NBTTagCompound diskData = createDiskData(slot, cellStack, storage.getCellStatus(slot));
                diskList.appendTag(diskData);
            }
        }

        storageData.setTag("disks", diskList);

        return storageData;
    }

    protected NBTTagCompound createDiskData(int slot, ItemStack cellStack, int status) {
        NBTTagCompound diskData = new NBTTagCompound();
        diskData.setInteger("slot", slot);
        diskData.setInteger("status", status);

        // Write the cell item
        NBTTagCompound cellNbt = new NBTTagCompound();
        cellStack.writeToNBT(cellNbt);
        diskData.setTag("cellItem", cellNbt);

        // Get cell inventory info - try item channel first, then fluid channel
        ICellHandler cellHandler = AEApi.instance().registries().cell().getHandler(cellStack);
        if (cellHandler == null) return diskData;

        // Try item channel first
        IStorageChannel<IAEItemStack> itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        ICellInventoryHandler<IAEItemStack> itemCellHandler = cellHandler.getCellInventory(cellStack, null, itemChannel);

        if (itemCellHandler != null) {
            populateDiskDataFromItemCell(diskData, itemCellHandler, itemChannel);

            return diskData;
        }

        // Try fluid channel
        IStorageChannel<IAEFluidStack> fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        ICellInventoryHandler<IAEFluidStack> fluidCellHandler = cellHandler.getCellInventory(cellStack, null, fluidChannel);

        if (fluidCellHandler != null) {
            populateDiskDataFromFluidCell(diskData, fluidCellHandler, fluidChannel);
            diskData.setBoolean("isFluid", true);
        }

        return diskData;
    }

    protected void populateDiskDataFromItemCell(NBTTagCompound diskData, ICellInventoryHandler<IAEItemStack> cellInvHandler, IStorageChannel<IAEItemStack> channel) {
        ICellInventory<IAEItemStack> cellInv = cellInvHandler.getCellInv();
        if (cellInv == null) return;

        diskData.setLong("usedBytes", cellInv.getUsedBytes());
        diskData.setLong("totalBytes", cellInv.getTotalBytes());
        diskData.setLong("usedTypes", cellInv.getStoredItemTypes());
        diskData.setLong("totalTypes", cellInv.getTotalItemTypes());
        diskData.setLong("storedItemCount", cellInv.getStoredItemCount());

        // Get partition (config inventory)
        IItemHandler configInv = cellInv.getConfigInventory();
        if (configInv != null) {
            NBTTagList partitionList = new NBTTagList();
            for (int i = 0; i < configInv.getSlots(); i++) {
                ItemStack partItem = configInv.getStackInSlot(i);
                if (!partItem.isEmpty()) {
                    NBTTagCompound partNbt = new NBTTagCompound();
                    partItem.writeToNBT(partNbt);
                    partitionList.appendTag(partNbt);
                }
            }
            diskData.setTag("partition", partitionList);
        }

        // Get stored items (contents preview)
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
        diskData.setTag("contents", contentsList);
    }

    protected void populateDiskDataFromFluidCell(NBTTagCompound diskData, ICellInventoryHandler<IAEFluidStack> cellInvHandler, IStorageChannel<IAEFluidStack> channel) {
        ICellInventory<IAEFluidStack> cellInv = cellInvHandler.getCellInv();
        if (cellInv == null) return;

        diskData.setLong("usedBytes", cellInv.getUsedBytes());
        diskData.setLong("totalBytes", cellInv.getTotalBytes());
        diskData.setLong("usedTypes", cellInv.getStoredItemTypes());
        diskData.setLong("totalTypes", cellInv.getTotalItemTypes());
        diskData.setLong("storedItemCount", cellInv.getStoredItemCount());

        // Get partition (config inventory) - fluid cells use FluidDummyItem
        IItemHandler configInv = cellInv.getConfigInventory();
        if (configInv != null) {
            NBTTagList partitionList = new NBTTagList();
            for (int i = 0; i < configInv.getSlots(); i++) {
                ItemStack partItem = configInv.getStackInSlot(i);
                if (!partItem.isEmpty()) {
                    NBTTagCompound partNbt = new NBTTagCompound();
                    partItem.writeToNBT(partNbt);
                    partitionList.appendTag(partNbt);
                }
            }
            diskData.setTag("partition", partitionList);
        }

        // Get stored fluids (contents preview) - convert to ItemStack representation (FluidDummyItem)
        IItemList<IAEFluidStack> contents = cellInv.getAvailableItems(channel.createList());
        NBTTagList contentsList = new NBTTagList();
        int count = 0;
        for (IAEFluidStack stack : contents) {
            if (count >= 63) break;

            // Convert to ItemStack representation (FluidDummyItem) for client display
            ItemStack itemRep = stack.asItemStackRepresentation();
            if (!itemRep.isEmpty()) {
                NBTTagCompound stackNbt = new NBTTagCompound();
                itemRep.writeToNBT(stackNbt);
                // Store the fluid amount for display purposes
                stackNbt.setLong("fluidAmount", stack.getStackSize());
                contentsList.appendTag(stackNbt);
                count++;
            }
        }
        diskData.setTag("contents", contentsList);
    }

    protected IItemHandler getCellInventory(IChestOrDrive storage) {
        if (storage instanceof TileDrive) {
            return ((TileDrive) storage).getInternalInventory();
        } else if (storage instanceof TileChest) {
            return ((TileChest) storage).getInternalInventory();
        }

        return null;
    }

    protected ItemStack getBlockItem(TileEntity te) {
        if (te.getWorld() != null) {
            return te.getWorld().getBlockState(te.getPos()).getBlock()
                .getPickBlock(te.getWorld().getBlockState(te.getPos()), null, te.getWorld(), te.getPos(), null);
        }

        return ItemStack.EMPTY;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }

    /**
     * Handle partition modification requests from client.
     */
    public void handlePartitionAction(long storageId, int diskSlot, PacketPartitionAction.Action action,
                                       int partitionSlot, ItemStack itemStack) {
        StorageTracker tracker = this.byId.get(storageId);
        if (tracker == null) return;

        IItemHandler cellInventory = getCellInventory(tracker.storage);
        if (cellInventory == null) return;

        ItemStack cellStack = cellInventory.getStackInSlot(diskSlot);
        if (cellStack.isEmpty()) return;

        ICellHandler cellHandler = AEApi.instance().registries().cell().getHandler(cellStack);
        if (cellHandler == null) return;

        // Try item channel first
        IStorageChannel<IAEItemStack> itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        ICellInventoryHandler<IAEItemStack> itemCellHandler = cellHandler.getCellInventory(cellStack, null, itemChannel);

        IItemHandler configInv = null;
        boolean isFluidCell = false;

        if (itemCellHandler != null && itemCellHandler.getCellInv() != null) {
            configInv = itemCellHandler.getCellInv().getConfigInventory();
        } else {
            // Try fluid channel
            IStorageChannel<IAEFluidStack> fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            ICellInventoryHandler<IAEFluidStack> fluidCellHandler = cellHandler.getCellInventory(cellStack, null, fluidChannel);

            if (fluidCellHandler != null && fluidCellHandler.getCellInv() != null) {
                configInv = fluidCellHandler.getCellInv().getConfigInventory();
                isFluidCell = true;
            }
        }

        if (configInv == null) return;

        switch (action) {
            case ADD_ITEM:
                if (partitionSlot >= 0 && partitionSlot < configInv.getSlots() && !itemStack.isEmpty()) {
                    // Find first empty slot if the target is occupied
                    if (!configInv.getStackInSlot(partitionSlot).isEmpty()) partitionSlot = findEmptySlot(configInv);
                    if (partitionSlot >= 0) setConfigSlot(configInv, partitionSlot, itemStack);
                }
                break;

            case REMOVE_ITEM:
                if (partitionSlot >= 0 && partitionSlot < configInv.getSlots()) {
                    setConfigSlot(configInv, partitionSlot, ItemStack.EMPTY);
                }
                break;

            case TOGGLE_ITEM:
                if (!itemStack.isEmpty()) {
                    int existingSlot = findItemInConfig(configInv, itemStack);
                    if (existingSlot >= 0) {
                        setConfigSlot(configInv, existingSlot, ItemStack.EMPTY);
                    } else {
                        int emptySlot = findEmptySlot(configInv);
                        if (emptySlot >= 0) setConfigSlot(configInv, emptySlot, itemStack);
                    }
                }
                break;

            case SET_ALL_FROM_CONTENTS:
                clearConfig(configInv);
                if (!isFluidCell) {
                    ICellInventoryHandler<IAEItemStack> cellInvHandler = cellHandler.getCellInventory(cellStack, null, itemChannel);
                    if (cellInvHandler != null && cellInvHandler.getCellInv() != null) {
                        IItemList<IAEItemStack> contents = cellInvHandler.getCellInv().getAvailableItems(itemChannel.createList());
                        int slot = 0;
                        for (IAEItemStack stack : contents) {
                            if (slot >= configInv.getSlots()) break;
                            setConfigSlot(configInv, slot++, stack.asItemStackRepresentation());
                        }
                    }
                } else {
                    IStorageChannel<IAEFluidStack> fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
                    ICellInventoryHandler<IAEFluidStack> fluidCellHandler = cellHandler.getCellInventory(cellStack, null, fluidChannel);
                    if (fluidCellHandler != null && fluidCellHandler.getCellInv() != null) {
                        IItemList<IAEFluidStack> contents = fluidCellHandler.getCellInv().getAvailableItems(fluidChannel.createList());
                        int slot = 0;
                        for (IAEFluidStack stack : contents) {
                            if (slot >= configInv.getSlots()) break;
                            // Fluid cells use dummy items - the configInv will handle conversion
                            setConfigSlot(configInv, slot++, stack.asItemStackRepresentation());
                        }
                    }
                }
                break;

            case CLEAR_ALL:
                clearConfig(configInv);
                break;
        }

        // Trigger refresh to send updated data to client
        this.needsFullRefresh = true;
    }

    /**
     * Handle disk eject requests from client.
     */
    public void handleEjectDisk(long storageId, int diskSlot, EntityPlayer player) {
        StorageTracker tracker = this.byId.get(storageId);
        if (tracker == null) return;

        IItemHandler cellInventory = getCellInventory(tracker.storage);
        if (cellInventory == null) return;

        ItemStack cellStack = cellInventory.getStackInSlot(diskSlot);
        if (cellStack.isEmpty()) return;

        // Extract the disk from the drive
        ItemStack extracted = cellInventory.extractItem(diskSlot, 1, false);
        if (extracted.isEmpty()) return;

        // Try to add to player inventory
        if (!player.inventory.addItemStackToInventory(extracted)) {
            // If inventory is full, drop on ground
            player.dropItem(extracted, false);
        }

        // Trigger refresh to send updated data to client
        this.needsFullRefresh = true;
    }

    protected int findEmptySlot(IItemHandler inv) {
        for (int i = 0; i < inv.getSlots(); i++) {
            if (inv.getStackInSlot(i).isEmpty()) return i;
        }

        return -1;
    }

    protected int findItemInConfig(IItemHandler inv, ItemStack stack) {
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack configStack = inv.getStackInSlot(i);
            if (ItemStack.areItemsEqual(configStack, stack)) return i;
        }

        return -1;
    }

    protected void setConfigSlot(IItemHandler inv, int slot, ItemStack stack) {
        appeng.util.helpers.ItemHandlerUtil.setStackInSlot(inv, slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
    }

    protected void clearConfig(IItemHandler inv) {
        appeng.util.helpers.ItemHandlerUtil.clear(inv);
    }

    protected static class StorageTracker {
        protected final long id;
        protected final TileEntity tile;
        protected final IChestOrDrive storage;

        public StorageTracker(long id, TileEntity tile, IChestOrDrive storage) {
            this.id = id;
            this.tile = tile;
            this.storage = storage;
        }
    }
}
