package com.cellterminal.items.cells.highdensity;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;

import com.cellterminal.items.cells.compacting.IItemCompactingCell;


/**
 * Interface for high-density compacting storage cell items.
 * Combines the compacting cell functionality with the HD byte multiplier.
 * 
 * Due to overflow concerns (compacting uses base units * conversion rates),
 * HD compacting cells are limited to 16M tier maximum.
 */
public interface IItemHighDensityCompactingCell extends IItemCompactingCell {

    /**
     * Get the displayed byte capacity of this cell.
     * This is the value shown in tooltips and GUI.
     */
    long getDisplayBytes(@Nonnull ItemStack cellItem);

    /**
     * Get the internal byte multiplier.
     * The actual storage capacity is getDisplayBytes() * getByteMultiplier().
     */
    long getByteMultiplier();

    /**
     * Check if this ItemStack is a valid HD compacting storage cell.
     */
    boolean isHighDensityCompactingCell(@Nonnull ItemStack i);

    /**
     * Multiply two longs with overflow protection.
     */
    static long multiplyWithOverflowProtection(long a, long b) {
        if (a == 0 || b == 0) return 0;
        if (a < 0 || b < 0) return 0;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;

        return a * b;
    }
}
