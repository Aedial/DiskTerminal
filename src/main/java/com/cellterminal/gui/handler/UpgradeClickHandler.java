package com.cellterminal.gui.handler;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import appeng.api.implementations.items.IUpgradeModule;

import com.cellterminal.client.CellContentRow;
import com.cellterminal.client.CellInfo;
import com.cellterminal.client.StorageBusContentRow;
import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.client.StorageInfo;
import com.cellterminal.config.CellTerminalServerConfig;
import com.cellterminal.gui.overlay.MessageHelper;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketUpgradeCell;
import com.cellterminal.network.PacketUpgradeStorageBus;

/**
 * Handler for upgrade click operations in the Cell Terminal GUI.
 */
public class UpgradeClickHandler {

    private UpgradeClickHandler() {}

    /**
     * Context for upgrade click handling.
     */
    public static class UpgradeClickContext {
        public final int currentTab;
        public final CellInfo hoveredCellCell;
        public final StorageInfo hoveredCellStorage;
        public final int hoveredCellSlotIndex;
        public final StorageInfo hoveredStorageLine;
        public final StorageBusInfo hoveredStorageBus;
        public final TerminalDataManager dataManager;

        public UpgradeClickContext(int currentTab, CellInfo hoveredCellCell, StorageInfo hoveredCellStorage,
                                   int hoveredCellSlotIndex, StorageInfo hoveredStorageLine,
                                   StorageBusInfo hoveredStorageBus, TerminalDataManager dataManager) {
            this.currentTab = currentTab;
            this.hoveredCellCell = hoveredCellCell;
            this.hoveredCellStorage = hoveredCellStorage;
            this.hoveredCellSlotIndex = hoveredCellSlotIndex;
            this.hoveredStorageLine = hoveredStorageLine;
            this.hoveredStorageBus = hoveredStorageBus;
            this.dataManager = dataManager;
        }
    }

    /**
     * Tab constants (must match GuiCellTerminalBase).
     */
    public static final int TAB_TERMINAL = 0;
    public static final int TAB_INVENTORY = 1;
    public static final int TAB_PARTITION = 2;
    public static final int TAB_STORAGE_BUS_INVENTORY = 3;
    public static final int TAB_STORAGE_BUS_PARTITION = 4;

    /**
     * Handle upgrade click when player is holding an upgrade item.
     *
     * @param ctx Context with hover state
     * @return true if an upgrade click was handled
     */
    public static boolean handleUpgradeClick(UpgradeClickContext ctx) {
        ItemStack heldStack = Minecraft.getMinecraft().player.inventory.getItemStack();
        if (heldStack.isEmpty()) return false;
        if (!(heldStack.getItem() instanceof IUpgradeModule)) return false;

        // Check if upgrade insertion is enabled
        if (CellTerminalServerConfig.isInitialized()
                && !CellTerminalServerConfig.getInstance().isUpgradeInsertEnabled()) {
            MessageHelper.error("cellterminal.error.upgrade_insert_disabled");

            return true;  // Consume click to prevent other handlers
        }

        boolean isShiftClick = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        if (isShiftClick) {
            CellInfo targetCell = findFirstVisibleCellThatCanAcceptUpgrade(ctx, heldStack);
            if (targetCell == null) return false;

            StorageInfo storage = ctx.dataManager.getStorageMap().get(targetCell.getParentStorageId());
            if (storage == null) return false;

            CellTerminalNetwork.INSTANCE.sendToServer(new PacketUpgradeCell(
                storage.getId(),
                targetCell.getSlot(),
                true
            ));

            return true;
        }

        // Regular click: check if hovering a cell directly
        if (ctx.hoveredCellCell != null && ctx.hoveredCellStorage != null) {
            if (!ctx.hoveredCellCell.canAcceptUpgrade(heldStack)) return false;

            CellTerminalNetwork.INSTANCE.sendToServer(new PacketUpgradeCell(
                ctx.hoveredCellStorage.getId(),
                ctx.hoveredCellSlotIndex,
                false
            ));

            return true;
        }

        // Regular click on storage header: upgrade first cell in that storage
        if (ctx.hoveredStorageLine != null) {
            CellInfo targetCell = findFirstCellInStorageThatCanAcceptUpgrade(ctx.hoveredStorageLine, heldStack);
            if (targetCell == null) return false;

            CellTerminalNetwork.INSTANCE.sendToServer(new PacketUpgradeCell(
                ctx.hoveredStorageLine.getId(),
                targetCell.getSlot(),
                false
            ));

            return true;
        }

        return false;
    }

    /**
     * Handle upgrade click on storage bus headers when player is holding an upgrade item.
     *
     * @param hoveredStorageBus The storage bus being hovered
     * @return true if an upgrade click was handled
     */
    public static boolean handleStorageBusUpgradeClick(StorageBusInfo hoveredStorageBus) {
        if (hoveredStorageBus == null) return false;

        ItemStack heldStack = Minecraft.getMinecraft().player.inventory.getItemStack();
        if (heldStack.isEmpty()) return false;
        if (!(heldStack.getItem() instanceof IUpgradeModule)) return false;

        // Check if upgrade insertion is enabled
        if (CellTerminalServerConfig.isInitialized()
                && !CellTerminalServerConfig.getInstance().isUpgradeInsertEnabled()) {
            MessageHelper.error("cellterminal.error.upgrade_insert_disabled");

            return true;  // Consume click to prevent other handlers
        }

        CellTerminalNetwork.INSTANCE.sendToServer(new PacketUpgradeStorageBus(hoveredStorageBus.getId()));

        return true;
    }

    /**
     * Find the first visible cell that can accept the given upgrade.
     */
    public static CellInfo findFirstVisibleCellThatCanAcceptUpgrade(UpgradeClickContext ctx, ItemStack upgradeStack) {
        List<Object> lines;

        switch (ctx.currentTab) {
            case TAB_TERMINAL:
                lines = ctx.dataManager.getLines();
                break;
            case TAB_INVENTORY:
                lines = ctx.dataManager.getInventoryLines();
                break;
            case TAB_PARTITION:
                lines = ctx.dataManager.getPartitionLines();
                break;
            default:
                return null;
        }

        Set<Long> checkedCellIds = new HashSet<>();

        for (Object line : lines) {
            CellInfo cell = null;

            if (line instanceof CellInfo) {
                cell = (CellInfo) line;
            } else if (line instanceof CellContentRow) {
                cell = ((CellContentRow) line).getCell();
            }

            if (cell == null) continue;

            long cellId = cell.getParentStorageId() * 100 + cell.getSlot();
            if (checkedCellIds.contains(cellId)) continue;
            checkedCellIds.add(cellId);

            if (cell.canAcceptUpgrade(upgradeStack)) return cell;
        }

        return null;
    }

    /**
     * Find the first cell in a specific storage that can accept the given upgrade.
     */
    public static CellInfo findFirstCellInStorageThatCanAcceptUpgrade(StorageInfo storage, ItemStack upgradeStack) {
        for (CellInfo cell : storage.getCells()) {
            if (cell.canAcceptUpgrade(upgradeStack)) return cell;
        }

        return null;
    }

    /**
     * Find the first visible storage bus that can accept the given upgrade.
     * Used for shift-clicking upgrades from inventory on storage bus tabs.
     */
    public static StorageBusInfo findFirstVisibleStorageBusThatCanAcceptUpgrade(UpgradeClickContext ctx, ItemStack upgradeStack) {
        List<Object> lines;

        switch (ctx.currentTab) {
            case TAB_STORAGE_BUS_INVENTORY:
                lines = ctx.dataManager.getStorageBusInventoryLines();
                break;
            case TAB_STORAGE_BUS_PARTITION:
                lines = ctx.dataManager.getStorageBusPartitionLines();
                break;
            default:
                return null;
        }

        Set<Long> checkedBusIds = new HashSet<>();

        for (Object line : lines) {
            StorageBusInfo bus = null;

            if (line instanceof StorageBusInfo) {
                bus = (StorageBusInfo) line;
            } else if (line instanceof StorageBusContentRow) {
                bus = ((StorageBusContentRow) line).getStorageBus();
            }

            if (bus == null) continue;

            long busId = bus.getId();
            if (checkedBusIds.contains(busId)) continue;
            checkedBusIds.add(busId);

            if (bus.canAcceptUpgrade(upgradeStack)) return bus;
        }

        return null;
    }
}
