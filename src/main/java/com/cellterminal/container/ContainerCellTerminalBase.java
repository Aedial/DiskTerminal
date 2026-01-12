package com.cellterminal.container;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.implementations.IUpgradeableHost;
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

import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketCellTerminalUpdate;
import com.cellterminal.network.PacketPartitionAction;


/**
 * Base container for Cell Terminal variants.
 * Contains shared functionality for scanning ME network storage and managing cell partitions.
 */
public abstract class ContainerCellTerminalBase extends AEBaseContainer {

    protected final Map<TileEntity, StorageTracker> trackers = new HashMap<>();
    protected final Map<Long, StorageTracker> byId = new LinkedHashMap<>();
    protected IGrid grid;
    protected NBTTagCompound pendingData = new NBTTagCompound();
    protected boolean needsFullRefresh = true;

    public ContainerCellTerminalBase(InventoryPlayer ip, IPart part) {
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
            CellTerminalNetwork.INSTANCE.sendTo(
                new PacketCellTerminalUpdate(this.pendingData),
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

        // Add terminal position for sorting
        addTerminalPosition(data);

        if (this.grid == null) {
            data.setTag("storages", storageList);
            this.pendingData = data;

            return;
        }

        // Scan ME Drives
        for (IGridNode gn : this.grid.getMachines(TileDrive.class)) {
            if (!gn.isActive()) continue;

            TileDrive drive = (TileDrive) gn.getMachine();
            NBTTagCompound storageData = createStorageData(drive, "tile.appliedenergistics2.drive.name");
            storageList.appendTag(storageData);
        }

        // Scan ME Chests
        for (IGridNode gn : this.grid.getMachines(TileChest.class)) {
            if (!gn.isActive()) continue;

            TileChest chest = (TileChest) gn.getMachine();
            NBTTagCompound storageData = createStorageData(chest, "tile.appliedenergistics2.chest.name");
            storageList.appendTag(storageData);
        }

        data.setTag("storages", storageList);
        this.pendingData = data;
    }

    /**
     * Add sorting origin to the update data for client-side sorting.
     * Uses origin (0, 0, 0) for consistent ordering regardless of terminal position.
     */
    protected void addTerminalPosition(NBTTagCompound data) {
        IActionHost host = this.getActionHost();
        if (host instanceof IUpgradeableHost) {
            TileEntity te = ((IUpgradeableHost) host).getTile();
            if (te != null) {
                data.setLong("terminalPos", BlockPos.ORIGIN.toLong());
                data.setInteger("terminalDim", te.getWorld().provider.getDimension());
            }
        }
    }

    protected NBTTagCompound createStorageData(IChestOrDrive storage, String defaultName) {
        TileEntity te = (TileEntity) storage;

        // Use position-based stable ID: combine pos hash with dimension
        // This ensures IDs remain consistent across regenerations
        long id = te.getPos().toLong() ^ ((long) te.getWorld().provider.getDimension() << 48);

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

        // Store the total slot count for the storage
        storageData.setInteger("slotCount", storage.getCellCount());

        // Collect cell information
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

    protected NBTTagCompound createCellData(int slot, ItemStack cellStack, int status) {
        NBTTagCompound cellData = new NBTTagCompound();
        cellData.setInteger("slot", slot);
        cellData.setInteger("status", status);

        // Write the cell item
        NBTTagCompound cellNbt = new NBTTagCompound();
        cellStack.writeToNBT(cellNbt);
        cellData.setTag("cellItem", cellNbt);

        // Get cell inventory info - try item channel first, then fluid channel
        ICellHandler cellHandler = AEApi.instance().registries().cell().getHandler(cellStack);
        if (cellHandler == null) return cellData;

        // Try item channel first
        IStorageChannel<IAEItemStack> itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        ICellInventoryHandler<IAEItemStack> itemCellHandler = cellHandler.getCellInventory(cellStack, null, itemChannel);

        if (itemCellHandler != null) {
            populateCellDataFromItemCell(cellData, itemCellHandler, itemChannel);

            return cellData;
        }

        // Try fluid channel
        IStorageChannel<IAEFluidStack> fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        ICellInventoryHandler<IAEFluidStack> fluidCellHandler = cellHandler.getCellInventory(cellStack, null, fluidChannel);

        if (fluidCellHandler != null) {
            populateCellDataFromFluidCell(cellData, fluidCellHandler, fluidChannel);
            cellData.setBoolean("isFluid", true);
        }

        return cellData;
    }

    protected void populateCellDataFromItemCell(NBTTagCompound cellData, ICellInventoryHandler<IAEItemStack> cellInvHandler, IStorageChannel<IAEItemStack> channel) {
        ICellInventory<IAEItemStack> cellInv = cellInvHandler.getCellInv();
        if (cellInv == null) return;

        cellData.setLong("usedBytes", cellInv.getUsedBytes());
        cellData.setLong("totalBytes", cellInv.getTotalBytes());
        cellData.setLong("usedTypes", cellInv.getStoredItemTypes());
        cellData.setLong("totalTypes", cellInv.getTotalItemTypes());
        cellData.setLong("storedItemCount", cellInv.getStoredItemCount());

        // Get partition (config inventory) - include slot index to preserve gaps
        IItemHandler configInv = cellInv.getConfigInventory();
        if (configInv != null) {
            NBTTagList partitionList = new NBTTagList();
            for (int i = 0; i < configInv.getSlots(); i++) {
                ItemStack partItem = configInv.getStackInSlot(i);
                NBTTagCompound partNbt = new NBTTagCompound();
                partNbt.setInteger("slot", i);

                if (!partItem.isEmpty()) {
                    partItem.writeToNBT(partNbt);
                }

                partitionList.appendTag(partNbt);
            }

            cellData.setTag("partition", partitionList);
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
        cellData.setTag("contents", contentsList);
    }

    protected void populateCellDataFromFluidCell(NBTTagCompound cellData, ICellInventoryHandler<IAEFluidStack> cellInvHandler, IStorageChannel<IAEFluidStack> channel) {
        ICellInventory<IAEFluidStack> cellInv = cellInvHandler.getCellInv();
        if (cellInv == null) return;

        cellData.setLong("usedBytes", cellInv.getUsedBytes());
        cellData.setLong("totalBytes", cellInv.getTotalBytes());
        cellData.setLong("usedTypes", cellInv.getStoredItemTypes());
        cellData.setLong("totalTypes", cellInv.getTotalItemTypes());
        cellData.setLong("storedItemCount", cellInv.getStoredItemCount());

        // Get partition (config inventory) - fluid cells use FluidDummyItem, include slot index
        IItemHandler configInv = cellInv.getConfigInventory();
        if (configInv != null) {
            NBTTagList partitionList = new NBTTagList();
            for (int i = 0; i < configInv.getSlots(); i++) {
                ItemStack partItem = configInv.getStackInSlot(i);
                NBTTagCompound partNbt = new NBTTagCompound();
                partNbt.setInteger("slot", i);

                if (!partItem.isEmpty()) {
                    partItem.writeToNBT(partNbt);
                }

                partitionList.appendTag(partNbt);
            }

            cellData.setTag("partition", partitionList);
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
        cellData.setTag("contents", contentsList);
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
    public void handlePartitionAction(long storageId, int cellSlot, PacketPartitionAction.Action action,
                                       int partitionSlot, ItemStack itemStack) {
        StorageTracker tracker = this.byId.get(storageId);
        if (tracker == null) return;

        IItemHandler cellInventory = getCellInventory(tracker.storage);
        if (cellInventory == null) return;

        ItemStack cellStack = cellInventory.getStackInSlot(cellSlot);
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
                    // Directly replace the slot (supports both adding to empty and replacing existing)
                    setConfigSlot(configInv, partitionSlot, itemStack);
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
     * Handle cell eject requests from client.
     * Ejects the cell from the drive to the player's inventory.
     */
    public void handleEjectCell(long storageId, int cellSlot, EntityPlayer player) {
        StorageTracker tracker = this.byId.get(storageId);
        if (tracker == null) return;

        IItemHandler cellInventory = getCellInventory(tracker.storage);
        if (cellInventory == null) return;

        ItemStack cellStack = cellInventory.getStackInSlot(cellSlot);
        if (cellStack.isEmpty()) return;

        // Extract the cell from the drive
        ItemStack extracted = cellInventory.extractItem(cellSlot, 1, false);
        if (extracted.isEmpty()) return;

        // Try to add to player inventory
        if (!player.inventory.addItemStackToInventory(extracted)) {
            // If inventory is full, drop on ground
            player.dropItem(extracted, false);
        }

        // Trigger refresh to send updated data to client
        this.needsFullRefresh = true;
    }

    /**
     * Handle cell pickup requests from client (for tab 2/3 slot clicking).
     * Puts the cell in the player's hand (cursor) for quick reorganization.
     * Supports swapping if player is already holding a cell.
     */
    public void handlePickupCell(long storageId, int cellSlot, EntityPlayer player) {
        StorageTracker tracker = this.byId.get(storageId);
        if (tracker == null) return;

        IItemHandler cellInventory = getCellInventory(tracker.storage);
        if (cellInventory == null) return;

        ItemStack cellStack = cellInventory.getStackInSlot(cellSlot);
        ItemStack heldStack = player.inventory.getItemStack();

        // If slot is empty and player is holding a cell, insert it
        if (cellStack.isEmpty()) {
            if (!heldStack.isEmpty() && AEApi.instance().registries().cell().getHandler(heldStack) != null) {
                ItemStack remainder = cellInventory.insertItem(cellSlot, heldStack.copy(), false);
                if (remainder.isEmpty()) {
                    player.inventory.setItemStack(ItemStack.EMPTY);
                } else {
                    player.inventory.setItemStack(remainder);
                }

                ((EntityPlayerMP) player).updateHeldItem();
                this.needsFullRefresh = true;
            }

            return;
        }

        // Slot has a cell - check if we can pick it up or swap
        if (!heldStack.isEmpty()) {
            // Try to swap: held item must be a valid cell
            if (AEApi.instance().registries().cell().getHandler(heldStack) == null) return;

            // Extract first, then insert held
            ItemStack extracted = cellInventory.extractItem(cellSlot, 1, false);
            if (extracted.isEmpty()) return;

            ItemStack remainder = cellInventory.insertItem(cellSlot, heldStack.copy(), false);
            if (!remainder.isEmpty()) {
                // Failed to insert - put extracted back and abort
                cellInventory.insertItem(cellSlot, extracted, false);

                return;
            }

            player.inventory.setItemStack(extracted);
        } else {
            // Just pick up the cell
            ItemStack extracted = cellInventory.extractItem(cellSlot, 1, false);
            if (extracted.isEmpty()) return;

            player.inventory.setItemStack(extracted);
        }

        ((EntityPlayerMP) player).updateHeldItem();
        this.needsFullRefresh = true;
    }

    /**
     * Handle cell insertion requests from client.
     * Inserts the held cell into the specified storage.
     */
    public void handleInsertCell(long storageId, int targetSlot, EntityPlayer player) {
        ItemStack heldStack = player.inventory.getItemStack();
        if (heldStack.isEmpty()) return;

        // Check if it's a valid cell
        if (AEApi.instance().registries().cell().getHandler(heldStack) == null) return;

        StorageTracker tracker = this.byId.get(storageId);
        if (tracker == null) return;

        IItemHandler cellInventory = getCellInventory(tracker.storage);
        if (cellInventory == null) return;

        // Find target slot
        int slot = targetSlot;
        if (slot < 0) {
            slot = findEmptySlot(cellInventory);
            if (slot < 0) {
                // No empty slot available
                return;
            }
        }

        // Try to insert the cell
        ItemStack remainder = cellInventory.insertItem(slot, heldStack.copy(), false);
        if (remainder.getCount() < heldStack.getCount()) {
            // Something was inserted
            if (remainder.isEmpty()) {
                player.inventory.setItemStack(ItemStack.EMPTY);
            } else {
                player.inventory.setItemStack(remainder);
            }

            ((EntityPlayerMP) player).updateHeldItem();
            this.needsFullRefresh = true;
        }
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

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        net.minecraft.inventory.Slot slot = this.inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) return super.transferStackInSlot(player, slotIndex);

        ItemStack stack = slot.getStack();

        // Check if it's a valid cell
        if (AEApi.instance().registries().cell().getHandler(stack) == null) {
            return super.transferStackInSlot(player, slotIndex);
        }

        // Get position for sorting
        BlockPos origin = BlockPos.ORIGIN;
        int terminalDim = getTerminalDimension();

        // Sort trackers by distance to origin (same dimension first, then by distance)
        List<StorageTracker> sortedTrackers = new ArrayList<>(this.byId.values());
        sortedTrackers.sort(createTrackerComparator(origin, terminalDim));

        // Try to insert into first available drive/chest (sorted by distance)
        for (StorageTracker tracker : sortedTrackers) {
            IItemHandler cellInventory = getCellInventory(tracker.storage);
            if (cellInventory == null) continue;

            int emptySlot = findEmptySlot(cellInventory);
            if (emptySlot < 0) continue;

            ItemStack remainder = cellInventory.insertItem(emptySlot, stack.copy(), false);
            if (remainder.getCount() < stack.getCount()) {
                // Something was inserted
                slot.putStack(remainder);
                slot.onSlotChanged();
                this.needsFullRefresh = true;
                this.detectAndSendChanges();

                return ItemStack.EMPTY;
            }
        }

        return super.transferStackInSlot(player, slotIndex);
    }


    protected int getTerminalDimension() {
        IActionHost host = this.getActionHost();
        if (host instanceof IUpgradeableHost) {
            TileEntity te = ((IUpgradeableHost) host).getTile();
            if (te != null) return te.getWorld().provider.getDimension();
        }

        return 0;
    }

    protected Comparator<StorageTracker> createTrackerComparator(BlockPos terminalPos, int terminalDim) {
        return (a, b) -> {
            int dimA = a.tile.getWorld().provider.getDimension();
            int dimB = b.tile.getWorld().provider.getDimension();

            // Same dimension as terminal comes first
            boolean aInDim = dimA == terminalDim;
            boolean bInDim = dimB == terminalDim;
            if (aInDim != bInDim) return aInDim ? -1 : 1;

            // Sort by dimension
            if (dimA != dimB) return Integer.compare(dimA, dimB);

            // Sort by distance to terminal
            double distA = terminalPos.distanceSq(a.tile.getPos());
            double distB = terminalPos.distanceSq(b.tile.getPos());

            return Double.compare(distA, distB);
        };
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
