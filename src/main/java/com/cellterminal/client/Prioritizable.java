package com.cellterminal.client;


/**
 * Shared interface for data objects that support AE2 priority editing.
 * <p>
 * Implemented by {@link StorageInfo} (drives/chests) and {@link StorageBusInfo}
 * (storage buses). Used by the priority field system to avoid duplicating
 * field logic for each data type.
 */
public interface Prioritizable {

    /** Unique identifier used for priority packet addressing. */
    long getId();

    /** Current priority value. */
    int getPriority();

    /** Whether this object supports priority editing (some storages don't). */
    boolean supportsPriority();
}
