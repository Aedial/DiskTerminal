package com.cellterminal.client;


/**
 * Filter types for Cell Terminal visibility filtering.
 * Each filter can be in one of three states: SHOW_ALL, SHOW_ONLY, HIDE.
 * 
 * Filters are stored separately for cells (tabs 0-2) and storage buses (tabs 3-4).
 */
public enum CellFilter {
    ITEM_CELLS("item_cells"),
    FLUID_CELLS("fluid_cells"),
    ESSENTIA_CELLS("essentia_cells"),
    HAS_ITEMS("has_items"),
    PARTITIONED("partitioned");

    private final String configKey;

    CellFilter(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }

    /**
     * Get the config key for this filter in the given context.
     * @param forStorageBus true for storage bus tabs, false for cell tabs
     */
    public String getConfigKey(boolean forStorageBus) {
        return (forStorageBus ? "bus_" : "cell_") + configKey;
    }

    /**
     * Filter state enum.
     */
    public enum State {
        SHOW_ALL,   // Default - don't filter based on this criterion
        SHOW_ONLY,  // Only show entries matching this criterion
        HIDE;       // Hide entries matching this criterion

        public State next() {
            State[] values = values();

            return values[(ordinal() + 1) % values.length];
        }

        public static State fromName(String name) {
            try {
                return valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                return SHOW_ALL;
            }
        }
    }
}
