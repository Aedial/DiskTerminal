package com.cellterminal.client;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;


/**
 * Manages per-tab state for the Cell Terminal GUI.
 * Tracks expansion state of entries and scroll position for each tab.
 *
 * Currently memory-only, but designed to be easily extended for config persistence.
 */
public class TabStateManager {

    /**
     * Enum for tab types. Uses distinct values from the GUI constants.
     */
    public enum TabType {
        TERMINAL(0),
        INVENTORY(1),
        PARTITION(2),
        STORAGE_BUS_INVENTORY(3),
        STORAGE_BUS_PARTITION(4);

        private final int index;

        TabType(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

        public static TabType fromIndex(int index) {
            for (TabType type : values()) {
                if (type.index == index) return type;
            }

            return TERMINAL;
        }
    }

    private static final TabStateManager INSTANCE = new TabStateManager();

    // Per-tab expansion states: Map<TabType, Map<StorageId, Expanded>>
    private final EnumMap<TabType, Map<Long, Boolean>> expansionStates = new EnumMap<>(TabType.class);

    // Per-tab scroll positions
    private final EnumMap<TabType, Integer> scrollPositions = new EnumMap<>(TabType.class);

    private TabStateManager() {
        for (TabType tab : TabType.values()) {
            expansionStates.put(tab, new HashMap<>());
            scrollPositions.put(tab, 0);
        }
    }

    public static TabStateManager getInstance() {
        return INSTANCE;
    }

    /**
     * Check if a storage entry is expanded for the given tab.
     *
     * @param tab       The tab type
     * @param storageId The storage ID
     * @return True if expanded (defaults to true if not set)
     */
    public boolean isExpanded(TabType tab, long storageId) {
        return expansionStates.get(tab).getOrDefault(storageId, true);
    }

    /**
     * Set the expansion state for a storage entry in the given tab.
     */
    public void setExpanded(TabType tab, long storageId, boolean expanded) {
        expansionStates.get(tab).put(storageId, expanded);
    }

    /**
     * Toggle the expansion state for a storage entry in the given tab.
     *
     * @return The new expansion state
     */
    public boolean toggleExpanded(TabType tab, long storageId) {
        boolean current = isExpanded(tab, storageId);
        setExpanded(tab, storageId, !current);

        return !current;
    }

    /**
     * Get the scroll position for the given tab.
     */
    public int getScrollPosition(TabType tab) {
        return scrollPositions.get(tab);
    }

    /**
     * Set the scroll position for the given tab.
     */
    public void setScrollPosition(TabType tab, int position) {
        scrollPositions.put(tab, Math.max(0, position));
    }

    /**
     * Clear all state (e.g., when closing GUI or switching terminals).
     */
    public void clearAll() {
        for (TabType tab : TabType.values()) {
            expansionStates.get(tab).clear();
            scrollPositions.put(tab, 0);
        }
    }

    /**
     * Clear state for a specific tab.
     */
    public void clearTab(TabType tab) {
        expansionStates.get(tab).clear();
        scrollPositions.put(tab, 0);
    }

    /**
     * Check if a storage bus entry is expanded for the given tab.
     * Uses negative IDs to distinguish from storage entries.
     */
    public boolean isBusExpanded(TabType tab, long busId) {
        return isExpanded(tab, -busId - 1);
    }

    /**
     * Set the expansion state for a storage bus entry in the given tab.
     */
    public void setBusExpanded(TabType tab, long busId, boolean expanded) {
        setExpanded(tab, -busId - 1, expanded);
    }

    /**
     * Toggle the expansion state for a storage bus entry in the given tab.
     *
     * @return The new expansion state
     */
    public boolean toggleBusExpanded(TabType tab, long busId) {
        return toggleExpanded(tab, -busId - 1);
    }
}
