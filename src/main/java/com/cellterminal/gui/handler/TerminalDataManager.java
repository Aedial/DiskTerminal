package com.cellterminal.gui.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import com.cellterminal.client.AdvancedSearchParser;
import com.cellterminal.client.CellContentRow;
import com.cellterminal.client.CellFilter;
import com.cellterminal.client.CellFilter.State;
import com.cellterminal.client.CellInfo;
import com.cellterminal.client.EmptySlotInfo;
import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.client.SlotLimit;
import com.cellterminal.client.StorageBusContentRow;
import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.client.StorageInfo;
import com.cellterminal.client.TabStateManager;
import com.cellterminal.client.TempCellInfo;
import com.cellterminal.config.CellTerminalClientConfig;
import com.cellterminal.network.chunked.DeltaApplier;
import com.cellterminal.network.chunked.PayloadMode;


/**
 * Manages storage data and line building for Cell Terminal GUI.
 * <p>
 * Filtering is "snapshot-based" - the list of visible items is only rebuilt when
 * filters/search/tab explicitly change, not when data updates come from the server.
 * This prevents items from disappearing in real-time as their contents change.
 */
public class TerminalDataManager {

    private static final int SLOTS_PER_ROW = 8;
    private static final int SLOTS_PER_ROW_BUS = 9;
    private static final int MAX_PARTITION_SLOTS = 63;

    private final Map<Long, StorageInfo> storageMap = new LinkedHashMap<>();
    private final Map<Long, StorageBusInfo> storageBusMap = new LinkedHashMap<>();
    // Raw NBT per temp-cell slot, keyed by slot index (also used as the delta ID).
    // Owned by applyTempCells / rebuildTempAreaLines.
    private final Map<Long, NBTTagCompound> tempCellSlotData = new LinkedHashMap<>();
    private final List<Object> lines = new ArrayList<>();
    private final List<Object> inventoryLines = new ArrayList<>();
    private final List<Object> partitionLines = new ArrayList<>();
    private final List<Object> storageBusInventoryLines = new ArrayList<>();
    private final List<Object> storageBusPartitionLines = new ArrayList<>();
    private final List<Object> tempAreaLines = new ArrayList<>();

    private BlockPos terminalPos = BlockPos.ORIGIN;
    private int terminalDimension = 0;

    // Current filter settings
    private String searchFilter = "";
    private SearchFilterMode searchMode = SearchFilterMode.MIXED;
    private final Map<CellFilter, State> activeFilters = new EnumMap<>(CellFilter.class);

    // Advanced search matcher (cached for performance)
    private AdvancedSearchParser.SearchMatcher advancedMatcher = null;
    private boolean useAdvancedSearch = false;
    private List<String> advancedSearchError = null;

    // Snapshot of visible cell keys (storageId:slot) when filters were last applied
    // Format: "storageId:slot" for cells, "bus:busId" for storage buses
    private final Set<String> visibleCellSnapshot = new HashSet<>();
    private final Set<String> visibleCellSnapshotInventory = new HashSet<>();
    private final Set<String> visibleCellSnapshotPartition = new HashSet<>();
    private final Set<Long> visibleBusSnapshotInventory = new HashSet<>();
    private final Set<Long> visibleBusSnapshotPartition = new HashSet<>();

    // Track whether we have initial data (first update should always rebuild)
    private boolean hasInitialData = false;

    public Map<Long, StorageInfo> getStorageMap() {
        return storageMap;
    }

    public Map<Long, StorageBusInfo> getStorageBusMap() {
        return storageBusMap;
    }

    public List<Object> getLines() {
        return lines;
    }

    public List<Object> getInventoryLines() {
        return inventoryLines;
    }

    public List<Object> getPartitionLines() {
        return partitionLines;
    }

    public List<Object> getStorageBusInventoryLines() {
        return storageBusInventoryLines;
    }

    public List<Object> getStorageBusPartitionLines() {
        return storageBusPartitionLines;
    }

    public List<Object> getTempAreaLines() {
        return tempAreaLines;
    }

    /**
     * Apply the META channel payload (always FULL).
     * Carries terminalPos / terminalDim / networkId so the client can verify its current
     * network context.
     *
     * @return the {@code networkId} field if present, or {@link Long#MIN_VALUE} if absent
     */
    public long applyMeta(PayloadMode mode, NBTTagCompound data) {
        if (data.hasKey("terminalPos")) {
            this.terminalPos = BlockPos.fromLong(data.getLong("terminalPos"));
            this.terminalDimension = data.getInteger("terminalDim");
        }
        return data.hasKey("networkId") ? data.getLong("networkId") : Long.MIN_VALUE;
    }

    /**
     * Apply the STORAGES channel payload (FULL or DELTA).
     */
    public void applyStorages(PayloadMode mode, NBTTagCompound data) {
        DeltaApplier.apply(mode, data, this.storageMap, StorageInfo::new, StorageInfo::getId);
        finishUpdate();
    }

    /**
     * Apply the BUSES channel payload (FULL or DELTA).
     */
    public void applyBuses(PayloadMode mode, NBTTagCompound data) {
        DeltaApplier.apply(mode, data, this.storageBusMap, StorageBusInfo::new, StorageBusInfo::getId);
        finishUpdate();
    }

    /**
     * Apply the TEMP_CELLS channel payload (FULL or DELTA).
     * Temp cell entries use {@code id == tempSlot} so DELTA application can be uniform.
     */
    public void applyTempCells(PayloadMode mode, NBTTagCompound data) {
        // Maintain a slot -> NBT raw map. We rebuild tempAreaLines from that map after each apply.
        DeltaApplier.applyRaw(mode, data, "id", "tempCells",
            this.tempCellSlotData::clear,
            (id, entry) -> {
                if (entry == null) {
                    this.tempCellSlotData.remove(id);
                } else {
                    this.tempCellSlotData.put(id, entry);
                }
                return null;
            });

        rebuildTempAreaLines();
        finishUpdate();
    }

    /**
     * Common bookkeeping after any section channel update.
     */
    private void finishUpdate() {
        if (!hasInitialData) {
            hasInitialData = true;
            rebuildLines();
        } else {
            rebuildLinesFromSnapshot();
        }
    }

    /**
     * Rebuild {@link #tempAreaLines} from the current {@link #tempCellSlotData} map.
     * Slots are visited in increasing order of their tempSlot index.
     */
    private void rebuildTempAreaLines() {
        this.tempAreaLines.clear();

        // Sort by tempSlot ascending so insert order matches the GUI layout regardless of
        // which delta order entries arrived in.
        List<Long> slots = new ArrayList<>(this.tempCellSlotData.keySet());
        Collections.sort(slots);

        for (Long slotKey : slots) {
            NBTTagCompound slotData = this.tempCellSlotData.get(slotKey);
            int tempSlot = slotData.getInteger("tempSlot");

            if (slotData.hasKey("cellData")) {
                NBTTagCompound cellData = slotData.getCompoundTag("cellData");
                CellInfo cellInfo = new CellInfo(cellData);
                ItemStack cellStack = cellInfo.getCellItem();

                TempCellInfo tempCell = new TempCellInfo(tempSlot, cellStack, cellInfo);
                this.tempAreaLines.add(tempCell);

                // Add content rows for inventory view (use 9 slots per row to match storage bus)
                // Always show at least 1 row, even if cell is empty
                int contentCount = cellInfo.getContents().size();
                int contentRows = Math.max(1, (contentCount + SLOTS_PER_ROW_BUS - 1) / SLOTS_PER_ROW_BUS);
                for (int row = 0; row < contentRows; row++) {
                    this.tempAreaLines.add(new CellContentRow(cellInfo, row * SLOTS_PER_ROW_BUS, row == 0, false));
                }

                // Add partition rows (use 9 slots per row to match storage bus)
                // Always show at least 1 row, even if no partition is set
                int highestPartitionSlot = getHighestNonEmptyPartitionSlot(cellInfo, SLOTS_PER_ROW_BUS);
                int partitionRows = Math.max(1, (highestPartitionSlot + SLOTS_PER_ROW_BUS) / SLOTS_PER_ROW_BUS);
                for (int row = 0; row < partitionRows; row++) {
                    this.tempAreaLines.add(new CellContentRow(cellInfo, row * SLOTS_PER_ROW_BUS, row == 0, true));
                }
            } else {
                // Empty slot - still show it so users can place cells there
                TempCellInfo emptySlot = new TempCellInfo(tempSlot);
                this.tempAreaLines.add(emptySlot);
            }
        }
    }

    /**
     * Set the search filter text and mode, then rebuild lines.
     */
    public void setSearchFilter(String filter, SearchFilterMode mode) {
        this.searchMode = mode;

        // Check for advanced search syntax
        if (AdvancedSearchParser.isAdvancedQuery(filter)) {
            AdvancedSearchParser.ParseResult result = AdvancedSearchParser.parse(filter);
            if (result.isSuccess()) {
                this.advancedMatcher = result.getMatcher();
                this.useAdvancedSearch = true;
                this.advancedSearchError = null;
            } else {
                this.advancedMatcher = null;
                this.useAdvancedSearch = false;
                this.advancedSearchError = result.getErrors();
            }
            this.searchFilter = "";  // Don't use simple search when advanced is active
        } else {
            this.useAdvancedSearch = false;
            this.advancedMatcher = null;
            this.advancedSearchError = null;
            this.searchFilter = filter.toLowerCase(Locale.ROOT).trim();
        }

        rebuildLines();
    }

    /**
     * Check if there is an advanced search parse error.
     */
    public boolean hasAdvancedSearchError() {
        return advancedSearchError != null && !advancedSearchError.isEmpty();
    }

    /**
     * Get the advanced search error message.
     */
    public List<String> getAdvancedSearchError() {
        return advancedSearchError;
    }

    /**
     * Get the current search filter text (lowercase, trimmed).
     */
    public String getSearchFilter() {
        return searchFilter;
    }

    /**
     * Check if advanced search is currently active.
     */
    public boolean isUsingAdvancedSearch() {
        return useAdvancedSearch;
    }

    /**
     * Get the advanced search matcher (may be null if not using advanced search).
     */
    public AdvancedSearchParser.SearchMatcher getAdvancedMatcher() {
        return advancedMatcher;
    }

    /**
     * Set the active cell/storage bus filters and rebuild lines.
     */
    public void setActiveFilters(Map<CellFilter, State> filters) {
        this.activeFilters.clear();
        this.activeFilters.putAll(filters);
        rebuildLines();
    }

    /**
     * Update filters without rebuilding (call rebuildLines separately).
     */
    public void updateFiltersQuiet(Map<CellFilter, State> filters) {
        this.activeFilters.clear();
        this.activeFilters.putAll(filters);
    }

    /**
     * Full rebuild of lines with filter evaluation.
     * This updates the snapshots with which items pass current filters.
     */
    public void rebuildLines() {
        // Clear snapshots - we're re-evaluating filters
        this.visibleCellSnapshot.clear();
        this.visibleCellSnapshotInventory.clear();
        this.visibleCellSnapshotPartition.clear();
        this.visibleBusSnapshotInventory.clear();
        this.visibleBusSnapshotPartition.clear();

        this.lines.clear();
        this.inventoryLines.clear();
        this.partitionLines.clear();
        this.storageBusInventoryLines.clear();
        this.storageBusPartitionLines.clear();

        rebuildCellLines(true);
        rebuildStorageBusLines(true);
    }

    /**
     * Rebuild lines using the existing snapshot (doesn't re-evaluate filters).
     * Items that were visible before stay visible, even if they would no longer match filters.
     * Items that weren't visible stay hidden, even if they would now match.
     */
    private void rebuildLinesFromSnapshot() {
        this.lines.clear();
        this.inventoryLines.clear();
        this.partitionLines.clear();
        this.storageBusInventoryLines.clear();
        this.storageBusPartitionLines.clear();

        rebuildCellLines(false);
        rebuildStorageBusLines(false);
    }

    /**
     * Rebuild lines for cell storages (drives/chests).
     * @param evaluateFilters If true, re-evaluate filters and update snapshots.
     *                        If false, use existing snapshots to determine visibility.
     */
    private void rebuildCellLines(boolean evaluateFilters) {
        List<StorageInfo> sortedStorages = new ArrayList<>(this.storageMap.values());
        sortedStorages.sort(createStorageComparator());

        for (StorageInfo storage : sortedStorages) {
            // Track if we added any cells from this storage for each list
            boolean addedToLines = false;
            boolean addedToInventoryLines = false;
            boolean addedToPartitionLines = false;

            // Keep track of where to insert storage header if needed
            int linesStartIndex = this.lines.size();
            int inventoryLinesStartIndex = this.inventoryLines.size();
            int partitionLinesStartIndex = this.partitionLines.size();

            for (int slot = 0; slot < storage.getSlotCount(); slot++) {
                CellInfo cell = storage.getCellAtSlot(slot);
                String cellKey = storage.getId() + ":" + slot;

                if (cell != null) {
                    cell.setParentStorageId(storage.getId());

                    boolean showInTerminal;
                    boolean showInInventory;
                    boolean showInPartition;

                    if (evaluateFilters) {
                        // Evaluate filters and update snapshots
                        if (!cellMatchesCellFilters(cell)) continue;

                        if (useAdvancedSearch && advancedMatcher != null) {
                            showInTerminal = advancedMatcher.matchesCell(cell, storage, searchMode);
                            showInInventory = advancedMatcher.matchesCell(cell, storage, SearchFilterMode.INVENTORY);
                            showInPartition = advancedMatcher.matchesCell(cell, storage, SearchFilterMode.PARTITION);
                        } else {
                            boolean matchesInventory = cellMatchesSearchFilter(cell, true);
                            boolean matchesPartition = cellMatchesSearchFilter(cell, false);
                            showInTerminal = matchesCellForMode(matchesInventory, matchesPartition);
                            showInInventory = matchesInventory;
                            showInPartition = matchesPartition;
                        }

                        // Update snapshots
                        if (showInTerminal) visibleCellSnapshot.add(cellKey);
                        if (showInInventory) visibleCellSnapshotInventory.add(cellKey);
                        if (showInPartition) visibleCellSnapshotPartition.add(cellKey);
                    } else {
                        // Use existing snapshots
                        showInTerminal = visibleCellSnapshot.contains(cellKey);
                        showInInventory = visibleCellSnapshotInventory.contains(cellKey);
                        showInPartition = visibleCellSnapshotPartition.contains(cellKey);
                    }

                    // Terminal tab
                    if (showInTerminal) {
                        if (!addedToLines) {
                            this.lines.add(linesStartIndex, storage);
                            addedToLines = true;
                        }

                        // Use per-tab state manager for Terminal tab expansion so it follows saved state
                        if (TabStateManager.getInstance().isExpanded(TabStateManager.TabType.TERMINAL, storage.getId())) this.lines.add(cell);
                    }

                    // Inventory tab
                    if (showInInventory) {
                        if (!addedToInventoryLines) {
                            this.inventoryLines.add(inventoryLinesStartIndex, storage);
                            addedToInventoryLines = true;
                        }

                        // Only add content rows if storage is expanded in this tab
                        if (TabStateManager.getInstance().isExpanded(TabStateManager.TabType.INVENTORY, storage.getId())) {
                            SlotLimit slotLimit = CellTerminalClientConfig.getInstance().getCellSlotLimit();
                            int contentCount = slotLimit.getEffectiveCount(cell.getContents().size());
                            int contentRows = Math.max(1, (contentCount + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW);
                            for (int row = 0; row < contentRows; row++) {
                                this.inventoryLines.add(new CellContentRow(cell, row * SLOTS_PER_ROW, row == 0));
                            }
                        }
                    }

                    // Partition tab
                    if (showInPartition) {
                        if (!addedToPartitionLines) {
                            this.partitionLines.add(partitionLinesStartIndex, storage);
                            addedToPartitionLines = true;
                        }

                        // Only add content rows if storage is expanded in this tab
                        if (TabStateManager.getInstance().isExpanded(TabStateManager.TabType.PARTITION, storage.getId())) {
                            int highestSlot = getHighestNonEmptyPartitionSlot(cell);
                            int partitionRows = Math.max(1, (highestSlot + SLOTS_PER_ROW) / SLOTS_PER_ROW);
                            for (int row = 0; row < partitionRows; row++) {
                                this.partitionLines.add(new CellContentRow(cell, row * SLOTS_PER_ROW, row == 0));
                            }
                        }
                    }
                } else {
                    // Empty slots - only show if no filter active (and only during filter evaluation)
                    if (evaluateFilters && searchFilter.isEmpty() && !useAdvancedSearch) {
                        if (!addedToInventoryLines) {
                            this.inventoryLines.add(inventoryLinesStartIndex, storage);
                            addedToInventoryLines = true;
                        }

                        if (!addedToPartitionLines) {
                            this.partitionLines.add(partitionLinesStartIndex, storage);
                            addedToPartitionLines = true;
                        }

                        // Only add empty slot rows if the storage is expanded for that tab
                        if (TabStateManager.getInstance().isExpanded(TabStateManager.TabType.INVENTORY, storage.getId())) {
                            EmptySlotInfo emptySlot = new EmptySlotInfo(storage.getId(), slot);
                            this.inventoryLines.add(emptySlot);
                        }

                        if (TabStateManager.getInstance().isExpanded(TabStateManager.TabType.PARTITION, storage.getId())) {
                            EmptySlotInfo emptySlot2 = new EmptySlotInfo(storage.getId(), slot);
                            this.partitionLines.add(emptySlot2);
                        }

                        // Track empty slots in snapshot for consistency
                        visibleCellSnapshotInventory.add(cellKey);
                        visibleCellSnapshotPartition.add(cellKey);
                    } else if (!evaluateFilters) {
                        // Show empty slots if they were in the snapshot
                        if (visibleCellSnapshotInventory.contains(cellKey)) {
                            if (!addedToInventoryLines) {
                                this.inventoryLines.add(inventoryLinesStartIndex, storage);
                                addedToInventoryLines = true;
                            }

                            if (TabStateManager.getInstance().isExpanded(TabStateManager.TabType.INVENTORY, storage.getId())) {
                                EmptySlotInfo emptySlot = new EmptySlotInfo(storage.getId(), slot);
                                this.inventoryLines.add(emptySlot);
                            }
                        }

                        if (visibleCellSnapshotPartition.contains(cellKey)) {
                            if (!addedToPartitionLines) {
                                this.partitionLines.add(partitionLinesStartIndex, storage);
                                addedToPartitionLines = true;
                            }

                            if (TabStateManager.getInstance().isExpanded(TabStateManager.TabType.PARTITION, storage.getId())) {
                                EmptySlotInfo emptySlot = new EmptySlotInfo(storage.getId(), slot);
                                this.partitionLines.add(emptySlot);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Rebuild lines for storage bus inventory and partition tabs.
     * Storage buses are sorted by position then by facing.
     * Each bus has a header row (StorageBusInfo) followed by content rows (StorageBusContentRow).
     * @param evaluateFilters If true, re-evaluate filters and update snapshots.
     *                        If false, use existing snapshots to determine visibility.
     */
    private void rebuildStorageBusLines(boolean evaluateFilters) {
        List<StorageBusInfo> sortedBuses = new ArrayList<>(this.storageBusMap.values());
        sortedBuses.sort(createStorageBusComparator());

        for (StorageBusInfo bus : sortedBuses) {
            long busId = bus.getId();
            boolean showInInventory;
            boolean showInPartition;

            if (evaluateFilters) {
                // First check storage bus type filters
                if (!storageBusMatchesCellFilters(bus)) continue;

                // Check advanced search first if active
                if (useAdvancedSearch && advancedMatcher != null) {
                    showInInventory = advancedMatcher.matchesStorageBus(bus, SearchFilterMode.INVENTORY);
                    showInPartition = advancedMatcher.matchesStorageBus(bus, SearchFilterMode.PARTITION);
                } else {
                    showInInventory = storageBusMatchesSearchFilter(bus, true);
                    showInPartition = storageBusMatchesSearchFilter(bus, false);
                }

                // Update snapshots
                if (showInInventory) visibleBusSnapshotInventory.add(busId);
                if (showInPartition) visibleBusSnapshotPartition.add(busId);
            } else {
                // Use existing snapshots
                showInInventory = visibleBusSnapshotInventory.contains(busId);
                showInPartition = visibleBusSnapshotPartition.contains(busId);
            }

            // Storage Bus Inventory tab - add header then content rows
            if (showInInventory) addStorageBusToInventoryLines(bus);

            // Storage Bus Partition tab - add header then content rows
            if (showInPartition) addStorageBusToPartitionLines(bus);
        }
    }

    /**
     * Add a storage bus to the inventory lines (used by advanced search).
     */
    private void addStorageBusToInventoryLines(StorageBusInfo bus) {
        this.storageBusInventoryLines.add(bus);  // Header row

        // Only add content rows if storage bus is expanded in this tab
        if (!TabStateManager.getInstance().isBusExpanded(TabStateManager.TabType.STORAGE_BUS_INVENTORY, bus.getId())) {
            return;
        }

        SlotLimit slotLimit = CellTerminalClientConfig.getInstance().getBusSlotLimit();
        int contentCount = slotLimit.getEffectiveCount(bus.getContents().size());
        int contentRows = Math.max(1, (contentCount + SLOTS_PER_ROW_BUS - 1) / SLOTS_PER_ROW_BUS);

        for (int row = 0; row < contentRows; row++) {
            this.storageBusInventoryLines.add(new StorageBusContentRow(bus, row * SLOTS_PER_ROW_BUS, row == 0));
        }
    }

    /**
     * Add a storage bus to the partition lines (used by advanced search).
     */
    private void addStorageBusToPartitionLines(StorageBusInfo bus) {
        this.storageBusPartitionLines.add(bus);  // Header row

        // Only add content rows if storage bus is expanded in this tab
        if (!TabStateManager.getInstance().isBusExpanded(TabStateManager.TabType.STORAGE_BUS_PARTITION, bus.getId())) {
            return;
        }

        int availableSlots = bus.getAvailableConfigSlots();
        int highestSlot = getHighestNonEmptyStorageBusPartitionSlot(bus);

        int partitionRows;
        if (highestSlot < 0) {
            partitionRows = 1;
        } else {
            int currentRow = highestSlot / SLOTS_PER_ROW_BUS;
            partitionRows = currentRow + 1;

            int lastSlotOfCurrentRow = (currentRow + 1) * SLOTS_PER_ROW_BUS - 1;
            if (highestSlot == lastSlotOfCurrentRow && lastSlotOfCurrentRow < availableSlots - 1) {
                partitionRows++;
            }
        }

        partitionRows = Math.min(partitionRows, (availableSlots + SLOTS_PER_ROW_BUS - 1) / SLOTS_PER_ROW_BUS);

        for (int row = 0; row < partitionRows; row++) {
            this.storageBusPartitionLines.add(
                new StorageBusContentRow(bus, row * SLOTS_PER_ROW_BUS, row == 0));
        }
    }

    /**
     * Check if a storage bus matches the search filter.
     * @param bus The storage bus to check
     * @param checkInventory true to check inventory contents, false to check partition
     * @return true if the bus matches the filter or if filter is empty
     */
    private boolean storageBusMatchesSearchFilter(StorageBusInfo bus, boolean checkInventory) {
        if (searchFilter.isEmpty()) return true;

        List<ItemStack> itemsToCheck = checkInventory ? bus.getContents() : bus.getPartition();

        for (ItemStack stack : itemsToCheck) {
            if (stack.isEmpty()) continue;

            // Check localized display name
            String displayName = stack.getDisplayName().toLowerCase(Locale.ROOT);
            if (displayName.contains(searchFilter)) return true;

            // Check registry name (internal ID)
            if (stack.getItem().getRegistryName() != null) {
                String registryName = stack.getItem().getRegistryName().toString().toLowerCase(Locale.ROOT);
                if (registryName.contains(searchFilter)) return true;
            }
        }

        return false;
    }

    /**
     * Check if a storage bus matches the cell type filters.
     * @param bus The storage bus to check
     * @return true if the bus passes all active filters
     */
    private boolean storageBusMatchesCellFilters(StorageBusInfo bus) {
        // Item type filter
        State itemState = activeFilters.getOrDefault(CellFilter.ITEM_CELLS, State.SHOW_ALL);
        if (itemState != State.SHOW_ALL) {
            if (itemState == State.SHOW_ONLY && !bus.isItem()) return false;
            if (itemState == State.HIDE && bus.isItem()) return false;
        }

        // Fluid type filter
        State fluidState = activeFilters.getOrDefault(CellFilter.FLUID_CELLS, State.SHOW_ALL);
        if (fluidState != State.SHOW_ALL) {
            if (fluidState == State.SHOW_ONLY && !bus.isFluid()) return false;
            if (fluidState == State.HIDE && bus.isFluid()) return false;
        }

        // Essentia type filter
        State essentiaState = activeFilters.getOrDefault(CellFilter.ESSENTIA_CELLS, State.SHOW_ALL);
        if (essentiaState != State.SHOW_ALL) {
            if (essentiaState == State.SHOW_ONLY && !bus.isEssentia()) return false;
            if (essentiaState == State.HIDE && bus.isEssentia()) return false;
        }

        // Gas type filter
        State gasState = activeFilters.getOrDefault(CellFilter.GAS_CELLS, State.SHOW_ALL);
        if (gasState != State.SHOW_ALL) {
            if (gasState == State.SHOW_ONLY && !bus.isGas()) return false;
            if (gasState == State.HIDE && bus.isGas()) return false;
        }

        // Has items filter
        State hasItemsState = activeFilters.getOrDefault(CellFilter.HAS_ITEMS, State.SHOW_ALL);
        if (hasItemsState != State.SHOW_ALL) {
            boolean hasContents = !bus.getContents().isEmpty();
            if (hasItemsState == State.SHOW_ONLY && !hasContents) return false;
            if (hasItemsState == State.HIDE && hasContents) return false;
        }

        // Partitioned filter
        State partitionedState = activeFilters.getOrDefault(CellFilter.PARTITIONED, State.SHOW_ALL);
        if (partitionedState != State.SHOW_ALL) {
            boolean isPartitioned = bus.hasPartition();
            if (partitionedState == State.SHOW_ONLY && !isPartitioned) return false;
            if (partitionedState == State.HIDE && isPartitioned) return false;
        }

        return true;
    }

    private int getHighestNonEmptyStorageBusPartitionSlot(StorageBusInfo bus) {
        List<ItemStack> partition = bus.getPartition();
        int highest = -1;

        for (int i = 0; i < partition.size(); i++) {
            if (!partition.get(i).isEmpty()) highest = i;
        }

        return highest;
    }

    private Comparator<StorageBusInfo> createStorageBusComparator() {
        return (a, b) -> {
            boolean aInDim = a.getDimension() == terminalDimension;
            boolean bInDim = b.getDimension() == terminalDimension;

            if (aInDim != bInDim) return aInDim ? -1 : 1;

            if (a.getDimension() != b.getDimension()) {
                return Integer.compare(a.getDimension(), b.getDimension());
            }

            double distA = terminalPos.distanceSq(a.getPos());
            double distB = terminalPos.distanceSq(b.getPos());

            int distCompare = Double.compare(distA, distB);
            if (distCompare != 0) return distCompare;

            // Same position - sort by facing index
            return Integer.compare(a.getSide().ordinal(), b.getSide().ordinal());
        };
    }

    /**
     * Check if a cell matches the filter based on the current search mode.
     */
    private boolean matchesCellForMode(boolean matchesInventory, boolean matchesPartition) {
        if (searchFilter.isEmpty() && !useAdvancedSearch) return true;

        switch (searchMode) {
            case INVENTORY:
                return matchesInventory;
            case PARTITION:
                return matchesPartition;
            case MIXED:
            default:
                return matchesInventory || matchesPartition;
        }
    }

    /**
     * Check if a cell matches the search filter for a specific search type.
     * @param cell The cell to check
     * @param checkInventory true to check inventory contents, false to check partition
     * @return true if the cell matches the filter or if filter is empty
     */
    private boolean cellMatchesSearchFilter(CellInfo cell, boolean checkInventory) {
        if (searchFilter.isEmpty()) return true;

        List<ItemStack> itemsToCheck = checkInventory ? cell.getContents() : cell.getPartition();

        for (ItemStack stack : itemsToCheck) {
            if (stack.isEmpty()) continue;

            // Check localized display name
            String displayName = stack.getDisplayName().toLowerCase(Locale.ROOT);
            if (displayName.contains(searchFilter)) return true;

            // Check registry name (internal ID)
            if (stack.getItem().getRegistryName() != null) {
                String registryName = stack.getItem().getRegistryName().toString().toLowerCase(Locale.ROOT);
                if (registryName.contains(searchFilter)) return true;
            }
        }

        return false;
    }

    /**
     * Check if a cell matches the cell type filters.
     * @param cell The cell to check
     * @return true if the cell passes all active filters
     */
    private boolean cellMatchesCellFilters(CellInfo cell) {
        // Item type filter
        State itemState = activeFilters.getOrDefault(CellFilter.ITEM_CELLS, State.SHOW_ALL);
        if (itemState != State.SHOW_ALL) {
            if (itemState == State.SHOW_ONLY && !cell.isItem()) return false;
            if (itemState == State.HIDE && cell.isItem()) return false;
        }

        // Fluid type filter
        State fluidState = activeFilters.getOrDefault(CellFilter.FLUID_CELLS, State.SHOW_ALL);
        if (fluidState != State.SHOW_ALL) {
            if (fluidState == State.SHOW_ONLY && !cell.isFluid()) return false;
            if (fluidState == State.HIDE && cell.isFluid()) return false;
        }

        // Essentia type filter
        State essentiaState = activeFilters.getOrDefault(CellFilter.ESSENTIA_CELLS, State.SHOW_ALL);
        if (essentiaState != State.SHOW_ALL) {
            if (essentiaState == State.SHOW_ONLY && !cell.isEssentia()) return false;
            if (essentiaState == State.HIDE && cell.isEssentia()) return false;
        }

        // Gas type filter
        State gasState = activeFilters.getOrDefault(CellFilter.GAS_CELLS, State.SHOW_ALL);
        if (gasState != State.SHOW_ALL) {
            if (gasState == State.SHOW_ONLY && !cell.isGas()) return false;
            if (gasState == State.HIDE && cell.isGas()) return false;
        }

        // Has items filter
        State hasItemsState = activeFilters.getOrDefault(CellFilter.HAS_ITEMS, State.SHOW_ALL);
        if (hasItemsState != State.SHOW_ALL) {
            boolean hasContents = !cell.getContents().isEmpty();
            if (hasItemsState == State.SHOW_ONLY && !hasContents) return false;
            if (hasItemsState == State.HIDE && hasContents) return false;
        }

        // Partitioned filter
        State partitionedState = activeFilters.getOrDefault(CellFilter.PARTITIONED, State.SHOW_ALL);
        if (partitionedState != State.SHOW_ALL) {
            boolean isPartitioned = hasNonEmptyPartition(cell);
            if (partitionedState == State.SHOW_ONLY && !isPartitioned) return false;
            if (partitionedState == State.HIDE && isPartitioned) return false;
        }

        return true;
    }

    /**
     * Check if a cell has at least one non-empty partition slot.
     */
    private boolean hasNonEmptyPartition(CellInfo cell) {
        for (ItemStack stack : cell.getPartition()) {
            if (!stack.isEmpty()) return true;
        }

        return false;
    }

    private int getHighestNonEmptyPartitionSlot(CellInfo cell) {
        return getHighestNonEmptyPartitionSlot(cell, SLOTS_PER_ROW);
    }

    /**
     * Get the highest non-empty partition slot index for a cell.
     * Also calculates if an extra row should be shown for expansion.
     *
     * @param cell The cell to check
     * @param slotsPerRow Number of slots per row (8 for regular tabs, 9 for temp area)
     * @return The highest slot index to show, or -1 if no partition items
     */
    private int getHighestNonEmptyPartitionSlot(CellInfo cell, int slotsPerRow) {
        List<ItemStack> partition = cell.getPartition();

        // Cap at cell's actual type limit (e.g. 8 for some custom cells) to avoid
        // generating empty continuation rows beyond what the cell can hold.
        int maxTypes = (int) cell.getTotalTypes();
        int effectiveMax = Math.min(MAX_PARTITION_SLOTS, maxTypes);
        int highest = -1;

        for (int i = 0; i < Math.min(partition.size(), effectiveMax); i++) {
            if (!partition.get(i).isEmpty()) {
                highest = i;
            }
        }

        if (highest >= 0) {
            int currentRows = (highest / slotsPerRow) + 1;
            int lastSlotInLastRow = (currentRows * slotsPerRow) - 1;

            if (highest == lastSlotInLastRow && highest < effectiveMax - 1) {
                highest = Math.min(highest + slotsPerRow, effectiveMax - 1);
            }
        }

        return highest;
    }

    private Comparator<StorageInfo> createStorageComparator() {
        return (a, b) -> {
            boolean aInDim = a.getDimension() == terminalDimension;
            boolean bInDim = b.getDimension() == terminalDimension;

            if (aInDim != bInDim) return aInDim ? -1 : 1;

            if (a.getDimension() != b.getDimension()) return Integer.compare(a.getDimension(), b.getDimension());

            double distA = terminalPos.distanceSq(a.getPos());
            double distB = terminalPos.distanceSq(b.getPos());

            return Double.compare(distA, distB);
        };
    }

    /**
     * Reset the data manager for a network switch.
     * This clears the hasInitialData flag so the next update does a full rebuild
     * instead of using snapshots from the old network context.
     */
    public void resetForNetworkSwitch() {
        this.hasInitialData = false;

        // Clear all snapshots since they're from the old network
        this.visibleCellSnapshot.clear();
        this.visibleCellSnapshotInventory.clear();
        this.visibleCellSnapshotPartition.clear();
        this.visibleBusSnapshotInventory.clear();
        this.visibleBusSnapshotPartition.clear();

        // Clear per-channel raw state so the next FULL payload starts from a clean slate.
        // Note: storageMap / storageBusMap are also cleared by their next FULL payload, but
        // clearing them here removes any flash of stale data while the new network's payload
        // is in transit.
        this.storageMap.clear();
        this.storageBusMap.clear();
        this.tempCellSlotData.clear();
        this.tempAreaLines.clear();
    }

    /**
     * Find the StorageInfo containing a given CellInfo.
     * Searches through all storages to find the one with matching parent ID.
     *
     * @param cell The cell to find the parent storage for
     * @return The StorageInfo containing this cell, or null if not found
     */
    public StorageInfo findStorageForCell(CellInfo cell) {
        if (cell == null) return null;

        long parentId = cell.getParentStorageId();
        return storageMap.get(parentId);
    }
}
