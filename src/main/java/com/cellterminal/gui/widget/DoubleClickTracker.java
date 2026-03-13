package com.cellterminal.gui.widget;

import com.cellterminal.gui.GuiConstants;


/**
 * Centralized double-click tracking for widgets that are recreated each frame.
 * <p>
 * Since widgets (headers, lines) are recreated in {@code buildVisibleRows()} every frame,
 * storing {@code lastClickTime} in the widget instance doesn't work - the state is lost
 * when a new widget object is created.
 * <p>
 * This static tracker stores the last click time and target ID, allowing widgets to
 * detect double-clicks across instance recreations.
 * <p>
 * Usage:
 * <pre>
 * // In widget's handleClick:
 * if (DoubleClickTracker.isDoubleClick(targetId)) {
 *     onDoubleClick();
 *     return true;
 * }
 * </pre>
 */
public final class DoubleClickTracker {

    private DoubleClickTracker() {}

    /** The ID of the last clicked target (combines type + id) */
    private static long lastClickTargetId = -1;

    /** Time of the last click */
    private static long lastClickTime = 0;

    /**
     * Check if the current click is a double-click on the given target.
     * <p>
     * If this is the first click on the target, records the time and returns false.
     * If this is a second click within the threshold, returns true and resets the state.
     *
     * @param targetId A unique identifier for the click target (e.g., storage ID, cell slot, etc.)
     * @return true if this is a double-click, false otherwise
     */
    public static boolean isDoubleClick(long targetId) {
        long currentTime = System.currentTimeMillis();

        if (lastClickTargetId == targetId
                && currentTime - lastClickTime < GuiConstants.DOUBLE_CLICK_TIME_MS) {
            // Double-click detected - reset state to prevent triple-click triggering
            lastClickTargetId = -1;
            lastClickTime = 0;
            return true;
        }

        // First click or different target - record for potential double-click
        lastClickTargetId = targetId;
        lastClickTime = currentTime;
        return false;
    }

    /**
     * Generate a unique target ID for a storage (drive/chest).
     * Uses positive IDs.
     */
    public static long storageTargetId(long storageId) {
        return storageId;
    }

    /**
     * Generate a unique target ID for a cell within a storage.
     * Encodes both storage ID and slot to create a unique identifier.
     */
    public static long cellTargetId(long storageId, int slot) {
        // Combine storage ID and slot into unique ID (high bits = storage, low 8 bits = slot)
        return (storageId << 8) | (slot & 0xFF);
    }

    /**
     * Generate a unique target ID for a storage bus.
     * Uses negative IDs to distinguish from storages.
     */
    public static long storageBusTargetId(long busId) {
        return -busId - 1;
    }

    /**
     * Generate a unique target ID for a subnet.
     * Uses a distinct negative range (offset by Long.MIN_VALUE/2) to avoid
     * collision with storageBusTargetId which uses -id - 1.
     */
    public static long subnetTargetId(long subnetId) {
        return Long.MIN_VALUE / 2 - subnetId;
    }

    /**
     * Reset the tracker state (e.g., when closing GUI).
     */
    public static void reset() {
        lastClickTargetId = -1;
        lastClickTime = 0;
    }
}
