package com.cellterminal.gui.rename;


/**
 * Interface for objects that can be renamed inline in the Cell Terminal GUI.
 * Implemented by SubnetInfo, StorageInfo, CellInfo, and StorageBusInfo.
 *
 * Provides a unified way to check rename eligibility, get/set custom names,
 * and identify the target for server-side rename packets.
 */
public interface Renameable {

    /**
     * Whether this target can be renamed.
     * Some targets may not support renaming (e.g., main network in subnet overview).
     */
    boolean isRenameable();

    /**
     * Get the current custom name, or null if no custom name is set.
     */
    String getCustomName();

    /**
     * Check if this target has a custom name.
     */
    boolean hasCustomName();

    /**
     * Set the custom name (for optimistic client-side update).
     * @param name The new name, or null/empty to clear
     */
    void setCustomName(String name);

    /**
     * Get the rename target type for the server-side packet.
     */
    RenameTargetType getRenameTargetType();

    /**
     * Get the primary identifier for this target (storage ID, cell parent storage ID, etc).
     */
    long getRenameId();

    /**
     * Get the secondary identifier (e.g., cell slot index, storage bus ID).
     * Returns -1 if not applicable.
     */
    default int getRenameSecondaryId() {
        return -1;
    }
}
