package com.cellterminal.gui;

import java.util.List;

import net.minecraft.item.ItemStack;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.fluids.items.FluidDummyItem;


/**
 * Utility methods for stack comparison in GUI code.
 */
public final class ComparisonUtils {

    private ComparisonUtils() {}

    /**
     * Check if an item stack is in the partition list.
     * For fluid items (FluidDummyItem), compares by fluid type only (ignoring amount and NBT).
     * For other items, uses item and NBT comparison (ignoring count).
     *
     * This is necessary because partitioning/filtering normalizes stacks to count 1,
     * without extra NBT data.
     */
    public static boolean isInPartition(ItemStack stack, List<ItemStack> partition) {
        if (stack.isEmpty()) return false;

        // Check if this is a fluid item
        FluidStack targetFluid = extractFluidFromStack(stack);

        if (targetFluid != null && targetFluid.getFluid() != null) {
            // Fluid comparison - compare by fluid type only
            for (ItemStack partItem : partition) {
                if (partItem.isEmpty()) continue;

                FluidStack partFluid = extractFluidFromStack(partItem);

                if (partFluid != null && partFluid.getFluid() == targetFluid.getFluid()) return true;
            }

            return false;
        }

        // Non-fluid comparison - compare by item and NBT only (ignore count)
        for (ItemStack partItem : partition) {
            if (partItem.isEmpty()) continue;

            if (ItemStack.areItemsEqual(stack, partItem) &&
                ItemStack.areItemStackTagsEqual(stack, partItem)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Extract a FluidStack from an ItemStack.
     * Handles both FluidDummyItem and fluid containers (buckets, tanks, etc.).
     */
    private static FluidStack extractFluidFromStack(ItemStack stack) {
        if (stack.isEmpty()) return null;

        if (stack.getItem() instanceof FluidDummyItem) {
            return ((FluidDummyItem) stack.getItem()).getFluidStack(stack);
        }

        return FluidUtil.getFluidContained(stack);
    }
}
