package com.cellterminal.container.handler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AEPartLocation;
import appeng.api.util.IConfigManager;
import appeng.fluids.items.FluidDummyItem;
import appeng.fluids.parts.PartFluidStorageBus;
import appeng.fluids.util.IAEFluidTank;
import appeng.me.storage.MEInventoryHandler;
import appeng.parts.automation.PartUpgradeable;
import appeng.parts.misc.PartStorageBus;
import appeng.util.helpers.ItemHandlerUtil;

import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.integration.StorageDrawersIntegration;
import com.cellterminal.integration.ThaumicEnergisticsIntegration;
import com.cellterminal.network.PacketStorageBusPartitionAction;
import com.cellterminal.integration.storagebus.StorageBusScannerRegistry;


/**
 * Handles storage bus data collection and NBT generation.
 * This is a server-side handler that generates the data sent to clients
 * when they're viewing storage bus tabs.
 */
public class StorageBusDataHandler {

    /**
     * Tracker for storage bus instances.
     */
    public static class StorageBusTracker {
        public final long id;
        public final Object storageBus;  // Can be PartStorageBus, PartFluidStorageBus, or essentia bus
        public final TileEntity hostTile;

        public StorageBusTracker(long id, Object storageBus, TileEntity hostTile) {
            this.id = id;
            this.storageBus = storageBus;
            this.hostTile = hostTile;
        }
    }

    /**
     * Collect all storage buses from the grid and generate NBT data.
     * @param grid The ME network grid
     * @param trackerMap Map to populate with trackers (keyed by bus ID)
     * @return NBTTagList containing all storage bus data
     */
    public static NBTTagList collectStorageBuses(IGrid grid, Map<Long, StorageBusTracker> trackerMap) {
        NBTTagList storageBusList = new NBTTagList();

        if (grid == null) return storageBusList;

        // Delegate collection to registered scanners
        StorageBusScannerRegistry.scanAll(grid, storageBusList, trackerMap);

        return storageBusList;
    }

    /**
     * Create a unique bus ID from position, dimension, side, and type flag.
     */
    public static long createBusId(TileEntity hostTile, int sideOrdinal, int typeFlag) {
        return hostTile.getPos().toLong()
            ^ ((long) hostTile.getWorld().provider.getDimension() << 48)
            ^ ((long) sideOrdinal << 40)
            ^ ((long) typeFlag << 39);
    }

    /**
     * Create NBT data for an item storage bus.
     */
    public static NBTTagCompound createItemStorageBusData(PartStorageBus bus, long busId) {
        TileEntity hostTile = bus.getHost().getTile();

        NBTTagCompound busData = new NBTTagCompound();
        busData.setLong("id", busId);
        busData.setLong("pos", hostTile.getPos().toLong());
        busData.setInteger("dim", hostTile.getWorld().provider.getDimension());
        busData.setInteger("side", bus.getSide().ordinal());
        busData.setInteger("priority", bus.getPriority());
        busData.setBoolean("isItem", true);
        // Slot parameters are provided by the scanner implementation

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
        addConnectedInventoryInfo(busData, hostTile, bus.getSide().getFacing());

        // Config slots (partition)
        addPartitionData(busData, bus.getInventoryByName("config"), capacityUpgrades);

        // Contents from the connected inventory
        addItemContentsData(busData, hostTile, bus.getSide().getFacing());

        // Upgrades list
        addUpgradesData(busData, bus.getInventoryByName("upgrades"));

        return busData;
    }

    /**
     * Create NBT data for a fluid storage bus.
     */
    public static NBTTagCompound createFluidStorageBusData(PartFluidStorageBus bus, long busId) {
        TileEntity hostTile = bus.getHost().getTile();

        NBTTagCompound busData = new NBTTagCompound();
        busData.setLong("id", busId);
        busData.setLong("pos", hostTile.getPos().toLong());
        busData.setInteger("dim", hostTile.getWorld().provider.getDimension());
        busData.setInteger("side", bus.getSide().ordinal());
        busData.setInteger("priority", bus.getPriority());
        busData.setBoolean("isFluid", true);
        // Slot parameters are provided by the scanner implementation

        // Capacity upgrade count
        int capacityUpgrades = bus.getInstalledUpgrades(Upgrades.CAPACITY);
        busData.setInteger("capacityUpgrades", capacityUpgrades);

        // Upgrade flags (fluid bus doesn't support all upgrades)
        busData.setBoolean("hasInverter", bus.getInstalledUpgrades(Upgrades.INVERTER) > 0);
        busData.setBoolean("hasSticky", false);
        busData.setBoolean("hasFuzzy", false);

        // Access restriction (IO mode)
        AccessRestriction access = (AccessRestriction) bus.getConfigManager().getSetting(Settings.ACCESS);
        busData.setInteger("access", access.ordinal());

        // Connected inventory info
        addConnectedInventoryInfo(busData, hostTile, bus.getSide().getFacing());

        // Fluid partition config
        addFluidPartitionData(busData, bus, capacityUpgrades);

        // Fluid contents from connected tank
        addFluidContentsData(busData, hostTile, bus.getSide().getFacing());

        // Upgrades list
        addUpgradesData(busData, bus.getInventoryByName("upgrades"));

        return busData;
    }

    private static void addConnectedInventoryInfo(NBTTagCompound busData, TileEntity hostTile, EnumFacing facing) {
        TileEntity targetTile = hostTile.getWorld().getTileEntity(hostTile.getPos().offset(facing));
        if (targetTile == null) return;

        IBlockState state = hostTile.getWorld().getBlockState(hostTile.getPos().offset(facing));
        ItemStack blockStack = getBlockAsItemStack(state, hostTile.getWorld(), hostTile.getPos().offset(facing));

        if (!blockStack.isEmpty()) {
            busData.setString("connectedName", blockStack.getDisplayName());

            NBTTagCompound iconNbt = new NBTTagCompound();
            blockStack.writeToNBT(iconNbt);
            busData.setTag("connectedIcon", iconNbt);
        }
    }

    private static void addPartitionData(NBTTagCompound busData, IItemHandler configInv, int capacityUpgrades) {
        if (configInv == null) return;

        int slotsToUse = computeAvailableSlotsFrom(busData, capacityUpgrades);
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

    private static void addFluidPartitionData(NBTTagCompound busData, PartFluidStorageBus bus, int capacityUpgrades) {
        IFluidHandler fluidConfig = bus.getFluidInventoryByName("config");
        if (fluidConfig == null) return;

        NBTTagList partitionList = new NBTTagList();
        IFluidTankProperties[] tanks = fluidConfig.getTankProperties();
        int slotsToUse = computeAvailableSlotsFrom(busData, capacityUpgrades);

        for (int i = 0; i < tanks.length && i < slotsToUse; i++) {
            FluidStack fluid = tanks[i].getContents();
            NBTTagCompound partNbt = new NBTTagCompound();
            partNbt.setInteger("slot", i);

            if (fluid != null && fluid.getFluid() != null) {
                IAEFluidStack aeFluid = AEApi.instance().storage()
                    .getStorageChannel(IFluidStorageChannel.class).createStack(fluid);
                if (aeFluid != null) {
                    ItemStack fluidRep = aeFluid.asItemStackRepresentation();
                    if (!fluidRep.isEmpty()) fluidRep.writeToNBT(partNbt);
                }
            }

            partitionList.appendTag(partNbt);
        }

        busData.setTag("partition", partitionList);
    }

    private static void addItemContentsData(NBTTagCompound busData, TileEntity hostTile, EnumFacing facing) {
        TileEntity target = hostTile.getWorld().getTileEntity(hostTile.getPos().offset(facing));
        if (target == null) return;

        EnumFacing targetSide = facing.getOpposite();
        NBTTagList contentsList = new NBTTagList();

        int slotsLimit = computeAvailableSlotsFrom(busData, busData.getInteger("capacityUpgrades"));

        // Try to use IItemRepository (Storage Drawers) first
        List<StorageDrawersIntegration.ItemRecordData> repoContents =
            StorageDrawersIntegration.tryGetItemRepositoryContents(target, targetSide);

        if (repoContents != null) {
            int count = 0;
            for (StorageDrawersIntegration.ItemRecordData record : repoContents) {
                if (count >= slotsLimit) break;
                NBTTagCompound stackNbt = new NBTTagCompound();
                record.itemPrototype.writeToNBT(stackNbt);
                stackNbt.setLong("Cnt", record.count);
                contentsList.appendTag(stackNbt);
                count++;
            }

            busData.setTag("contents", contentsList);

            return;
        }

        // Fall back to standard IItemHandler
        IItemHandler targetHandler = target.getCapability(
            CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, targetSide);

        if (targetHandler == null) return;

        // Build a map of item types to their total counts
        Map<ItemStack, Long> itemCounts = new LinkedHashMap<>();

        for (int i = 0; i < targetHandler.getSlots(); i++) {
            ItemStack slotStack = targetHandler.getStackInSlot(i);
            if (slotStack.isEmpty()) continue;

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
            if (count >= slotsLimit) break;
            NBTTagCompound stackNbt = new NBTTagCompound();
            entry.getKey().writeToNBT(stackNbt);
            stackNbt.setLong("Cnt", entry.getValue());
            contentsList.appendTag(stackNbt);
            count++;
        }

        busData.setTag("contents", contentsList);
    }

    private static void addFluidContentsData(NBTTagCompound busData, TileEntity hostTile, EnumFacing facing) {
        TileEntity targetTile = hostTile.getWorld().getTileEntity(hostTile.getPos().offset(facing));
        if (targetTile == null) return;

        IFluidHandler targetFluidHandler = targetTile.getCapability(
            CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
            facing.getOpposite()
        );

        if (targetFluidHandler == null) return;

        NBTTagList contentsList = new NBTTagList();
        IFluidTankProperties[] tanks = targetFluidHandler.getTankProperties();
        Map<String, Long> fluidCounts = new LinkedHashMap<>();

        for (IFluidTankProperties tank : tanks) {
            FluidStack fluid = tank.getContents();
            if (fluid == null || fluid.amount <= 0) continue;

            String fluidName = fluid.getFluid().getName();
            fluidCounts.merge(fluidName, (long) fluid.amount, Long::sum);
        }

        int count = 0;
        int slotsLimit = computeAvailableSlotsFrom(busData, busData.getInteger("capacityUpgrades"));
        for (Map.Entry<String, Long> entry : fluidCounts.entrySet()) {
            if (count >= slotsLimit) break;

            FluidStack fluid = FluidRegistry.getFluidStack(entry.getKey(), 1000);
            if (fluid == null) continue;

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

    private static int computeAvailableSlotsFrom(NBTTagCompound busData, int capacityUpgrades) {
        int base = busData.hasKey("baseConfigSlots") ? busData.getInteger("baseConfigSlots") : StorageBusInfo.BASE_CONFIG_SLOTS;
        int perUpg = busData.hasKey("slotsPerUpgrade") ? busData.getInteger("slotsPerUpgrade") : StorageBusInfo.SLOTS_PER_CAPACITY_UPGRADE;
        int max = busData.hasKey("maxConfigSlots") ? busData.getInteger("maxConfigSlots") : StorageBusInfo.MAX_CONFIG_SLOTS;

        int raw = base + perUpg * Math.max(0, capacityUpgrades);
        if (raw > max) return max;

        return raw;
    }

    private static void addUpgradesData(NBTTagCompound busData, IItemHandler upgradesInv) {
        if (upgradesInv == null) return;

        NBTTagList upgradeList = new NBTTagList();
        for (int i = 0; i < upgradesInv.getSlots(); i++) {
            ItemStack upgrade = upgradesInv.getStackInSlot(i);
            if (!upgrade.isEmpty()) {
                NBTTagCompound upgradeNbt = new NBTTagCompound();
                upgrade.writeToNBT(upgradeNbt);
                upgradeNbt.setInteger("slot", i);
                upgradeList.appendTag(upgradeNbt);
            }
        }

        busData.setTag("upgrades", upgradeList);
    }

    // ==================== ACTION METHODS ====================

    /**
     * Handle partition action for a storage bus.
     * @return true if partition was modified
     */
    public static boolean handlePartitionAction(StorageBusTracker tracker,
                                                  PacketStorageBusPartitionAction.Action action,
                                                  int partitionSlot, ItemStack itemStack) {
        if (tracker.storageBus instanceof PartStorageBus) {
            return handleItemBusPartition((PartStorageBus) tracker.storageBus, action, partitionSlot, itemStack, tracker);
        } else if (tracker.storageBus instanceof PartFluidStorageBus) {
            return handleFluidBusPartition((PartFluidStorageBus) tracker.storageBus, action, partitionSlot, itemStack, tracker);
        } else if (ThaumicEnergisticsIntegration.isModLoaded()) {
            ThaumicEnergisticsIntegration.handleEssentiaStorageBusPartition(tracker.storageBus, action, partitionSlot, itemStack);
            tracker.hostTile.markDirty();

            return true;
        }

        return false;
    }

    private static boolean handleItemBusPartition(PartStorageBus bus,
                                                   PacketStorageBusPartitionAction.Action action,
                                                   int partitionSlot, ItemStack itemStack,
                                                   StorageBusTracker tracker) {
        IItemHandler configInv = bus.getInventoryByName("config");
        if (configInv == null) return false;

        int slotsToUse = StorageBusInfo.calculateAvailableSlots(bus.getInstalledUpgrades(Upgrades.CAPACITY));
        executeItemPartitionAction(configInv, slotsToUse, action, partitionSlot, itemStack, bus);
        tracker.hostTile.markDirty();

        return true;
    }

    private static boolean handleFluidBusPartition(PartFluidStorageBus bus,
                                                    PacketStorageBusPartitionAction.Action action,
                                                    int partitionSlot, ItemStack itemStack,
                                                    StorageBusTracker tracker) {
        IFluidHandler configInv = bus.getFluidInventoryByName("config");
        if (configInv == null) return false;

        int slotsToUse = StorageBusInfo.calculateAvailableSlots(bus.getInstalledUpgrades(Upgrades.CAPACITY));
        FluidStack fluid = extractFluidFromItem(itemStack);
        executeFluidPartitionAction(configInv, slotsToUse, action, partitionSlot, fluid, bus);
        tracker.hostTile.markDirty();

        return true;
    }

    private static void executeItemPartitionAction(IItemHandler configInv, int slotsToUse,
                                                    PacketStorageBusPartitionAction.Action action,
                                                    int partitionSlot, ItemStack itemStack,
                                                    PartStorageBus bus) {
        switch (action) {
            case ADD_ITEM:
                if (partitionSlot >= 0 && partitionSlot < slotsToUse && !itemStack.isEmpty()) {
                    ItemHandlerUtil.setStackInSlot(configInv, partitionSlot, itemStack.copy());
                }
                break;
            case REMOVE_ITEM:
                if (partitionSlot >= 0 && partitionSlot < slotsToUse) {
                    ItemHandlerUtil.setStackInSlot(configInv, partitionSlot, ItemStack.EMPTY);
                }
                break;
            case TOGGLE_ITEM:
                if (!itemStack.isEmpty()) {
                    int existing = findItemInConfig(configInv, itemStack, slotsToUse);
                    if (existing >= 0) {
                        ItemHandlerUtil.setStackInSlot(configInv, existing, ItemStack.EMPTY);
                    } else {
                        int empty = findEmptySlot(configInv, slotsToUse);
                        if (empty >= 0) ItemHandlerUtil.setStackInSlot(configInv, empty, itemStack.copy());
                    }
                }
                break;
            case SET_ALL_FROM_CONTENTS:
                clearConfig(configInv, slotsToUse);
                TileEntity hostTile = bus.getHost().getTile();
                TileEntity targetTile = hostTile.getWorld().getTileEntity(
                    hostTile.getPos().offset(bus.getSide().getFacing()));

                if (targetTile != null) {
                    EnumFacing targetSide = bus.getSide().getFacing().getOpposite();

                    // Try Storage Drawers IItemRepository first
                    List<StorageDrawersIntegration.ItemRecordData> repoContents =
                        StorageDrawersIntegration.tryGetItemRepositoryContents(targetTile, targetSide);

                    if (repoContents != null) {
                        int slot = 0;
                        for (StorageDrawersIntegration.ItemRecordData record : repoContents) {
                            if (slot >= slotsToUse) break;
                            ItemStack configStack = record.itemPrototype.copy();
                            configStack.setCount(1);
                            ItemHandlerUtil.setStackInSlot(configInv, slot++, configStack);
                        }
                    } else {
                        // Fall back to standard IItemHandler
                        IItemHandler targetHandler = targetTile.getCapability(
                            CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, targetSide);

                        if (targetHandler != null) {
                            int slot = 0;
                            for (int i = 0; i < targetHandler.getSlots() && slot < slotsToUse; i++) {
                                ItemStack slotStack = targetHandler.getStackInSlot(i);
                                if (slotStack.isEmpty()) continue;

                                // Check if this item type is already in config
                                if (findItemInConfig(configInv, slotStack, slot) < 0) {
                                    ItemStack configStack = slotStack.copy();
                                    configStack.setCount(1);
                                    ItemHandlerUtil.setStackInSlot(configInv, slot++, configStack);
                                }
                            }
                        }
                    }
                }
                break;
            case CLEAR_ALL:
                clearConfig(configInv, slotsToUse);
                break;
        }
    }

    private static void executeFluidPartitionAction(IFluidHandler configInv, int slotsToUse,
                                                     PacketStorageBusPartitionAction.Action action,
                                                     int partitionSlot, FluidStack fluid,
                                                     PartFluidStorageBus bus) {
        IAEFluidTank aeConfig = (configInv instanceof IAEFluidTank)
            ? (IAEFluidTank) configInv : null;
        if (aeConfig == null) return;

        // Normalize fluid to remove NBT tags that could cause comparison issues
        FluidStack normalizedFluid = normalizeFluid(fluid);

        switch (action) {
            case ADD_ITEM:
                if (partitionSlot >= 0 && partitionSlot < slotsToUse && normalizedFluid != null) {
                    setFluidSlot(aeConfig, partitionSlot, normalizedFluid);
                }
                break;
            case REMOVE_ITEM:
                if (partitionSlot >= 0 && partitionSlot < slotsToUse) {
                    aeConfig.setFluidInSlot(partitionSlot, null);
                }
                break;
            case TOGGLE_ITEM:
                if (normalizedFluid != null) {
                    int existing = findFluidInConfig(configInv, normalizedFluid, slotsToUse);
                    if (existing >= 0) {
                        aeConfig.setFluidInSlot(existing, null);
                    } else {
                        int empty = findEmptyFluidSlot(configInv, slotsToUse);
                        if (empty >= 0) setFluidSlot(aeConfig, empty, normalizedFluid);
                    }
                }
                break;
            case SET_ALL_FROM_CONTENTS:
                for (int i = 0; i < slotsToUse; i++) aeConfig.setFluidInSlot(i, null);
                TileEntity fluidHostTile = bus.getHost().getTile();
                TileEntity fluidTargetTile = fluidHostTile.getWorld().getTileEntity(
                    fluidHostTile.getPos().offset(bus.getSide().getFacing()));

                if (fluidTargetTile != null) {
                    IFluidHandler targetFluidHandler = fluidTargetTile.getCapability(
                        CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
                        bus.getSide().getFacing().getOpposite());

                    if (targetFluidHandler != null) {
                        IFluidTankProperties[] tanks = targetFluidHandler.getTankProperties();
                        int slot = 0;

                        for (IFluidTankProperties tank : tanks) {
                            if (slot >= slotsToUse) break;

                            FluidStack tankFluid = tank.getContents();
                            if (tankFluid == null || tankFluid.amount <= 0) continue;

                            FluidStack normalized = normalizeFluid(tankFluid);

                            // Check if this fluid type is already in config
                            if (findFluidInConfig(aeConfig, normalized, slot) < 0) {
                                setFluidSlot(aeConfig, slot++, normalized);
                            }
                        }
                    }
                }
                break;
            case CLEAR_ALL:
                for (int i = 0; i < slotsToUse; i++) aeConfig.setFluidInSlot(i, null);
                break;
        }
    }

    /**
     * Normalize a FluidStack to ensure consistent comparison.
     * Creates a new FluidStack with only the fluid type (no NBT tag, standard amount).
     */
    private static FluidStack normalizeFluid(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) return null;

        return new FluidStack(fluid.getFluid(), net.minecraftforge.fluids.Fluid.BUCKET_VOLUME);
    }

    private static void setFluidSlot(IAEFluidTank config, int slot, FluidStack fluid) {
        IAEFluidStack aeFluid = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class).createStack(fluid);
        config.setFluidInSlot(slot, aeFluid);
    }

    private static FluidStack extractFluidFromItem(ItemStack itemStack) {
        if (itemStack.isEmpty()) return null;
        if (itemStack.getItem() instanceof FluidDummyItem) {
            return ((FluidDummyItem) itemStack.getItem()).getFluidStack(itemStack);
        }

        return FluidUtil.getFluidContained(itemStack);
    }

    private static int findItemInConfig(IItemHandler inv, ItemStack stack, int limit) {
        for (int i = 0; i < inv.getSlots() && i < limit; i++) {
            ItemStack slotStack = inv.getStackInSlot(i);
            // Compare by item and NBT only, not count (config slots have count=1, content items may differ)
            if (ItemStack.areItemsEqual(slotStack, stack) && ItemStack.areItemStackTagsEqual(slotStack, stack)) {
                return i;
            }
        }

        return -1;
    }

    private static int findEmptySlot(IItemHandler inv, int limit) {
        for (int i = 0; i < inv.getSlots() && i < limit; i++) {
            if (inv.getStackInSlot(i).isEmpty()) return i;
        }

        return -1;
    }

    private static void clearConfig(IItemHandler inv, int limit) {
        for (int i = 0; i < inv.getSlots() && i < limit; i++) {
            ItemHandlerUtil.setStackInSlot(inv, i, ItemStack.EMPTY);
        }
    }

    private static int findFluidInConfig(IFluidHandler config, FluidStack fluid, int limit) {
        if (fluid == null || fluid.getFluid() == null) return -1;

        IFluidTankProperties[] tanks = config.getTankProperties();
        for (int i = 0; i < tanks.length && i < limit; i++) {
            FluidStack contents = tanks[i].getContents();

            if (contents != null && contents.getFluid() == fluid.getFluid()) return i;
        }

        return -1;
    }

    private static int findEmptyFluidSlot(IFluidHandler config, int limit) {
        IFluidTankProperties[] tanks = config.getTankProperties();
        for (int i = 0; i < tanks.length && i < limit; i++) {
            FluidStack contents = tanks[i].getContents();
            if (contents == null || contents.amount == 0) return i;
        }

        return -1;
    }

    /**
     * Toggle IO mode for a storage bus.
     * @return true if mode was changed
     */
    public static boolean toggleIOMode(StorageBusTracker tracker) {
        if (!(tracker.storageBus instanceof PartUpgradeable)) return false;

        IConfigManager configManager =
            ((PartUpgradeable) tracker.storageBus).getConfigManager();
        if (configManager == null) return false;

        AccessRestriction current = (AccessRestriction) configManager.getSetting(Settings.ACCESS);
        AccessRestriction next;

        switch (current) {
            case READ_WRITE: next = AccessRestriction.READ; break;
            case READ: next = AccessRestriction.WRITE; break;
            default: next = AccessRestriction.READ_WRITE;
        }

        configManager.putSetting(Settings.ACCESS, next);
        tracker.hostTile.markDirty();

        return true;
    }

    /**
     * Insert an upgrade into a storage bus.
     * @return true if upgrade was inserted
     */
    public static boolean insertUpgrade(StorageBusTracker tracker, ItemStack upgradeStack) {
        if (!(tracker.storageBus instanceof PartUpgradeable)) return false;

        IItemHandler upgradesInv = ((PartUpgradeable) tracker.storageBus).getInventoryByName("upgrades");
        if (upgradesInv == null) return false;

        ItemStack toInsert = upgradeStack.copy();
        toInsert.setCount(1);

        for (int slot = 0; slot < upgradesInv.getSlots(); slot++) {
            ItemStack remainder = upgradesInv.insertItem(slot, toInsert, false);
            if (remainder.isEmpty()) {
                upgradeStack.shrink(1);

                return true;
            }
        }

        return false;
    }

    private static ItemStack getBlockAsItemStack(IBlockState state, World world, BlockPos pos) {
        if (state == null || state.getBlock() == null) return ItemStack.EMPTY;

        Item item = Item.getItemFromBlock(state.getBlock());
        if (item != null && item != Items.AIR) {
            int meta = state.getBlock().damageDropped(state);

            return new ItemStack(item, 1, meta);
        }

        try {
            int meta = state.getBlock().getMetaFromState(state);
            ItemStack stack = new ItemStack(state.getBlock(), 1, meta);
            if (!stack.isEmpty()) return stack;
        } catch (Exception e) {
            // Ignore
        }

        try {
            ItemStack stack = new ItemStack(state.getBlock());
            if (!stack.isEmpty()) return stack;
        } catch (Exception e) {
            // Ignore
        }

        return ItemStack.EMPTY;
    }
}
