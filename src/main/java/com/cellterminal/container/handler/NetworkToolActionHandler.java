package com.cellterminal.container.handler;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.ICellHandler;

import com.cells.api.IItemCompactingCell;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.fluids.parts.PartFluidStorageBus;
import appeng.me.helpers.PlayerSource;
import appeng.parts.misc.PartStorageBus;

import com.cellterminal.CellTerminal;
import com.cellterminal.client.CellFilter;
import com.cellterminal.container.ContainerCellTerminalBase.StorageTracker;
import com.cellterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.cellterminal.gui.networktools.AttributeUniqueTool;
import com.cellterminal.gui.networktools.MassPartitionBusTool;
import com.cellterminal.gui.networktools.MassPartitionCellTool;
import com.cellterminal.integration.ThaumicEnergisticsIntegration;
import com.cellterminal.network.PacketPartitionAction;
import com.cellterminal.network.PacketStorageBusPartitionAction;


/**
 * Server-side handler for network tool actions.
 * Executes batch operations on cells and storage buses.
 */
public final class NetworkToolActionHandler {

    /**
     * Cell type enum for tracking separate counts per type.
     */
    public enum CellType {
        ITEM, FLUID, ESSENTIA
    }

    private NetworkToolActionHandler() {}

    /**
     * Handle a network tool action.
     * @param toolId The tool ID
     * @param activeFilters The active filters
     * @param storageById Map of storage trackers by ID
     * @param storageBusById Map of storage bus trackers by ID
     * @param grid The ME grid
     * @param player The player executing the action (for error messages)
     */
    public static void handleAction(String toolId,
                                    Map<CellFilter, CellFilter.State> activeFilters,
                                    Map<Long, StorageTracker> storageById,
                                    Map<Long, StorageBusTracker> storageBusById,
                                    IGrid grid,
                                    EntityPlayer player) {

        switch (toolId) {
            case MassPartitionCellTool.TOOL_ID:
                handleMassPartitionCells(activeFilters, storageById);
                break;

            case MassPartitionBusTool.TOOL_ID:
                handleMassPartitionBuses(activeFilters, storageBusById);
                break;

            case AttributeUniqueTool.TOOL_ID:
                handleAttributeUnique(activeFilters, storageById, grid, player);
                break;

            default:
                CellTerminal.LOGGER.warn("Unknown network tool ID: " + toolId);
                sendError(player, "cellterminal.networktools.error.unknown_tool");
        }
    }

    /**
     * Send an error message to the player.
     */
    private static void sendError(EntityPlayer player, String translationKey, Object... args) {
        if (player != null) {
            player.sendMessage(new TextComponentTranslation(translationKey, args));
        }
    }

    /**
     * Send a success message to the player.
     */
    private static void sendSuccess(EntityPlayer player, String translationKey, Object... args) {
        if (player != null) {
            player.sendMessage(new TextComponentTranslation(translationKey, args));
        }
    }

    /**
     * Mass partition all filtered cells from their contents.
     */
    private static void handleMassPartitionCells(Map<CellFilter, CellFilter.State> activeFilters,
                                                  Map<Long, StorageTracker> storageById) {

        for (StorageTracker tracker : storageById.values()) {
            IItemHandler cellInventory = CellDataHandler.getCellInventory(tracker.storage);
            if (cellInventory == null) continue;

            for (int slot = 0; slot < cellInventory.getSlots(); slot++) {
                ItemStack cellStack = cellInventory.getStackInSlot(slot);
                if (cellStack.isEmpty()) continue;

                if (!matchesCellFilters(cellStack, activeFilters)) continue;

                // Skip compacting cells - they use a special chain mechanism
                if (cellStack.getItem() instanceof IItemCompactingCell) continue;

                // Execute partition from contents
                CellActionHandler.handlePartitionAction(
                    tracker.storage, tracker.tile, slot,
                    PacketPartitionAction.Action.SET_ALL_FROM_CONTENTS,
                    -1, ItemStack.EMPTY);
            }
        }
    }

    /**
     * Mass partition all filtered storage buses from their contents.
     */
    private static void handleMassPartitionBuses(Map<CellFilter, CellFilter.State> activeFilters,
                                                  Map<Long, StorageBusTracker> storageBusById) {

        for (StorageBusTracker tracker : storageBusById.values()) {
            if (!matchesBusFilters(tracker, activeFilters)) continue;

            // Execute partition from contents
            StorageBusDataHandler.handlePartitionAction(
                tracker,
                PacketStorageBusPartitionAction.Action.SET_ALL_FROM_CONTENTS,
                -1, ItemStack.EMPTY);
        }
    }

    /**
     * Attribute unique items to cells (1 item type per cell).
     * Moves items between cells so each cell contains only one item type,
     * then partitions the cells accordingly.
     *
     * This operation bypasses normal network energy costs to prevent network
     * shutdown when dealing with cells containing billions of items.
     */
    private static void handleAttributeUnique(Map<CellFilter, CellFilter.State> activeFilters,
                                               Map<Long, StorageTracker> storageById,
                                               IGrid grid,
                                               EntityPlayer player) {

        // Track data separately for each cell type
        Map<CellType, TypeStats> statsByType = new EnumMap<>(CellType.class);
        for (CellType type : CellType.values()) statsByType.put(type, new TypeStats());

        // First pass: collect all filtered cells and their contents, grouped by type
        for (StorageTracker tracker : storageById.values()) {
            IItemHandler cellInventory = CellDataHandler.getCellInventory(tracker.storage);
            if (cellInventory == null) continue;

            for (int slot = 0; slot < cellInventory.getSlots(); slot++) {
                ItemStack cellStack = cellInventory.getStackInSlot(slot);
                if (cellStack.isEmpty()) continue;

                if (!matchesCellFilters(cellStack, activeFilters)) continue;

                // Skip compacting cells - they use a special chain mechanism
                if (cellStack.getItem() instanceof IItemCompactingCell) continue;

                CellType type = getCellType(cellStack);
                TypeStats stats = statsByType.get(type);
                CellTarget target = new CellTarget(tracker, slot, cellStack);
                stats.filteredCells.add(target);

                // Collect unique items from cell contents
                collectUniqueItemsForType(cellStack, type, stats);
            }
        }

        // Check if any type has content
        boolean hasAnyContent = false;
        for (TypeStats stats : statsByType.values()) {
            if (!stats.uniqueStacks.isEmpty()) {
                hasAnyContent = true;
                break;
            }
        }

        if (!hasAnyContent) {
            sendError(player, "cellterminal.networktools.error.no_items");
            return;
        }

        // Second pass: collect empty, non-partitioned cells of each type
        // FIXME: Currently we just assume all cells can accept any item, which is kinda bad.
        //        There should probably be a check for simulated insertion to verify compatibility.
        for (StorageTracker tracker : storageById.values()) {
            IItemHandler cellInventory = CellDataHandler.getCellInventory(tracker.storage);
            if (cellInventory == null) continue;

            for (int slot = 0; slot < cellInventory.getSlots(); slot++) {
                ItemStack cellStack = cellInventory.getStackInSlot(slot);
                if (cellStack.isEmpty()) continue;

                // Skip compacting cells - they use a special chain mechanism
                if (cellStack.getItem() instanceof IItemCompactingCell) continue;

                CellType type = getCellType(cellStack);
                TypeStats stats = statsByType.get(type);

                // Skip if this type has no content to redistribute
                if (stats.uniqueStacks.isEmpty()) continue;

                // Check if already in filtered cells
                final StorageTracker trackerFinal = tracker;
                final int slotFinal = slot;
                boolean alreadyIncluded = stats.filteredCells.stream()
                    .anyMatch(fc -> fc.tracker == trackerFinal && fc.slot == slotFinal);
                if (alreadyIncluded) continue;

                // Check if empty and non-partitioned
                if (isCellEmptyAndNonPartitioned(cellStack)) {
                    stats.emptyCells.add(new CellTarget(tracker, slot, cellStack));
                }
            }
        }

        // Validate: check each type has enough cells
        StringBuilder errorBuilder = new StringBuilder();
        boolean hasError = false;
        for (CellType type : CellType.values()) {
            TypeStats stats = statsByType.get(type);
            if (stats.uniqueStacks.isEmpty()) continue;

            int availableCells = stats.filteredCells.size() + stats.emptyCells.size();
            int uniqueCount = stats.uniqueStacks.size();

            if (availableCells < uniqueCount) {
                hasError = true;
                String typeName = type.name().toLowerCase();
                String typeKey = "cellterminal.networktools.attribute_unique.type." + typeName;
                String detailKey = "cellterminal.networktools.attribute_unique.error.type_detail";
                errorBuilder.append("\n" + new TextComponentTranslation(detailKey,
                    new TextComponentTranslation(typeKey), availableCells, uniqueCount).getFormattedText());
            }
        }

        if (hasError) {
            sendError(player, "cellterminal.networktools.attribute_unique.error.not_enough_cells_by_type",
                errorBuilder.toString());
            return;
        }

        // Create action source for operations (bypasses normal energy costs by using null grid)
        IActionSource source = player != null ? new PlayerSource(player, null) : null;

        // Execute redistribution for each cell type
        int totalMoved = 0;
        int totalCellsAffected = 0;

        for (CellType type : CellType.values()) {
            TypeStats stats = statsByType.get(type);
            if (stats.uniqueStacks.isEmpty()) continue;

            int[] result = redistributeCellType(type, stats, source);
            totalMoved += result[0];
            totalCellsAffected += result[1];
        }

        // Refresh all affected storage devices to update drive colors and network storage
        refreshAffectedStorage(statsByType, grid);

        sendSuccess(player, "cellterminal.networktools.attribute_unique.success",
            totalMoved, totalCellsAffected);
    }

    /**
     * Redistribute items within cells of a specific type.
     * Returns [itemTypesMoved, cellsAffected]
     */
    private static int[] redistributeCellType(CellType type, TypeStats stats, IActionSource source) {
        // Combine filtered and empty cells for this type
        List<CellTarget> allCells = new ArrayList<>(stats.filteredCells);
        allCells.addAll(stats.emptyCells);

        // Phase 1: Extract all items from all cells of this type
        Map<String, IAEStack<?>> extractedStacks = new java.util.LinkedHashMap<>();

        // FIXME: Some sanity checks are needed here to ensure the cells can actually accept the items.
        //       Currently, if cells cannot accept items, these items are just lost.
        //       Same for any overflow, they will be voided, instead of being left in the original cell.

        for (CellTarget target : stats.filteredCells) {
            extractAllFromCell(target.cellStack, type, extractedStacks, source);
        }

        // Phase 2: Clear partitions on all cells and set new partitions BEFORE injecting items
        // This ensures cells are properly partitioned to receive only their assigned item type
        int cellIndex = 0;
        for (CellTarget target : allCells) {
            CellActionHandler.handlePartitionAction(
                target.tracker.storage, target.tracker.tile, target.slot,
                PacketPartitionAction.Action.CLEAR_ALL,
                -1, ItemStack.EMPTY);
        }

        // Phase 3: Set partitions for cells that will receive items
        cellIndex = 0;
        for (Map.Entry<String, IAEStack<?>> entry : extractedStacks.entrySet()) {
            if (cellIndex >= allCells.size()) break;

            CellTarget target = allCells.get(cellIndex);
            IAEStack<?> stack = entry.getValue();

            // Set partition to this item type
            ItemStack partitionItem = getPartitionItemFromStack(stack, type);
            if (!partitionItem.isEmpty()) {
                CellActionHandler.handlePartitionAction(
                    target.tracker.storage, target.tracker.tile, target.slot,
                    PacketPartitionAction.Action.ADD_ITEM,
                    0, partitionItem);
            }

            cellIndex++;
        }

        // Phase 4: Inject items into cells (now properly partitioned)
        cellIndex = 0;
        int itemTypesMoved = 0;

        for (Map.Entry<String, IAEStack<?>> entry : extractedStacks.entrySet()) {
            if (cellIndex >= allCells.size()) break;

            CellTarget target = allCells.get(cellIndex);
            IAEStack<?> stack = entry.getValue();

            // Inject items into cell
            injectIntoCell(target.cellStack, type, stack, source);

            // Force cell refresh
            target.tracker.tile.markDirty();

            cellIndex++;
            itemTypesMoved++;
        }

        // Phase 5: Mark remaining cells dirty (partitions already cleared in Phase 2)
        while (cellIndex < allCells.size()) {
            CellTarget target = allCells.get(cellIndex);
            target.tracker.tile.markDirty();
            cellIndex++;
        }

        return new int[] { itemTypesMoved, allCells.size() };
    }

    /**
     * Refresh all storage devices that were affected by the redistribution.
     * This forces drives to recalculate their cell state by simulating cell removal/reinsertion.
     */
    private static void refreshAffectedStorage(Map<CellType, TypeStats> statsByType, IGrid grid) {
        // Collect all affected cell slots per storage tracker
        Map<StorageTracker, Set<Integer>> affectedSlots = new java.util.LinkedHashMap<>();

        for (TypeStats stats : statsByType.values()) {
            for (CellTarget target : stats.filteredCells) {
                affectedSlots.computeIfAbsent(target.tracker, k -> new HashSet<>()).add(target.slot);
            }
            for (CellTarget target : stats.emptyCells) {
                affectedSlots.computeIfAbsent(target.tracker, k -> new HashSet<>()).add(target.slot);
            }
        }

        // Force each affected storage device to recalculate by simulating cell swap
        for (Map.Entry<StorageTracker, Set<Integer>> entry : affectedSlots.entrySet()) {
            StorageTracker tracker = entry.getKey();
            Set<Integer> slots = entry.getValue();

            IItemHandler cellInventory = CellDataHandler.getCellInventory(tracker.storage);
            if (cellInventory == null) continue;

            // For each affected slot, extract and re-insert to trigger onChangeInventory
            for (int slot : slots) {
                ItemStack cellStack = cellInventory.extractItem(slot, 1, false);
                if (!cellStack.isEmpty()) {
                    cellInventory.insertItem(slot, cellStack, false);
                }
            }
        }

        // Post a network update event to refresh the grid storage
        if (grid != null) {
            try {
                grid.postEvent(new MENetworkCellArrayUpdate());
            } catch (Exception e) {
                CellTerminal.LOGGER.warn("Failed to post MENetworkCellArrayUpdate: " + e.getMessage());
            }
        }
    }

    /**
     * Extract all items from a cell into the extractedStacks map.
     */
    @SuppressWarnings("unchecked")
    private static void extractAllFromCell(ItemStack cellStack, CellType type,
                                            Map<String, IAEStack<?>> extractedStacks,
                                            IActionSource source) {
        if (type == CellType.ITEM) {
            ICellInventoryHandler<IAEItemStack> handler = getCellHandler(cellStack,
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            if (handler == null || handler.getCellInv() == null) return;

            ICellInventory<IAEItemStack> cellInv = handler.getCellInv();
            IItemList<IAEItemStack> available = cellInv.getAvailableItems(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createList());

            for (IAEItemStack stack : available) {
                // Extract from cell
                IAEItemStack extracted = cellInv.extractItems(stack.copy(), Actionable.MODULATE, source);
                if (extracted != null && extracted.getStackSize() > 0) {
                    String key = getItemKey(stack.createItemStack());
                    IAEStack<?> existing = extractedStacks.get(key);
                    if (existing != null) {
                        existing.incStackSize(extracted.getStackSize());
                    } else {
                        extractedStacks.put(key, extracted.copy());
                    }
                }
            }

            cellInv.persist();
        } else if (type == CellType.FLUID) {
            ICellInventoryHandler<IAEFluidStack> handler = getCellHandler(cellStack,
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
            if (handler == null || handler.getCellInv() == null) return;

            ICellInventory<IAEFluidStack> cellInv = handler.getCellInv();
            IItemList<IAEFluidStack> available = cellInv.getAvailableItems(
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class).createList());

            for (IAEFluidStack stack : available) {
                IAEFluidStack extracted = cellInv.extractItems(stack.copy(), Actionable.MODULATE, source);
                if (extracted != null && extracted.getStackSize() > 0) {
                    String key = "fluid:" + stack.getFluidStack().getFluid().getName();
                    IAEStack<?> existing = extractedStacks.get(key);
                    if (existing != null) {
                        existing.incStackSize(extracted.getStackSize());
                    } else {
                        extractedStacks.put(key, extracted.copy());
                    }
                }
            }

            cellInv.persist();
        } else if (type == CellType.ESSENTIA && ThaumicEnergisticsIntegration.isModLoaded()) {
            // Essentia cells use a different storage channel, delegate to integration
            @SuppressWarnings("unchecked")
            Map<String, Object> essentiaExtracted = (Map<String, Object>) (Map<?, ?>) extractedStacks;
            ThaumicEnergisticsIntegration.extractAllEssentiaFromCell(cellStack, essentiaExtracted);
        }
    }

    /**
     * Inject a stack into a cell.
     */
    @SuppressWarnings("unchecked")
    private static void injectIntoCell(ItemStack cellStack, CellType type,
                                        IAEStack<?> stack, IActionSource source) {
        if (type == CellType.ITEM && stack instanceof IAEItemStack) {
            ICellInventoryHandler<IAEItemStack> handler = getCellHandler(cellStack,
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            if (handler == null || handler.getCellInv() == null) return;

            ICellInventory<IAEItemStack> cellInv = handler.getCellInv();
            cellInv.injectItems((IAEItemStack) stack, Actionable.MODULATE, source);
            cellInv.persist();
        } else if (type == CellType.FLUID && stack instanceof IAEFluidStack) {
            ICellInventoryHandler<IAEFluidStack> handler = getCellHandler(cellStack,
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
            if (handler == null || handler.getCellInv() == null) return;

            ICellInventory<IAEFluidStack> cellInv = handler.getCellInv();
            cellInv.injectItems((IAEFluidStack) stack, Actionable.MODULATE, source);
            cellInv.persist();
        } else if (type == CellType.ESSENTIA && ThaumicEnergisticsIntegration.isModLoaded()) {
            // Essentia stacks are stored as Object in the map, delegate to integration
            ThaumicEnergisticsIntegration.injectEssentiaIntoCell(cellStack, stack);
        }
    }

    /**
     * Get an ItemStack suitable for partition from an IAEStack.
     */
    private static ItemStack getPartitionItemFromStack(IAEStack<?> stack, CellType type) {
        if (type == CellType.ITEM && stack instanceof IAEItemStack) {
            ItemStack result = ((IAEItemStack) stack).createItemStack();
            result.setCount(1);

            return result;
        } else if (type == CellType.FLUID && stack instanceof IAEFluidStack) {
            return ((IAEFluidStack) stack).asItemStackRepresentation();
        } else if (type == CellType.ESSENTIA && ThaumicEnergisticsIntegration.isModLoaded()) {
            return ThaumicEnergisticsIntegration.getEssentiaPartitionItem(stack);
        }

        return ItemStack.EMPTY;
    }

    /**
     * Get the cell type for a cell stack.
     */
    private static CellType getCellType(ItemStack cellStack) {
        if (isEssentiaCell(cellStack)) return CellType.ESSENTIA;
        if (isFluidCell(cellStack)) return CellType.FLUID;

        return CellType.ITEM;
    }

    /**
     * Collect unique items from a cell into the TypeStats for that type.
     */
    private static void collectUniqueItemsForType(ItemStack cellStack, CellType type, TypeStats stats) {
        if (type == CellType.ITEM) {
            ICellInventoryHandler<IAEItemStack> handler = getCellHandler(cellStack,
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            if (handler == null || handler.getCellInv() == null) return;

            IItemList<IAEItemStack> available = handler.getCellInv().getAvailableItems(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createList());

            for (IAEItemStack stack : available) {
                String key = getItemKey(stack.createItemStack());
                if (stats.uniqueKeys.add(key)) {
                    ItemStack copy = stack.createItemStack();
                    copy.setCount(1);
                    stats.uniqueStacks.add(copy);
                }
            }
        } else if (type == CellType.FLUID) {
            ICellInventoryHandler<IAEFluidStack> handler = getCellHandler(cellStack,
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
            if (handler == null || handler.getCellInv() == null) return;

            IItemList<IAEFluidStack> available = handler.getCellInv().getAvailableItems(
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class).createList());

            for (IAEFluidStack stack : available) {
                FluidStack fluidStack = stack.getFluidStack();
                if (fluidStack == null || fluidStack.getFluid() == null) continue;

                String key = "fluid:" + fluidStack.getFluid().getName();
                if (stats.uniqueKeys.add(key)) {
                    stats.uniqueStacks.add(stack.asItemStackRepresentation());
                }
            }
        } else if (type == CellType.ESSENTIA && ThaumicEnergisticsIntegration.isModLoaded()) {
            ThaumicEnergisticsIntegration.collectUniqueEssentiaFromCell(cellStack, stats.uniqueKeys, stats.uniqueStacks);
        }
    }

    /**
     * Helper class to track statistics per cell type.
     */
    private static class TypeStats {
        final List<CellTarget> filteredCells = new ArrayList<>();
        final List<CellTarget> emptyCells = new ArrayList<>();
        final Set<String> uniqueKeys = new HashSet<>();
        final List<ItemStack> uniqueStacks = new ArrayList<>();
    }

    private static boolean isFluidCell(ItemStack cellStack) {
        ICellInventoryHandler<IAEFluidStack> fluidHandler = getCellHandler(cellStack,
            AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
        return fluidHandler != null && fluidHandler.getCellInv() != null;
    }

    private static boolean isEssentiaCell(ItemStack cellStack) {
        if (!ThaumicEnergisticsIntegration.isModLoaded()) return false;
        return isEssentiaCellInternal(cellStack);
    }

    private static boolean isCellEmptyAndNonPartitioned(ItemStack cellStack) {
        // Check item cells
        ICellInventoryHandler<IAEItemStack> itemHandler = getCellHandler(cellStack,
            AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

        if (itemHandler != null && itemHandler.getCellInv() != null) {
            ICellInventory<IAEItemStack> cellInv = itemHandler.getCellInv();
            boolean isEmpty = cellInv.getUsedBytes() == 0;
            boolean hasPartition = hasConfigItems(cellInv.getConfigInventory());
            return isEmpty && !hasPartition;
        }

        // Check fluid cells
        ICellInventoryHandler<IAEFluidStack> fluidHandler = getCellHandler(cellStack,
            AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));

        if (fluidHandler != null && fluidHandler.getCellInv() != null) {
            ICellInventory<IAEFluidStack> cellInv = fluidHandler.getCellInv();
            boolean isEmpty = cellInv.getUsedBytes() == 0;
            boolean hasPartition = hasConfigItems(cellInv.getConfigInventory());
            return isEmpty && !hasPartition;
        }

        // Check essentia cells (ThaumicEnergistics)
        if (ThaumicEnergisticsIntegration.isModLoaded()) {
            ICellHandler handler = AEApi.instance().registries().cell().getHandler(cellStack);
            if (handler != null && ThaumicEnergisticsIntegration.isEssentiaEmptyAndNonPartitioned(handler, cellStack)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Collect unique items from a cell's contents.
     */
    private static void collectUniqueItems(ItemStack cellStack,
                                            Set<String> uniqueItemKeys,
                                            List<ItemStack> uniqueItemStacks) {
        // Try item storage channel
        ICellInventoryHandler<IAEItemStack> itemHandler = getCellHandler(cellStack,
            AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

        if (itemHandler != null && itemHandler.getCellInv() != null) {
            ICellInventory<IAEItemStack> cellInv = itemHandler.getCellInv();
            IItemList<IAEItemStack> available = cellInv.getAvailableItems(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createList());

            for (IAEItemStack stack : available) {
                ItemStack itemStack = stack.createItemStack();
                String key = getItemKey(itemStack);
                if (uniqueItemKeys.add(key)) {
                    ItemStack copy = itemStack.copy();
                    copy.setCount(1);
                    uniqueItemStacks.add(copy);
                }
            }

            return;
        }

        // Try fluid storage channel
        ICellInventoryHandler<IAEFluidStack> fluidHandler = getCellHandler(cellStack,
            AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));

        if (fluidHandler != null && fluidHandler.getCellInv() != null) {
            ICellInventory<IAEFluidStack> cellInv = fluidHandler.getCellInv();
            IItemList<IAEFluidStack> available = cellInv.getAvailableItems(
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class).createList());

            for (IAEFluidStack aeFluidStack : available) {
                FluidStack fluidStack = aeFluidStack.getFluidStack();
                if (fluidStack == null || fluidStack.getFluid() == null) continue;

                Fluid fluid = fluidStack.getFluid();
                String key = "fluid:" + fluid.getName();
                if (uniqueItemKeys.add(key)) {
                    // Try to get a bucket or container for this fluid
                    // Use the fluid's block if available
                    ItemStack bucketStack = net.minecraftforge.fluids.FluidUtil.getFilledBucket(fluidStack);
                    if (!bucketStack.isEmpty()) uniqueItemStacks.add(bucketStack);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> ICellInventoryHandler<T> getCellHandler(
            ItemStack cellStack, IStorageChannel<T> channel) {
        ICellHandler handler = AEApi.instance().registries().cell().getHandler(cellStack);
        if (handler == null) return null;

        return handler.getCellInventory(cellStack, null, channel);
    }

    private static String getItemKey(ItemStack stack) {
        String base = stack.getItem().getRegistryName().toString() + "@" + stack.getMetadata();
        if (stack.hasTagCompound()) base += "#" + stack.getTagCompound().hashCode();

        return base;
    }

    /**
     * Check if a cell matches the active filters.
     */
    private static boolean matchesCellFilters(ItemStack cellStack,
                                               Map<CellFilter, CellFilter.State> activeFilters) {
        // Determine cell type
        boolean isFluid = false;
        boolean isEssentia = false;

        ICellInventoryHandler<IAEFluidStack> fluidHandler = getCellHandler(cellStack,
            AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));

        if (fluidHandler != null && fluidHandler.getCellInv() != null) isFluid = true;

        // Check if it's an essentia cell
        if (ThaumicEnergisticsIntegration.isModLoaded()) {
            isEssentia = isEssentiaCellInternal(cellStack);
            if (isEssentia) isFluid = false;
        }

        boolean isItem = !isFluid && !isEssentia;

        // Check type filters
        CellFilter.State itemState = activeFilters.getOrDefault(CellFilter.ITEM_CELLS, CellFilter.State.SHOW_ALL);
        CellFilter.State fluidState = activeFilters.getOrDefault(CellFilter.FLUID_CELLS, CellFilter.State.SHOW_ALL);
        CellFilter.State essentiaState = activeFilters.getOrDefault(CellFilter.ESSENTIA_CELLS, CellFilter.State.SHOW_ALL);

        if (isItem && itemState == CellFilter.State.HIDE) return false;
        if (isFluid && fluidState == CellFilter.State.HIDE) return false;
        if (isEssentia && essentiaState == CellFilter.State.HIDE) return false;

        boolean hasShowOnly = (itemState == CellFilter.State.SHOW_ONLY)
            || (fluidState == CellFilter.State.SHOW_ONLY)
            || (essentiaState == CellFilter.State.SHOW_ONLY);

        if (hasShowOnly) {
            if (isItem && itemState != CellFilter.State.SHOW_ONLY) return false;
            if (isFluid && fluidState != CellFilter.State.SHOW_ONLY) return false;
            if (isEssentia && essentiaState != CellFilter.State.SHOW_ONLY) return false;
        }

        // Check has items filter
        CellFilter.State hasItemsState = activeFilters.getOrDefault(CellFilter.HAS_ITEMS, CellFilter.State.SHOW_ALL);
        if (hasItemsState != CellFilter.State.SHOW_ALL) {
            boolean hasItems = checkCellHasContents(cellStack);
            if (hasItemsState == CellFilter.State.SHOW_ONLY && !hasItems) return false;
            if (hasItemsState == CellFilter.State.HIDE && hasItems) return false;
        }

        // Check partitioned filter
        CellFilter.State partitionedState = activeFilters.getOrDefault(CellFilter.PARTITIONED, CellFilter.State.SHOW_ALL);
        if (partitionedState != CellFilter.State.SHOW_ALL) {
            boolean hasPartition = checkCellHasPartition(cellStack);
            if (partitionedState == CellFilter.State.SHOW_ONLY && !hasPartition) return false;
            if (partitionedState == CellFilter.State.HIDE && hasPartition) return false;
        }

        return true;
    }

    private static boolean isEssentiaCellInternal(ItemStack cellStack) {
        // Check if ThaumicEnergisticsIntegration can populate this cell as essentia
        ICellHandler handler = AEApi.instance().registries().cell().getHandler(cellStack);
        if (handler == null) return false;

        return ThaumicEnergisticsIntegration.tryPopulateEssentiaCell(handler, cellStack, Integer.MAX_VALUE) != null;
    }

    private static boolean checkCellHasContents(ItemStack cellStack) {
        ICellInventoryHandler<IAEItemStack> itemHandler = getCellHandler(cellStack,
            AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

        if (itemHandler != null && itemHandler.getCellInv() != null) {
            return itemHandler.getCellInv().getUsedBytes() > 0;
        }

        ICellInventoryHandler<IAEFluidStack> fluidHandler = getCellHandler(cellStack,
            AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));

        if (fluidHandler != null && fluidHandler.getCellInv() != null) {
            return fluidHandler.getCellInv().getUsedBytes() > 0;
        }

        return false;
    }

    private static boolean checkCellHasPartition(ItemStack cellStack) {
        ICellInventoryHandler<IAEItemStack> itemHandler = getCellHandler(cellStack,
            AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

        if (itemHandler != null && itemHandler.getCellInv() != null) {
            return hasConfigItems(itemHandler.getCellInv().getConfigInventory());
        }

        ICellInventoryHandler<IAEFluidStack> fluidHandler = getCellHandler(cellStack,
            AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));

        if (fluidHandler != null && fluidHandler.getCellInv() != null) {
            return hasConfigItems(fluidHandler.getCellInv().getConfigInventory());
        }

        return false;
    }

    private static boolean hasConfigItems(IItemHandler configInv) {
        if (configInv == null) return false;

        for (int i = 0; i < configInv.getSlots(); i++) {
            if (!configInv.getStackInSlot(i).isEmpty()) return true;
        }

        return false;
    }

    /**
     * Check if a storage bus matches the active filters.
     * Note: Search mode is validated client-side; server only checks filter states.
     */
    private static boolean matchesBusFilters(StorageBusTracker tracker,
                                              Map<CellFilter, CellFilter.State> activeFilters) {
        boolean isFluid = tracker.storageBus instanceof PartFluidStorageBus;
        boolean isEssentia = isEssentiaStorageBus(tracker);
        boolean isItem = tracker.storageBus instanceof PartStorageBus && !isFluid && !isEssentia;

        CellFilter.State itemState = activeFilters.getOrDefault(CellFilter.ITEM_CELLS, CellFilter.State.SHOW_ALL);
        CellFilter.State fluidState = activeFilters.getOrDefault(CellFilter.FLUID_CELLS, CellFilter.State.SHOW_ALL);
        CellFilter.State essentiaState = activeFilters.getOrDefault(CellFilter.ESSENTIA_CELLS, CellFilter.State.SHOW_ALL);

        if (isItem && itemState == CellFilter.State.HIDE) return false;
        if (isFluid && fluidState == CellFilter.State.HIDE) return false;
        if (isEssentia && essentiaState == CellFilter.State.HIDE) return false;

        boolean hasShowOnly = (itemState == CellFilter.State.SHOW_ONLY)
            || (fluidState == CellFilter.State.SHOW_ONLY)
            || (essentiaState == CellFilter.State.SHOW_ONLY);

        if (hasShowOnly) {
            if (isItem && itemState != CellFilter.State.SHOW_ONLY) return false;
            if (isFluid && fluidState != CellFilter.State.SHOW_ONLY) return false;
            if (isEssentia && essentiaState != CellFilter.State.SHOW_ONLY) return false;
        }

        CellFilter.State hasItemsState = activeFilters.getOrDefault(CellFilter.HAS_ITEMS, CellFilter.State.SHOW_ALL);
        if (hasItemsState != CellFilter.State.SHOW_ALL) {
            boolean hasItems = checkBusHasContents(tracker);
            if (hasItemsState == CellFilter.State.SHOW_ONLY && !hasItems) return false;
            if (hasItemsState == CellFilter.State.HIDE && hasItems) return false;
        }

        CellFilter.State partitionedState = activeFilters.getOrDefault(CellFilter.PARTITIONED, CellFilter.State.SHOW_ALL);
        if (partitionedState != CellFilter.State.SHOW_ALL) {
            boolean hasPartition = checkBusHasPartition(tracker);
            if (partitionedState == CellFilter.State.SHOW_ONLY && !hasPartition) return false;
            if (partitionedState == CellFilter.State.HIDE && hasPartition) return false;
        }

        return true;
    }

    private static boolean isEssentiaStorageBus(StorageBusTracker tracker) {
        if (!ThaumicEnergisticsIntegration.isModLoaded()) return false;

        Class<?> essentiaClass = ThaumicEnergisticsIntegration.getEssentiaStorageBusClass();
        if (essentiaClass == null) return false;

        return essentiaClass.isInstance(tracker.storageBus);
    }

    private static boolean checkBusHasContents(StorageBusTracker tracker) {
        // Storage buses don't have "contents" in the same sense as cells
        // They connect to external inventories which may have contents
        // For this filter, we check if the bus can see any items
        return StorageBusDataHandler.busHasConnectedInventory(tracker);
    }

    private static boolean checkBusHasPartition(StorageBusTracker tracker) {
        return StorageBusDataHandler.busHasPartition(tracker);
    }

    private static class CellTarget {
        final StorageTracker tracker;
        final int slot;
        final ItemStack cellStack;

        CellTarget(StorageTracker tracker, int slot, ItemStack cellStack) {
            this.tracker = tracker;
            this.slot = slot;
            this.cellStack = cellStack;
        }
    }
}
