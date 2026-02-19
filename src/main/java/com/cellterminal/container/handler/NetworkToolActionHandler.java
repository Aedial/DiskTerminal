package com.cellterminal.container.handler;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.translation.I18n;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.ICellHandler;
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

import com.cells.api.IItemCompactingCell;

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
import com.cellterminal.util.BigStackTracker;
import com.cellterminal.util.FluidStackKey;
import com.cellterminal.util.ItemStackKey;
import com.cellterminal.util.SafeMath;


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
     * Get a human-readable name for a cell (e.g., "64k ME Storage Cell").
     */
    private static String getCellDisplayName(ItemStack cellStack) {
        if (cellStack.isEmpty()) return "Empty";
        return cellStack.getDisplayName();
    }

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
     *
     * Algorithm:
     * 1. Collect all filtered cells and their contents, grouped by cell type (item/fluid/essentia)
     * 2. Collect empty non-partitioned cells as additional targets
     * 3. For each cell type:
     *    a. Calculate capacity of each cell and total stack sizes
     *    b. Sort cells by capacity (descending) and stacks by size (descending)
     *    c. Use simulation to assign stacks to cells, verifying compatibility
     *    d. If a stack cannot fit entirely, mark it for partial handling
     *    e. Execute the actual redistribution only if simulation succeeds
     * 4. Report results including any partial redistributions
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

                // Calculate cell capacity for proper assignment
                long capacity = getCellCapacity(cellStack, type);
                target.capacity = capacity;

                stats.filteredCells.add(target);

                // Collect unique items with their full counts from cell contents
                collectItemsWithCountsForType(cellStack, type, stats);
            }
        }

        // Check if any type has content
        boolean hasAnyContent = false;
        for (TypeStats stats : statsByType.values()) {
            if (!stats.stackTracker.isEmpty()) {
                hasAnyContent = true;
                break;
            }
        }

        if (!hasAnyContent) {
            sendError(player, "cellterminal.networktools.error.no_items");
            return;
        }

        // Second pass: collect empty, non-partitioned cells of each type as additional targets
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
                if (stats.stackTracker.isEmpty()) continue;

                // Check if already in filtered cells
                final StorageTracker trackerFinal = tracker;
                final int slotFinal = slot;
                boolean alreadyIncluded = stats.filteredCells.stream()
                    .anyMatch(fc -> fc.tracker == trackerFinal && fc.slot == slotFinal);
                if (alreadyIncluded) continue;

                // Check if empty and non-partitioned
                if (isCellEmptyAndNonPartitioned(cellStack)) {
                    CellTarget target = new CellTarget(tracker, slot, cellStack);
                    target.capacity = getCellCapacity(cellStack, type);
                    stats.emptyCells.add(target);
                }
            }
        }

        // Create action source for operations (bypasses normal energy costs by using null grid)
        IActionSource source = player != null ? new PlayerSource(player, null) : null;

        // Track overall results
        int totalMoved = 0;
        int totalCellsAffected = 0;
        int totalPartiallyMoved = 0;
        int totalConsolidated = 0;
        int totalFailed = 0;
        List<String> warnings = new ArrayList<>();

        // Execute redistribution for each cell type with proper simulation
        for (CellType type : CellType.values()) {
            TypeStats stats = statsByType.get(type);
            if (stats.stackTracker.isEmpty()) continue;

            RedistributionResult result = redistributeCellTypeWithSimulation(type, stats, source, player);
            totalMoved += result.fullyMoved;
            totalCellsAffected += result.cellsAffected;
            totalPartiallyMoved += result.partiallyMoved;
            totalConsolidated += result.consolidated;
            totalFailed += result.failed;

            if (!result.warnings.isEmpty()) warnings.addAll(result.warnings);
        }

        // Refresh all affected storage devices to update drive colors and network storage
        refreshAffectedStorage(statsByType, grid);

        // Report results to player
        if (totalFailed > 0 || totalPartiallyMoved > 0) {
            // Partial success - some items couldn't be fully redistributed
            if (totalMoved > 0 || totalPartiallyMoved > 0) {
                sendSuccess(player, "cellterminal.networktools.attribute_unique.partial_success",
                    totalMoved + totalPartiallyMoved, totalCellsAffected, totalFailed);
            } else {
                sendError(player, "cellterminal.networktools.attribute_unique.error.redistribution_failed");
            }

            // Send warnings about incompatible or capacity issues
            for (String warning : warnings) player.sendMessage(new TextComponentString(warning));
        } else if (totalConsolidated > 0) {
            // Success but some items were consolidated (not unique)
            sendSuccess(player, "cellterminal.networktools.attribute_unique.success_with_consolidated",
                totalMoved, totalCellsAffected, totalConsolidated);
        } else {
            sendSuccess(player, "cellterminal.networktools.attribute_unique.success",
                totalMoved, totalCellsAffected);
        }
    }

    /**
     * Result of a redistribution operation for a cell type.
     */
    private static class RedistributionResult {
        int fullyMoved = 0;       // Number of item types fully redistributed
        int partiallyMoved = 0;   // Number of item types partially redistributed (some left in original)
        int failed = 0;           // Number of item types that couldn't be moved at all
        int consolidated = 0;     // Number of item types consolidated into cells with other types (not unique)
        int cellsAffected = 0;    // Total cells modified
        List<String> warnings = new ArrayList<>();
    }

    /**
     * Assignment plan for a single stack to a target cell.
     * The key is an ItemStackKey for items, FluidStackKey for fluids, or a String for essentia.
     */
    private static class StackAssignment {
        final Object key;
        final IAEStack<?> stack;
        final CellTarget targetCell;
        final long amountToMove;
        final boolean isPartial;

        StackAssignment(Object key, IAEStack<?> stack, CellTarget targetCell, long amountToMove, boolean isPartial) {
            this.key = key;
            this.stack = stack;
            this.targetCell = targetCell;
            this.amountToMove = amountToMove;
            this.isPartial = isPartial;
        }
    }

    /**
     * Redistribute items within cells of a specific type using simulation for safety.
     * Uses BigStackTracker to handle item counts exceeding Long.MAX_VALUE without data loss.
     *
     * CRITICAL: This method MUST NOT lose any items. If redistribution cannot complete
     * fully, all items must be returned to their source cells.
     * Do not remove the logging statements - they are essential for diagnosing issues with specific cells or items.
     *
     * Algorithm:
     * 1. Extract all items from filtered cells FIRST (before any simulation)
     * 2. Clear all partitions from target cells
     * 3. NOW simulate/assign against the actual empty cell state
     * 4. Inject items according to assignment
     * 5. Put back any remaining items
     */
    private static RedistributionResult redistributeCellTypeWithSimulation(CellType type, TypeStats stats,
                                                                            IActionSource source,
                                                                            EntityPlayer player) {
        RedistributionResult result = new RedistributionResult();

        // Gather ALL potential target cells - filtered cells plus all empty non-partitioned cells
        // Empty cells serve as fallback targets for overflow or incompatible items
        List<CellTarget> targetCells = new ArrayList<>(stats.filteredCells);
        targetCells.addAll(stats.emptyCells);

        // Phase 1: Extract all items from FILTERED cells FIRST
        // This must happen before simulation so we're simulating against empty cells
        CellTerminal.LOGGER.info("[AttributeUnique] Phase 1: Extracting items from {} filtered cells", stats.filteredCells.size());
        BigStackTracker extractedTracker = new BigStackTracker();
        for (CellTarget target : stats.filteredCells) {
            String cellName = getCellDisplayName(target.cellStack);
            BigStackTracker beforeExtract = new BigStackTracker();
            extractAllFromCellToBigTracker(target.cellStack, type, extractedTracker, source);

            // Log what was extracted (compare before/after would be complex, so just log the cell)
            CellTerminal.LOGGER.info("[AttributeUnique]   Extracted from: {}", cellName);
        }

        // Log total extracted items
        for (Map.Entry<Object, BigStackTracker.Entry> entry : extractedTracker.entrySet()) {
            CellTerminal.LOGGER.info("[AttributeUnique]   Total extracted: {} x{}",
                getStackDisplayName(entry.getValue().getTemplate(), type),
                entry.getValue().getCount());
        }

        // Phase 2: Clear all partitions from TARGET cells only
        CellTerminal.LOGGER.info("[AttributeUnique] Phase 2: Clearing partitions from {} target cells", targetCells.size());
        for (CellTarget target : targetCells) {
            CellActionHandler.handlePartitionAction(
                target.tracker.storage, target.tracker.tile, target.slot,
                PacketPartitionAction.Action.CLEAR_ALL,
                -1, ItemStack.EMPTY);
        }

        // Phase 3: Probe each cell's TRUE capacity now that they're empty
        // Some modded cells report small "bytes" but can hold billions of items
        CellTerminal.LOGGER.info("[AttributeUnique] Phase 3: Probing capacity of {} target cells", targetCells.size());
        Map<CellTarget, Long> cellCapacities = new LinkedHashMap<>();
        for (CellTarget cell : targetCells) {
            long capacity = probeCellCapacity(cell.cellStack, type, source);
            cellCapacities.put(cell, capacity);
            CellTerminal.LOGGER.info("[AttributeUnique]   Cell '{}' probed capacity: {}",
                getCellDisplayName(cell.cellStack), capacity);
        }

        // Sort by probed capacity (SMALLEST first for best-fit algorithm)
        // Best-fit assigns items to the smallest cell that can hold them,
        // preventing huge cells from being wasted on small stacks
        targetCells.sort((a, b) -> Long.compare(
            cellCapacities.getOrDefault(a, 0L),
            cellCapacities.getOrDefault(b, 0L)));

        // Log sorted order
        CellTerminal.LOGGER.info("[AttributeUnique] Cells sorted by capacity (smallest first for best-fit):");
        for (int i = 0; i < targetCells.size(); i++) {
            CellTarget cell = targetCells.get(i);
            CellTerminal.LOGGER.info("[AttributeUnique]   {}: '{}' capacity={}",
                i + 1, getCellDisplayName(cell.cellStack), cellCapacities.get(cell));
        }

        // Convert extracted items to sorted list (largest totals first)
        // We still process largest items first to ensure they get cells
        List<Map.Entry<Object, BigStackTracker.Entry>> sortedStacks =
            new ArrayList<>(extractedTracker.entrySet());
        sortedStacks.sort((a, b) -> b.getValue().getCount().compareTo(a.getValue().getCount()));

        // Log sorted stacks
        CellTerminal.LOGGER.info("[AttributeUnique] Stacks sorted by count (largest first):");
        for (int i = 0; i < sortedStacks.size(); i++) {
            Map.Entry<Object, BigStackTracker.Entry> entry = sortedStacks.get(i);
            CellTerminal.LOGGER.info("[AttributeUnique]   {}: {} x{}",
                i + 1, getStackDisplayName(entry.getValue().getTemplate(), type),
                entry.getValue().getCount());
        }

        List<StackAssignment> assignments = new ArrayList<>();
        Set<CellTarget> usedCells = new HashSet<>();

        CellTerminal.LOGGER.info("[AttributeUnique] Assigning stacks to cells (best-fit algorithm)...");
        for (Map.Entry<Object, BigStackTracker.Entry> entry : sortedStacks) {
            Object key = entry.getKey();
            BigStackTracker.Entry trackerEntry = entry.getValue();
            BigInteger totalCount = trackerEntry.getCount();
            String itemName = getStackDisplayName(trackerEntry.getTemplate(), type);

            CellTerminal.LOGGER.info("[AttributeUnique] Assigning {} x{}", itemName, totalCount);

            // Track how much of this item type we still need to assign
            BigInteger remainingToAssign = totalCount;
            boolean anyAssigned = false;

            // BEST-FIT: Find the smallest cell that can hold ALL of this item type
            // This prevents wasting huge cells on small stacks
            CellTarget bestFitCell = null;
            long bestFitCapacity = Long.MAX_VALUE;

            for (CellTarget cell : targetCells) {
                if (usedCells.contains(cell)) continue;

                long capacity = cellCapacities.getOrDefault(cell, 0L);
                // Check if this cell can hold the entire stack
                BigInteger bigCapacity = BigInteger.valueOf(capacity);
                if (bigCapacity.compareTo(totalCount) >= 0 && capacity < bestFitCapacity) {
                    // Verify the cell actually accepts this item type
                    long testAmount = remainingToAssign.compareTo(SafeMath.MAX_LONG) > 0
                        ? Long.MAX_VALUE
                        : remainingToAssign.longValue();
                    IAEStack<?> testStack = trackerEntry.getTemplate().copy();
                    testStack.setStackSize(testAmount);
                    long canAccept = simulateInjection(cell.cellStack, type, testStack, source);

                    if (canAccept >= testAmount) {
                        bestFitCell = cell;
                        bestFitCapacity = capacity;
                        CellTerminal.LOGGER.debug("[AttributeUnique]   Best-fit candidate: '{}' capacity={}",
                            getCellDisplayName(cell.cellStack), capacity);
                        break; // Cells are sorted smallest-first, so first match is best
                    }
                }
            }

            // If we found a best-fit cell, use it
            if (bestFitCell != null) {
                long testAmount = remainingToAssign.compareTo(SafeMath.MAX_LONG) > 0
                    ? Long.MAX_VALUE
                    : remainingToAssign.longValue();
                IAEStack<?> testStack = trackerEntry.getTemplate().copy();
                testStack.setStackSize(testAmount);
                long canAccept = simulateInjection(bestFitCell.cellStack, type, testStack, source);

                CellTerminal.LOGGER.info("[AttributeUnique]   -> Best-fit: {} x{} to '{}' (capacity={})",
                    itemName, canAccept, getCellDisplayName(bestFitCell.cellStack),
                    cellCapacities.getOrDefault(bestFitCell, -1L));

                IAEStack<?> assignStack = trackerEntry.getTemplate().copy();
                assignStack.setStackSize(canAccept);
                assignments.add(new StackAssignment(key, assignStack, bestFitCell, canAccept, false));
                usedCells.add(bestFitCell);
                anyAssigned = true;
                remainingToAssign = remainingToAssign.subtract(BigInteger.valueOf(canAccept));
            }

            // If best-fit didn't work (no single cell can hold it all), fall back to largest-first
            // This handles items that need to span multiple cells
            if (remainingToAssign.compareTo(BigInteger.ZERO) > 0) {
                CellTerminal.LOGGER.info("[AttributeUnique]   No single cell can hold all, using largest-first fallback");

                // CONSOLIDATION CHECK: Before spreading across many small cells, check if any
                // USED large cell can accept the entire remaining amount. This is better than
                // spreading 215M items across 5+ small 64k cells when a 2G cell has room.
                // We check used cells because they may have vast remaining capacity.
                long remainingLong = remainingToAssign.compareTo(SafeMath.MAX_LONG) > 0
                    ? Long.MAX_VALUE
                    : remainingToAssign.longValue();

                CellTarget consolidationCell = null;
                for (CellTarget cell : targetCells) {
                    // Only consider cells that are already used (unused cells are for other items)
                    if (!usedCells.contains(cell)) continue;

                    IAEStack<?> testStack = trackerEntry.getTemplate().copy();
                    testStack.setStackSize(remainingLong);
                    long canAccept = simulateInjection(cell.cellStack, type, testStack, source);

                    // If this cell can hold ALL remaining items, use it for consolidation
                    if (canAccept >= remainingLong) {
                        consolidationCell = cell;
                        CellTerminal.LOGGER.info("[AttributeUnique]   -> Consolidating: {} x{} into already-used '{}' (has room)",
                            itemName, canAccept, getCellDisplayName(cell.cellStack));

                        IAEStack<?> assignStack = trackerEntry.getTemplate().copy();
                        assignStack.setStackSize(canAccept);
                        // Mark as partial since it's sharing a cell with other items (not unique)
                        assignments.add(new StackAssignment(key, assignStack, cell, canAccept, true));
                        anyAssigned = true;
                        remainingToAssign = remainingToAssign.subtract(BigInteger.valueOf(canAccept));
                        result.consolidated++;
                        break;
                    }
                }

                // If consolidation didn't work, fall back to spreading across unused cells
                if (consolidationCell == null && remainingToAssign.compareTo(BigInteger.ZERO) > 0) {
                    // Sort remaining cells largest-first for this fallback
                    List<CellTarget> remainingCells = new ArrayList<>();
                    for (CellTarget cell : targetCells) {
                        if (!usedCells.contains(cell)) remainingCells.add(cell);
                    }
                    remainingCells.sort((a, b) -> Long.compare(
                        cellCapacities.getOrDefault(b, 0L),
                        cellCapacities.getOrDefault(a, 0L)));

                    // FAIR SHARE: Calculate how many cells this item can use
                    // We must reserve at least 1 cell for each remaining item type to ensure fair distribution
                    // Otherwise one large item could consume ALL remaining cells, leaving nothing for others
                    int currentStackIndex = sortedStacks.indexOf(entry);
                    int remainingItemTypes = sortedStacks.size() - currentStackIndex;
                    int availableCells = remainingCells.size();
                    int maxCellsForThisItem = Math.max(1, availableCells - (remainingItemTypes - 1));

                    CellTerminal.LOGGER.info("[AttributeUnique]   Fair share: {} cells available, {} item types remaining, max {} cells for this item",
                        availableCells, remainingItemTypes, maxCellsForThisItem);

                    int cellsUsedForThisItem = 0;

                    for (CellTarget cell : remainingCells) {
                        if (remainingToAssign.compareTo(BigInteger.ZERO) <= 0) break;

                        // FAIR SHARE CHECK: Don't exceed our allocation if other items still need cells
                        if (cellsUsedForThisItem >= maxCellsForThisItem && remainingItemTypes > 1) {
                            CellTerminal.LOGGER.info("[AttributeUnique]   Stopping at {} cells to reserve cells for {} other item types",
                                cellsUsedForThisItem, remainingItemTypes - 1);
                            break;
                        }

                        long cellCapacity = cellCapacities.getOrDefault(cell, 0L);
                        long testAmount = remainingToAssign.compareTo(SafeMath.MAX_LONG) > 0
                            ? Long.MAX_VALUE
                            : remainingToAssign.longValue();

                        // UTILIZATION CHECK: Don't waste large cells on tiny amounts
                        // If this would use less than 1% of the cell's capacity AND we've already
                        // assigned to at least one cell, skip this cell and let Phase 5 handle remainder
                        if (cellsUsedForThisItem > 0 && cellCapacity > 0) {
                            double utilization = (double) testAmount / (double) cellCapacity;
                            if (utilization < 0.01) {
                                CellTerminal.LOGGER.info("[AttributeUnique]   Skipping '{}' - would only use {}% of capacity, leaving {} for Phase 5",
                                    getCellDisplayName(cell.cellStack), String.format("%.6f", utilization * 100), testAmount);
                                break;  // Leave remainder for Phase 5
                            }
                        }

                        IAEStack<?> testStack = trackerEntry.getTemplate().copy();
                        testStack.setStackSize(testAmount);

                        long canAccept = simulateInjection(cell.cellStack, type, testStack, source);
                        if (canAccept <= 0) {
                            CellTerminal.LOGGER.warn("[AttributeUnique]   Cell '{}' rejected {} (canAccept=0)",
                                getCellDisplayName(cell.cellStack), itemName);
                            continue;
                        }

                        BigInteger bigAmountToAssign = BigInteger.valueOf(canAccept);
                        boolean isPartial = bigAmountToAssign.compareTo(remainingToAssign) < 0;

                        CellTerminal.LOGGER.info("[AttributeUnique]   -> Fallback: {} x{} to '{}' (capacity={})",
                            itemName, canAccept, getCellDisplayName(cell.cellStack),
                            cellCapacities.getOrDefault(cell, -1L));

                        IAEStack<?> assignStack = trackerEntry.getTemplate().copy();
                        assignStack.setStackSize(canAccept);
                        assignments.add(new StackAssignment(key, assignStack, cell, canAccept, isPartial));
                        usedCells.add(cell);
                        anyAssigned = true;
                        cellsUsedForThisItem++;

                        remainingToAssign = remainingToAssign.subtract(bigAmountToAssign);
                    }
                }
            }

            if (remainingToAssign.compareTo(BigInteger.ZERO) <= 0) {
                result.fullyMoved++;
            } else if (anyAssigned) {
                result.partiallyMoved++;
                result.warnings.add(I18n.translateToLocalFormatted(
                    "cellterminal.networktools.warning.partial",
                    getStackDisplayName(trackerEntry.getTemplate(), type),
                    totalCount.subtract(remainingToAssign).toString(),
                    totalCount.toString()));
            } else {
                result.failed++;
                result.warnings.add(I18n.translateToLocalFormatted(
                    "cellterminal.networktools.warning.failed",
                    getStackDisplayName(trackerEntry.getTemplate(), type)));
            }
        }

        // Phase 4: Execute assignments - set partitions and inject items
        // IMPORTANT: Track actual injected amounts since cells may reject some items
        CellTerminal.LOGGER.info("[AttributeUnique] Phase 4: Executing {} assignments", assignments.size());
        Set<CellTarget> affectedCells = new HashSet<>();

        for (StackAssignment assignment : assignments) {
            CellTarget target = assignment.targetCell;
            BigStackTracker.Entry extractedEntry = extractedTracker.get(assignment.key);
            String cellName = getCellDisplayName(target.cellStack);
            String itemName = getStackDisplayName(assignment.stack, type);

            if (extractedEntry == null || extractedEntry.getCount().compareTo(BigInteger.ZERO) <= 0) {
                CellTerminal.LOGGER.warn("[AttributeUnique]   No extracted items for {} to inject into '{}'",
                    itemName, cellName);
                result.warnings.add(I18n.translateToLocalFormatted(
                    "cellterminal.networktools.warning.missing_extracted",
                    assignment.key));
                continue;
            }

            // Set partition to this item type
            ItemStack partitionItem = getPartitionItemFromStack(extractedEntry.getTemplate(), type);
            if (!partitionItem.isEmpty()) {
                CellActionHandler.handlePartitionAction(
                    target.tracker.storage, target.tracker.tile, target.slot,
                    PacketPartitionAction.Action.ADD_ITEM,
                    0, partitionItem);
            }

            // Inject items - track what was ACTUALLY injected vs rejected
            long toInject = Math.min(assignment.amountToMove,
                extractedEntry.getCount().min(SafeMath.MAX_LONG).longValue());
            IAEStack<?> injectStack = extractedEntry.getTemplate().copy();
            injectStack.setStackSize(toInject);

            CellTerminal.LOGGER.info("[AttributeUnique]   Injecting {} x{} into '{}'",
                itemName, toInject, cellName);

            long actuallyInjected = injectIntoCellWithTracking(target.cellStack, type, injectStack, source);

            // Only subtract what was actually injected
            if (actuallyInjected > 0) {
                extractedTracker.subtractCount(assignment.key, actuallyInjected);
                CellTerminal.LOGGER.info("[AttributeUnique]     Actually injected: {}, remaining to place: {}",
                    actuallyInjected, extractedEntry.getCount());
            } else {
                CellTerminal.LOGGER.warn("[AttributeUnique]     FAILED to inject into '{}' - cell rejected all items!",
                    cellName);
            }

            affectedCells.add(target);
            target.tracker.tile.markDirty();
        }

        // Phase 5: Handle remaining extracted items - put them back into ANY available cell
        // This handles items that were rejected during Phase 4 injection
        CellTerminal.LOGGER.info("[AttributeUnique] Phase 5: Handling remaining items");
        for (Map.Entry<Object, BigStackTracker.Entry> remaining : extractedTracker.entrySet()) {
            BigStackTracker.Entry entry = remaining.getValue();
            BigInteger remainingCount = entry.getCount();
            if (remainingCount.compareTo(BigInteger.ZERO) <= 0) continue;

            String itemName = getStackDisplayName(entry.getTemplate(), type);
            CellTerminal.LOGGER.warn("[AttributeUnique]   {} x{} still needs placement!", itemName, remainingCount);

            // Try ALL target cells - they may have space even if "used" for another item
            // (cells can hold multiple types until partitioned)
            for (CellTarget cell : targetCells) {
                if (remainingCount.compareTo(BigInteger.ZERO) <= 0) break;

                long testAmount = remainingCount.compareTo(SafeMath.MAX_LONG) > 0
                    ? Long.MAX_VALUE
                    : remainingCount.longValue();

                IAEStack<?> testStack = entry.getTemplate().copy();
                testStack.setStackSize(testAmount);

                long canAccept = simulateInjection(cell.cellStack, type, testStack, source);
                if (canAccept <= 0) {
                    CellTerminal.LOGGER.debug("[AttributeUnique]     Cell '{}' cannot accept {}",
                        getCellDisplayName(cell.cellStack), itemName);
                    continue;
                }

                IAEStack<?> injectStack = entry.getTemplate().copy();
                injectStack.setStackSize(canAccept);

                CellTerminal.LOGGER.info("[AttributeUnique]     Pushing {} x{} back into '{}'",
                    itemName, canAccept, getCellDisplayName(cell.cellStack));

                long actuallyInjected = injectIntoCellWithTracking(cell.cellStack, type, injectStack, source);
                if (actuallyInjected <= 0) {
                    CellTerminal.LOGGER.warn("[AttributeUnique]     FAILED to push back into '{}'",
                        getCellDisplayName(cell.cellStack));
                    continue;
                }

                cell.tracker.tile.markDirty();
                affectedCells.add(cell);

                remainingCount = remainingCount.subtract(BigInteger.valueOf(actuallyInjected));
                CellTerminal.LOGGER.info("[AttributeUnique]     Pushed {} into '{}', {} remaining",
                    actuallyInjected, getCellDisplayName(cell.cellStack), remainingCount);
            }

            if (remainingCount.compareTo(BigInteger.ZERO) > 0) {
                CellTerminal.LOGGER.error("[AttributeUnique] CRITICAL: Could not put back {} x{} - ITEMS LOST!",
                    itemName, remainingCount);
                result.warnings.add(I18n.translateToLocalFormatted(
                    "cellterminal.networktools.warning.lost",
                    remainingCount.toString(), getStackDisplayName(entry.getTemplate(), type)));
            }
        }

        // Mark all target cells dirty
        for (CellTarget target : targetCells) target.tracker.tile.markDirty();

        result.cellsAffected = affectedCells.size();
        return result;
    }

    /**
     * Probe a cell's true capacity by simulating injection of large amounts.
     * Some modded cells (like Hyper-Density) report small byte capacity but can hold billions.
     * Tests with Integer.MAX_VALUE first, then Long.MAX_VALUE to find the true limit.
     */
    private static long probeCellCapacity(ItemStack cellStack, CellType type, IActionSource source) {
        // Create a dummy stack for capacity testing
        IAEStack<?> testStack = createDummyStack(type);
        if (testStack == null) return 0;

        // First test with Integer.MAX_VALUE - this catches most normal cells
        testStack.setStackSize(Integer.MAX_VALUE);
        long intCapacity = simulateInjection(cellStack, type, testStack, source);

        // If cell accepted all of Integer.MAX_VALUE, test with Long.MAX_VALUE
        if (intCapacity >= Integer.MAX_VALUE) {
            testStack.setStackSize(Long.MAX_VALUE);
            return simulateInjection(cellStack, type, testStack, source);
        }

        return intCapacity;
    }

    /**
     * Create a dummy stack for capacity testing.
     * Uses a common item/fluid that any cell should be able to accept.
     */
    @SuppressWarnings("unchecked")
    private static IAEStack<?> createDummyStack(CellType type) {
        if (type == CellType.ITEM) {
            // Use stone as a generic test item
            ItemStack testItem = new ItemStack(net.minecraft.init.Blocks.STONE);
            return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
                .createStack(testItem);
        } else if (type == CellType.FLUID) {
            // Use water as a generic test fluid
            FluidStack testFluid = new FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1000);
            return AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class)
                .createStack(testFluid);
        }

        // Essentia cells - return null, will use byte capacity
        return null;
    }

    /**
     * Simulate injection to check how much of a stack can be accepted by a cell.
     * Returns the amount that can be accepted (0 if incompatible or full).
     */
    @SuppressWarnings("unchecked")
    private static long simulateInjection(ItemStack cellStack, CellType type,
                                           IAEStack<?> stack, IActionSource source) {
        if (type == CellType.ITEM && stack instanceof IAEItemStack) {
            ICellInventoryHandler<IAEItemStack> handler = getCellHandler(cellStack,
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            if (handler == null || handler.getCellInv() == null) return 0;

            ICellInventory<IAEItemStack> cellInv = handler.getCellInv();

            // Check if cell can hold a new type (if this is a new type for the cell)
            if (!cellInv.canHoldNewItem()) {
                // Check if this item type already exists in the cell
                IItemList<IAEItemStack> existing = cellInv.getAvailableItems(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createList());
                boolean typeExists = false;
                for (IAEItemStack ex : existing) {
                    if (ex.isSameType((IAEItemStack) stack)) {
                        typeExists = true;
                        break;
                    }
                }
                if (!typeExists) return 0;
            }

            // Simulate injection to check compatibility and capacity
            IAEItemStack rejected = cellInv.injectItems(((IAEItemStack) stack).copy(), Actionable.SIMULATE, source);
            if (rejected == null) return stack.getStackSize();

            return stack.getStackSize() - rejected.getStackSize();

        } else if (type == CellType.FLUID && stack instanceof IAEFluidStack) {
            ICellInventoryHandler<IAEFluidStack> handler = getCellHandler(cellStack,
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
            if (handler == null || handler.getCellInv() == null) return 0;

            ICellInventory<IAEFluidStack> cellInv = handler.getCellInv();

            if (!cellInv.canHoldNewItem()) {
                IItemList<IAEFluidStack> existing = cellInv.getAvailableItems(
                    AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class).createList());
                boolean typeExists = false;
                IAEFluidStack fluidStack = (IAEFluidStack) stack;
                for (IAEFluidStack ex : existing) {
                    // Compare fluids by checking if they are the same fluid type
                    if (ex.getFluid() == fluidStack.getFluid()) {
                        typeExists = true;
                        break;
                    }
                }

                if (!typeExists) return 0;
            }

            IAEFluidStack rejected = cellInv.injectItems(((IAEFluidStack) stack).copy(), Actionable.SIMULATE, source);
            if (rejected == null) return stack.getStackSize();

            return stack.getStackSize() - rejected.getStackSize();

        } else if (type == CellType.ESSENTIA && ThaumicEnergisticsIntegration.isModLoaded()) {
            return ThaumicEnergisticsIntegration.simulateEssentiaInjection(cellStack, stack, source);
        }

        return 0;
    }

    /**
     * Get the total capacity of a cell in bytes.
     */
    private static long getCellCapacity(ItemStack cellStack, CellType type) {
        if (type == CellType.ITEM) {
            ICellInventoryHandler<IAEItemStack> handler = getCellHandler(cellStack,
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            if (handler != null && handler.getCellInv() != null) {
                return handler.getCellInv().getTotalBytes();
            }
        } else if (type == CellType.FLUID) {
            ICellInventoryHandler<IAEFluidStack> handler = getCellHandler(cellStack,
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
            if (handler != null && handler.getCellInv() != null) {
                return handler.getCellInv().getTotalBytes();
            }
        } else if (type == CellType.ESSENTIA && ThaumicEnergisticsIntegration.isModLoaded()) {
            return ThaumicEnergisticsIntegration.getEssentiaCellCapacity(cellStack);
        }

        return 0;
    }

    /**
     * Get a display name for a stack for error messages.
     */
    private static String getStackDisplayName(IAEStack<?> stack, CellType type) {
        if (type == CellType.ITEM && stack instanceof IAEItemStack) {
            return ((IAEItemStack) stack).createItemStack().getDisplayName();
        } else if (type == CellType.FLUID && stack instanceof IAEFluidStack) {
            FluidStack fs = ((IAEFluidStack) stack).getFluidStack();
            return fs != null ? fs.getLocalizedName() : "Unknown Fluid";
        } else if (type == CellType.ESSENTIA && ThaumicEnergisticsIntegration.isModLoaded()) {
            return ThaumicEnergisticsIntegration.getEssentiaStackName(stack);
        }

        return "Unknown";
    }

    /**
     * Collect items with their full counts from a cell into TypeStats.
     * Unlike collectUniqueItemsForType, this tracks the total count of each item type.
     * Uses BigStackTracker for lossless aggregation of counts exceeding Long.MAX_VALUE.
     */
    private static void collectItemsWithCountsForType(ItemStack cellStack, CellType type, TypeStats stats) {
        if (type == CellType.ITEM) {
            ICellInventoryHandler<IAEItemStack> handler = getCellHandler(cellStack,
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            if (handler == null || handler.getCellInv() == null) return;

            IItemList<IAEItemStack> available = handler.getCellInv().getAvailableItems(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createList());

            for (IAEItemStack stack : available) {
                ItemStackKey key = ItemStackKey.of(stack.createItemStack());
                if (key == null) continue;

                // BigStackTracker handles arbitrarily large totals without overflow
                if (!stats.stackTracker.containsKey(key)) {
                    stats.uniqueKeys.add(key);
                    ItemStack copy = stack.createItemStack();
                    copy.setCount(1);
                    stats.uniqueStacks.add(copy);
                }
                stats.stackTracker.add(key, stack);
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

                FluidStackKey key = FluidStackKey.of(fluidStack);
                if (key == null) continue;

                // BigStackTracker handles arbitrarily large totals without overflow
                if (!stats.stackTracker.containsKey(key)) {
                    stats.uniqueKeys.add(key);
                    stats.uniqueStacks.add(stack.asItemStackRepresentation());
                }
                stats.stackTracker.add(key, stack);
            }

        } else if (type == CellType.ESSENTIA && ThaumicEnergisticsIntegration.isModLoaded()) {
            ThaumicEnergisticsIntegration.collectEssentiaWithCounts(cellStack, stats.uniqueKeys,
                stats.uniqueStacks, stats.stackTracker);
        }
    }

    /**
     * Refresh all storage devices that were affected by the redistribution.
     * This forces drives to recalculate their cell state by simulating cell removal/reinsertion.
     */
    private static void refreshAffectedStorage(Map<CellType, TypeStats> statsByType, IGrid grid) {
        // Collect all affected cell slots per storage tracker
        Map<StorageTracker, Set<Integer>> affectedSlots = new LinkedHashMap<>();

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
     * Uses BigStackTracker for lossless aggregation of extracted items.
     *
     * Note: This method extracts in a LOOP until the cell is empty.
     * A single extraction may not drain cells with counts exceeding Long.MAX_VALUE,
     * so we must keep extracting until getUsedBytes() reports 0.
     */
    private static void extractAllFromCellToBigTracker(ItemStack cellStack, CellType type,
                                                        BigStackTracker tracker,
                                                        IActionSource source) {
        if (type == CellType.ITEM) {
            ICellInventoryHandler<IAEItemStack> handler = getCellHandler(cellStack,
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            if (handler == null || handler.getCellInv() == null) return;

            ICellInventory<IAEItemStack> cellInv = handler.getCellInv();
            IStorageChannel<IAEItemStack> channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);

            // Keep extracting until cell is empty - handles cells with counts > Long.MAX_VALUE
            int safetyCounter = 0;
            while (cellInv.getUsedBytes() > 0 && safetyCounter++ < 1000) {
                IItemList<IAEItemStack> available = cellInv.getAvailableItems(channel.createList());
                boolean extractedAny = false;

                for (IAEItemStack stack : available) {
                    // Extract from cell
                    IAEItemStack extracted = cellInv.extractItems(stack.copy(), Actionable.MODULATE, source);
                    if (extracted == null || extracted.getStackSize() <= 0) continue;

                    extractedAny = true;
                    ItemStackKey key = ItemStackKey.of(stack.createItemStack());
                    if (key == null) continue;

                    // BigStackTracker handles arbitrary totals without overflow
                    tracker.add(key, extracted);
                }

                // If we couldn't extract anything but cell still has bytes, something is wrong
                if (!extractedAny) break;
            }

            cellInv.persist();

        } else if (type == CellType.FLUID) {
            ICellInventoryHandler<IAEFluidStack> handler = getCellHandler(cellStack,
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
            if (handler == null || handler.getCellInv() == null) return;

            ICellInventory<IAEFluidStack> cellInv = handler.getCellInv();
            IStorageChannel<IAEFluidStack> channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);

            // Keep extracting until cell is empty - handles cells with counts > Long.MAX_VALUE
            int safetyCounter = 0;
            while (cellInv.getUsedBytes() > 0 && safetyCounter++ < 1000) {
                IItemList<IAEFluidStack> available = cellInv.getAvailableItems(channel.createList());
                boolean extractedAny = false;

                for (IAEFluidStack stack : available) {
                    IAEFluidStack extracted = cellInv.extractItems(stack.copy(), Actionable.MODULATE, source);
                    if (extracted == null || extracted.getStackSize() <= 0) continue;

                    extractedAny = true;
                    FluidStackKey key = FluidStackKey.of(stack.getFluidStack());
                    if (key == null) continue;

                    // BigStackTracker handles arbitrary totals without overflow
                    tracker.add(key, extracted);
                }

                // If we couldn't extract anything but cell still has bytes, something is wrong
                if (!extractedAny) break;
            }

            cellInv.persist();

        } else if (type == CellType.ESSENTIA && ThaumicEnergisticsIntegration.isModLoaded()) {
            // Essentia cells use a different storage channel, delegate to integration
            // Essentia keys remain as String for compatibility with integration layer
            ThaumicEnergisticsIntegration.extractAllEssentiaFromCellToBigTracker(cellStack, tracker);
        }
    }

    /**
     * Inject a stack into a cell, returning the amount actually injected.
     * This is critical for tracking - cells may reject items due to capacity or type restrictions.
     */
    @SuppressWarnings("unchecked")
    private static long injectIntoCellWithTracking(ItemStack cellStack, CellType type,
                                                    IAEStack<?> stack, IActionSource source) {
        long requested = stack.getStackSize();

        if (type == CellType.ITEM && stack instanceof IAEItemStack) {
            ICellInventoryHandler<IAEItemStack> handler = getCellHandler(cellStack,
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            if (handler == null || handler.getCellInv() == null) return 0;

            ICellInventory<IAEItemStack> cellInv = handler.getCellInv();
            IAEItemStack rejected = cellInv.injectItems((IAEItemStack) stack, Actionable.MODULATE, source);
            cellInv.persist();

            if (rejected == null) return requested;

            return requested - rejected.getStackSize();

        } else if (type == CellType.FLUID && stack instanceof IAEFluidStack) {
            ICellInventoryHandler<IAEFluidStack> handler = getCellHandler(cellStack,
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
            if (handler == null || handler.getCellInv() == null) return 0;

            ICellInventory<IAEFluidStack> cellInv = handler.getCellInv();
            IAEFluidStack rejected = cellInv.injectItems((IAEFluidStack) stack, Actionable.MODULATE, source);
            cellInv.persist();

            if (rejected == null) return requested;

            return requested - rejected.getStackSize();

        } else if (type == CellType.ESSENTIA && ThaumicEnergisticsIntegration.isModLoaded()) {
            // Essentia injection - delegate to integration
            // TODO: Track actual injection for essentia
            ThaumicEnergisticsIntegration.injectEssentiaIntoCell(cellStack, stack);
            return requested;
        }

        return 0;
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
                ItemStackKey key = ItemStackKey.of(stack.createItemStack());
                if (key != null && stats.uniqueKeys.add(key)) {
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

                FluidStackKey key = FluidStackKey.of(fluidStack);
                if (key != null && stats.uniqueKeys.add(key)) {
                    stats.uniqueStacks.add(stack.asItemStackRepresentation());
                }
            }

        } else if (type == CellType.ESSENTIA && ThaumicEnergisticsIntegration.isModLoaded()) {
            ThaumicEnergisticsIntegration.collectUniqueEssentiaFromCell(cellStack, stats.uniqueKeys, stats.uniqueStacks);
        }
    }

    /**
     * Helper class to track statistics per cell type.
     * Keys are ItemStackKey for items, FluidStackKey for fluids, or String for essentia.
     * Uses BigStackTracker to support totals exceeding Long.MAX_VALUE without data loss.
     */
    private static class TypeStats {
        final List<CellTarget> filteredCells = new ArrayList<>();
        final List<CellTarget> emptyCells = new ArrayList<>();
        final Set<Object> uniqueKeys = new HashSet<>();
        final List<ItemStack> uniqueStacks = new ArrayList<>();
        /** Tracks item counts using BigInteger to prevent overflow data loss */
        final BigStackTracker stackTracker = new BigStackTracker();
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

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> ICellInventoryHandler<T> getCellHandler(
            ItemStack cellStack, IStorageChannel<T> channel) {
        ICellHandler handler = AEApi.instance().registries().cell().getHandler(cellStack);
        if (handler == null) return null;

        return handler.getCellInventory(cellStack, null, channel);
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
        /** Cell capacity in bytes, used for sorting cells by capacity */
        long capacity = 0;

        CellTarget(StorageTracker tracker, int slot, ItemStack cellStack) {
            this.tracker = tracker;
            this.slot = slot;
            this.cellStack = cellStack;
        }
    }
}
