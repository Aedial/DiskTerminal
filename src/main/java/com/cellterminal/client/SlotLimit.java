package com.cellterminal.client;


/**
 * Slot limit options for controlling how many types are shown per cell/bus content.
 * This affects rendering only, not the actual storage capacity.
 */
public enum SlotLimit {

    /** Show only 8 types (1 row) */
    LIMIT_8(8, "8"),

    /** Show only 32 types (4 rows) */
    LIMIT_32(32, "32"),

    /** Show only 64 types (8 rows) */
    LIMIT_64(64, "64"),

    /** Show all types (no limit) */
    UNLIMITED(-1, "∞");

    private final int limit;
    private final String displayText;

    SlotLimit(int limit, String displayText) {
        this.limit = limit;
        this.displayText = displayText;
    }

    /**
     * Get the numeric limit value.
     * @return limit value, or -1 for unlimited
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Get the display text for buttons/tooltips.
     */
    public String getDisplayText() {
        return displayText;
    }

    /**
     * Check if a given content count should be limited by this setting.
     * @param count the content count to check
     * @return true if content should be limited (count exceeds limit)
     */
    public boolean shouldLimit(int count) {
        if (limit < 0) return false;

        return count > limit;
    }

    /**
     * Get the effective count to display.
     * @param actualCount the actual content count
     * @return the count to display (limited if applicable)
     */
    public int getEffectiveCount(int actualCount) {
        if (limit < 0) return actualCount;

        return Math.min(actualCount, limit);
    }

    /**
     * Cycle to the next slot limit option.
     */
    public SlotLimit next() {
        SlotLimit[] values = values();

        return values[(ordinal() + 1) % values.length];
    }

    /**
     * Parse a slot limit from its name.
     * @param name the name to parse
     * @return the slot limit, or UNLIMITED if not found
     */
    public static SlotLimit fromName(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNLIMITED;
        }
    }
}
