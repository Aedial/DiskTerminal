package com.cellterminal.gui.storagebus;

import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;

import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.gui.render.RenderContext;


/**
 * Tracks hover state for storage bus-related GUI elements.
 *
 * This class encapsulates all the hover tracking for storage buses in the
 * Storage Bus Inventory and Partition tabs. It is updated during rendering
 * and consumed by click handlers and tooltip rendering.
 */
public class StorageBusHoverState {

    // Hovered storage bus
    private StorageBusInfo hoveredStorageBus = null;

    // Hovered content slot in a storage bus
    private int hoveredStorageBusContentSlot = -1;
    private ItemStack hoveredContentStack = ItemStack.EMPTY;
    private int hoveredContentX = 0;
    private int hoveredContentY = 0;

    // Hovered partition slot
    private int hoveredStorageBusPartitionSlot = -1;

    // Hovered action buttons
    private StorageBusInfo hoveredClearButtonStorageBus = null;
    private StorageBusInfo hoveredIOModeButtonStorageBus = null;
    private StorageBusInfo hoveredPartitionAllButtonStorageBus = null;

    // Line hover
    private int hoveredLineIndex = -1;

    // JEI ghost ingredient targets
    private final List<RenderContext.StorageBusPartitionSlotTarget> partitionSlotTargets;

    public StorageBusHoverState(List<RenderContext.StorageBusPartitionSlotTarget> partitionSlotTargets) {
        this.partitionSlotTargets = partitionSlotTargets;
    }

    /**
     * Reset all hover state at the beginning of a render cycle.
     */
    public void reset() {
        hoveredStorageBus = null;
        hoveredStorageBusContentSlot = -1;
        hoveredContentStack = ItemStack.EMPTY;
        hoveredContentX = 0;
        hoveredContentY = 0;
        hoveredStorageBusPartitionSlot = -1;
        hoveredClearButtonStorageBus = null;
        hoveredIOModeButtonStorageBus = null;
        hoveredPartitionAllButtonStorageBus = null;
        hoveredLineIndex = -1;
        partitionSlotTargets.clear();
    }

    // ========================================
    // STORAGE BUS HOVER
    // ========================================

    public void setHoveredStorageBus(StorageBusInfo storageBus) {
        this.hoveredStorageBus = storageBus;
    }

    public StorageBusInfo getHoveredStorageBus() {
        return hoveredStorageBus;
    }

    // ========================================
    // CONTENT SLOT HOVER
    // ========================================

    public void setHoveredContentSlot(int slotIndex, ItemStack stack, int absX, int absY) {
        this.hoveredStorageBusContentSlot = slotIndex;
        this.hoveredContentStack = stack;
        this.hoveredContentX = absX;
        this.hoveredContentY = absY;
    }

    public int getHoveredStorageBusContentSlot() {
        return hoveredStorageBusContentSlot;
    }

    public ItemStack getHoveredContentStack() {
        return hoveredContentStack;
    }

    public int getHoveredContentX() {
        return hoveredContentX;
    }

    public int getHoveredContentY() {
        return hoveredContentY;
    }

    // ========================================
    // PARTITION SLOT HOVER
    // ========================================

    public void setHoveredPartitionSlot(int slotIndex) {
        this.hoveredStorageBusPartitionSlot = slotIndex;
    }

    public int getHoveredStorageBusPartitionSlot() {
        return hoveredStorageBusPartitionSlot;
    }

    // ========================================
    // BUTTON HOVER
    // ========================================

    public void setHoveredClearButton(StorageBusInfo storageBus) {
        this.hoveredClearButtonStorageBus = storageBus;
    }

    public StorageBusInfo getHoveredClearButtonStorageBus() {
        return hoveredClearButtonStorageBus;
    }

    public void setHoveredIOModeButton(StorageBusInfo storageBus) {
        this.hoveredIOModeButtonStorageBus = storageBus;
    }

    public StorageBusInfo getHoveredIOModeButtonStorageBus() {
        return hoveredIOModeButtonStorageBus;
    }

    public void setHoveredPartitionAllButton(StorageBusInfo storageBus) {
        this.hoveredPartitionAllButtonStorageBus = storageBus;
    }

    public StorageBusInfo getHoveredPartitionAllButtonStorageBus() {
        return hoveredPartitionAllButtonStorageBus;
    }

    // ========================================
    // LINE HOVER
    // ========================================

    public void setHoveredLineIndex(int lineIndex) {
        this.hoveredLineIndex = lineIndex;
    }

    public int getHoveredLineIndex() {
        return hoveredLineIndex;
    }

    // ========================================
    // JEI GHOST TARGETS
    // ========================================

    public void addPartitionSlotTarget(StorageBusInfo storageBus, int slotIndex, int x, int y, int width, int height) {
        partitionSlotTargets.add(new RenderContext.StorageBusPartitionSlotTarget(storageBus, slotIndex, x, y, width, height));
    }

    public List<RenderContext.StorageBusPartitionSlotTarget> getPartitionSlotTargets() {
        return partitionSlotTargets;
    }

    /**
     * Copy hover state to a RenderContext for compatibility with existing code.
     */
    public void copyToRenderContext(RenderContext ctx) {
        ctx.hoveredStorageBus = hoveredStorageBus;
        ctx.hoveredStorageBusContentSlot = hoveredStorageBusContentSlot;
        ctx.hoveredStorageBusPartitionSlot = hoveredStorageBusPartitionSlot;
        ctx.hoveredClearButtonStorageBus = hoveredClearButtonStorageBus;
        ctx.hoveredIOModeButtonStorageBus = hoveredIOModeButtonStorageBus;
        ctx.hoveredPartitionAllButtonStorageBus = hoveredPartitionAllButtonStorageBus;
        ctx.hoveredLineIndex = hoveredLineIndex;

        // Copy content stack to context if set
        if (!hoveredContentStack.isEmpty()) {
            ctx.hoveredContentStack = hoveredContentStack;
            ctx.hoveredContentX = hoveredContentX;
            ctx.hoveredContentY = hoveredContentY;
        }
    }
}
