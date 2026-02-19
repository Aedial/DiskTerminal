package com.cellterminal.gui.networktools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.cells.api.IItemCompactingCell;

import com.cellterminal.client.AdvancedSearchParser;
import com.cellterminal.client.CellFilter;
import com.cellterminal.client.CellInfo;
import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.client.StorageInfo;


/**
 * Utility class for filtering cells and storage buses based on active filter states.
 * Used by network tools to determine which elements they should operate on.
 */
public final class NetworkToolFilterUtils {

    private NetworkToolFilterUtils() {}

    /**
     * Get all cells that pass the current filters.
     * @param storageMap The map of storage devices
     * @param activeFilters The active filter states
     * @param searchMode The search filter mode (affects HAS_ITEMS filter interpretation)
     * @param searchFilter The text search filter (lowercase, trimmed)
     * @param useAdvancedSearch Whether advanced search is active
     * @param advancedMatcher The advanced search matcher (may be null)
     * @return List of cells that pass the filters
     */
    public static List<INetworkTool.FilteredCell> getFilteredCells(
            Map<Long, StorageInfo> storageMap,
            Map<CellFilter, CellFilter.State> activeFilters,
            SearchFilterMode searchMode,
            String searchFilter,
            boolean useAdvancedSearch,
            AdvancedSearchParser.SearchMatcher advancedMatcher) {

        List<INetworkTool.FilteredCell> result = new ArrayList<>();

        for (StorageInfo storage : storageMap.values()) {
            for (int slot = 0; slot < storage.getSlotCount(); slot++) {
                CellInfo cell = storage.getCellAtSlot(slot);
                if (cell == null) continue;

                // First check cell type/button filters
                if (!cellMatchesFilters(cell, activeFilters, searchMode)) continue;

                // Then check text search filter
                if (!cellMatchesSearchFilter(cell, storage, searchMode, searchFilter, useAdvancedSearch, advancedMatcher)) continue;

                result.add(new INetworkTool.FilteredCell(cell, storage));
            }
        }

        return result;
    }

    /**
     * Get all storage buses that pass the current filters.
     * @param storageBusMap The map of storage buses
     * @param activeFilters The active filter states
     * @param searchMode The search filter mode (affects HAS_ITEMS filter interpretation)
     * @param searchFilter The text search filter (lowercase, trimmed)
     * @param useAdvancedSearch Whether advanced search is active
     * @param advancedMatcher The advanced search matcher (may be null)
     * @return List of storage buses that pass the filters
     */
    public static List<StorageBusInfo> getFilteredStorageBuses(
            Map<Long, StorageBusInfo> storageBusMap,
            Map<CellFilter, CellFilter.State> activeFilters,
            SearchFilterMode searchMode,
            String searchFilter,
            boolean useAdvancedSearch,
            AdvancedSearchParser.SearchMatcher advancedMatcher) {

        List<StorageBusInfo> result = new ArrayList<>();

        for (StorageBusInfo bus : storageBusMap.values()) {
            // First check bus type/button filters
            if (!storageBusMatchesFilters(bus, activeFilters, searchMode)) continue;

            // Then check text search filter
            if (!storageBusMatchesSearchFilter(bus, searchMode, searchFilter, useAdvancedSearch, advancedMatcher)) continue;

            result.add(bus);
        }

        return result;
    }

    /**
     * Get all empty, non-partitioned cells of a specific type (item/fluid/essentia).
     * These can be used as additional targets for distribution operations like AttributeUnique.
     * @param storageMap The map of storage devices
     * @param includeItem Whether to include item cells
     * @param includeFluid Whether to include fluid cells
     * @param includeEssentia Whether to include essentia cells
     * @return List of empty, non-partitioned cells
     */
    public static List<INetworkTool.FilteredCell> getEmptyNonPartitionedCells(
            Map<Long, StorageInfo> storageMap,
            boolean includeItem, boolean includeFluid, boolean includeEssentia) {

        List<INetworkTool.FilteredCell> result = new ArrayList<>();

        for (StorageInfo storage : storageMap.values()) {
            for (int slot = 0; slot < storage.getSlotCount(); slot++) {
                CellInfo cell = storage.getCellAtSlot(slot);
                if (cell == null) continue;

                // Skip compacting cells - they use a special chain mechanism
                if (cell.getCellItem().getItem() instanceof IItemCompactingCell) continue;

                // Check cell type matches requested types
                boolean isFluid = cell.isFluid();
                boolean isEssentia = cell.isEssentia();
                boolean isItem = !isFluid && !isEssentia;

                if (isItem && !includeItem) continue;
                if (isFluid && !includeFluid) continue;
                if (isEssentia && !includeEssentia) continue;

                // Check if empty and non-partitioned
                boolean isEmpty = cell.getContents().isEmpty() ||
                    cell.getContents().stream().allMatch(ItemStack::isEmpty);
                boolean hasPartition = cell.getPartition().stream().anyMatch(stack -> !stack.isEmpty());

                if (isEmpty && !hasPartition) result.add(new INetworkTool.FilteredCell(cell, storage));
            }
        }

        return result;
    }

    /**
     * Check if a cell matches the active filters.
     * @param cell The cell to check
     * @param activeFilters The active filter states
     * @param searchMode The search filter mode (affects HAS_ITEMS filter interpretation)
     */
    public static boolean cellMatchesFilters(CellInfo cell,
                                              Map<CellFilter, CellFilter.State> activeFilters,
                                              SearchFilterMode searchMode) {
        // Check cell type filters
        if (!checkTypeFilter(cell, activeFilters)) return false;

        // Check has items filter (respects search mode)
        if (!checkHasItemsFilter(cell, activeFilters, searchMode)) return false;

        // Check partitioned filter
        if (!checkPartitionedFilter(cell, activeFilters)) return false;

        return true;
    }

    /**
     * Check if a storage bus matches the active filters.
     * @param bus The storage bus to check
     * @param activeFilters The active filter states
     * @param searchMode The search filter mode (affects HAS_ITEMS filter interpretation)
     */
    public static boolean storageBusMatchesFilters(StorageBusInfo bus,
                                                    Map<CellFilter, CellFilter.State> activeFilters,
                                                    SearchFilterMode searchMode) {
        // Check bus type filters
        if (!checkBusTypeFilter(bus, activeFilters)) return false;

        // Check has items filter (respects search mode)
        if (!checkBusHasItemsFilter(bus, activeFilters, searchMode)) return false;

        // Check partitioned filter
        if (!checkBusPartitionedFilter(bus, activeFilters)) return false;

        return true;
    }

    private static boolean checkTypeFilter(CellInfo cell, Map<CellFilter, CellFilter.State> activeFilters) {
        CellFilter.State itemState = activeFilters.getOrDefault(CellFilter.ITEM_CELLS, CellFilter.State.SHOW_ALL);
        CellFilter.State fluidState = activeFilters.getOrDefault(CellFilter.FLUID_CELLS, CellFilter.State.SHOW_ALL);
        CellFilter.State essentiaState = activeFilters.getOrDefault(CellFilter.ESSENTIA_CELLS, CellFilter.State.SHOW_ALL);

        boolean isFluid = cell.isFluid();
        boolean isEssentia = cell.isEssentia();
        boolean isItem = !isFluid && !isEssentia;

        // If any filter is set to HIDE and matches this cell, exclude it
        if (isItem && itemState == CellFilter.State.HIDE) return false;
        if (isFluid && fluidState == CellFilter.State.HIDE) return false;
        if (isEssentia && essentiaState == CellFilter.State.HIDE) return false;

        // If any filter is set to SHOW_ONLY, only include matching cells
        boolean hasShowOnly = (itemState == CellFilter.State.SHOW_ONLY)
            || (fluidState == CellFilter.State.SHOW_ONLY)
            || (essentiaState == CellFilter.State.SHOW_ONLY);

        if (hasShowOnly) {
            if (isItem && itemState != CellFilter.State.SHOW_ONLY) return false;
            if (isFluid && fluidState != CellFilter.State.SHOW_ONLY) return false;
            if (isEssentia && essentiaState != CellFilter.State.SHOW_ONLY) return false;
        }

        return true;
    }

    private static boolean checkHasItemsFilter(CellInfo cell,
                                                Map<CellFilter, CellFilter.State> activeFilters,
                                                SearchFilterMode searchMode) {
        CellFilter.State state = activeFilters.getOrDefault(CellFilter.HAS_ITEMS, CellFilter.State.SHOW_ALL);
        if (state == CellFilter.State.SHOW_ALL) return true;

        // Determine what "has items" means based on search mode
        boolean hasItems;
        switch (searchMode) {
            case INVENTORY:
                // Only check contents
                hasItems = cell.getContents().stream().anyMatch(stack -> !stack.isEmpty());
                break;
            case PARTITION:
                // Only check partition
                hasItems = cell.getPartition().stream().anyMatch(stack -> !stack.isEmpty());
                break;
            case MIXED:
            default:
                // Check both - cell has items if either contents or partition has items
                hasItems = cell.getContents().stream().anyMatch(stack -> !stack.isEmpty())
                    || cell.getPartition().stream().anyMatch(stack -> !stack.isEmpty());
                break;
        }

        if (state == CellFilter.State.SHOW_ONLY) return hasItems;
        if (state == CellFilter.State.HIDE) return !hasItems;

        return true;
    }

    private static boolean checkPartitionedFilter(CellInfo cell, Map<CellFilter, CellFilter.State> activeFilters) {
        CellFilter.State state = activeFilters.getOrDefault(CellFilter.PARTITIONED, CellFilter.State.SHOW_ALL);
        if (state == CellFilter.State.SHOW_ALL) return true;

        // Check if partition has any non-empty items
        boolean hasPartition = cell.getPartition().stream().anyMatch(stack -> !stack.isEmpty());
        if (state == CellFilter.State.SHOW_ONLY) return hasPartition;
        if (state == CellFilter.State.HIDE) return !hasPartition;

        return true;
    }

    private static boolean checkBusTypeFilter(StorageBusInfo bus, Map<CellFilter, CellFilter.State> activeFilters) {
        CellFilter.State itemState = activeFilters.getOrDefault(CellFilter.ITEM_CELLS, CellFilter.State.SHOW_ALL);
        CellFilter.State fluidState = activeFilters.getOrDefault(CellFilter.FLUID_CELLS, CellFilter.State.SHOW_ALL);
        CellFilter.State essentiaState = activeFilters.getOrDefault(CellFilter.ESSENTIA_CELLS, CellFilter.State.SHOW_ALL);

        boolean isFluid = bus.isFluid();
        boolean isEssentia = bus.isEssentia();
        boolean isItem = !isFluid && !isEssentia;

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

        return true;
    }

    private static boolean checkBusHasItemsFilter(StorageBusInfo bus,
                                                   Map<CellFilter, CellFilter.State> activeFilters,
                                                   SearchFilterMode searchMode) {
        CellFilter.State state = activeFilters.getOrDefault(CellFilter.HAS_ITEMS, CellFilter.State.SHOW_ALL);
        if (state == CellFilter.State.SHOW_ALL) return true;

        // Determine what "has items" means based on search mode
        boolean hasItems;
        switch (searchMode) {
            case INVENTORY:
                // Only check contents
                hasItems = !bus.getContents().isEmpty();
                break;
            case PARTITION:
                // Only check partition
                hasItems = bus.hasPartition();
                break;
            case MIXED:
            default:
                // Check both - bus has items if either contents or partition has items
                hasItems = !bus.getContents().isEmpty() || bus.hasPartition();
                break;
        }

        if (state == CellFilter.State.SHOW_ONLY) return hasItems;
        if (state == CellFilter.State.HIDE) return !hasItems;

        return true;
    }

    private static boolean checkBusPartitionedFilter(StorageBusInfo bus, Map<CellFilter, CellFilter.State> activeFilters) {
        CellFilter.State state = activeFilters.getOrDefault(CellFilter.PARTITIONED, CellFilter.State.SHOW_ALL);
        if (state == CellFilter.State.SHOW_ALL) return true;

        boolean hasPartition = bus.hasPartition();
        if (state == CellFilter.State.SHOW_ONLY) return hasPartition;
        if (state == CellFilter.State.HIDE) return !hasPartition;

        return true;
    }

    /**
     * Check if a cell matches the text search filter.
     * @param cell The cell to check
     * @param storage The parent storage
     * @param searchMode The search filter mode
     * @param searchFilter The text search filter (lowercase, trimmed)
     * @param useAdvancedSearch Whether advanced search is active
     * @param advancedMatcher The advanced search matcher (may be null)
     * @return true if the cell matches the search filter or if filter is empty
     */
    private static boolean cellMatchesSearchFilter(
            CellInfo cell,
            StorageInfo storage,
            SearchFilterMode searchMode,
            String searchFilter,
            boolean useAdvancedSearch,
            AdvancedSearchParser.SearchMatcher advancedMatcher) {

        // If using advanced search with a valid matcher, use it
        if (useAdvancedSearch && advancedMatcher != null) {
            return advancedMatcher.matchesCell(cell, storage, searchMode);
        }

        // Otherwise, use simple text search
        if (searchFilter == null || searchFilter.isEmpty()) return true;

        // Check based on search mode
        boolean matchesInventory = itemListContainsText(cell.getContents(), searchFilter);
        boolean matchesPartition = itemListContainsText(cell.getPartition(), searchFilter);

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
     * Check if a storage bus matches the text search filter.
     * @param bus The storage bus to check
     * @param searchMode The search filter mode
     * @param searchFilter The text search filter (lowercase, trimmed)
     * @param useAdvancedSearch Whether advanced search is active
     * @param advancedMatcher The advanced search matcher (may be null)
     * @return true if the bus matches the search filter or if filter is empty
     */
    private static boolean storageBusMatchesSearchFilter(
            StorageBusInfo bus,
            SearchFilterMode searchMode,
            String searchFilter,
            boolean useAdvancedSearch,
            AdvancedSearchParser.SearchMatcher advancedMatcher) {

        // If using advanced search with a valid matcher, use it
        if (useAdvancedSearch && advancedMatcher != null) {
            return advancedMatcher.matchesStorageBus(bus, searchMode);
        }

        // Otherwise, use simple text search
        if (searchFilter == null || searchFilter.isEmpty()) return true;

        // Check based on search mode
        boolean matchesInventory = itemListContainsText(bus.getContents(), searchFilter);
        boolean matchesPartition = itemListContainsText(bus.getPartition(), searchFilter);

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
     * Check if any item in the list matches the search text.
     * @param items The list of items to check
     * @param searchText The search text (lowercase)
     * @return true if any item matches
     */
    private static boolean itemListContainsText(List<ItemStack> items, String searchText) {
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;

            // Check localized display name
            String displayName = stack.getDisplayName().toLowerCase(Locale.ROOT);
            if (displayName.contains(searchText)) return true;

            // Check registry name (internal ID)
            if (stack.getItem().getRegistryName() != null) {
                String registryName = stack.getItem().getRegistryName().toString().toLowerCase(Locale.ROOT);
                if (registryName.contains(searchText)) return true;
            }
        }

        return false;
    }
}
