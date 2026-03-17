package com.cellterminal.integration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IItemList;
import appeng.util.helpers.ItemHandlerUtil;

import com.cellterminal.CellTerminal;
import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.client.StorageType;
import com.cellterminal.config.CellTerminalServerConfig;
import com.cellterminal.network.PacketStorageBusPartitionAction;
import com.cellterminal.util.BigStackTracker;


/**
 * Integration handler for MekanismEnergistics mod.
 * Provides support for Gas cells and gas storage buses in the Cell Terminal.
 * All MekanismEnergistics interactions are wrapped in @Optional to prevent class loading issues.
 */
public class MekanismEnergisticsIntegration {

    private static final String MODID = "mekeng";
    private static Boolean modLoaded = null;

    /**
     * Check if MekanismEnergistics is loaded.
     */
    public static boolean isModLoaded() {
        if (modLoaded == null) {
            boolean loaded = Loader.isModLoaded(MODID);
            if (loaded && CellTerminalServerConfig.isInitialized()) {
                loaded = CellTerminalServerConfig.getInstance().isIntegrationMekanismEnergisticsEnabled();
            }
            modLoaded = loaded;
        }

        return modLoaded;
    }

    // ========================================
    // Cell Data Population
    // ========================================

    /**
     * Try to get cell data from a Gas cell.
     * Returns null if not a Gas cell or if MekanismEnergistics is not loaded.
     * @param cellHandler The cell handler
     * @param cellStack The cell ItemStack
     * @param slotLimit Maximum number of item types to include
     * @return NBT data for the cell, or null if not a Gas cell
     */
    public static NBTTagCompound tryPopulateGasCell(ICellHandler cellHandler, ItemStack cellStack,
                                                     int slotLimit) {
        if (!isModLoaded()) return null;

        return tryPopulateGasCellInternal(cellHandler, cellStack, slotLimit);
    }

    @Optional.Method(modid = MODID)
    private static NBTTagCompound tryPopulateGasCellInternal(ICellHandler cellHandler, ItemStack cellStack,
                                                              int slotLimit) {
        try {
            IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
                AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);

            if (gasChannel == null) return null;

            ICellInventoryHandler<com.mekeng.github.common.me.data.IAEGasStack> gasCellHandler =
                cellHandler.getCellInventory(cellStack, null, gasChannel);

            if (gasCellHandler == null) return null;

            ICellInventory<com.mekeng.github.common.me.data.IAEGasStack> cellInv = gasCellHandler.getCellInv();
            if (cellInv == null) return null;

            NBTTagCompound cellData = new NBTTagCompound();

            // Mark as Gas cell
            StorageType.GAS.writeToNBT(cellData);

            // Basic cell stats
            cellData.setLong("usedBytes", cellInv.getUsedBytes());
            cellData.setLong("totalBytes", cellInv.getTotalBytes());
            cellData.setLong("usedTypes", cellInv.getStoredItemTypes());
            cellData.setLong("totalTypes", cellInv.getTotalItemTypes());
            cellData.setLong("storedItemCount", cellInv.getStoredItemCount());

            // Get partition (config inventory) - Gas cells use dummy gas items
            IItemHandler configInv = cellInv.getConfigInventory();
            if (configInv != null) {
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

            // Get stored gas (contents preview) - convert to ItemStack representation (DummyGas)
            IItemList<com.mekeng.github.common.me.data.IAEGasStack> contents =
                cellInv.getAvailableItems(gasChannel.createList());

            NBTTagList contentsList = new NBTTagList();
            int count = 0;
            for (com.mekeng.github.common.me.data.IAEGasStack stack : contents) {
                if (count >= slotLimit) break;

                // Convert to ItemStack representation (DummyGas) for client display
                ItemStack itemRep = stack.asItemStackRepresentation();
                if (!itemRep.isEmpty()) {
                    NBTTagCompound stackNbt = new NBTTagCompound();
                    itemRep.writeToNBT(stackNbt);
                    // Store the gas amount for display purposes
                    stackNbt.setLong("gasAmount", stack.getStackSize());
                    contentsList.appendTag(stackNbt);
                    count++;
                }
            }

            cellData.setTag("contents", contentsList);

            // Get upgrades
            IItemHandler upgradesInv = cellInv.getUpgradesInventory();
            if (upgradesInv != null) {
                NBTTagList upgradeList = new NBTTagList();
                for (int i = 0; i < upgradesInv.getSlots(); i++) {
                    ItemStack upgrade = upgradesInv.getStackInSlot(i);
                    if (upgrade.isEmpty()) continue;

                    NBTTagCompound upgradeNbt = new NBTTagCompound();
                    upgrade.writeToNBT(upgradeNbt);
                    upgradeNbt.setInteger("slot", i);
                    upgradeList.appendTag(upgradeNbt);
                }
                cellData.setTag("upgrades", upgradeList);
            }

            return cellData;
        } catch (Exception e) {
            CellTerminal.LOGGER.debug("Failed to get Gas cell data: {}", e.getMessage());

            return null;
        }
    }

    // ========================================
    // Cell Inventory Access
    // ========================================

    /**
     * Try to get the Gas cell inventory handler for partition modifications.
     * Returns an object array: [configInv, gasChannel, gasCellHandler] or null if not a Gas cell.
     */
    public static Object[] tryGetGasConfigInventory(ICellHandler cellHandler, ItemStack cellStack) {
        if (!isModLoaded()) return null;

        return tryGetGasConfigInventoryInternal(cellHandler, cellStack);
    }

    @Optional.Method(modid = MODID)
    private static Object[] tryGetGasConfigInventoryInternal(ICellHandler cellHandler, ItemStack cellStack) {
        try {
            IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
                AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);

            if (gasChannel == null) return null;

            ICellInventoryHandler<com.mekeng.github.common.me.data.IAEGasStack> gasCellHandler =
                cellHandler.getCellInventory(cellStack, null, gasChannel);

            if (gasCellHandler == null || gasCellHandler.getCellInv() == null) return null;

            IItemHandler configInv = gasCellHandler.getCellInv().getConfigInventory();
            if (configInv == null) return null;

            return new Object[] { configInv, gasChannel, gasCellHandler };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if a gas cell is empty and has no partition.
     * @param cellHandler The AE cell handler
     * @param cellStack The cell ItemStack
     * @return true if the cell is empty and has no partition, false otherwise
     */
    public static boolean isGasEmptyAndNonPartitioned(ICellHandler cellHandler, ItemStack cellStack) {
        if (!isModLoaded()) return false;

        return isGasEmptyAndNonPartitionedInternal(cellHandler, cellStack);
    }

    @Optional.Method(modid = MODID)
    private static boolean isGasEmptyAndNonPartitionedInternal(ICellHandler cellHandler, ItemStack cellStack) {
        try {
            IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
                AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);

            if (gasChannel == null) return false;

            ICellInventoryHandler<com.mekeng.github.common.me.data.IAEGasStack> handler =
                cellHandler.getCellInventory(cellStack, null, gasChannel);

            if (handler == null || handler.getCellInv() == null) return false;

            ICellInventory<com.mekeng.github.common.me.data.IAEGasStack> cellInv = handler.getCellInv();
            boolean isEmpty = cellInv.getUsedBytes() == 0;

            net.minecraftforge.items.IItemHandler configInv = cellInv.getConfigInventory();
            boolean hasPartition = false;
            if (configInv != null) {
                for (int i = 0; i < configInv.getSlots(); i++) {
                    if (!configInv.getStackInSlot(i).isEmpty()) {
                        hasPartition = true;
                        break;
                    }
                }
            }

            return isEmpty && !hasPartition;
        } catch (Exception e) {
            return false;
        }
    }

    // ========================================
    // Cell Partition Operations
    // ========================================

    /**
     * Set all partition slots from cell contents for Gas cells.
     */
    public static void setAllFromGasContents(net.minecraftforge.items.IItemHandler configInv, Object[] gasData) {
        if (!isModLoaded() || gasData == null || gasData.length < 3) return;

        setAllFromGasContentsInternal(configInv, gasData);
    }

    @Optional.Method(modid = MODID)
    @SuppressWarnings("unchecked")
    private static void setAllFromGasContentsInternal(net.minecraftforge.items.IItemHandler configInv, Object[] gasData) {
        try {
            IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
                (IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack>) gasData[1];
            ICellInventoryHandler<com.mekeng.github.common.me.data.IAEGasStack> gasCellHandler =
                (ICellInventoryHandler<com.mekeng.github.common.me.data.IAEGasStack>) gasData[2];

            if (gasCellHandler.getCellInv() == null) return;

            IItemList<com.mekeng.github.common.me.data.IAEGasStack> contents =
                gasCellHandler.getCellInv().getAvailableItems(gasChannel.createList());

            int slot = 0;
            for (com.mekeng.github.common.me.data.IAEGasStack stack : contents) {
                if (slot >= configInv.getSlots()) break;

                // Normalize gas to standard 1000 mB amount for consistent partition display
                ItemStack gasRep = normalizeGasStackInternal(stack.asItemStackRepresentation());
                ItemHandlerUtil.setStackInSlot(configInv, slot++, gasRep);
            }
        } catch (Exception e) {
            CellTerminal.LOGGER.debug("Failed to set Gas partition from contents: {}", e.getMessage());
        }
    }

    // ========================================
    // Cell Content Operations (BigStackTracker)
    // ========================================

    /**
     * Collect unique gas stacks from a cell.
     * @param cellStack The gas cell ItemStack
     * @param uniqueKeys Set to track unique keys (will be modified)
     * @param uniqueStacks List to add unique ItemStack representations to (will be modified)
     */
    public static void collectUniqueGasFromCell(ItemStack cellStack,
                                                Set<Object> uniqueKeys,
                                                List<ItemStack> uniqueStacks) {
        if (!isModLoaded()) return;

        collectUniqueGasFromCellInternal(cellStack, uniqueKeys, uniqueStacks);
    }

    @Optional.Method(modid = MODID)
    private static void collectUniqueGasFromCellInternal(ItemStack cellStack,
                                                         Set<Object> uniqueKeys,
                                                         List<ItemStack> uniqueStacks) {
        try {
            IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
                AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);
            if (gasChannel == null) return;

            ICellHandler cellHandler = AEApi.instance().registries().cell().getHandler(cellStack);
            if (cellHandler == null) return;

            ICellInventoryHandler<com.mekeng.github.common.me.data.IAEGasStack> handler =
                cellHandler.getCellInventory(cellStack, null, gasChannel);
            if (handler == null || handler.getCellInv() == null) return;

            IItemList<com.mekeng.github.common.me.data.IAEGasStack> available =
                handler.getCellInv().getAvailableItems(gasChannel.createList());

            for (com.mekeng.github.common.me.data.IAEGasStack stack : available) {
                mekanism.api.gas.Gas gas = stack.getGas();
                if (gas == null) continue;

                String key = "gas:" + gas.getName();
                if (uniqueKeys.add(key)) uniqueStacks.add(stack.asItemStackRepresentation());
            }
        } catch (Exception e) {
            CellTerminal.LOGGER.debug("Failed to collect unique gas from cell: {}", e.getMessage());
        }
    }

    /**
     * Extract all gas from a cell for AttributeUnique tool.
     * Uses BigStackTracker for lossless aggregation.
     *
     * @param cellStack The gas cell ItemStack
     * @param tracker BigStackTracker to store extracted stacks (will be modified)
     */
    public static void extractAllGasFromCellToBigTracker(ItemStack cellStack,
                                                          BigStackTracker tracker) {
        if (!isModLoaded()) return;

        extractAllGasFromCellToBigTrackerInternal(cellStack, tracker);
    }

    @Optional.Method(modid = MODID)
    private static void extractAllGasFromCellToBigTrackerInternal(ItemStack cellStack,
                                                                    BigStackTracker tracker) {
        try {
            IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
                AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);
            if (gasChannel == null) return;

            ICellHandler cellHandler = AEApi.instance().registries().cell().getHandler(cellStack);
            if (cellHandler == null) return;

            ICellInventoryHandler<com.mekeng.github.common.me.data.IAEGasStack> handler =
                cellHandler.getCellInventory(cellStack, null, gasChannel);
            if (handler == null || handler.getCellInv() == null) return;

            ICellInventory<com.mekeng.github.common.me.data.IAEGasStack> cellInv = handler.getCellInv();
            IItemList<com.mekeng.github.common.me.data.IAEGasStack> available =
                cellInv.getAvailableItems(gasChannel.createList());

            for (com.mekeng.github.common.me.data.IAEGasStack stack : available) {
                com.mekeng.github.common.me.data.IAEGasStack extracted =
                    cellInv.extractItems(stack.copy(), Actionable.MODULATE, null);
                if (extracted == null || extracted.getStackSize() <= 0) continue;

                mekanism.api.gas.Gas gas = extracted.getGas();
                if (gas == null) continue;

                String key = "gas:" + gas.getName();
                // Add to tracker using BigInteger arithmetic (no overflow possible)
                tracker.add(key, extracted);
            }

            cellInv.persist();
        } catch (Exception e) {
            CellTerminal.LOGGER.debug("Failed to extract gas from cell: {}", e.getMessage());
        }
    }

    /**
     * Inject a gas stack into a cell.
     * @param cellStack The gas cell ItemStack
     * @param gasStack The gas stack to inject (must be IAEGasStack)
     */
    public static void injectGasIntoCell(ItemStack cellStack, Object gasStack) {
        if (!isModLoaded()) return;

        injectGasIntoCellInternal(cellStack, gasStack);
    }

    @Optional.Method(modid = MODID)
    private static void injectGasIntoCellInternal(ItemStack cellStack, Object gasStack) {
        try {
            if (!(gasStack instanceof com.mekeng.github.common.me.data.IAEGasStack)) return;

            IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
                AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);
            if (gasChannel == null) return;

            ICellHandler cellHandler = AEApi.instance().registries().cell().getHandler(cellStack);
            if (cellHandler == null) return;

            ICellInventoryHandler<com.mekeng.github.common.me.data.IAEGasStack> handler =
                cellHandler.getCellInventory(cellStack, null, gasChannel);
            if (handler == null || handler.getCellInv() == null) return;

            ICellInventory<com.mekeng.github.common.me.data.IAEGasStack> cellInv = handler.getCellInv();
            cellInv.injectItems((com.mekeng.github.common.me.data.IAEGasStack) gasStack,
                Actionable.MODULATE, null);
            cellInv.persist();
        } catch (Exception e) {
            CellTerminal.LOGGER.debug("Failed to inject gas into cell: {}", e.getMessage());
        }
    }

    /**
     * Get an ItemStack representation for partition from a gas stack.
     * @param gasStack The gas stack object (IAEGasStack)
     * @return ItemStack representation for partition, or empty if invalid
     */
    public static ItemStack getGasPartitionItem(Object gasStack) {
        if (!isModLoaded()) return ItemStack.EMPTY;

        return getGasPartitionItemInternal(gasStack);
    }

    @Optional.Method(modid = MODID)
    private static ItemStack getGasPartitionItemInternal(Object gasStack) {
        try {
            if (!(gasStack instanceof com.mekeng.github.common.me.data.IAEGasStack)) return ItemStack.EMPTY;

            return ((com.mekeng.github.common.me.data.IAEGasStack) gasStack).asItemStackRepresentation();
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    // ========================================
    // Simulation & Capacity
    // ========================================

    /**
     * Simulate gas injection into a cell to check capacity.
     * @param cellStack The gas cell ItemStack
     * @param stack The stack to simulate (must be IAEGasStack)
     * @param source The action source
     * @return Amount that can be accepted, 0 if incompatible or full
     */
    public static long simulateGasInjection(ItemStack cellStack, Object stack, Object source) {
        if (!isModLoaded()) return 0;

        return simulateGasInjectionInternal(cellStack, stack, source);
    }

    @Optional.Method(modid = MODID)
    private static long simulateGasInjectionInternal(ItemStack cellStack, Object stack, Object source) {
        try {
            if (!(stack instanceof com.mekeng.github.common.me.data.IAEGasStack)) return 0;

            IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
                AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);
            if (gasChannel == null) return 0;

            ICellHandler cellHandler = AEApi.instance().registries().cell().getHandler(cellStack);
            if (cellHandler == null) return 0;

            ICellInventoryHandler<com.mekeng.github.common.me.data.IAEGasStack> handler =
                cellHandler.getCellInventory(cellStack, null, gasChannel);
            if (handler == null || handler.getCellInv() == null) return 0;

            ICellInventory<com.mekeng.github.common.me.data.IAEGasStack> cellInv = handler.getCellInv();
            com.mekeng.github.common.me.data.IAEGasStack gasStack =
                (com.mekeng.github.common.me.data.IAEGasStack) stack;

            // Check if cell can hold a new type
            if (!cellInv.canHoldNewItem()) {
                IItemList<com.mekeng.github.common.me.data.IAEGasStack> existing =
                    cellInv.getAvailableItems(gasChannel.createList());
                boolean typeExists = false;
                mekanism.api.gas.Gas targetGas = gasStack.getGas();
                for (com.mekeng.github.common.me.data.IAEGasStack ex : existing) {
                    if (ex.getGas() == targetGas) {
                        typeExists = true;
                        break;
                    }
                }
                if (!typeExists) return 0;
            }

            // Simulate injection
            com.mekeng.github.common.me.data.IAEGasStack rejected =
                cellInv.injectItems(gasStack.copy(), Actionable.SIMULATE, (IActionSource) source);

            if (rejected == null) return gasStack.getStackSize();

            return gasStack.getStackSize() - rejected.getStackSize();
        } catch (Exception e) {
            CellTerminal.LOGGER.debug("Failed to simulate gas injection: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Get the capacity of a gas cell in bytes.
     * @param cellStack The gas cell ItemStack
     * @return Cell capacity in bytes, 0 if not a gas cell
     */
    public static long getGasCellCapacity(ItemStack cellStack) {
        if (!isModLoaded()) return 0;

        return getGasCellCapacityInternal(cellStack);
    }

    @Optional.Method(modid = MODID)
    private static long getGasCellCapacityInternal(ItemStack cellStack) {
        try {
            IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
                AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);
            if (gasChannel == null) return 0;

            ICellHandler cellHandler = AEApi.instance().registries().cell().getHandler(cellStack);
            if (cellHandler == null) return 0;

            ICellInventoryHandler<com.mekeng.github.common.me.data.IAEGasStack> handler =
                cellHandler.getCellInventory(cellStack, null, gasChannel);
            if (handler == null || handler.getCellInv() == null) return 0;

            return handler.getCellInv().getTotalBytes();
        } catch (Exception e) {
            return 0;
        }
    }

    // ========================================
    // Display Names
    // ========================================

    /**
     * Get a display name for a gas stack.
     * @param stack The stack object (IAEGasStack)
     * @return Display name or "Unknown Gas"
     */
    public static String getGasStackName(Object stack) {
        if (!isModLoaded()) return "Unknown Gas";

        return getGasStackNameInternal(stack);
    }

    @Optional.Method(modid = MODID)
    private static String getGasStackNameInternal(Object stack) {
        try {
            if (!(stack instanceof com.mekeng.github.common.me.data.IAEGasStack)) return "Unknown Gas";

            com.mekeng.github.common.me.data.IAEGasStack gasStack =
                (com.mekeng.github.common.me.data.IAEGasStack) stack;
            mekanism.api.gas.Gas gas = gasStack.getGas();

            if (gas != null) return gas.getLocalizedName();
        } catch (Exception e) {
            // Ignored
        }

        return "Unknown Gas";
    }

    // ========================================
    // Collection with Counts
    // ========================================

    /**
     * Collect gas from a cell with full counts for AttributeUnique tool.
     * Uses BigStackTracker for lossless aggregation.
     *
     * @param cellStack The gas cell ItemStack
     * @param uniqueKeys Set to track unique keys (will be modified)
     * @param uniqueStacks List to add unique ItemStack representations (will be modified)
     * @param tracker BigStackTracker to accumulate counts with arbitrary precision
     */
    public static void collectGasWithCounts(ItemStack cellStack,
                                            Set<Object> uniqueKeys,
                                            List<ItemStack> uniqueStacks,
                                            BigStackTracker tracker) {
        if (!isModLoaded()) return;

        collectGasWithCountsInternal(cellStack, uniqueKeys, uniqueStacks, tracker);
    }

    @Optional.Method(modid = MODID)
    private static void collectGasWithCountsInternal(ItemStack cellStack,
                                                    Set<Object> uniqueKeys,
                                                    List<ItemStack> uniqueStacks,
                                                    BigStackTracker tracker) {
        try {
            IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
                AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);
            if (gasChannel == null) return;

            ICellHandler cellHandler = AEApi.instance().registries().cell().getHandler(cellStack);
            if (cellHandler == null) return;

            ICellInventoryHandler<com.mekeng.github.common.me.data.IAEGasStack> handler =
                cellHandler.getCellInventory(cellStack, null, gasChannel);
            if (handler == null || handler.getCellInv() == null) return;

            IItemList<com.mekeng.github.common.me.data.IAEGasStack> available =
                handler.getCellInv().getAvailableItems(gasChannel.createList());

            for (com.mekeng.github.common.me.data.IAEGasStack stack : available) {
                mekanism.api.gas.Gas gas = stack.getGas();
                if (gas == null) continue;

                String key = "gas:" + gas.getName();

                // Track unique types for UI display
                if (!tracker.containsKey(key)) {
                    uniqueKeys.add(key);
                    uniqueStacks.add(stack.asItemStackRepresentation());
                }

                // Add to tracker using BigInteger arithmetic (no overflow possible)
                tracker.add(key, stack);
            }
        } catch (Exception e) {
            CellTerminal.LOGGER.debug("Failed to collect gas with counts from cell: {}", e.getMessage());
        }
    }

    // ========================================
    // Type Checking
    // ========================================

    /**
     * Check if a stack object is an IAEGasStack.
     * @param stack The object to check
     * @return true if it's a gas stack
     */
    public static boolean isGasStack(Object stack) {
        if (!isModLoaded()) return false;

        return isGasStackInternal(stack);
    }

    @Optional.Method(modid = MODID)
    private static boolean isGasStackInternal(Object stack) {
        return stack instanceof com.mekeng.github.common.me.data.IAEGasStack;
    }

    // ========================================
    // Item Conversion (for JEI / partition GUI)
    // ========================================

    /**
     * Try to convert an ItemStack containing gas to its DummyGas representation.
     * This is used for partition GUI when dragging gas containers onto gas cells.
     *
     * @param itemStack The item that may contain gas (gas tank, etc.)
     * @return The DummyGas ItemStack representation, or ItemStack.EMPTY if not a gas container
     */
    public static ItemStack tryConvertGasContainerToGas(ItemStack itemStack) {
        if (!isModLoaded()) return ItemStack.EMPTY;

        return tryConvertGasContainerToGasInternal(itemStack);
    }

    @Optional.Method(modid = MODID)
    private static ItemStack tryConvertGasContainerToGasInternal(ItemStack itemStack) {
        try {
            // If already a DummyGas item, return as-is
            if (itemStack.getItem() instanceof com.mekeng.github.common.item.ItemDummyGas) {
                return itemStack;
            }

            mekanism.api.gas.GasStack gasContained = null;

            // Try IGasItem interface first (Mekanism gas tanks, creative tanks, etc.)
            // Mekanism items store gas via the IGasItem interface on the Item class,
            // NOT via the GAS_HANDLER_CAPABILITY (which is only for TileEntities).
            if (itemStack.getItem() instanceof mekanism.api.gas.IGasItem) {
                mekanism.api.gas.IGasItem gasItem = (mekanism.api.gas.IGasItem) itemStack.getItem();
                gasContained = gasItem.getGas(itemStack);
            }

            // Fallback: check GAS_HANDLER_CAPABILITY (for modded items that use capabilities)
            if ((gasContained == null || gasContained.amount <= 0)
                    && itemStack.hasCapability(mekanism.common.capabilities.Capabilities.GAS_HANDLER_CAPABILITY, null)) {
                mekanism.api.gas.IGasHandler gasHandler =
                    itemStack.getCapability(mekanism.common.capabilities.Capabilities.GAS_HANDLER_CAPABILITY, null);

                if (gasHandler != null) {
                    gasContained = gasHandler.drawGas(EnumFacing.UP, Integer.MAX_VALUE, false);
                }
            }

            if (gasContained == null || gasContained.amount <= 0 || gasContained.getGas() == null) {
                return ItemStack.EMPTY;
            }

            // Normalize to standard 1000 mB amount for consistent partition display
            mekanism.api.gas.GasStack normalizedGas = new mekanism.api.gas.GasStack(gasContained.getGas(), 1000);

            // Create an IAEGasStack and get its item representation (DummyGas)
            IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
                AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);

            if (gasChannel == null) return ItemStack.EMPTY;

            com.mekeng.github.common.me.data.IAEGasStack aeGasStack =
                gasChannel.createStack(normalizedGas);

            if (aeGasStack == null) return ItemStack.EMPTY;

            return aeGasStack.asItemStackRepresentation();
        } catch (Exception e) {
            CellTerminal.LOGGER.debug("Failed to convert gas container to gas: {}", e.getMessage());

            return ItemStack.EMPTY;
        }
    }

    /**
     * Normalize a gas ItemStack to ensure consistent partition comparison.
     * Creates a clean DummyGas ItemStack with only the gas type and a standard amount (1000 mB).
     * This is the gas equivalent of CellActionHandler.normalizeFluidStack().
     *
     * @param stack The gas ItemStack to normalize (DummyGas, gas tank, or any gas container)
     * @return A normalized DummyGas ItemStack with 1000 mB amount, or the original stack if not gas
     */
    public static ItemStack normalizeGasStack(ItemStack stack) {
        if (!isModLoaded() || stack.isEmpty()) return stack;

        return normalizeGasStackInternal(stack);
    }

    @Optional.Method(modid = MODID)
    private static ItemStack normalizeGasStackInternal(ItemStack stack) {
        try {
            mekanism.api.gas.GasStack gasStack = getGasFromItem(stack);
            if (gasStack == null || gasStack.getGas() == null) return stack;

            // Create a clean GasStack with just the gas type and a standard 1000 mB amount
            mekanism.api.gas.GasStack normalized = new mekanism.api.gas.GasStack(gasStack.getGas(), 1000);

            IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
                AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);
            if (gasChannel == null) return stack;

            com.mekeng.github.common.me.data.IAEGasStack aeStack = gasChannel.createStack(normalized);
            if (aeStack == null) return stack;

            return aeStack.asItemStackRepresentation();
        } catch (Exception e) {
            CellTerminal.LOGGER.debug("Failed to normalize gas stack: {}", e.getMessage());

            return stack;
        }
    }

    /**
     * Find a gas in a cell config inventory (IItemHandler), comparing by gas type only.
     * This is the gas equivalent of CellActionHandler.findFluidInConfig().
     *
     * @param inv The config inventory
     * @param stack The gas ItemStack to find
     * @return The slot index of the matching gas, or -1 if not found
     */
    public static int findGasInCellConfig(IItemHandler inv, ItemStack stack) {
        if (!isModLoaded()) return -1;

        return findGasInCellConfigInternal(inv, stack);
    }

    @Optional.Method(modid = MODID)
    private static int findGasInCellConfigInternal(IItemHandler inv, ItemStack stack) {
        mekanism.api.gas.GasStack targetGas = getGasFromItem(stack);
        if (targetGas == null || targetGas.getGas() == null) return -1;

        for (int i = 0; i < inv.getSlots(); i++) {
            mekanism.api.gas.GasStack slotGas = getGasFromItem(inv.getStackInSlot(i));

            if (slotGas != null && slotGas.getGas() == targetGas.getGas()) return i;
        }

        return -1;
    }

    /**
     * Check if an object is a gas-related JEI ingredient.
     * This includes MekanismEnergistics IAEGasStack or Mekanism GasStack objects.
     *
     * @param ingredient The JEI ingredient to check
     * @return true if the ingredient represents gas
     */
    public static boolean isGasIngredient(Object ingredient) {
        if (!isModLoaded() || ingredient == null) return false;

        return isGasIngredientInternal(ingredient);
    }

    @Optional.Method(modid = MODID)
    private static boolean isGasIngredientInternal(Object ingredient) {
        try {
            // Check for IAEGasStack
            if (ingredient instanceof com.mekeng.github.common.me.data.IAEGasStack) return true;

            // Check for Mekanism GasStack
            if (ingredient instanceof mekanism.api.gas.GasStack) return true;

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Try to convert a JEI ingredient to a gas ItemStack representation.
     * Handles IAEGasStack and Mekanism GasStack objects.
     *
     * @param ingredient The JEI ingredient to convert
     * @return ItemStack representation of the gas, or empty if not gas
     */
    public static ItemStack tryConvertJeiIngredientToGas(Object ingredient) {
        if (!isModLoaded() || ingredient == null) return ItemStack.EMPTY;

        return tryConvertJeiIngredientToGasInternal(ingredient);
    }

    @Optional.Method(modid = MODID)
    private static ItemStack tryConvertJeiIngredientToGasInternal(Object ingredient) {
        try {
            IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
                AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);

            // Handle IAEGasStack directly
            if (ingredient instanceof com.mekeng.github.common.me.data.IAEGasStack) {
                com.mekeng.github.common.me.data.IAEGasStack gasStack =
                    (com.mekeng.github.common.me.data.IAEGasStack) ingredient;

                // Normalize to standard 1000 mB amount
                return normalizeGasStackInternal(gasStack.asItemStackRepresentation());
            }

            // Handle Mekanism GasStack
            if (ingredient instanceof mekanism.api.gas.GasStack) {
                mekanism.api.gas.GasStack mekanismGasStack = (mekanism.api.gas.GasStack) ingredient;
                com.mekeng.github.common.me.data.IAEGasStack aeGasStack =
                    gasChannel.createStack(mekanismGasStack);

                if (aeGasStack != null) {
                    // Normalize to standard 1000 mB amount
                    return normalizeGasStackInternal(aeGasStack.asItemStackRepresentation());
                }
            }

            return ItemStack.EMPTY;
        } catch (Exception e) {
            CellTerminal.LOGGER.debug("Failed to convert JEI ingredient to gas: {}", e.getMessage());

            return ItemStack.EMPTY;
        }
    }

    // ========================================
    // Storage Bus Support
    // ========================================

    /**
     * Get the PartGasStorageBus class for grid machine iteration.
     * Returns null if MekanismEnergistics is not loaded.
     */
    public static Class<?> getGasStorageBusClass() {
        if (!isModLoaded()) return null;

        return getGasStorageBusClassInternal();
    }

    @Optional.Method(modid = MODID)
    private static Class<?> getGasStorageBusClassInternal() {
        try {
            return com.mekeng.github.common.part.PartGasStorageBus.class;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Create NBT data for a gas storage bus.
     * Returns null if not a gas storage bus or if MekanismEnergistics is not loaded.
     *
     * @param machine The grid machine (should be a PartGasStorageBus)
     * @param busId The unique bus ID
     * @return NBT data for the storage bus, or null if not supported
     */
    public static NBTTagCompound tryCreateGasStorageBusData(Object machine, long busId) {
        if (!isModLoaded()) return null;

        return tryCreateGasStorageBusDataInternal(machine, busId);
    }

    @Optional.Method(modid = MODID)
    private static NBTTagCompound tryCreateGasStorageBusDataInternal(Object machine, long busId) {
        try {
            if (!(machine instanceof com.mekeng.github.common.part.PartGasStorageBus)) return null;

            com.mekeng.github.common.part.PartGasStorageBus bus =
                (com.mekeng.github.common.part.PartGasStorageBus) machine;

            TileEntity hostTile = bus.getHost().getTile();
            if (hostTile == null) return null;

            NBTTagCompound busData = new NBTTagCompound();
            busData.setLong("id", busId);
            busData.setLong("pos", hostTile.getPos().toLong());
            busData.setInteger("dim", hostTile.getWorld().provider.getDimension());
            busData.setInteger("side", bus.getSide().ordinal());
            busData.setInteger("priority", bus.getPriority());
            StorageType.GAS.writeToNBT(busData);

            // Gas storage buses have 63 config slots (same as capacity-maxed item/fluid buses)
            busData.setInteger("baseConfigSlots", StorageBusInfo.MAX_CONFIG_SLOTS);
            busData.setInteger("slotsPerUpgrade", 0);
            busData.setInteger("maxConfigSlots", StorageBusInfo.MAX_CONFIG_SLOTS);

            // Access restriction from config manager
            AccessRestriction access = (AccessRestriction)
                bus.getConfigManager().getSetting(Settings.ACCESS);
            int accessValue = 3; // READ_WRITE default
            if (access == AccessRestriction.READ) accessValue = 1;
            else if (access == AccessRestriction.WRITE) accessValue = 2;
            else if (access == AccessRestriction.NO_ACCESS) accessValue = 0;
            busData.setInteger("access", accessValue);

            // Connected inventory info - check for IGasHandler capability
            EnumFacing targetSide = bus.getSide().getFacing().getOpposite();
            TileEntity targetTile = hostTile.getWorld().getTileEntity(
                hostTile.getPos().offset(bus.getSide().getFacing()));

            if (targetTile != null &&
                targetTile.hasCapability(mekanism.common.capabilities.Capabilities.GAS_HANDLER_CAPABILITY, targetSide)) {
                // Get the block as icon
                IBlockState state = hostTile.getWorld().getBlockState(
                    hostTile.getPos().offset(bus.getSide().getFacing()));
                ItemStack blockStack = new ItemStack(state.getBlock(), 1, state.getBlock().getMetaFromState(state));

                if (!blockStack.isEmpty()) {
                    busData.setString("connectedName", blockStack.getDisplayName());

                    NBTTagCompound iconNbt = new NBTTagCompound();
                    blockStack.writeToNBT(iconNbt);
                    busData.setTag("connectedIcon", iconNbt);
                }
            }

            // Get config (partition) from gas inventory
            com.mekeng.github.common.me.inventory.IGasInventory config = bus.getConfig();
            if (config != null) {
                NBTTagList partitionList = new NBTTagList();

                IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
                    AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);

                for (int i = 0; i < config.size(); i++) {
                    NBTTagCompound partNbt = new NBTTagCompound();
                    partNbt.setInteger("slot", i);

                    mekanism.api.gas.GasStack gasStack = config.getGasStack(i);
                    if (gasStack != null && gasStack.getGas() != null) {
                        // Convert gas to DummyGas item for display
                        com.mekeng.github.common.me.data.IAEGasStack aeStack =
                            gasChannel.createStack(gasStack);

                        if (aeStack != null) {
                            ItemStack itemRep = aeStack.asItemStackRepresentation();
                            if (!itemRep.isEmpty()) itemRep.writeToNBT(partNbt);
                        }
                    }

                    partitionList.appendTag(partNbt);
                }

                busData.setTag("partition", partitionList);
            }

            // Get contents from connected IGasHandler
            if (targetTile != null &&
                targetTile.hasCapability(mekanism.common.capabilities.Capabilities.GAS_HANDLER_CAPABILITY, targetSide)) {
                mekanism.api.gas.IGasHandler gasHandler =
                    targetTile.getCapability(mekanism.common.capabilities.Capabilities.GAS_HANDLER_CAPABILITY, targetSide);

                NBTTagList contentsList = new NBTTagList();

                if (gasHandler != null) {
                    int count = 0;

                    IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
                        AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);

                    // IGasHandler uses receiveGas/drawGas pattern - iterate tanks
                    Set<String> seenGases = new HashSet<>();
                    // Draw small amounts to inspect; use simulate mode (false)
                    mekanism.api.gas.GasStack drawn = gasHandler.drawGas(targetSide, Integer.MAX_VALUE, false);
                    if (drawn != null && drawn.amount > 0 && drawn.getGas() != null) {
                        String gasName = drawn.getGas().getName();
                        if (seenGases.add(gasName)) {
                            com.mekeng.github.common.me.data.IAEGasStack aeStack =
                                gasChannel.createStack(drawn);

                            if (aeStack != null) {
                                ItemStack itemRep = aeStack.asItemStackRepresentation();
                                if (!itemRep.isEmpty()) {
                                    NBTTagCompound stackNbt = new NBTTagCompound();
                                    itemRep.writeToNBT(stackNbt);
                                    stackNbt.setLong("Cnt", drawn.amount);
                                    contentsList.appendTag(stackNbt);
                                }
                            }
                        }
                    }
                }

                busData.setTag("contents", contentsList);
            }

            // Upgrades - get from the upgrades inventory
            NBTTagList upgradeList = new NBTTagList();
            IItemHandler upgradeInv = bus.getInventoryByName("upgrades");
            if (upgradeInv != null) {
                for (int i = 0; i < upgradeInv.getSlots(); i++) {
                    ItemStack upgradeStack = upgradeInv.getStackInSlot(i);
                    if (!upgradeStack.isEmpty()) {
                        NBTTagCompound upgradeNbt = new NBTTagCompound();
                        upgradeStack.writeToNBT(upgradeNbt);
                        upgradeList.appendTag(upgradeNbt);
                    }
                }
            }

            busData.setTag("upgrades", upgradeList);

            return busData;
        } catch (Exception e) {
            CellTerminal.LOGGER.debug("Failed to create gas storage bus data: {}", e.getMessage());

            return null;
        }
    }

    // ========================================
    // Storage Bus Partition Handling
    // ========================================

    /**
     * Handle partition action for gas storage buses.
     */
    public static void handleGasStorageBusPartition(Object storageBus,
                                                    PacketStorageBusPartitionAction.Action action,
                                                    int partitionSlot,
                                                    ItemStack itemStack) {
        if (!isModLoaded()) return;

        handleGasStorageBusPartitionInternal(storageBus, action, partitionSlot, itemStack);
    }

    @Optional.Method(modid = MODID)
    private static void handleGasStorageBusPartitionInternal(Object storageBus,
                                                             PacketStorageBusPartitionAction.Action action,
                                                             int partitionSlot,
                                                             ItemStack itemStack) {
        try {
            if (!(storageBus instanceof com.mekeng.github.common.part.PartGasStorageBus)) return;

            com.mekeng.github.common.part.PartGasStorageBus bus =
                (com.mekeng.github.common.part.PartGasStorageBus) storageBus;

            com.mekeng.github.common.me.inventory.IGasInventory config = bus.getConfig();
            if (config == null) return;

            // Convert ItemStack to GasStack if it's a gas representation
            mekanism.api.gas.GasStack gasFromItem = null;
            if (!itemStack.isEmpty()) gasFromItem = getGasFromItem(itemStack);

            // Normalize gas to standard 1000 mB for consistent partition display
            if (gasFromItem != null) {
                gasFromItem = new mekanism.api.gas.GasStack(gasFromItem.getGas(), 1000);
            }

            switch (action) {
                case ADD_ITEM:
                    if (partitionSlot >= 0 && gasFromItem != null) {
                        config.setGas(partitionSlot, gasFromItem);
                    }
                    break;

                case REMOVE_ITEM:
                    if (partitionSlot >= 0) config.setGas(partitionSlot, null);
                    break;

                case TOGGLE_ITEM:
                    if (gasFromItem != null) {
                        int existingSlot = findGasInConfig(config, gasFromItem.getGas());
                        if (existingSlot >= 0) {
                            config.setGas(existingSlot, null);
                        } else {
                            int emptySlot = findEmptyGasSlot(config);
                            if (emptySlot >= 0) config.setGas(emptySlot, gasFromItem);
                        }
                    }
                    break;

                case SET_ALL_FROM_CONTENTS:
                    // Clear all gas slots first
                    clearGasConfig(config);
                    // Get contents from connected IGasHandler
                    TileEntity hostTile = bus.getHost().getTile();
                    if (hostTile != null && hostTile.getWorld() != null) {
                        EnumFacing targetSide = bus.getSide().getFacing().getOpposite();
                        TileEntity targetTile = hostTile.getWorld().getTileEntity(
                            hostTile.getPos().offset(bus.getSide().getFacing()));
                        if (targetTile != null &&
                            targetTile.hasCapability(mekanism.common.capabilities.Capabilities.GAS_HANDLER_CAPABILITY, targetSide)) {
                            mekanism.api.gas.IGasHandler gasHandler =
                                targetTile.getCapability(mekanism.common.capabilities.Capabilities.GAS_HANDLER_CAPABILITY, targetSide);
                            if (gasHandler != null) {
                                mekanism.api.gas.GasStack drawn = gasHandler.drawGas(targetSide, Integer.MAX_VALUE, false);
                                if (drawn != null && drawn.amount > 0 && drawn.getGas() != null) {
                                    // Normalize to standard 1000 mB for consistent partition display
                                    mekanism.api.gas.GasStack normalizedDrawn =
                                        new mekanism.api.gas.GasStack(drawn.getGas(), 1000);
                                    int emptySlot = findEmptyGasSlot(config);
                                    if (emptySlot >= 0) config.setGas(emptySlot, normalizedDrawn);
                                }
                            }
                        }
                    }
                    break;

                case CLEAR_ALL:
                    clearGasConfig(config);
                    break;
            }
        } catch (Exception e) {
            CellTerminal.LOGGER.debug("Failed to handle gas storage bus partition: {}", e.getMessage());
        }
    }

    @Optional.Method(modid = MODID)
    private static mekanism.api.gas.GasStack getGasFromItem(ItemStack stack) {
        // Try DummyGas item first (MekanismEnergistics representation)
        if (stack.getItem() instanceof com.mekeng.github.common.item.ItemDummyGas) {
            com.mekeng.github.common.item.ItemDummyGas dummyItem =
                (com.mekeng.github.common.item.ItemDummyGas) stack.getItem();

            return dummyItem.getGasStack(stack);
        }

        // Try IGasItem interface (Mekanism gas tanks, creative tanks, etc.)
        // Mekanism items store gas via the IGasItem interface on the Item class.
        if (stack.getItem() instanceof mekanism.api.gas.IGasItem) {
            mekanism.api.gas.GasStack gas = ((mekanism.api.gas.IGasItem) stack.getItem()).getGas(stack);
            if (gas != null && gas.amount > 0) return gas;
        }

        // Fallback: try items with gas handler capability (for modded items that use capabilities)
        if (stack.hasCapability(mekanism.common.capabilities.Capabilities.GAS_HANDLER_CAPABILITY, null)) {
            mekanism.api.gas.IGasHandler gasHandler =
                stack.getCapability(mekanism.common.capabilities.Capabilities.GAS_HANDLER_CAPABILITY, null);
            if (gasHandler != null) {
                mekanism.api.gas.GasStack drawn = gasHandler.drawGas(EnumFacing.UP, Integer.MAX_VALUE, false);
                if (drawn != null && drawn.amount > 0) return drawn;
            }
        }

        return null;
    }

    @Optional.Method(modid = MODID)
    private static int findGasInConfig(com.mekeng.github.common.me.inventory.IGasInventory config,
                                        mekanism.api.gas.Gas gas) {
        for (int i = 0; i < config.size(); i++) {
            mekanism.api.gas.GasStack gs = config.getGasStack(i);
            if (gs != null && gs.getGas() == gas) return i;
        }

        return -1;
    }

    @Optional.Method(modid = MODID)
    private static int findEmptyGasSlot(com.mekeng.github.common.me.inventory.IGasInventory config) {
        for (int i = 0; i < config.size(); i++) {
            mekanism.api.gas.GasStack gs = config.getGasStack(i);
            if (gs == null || gs.getGas() == null) return i;
        }

        return -1;
    }

    @Optional.Method(modid = MODID)
    private static void clearGasConfig(com.mekeng.github.common.me.inventory.IGasInventory config) {
        for (int i = 0; i < config.size(); i++) config.setGas(i, null);
    }

    // ========================================
    // Storage Bus Queries
    // ========================================

    /**
     * Check if a gas storage bus has a connected inventory.
     * @param storageBus The gas storage bus object
     * @return true if connected to a gas-capable container
     */
    public static boolean gasStorageBusHasConnectedInventory(Object storageBus) {
        if (!isModLoaded()) return false;

        return gasStorageBusHasConnectedInventoryInternal(storageBus);
    }

    @Optional.Method(modid = MODID)
    private static boolean gasStorageBusHasConnectedInventoryInternal(Object storageBus) {
        if (!(storageBus instanceof com.mekeng.github.common.part.PartGasStorageBus)) return false;

        try {
            com.mekeng.github.common.part.PartGasStorageBus bus =
                (com.mekeng.github.common.part.PartGasStorageBus) storageBus;

            TileEntity hostTile = bus.getHost().getTile();
            if (hostTile == null || hostTile.getWorld() == null) return false;

            EnumFacing targetSide = bus.getSide().getFacing().getOpposite();
            TileEntity targetTile = hostTile.getWorld().getTileEntity(
                hostTile.getPos().offset(bus.getSide().getFacing()));

            return targetTile != null &&
                targetTile.hasCapability(mekanism.common.capabilities.Capabilities.GAS_HANDLER_CAPABILITY, targetSide);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if a gas storage bus has a partition (filter) configured.
     * @param storageBus The gas storage bus object
     * @return true if the bus has at least one gas in its filter
     */
    public static boolean gasStorageBusHasPartition(Object storageBus) {
        if (!isModLoaded()) return false;

        return gasStorageBusHasPartitionInternal(storageBus);
    }

    @Optional.Method(modid = MODID)
    private static boolean gasStorageBusHasPartitionInternal(Object storageBus) {
        if (!(storageBus instanceof com.mekeng.github.common.part.PartGasStorageBus)) return false;

        try {
            com.mekeng.github.common.part.PartGasStorageBus bus =
                (com.mekeng.github.common.part.PartGasStorageBus) storageBus;
            com.mekeng.github.common.me.inventory.IGasInventory config = bus.getConfig();
            if (config == null) return false;

            for (int i = 0; i < config.size(); i++) {
                mekanism.api.gas.GasStack gs = config.getGasStack(i);
                if (gs != null && gs.getGas() != null) return true;
            }
        } catch (Exception e) {
            CellTerminal.LOGGER.debug("Error checking gas storage bus partition: {}", e.getMessage());
        }

        return false;
    }
}
