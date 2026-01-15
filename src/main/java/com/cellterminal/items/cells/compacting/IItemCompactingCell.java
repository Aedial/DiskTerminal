package com.cellterminal.items.cells.compacting;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.data.IAEItemStack;


/**
 * Interface for compacting storage cell items.
 * Extends the standard cell workbench functionality.
 */
public interface IItemCompactingCell extends ICellWorkbenchItem {

    /**
     * Get the total byte capacity of this cell.
     * Returns long to support cells larger than 2GB.
     */
    long getBytes(@Nonnull ItemStack cellItem);

    /**
     * Get the bytes used per stored type.
     */
    long getBytesPerType(@Nonnull ItemStack cellItem);

    /**
     * Get the idle power drain of this cell.
     */
    double getIdleDrain();

    /**
     * Check if the given item is blacklisted from this cell.
     */
    boolean isBlackListed(@Nonnull ItemStack cellItem, @Nonnull IAEItemStack requestedAddition);

    /**
     * Check if this cell can be stored inside other storage cells.
     */
    boolean storableInStorageCell();

    /**
     * Check if this ItemStack is a valid compacting storage cell.
     */
    boolean isCompactingCell(@Nonnull ItemStack i);
}
