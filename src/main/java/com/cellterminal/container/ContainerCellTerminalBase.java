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
import appeng.api.config.Upgrades;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.parts.IPart;
import appeng.container.AEBaseContainer;
import appeng.helpers.IPriorityHost;
import appeng.parts.automation.PartUpgradeable;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;
import appeng.util.Platform;

import com.cellterminal.container.handler.CellActionHandler;
import com.cellterminal.container.handler.CellDataHandler;
import com.cellterminal.container.handler.StorageBusDataHandler;
import com.cellterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketCellTerminalUpdate;
import com.cellterminal.network.PacketPartitionAction;
import com.cellterminal.network.PacketStorageBusPartitionAction;


/**
 * Base container for Cell Terminal variants.
 * Contains shared functionality for scanning ME network storage and managing cell partitions.
 */
public abstract class ContainerCellTerminalBase extends AEBaseContainer {

    // Storage bus tab indices (must match GuiCellTerminalBase)
    public static final int TAB_STORAGE_BUS_INVENTORY = 3;
    public static final int TAB_STORAGE_BUS_PARTITION = 4;

    protected final Map<TileEntity, StorageTracker> trackers = new HashMap<>();
    protected final Map<Long, StorageTracker> byId = new LinkedHashMap<>();
    protected final Map<Long, StorageBusTracker> storageBusById = new LinkedHashMap<>();
    protected IGrid grid;
    protected NBTTagCompound pendingData = new NBTTagCompound();
    protected boolean needsFullRefresh = true;
    protected boolean needsStorageBusRefresh = false;

    // Current active tab on client - determines whether to poll storage bus data
    protected int activeTab = 0;

    // Tick counter for storage bus polling (only poll every N ticks when on storage bus tab)
    protected static final boolean ENABLE_STORAGE_BUS_POLLING = true;
    protected int storageBusPollCounter = 0;
    protected static final int STORAGE_BUS_POLL_INTERVAL = 20;  // Poll every second (20 ticks)

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

        // Handle storage bus polling when on storage bus tabs
        handleStorageBusPolling();

        if (!this.pendingData.isEmpty()) {
            CellTerminalNetwork.INSTANCE.sendTo(
                new PacketCellTerminalUpdate(this.pendingData),
                (EntityPlayerMP) this.getPlayerInv().player
            );

            this.pendingData = new NBTTagCompound();
        }
    }

    /**
     * Handle storage bus polling when the client is on a storage bus tab.
     * Only polls if activeTab is a storage bus tab, and respects poll interval.
     */
    protected void handleStorageBusPolling() {
        boolean isOnStorageBusTab = (activeTab == TAB_STORAGE_BUS_INVENTORY || activeTab == TAB_STORAGE_BUS_PARTITION);
        if (!ENABLE_STORAGE_BUS_POLLING || (!isOnStorageBusTab && !needsStorageBusRefresh)) return;

        storageBusPollCounter++;

        if (needsStorageBusRefresh || storageBusPollCounter >= STORAGE_BUS_POLL_INTERVAL) {
            regenStorageBusList();
            storageBusPollCounter = 0;
            needsStorageBusRefresh = false;
        }
    }

    /**
     * Set the active tab on the server (called from packet handler).
     * This enables storage bus polling only when needed.
     */
    public void setActiveTab(int tab) {
        boolean wasOnStorageBusTab = (activeTab == TAB_STORAGE_BUS_INVENTORY || activeTab == TAB_STORAGE_BUS_PARTITION);
        boolean isOnStorageBusTab = (tab == TAB_STORAGE_BUS_INVENTORY || tab == TAB_STORAGE_BUS_PARTITION);

        this.activeTab = tab;

        // If switching to a storage bus tab (or initial opening on storage bus tab), immediately refresh
        if (isOnStorageBusTab) {
            needsStorageBusRefresh = true;
            storageBusPollCounter = 0;
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
        addTerminalPosition(data);

        if (this.grid != null) {
            CellDataHandler.StorageTrackerCallback callback = (id, tile, storage) -> {
                StorageTracker tracker = new StorageTracker(id, tile, storage);
                this.trackers.put(tile, tracker);
                this.byId.put(id, tracker);
            };

            // Scan ME Drives
            for (IGridNode gn : this.grid.getMachines(TileDrive.class)) {
                if (!gn.isActive()) continue;
                storageList.appendTag(CellDataHandler.createStorageData((TileDrive) gn.getMachine(),
                    "tile.appliedenergistics2.drive.name", callback));
            }

            // Scan ME Chests
            for (IGridNode gn : this.grid.getMachines(TileChest.class)) {
                if (!gn.isActive()) continue;
                storageList.appendTag(CellDataHandler.createStorageData((TileChest) gn.getMachine(),
                    "tile.appliedenergistics2.chest.name", callback));
            }
        }

        data.setTag("storages", storageList);
        this.pendingData = data;
    }

    /**
     * Regenerate storage bus list. Called periodically when client is on storage bus tab.
     * Sends a flat list of storage buses, sorted and displayed individually.
     */
    protected void regenStorageBusList() {
        this.storageBusById.clear();

        NBTTagCompound data = new NBTTagCompound();

        // Add terminal position for sorting
        addTerminalPosition(data);
        data.setTag("storageBuses", StorageBusDataHandler.collectStorageBuses(this.grid, this.storageBusById));
        mergeIntoPendingData(data);
    }

    /**
     * Merge additional data into pending data (for incremental updates).
     */
    protected void mergeIntoPendingData(NBTTagCompound data) {
        for (String key : data.getKeySet()) this.pendingData.setTag(key, data.getTag(key));
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

        if (CellActionHandler.handlePartitionAction(tracker.storage, tracker.tile, cellSlot, action, partitionSlot, itemStack)) {
            this.needsFullRefresh = true;
        }
    }

    /**
     * Handle storage bus partition modification requests from client.
     * Supports item storage buses (PartStorageBus), fluid storage buses (PartFluidStorageBus),
     * and essentia storage buses (Thaumic Energistics).
     */
    public void handleStorageBusPartitionAction(long storageBusId,
                                                  PacketStorageBusPartitionAction.Action action,
                                                  int partitionSlot, ItemStack itemStack) {
        StorageBusTracker tracker = this.storageBusById.get(storageBusId);
        if (tracker == null) return;

        if (StorageBusDataHandler.handlePartitionAction(tracker, action, partitionSlot, itemStack)) {
            this.needsStorageBusRefresh = true;
        }
    }

    /**
     * Handle storage bus IO mode (access restriction) toggle from client.
     * Cycles through: READ_WRITE -> READ -> WRITE -> READ_WRITE
     */
    public void handleStorageBusIOModeToggle(long storageBusId) {
        StorageBusTracker tracker = this.storageBusById.get(storageBusId);
        if (tracker == null) return;

        if (StorageBusDataHandler.toggleIOMode(tracker)) this.needsStorageBusRefresh = true;
    }

    /**
     * Handle cell eject requests from client.
     * Always ejects to player's inventory (or drops if inventory is full).
     */
    public void handleEjectCell(long storageId, int cellSlot, EntityPlayer player) {
        StorageTracker tracker = this.byId.get(storageId);
        if (tracker == null) return;

        if (CellActionHandler.ejectCell(tracker.storage, cellSlot, player)) this.needsFullRefresh = true;
    }

    /**
     * Handle priority change requests from client.
     * Supports both storage tiles (ME Drive, Chest) and storage buses.
     */
    public void handleSetPriority(long storageId, int priority) {
        // Try storage tiles first
        StorageTracker tracker = this.byId.get(storageId);
        if (tracker != null) {
            if (tracker.tile instanceof IPriorityHost) {
                IPriorityHost priorityHost = (IPriorityHost) tracker.tile;
                priorityHost.setPriority(priority);
                tracker.tile.markDirty();
                this.needsFullRefresh = true;
            }

            return;
        }

        // Try storage buses
        StorageBusTracker busTracker = this.storageBusById.get(storageId);
        if (busTracker != null && busTracker.storageBus instanceof IPriorityHost) {
            ((IPriorityHost) busTracker.storageBus).setPriority(priority);
            this.needsStorageBusRefresh = true;
        }
    }

    /**
     * Handle upgrade cell requests from client.
     * Takes the upgrade from the player's held item or a specific inventory slot and inserts it into the cell.
     * @param player The player holding the upgrade
     * @param storageId The storage containing the cell
     * @param cellSlot The slot of the cell in the storage
     * @param shiftClick If true, find first visible cell that doesn't have this upgrade
     * @param fromSlot Inventory slot to take upgrade from (-1 = cursor)
     */
    public void handleUpgradeCell(EntityPlayer player, long storageId, int cellSlot, boolean shiftClick, int fromSlot) {
        StorageTracker tracker = this.byId.get(storageId);
        if (tracker == null) return;

        ItemStack upgradeStack = fromSlot >= 0 ? player.inventory.getStackInSlot(fromSlot) : player.inventory.getItemStack();
        if (CellActionHandler.upgradeCell(tracker.storage, tracker.tile, cellSlot, upgradeStack, player, fromSlot)) {
            this.needsFullRefresh = true;
        }
    }

    /**
     * Handle upgrade cell requests (legacy signature for compatibility).
     */
    public void handleUpgradeCell(EntityPlayer player, long storageId, int cellSlot, boolean shiftClick) {
        handleUpgradeCell(player, storageId, cellSlot, shiftClick, -1);
    }

    /**
     * Handle upgrade storage bus requests from client.
     * Takes the upgrade from the player's held item or a specific inventory slot and inserts it into the storage bus.
     * @param player The player holding the upgrade
     * @param storageBusId The storage bus to upgrade
     * @param fromSlot Inventory slot to take upgrade from (-1 = cursor)
     */
    public void handleUpgradeStorageBus(EntityPlayer player, long storageBusId, int fromSlot) {
        ItemStack upgradeStack;

        if (fromSlot >= 0) {
            upgradeStack = player.inventory.getStackInSlot(fromSlot);
        } else {
            upgradeStack = player.inventory.getItemStack();
        }

        if (upgradeStack.isEmpty()) return;
        if (!(upgradeStack.getItem() instanceof IUpgradeModule)) return;

        IUpgradeModule upgradeModule = (IUpgradeModule) upgradeStack.getItem();
        Upgrades upgradeType = upgradeModule.getType(upgradeStack);
        if (upgradeType == null) return;

        StorageBusTracker tracker = this.storageBusById.get(storageBusId);
        if (tracker == null) return;

        // Only item and fluid storage buses support upgrades
        if (!(tracker.storageBus instanceof PartUpgradeable)) return;

        IItemHandler upgradesInv = ((PartUpgradeable) tracker.storageBus).getInventoryByName("upgrades");
        if (upgradesInv == null) return;

        // Try to insert the upgrade
        ItemStack toInsert = upgradeStack.copy();
        toInsert.setCount(1);

        for (int slot = 0; slot < upgradesInv.getSlots(); slot++) {
            ItemStack remainder = upgradesInv.insertItem(slot, toInsert, false);
            if (remainder.isEmpty()) {
                upgradeStack.shrink(1);

                if (fromSlot >= 0) {
                    player.inventory.markDirty();
                } else {
                    ((EntityPlayerMP) player).updateHeldItem();
                }
            }

            this.needsStorageBusRefresh = true;
        }
    }

    /**
     * Handle cell pickup requests from client.
     */
    public void handlePickupCell(long storageId, int cellSlot, EntityPlayer player, boolean toInventory) {
        StorageTracker tracker = this.byId.get(storageId);
        if (tracker == null) return;

        if (CellActionHandler.pickupCell(tracker.storage, cellSlot, player, toInventory)) {
            this.needsFullRefresh = true;
        }
    }

    /**
     * Handle cell insertion requests from client.
     * Inserts the held cell into the specified storage.
     */
    public void handleInsertCell(long storageId, int targetSlot, EntityPlayer player) {
        StorageTracker tracker = this.byId.get(storageId);
        if (tracker == null) return;

        if (CellActionHandler.insertCell(tracker.storage, targetSlot, player)) {
            this.needsFullRefresh = true;
        }
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
            IItemHandler cellInventory = CellDataHandler.getCellInventory(tracker.storage);
            if (cellInventory == null) continue;

            int emptySlot = CellActionHandler.findEmptySlot(cellInventory);
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
