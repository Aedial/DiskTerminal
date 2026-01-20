package com.cellterminal.gui.tab;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import com.cellterminal.client.CellInfo;
import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.client.StorageInfo;
import com.cellterminal.gui.handler.TerminalDataManager;


/**
 * Context object providing access to tab-related state and services.
 * Passed to tab controllers to give them access to the data they need.
 */
public class TabContext {

    private final TerminalDataManager dataManager;
    private final TabContextCallback callback;
    private final int terminalDimension;

    public TabContext(TerminalDataManager dataManager, TabContextCallback callback, int terminalDimension) {
        this.dataManager = dataManager;
        this.callback = callback;
        this.terminalDimension = terminalDimension;
    }

    public TerminalDataManager getDataManager() {
        return dataManager;
    }

    public Map<Long, StorageInfo> getStorageMap() {
        return dataManager.getStorageMap();
    }

    public Map<Long, StorageBusInfo> getStorageBusMap() {
        return dataManager.getStorageBusMap();
    }

    public int getTerminalDimension() {
        return terminalDimension;
    }

    public List<Object> getTerminalLines() {
        return dataManager.getLines();
    }

    public List<Object> getInventoryLines() {
        return dataManager.getInventoryLines();
    }

    public List<Object> getPartitionLines() {
        return dataManager.getPartitionLines();
    }

    public List<Object> getStorageBusInventoryLines() {
        return dataManager.getStorageBusInventoryLines();
    }

    public List<Object> getStorageBusPartitionLines() {
        return dataManager.getStorageBusPartitionLines();
    }

    // Callback methods for GUI interactions

    public void toggleStorageExpansion(StorageInfo storage) {
        callback.onStorageToggle(storage);
    }

    public void openInventoryPopup(CellInfo cell, int mouseX, int mouseY) {
        callback.openInventoryPopup(cell, mouseX, mouseY);
    }

    public void openPartitionPopup(CellInfo cell, int mouseX, int mouseY) {
        callback.openPartitionPopup(cell, mouseX, mouseY);
    }

    public void togglePartitionItem(CellInfo cell, ItemStack stack) {
        callback.onTogglePartitionItem(cell, stack);
    }

    public void addPartitionItem(CellInfo cell, int slotIndex, ItemStack stack) {
        callback.onAddPartitionItem(cell, slotIndex, stack);
    }

    public void removePartitionItem(CellInfo cell, int slotIndex) {
        callback.onRemovePartitionItem(cell, slotIndex);
    }

    public void scrollToLine(int lineIndex) {
        callback.scrollToLine(lineIndex);
    }

    /**
     * Callback interface for GUI interactions triggered by tab controllers.
     */
    public interface TabContextCallback {

        void onStorageToggle(StorageInfo storage);

        void openInventoryPopup(CellInfo cell, int mouseX, int mouseY);

        void openPartitionPopup(CellInfo cell, int mouseX, int mouseY);

        void onTogglePartitionItem(CellInfo cell, ItemStack stack);

        void onAddPartitionItem(CellInfo cell, int slotIndex, ItemStack stack);

        void onRemovePartitionItem(CellInfo cell, int slotIndex);

        void scrollToLine(int lineIndex);
    }
}
