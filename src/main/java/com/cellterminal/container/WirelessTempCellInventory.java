package com.cellterminal.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.storage.ICellWorkbenchItem;

import com.cellterminal.items.ItemWirelessCellTerminal;


/**
 * IItemHandlerModifiable wrapper for the wireless terminal's temp cell storage.
 * This allows the temp area tab to work with wireless terminals by storing
 * temp cells in the terminal item's NBT data.
 * <p>
 * Unlike the tile-based PartCellTerminal which has its own inventory,
 * this wrapper reads/writes directly to the wireless terminal ItemStack's NBT.
 */
public class WirelessTempCellInventory implements IItemHandlerModifiable {

    private final EntityPlayer player;
    private final int terminalSlot;
    private final boolean isBauble;

    /**
     * Create a temp cell inventory for a wireless terminal.
     *
     * @param player The player holding the terminal
     * @param terminalSlot The inventory slot containing the terminal (or bauble slot)
     * @param isBauble Whether the terminal is in a bauble slot
     */
    public WirelessTempCellInventory(EntityPlayer player, int terminalSlot, boolean isBauble) {
        this.player = player;
        this.terminalSlot = terminalSlot;
        this.isBauble = isBauble;
    }

    /**
     * Get the wireless terminal ItemStack.
     */
    private ItemStack getTerminalStack() {
        if (isBauble) return getBaubleStack();

        return player.inventory.getStackInSlot(terminalSlot);
    }

    /**
     * Get the wireless terminal item instance.
     */
    private ItemWirelessCellTerminal getTerminalItem() {
        ItemStack stack = getTerminalStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemWirelessCellTerminal)) return null;

        return (ItemWirelessCellTerminal) stack.getItem();
    }

    /**
     * Get the terminal stack from bauble slot.
     */
    private ItemStack getBaubleStack() {
        try {
            return baubles.api.BaublesApi.getBaublesHandler(player).getStackInSlot(terminalSlot);
        } catch (Throwable e) {
            // Baubles not loaded
            return ItemStack.EMPTY;
        }
    }

    @Override
    public int getSlots() {
        ItemWirelessCellTerminal item = getTerminalItem();

        return item != null ? item.getMaxTempCells() : 0;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        ItemWirelessCellTerminal item = getTerminalItem();
        if (item == null) return ItemStack.EMPTY;

        return item.getTempCell(getTerminalStack(), slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        ItemWirelessCellTerminal item = getTerminalItem();
        if (item == null) return;

        item.setTempCell(getTerminalStack(), slot, stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        // Only accept valid cell items
        if (!(stack.getItem() instanceof ICellWorkbenchItem)) return stack;

        ItemStack existing = getStackInSlot(slot);
        if (!existing.isEmpty()) return stack; // Slot occupied

        if (!simulate) setStackInSlot(slot, stack.copy());

        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack existing = getStackInSlot(slot);
        if (existing.isEmpty() || amount <= 0) return ItemStack.EMPTY;

        // Cells don't stack, extract the whole cell
        if (!simulate) setStackInSlot(slot, ItemStack.EMPTY);

        return existing.copy();
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1; // One cell per slot
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return stack.isEmpty() || stack.getItem() instanceof ICellWorkbenchItem;
    }
}
