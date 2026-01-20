package com.cellterminal.gui.handler;

import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;

import net.minecraftforge.fluids.FluidStack;

import appeng.fluids.items.FluidDummyItem;

import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.integration.ThaumicEnergisticsIntegration;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketHighlightBlock;
import com.cellterminal.network.PacketStorageBusIOMode;
import com.cellterminal.network.PacketStorageBusPartitionAction;


/**
 * Handles click logic for storage bus tabs (tabs 4 and 5).
 */
public class StorageBusClickHandler {

    // Tab constants
    public static final int TAB_STORAGE_BUS_INVENTORY = 3;
    public static final int TAB_STORAGE_BUS_PARTITION = 4;

    // Double-click tracking
    private long lastClickedStorageBusId = -1;
    private long lastClickTimeStorageBus = 0;
    private static final long DOUBLE_CLICK_TIME = 400;

    /**
     * Context containing all the hover state needed for handling clicks.
     */
    public static class ClickContext {
        public int currentTab;
        public StorageBusInfo hoveredStorageBus;
        public int hoveredStorageBusPartitionSlot = -1;
        public int hoveredStorageBusContentSlot = -1;
        public StorageBusInfo hoveredClearButtonStorageBus;
        public StorageBusInfo hoveredIOModeButtonStorageBus;
        public StorageBusInfo hoveredPartitionAllButtonStorageBus;
        public ItemStack hoveredContentStack = ItemStack.EMPTY;
        public Set<Long> selectedStorageBusIds;
        public Map<Long, StorageBusInfo> storageBusMap;

        public static ClickContext from(int currentTab, StorageBusInfo hoveredStorageBus,
                int hoveredPartitionSlot, int hoveredContentSlot,
                StorageBusInfo clearButton, StorageBusInfo ioModeButton, StorageBusInfo partitionAllButton,
                ItemStack hoveredContentStack, Set<Long> selectedStorageBusIds, Map<Long, StorageBusInfo> storageBusMap) {
            ClickContext ctx = new ClickContext();
            ctx.currentTab = currentTab;
            ctx.hoveredStorageBus = hoveredStorageBus;
            ctx.hoveredStorageBusPartitionSlot = hoveredPartitionSlot;
            ctx.hoveredStorageBusContentSlot = hoveredContentSlot;
            ctx.hoveredClearButtonStorageBus = clearButton;
            ctx.hoveredIOModeButtonStorageBus = ioModeButton;
            ctx.hoveredPartitionAllButtonStorageBus = partitionAllButton;
            ctx.hoveredContentStack = hoveredContentStack;
            ctx.selectedStorageBusIds = selectedStorageBusIds;
            ctx.storageBusMap = storageBusMap;
            return ctx;
        }
    }

    /**
     * Handle click events on storage bus tabs.
     * @return true if the click was handled
     */
    public boolean handleClick(ClickContext ctx, int mouseButton) {
        long now = System.currentTimeMillis();
        boolean wasDoubleClick = checkDoubleClick(ctx.hoveredStorageBus, now, mouseButton);

        if (ctx.currentTab == TAB_STORAGE_BUS_INVENTORY) {
            return handleInventoryTabClick(ctx, mouseButton);
        }

        if (ctx.currentTab == TAB_STORAGE_BUS_PARTITION) {
            return handlePartitionTabClick(ctx, mouseButton, wasDoubleClick);
        }

        return false;
    }

    private boolean checkDoubleClick(StorageBusInfo hoveredBus, long now, int mouseButton) {
        if (hoveredBus == null || mouseButton != 0) return false;

        long currentBusId = hoveredBus.getId();

        if (currentBusId == lastClickedStorageBusId && now - lastClickTimeStorageBus < DOUBLE_CLICK_TIME) {
            CellTerminalNetwork.INSTANCE.sendToServer(
                new PacketHighlightBlock(hoveredBus.getPos(), hoveredBus.getDimension())
            );
            lastClickedStorageBusId = -1;
            lastClickTimeStorageBus = now;

            return true;
        }

        lastClickedStorageBusId = currentBusId;
        lastClickTimeStorageBus = now;

        return false;
    }

    private boolean handleInventoryTabClick(ClickContext ctx, int mouseButton) {
        if (mouseButton != 0) return false;

        // IO Mode button
        if (ctx.hoveredIOModeButtonStorageBus != null && ctx.hoveredIOModeButtonStorageBus.supportsIOMode()) {
            CellTerminalNetwork.INSTANCE.sendToServer(
                new PacketStorageBusIOMode(ctx.hoveredIOModeButtonStorageBus.getId())
            );

            return true;
        }

        // Partition All button
        if (ctx.hoveredPartitionAllButtonStorageBus != null) {
            CellTerminalNetwork.INSTANCE.sendToServer(
                new PacketStorageBusPartitionAction(
                    ctx.hoveredPartitionAllButtonStorageBus.getId(),
                    PacketStorageBusPartitionAction.Action.PARTITION_ALL
                )
            );

            return true;
        }

        // Content item click - toggle partition
        if (ctx.hoveredStorageBusContentSlot >= 0 && ctx.hoveredStorageBus != null
                && !ctx.hoveredContentStack.isEmpty()) {
            CellTerminalNetwork.INSTANCE.sendToServer(
                new PacketStorageBusPartitionAction(
                    ctx.hoveredStorageBus.getId(),
                    PacketStorageBusPartitionAction.Action.TOGGLE_ITEM,
                    -1,
                    ctx.hoveredContentStack
                )
            );

            return true;
        }

        return false;
    }

    private boolean handlePartitionTabClick(ClickContext ctx, int mouseButton, boolean wasDoubleClick) {
        if (mouseButton != 0) return false;

        // IO Mode button
        if (ctx.hoveredIOModeButtonStorageBus != null && ctx.hoveredIOModeButtonStorageBus.supportsIOMode()) {
            CellTerminalNetwork.INSTANCE.sendToServer(
                new PacketStorageBusIOMode(ctx.hoveredIOModeButtonStorageBus.getId())
            );

            return true;
        }

        // Clear button
        if (ctx.hoveredClearButtonStorageBus != null) {
            CellTerminalNetwork.INSTANCE.sendToServer(
                new PacketStorageBusPartitionAction(
                    ctx.hoveredClearButtonStorageBus.getId(),
                    PacketStorageBusPartitionAction.Action.CLEAR_ALL
                )
            );

            return true;
        }

        // Header click - toggle selection (multi-select for keybind actions)
        if (ctx.hoveredStorageBus != null && ctx.hoveredStorageBusPartitionSlot < 0 && !wasDoubleClick) {
            return handleSelectionToggle(ctx);
        }

        // Partition slot click
        if (ctx.hoveredStorageBusPartitionSlot >= 0 && ctx.hoveredStorageBus != null) {
            return handlePartitionSlotClick(ctx);
        }

        return false;
    }

    private boolean handleSelectionToggle(ClickContext ctx) {
        long busId = ctx.hoveredStorageBus.getId();

        if (ctx.selectedStorageBusIds.contains(busId)) {
            ctx.selectedStorageBusIds.remove(busId);

            return true;
        }

        // Validate that new selection is same type as existing selection
        if (!ctx.selectedStorageBusIds.isEmpty()) {
            StorageBusInfo existingBus = findExistingSelectedBus(ctx);

            if (existingBus != null) {
                boolean sameType = (ctx.hoveredStorageBus.isFluid() == existingBus.isFluid())
                    && (ctx.hoveredStorageBus.isEssentia() == existingBus.isEssentia());

                if (!sameType) {
                    Minecraft.getMinecraft().player.sendMessage(
                        new TextComponentTranslation("cellterminal.error.mixed_bus_selection"));

                    return true;
                }
            }
        }

        ctx.selectedStorageBusIds.add(busId);

        return true;
    }

    private StorageBusInfo findExistingSelectedBus(ClickContext ctx) {
        for (Long existingId : ctx.selectedStorageBusIds) {
            StorageBusInfo bus = ctx.storageBusMap.get(existingId);
            if (bus != null) return bus;
        }

        return null;
    }

    private boolean handlePartitionSlotClick(ClickContext ctx) {
        ItemStack heldStack = Minecraft.getMinecraft().player.inventory.getItemStack();

        if (!heldStack.isEmpty()) {
            return handleAddPartitionItem(ctx, heldStack);
        }

        // Left click without item: clear slot
        CellTerminalNetwork.INSTANCE.sendToServer(
            new PacketStorageBusPartitionAction(
                ctx.hoveredStorageBus.getId(),
                PacketStorageBusPartitionAction.Action.REMOVE_ITEM,
                ctx.hoveredStorageBusPartitionSlot,
                ItemStack.EMPTY
            )
        );

        return true;
    }

    private boolean handleAddPartitionItem(ClickContext ctx, ItemStack heldStack) {
        ItemStack stackToSend = heldStack;

        if (ctx.hoveredStorageBus.isFluid()) {
            if (!(heldStack.getItem() instanceof FluidDummyItem)) {
                FluidStack fluid = net.minecraftforge.fluids.FluidUtil.getFluidContained(heldStack);
                if (fluid == null) {
                    Minecraft.getMinecraft().player.sendMessage(
                        new TextComponentTranslation("cellterminal.error.fluid_bus_item"));

                    return true;
                }
            }
        } else if (ctx.hoveredStorageBus.isEssentia()) {
            ItemStack essentiaRep = ThaumicEnergisticsIntegration.tryConvertEssentiaContainerToAspect(heldStack);
            if (essentiaRep.isEmpty()) {
                Minecraft.getMinecraft().player.sendMessage(
                    new TextComponentTranslation("cellterminal.error.essentia_bus_item"));

                return true;
            }
            stackToSend = essentiaRep;
        }

        CellTerminalNetwork.INSTANCE.sendToServer(
            new PacketStorageBusPartitionAction(
                ctx.hoveredStorageBus.getId(),
                PacketStorageBusPartitionAction.Action.ADD_ITEM,
                ctx.hoveredStorageBusPartitionSlot,
                stackToSend
            )
        );

        return true;
    }
}
