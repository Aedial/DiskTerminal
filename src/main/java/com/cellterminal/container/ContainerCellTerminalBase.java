package com.cellterminal.container;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
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
import appeng.api.storage.ICellWorkbenchItem;
import appeng.container.AEBaseContainer;
import appeng.helpers.IPriorityHost;
import appeng.parts.automation.PartUpgradeable;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;
import appeng.util.Platform;

import com.cellterminal.CellTerminal;
import com.cellterminal.config.CellTerminalServerConfig;
import com.cellterminal.container.handler.CellActionHandler;
import com.cellterminal.container.handler.CellDataHandler;
import com.cellterminal.container.handler.StorageBusDataHandler;
import com.cellterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.cellterminal.gui.overlay.MessageHelper;
import com.cellterminal.integration.storage.StorageScannerRegistry;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketCellTerminalUpdate;
import com.cellterminal.network.PacketExtractUpgrade;
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
    protected int storageBusPollCounter = 0;
    protected boolean hasPolledOnce = false;  // Track if initial poll has been done for storage bus tab

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
            this.regenStorageBusList();  // Also refresh storage bus data to prevent blank tabs on initial open
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
     * Only polls if activeTab is a storage bus tab, and respects poll interval from config.
     * If polling is disabled in config, only the initial poll when switching to the tab is performed.
     */
    protected void handleStorageBusPolling() {
        boolean isOnStorageBusTab = (activeTab == TAB_STORAGE_BUS_INVENTORY || activeTab == TAB_STORAGE_BUS_PARTITION);

        // If not on storage bus tab and no pending refresh, nothing to do
        if (!isOnStorageBusTab && !needsStorageBusRefresh) return;

        CellTerminalServerConfig config = CellTerminalServerConfig.getInstance();

        // Always handle the initial/forced refresh when switching to storage bus tab
        if (needsStorageBusRefresh) {
            regenStorageBusList();
            storageBusPollCounter = 0;
            needsStorageBusRefresh = false;
            hasPolledOnce = true;

            return;
        }

        // If polling is disabled, only do initial poll (handled above via needsStorageBusRefresh)
        if (!config.isStorageBusPollingEnabled()) return;

        // Handle periodic polling
        storageBusPollCounter++;

        if (storageBusPollCounter >= config.getPollingInterval()) {
            regenStorageBusList();
            storageBusPollCounter = 0;
        }
    }

    /**
     * Set the active tab on the server (called from packet handler).
     * Triggers storage bus refresh only on first switch to a storage bus tab.
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

            // Use the registry-based scanner for all storage types
            StorageScannerRegistry.scanAllStorages(this.grid, storageList, callback);
        } else {
            CellTerminal.LOGGER.warn("regenStorageList: grid is null!");
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
        // Check if partition editing is enabled in server config
        if (!CellTerminalServerConfig.getInstance().isPartitionEditEnabled()) {
            MessageHelper.error("cellterminal.error.partition_edit_disabled");

            return;
        }

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
        // Check if partition editing is enabled in server config
        if (!CellTerminalServerConfig.getInstance().isPartitionEditEnabled()) {
            MessageHelper.error("cellterminal.error.partition_edit_disabled");

            return;
        }

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
        // Check if cell eject is enabled in server config
        if (!CellTerminalServerConfig.getInstance().isCellEjectEnabled()) {
            MessageHelper.error("cellterminal.error.cell_eject_disabled");

            return;
        }

        StorageTracker tracker = this.byId.get(storageId);
        if (tracker == null) return;

        if (CellActionHandler.ejectCell(tracker.storage, cellSlot, player)) this.needsFullRefresh = true;
    }

    /**
     * Handle priority change requests from client.
     * Supports both storage tiles (ME Drive, Chest) and storage buses.
     */
    public void handleSetPriority(long storageId, int priority) {
        // Check if priority editing is enabled in server config
        if (!CellTerminalServerConfig.getInstance().isPriorityEditEnabled()) {
            MessageHelper.error("cellterminal.error.priority_edit_disabled");

            return;
        }

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
        // Check if upgrade insertion is enabled in server config
        if (!CellTerminalServerConfig.getInstance().isUpgradeInsertEnabled()) {
            MessageHelper.error("cellterminal.error.upgrade_insert_disabled");

            return;
        }

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
        // Check if upgrade insertion is enabled in server config
        if (!CellTerminalServerConfig.getInstance().isUpgradeInsertEnabled()) {
            MessageHelper.error("cellterminal.error.upgrade_insert_disabled");

            return;
        }

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

                this.needsStorageBusRefresh = true;

                return;
            }
        }
    }

    /**
     * Handle upgrade extraction requests from client.
     * Extracts an upgrade from a cell or storage bus and gives it to the player.
     * @param player The player to give the upgrade to
     * @param targetType Whether the target is a cell or storage bus
     * @param targetId The storage ID (for cells) or storage bus ID
     * @param cellSlot The slot of the cell (only used for cells)
     * @param upgradeIndex The upgrade slot index to extract from
     * @param toInventory If true, put in inventory; if false, put in hand
     */
    public void handleExtractUpgrade(EntityPlayer player, PacketExtractUpgrade.TargetType targetType,
                                      long targetId, int cellSlot, int upgradeIndex, boolean toInventory) {
        // Check if upgrade extraction is enabled in server config
        if (!CellTerminalServerConfig.getInstance().isUpgradeExtractEnabled()) {
            MessageHelper.error("cellterminal.error.upgrade_extract_disabled");

            return;
        }

        IItemHandler upgradesInv = null;
        TileEntity tile = null;

        if (targetType == PacketExtractUpgrade.TargetType.CELL) {
            StorageTracker tracker = this.byId.get(targetId);
            if (tracker == null) return;

            IItemHandler cellInventory = CellDataHandler.getCellInventory(tracker.storage);
            if (cellInventory == null) return;

            ItemStack cellStack = cellInventory.getStackInSlot(cellSlot);
            if (cellStack.isEmpty() || !(cellStack.getItem() instanceof ICellWorkbenchItem)) return;

            ICellWorkbenchItem cellItem = (ICellWorkbenchItem) cellStack.getItem();
            if (!cellItem.isEditable(cellStack)) return;

            upgradesInv = cellItem.getUpgradesInventory(cellStack);
            tile = tracker.tile;
        } else {
            StorageBusTracker tracker = this.storageBusById.get(targetId);
            if (tracker == null) return;

            if (!(tracker.storageBus instanceof PartUpgradeable)) return;

            upgradesInv = ((PartUpgradeable) tracker.storageBus).getInventoryByName("upgrades");
            tile = tracker.hostTile;
        }

        if (upgradesInv == null) return;
        if (upgradeIndex < 0 || upgradeIndex >= upgradesInv.getSlots()) return;

        ItemStack upgradeStack = upgradesInv.extractItem(upgradeIndex, 1, false);
        if (upgradeStack.isEmpty()) return;

        boolean success = false;

        if (toInventory) {
            // Try to add to player's inventory
            success = player.inventory.addItemStackToInventory(upgradeStack);
            if (success) player.inventory.markDirty();
        } else {
            // Try to put in player's hand
            ItemStack heldStack = player.inventory.getItemStack();
            if (heldStack.isEmpty()) {
                player.inventory.setItemStack(upgradeStack);
                ((EntityPlayerMP) player).updateHeldItem();
                success = true;
            } else if (ItemStack.areItemsEqual(heldStack, upgradeStack)
                    && ItemStack.areItemStackTagsEqual(heldStack, upgradeStack)
                    && heldStack.getCount() < heldStack.getMaxStackSize()) {
                heldStack.grow(1);
                ((EntityPlayerMP) player).updateHeldItem();
                success = true;
            }
        }

        if (!success) {
            // If we couldn't give the upgrade to the player, drop it on the floor
            player.dropItem(upgradeStack, false);
        }

        if (tile != null) tile.markDirty();

        if (targetType == PacketExtractUpgrade.TargetType.CELL) {
            this.needsFullRefresh = true;
        } else {
            this.needsStorageBusRefresh = true;
        }
    }

    /**
     * Handle cell pickup requests from client.
     */
    public void handlePickupCell(long storageId, int cellSlot, EntityPlayer player, boolean toInventory) {
        CellTerminalServerConfig config = CellTerminalServerConfig.getInstance();
        StorageTracker tracker = this.byId.get(storageId);
        if (tracker == null) return;

        IItemHandler cellInventory = CellDataHandler.getCellInventory(tracker.storage);
        if (cellInventory == null) return;

        ItemStack cellStack = cellInventory.getStackInSlot(cellSlot);
        ItemStack heldStack = player.inventory.getItemStack();

        // Empty slot + holding cell = insert (only for hand mode)
        if (cellStack.isEmpty()) {
            if (!toInventory && !heldStack.isEmpty()) {
                // Check if cell insert is enabled
                if (!config.isCellInsertEnabled()) {
                    MessageHelper.error("cellterminal.error.cell_insert_disabled");

                    return;
                }
            }
        } else if (toInventory) {
            // Shift-click: extract to inventory
            if (!config.isCellEjectEnabled()) {
                MessageHelper.error("cellterminal.error.cell_eject_disabled");

                return;
            }
        } else if (!heldStack.isEmpty()) {
            // Regular click with held item: swap
            if (!config.isCellSwapEnabled()) {
                MessageHelper.error("cellterminal.error.cell_swap_disabled");

                return;
            }
        } else {
            // Regular click with empty hand: pick up
            if (!config.isCellEjectEnabled()) {
                MessageHelper.error("cellterminal.error.cell_eject_disabled");

                return;
            }
        }

        if (CellActionHandler.pickupCell(tracker.storage, cellSlot, player, toInventory)) {
            this.needsFullRefresh = true;
        }
    }

    /**
     * Handle cell insertion requests from client.
     * Inserts the held cell into the specified storage.
     */
    public void handleInsertCell(long storageId, int targetSlot, EntityPlayer player) {
        // Check if cell insert is enabled in server config
        if (!CellTerminalServerConfig.getInstance().isCellInsertEnabled()) {
            MessageHelper.error("cellterminal.error.cell_insert_disabled");

            return;
        }

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

        // Check if cell insert is enabled in server config
        if (!CellTerminalServerConfig.getInstance().isCellInsertEnabled()) {
            MessageHelper.error("cellterminal.error.cell_insert_disabled");

            return ItemStack.EMPTY;
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
