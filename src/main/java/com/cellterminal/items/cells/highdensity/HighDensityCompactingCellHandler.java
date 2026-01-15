package com.cellterminal.items.cells.highdensity;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;


/**
 * Cell handler for high-density compacting storage cells.
 * Registered with AE2's cell registry.
 */
public class HighDensityCompactingCellHandler implements ICellHandler {

    @Override
    public boolean isCell(ItemStack is) {
        if (is.isEmpty()) return false;

        return is.getItem() instanceof IItemHighDensityCompactingCell
            && ((IItemHighDensityCompactingCell) is.getItem()).isHighDensityCompactingCell(is);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(ItemStack is, ISaveProvider container, IStorageChannel<T> channel) {
        if (!isCell(is)) return null;

        IStorageChannel<IAEItemStack> itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        if (channel != itemChannel) return null;

        IItemHighDensityCompactingCell cellType = (IItemHighDensityCompactingCell) is.getItem();
        HighDensityCompactingCellInventory inventory = new HighDensityCompactingCellInventory(cellType, is, container);

        return (ICellInventoryHandler<T>) new HighDensityCompactingCellInventoryHandler(inventory, itemChannel);
    }
}
