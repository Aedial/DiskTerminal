package com.cellterminal.container.handler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.AEApi;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellWorkbenchItem;

import com.cellterminal.container.ContainerCellTerminalBase;
import com.cellterminal.network.PacketTempCellAction;
import com.cellterminal.network.PacketTempCellPartitionAction;
import com.cellterminal.util.PlayerMessageHelper;


/**
 * Handles temp cell area actions for the cell terminal.
 */
public class TempCellActionHandler {

    /**
     * Handle temp cell action (insert, extract, send, upgrade).
     * @param toInventory For EXTRACT: if true, send directly to inventory instead of cursor (shift-click)
     *                    For UPGRADE: if true, server finds first cell that can accept
     */
    public static void handleAction(ContainerCellTerminalBase container,
                                     PacketTempCellAction.Action action,
                                     int tempSlotIndex, int playerSlotIndex,
                                     ItemStack itemStack, EntityPlayer player, boolean toInventory) {
        switch (action) {
            case INSERT:
                handleInsert(container, tempSlotIndex, playerSlotIndex, itemStack, player);
                break;
            case EXTRACT:
                handleExtract(container, tempSlotIndex, player, toInventory);
                break;
            case SEND:
                handleSend(container, tempSlotIndex, player);
                break;
            case UPGRADE:
                handleUpgrade(container, tempSlotIndex, playerSlotIndex, player, toInventory);
                break;
            case SWAP:
                handleSwap(container, tempSlotIndex, player);
                break;
        }
    }

    /**
     * Handle partition action on a temp cell.
     */
    public static void handlePartitionAction(ContainerCellTerminalBase container,
                                              int tempSlotIndex,
                                              PacketTempCellPartitionAction.Action action,
                                              int partitionSlot, ItemStack itemStack) {
        IItemHandlerModifiable tempInv = getTempCellInventory(container);
        if (tempInv == null) return;

        if (tempSlotIndex < 0 || tempSlotIndex >= tempInv.getSlots()) return;

        ItemStack cellStack = tempInv.getStackInSlot(tempSlotIndex);
        if (cellStack.isEmpty()) return;

        ICellHandler cellHandler = AEApi.instance().registries().cell().getHandler(cellStack);
        if (cellHandler == null) return;

        CellActionHandler.ConfigResult config = CellActionHandler.getConfigInventory(cellHandler, cellStack);
        if (config.configInv == null) return;

        // Execute the partition action using the same logic as regular cells
        CellActionHandler.executePartitionActionDirect(config.configInv, action, partitionSlot, itemStack,
            cellHandler, cellStack, config.isFluidCell, config.essentiaData);

        // Update the cell NBT in the temp inventory
        tempInv.setStackInSlot(tempSlotIndex, cellStack);

        // Mark the container as needing refresh
        markDirty(container);
    }

    /**
     * Insert a cell from player inventory into temp area.
     */
    private static void handleInsert(ContainerCellTerminalBase container, int tempSlotIndex,
                                      int playerSlotIndex, ItemStack itemStack, EntityPlayer player) {
        IItemHandlerModifiable tempInv = getTempCellInventory(container);
        if (tempInv == null) return;

        // Determine target slot
        int targetSlot = tempSlotIndex;
        if (targetSlot < 0) {
            // Find first empty slot
            for (int i = 0; i < tempInv.getSlots(); i++) {
                if (tempInv.getStackInSlot(i).isEmpty()) {
                    targetSlot = i;
                    break;
                }
            }

            if (targetSlot < 0) {
                PlayerMessageHelper.error(player, "gui.cellterminal.temp_area.full");

                return;
            }
        }

        if (targetSlot >= tempInv.getSlots()) return;

        // Check if target slot is empty
        if (!tempInv.getStackInSlot(targetSlot).isEmpty()) {
            PlayerMessageHelper.error(player, "gui.cellterminal.temp_area.slot_occupied");

            return;
        }

        // Get the cell from player inventory (only take ONE item from stack)
        ItemStack cellStack;
        if (!itemStack.isEmpty()) {
            // From JEI ghost drag - find matching stack in inventory
            // FIXME: JEI ghost dragging...? Why? Seems error-prone and something nobody would use.
            cellStack = findAndRemoveFromInventory(player, itemStack);
        } else if (playerSlotIndex >= 0) {
            // From specific slot - take only one item
            ItemStack sourceStack = player.inventory.getStackInSlot(playerSlotIndex);
            if (sourceStack.isEmpty()) {
                PlayerMessageHelper.error(player, "gui.cellterminal.temp_area.no_cell");

                return;
            }

            cellStack = sourceStack.splitStack(1);

            if (sourceStack.isEmpty()) {
                player.inventory.setInventorySlotContents(playerSlotIndex, ItemStack.EMPTY);
            }

            player.inventory.markDirty();
        } else {
            // From cursor - take only one item
            ItemStack cursorStack = player.inventory.getItemStack();
            if (cursorStack.isEmpty()) {
                PlayerMessageHelper.error(player, "gui.cellterminal.temp_area.no_cell");

                return;
            }

            cellStack = cursorStack.splitStack(1);

            // Update cursor (may be empty or have remaining items)
            if (cursorStack.isEmpty()) player.inventory.setItemStack(ItemStack.EMPTY);
            ((EntityPlayerMP) player).updateHeldItem();
        }

        if (cellStack.isEmpty()) {
            PlayerMessageHelper.error(player, "gui.cellterminal.temp_area.no_cell");

            return;
        }

        // Validate it's a storage cell
        if (!(cellStack.getItem() instanceof ICellWorkbenchItem)) {
            // Return to player cursor first, then inventory
            ItemStack cursorStack = player.inventory.getItemStack();
            if (cursorStack.isEmpty()) {
                player.inventory.setItemStack(cellStack);
                ((EntityPlayerMP) player).updateHeldItem();
            } else if (ItemStack.areItemsEqual(cursorStack, cellStack) &&
                       ItemStack.areItemStackTagsEqual(cursorStack, cellStack) &&
                       cursorStack.getCount() < cursorStack.getMaxStackSize()) {
                cursorStack.grow(1);
                ((EntityPlayerMP) player).updateHeldItem();
            } else if (!player.inventory.addItemStackToInventory(cellStack)) {
                player.dropItem(cellStack, false);
            }

            PlayerMessageHelper.error(player, "gui.cellterminal.temp_area.not_cell");

            return;
        }

        // Insert into temp area
        tempInv.setStackInSlot(targetSlot, cellStack);
        markDirty(container);
    }

    /**
     * Extract a cell from temp area back to player cursor or inventory.
     * @param toInventory If true, send directly to inventory (shift-click behavior).
     *                    If false, send to cursor first, then inventory if cursor is occupied.
     */
    private static void handleExtract(ContainerCellTerminalBase container, int tempSlotIndex,
                                       EntityPlayer player, boolean toInventory) {
        IItemHandlerModifiable tempInv = getTempCellInventory(container);
        if (tempInv == null) return;

        if (tempSlotIndex < 0 || tempSlotIndex >= tempInv.getSlots()) return;

        ItemStack cellStack = tempInv.getStackInSlot(tempSlotIndex);
        if (cellStack.isEmpty()) return;

        // Extract from temp area
        tempInv.setStackInSlot(tempSlotIndex, ItemStack.EMPTY);

        // Shift-click: send directly to inventory
        if (toInventory) {
            if (!player.inventory.addItemStackToInventory(cellStack)) {
                // Inventory full - drop item
                player.dropItem(cellStack, false);
            }

            markDirty(container);

            return;
        }

        // Normal click: try to give to cursor first
        ItemStack cursorStack = player.inventory.getItemStack();
        if (cursorStack.isEmpty()) {
            player.inventory.setItemStack(cellStack);
            ((EntityPlayerMP) player).updateHeldItem();
        } else if (ItemStack.areItemsEqual(cursorStack, cellStack) &&
                   ItemStack.areItemStackTagsEqual(cursorStack, cellStack) &&
                   cursorStack.getCount() < cursorStack.getMaxStackSize()) {
            // Can stack with cursor
            cursorStack.grow(cellStack.getCount());
            ((EntityPlayerMP) player).updateHeldItem();
        } else {
            // Cursor occupied - try inventory, then drop
            if (!player.inventory.addItemStackToInventory(cellStack)) player.dropItem(cellStack, false);
        }

        markDirty(container);
    }

    /**
     * Swap the cell in a temp area slot with the cell on the player's cursor.
     * The existing cell goes to the cursor, and the cursor cell goes into the slot.
     */
    private static void handleSwap(ContainerCellTerminalBase container, int tempSlotIndex, EntityPlayer player) {
        IItemHandlerModifiable tempInv = getTempCellInventory(container);
        if (tempInv == null) return;

        if (tempSlotIndex < 0 || tempSlotIndex >= tempInv.getSlots()) return;

        ItemStack existingCell = tempInv.getStackInSlot(tempSlotIndex);
        if (existingCell.isEmpty()) return;

        ItemStack cursorStack = player.inventory.getItemStack();
        if (cursorStack.isEmpty()) return;

        // Validate the cursor item is a valid cell
        if (!(cursorStack.getItem() instanceof ICellWorkbenchItem)) {
            PlayerMessageHelper.error(player, "gui.cellterminal.temp_area.not_cell");

            return;
        }

        // Only swap single items (cells don't stack, but just in case)
        ItemStack newCell = cursorStack.splitStack(1);

        // Put existing cell on cursor, new cell in slot
        tempInv.setStackInSlot(tempSlotIndex, newCell);

        // Give the old cell back to the cursor
        if (cursorStack.isEmpty()) {
            // Cursor is now empty after splitting - just set it to the existing cell
            player.inventory.setItemStack(existingCell);
        } else {
            // Cursor still has items (shouldn't happen for cells, but handle gracefully)
            // Try to add the existing cell to inventory, or drop it
            if (!player.inventory.addItemStackToInventory(existingCell)) {
                player.dropItem(existingCell, false);
            }
        }

        ((EntityPlayerMP) player).updateHeldItem();
        markDirty(container);
    }

    /**
     * Send a temp cell to the first available slot in the ME network.
     */
    private static void handleSend(ContainerCellTerminalBase container, int tempSlotIndex, EntityPlayer player) {
        IItemHandlerModifiable tempInv = getTempCellInventory(container);
        if (tempInv == null) return;

        if (tempSlotIndex < 0 || tempSlotIndex >= tempInv.getSlots()) return;

        ItemStack cellStack = tempInv.getStackInSlot(tempSlotIndex);
        if (cellStack.isEmpty()) return;

        // Find first available slot in ME network storage
        InsertResult result = findAndInsertIntoNetwork(container, cellStack);

        if (!result.success) {
            PlayerMessageHelper.error(player, "cellterminal.temp_area.no_slot");

            return;
        }

        // Remove from temp area
        tempInv.setStackInSlot(tempSlotIndex, ItemStack.EMPTY);
        markDirty(container);
        String gridName = container.getGridName();
        PlayerMessageHelper.success(player, "cellterminal.temp_area.sent", gridName);
    }

    /**
     * Insert an upgrade card into a temp cell.
     *
     * @param tempSlotIndex The temp slot to upgrade (-1 = find first that accepts)
     * @param fromSlot The player inventory slot to take the upgrade from (-1 = cursor)
     * @param shiftClick If true, find first cell that can accept this upgrade
     */
    private static void handleUpgrade(ContainerCellTerminalBase container, int tempSlotIndex,
                                       int fromSlot, EntityPlayer player, boolean shiftClick) {
        IItemHandlerModifiable tempInv = getTempCellInventory(container);
        if (tempInv == null) return;

        ItemStack upgradeStack = fromSlot >= 0
            ? player.inventory.getStackInSlot(fromSlot)
            : player.inventory.getItemStack();

        if (upgradeStack.isEmpty()) return;

        if (!(upgradeStack.getItem() instanceof appeng.api.implementations.items.IUpgradeModule)) return;

        // Shift-click: find first cell that can accept this upgrade
        if (shiftClick || tempSlotIndex < 0) {
            for (int i = 0; i < tempInv.getSlots(); i++) {
                if (tryInsertUpgradeIntoTempCell(tempInv, i, upgradeStack, player, fromSlot)) {
                    markDirty(container);

                    return;
                }
            }

            return;
        }

        // Regular click: use specific temp slot
        if (tempSlotIndex >= tempInv.getSlots()) return;

        if (tryInsertUpgradeIntoTempCell(tempInv, tempSlotIndex, upgradeStack, player, fromSlot)) {
            markDirty(container);
        }
    }

    /**
     * Try to insert an upgrade into a temp cell at the given slot.
     *
     * @return true if the upgrade was successfully inserted
     */
    private static boolean tryInsertUpgradeIntoTempCell(IItemHandlerModifiable tempInv, int tempSlotIndex,
                                                         ItemStack upgradeStack, EntityPlayer player, int fromSlot) {
        ItemStack cellStack = tempInv.getStackInSlot(tempSlotIndex);
        if (cellStack.isEmpty()) return false;

        if (!(cellStack.getItem() instanceof ICellWorkbenchItem)) return false;

        ICellWorkbenchItem cellItem = (ICellWorkbenchItem) cellStack.getItem();
        if (!cellItem.isEditable(cellStack)) return false;

        IItemHandler upgradesInv = cellItem.getUpgradesInventory(cellStack);
        if (upgradesInv == null) return false;

        ItemStack toInsert = upgradeStack.copy();
        toInsert.setCount(1);

        for (int slot = 0; slot < upgradesInv.getSlots(); slot++) {
            ItemStack remainder = upgradesInv.insertItem(slot, toInsert, false);
            if (remainder.isEmpty()) {
                upgradeStack.shrink(1);

                if (fromSlot >= 0) {
                    player.inventory.markDirty();
                } else {
                    ((EntityPlayerMP) player).updateHeldItem();
                }

                // Update the cell in temp inventory (NBT was modified)
                tempInv.setStackInSlot(tempSlotIndex, cellStack);

                return true;
            }
        }

        return false;
    }

    /**
     * Get the temp cell inventory from the container.
     */
    private static IItemHandlerModifiable getTempCellInventory(ContainerCellTerminalBase container) {
        return container.getTempCellInventory();
    }

    /**
     * Mark the container as needing refresh.
     */
    private static void markDirty(ContainerCellTerminalBase container) {
        // Trigger refresh
        container.requestFullRefresh();
    }

    /**
     * Find and remove a matching stack from player inventory.
     */
    private static ItemStack findAndRemoveFromInventory(EntityPlayer player, ItemStack target) {
        for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
            ItemStack stack = player.inventory.mainInventory.get(i);

            if (!stack.isEmpty() && ItemStack.areItemsEqual(stack, target) &&
                    ItemStack.areItemStackTagsEqual(stack, target)) {
                ItemStack extracted = stack.splitStack(1);

                if (stack.isEmpty()) player.inventory.mainInventory.set(i, ItemStack.EMPTY);

                return extracted;
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * Find and insert cell into first available network storage slot.
     */
    private static InsertResult findAndInsertIntoNetwork(ContainerCellTerminalBase container, ItemStack cellStack) {
        // Iterate through all tracked storages to find an empty slot
        for (ContainerCellTerminalBase.StorageTracker tracker : container.getStorageTrackers()) {
            IItemHandler cellInv = CellDataHandler.getCellInventory(tracker.storage);
            if (cellInv == null) continue;

            for (int slot = 0; slot < cellInv.getSlots(); slot++) {
                if (!cellInv.getStackInSlot(slot).isEmpty()) continue;

                // Found empty slot - try to insert
                ItemStack remainder = cellInv.insertItem(slot, cellStack, false);
                if (remainder.isEmpty()) {
                    tracker.tile.markDirty();

                    return new InsertResult(true, tracker.id, slot);
                }
            }
        }

        return new InsertResult(false, -1, -1);
    }

    public static class InsertResult {
        public final boolean success;
        public final long storageId;
        public final int slot;

        public InsertResult(boolean success, long storageId, int slot) {
            this.success = success;
            this.storageId = storageId;
            this.slot = slot;
        }
    }
}
