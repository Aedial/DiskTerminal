package com.cellterminal.gui.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.cellterminal.client.CellInfo;
import com.cellterminal.client.StorageBusInfo;
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

    // Storage Bus tab hover state (Tab 4/5)
    public StorageBusInfo hoveredStorageBus = null;
    public int hoveredStorageBusPartitionSlot = -1;
    public int hoveredStorageBusContentSlot = -1;
    public StorageBusInfo hoveredClearButtonStorageBus = null;  // Tab 5: clear button hovered
    public StorageBusInfo hoveredIOModeButtonStorageBus = null;  // Tab 4: IO mode button hovered
    public StorageBusInfo hoveredPartitionAllButtonStorageBus = null;  // Tab 4: partition all button hovered
    public final List<StorageBusPartitionSlotTarget> storageBusPartitionSlotTargets = new ArrayList<>();

    // Cell partition-all / clear-partition button hover (Tab 2/3)
    public CellInfo hoveredPartitionAllButtonCell = null;  // Tab 2: partition all button hovered
    public CellInfo hoveredClearPartitionButtonCell = null;  // Tab 3: clear partition button hovered

    // Selected storage bus IDs for Tab 5 (multi-select, receives items from A key/JEI)
    public java.util.Set<Long> selectedStorageBusIds = new java.util.HashSet<>();

    // Visible storage positions for priority field rendering
    public final List<VisibleStorageEntry> visibleStorages = new ArrayList<>();

    // Visible storage bus positions for priority field rendering (Tab 4/5)
    public final List<VisibleStorageBusEntry> visibleStorageBuses = new ArrayList<>();

    // Upgrade icon hover tracking
    public final List<UpgradeIconTarget> upgradeIconTargets = new ArrayList<>();
    public UpgradeIconTarget hoveredUpgradeIcon = null;

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
     * Tracks a visible storage bus entry and its Y position for priority field placement.
     */
    public static class VisibleStorageBusEntry {
        public final StorageBusInfo storageBus;
        public final int y;

        public VisibleStorageBusEntry(StorageBusInfo storageBus, int y) {
            this.storageBus = storageBus;
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
        hoveredStorageBus = null;
        hoveredStorageBusPartitionSlot = -1;
        hoveredStorageBusContentSlot = -1;
        hoveredClearButtonStorageBus = null;
        hoveredIOModeButtonStorageBus = null;
        hoveredPartitionAllButtonStorageBus = null;
        hoveredPartitionAllButtonCell = null;
        hoveredClearPartitionButtonCell = null;
        storageBusPartitionSlotTargets.clear();
        visibleStorages.clear();
        visibleStorageBuses.clear();
        upgradeIconTargets.clear();
        hoveredUpgradeIcon = null;
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

    /**
     * Helper class to track storage bus partition slot positions for JEI ghost ingredients.
     */
    public static class StorageBusPartitionSlotTarget {
        public final StorageBusInfo storageBus;
        public final int slotIndex;
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        public StorageBusPartitionSlotTarget(StorageBusInfo storageBus, int slotIndex, int x, int y, int width, int height) {
            this.storageBus = storageBus;
            this.slotIndex = slotIndex;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Helper class to track upgrade icon positions for tooltips and click handling.
     */
    public static class UpgradeIconTarget {
        public final CellInfo cell;               // For cell upgrades (null for storage bus)
        public final StorageBusInfo storageBus;   // For storage bus upgrades (null for cell)
        public final ItemStack upgrade;
        public final int upgradeIndex;
        public final int x;
        public final int y;
        public static final int SIZE = 8;  // Small icon size

        public UpgradeIconTarget(CellInfo cell, ItemStack upgrade, int upgradeIndex, int x, int y) {
            this.cell = cell;
            this.storageBus = null;
            this.upgrade = upgrade;
            this.upgradeIndex = upgradeIndex;
            this.x = x;
            this.y = y;
        }

        public UpgradeIconTarget(StorageBusInfo storageBus, ItemStack upgrade, int upgradeIndex, int x, int y) {
            this.cell = null;
            this.storageBus = storageBus;
            this.upgrade = upgrade;
            this.upgradeIndex = upgradeIndex;
            this.x = x;
            this.y = y;
        }

        public boolean isMouseOver(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE;
        }
    }
}
