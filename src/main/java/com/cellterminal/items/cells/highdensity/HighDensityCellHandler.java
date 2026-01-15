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
 * Cell handler for high-density storage cells.
 * Registered with AE2's cell registry to handle high-density cell items.
 */
public class HighDensityCellHandler implements ICellHandler {

    @Override
    public boolean isCell(ItemStack is) {
        if (is.isEmpty()) return false;

        return is.getItem() instanceof IItemHighDensityCell
            && ((IItemHighDensityCell) is.getItem()).isHighDensityCell(is);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(ItemStack is, ISaveProvider container, IStorageChannel<T> channel) {
        if (!isCell(is)) return null;

        // High-density cells only work with item channel
        IStorageChannel<IAEItemStack> itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        if (channel != itemChannel) return null;

        IItemHighDensityCell cellType = (IItemHighDensityCell) is.getItem();

        HighDensityCellInventory inventory = new HighDensityCellInventory(cellType, is, container);

        return (ICellInventoryHandler<T>) new HighDensityCellInventoryHandler(inventory, itemChannel);
    }
}
