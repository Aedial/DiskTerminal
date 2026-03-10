package com.cellterminal.gui.widget.tab;

import java.util.Set;

import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.cellterminal.client.CellInfo;
import com.cellterminal.gui.handler.TerminalDataManager;
import com.cellterminal.gui.rename.Renameable;

import net.minecraft.util.math.BlockPos;


/**
 * Minimal interface that the parent GUI provides to tab widgets.
 * <p>
 * Replaces the old pattern of wiring dozens of individual callbacks per tab.
 * Tab widgets use this to communicate upward to the GUI for actions that require
 * GUI-level state (scrollbar, popups, modals, network packets).
 * <p>
 * The intent is to keep this as small as possible — each tab should handle its own
 * logic internally whenever feasible, and only call back to the GUI for truly
 * shared operations (sending network packets, opening popups, etc.).
 */
public interface GuiContext {

    // ---- Data access ----

    /** Get the terminal data manager for accessing cell/bus/line data. */
    TerminalDataManager getDataManager();

    /** Get the item stack currently held by the player's cursor. */
    ItemStack getHeldStack();

    /** Get the slot under the mouse cursor, or null if none. */
    Slot getSlotUnderMouse();

    /** Whether shift key is held. */
    boolean isShiftDown();

    // ---- Network packet helpers ----

    /** Send a packet to the server. */
    void sendPacket(Object packet);

    // ---- GUI-level actions ----

    /** Trigger a full rebuild of lines and scrollbar update. */
    void rebuildAndUpdateScrollbar();

    /** Scroll to a specific line index. */
    void scrollToLine(int lineIndex);

    /** Open an inventory preview popup for a cell. */
    void openInventoryPopup(CellInfo cell);

    /** Open a partition preview popup for a cell. */
    void openPartitionPopup(CellInfo cell);

    /** Start inline rename editing for a renameable target at the given row position. */
    void startInlineRename(Renameable target, int rowY, int renameX, int renameRightEdge);

    /** Send a rename packet for any renameable target. */
    void sendRenamePacket(Renameable target, String newName);

    /** Show an overlay error message. */
    void showError(String translationKey, Object... args);

    /** Show an overlay success message. */
    void showSuccess(String translationKey, Object... args);

    /** Show an overlay warning message. */
    void showWarning(String translationKey, Object... args);

    // ---- Highlight in world ----

    /**
     * Highlight a block position in the world (double-click on headers).
     * @param pos The block position to highlight
     * @param dimension The dimension ID where the block is located
     * @param displayName A display name to show in the success message
     */
    void highlightInWorld(BlockPos pos, int dimension, String displayName);

    /**
     * Highlight a cell's parent storage in the world (double-click on cells).
     * Finds the storage containing the cell and highlights its position.
     * @param cell The cell to highlight
     */
    void highlightCellInWorld(CellInfo cell);

    // ---- Selection state (for multi-select keybinds) ----

    /** Get the set of selected storage bus IDs. */
    Set<Long> getSelectedStorageBusIds();

    /** Get the set of selected temp cell slot indices. */
    Set<Integer> getSelectedTempCellSlots();
}
