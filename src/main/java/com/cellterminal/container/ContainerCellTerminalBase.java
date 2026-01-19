package com.cellterminal.container;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.parts.IPart;
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
import appeng.api.util.AEPartLocation;
import appeng.api.util.IConfigManager;
import appeng.container.AEBaseContainer;
import appeng.fluids.items.FluidDummyItem;
import appeng.fluids.parts.PartFluidStorageBus;
import appeng.fluids.util.IAEFluidTank;
import appeng.helpers.IPriorityHost;
import appeng.me.storage.MEInventoryHandler;
import appeng.parts.automation.PartUpgradeable;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;
import appeng.util.Platform;
import appeng.util.helpers.ItemHandlerUtil;

import com.cells.api.IItemCompactingCell;

import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.integration.ThaumicEnergisticsIntegration;
import com.cellterminal.integration.StorageDrawersIntegration;
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
     * Regenerate storage bus list. Called periodically when client is on storage bus tab.
     * Sends a flat list of storage buses, sorted and displayed individually.
     */
    protected void regenStorageBusList() {
        this.storageBusById.clear();

        NBTTagCompound data = new NBTTagCompound();
        NBTTagList storageBusList = new NBTTagList();

        // Add terminal position for sorting
        addTerminalPosition(data);

        if (this.grid == null) {
            data.setTag("storageBuses", storageBusList);
            mergeIntoPendingData(data);

            return;
        }

        // Collect all item storage buses
        for (IGridNode gn : this.grid.getMachines(PartStorageBus.class)) {
            if (!gn.isActive()) continue;

            PartStorageBus storageBus = (PartStorageBus) gn.getMachine();
            TileEntity hostTile = storageBus.getHost().getTile();
            if (hostTile == null) continue;

            // Create unique bus ID from position, dimension, and facing
            long busId = hostTile.getPos().toLong()
                ^ ((long) hostTile.getWorld().provider.getDimension() << 48)
                ^ ((long) storageBus.getSide().ordinal() << 40);

            StorageBusTracker tracker = new StorageBusTracker(busId, storageBus, hostTile);
            this.storageBusById.put(busId, tracker);

            NBTTagCompound busData = createStorageBusData(storageBus, busId);
            storageBusList.appendTag(busData);
        }

        // Collect all fluid storage buses
        for (IGridNode gn : this.grid.getMachines(PartFluidStorageBus.class)) {
            if (!gn.isActive()) continue;

            PartFluidStorageBus storageBus = (PartFluidStorageBus) gn.getMachine();
            TileEntity hostTile = storageBus.getHost().getTile();
            if (hostTile == null) continue;

            // Create unique bus ID from position, dimension, and facing
            // Use a different high bit to distinguish from item buses
            long busId = hostTile.getPos().toLong()
                ^ ((long) hostTile.getWorld().provider.getDimension() << 48)
                ^ ((long) storageBus.getSide().ordinal() << 40)
                ^ (1L << 39);  // Set a bit to distinguish fluid buses

            StorageBusTracker tracker = new StorageBusTracker(busId, storageBus, hostTile);
            this.storageBusById.put(busId, tracker);

            NBTTagCompound busData = createFluidStorageBusData(storageBus, busId);
            storageBusList.appendTag(busData);
        }

        // Collect all essentia storage buses (from Thaumic Energistics)
        collectEssentiaStorageBuses(storageBusList);

        data.setTag("storageBuses", storageBusList);
        mergeIntoPendingData(data);
    }

    /**
     * Collect essentia storage buses from Thaumic Energistics if loaded.
     */
    @SuppressWarnings("unchecked")
    protected void collectEssentiaStorageBuses(NBTTagList storageBusList) {
        Class<?> essentiaStorageBusClass = ThaumicEnergisticsIntegration.getEssentiaStorageBusClass();
        if (essentiaStorageBusClass == null || this.grid == null) return;

        // Cast to the expected type for getMachines
        Class<? extends IGridHost> busClass = (Class<? extends IGridHost>) essentiaStorageBusClass;

        for (IGridNode gn : this.grid.getMachines(busClass)) {
            if (!gn.isActive()) continue;

            Object machine = gn.getMachine();
            if (machine == null) continue;

            // Get host tile via reflection or interface
            TileEntity hostTile = null;
            try {
                Method getTileMethod = machine.getClass().getMethod("getTile");
                Object result = getTileMethod.invoke(machine);
                if (result instanceof TileEntity) hostTile = (TileEntity) result;
            } catch (Exception e) {
                continue;
            }

            if (hostTile == null) continue;

            // Get side via reflection (it's a public field in Thaumic Energistics parts)
            EnumFacing side = null;
            try {
                Field sideField = machine.getClass().getField("side");
                Object result = sideField.get(machine);
                if (result instanceof AEPartLocation) side = ((AEPartLocation) result).getFacing();
            } catch (Exception e) {
                continue;
            }

            if (side == null) continue;

            // Create unique bus ID with a different bit pattern for essentia buses
            long busId = hostTile.getPos().toLong()
                ^ ((long) hostTile.getWorld().provider.getDimension() << 48)
                ^ ((long) side.ordinal() << 40)
                ^ (2L << 39);  // Use bit pattern 2 for essentia buses

            // Create tracker for essentia bus (so partition actions can find it)
            StorageBusTracker tracker = new StorageBusTracker(busId, machine, hostTile);
            this.storageBusById.put(busId, tracker);

            NBTTagCompound busData = ThaumicEnergisticsIntegration.tryCreateEssentiaStorageBusData(machine, busId);
            if (busData != null) storageBusList.appendTag(busData);
        }
    }

    /**
     * Create NBT data for a single storage bus.
     */
    protected NBTTagCompound createStorageBusData(PartStorageBus bus, long busId) {
        TileEntity hostTile = bus.getHost().getTile();

        NBTTagCompound busData = new NBTTagCompound();
        busData.setLong("id", busId);
        busData.setLong("pos", hostTile.getPos().toLong());
        busData.setInteger("dim", hostTile.getWorld().provider.getDimension());
        busData.setInteger("side", bus.getSide().ordinal());
        busData.setInteger("priority", bus.getPriority());

        // Capacity upgrade count
        int capacityUpgrades = bus.getInstalledUpgrades(Upgrades.CAPACITY);
        busData.setInteger("capacityUpgrades", capacityUpgrades);

        // Upgrade flags
        busData.setBoolean("hasInverter", bus.getInstalledUpgrades(Upgrades.INVERTER) > 0);
        busData.setBoolean("hasSticky", bus.getInstalledUpgrades(Upgrades.STICKY) > 0);
        busData.setBoolean("hasFuzzy", bus.getInstalledUpgrades(Upgrades.FUZZY) > 0);

        // Access restriction (IO mode)
        AccessRestriction access = (AccessRestriction) bus.getConfigManager().getSetting(Settings.ACCESS);
        busData.setInteger("access", access.ordinal());

        // Connected inventory info
        TileEntity targetTile = hostTile.getWorld().getTileEntity(hostTile.getPos().offset(bus.getSide().getFacing()));
        if (targetTile != null) {
            // Get the block name and use block as icon (always the container block, not inventory contents)
            IBlockState state = hostTile.getWorld().getBlockState(
                hostTile.getPos().offset(bus.getSide().getFacing()));
            ItemStack blockStack = getBlockAsItemStack(state, hostTile.getWorld(), hostTile.getPos().offset(bus.getSide().getFacing()));

            if (!blockStack.isEmpty()) {
                busData.setString("connectedName", blockStack.getDisplayName());

                NBTTagCompound iconNbt = new NBTTagCompound();
                blockStack.writeToNBT(iconNbt);
                busData.setTag("connectedIcon", iconNbt);
            }
        }

        // Config slots (partition)
        IItemHandler configInv = bus.getInventoryByName("config");
        if (configInv != null) {
            int slotsToUse = StorageBusInfo.calculateAvailableSlots(capacityUpgrades);
            NBTTagList partitionList = new NBTTagList();
            for (int i = 0; i < configInv.getSlots() && i < slotsToUse; i++) {
                ItemStack partItem = configInv.getStackInSlot(i);
                NBTTagCompound partNbt = new NBTTagCompound();
                partNbt.setInteger("slot", i);
                if (!partItem.isEmpty()) partItem.writeToNBT(partNbt);
                partitionList.appendTag(partNbt);
            }
            busData.setTag("partition", partitionList);
        }

        // Contents from the connected inventory (unfiltered - directly from target)
        // This shows ALL items in the connected inventory, not just those matching partition
        TileEntity self = bus.getHost().getTile();
        TileEntity target = self.getWorld().getTileEntity(self.getPos().offset(bus.getSide().getFacing()));
        if (target != null) {
            EnumFacing targetSide = bus.getSide().getFacing().getOpposite();
            NBTTagList contentsList = new NBTTagList();

            // Try to use IItemRepository (Storage Drawers) first for more efficient query
            List<StorageDrawersIntegration.ItemRecordData> repoContents =
                StorageDrawersIntegration.tryGetItemRepositoryContents(target, targetSide);

            if (repoContents != null) {
                // Use IItemRepository data
                int count = 0;
                for (StorageDrawersIntegration.ItemRecordData record : repoContents) {
                    if (count >= 63) break;
                    NBTTagCompound stackNbt = new NBTTagCompound();
                    record.itemPrototype.writeToNBT(stackNbt);
                    stackNbt.setLong("Cnt", record.count);
                    contentsList.appendTag(stackNbt);
                    count++;
                }

                busData.setTag("contents", contentsList);
            } else {
                // Fall back to standard IItemHandler
                net.minecraftforge.items.IItemHandler targetHandler = target.getCapability(
                    net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, targetSide);

                if (targetHandler != null) {
                    // Build a map of item types to their total counts
                    Map<ItemStack, Long> itemCounts = new LinkedHashMap<>();

                    for (int i = 0; i < targetHandler.getSlots(); i++) {
                        ItemStack slotStack = targetHandler.getStackInSlot(i);
                        if (slotStack.isEmpty()) continue;

                        // Find existing entry or create new one
                        boolean found = false;
                        for (Map.Entry<ItemStack, Long> entry : itemCounts.entrySet()) {
                            if (ItemStack.areItemsEqual(entry.getKey(), slotStack) &&
                                ItemStack.areItemStackTagsEqual(entry.getKey(), slotStack)) {
                                entry.setValue(entry.getValue() + slotStack.getCount());
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            ItemStack keyStack = slotStack.copy();
                            keyStack.setCount(1);
                            itemCounts.put(keyStack, (long) slotStack.getCount());
                        }
                    }

                    int count = 0;
                    for (Map.Entry<ItemStack, Long> entry : itemCounts.entrySet()) {
                        if (count >= 63) break;
                        NBTTagCompound stackNbt = new NBTTagCompound();
                        // Write as AE2-style stack with count separate from item count
                        entry.getKey().writeToNBT(stackNbt);
                        stackNbt.setLong("Cnt", entry.getValue());
                        contentsList.appendTag(stackNbt);
                        count++;
                    }

                    busData.setTag("contents", contentsList);
                }
            }
        }

        // Upgrades list
        IItemHandler upgradesInv = bus.getInventoryByName("upgrades");
        if (upgradesInv != null) {
            NBTTagList upgradeList = new NBTTagList();
            for (int i = 0; i < upgradesInv.getSlots(); i++) {
                ItemStack upgrade = upgradesInv.getStackInSlot(i);
                if (!upgrade.isEmpty()) {
                    NBTTagCompound upgradeNbt = new NBTTagCompound();
                    upgrade.writeToNBT(upgradeNbt);
                    upgradeList.appendTag(upgradeNbt);
                }
            }
            busData.setTag("upgrades", upgradeList);
        }

        return busData;
    }

    /**
     * Create NBT data for a fluid storage bus.
     */
    protected NBTTagCompound createFluidStorageBusData(PartFluidStorageBus bus, long busId) {
        TileEntity hostTile = bus.getHost().getTile();

        NBTTagCompound busData = new NBTTagCompound();
        busData.setLong("id", busId);
        busData.setLong("pos", hostTile.getPos().toLong());
        busData.setInteger("dim", hostTile.getWorld().provider.getDimension());
        busData.setInteger("side", bus.getSide().ordinal());
        busData.setInteger("priority", bus.getPriority());
        busData.setBoolean("isFluid", true);  // Mark as fluid storage bus

        // Capacity upgrade count
        int capacityUpgrades = bus.getInstalledUpgrades(Upgrades.CAPACITY);
        busData.setInteger("capacityUpgrades", capacityUpgrades);

        // Upgrade flags (fluid bus doesn't support all upgrades)
        busData.setBoolean("hasInverter", bus.getInstalledUpgrades(Upgrades.INVERTER) > 0);
        busData.setBoolean("hasSticky", false);  // Fluid bus doesn't support sticky
        busData.setBoolean("hasFuzzy", false);   // Fluid bus doesn't support fuzzy

        // Access restriction (IO mode)
        AccessRestriction access = (AccessRestriction) bus.getConfigManager().getSetting(Settings.ACCESS);
        busData.setInteger("access", access.ordinal());

        // Connected inventory info
        TileEntity targetTile = hostTile.getWorld().getTileEntity(hostTile.getPos().offset(bus.getSide().getFacing()));
        if (targetTile != null) {
            // Get the block name as the connected inventory name
            IBlockState state = hostTile.getWorld().getBlockState(
                hostTile.getPos().offset(bus.getSide().getFacing()));
            ItemStack blockStack = getBlockAsItemStack(state, hostTile.getWorld(), hostTile.getPos().offset(bus.getSide().getFacing()));

            if (!blockStack.isEmpty()) {
                busData.setString("connectedName", blockStack.getDisplayName());
                NBTTagCompound iconNbt = new NBTTagCompound();
                blockStack.writeToNBT(iconNbt);
                busData.setTag("connectedIcon", iconNbt);
            }
        }

        // Get fluid config (partition) - convert fluids to AE2 fluid dummy items for display
        IFluidHandler fluidConfig = bus.getFluidInventoryByName("config");
        if (fluidConfig != null) {
            NBTTagList partitionList = new NBTTagList();
            IFluidTankProperties[] tanks = fluidConfig.getTankProperties();
            int capacityUpgradesForSlots = bus.getInstalledUpgrades(Upgrades.CAPACITY);
            int slotsToUse = StorageBusInfo.calculateAvailableSlots(capacityUpgradesForSlots);

            for (int i = 0; i < tanks.length && i < slotsToUse; i++) {
                FluidStack fluid = tanks[i].getContents();
                NBTTagCompound partNbt = new NBTTagCompound();
                partNbt.setInteger("slot", i);

                if (fluid != null && fluid.getFluid() != null) {
                    // Convert fluid to AE2 dummy item representation (not bucket)
                    IAEFluidStack aeFluid = AEApi.instance().storage()
                        .getStorageChannel(IFluidStorageChannel.class).createStack(fluid);
                    if (aeFluid != null) {
                        ItemStack fluidRep = aeFluid.asItemStackRepresentation();
                        if (!fluidRep.isEmpty()) {
                            fluidRep.writeToNBT(partNbt);
                        }
                    }
                }

                partitionList.appendTag(partNbt);
            }

            busData.setTag("partition", partitionList);
        }

        // Get fluid contents from connected tank
        if (targetTile != null) {
            NBTTagList contentsList = new NBTTagList();
            IFluidHandler targetFluidHandler = targetTile.getCapability(
                net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
                bus.getSide().getFacing().getOpposite()
            );

            if (targetFluidHandler != null) {
                IFluidTankProperties[] tanks = targetFluidHandler.getTankProperties();
                Map<String, Long> fluidCounts = new LinkedHashMap<>();

                for (IFluidTankProperties tank : tanks) {
                    FluidStack fluid = tank.getContents();
                    if (fluid == null || fluid.amount <= 0) continue;

                    String fluidName = fluid.getFluid().getName();
                    fluidCounts.merge(fluidName, (long) fluid.amount, Long::sum);
                }

                int count = 0;
                for (Map.Entry<String, Long> entry : fluidCounts.entrySet()) {
                    if (count >= 63) break;

                    FluidStack fluid = net.minecraftforge.fluids.FluidRegistry.getFluidStack(entry.getKey(), 1000);
                    if (fluid == null) continue;

                    // Convert fluid to AE2 dummy item representation (not bucket)
                    IAEFluidStack aeFluid = AEApi.instance().storage()
                        .getStorageChannel(IFluidStorageChannel.class).createStack(fluid);
                    if (aeFluid == null) continue;

                    ItemStack fluidRep = aeFluid.asItemStackRepresentation();
                    if (fluidRep.isEmpty()) continue;

                    NBTTagCompound stackNbt = new NBTTagCompound();
                    fluidRep.writeToNBT(stackNbt);
                    stackNbt.setLong("Cnt", entry.getValue());
                    contentsList.appendTag(stackNbt);
                    count++;
                }

                busData.setTag("contents", contentsList);
            }
        }

        // Upgrades list
        IItemHandler upgradesInv = bus.getInventoryByName("upgrades");
        if (upgradesInv != null) {
            NBTTagList upgradeList = new NBTTagList();
            for (int i = 0; i < upgradesInv.getSlots(); i++) {
                ItemStack upgrade = upgradesInv.getStackInSlot(i);
                if (!upgrade.isEmpty()) {
                    NBTTagCompound upgradeNbt = new NBTTagCompound();
                    upgrade.writeToNBT(upgradeNbt);
                    upgradeList.appendTag(upgradeNbt);
                }
            }
            busData.setTag("upgrades", upgradeList);
        }

        return busData;
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

    /**
     * Get an ItemStack representation of a block.
     * Uses multiple fallback methods to ensure we get a valid item representation.
     */
    protected ItemStack getBlockAsItemStack(IBlockState state, net.minecraft.world.World world, BlockPos pos) {
        if (state == null || state.getBlock() == null) return ItemStack.EMPTY;

        // First try: Get the item from the block directly
        net.minecraft.item.Item item = net.minecraft.item.Item.getItemFromBlock(state.getBlock());
        if (item != null && item != net.minecraft.init.Items.AIR) {
            int meta = state.getBlock().damageDropped(state);

            return new ItemStack(item, 1, meta);
        }

        // Second try: Create from block with metadata
        try {
            int meta = state.getBlock().getMetaFromState(state);
            ItemStack stack = new ItemStack(state.getBlock(), 1, meta);
            if (!stack.isEmpty()) return stack;
        } catch (Exception e) {
            // Ignore
        }

        // Third try: Create with default metadata
        try {
            ItemStack stack = new ItemStack(state.getBlock());
            if (!stack.isEmpty()) return stack;
        } catch (Exception e) {
            // Ignore
        }

        return ItemStack.EMPTY;
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

        // Get priority from IPriorityHost
        if (te instanceof IPriorityHost) storageData.setInteger("priority", ((IPriorityHost) te).getPriority());

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

        // Get cell inventory info - try item channel first, then fluid channel, then essentia
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

            return cellData;
        }

        // Try Essentia channel (Thaumic Energistics)
        NBTTagCompound essentiaData = ThaumicEnergisticsIntegration.tryPopulateEssentiaCell(cellHandler, cellStack);
        if (essentiaData != null) {
            // Merge essentia data into cellData
            for (String key : essentiaData.getKeySet()) cellData.setTag(key, essentiaData.getTag(key));
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

        // Get upgrade information
        populateCellUpgrades(cellData, cellInv);
    }

    /**
     * Populate upgrade information for a cell.
     */
    protected void populateCellUpgrades(NBTTagCompound cellData, ICellInventory<?> cellInv) {
        IItemHandler upgradesInv = cellInv.getUpgradesInventory();
        if (upgradesInv == null) return;

        NBTTagList upgradeList = new NBTTagList();
        boolean hasSticky = false;
        boolean hasFuzzy = false;
        boolean hasInverter = false;

        for (int i = 0; i < upgradesInv.getSlots(); i++) {
            ItemStack upgrade = upgradesInv.getStackInSlot(i);
            if (upgrade.isEmpty()) continue;

            NBTTagCompound upgradeNbt = new NBTTagCompound();
            upgrade.writeToNBT(upgradeNbt);
            upgradeList.appendTag(upgradeNbt);

            // Check upgrade type
            if (upgrade.getItem() instanceof IUpgradeModule) {
                Upgrades upgradeType = ((IUpgradeModule) upgrade.getItem()).getType(upgrade);
                if (upgradeType != null) {
                    switch (upgradeType) {
                        case STICKY:
                            hasSticky = true;
                            break;
                        case FUZZY:
                            hasFuzzy = true;
                            break;
                        case INVERTER:
                            hasInverter = true;
                            break;
                        default:
                            break;
                    }
                }
            }
        }

        cellData.setTag("upgrades", upgradeList);
        cellData.setBoolean("hasSticky", hasSticky);
        cellData.setBoolean("hasFuzzy", hasFuzzy);
        cellData.setBoolean("hasInverter", hasInverter);
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

        // Get upgrade information
        populateCellUpgrades(cellData, cellInv);
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
        Object[] essentiaData = null;

        if (itemCellHandler != null && itemCellHandler.getCellInv() != null) {
            configInv = itemCellHandler.getCellInv().getConfigInventory();
        } else {
            // Try fluid channel
            IStorageChannel<IAEFluidStack> fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            ICellInventoryHandler<IAEFluidStack> fluidCellHandler = cellHandler.getCellInventory(cellStack, null, fluidChannel);

            if (fluidCellHandler != null && fluidCellHandler.getCellInv() != null) {
                configInv = fluidCellHandler.getCellInv().getConfigInventory();
                isFluidCell = true;
            } else {
                // Try Essentia channel (Thaumic Energistics)
                essentiaData = ThaumicEnergisticsIntegration.tryGetEssentiaConfigInventory(cellHandler, cellStack);
                if (essentiaData != null) configInv = (IItemHandler) essentiaData[0];
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
                if (essentiaData != null) {
                    // Handle Essentia cells
                    ThaumicEnergisticsIntegration.setAllFromEssentiaContents(configInv, essentiaData);
                } else if (!isFluidCell) {
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

        // For compacting cells (from AE2 Storage Cells mod), trigger chain initialization when partition is set
        // Pass the partition item directly to avoid timing issues with NBT reads
        // IMPORTANT: We must refresh the handler cache BEFORE writing chain data,
        // because the refresh calls persist() on the OLD handler which would overwrite our changes.
        if (cellStack.getItem() instanceof IItemCompactingCell) {
            // First, force refresh to persist and clear the old handler
            // This prevents the old handler from overwriting our chain data later
            forceCellHandlerRefresh(tracker, cellSlot);

            ItemStack partitionItem = ItemStack.EMPTY;

            // Determine what item was just partitioned based on action
            if (action == PacketPartitionAction.Action.ADD_ITEM && !itemStack.isEmpty()) {
                partitionItem = itemStack;
            } else if (action == PacketPartitionAction.Action.TOGGLE_ITEM && !itemStack.isEmpty()) {
                // For toggle, only use if we're adding (not removing)
                int existingSlot = findItemInConfig(configInv, itemStack);
                if (existingSlot < 0) {
                    // Item wasn't found, so we just added it
                    partitionItem = itemStack;
                }
            } else if (action == PacketPartitionAction.Action.SET_ALL_FROM_CONTENTS) {
                // Get first item from config as partition
                for (int i = 0; i < configInv.getSlots(); i++) {
                    ItemStack slot = configInv.getStackInSlot(i);
                    if (!slot.isEmpty()) {
                        partitionItem = slot;
                        break;
                    }
                }
            }

            // Now write the chain data - the old handler is gone so it won't overwrite this
            ((IItemCompactingCell) cellStack.getItem()).initializeCompactingCellChain(cellStack, partitionItem, tracker.tile.getWorld());
        }

        // Mark the TileEntity as dirty to save changes
        tracker.tile.markDirty();

        // Trigger refresh to send updated data to client
        this.needsFullRefresh = true;
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

        // Determine bus type and handle accordingly
        if (tracker.storageBus instanceof PartStorageBus) {
            handleItemStorageBusPartition((PartStorageBus) tracker.storageBus, action, partitionSlot, itemStack, tracker);
        } else if (tracker.storageBus instanceof PartFluidStorageBus) {
            handleFluidStorageBusPartition((PartFluidStorageBus) tracker.storageBus, action, partitionSlot, itemStack, tracker);
        } else {
            // Try essentia storage bus via reflection (Thaumic Energistics)
            if (ThaumicEnergisticsIntegration.isModLoaded()) {
                ThaumicEnergisticsIntegration.handleEssentiaStorageBusPartition(tracker.storageBus, action, partitionSlot, itemStack);
                tracker.hostTile.markDirty();
                this.needsStorageBusRefresh = true;
            }
        }
    }

    /**
     * Handle partition action for item storage buses.
     */
    private void handleItemStorageBusPartition(PartStorageBus itemBus,
                                                PacketStorageBusPartitionAction.Action action,
                                                int partitionSlot, ItemStack itemStack,
                                                StorageBusTracker tracker) {
        IItemHandler configInv = itemBus.getInventoryByName("config");
        if (configInv == null) return;

        int capacityUpgrades = itemBus.getInstalledUpgrades(Upgrades.CAPACITY);
        int slotsToUse = StorageBusInfo.calculateAvailableSlots(capacityUpgrades);

        switch (action) {
            case ADD_ITEM:
                if (partitionSlot >= 0 && partitionSlot < slotsToUse && !itemStack.isEmpty()) {
                    setConfigSlot(configInv, partitionSlot, itemStack);
                }
                break;

            case REMOVE_ITEM:
                if (partitionSlot >= 0 && partitionSlot < slotsToUse) {
                    setConfigSlot(configInv, partitionSlot, ItemStack.EMPTY);
                }
                break;

            case TOGGLE_ITEM:
                if (!itemStack.isEmpty()) {
                    int existingSlot = findItemInConfigWithLimit(configInv, itemStack, slotsToUse);
                    if (existingSlot >= 0) {
                        setConfigSlot(configInv, existingSlot, ItemStack.EMPTY);
                    } else {
                        int emptySlot = findEmptySlotWithLimit(configInv, slotsToUse);
                        if (emptySlot >= 0) setConfigSlot(configInv, emptySlot, itemStack);
                    }
                }
                break;

            case SET_ALL_FROM_CONTENTS:
            case PARTITION_ALL:
                clearConfigWithLimit(configInv, slotsToUse);
                MEInventoryHandler<IAEItemStack> handler = itemBus.getInternalHandler();
                if (handler != null) {
                    IStorageChannel<IAEItemStack> itemChannel = AEApi.instance().storage()
                        .getStorageChannel(IItemStorageChannel.class);
                    IItemList<IAEItemStack> contents = handler.getAvailableItems(itemChannel.createList());
                    int slot = 0;
                    for (IAEItemStack stack : contents) {
                        if (slot >= slotsToUse) break;
                        setConfigSlot(configInv, slot++, stack.asItemStackRepresentation());
                    }
                }
                break;

            case CLEAR_ALL:
                clearConfigWithLimit(configInv, slotsToUse);
                break;
        }

        tracker.hostTile.markDirty();
        this.needsStorageBusRefresh = true;
    }

    /**
     * Handle partition action for fluid storage buses.
     */
    private void handleFluidStorageBusPartition(PartFluidStorageBus fluidBus,
                                                 PacketStorageBusPartitionAction.Action action,
                                                 int partitionSlot, ItemStack itemStack,
                                                 StorageBusTracker tracker) {
        IFluidHandler configInv = fluidBus.getFluidInventoryByName("config");
        if (configInv == null) return;

        int capacityUpgrades = fluidBus.getInstalledUpgrades(Upgrades.CAPACITY);
        int slotsToUse = StorageBusInfo.calculateAvailableSlots(capacityUpgrades);

        // Convert ItemStack to FluidStack if needed
        FluidStack fluidFromItem = null;
        if (!itemStack.isEmpty()) {
            // Check for AE2 FluidDummyItem first (fluid representation)
            if (itemStack.getItem() instanceof FluidDummyItem) {
                fluidFromItem = ((FluidDummyItem) itemStack.getItem()).getFluidStack(itemStack);
            }

            // Fall back to FluidUtil for actual fluid containers (buckets, etc)
            if (fluidFromItem == null) {
                fluidFromItem = net.minecraftforge.fluids.FluidUtil.getFluidContained(itemStack);
            }
        }

        switch (action) {
            case ADD_ITEM:
                if (partitionSlot >= 0 && partitionSlot < slotsToUse && fluidFromItem != null) {
                    setFluidConfigSlot(configInv, partitionSlot, fluidFromItem);
                }
                break;

            case REMOVE_ITEM:
                if (partitionSlot >= 0 && partitionSlot < slotsToUse) {
                    setFluidConfigSlot(configInv, partitionSlot, null);
                }
                break;

            case TOGGLE_ITEM:
                if (fluidFromItem != null) {
                    int existingSlot = findFluidInConfig(configInv, fluidFromItem, slotsToUse);
                    if (existingSlot >= 0) {
                        setFluidConfigSlot(configInv, existingSlot, null);
                    } else {
                        int emptySlot = findEmptyFluidSlot(configInv, slotsToUse);
                        if (emptySlot >= 0) setFluidConfigSlot(configInv, emptySlot, fluidFromItem);
                    }
                }
                break;

            case SET_ALL_FROM_CONTENTS:
            case PARTITION_ALL:
                clearFluidConfig(configInv, slotsToUse);
                MEInventoryHandler<IAEFluidStack> handler = fluidBus.getInternalHandler();
                if (handler != null) {
                    IStorageChannel<IAEFluidStack> fluidChannel = AEApi.instance().storage()
                        .getStorageChannel(IFluidStorageChannel.class);
                    IItemList<IAEFluidStack> contents = handler.getAvailableItems(fluidChannel.createList());
                    int slot = 0;
                    for (IAEFluidStack stack : contents) {
                        if (slot >= slotsToUse) break;
                        setFluidConfigSlot(configInv, slot++, stack.getFluidStack());
                    }
                }
                break;

            case CLEAR_ALL:
                clearFluidConfig(configInv, slotsToUse);
                break;
        }

        tracker.hostTile.markDirty();
        this.needsStorageBusRefresh = true;
    }

    // Helper methods for fluid config manipulation
    private void setFluidConfigSlot(IFluidHandler config, int slot, FluidStack fluid) {
        if (config instanceof IAEFluidTank) {
            IAEFluidTank aeConfig = (IAEFluidTank) config;
            if (fluid != null) {
                IAEFluidStack aeFluid = AEApi.instance().storage()
                    .getStorageChannel(IFluidStorageChannel.class).createStack(fluid);
                aeConfig.setFluidInSlot(slot, aeFluid);
            } else {
                aeConfig.setFluidInSlot(slot, null);
            }
        }
    }

    private int findFluidInConfig(IFluidHandler config, FluidStack fluid, int limit) {
        IFluidTankProperties[] tanks = config.getTankProperties();
        for (int i = 0; i < tanks.length && i < limit; i++) {
            FluidStack configFluid = tanks[i].getContents();
            if (configFluid != null && configFluid.isFluidEqual(fluid)) return i;
        }

        return -1;
    }

    private int findEmptyFluidSlot(IFluidHandler config, int limit) {
        IFluidTankProperties[] tanks = config.getTankProperties();
        for (int i = 0; i < tanks.length && i < limit; i++) {
            FluidStack configFluid = tanks[i].getContents();
            if (configFluid == null || configFluid.amount == 0) return i;
        }

        return -1;
    }

    private void clearFluidConfig(IFluidHandler config, int limit) {
        if (config instanceof IAEFluidTank) {
            IAEFluidTank aeConfig = (IAEFluidTank) config;
            for (int i = 0; i < limit; i++) aeConfig.setFluidInSlot(i, null);
        }
    }

    /**
     * Handle storage bus IO mode (access restriction) toggle from client.
     * Cycles through: READ_WRITE -> READ -> WRITE -> READ_WRITE
     */
    public void handleStorageBusIOModeToggle(long storageBusId) {
        StorageBusTracker tracker = this.storageBusById.get(storageBusId);
        if (tracker == null) return;

        // Only item and fluid storage buses support config manager
        if (!(tracker.storageBus instanceof PartUpgradeable)) return;

        IConfigManager configManager = ((PartUpgradeable) tracker.storageBus).getConfigManager();

        // Essentia storage buses don't support config manager (returns null)
        if (configManager == null) return;

        AccessRestriction current = (AccessRestriction) configManager.getSetting(Settings.ACCESS);

        // Cycle to next mode: READ_WRITE -> READ -> WRITE -> READ_WRITE
        AccessRestriction next;
        switch (current) {
            case READ_WRITE:
                next = AccessRestriction.READ;
                break;
            case READ:
                next = AccessRestriction.WRITE;
                break;
            case WRITE:
                next = AccessRestriction.READ_WRITE;
                break;
            default:
                next = AccessRestriction.READ_WRITE;
        }

        configManager.putSetting(Settings.ACCESS, next);

        // Mark the host tile as dirty
        tracker.hostTile.markDirty();

        // Trigger refresh to send updated data to client
        this.needsStorageBusRefresh = true;
    }

    protected int findItemInConfigWithLimit(IItemHandler inv, ItemStack stack, int limit) {
        for (int i = 0; i < inv.getSlots() && i < limit; i++) {
            ItemStack configStack = inv.getStackInSlot(i);
            if (ItemStack.areItemStacksEqual(configStack, stack)) return i;
        }

        return -1;
    }

    protected int findEmptySlotWithLimit(IItemHandler inv, int limit) {
        for (int i = 0; i < inv.getSlots() && i < limit; i++) {
            if (inv.getStackInSlot(i).isEmpty()) return i;
        }

        return -1;
    }

    protected void clearConfigWithLimit(IItemHandler inv, int limit) {
        for (int i = 0; i < inv.getSlots() && i < limit; i++) {
            ItemHandlerUtil.setStackInSlot(inv, i, ItemStack.EMPTY);
        }
    }

    /**
     * Force the drive/chest to rebuild its cell inventory handler cache.
     * This is done by triggering onChangeInventory through a simulated cell change.
     * The cell's partition list is cached in BasicCellInventoryHandler's constructor,
     * so we must force the drive to recreate the handler for changes to take effect.
     */
    private void forceCellHandlerRefresh(StorageTracker tracker, int cellSlot) {
        IItemHandler cellInventory = getCellInventory(tracker.storage);
        if (cellInventory == null) return;

        ItemStack cellStack = cellInventory.getStackInSlot(cellSlot);
        if (cellStack.isEmpty()) return;

        // Extract and re-insert the cell to trigger onChangeInventory
        // This forces the drive to clear its cache and rebuild the cell handler
        if (cellInventory instanceof IItemHandlerModifiable) {
            IItemHandlerModifiable modifiable = (IItemHandlerModifiable) cellInventory;

            // Setting the same stack triggers the inventory change listener
            // which clears the drive's isCached flag and rebuilds handlers
            modifiable.setStackInSlot(cellSlot, cellStack);
        }
    }

    /**
     * Handle cell eject requests from client.
     * Always ejects to player's inventory (or drops if inventory is full).
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

        // Add to player inventory, or drop if full
        if (!player.inventory.addItemStackToInventory(extracted)) {
            player.dropItem(extracted, false);
        }

        // Trigger refresh to send updated data to client
        this.needsFullRefresh = true;
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
        ItemStack upgradeStack;

        if (fromSlot >= 0) {
            // Take upgrade from inventory slot
            upgradeStack = player.inventory.getStackInSlot(fromSlot);
        } else {
            // Take upgrade from cursor
            upgradeStack = player.inventory.getItemStack();
        }

        if (upgradeStack.isEmpty()) return;

        // Check if held item is an upgrade
        if (!(upgradeStack.getItem() instanceof IUpgradeModule)) return;

        IUpgradeModule upgradeModule = (IUpgradeModule) upgradeStack.getItem();
        Upgrades upgradeType = upgradeModule.getType(upgradeStack);
        if (upgradeType == null) return;

        StorageTracker tracker = this.byId.get(storageId);
        if (tracker == null) return;

        IItemHandler cellInventory = getCellInventory(tracker.storage);
        if (cellInventory == null) return;

        // Try to upgrade the specified cell
        // (Client determines the target cell for both regular and shift-click)
        ItemStack cellStack = cellInventory.getStackInSlot(cellSlot);
        if (cellStack.isEmpty()) return;

        if (tryInsertUpgradeIntoCell(cellStack, upgradeStack, upgradeType)) {
            // Mark tile as dirty
            tracker.tile.markDirty();

            // Force refresh
            forceCellHandlerRefresh(tracker, cellSlot);

            // Update player's inventory
            if (fromSlot >= 0) {
                player.inventory.markDirty();
            } else {
                ((EntityPlayerMP) player).updateHeldItem();
            }

            // Trigger refresh to send updated data to client
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

                this.needsStorageBusRefresh = true;

                return;
            }
        }
    }

    /**
     * Try to insert an upgrade into a cell.
     * @param cellStack The cell ItemStack (will be modified in place)
     * @param upgradeStack The upgrade to insert (will have count decremented)
     * @param upgradeType The type of upgrade
     * @return true if the upgrade was inserted
     */
    private boolean tryInsertUpgradeIntoCell(ItemStack cellStack, ItemStack upgradeStack, Upgrades upgradeType) {
        // Cell must implement ICellWorkbenchItem to support upgrades
        if (!(cellStack.getItem() instanceof ICellWorkbenchItem)) return false;

        ICellWorkbenchItem cellItem = (ICellWorkbenchItem) cellStack.getItem();
        if (!cellItem.isEditable(cellStack)) return false;

        IItemHandler upgradesInv = cellItem.getUpgradesInventory(cellStack);
        if (upgradesInv == null) return false;

        // Try to insert the upgrade
        ItemStack toInsert = upgradeStack.copy();
        toInsert.setCount(1);

        for (int slot = 0; slot < upgradesInv.getSlots(); slot++) {
            ItemStack remainder = upgradesInv.insertItem(slot, toInsert, false);
            if (remainder.isEmpty()) {
                // Successfully inserted - decrement held stack
                upgradeStack.shrink(1);

                return true;
            }
        }

        return false;
    }

    /**
     * Handle cell pickup requests from client (for tab 2/3 slot clicking).
     * If toInventory is true (shift-click), puts the cell in player's inventory.
     * If toInventory is false (regular click), puts the cell in player's hand for reorganization.
     * Supports swapping if player is already holding a cell (only when not toInventory).
     */
    public void handlePickupCell(long storageId, int cellSlot, EntityPlayer player, boolean toInventory) {
        StorageTracker tracker = this.byId.get(storageId);
        if (tracker == null) return;

        IItemHandler cellInventory = getCellInventory(tracker.storage);
        if (cellInventory == null) return;

        ItemStack cellStack = cellInventory.getStackInSlot(cellSlot);
        ItemStack heldStack = player.inventory.getItemStack();

        // If slot is empty and player is holding a cell, insert it (only for hand mode)
        if (cellStack.isEmpty()) {
            if (!toInventory && !heldStack.isEmpty() && AEApi.instance().registries().cell().getHandler(heldStack) != null) {
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

        // Shift-click: extract to inventory
        if (toInventory) {
            ItemStack extracted = cellInventory.extractItem(cellSlot, 1, false);
            if (extracted.isEmpty()) return;

            if (!player.inventory.addItemStackToInventory(extracted)) {
                player.dropItem(extracted, false);
            }

            this.needsFullRefresh = true;

            return;
        }

        // Regular click: pick up to hand or swap
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
            if (slot < 0) return;  // No empty slot available
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
            if (ItemStack.areItemStacksEqual(configStack, stack)) return i;
        }

        return -1;
    }

    protected void setConfigSlot(IItemHandler inv, int slot, ItemStack stack) {
        ItemHandlerUtil.setStackInSlot(inv, slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
    }

    protected void clearConfig(IItemHandler inv) {
        ItemHandlerUtil.clear(inv);
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

    protected static class StorageBusTracker {
        protected final long id;
        protected final Object storageBus;  // Can be PartStorageBus, PartFluidStorageBus, or essentia bus
        protected final TileEntity hostTile;

        public StorageBusTracker(long id, Object storageBus, TileEntity hostTile) {
            this.id = id;
            this.storageBus = storageBus;
            this.hostTile = hostTile;
        }
    }
}
