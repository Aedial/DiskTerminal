package com.cellterminal.gui.handler;

import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import com.cellterminal.client.CellInfo;
import com.cellterminal.client.CellTerminalClientConfig;
import com.cellterminal.client.StorageInfo;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketEjectCell;
import com.cellterminal.network.PacketHighlightBlock;
import com.cellterminal.network.PacketInsertCell;
import com.cellterminal.network.PacketPartitionAction;
import com.cellterminal.network.PacketPickupCell;


/**
 * Handles mouse click logic for Cell Terminal GUI.
 */
public class TerminalClickHandler {

    // Tab constants
    public static final int TAB_TERMINAL = 0;
    public static final int TAB_INVENTORY = 1;
    public static final int TAB_PARTITION = 2;
    public static final int TABS_COUNT = 5;

    // Layout constants
    private static final int TAB_WIDTH = 22;
    private static final int TAB_HEIGHT = 22;
    private static final int TAB_Y_OFFSET = -22;
    private static final int ROW_HEIGHT = 18;
    private static final int BUTTON_SIZE = 14;
    private static final int BUTTON_EJECT_X = 135;
    private static final int BUTTON_INVENTORY_X = 150;
    private static final int BUTTON_PARTITION_X = 165;

    // Double-click tracking (Tab 1)
    private long lastClickTime = 0;
    private int lastClickedLineIndex = -1;

    // Double-click tracking (Tabs 2/3) - uses line index for reliability
    private long lastClickTimeTab23 = 0;
    private int lastClickedLineIndexTab23 = -1;

    // Callback interface for GUI interactions
    public interface Callback {
        void onTabChanged(int tab);
        void onStorageToggle(StorageInfo storage);
        void openInventoryPopup(CellInfo cell, int mouseX, int mouseY);
        void openPartitionPopup(CellInfo cell, int mouseX, int mouseY);
        void onTogglePartitionItem(CellInfo cell, ItemStack stack);
        void onAddPartitionItem(CellInfo cell, int slotIndex, ItemStack stack);
        void onRemovePartitionItem(CellInfo cell, int slotIndex);
    }

    public boolean handleTabClick(int mouseX, int mouseY, int guiLeft, int guiTop, int currentTab, Callback callback) {
        int tabY = guiTop + TAB_Y_OFFSET;

        for (int i = 0; i < TABS_COUNT; i++) {
            int tabX = guiLeft + 4 + (i * (TAB_WIDTH + 2));

            if (mouseX >= tabX && mouseX < tabX + TAB_WIDTH
                    && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                if (currentTab != i) {
                    callback.onTabChanged(i);
                    CellTerminalClientConfig.getInstance().setSelectedTab(i);
                }

                return true;
            }
        }

        return false;
    }

    public void handleTerminalTabClick(int mouseX, int mouseY, int mouseButton, int guiLeft, int guiTop,
            int rowsVisible, int currentScroll, List<Object> lines,
            Map<Long, StorageInfo> storageMap, int terminalDimension, Callback callback) {

        int relX = mouseX - guiLeft;
        int relY = mouseY - guiTop;

        if (relX < 4 || relX > 190 || relY < 18 || relY >= 18 + rowsVisible * ROW_HEIGHT) return;

        int row = (relY - 18) / ROW_HEIGHT;
        int lineIndex = currentScroll + row;

        if (lineIndex >= lines.size()) return;

        Object line = lines.get(lineIndex);

        // Check for held cell - insert into storage
        ItemStack heldStack = Minecraft.getMinecraft().player.inventory.getItemStack();
        if (!heldStack.isEmpty()) {
            long storageId = -1;

            if (line instanceof StorageInfo) {
                storageId = ((StorageInfo) line).getId();
            } else if (line instanceof CellInfo) {
                storageId = ((CellInfo) line).getParentStorageId();
            }

            if (storageId >= 0) {
                CellTerminalNetwork.INSTANCE.sendToServer(new PacketInsertCell(storageId, -1));

                return;
            }
        }

        // Check for double-click to highlight block
        long now = System.currentTimeMillis();
        if (lineIndex == lastClickedLineIndex && now - lastClickTime < 400) {
            handleDoubleClick(line, storageMap, terminalDimension);
            lastClickedLineIndex = -1;

            return;
        }

        lastClickedLineIndex = lineIndex;
        lastClickTime = now;

        if (line instanceof StorageInfo) {
            StorageInfo storage = (StorageInfo) line;

            if (relX >= 165 && relX < 180) {
                storage.toggleExpanded();
                callback.onStorageToggle(storage);
            }

            return;
        }

        if (line instanceof CellInfo) {
            handleCellClick((CellInfo) line, relX, relY, row, mouseX, mouseY, callback);
        }
    }

    public void handleCellTabClick(int currentTab, CellInfo hoveredCellCell, int hoveredContentSlotIndex,
            CellInfo hoveredPartitionCell, int hoveredPartitionSlotIndex,
            StorageInfo hoveredCellStorage, int hoveredCellSlotIndex,
            StorageInfo hoveredStorageLine, int hoveredLineIndex,
            Map<Long, StorageInfo> storageMap, int terminalDimension, Callback callback) {

        // Check for double-click to highlight block (on storage entries or cells)
        long now = System.currentTimeMillis();
        StorageInfo clickedStorage = hoveredCellStorage;

        // Determine which storage was clicked (either directly or via cell/content)
        if (clickedStorage == null && hoveredStorageLine != null) clickedStorage = hoveredStorageLine;

        if (clickedStorage == null && hoveredCellCell != null) {
            clickedStorage = storageMap.get(hoveredCellCell.getParentStorageId());
        }

        if (clickedStorage == null && hoveredPartitionCell != null) {
            clickedStorage = storageMap.get(hoveredPartitionCell.getParentStorageId());
        }

        // Use line index for double-click detection (more reliable than storage ID)
        if (hoveredLineIndex >= 0 && hoveredLineIndex == lastClickedLineIndexTab23
                && now - lastClickTimeTab23 < 400) {
            // Double-click detected - highlight the storage
            if (clickedStorage != null) handleDoubleClickTab23(clickedStorage, terminalDimension);
            lastClickedLineIndexTab23 = -1;

            return;
        }

        lastClickedLineIndexTab23 = hoveredLineIndex;
        lastClickTimeTab23 = now;

        // Tab 2 (Inventory): Check if clicking on a content item to toggle partition
        if (currentTab == TAB_INVENTORY && hoveredCellCell != null && hoveredContentSlotIndex >= 0) {
            List<ItemStack> contents = hoveredCellCell.getContents();

            if (hoveredContentSlotIndex < contents.size() && !contents.get(hoveredContentSlotIndex).isEmpty()) {
                callback.onTogglePartitionItem(hoveredCellCell, contents.get(hoveredContentSlotIndex));

                return;
            }
        }

        // Tab 3 (Partition): Check if clicking on a partition slot
        if (currentTab == TAB_PARTITION && hoveredPartitionCell != null && hoveredPartitionSlotIndex >= 0) {
            List<ItemStack> partitions = hoveredPartitionCell.getPartition();
            ItemStack heldStack = Minecraft.getMinecraft().player.inventory.getItemStack();
            boolean slotOccupied = hoveredPartitionSlotIndex < partitions.size() && !partitions.get(hoveredPartitionSlotIndex).isEmpty();

            if (!heldStack.isEmpty()) {
                callback.onAddPartitionItem(hoveredPartitionCell, hoveredPartitionSlotIndex, heldStack);

                return;
            }

            if (slotOccupied) {
                callback.onRemovePartitionItem(hoveredPartitionCell, hoveredPartitionSlotIndex);

                return;
            }
        }

        // Handle cell slot clicks (pickup/swap cells)
        // Shift-click: eject to inventory, regular click: pick up to hand
        if (hoveredCellStorage == null || hoveredCellSlotIndex < 0) return;

        boolean toInventory = GuiScreen.isShiftKeyDown();
        CellTerminalNetwork.INSTANCE.sendToServer(
            new PacketPickupCell(hoveredCellStorage.getId(), hoveredCellSlotIndex, toInventory)
        );
    }

    private void handleDoubleClick(Object line, Map<Long, StorageInfo> storageMap, int terminalDimension) {
        StorageInfo storage = null;

        if (line instanceof StorageInfo) {
            storage = (StorageInfo) line;
        } else if (line instanceof CellInfo) {
            CellInfo cell = (CellInfo) line;
            storage = storageMap.get(cell.getParentStorageId());
        }

        if (storage == null) return;

        highlightStorage(storage);
    }

    private void handleDoubleClickTab23(StorageInfo storage, int terminalDimension) {
        if (storage == null) return;

        highlightStorage(storage);
    }

    private void highlightStorage(StorageInfo storage) {
        if (storage.getDimension() != Minecraft.getMinecraft().player.dimension) {
            Minecraft.getMinecraft().player.sendMessage(
                new TextComponentTranslation("cellterminal.error.different_dimension")
            );

            return;
        }

        CellTerminalNetwork.INSTANCE.sendToServer(
            new PacketHighlightBlock(storage.getPos(), storage.getDimension())
        );

        // Send green chat message with block name and coordinates
        String blockName = storage.getName();
        String coords = String.format("[%d, %d, %d]",
            storage.getPos().getX(), storage.getPos().getY(), storage.getPos().getZ());
        String message = TextFormatting.GREEN + blockName + " - " + coords;
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(message));
    }

    private void handleCellClick(CellInfo cell, int relX, int relY, int row, int mouseX, int mouseY, Callback callback) {
        int rowY = 18 + row * ROW_HEIGHT;

        // Eject button: always ejects to player's inventory
        if (relX >= BUTTON_EJECT_X && relX < BUTTON_EJECT_X + BUTTON_SIZE
                && relY >= rowY + 1 && relY < rowY + 1 + BUTTON_SIZE) {
            CellTerminalNetwork.INSTANCE.sendToServer(
                new PacketEjectCell(cell.getParentStorageId(), cell.getSlot())
            );

            return;
        }

        if (relX >= BUTTON_INVENTORY_X && relX < BUTTON_INVENTORY_X + BUTTON_SIZE
                && relY >= rowY + 1 && relY < rowY + 1 + BUTTON_SIZE) {
            callback.openInventoryPopup(cell, mouseX, mouseY);

            return;
        }

        if (relX >= BUTTON_PARTITION_X && relX < BUTTON_PARTITION_X + BUTTON_SIZE
                && relY >= rowY + 1 && relY < rowY + 1 + BUTTON_SIZE) {
            callback.openPartitionPopup(cell, mouseX, mouseY);
        }
    }
}
