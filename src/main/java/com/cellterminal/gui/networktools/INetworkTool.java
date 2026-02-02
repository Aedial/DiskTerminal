package com.cellterminal.gui.networktools;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

import net.minecraft.item.ItemStack;

import com.cellterminal.client.AdvancedSearchParser;
import com.cellterminal.client.CellFilter;
import com.cellterminal.client.CellInfo;
import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.client.StorageInfo;


/**
 * Interface for network tools that perform batch operations on cells and storage buses.
 * Each tool can display a preview of what it will affect and execute the operation.
 */
public interface INetworkTool {

    /**
     * Get the unique identifier for this tool.
     * @return The tool ID
     */
    String getId();

    /**
     * Get the localized display name of this tool.
     * @return The tool name
     */
    String getName();

    /**
     * Get the localized description of what this tool does.
     * @return The tool description
     */
    String getDescription();

    /**
     * Get the detailed help text for the '?' tooltip.
     * @return List of localized help lines
     */
    List<String> getHelpLines();

    /**
     * Get the icon to display for this tool.
     * @return The icon ItemStack
     */
    ItemStack getIcon();

    /**
     * Get the preview information showing what this tool will affect.
     * @param context The tool context with current filter state and data
     * @return The preview info
     */
    ToolPreviewInfo getPreview(ToolContext context);

    /**
     * Check if the tool can be executed with the current state.
     * @param context The tool context
     * @return null if executable, otherwise error message
     */
    String getExecutionError(ToolContext context);

    /**
     * Get the confirmation message to display in the modal.
     * @param context The tool context
     * @return The confirmation message
     */
    String getConfirmationMessage(ToolContext context);

    /**
     * Execute the tool operation.
     * This should send a packet to the server to perform the actual action.
     * @param context The tool context
     */
    void execute(ToolContext context);

    /**
     * Preview information for a network tool.
     */
    class ToolPreviewInfo {
        private final ItemStack icon;
        private final String countText;
        private final int countColor;
        private final List<String> tooltipLines;

        public ToolPreviewInfo(ItemStack icon, String countText, int countColor, List<String> tooltipLines) {
            this.icon = icon;
            this.countText = countText;
            this.countColor = countColor;
            this.tooltipLines = tooltipLines;
        }

        public ItemStack getIcon() {
            return icon;
        }

        public String getCountText() {
            return countText;
        }

        public int getCountColor() {
            return countColor;
        }

        public List<String> getTooltipLines() {
            return tooltipLines;
        }
    }

    /**
     * Context providing access to filtered data for tools.
     */
    class ToolContext {
        private final Map<Long, StorageInfo> storageMap;
        private final Map<Long, StorageBusInfo> storageBusMap;
        private final Map<CellFilter, CellFilter.State> activeFilters;
        private final SearchFilterMode searchMode;
        private final NetworkToolCallback callback;
        private final String searchFilter;
        private final boolean useAdvancedSearch;
        private final AdvancedSearchParser.SearchMatcher advancedMatcher;

        public ToolContext(Map<Long, StorageInfo> storageMap,
                          Map<Long, StorageBusInfo> storageBusMap,
                          Map<CellFilter, CellFilter.State> activeFilters,
                          SearchFilterMode searchMode,
                          NetworkToolCallback callback,
                          String searchFilter,
                          boolean useAdvancedSearch,
                          AdvancedSearchParser.SearchMatcher advancedMatcher) {
            this.storageMap = storageMap;
            this.storageBusMap = storageBusMap;
            this.activeFilters = activeFilters;
            this.searchMode = searchMode;
            this.callback = callback;
            this.searchFilter = searchFilter != null ? searchFilter : "";
            this.useAdvancedSearch = useAdvancedSearch;
            this.advancedMatcher = advancedMatcher;
        }

        public Map<Long, StorageInfo> getStorageMap() {
            return storageMap;
        }

        public Map<Long, StorageBusInfo> getStorageBusMap() {
            return storageBusMap;
        }

        public Map<CellFilter, CellFilter.State> getActiveFilters() {
            return activeFilters;
        }

        public SearchFilterMode getSearchMode() {
            return searchMode;
        }

        public NetworkToolCallback getCallback() {
            return callback;
        }

        public String getSearchFilter() {
            return searchFilter;
        }

        public boolean isUsingAdvancedSearch() {
            return useAdvancedSearch;
        }

        public AdvancedSearchParser.SearchMatcher getAdvancedMatcher() {
            return advancedMatcher;
        }

        /**
         * Get all cells that pass the current filters.
         * @return List of filtered cells with their parent storage
         */
        public List<FilteredCell> getFilteredCells() {
            return NetworkToolFilterUtils.getFilteredCells(
                storageMap, activeFilters, searchMode,
                searchFilter, useAdvancedSearch, advancedMatcher);
        }

        /**
         * Get all storage buses that pass the current filters.
         * @return List of filtered storage buses
         */
        public List<StorageBusInfo> getFilteredStorageBuses() {
            return NetworkToolFilterUtils.getFilteredStorageBuses(
                storageBusMap, activeFilters, searchMode,
                searchFilter, useAdvancedSearch, advancedMatcher);
        }

        /**
         * Get available cells for redistribution: filtered cells + empty non-partitioned cells of matching types.
         * Used by AttributeUnique tool to find all cells that can receive items.
         * @param includeItem Whether to include item cells
         * @param includeFluid Whether to include fluid cells
         * @param includeEssentia Whether to include essentia cells
         * @return List of available cells
         */
        public List<FilteredCell> getAvailableCellsForRedistribution(
                boolean includeItem, boolean includeFluid, boolean includeEssentia) {
            List<FilteredCell> filtered = getFilteredCells();
            List<FilteredCell> emptyNonPartitioned = getEmptyNonPartitionedCells(
                includeItem, includeFluid, includeEssentia);

            // Combine, avoiding duplicates (empty non-partitioned cells might already be in filtered)
            Set<CellInfo> seen = new HashSet<>();
            List<FilteredCell> result = new ArrayList<>();

            for (FilteredCell fc : filtered) {
                if (seen.add(fc.getCell())) result.add(fc);
            }
            for (FilteredCell fc : emptyNonPartitioned) {
                if (seen.add(fc.getCell())) result.add(fc);
            }

            return result;
        }

        /**
         * Get empty non-partitioned cells of matching types.
         * Used by AttributeUnique tool to find additional cells for redistribution.
         * @param includeItem Whether to include item cells
         * @param includeFluid Whether to include fluid cells
         * @param includeEssentia Whether to include essentia cells
         * @return List of empty non-partitioned cells
         */
        public List<FilteredCell> getEmptyNonPartitionedCells(
                boolean includeItem, boolean includeFluid, boolean includeEssentia) {
            return NetworkToolFilterUtils.getEmptyNonPartitionedCells(
                storageMap, includeItem, includeFluid, includeEssentia);
        }
    }

    /**
     * Represents a cell with its parent storage for tool operations.
     */
    class FilteredCell {
        private final CellInfo cell;
        private final StorageInfo storage;

        public FilteredCell(CellInfo cell, StorageInfo storage) {
            this.cell = cell;
            this.storage = storage;
        }

        public CellInfo getCell() {
            return cell;
        }

        public StorageInfo getStorage() {
            return storage;
        }
    }

    /**
     * Callback interface for network tool actions.
     */
    interface NetworkToolCallback {
        void sendToolPacket(String toolId, byte[] data);
        void showError(String message);
        void showSuccess(String message);
    }
}
