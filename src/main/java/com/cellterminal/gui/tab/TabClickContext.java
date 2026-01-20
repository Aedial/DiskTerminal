package com.cellterminal.gui.tab;

import java.util.Set;

import org.lwjgl.input.Keyboard;

import net.minecraft.item.ItemStack;

import com.cellterminal.client.CellInfo;
import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.client.StorageInfo;


/**
 * Context object containing all information about a click event on a tab.
 * This is passed to tab controllers to handle click logic.
 */
public class TabClickContext {

    // Mouse information
    public final int mouseX;
    public final int mouseY;
    public final int mouseButton;
    public final int relMouseX;  // Relative to GUI
    public final int relMouseY;  // Relative to GUI

    // GUI positioning
    public final int guiLeft;
    public final int guiTop;
    public final int rowsVisible;
    public final int currentScroll;

    // General tab context
    public final TabContext tabContext;

    // Hovered elements (set by renderer during draw)
    public CellInfo hoveredCell;
    public int hoveredCellHoverType;  // 0=none, 1=inventory, 2=partition, 3=eject
    public StorageInfo hoveredStorageLine;
    public int hoveredLineIndex;
    public CellInfo hoveredCellCell;
    public StorageInfo hoveredCellStorage;
    public int hoveredCellSlotIndex;
    public ItemStack hoveredContentStack;
    public int hoveredContentSlotIndex;
    public int hoveredPartitionSlotIndex;
    public CellInfo hoveredPartitionCell;

    // Storage bus hover state
    public StorageBusInfo hoveredStorageBus;
    public int hoveredStorageBusPartitionSlot;
    public int hoveredStorageBusContentSlot;
    public StorageBusInfo hoveredClearButtonStorageBus;
    public StorageBusInfo hoveredIOModeButtonStorageBus;
    public StorageBusInfo hoveredPartitionAllButtonStorageBus;
    public Set<Long> selectedStorageBusIds;

    // Cell button hover state
    public CellInfo hoveredPartitionAllButtonCell;
    public CellInfo hoveredClearPartitionButtonCell;

    public TabClickContext(int mouseX, int mouseY, int mouseButton,
                            int guiLeft, int guiTop, int rowsVisible, int currentScroll,
                            TabContext tabContext) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.mouseButton = mouseButton;
        this.relMouseX = mouseX - guiLeft;
        this.relMouseY = mouseY - guiTop;
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
        this.rowsVisible = rowsVisible;
        this.currentScroll = currentScroll;
        this.tabContext = tabContext;
    }

    /**
     * Copy hover state from the GUI after rendering.
     */
    public void copyHoverState(CellInfo hoveredCell, int hoverType,
                                StorageInfo hoveredStorageLine, int hoveredLineIndex,
                                CellInfo hoveredCellCell, StorageInfo hoveredCellStorage,
                                int hoveredCellSlotIndex, ItemStack hoveredContentStack,
                                int hoveredContentSlotIndex, int hoveredPartitionSlotIndex,
                                CellInfo hoveredPartitionCell,
                                StorageBusInfo hoveredStorageBus,
                                int hoveredStorageBusPartitionSlot,
                                int hoveredStorageBusContentSlot,
                                StorageBusInfo hoveredClearButtonStorageBus,
                                StorageBusInfo hoveredIOModeButtonStorageBus,
                                StorageBusInfo hoveredPartitionAllButtonStorageBus,
                                Set<Long> selectedStorageBusIds,
                                CellInfo hoveredPartitionAllButtonCell,
                                CellInfo hoveredClearPartitionButtonCell) {
        this.hoveredCell = hoveredCell;
        this.hoveredCellHoverType = hoverType;
        this.hoveredStorageLine = hoveredStorageLine;
        this.hoveredLineIndex = hoveredLineIndex;
        this.hoveredCellCell = hoveredCellCell;
        this.hoveredCellStorage = hoveredCellStorage;
        this.hoveredCellSlotIndex = hoveredCellSlotIndex;
        this.hoveredContentStack = hoveredContentStack;
        this.hoveredContentSlotIndex = hoveredContentSlotIndex;
        this.hoveredPartitionSlotIndex = hoveredPartitionSlotIndex;
        this.hoveredPartitionCell = hoveredPartitionCell;
        this.hoveredStorageBus = hoveredStorageBus;
        this.hoveredStorageBusPartitionSlot = hoveredStorageBusPartitionSlot;
        this.hoveredStorageBusContentSlot = hoveredStorageBusContentSlot;
        this.hoveredClearButtonStorageBus = hoveredClearButtonStorageBus;
        this.hoveredIOModeButtonStorageBus = hoveredIOModeButtonStorageBus;
        this.hoveredPartitionAllButtonStorageBus = hoveredPartitionAllButtonStorageBus;
        this.selectedStorageBusIds = selectedStorageBusIds;
        this.hoveredPartitionAllButtonCell = hoveredPartitionAllButtonCell;
        this.hoveredClearPartitionButtonCell = hoveredClearPartitionButtonCell;
    }

    /**
     * Check if this is a left click.
     */
    public boolean isLeftClick() {
        return mouseButton == 0;
    }

    /**
     * Check if this is a right click.
     */
    public boolean isRightClick() {
        return mouseButton == 1;
    }

    /**
     * Check if shift is held.
     */
    public boolean isShiftHeld() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }
}
