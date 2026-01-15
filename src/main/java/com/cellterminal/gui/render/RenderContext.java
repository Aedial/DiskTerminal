package com.cellterminal.gui.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.cellterminal.client.CellInfo;
import com.cellterminal.client.StorageInfo;


/**
 * Holds the rendering context and hover state for Cell Terminal tab rendering.
 * This object is passed to renderers and updated during drawing to track what
 * the user is hovering over for tooltips and click handling.
 */
public class RenderContext {

    // Storage data
    public Map<Long, StorageInfo> storageMap;

    // Visible area bounds
    public int visibleTop;
    public int visibleBottom;
    public int rowsVisible;

    // GUI positioning
    public int guiLeft;
    public int guiTop;

    // Hover state - Terminal tab
    public CellInfo hoveredCell = null;
    public int hoverType = 0; // 0=none, 1=inventory, 2=partition, 3=eject
    public StorageInfo hoveredStorageLine = null; // Storage header line being hovered

    // Hover state - line tracking
    public int hoveredLineIndex = -1;

    // Hover state - Tab 2/3 (Inventory/Partition)
    public CellInfo hoveredCellCell = null;
    public StorageInfo hoveredCellStorage = null;
    public int hoveredCellSlotIndex = -1;
    public ItemStack hoveredContentStack = ItemStack.EMPTY;
    public int hoveredContentX = 0;
    public int hoveredContentY = 0;
    public int hoveredContentSlotIndex = -1;

    // Partition slot tracking for JEI ghost ingredients
    public int hoveredPartitionSlotIndex = -1;
    public CellInfo hoveredPartitionCell = null;
    public final List<PartitionSlotTarget> partitionSlotTargets = new ArrayList<>();

    // Visible storage positions for priority field rendering
    public final List<VisibleStorageEntry> visibleStorages = new ArrayList<>();

    /**
     * Tracks a visible storage entry and its Y position for priority field placement.
     */
    public static class VisibleStorageEntry {
        public final StorageInfo storage;
        public final int y;

        public VisibleStorageEntry(StorageInfo storage, int y) {
            this.storage = storage;
            this.y = y;
        }
    }

    /**
     * Reset all hover state at the start of a render cycle.
     */
    public void resetHoverState() {
        hoveredCell = null;
        hoverType = 0;
        hoveredStorageLine = null;
        hoveredLineIndex = -1;
        hoveredContentStack = ItemStack.EMPTY;
        hoveredCellCell = null;
        hoveredCellStorage = null;
        hoveredCellSlotIndex = -1;
        hoveredContentSlotIndex = -1;
        hoveredPartitionSlotIndex = -1;
        hoveredPartitionCell = null;
        partitionSlotTargets.clear();
        visibleStorages.clear();
    }

    /**
     * Helper class to track partition slot positions for JEI ghost ingredients.
     */
    public static class PartitionSlotTarget {
        public final CellInfo cell;
        public final int slotIndex;
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        public PartitionSlotTarget(CellInfo cell, int slotIndex, int x, int y, int width, int height) {
            this.cell = cell;
            this.slotIndex = slotIndex;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
