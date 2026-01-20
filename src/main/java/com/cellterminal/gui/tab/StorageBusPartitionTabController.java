package com.cellterminal.gui.tab;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import net.minecraftforge.fluids.FluidStack;

import appeng.fluids.items.FluidDummyItem;

import com.cellterminal.client.KeyBindings;
import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.gui.handler.QuickPartitionHandler;
import com.cellterminal.gui.overlay.MessageHelper;
import com.cellterminal.integration.ThaumicEnergisticsIntegration;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketStorageBusPartitionAction;


/**
 * Tab controller for the Storage Bus Partition tab (Tab 4).
 * This tab displays storage buses with their partition configuration.
 * Supports multi-selection of buses and keybind to add items to selected buses.
 */
public class StorageBusPartitionTabController implements ITabController {

    public static final int TAB_INDEX = 4;

    @Override
    public int getTabIndex() {
        return TAB_INDEX;
    }

    @Override
    public SearchFilterMode getEffectiveSearchMode(SearchFilterMode userSelectedMode) {
        // Storage Bus Partition tab forces PARTITION mode
        return SearchFilterMode.PARTITION;
    }

    @Override
    public boolean showSearchModeButton() {
        // Storage bus tabs hide the search mode button
        return false;
    }

    @Override
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();

        lines.add(I18n.format("gui.cellterminal.controls.storage_bus_add_key",
            KeyBindings.ADD_TO_STORAGE_BUS.getDisplayName()));

        lines.add(I18n.format("gui.cellterminal.controls.storage_bus_capacity"));

        lines.add("");  // spacing line

        lines.add(I18n.format("gui.cellterminal.controls.jei_drag"));
        lines.add(I18n.format("gui.cellterminal.controls.click_to_remove"));
        lines.add(I18n.format("gui.cellterminal.controls.double_click_storage"));

        return lines;
    }

    @Override
    public boolean handleClick(TabClickContext context) {
        // Storage bus click handling is done in GuiCellTerminalBase.handleStorageBusTabClick
        return false;
    }

    @Override
    public boolean handleKeyTyped(int keyCode, TabContext context) {
        if (!KeyBindings.ADD_TO_STORAGE_BUS.isActiveAndMatches(keyCode)) return false;

        // This method is called from the GUI which has access to the selected bus IDs
        // We need to get them from the context
        // For now, this is handled in GuiCellTerminalBase since we need access to
        // selectedStorageBusIds which is GUI state

        return false;  // Let GUI handle this for now since it needs GUI-specific state
    }

    /**
     * Handle the add-to-storage-bus keybind.
     * Called from GuiCellTerminalBase with the actual selected bus IDs.
     * @param selectedBusIds The set of selected storage bus IDs
     * @param hoveredSlot The slot the mouse is over (or null)
     * @param storageBusMap Map of storage bus IDs to info
     * @return true if the keybind was handled
     */
    public static boolean handleAddToStorageBusKeybind(Set<Long> selectedBusIds,
                                                         Slot hoveredSlot,
                                                         Map<Long, StorageBusInfo> storageBusMap) {
        if (selectedBusIds.isEmpty()) {
            if (Minecraft.getMinecraft().player != null) {
                MessageHelper.warning("cellterminal.storage_bus.no_selection");
            }

            return true;
        }

        // Try to get item from inventory slot first
        ItemStack stack = ItemStack.EMPTY;

        if (hoveredSlot != null && hoveredSlot.getHasStack()) {
            stack = hoveredSlot.getStack();
        }

        // If no inventory item, try JEI/bookmark
        if (stack.isEmpty()) {
            QuickPartitionHandler.HoveredIngredient jeiItem = QuickPartitionHandler.getHoveredIngredient();
            if (jeiItem != null && !jeiItem.stack.isEmpty()) stack = jeiItem.stack;
        }

        if (stack.isEmpty()) {
            if (Minecraft.getMinecraft().player != null) {
                MessageHelper.warning("cellterminal.storage_bus.no_item");
            }

            return true;
        }

        // Add to all selected storage buses
        int successCount = 0;
        int invalidItemCount = 0;
        int noSlotCount = 0;

        for (Long busId : selectedBusIds) {
            StorageBusInfo storageBus = storageBusMap.get(busId);
            if (storageBus == null) continue;

            // Convert the item for non-item bus types first to check validity
            ItemStack stackToSend = stack;
            boolean validForBusType = true;

            if (storageBus.isFluid()) {
                // For fluid buses, need FluidDummyItem or fluid container
                if (!(stack.getItem() instanceof FluidDummyItem)) {
                    FluidStack fluid = net.minecraftforge.fluids.FluidUtil.getFluidContained(stack);
                    // Can't use this item on fluid bus
                    if (fluid == null) {
                        invalidItemCount++;
                        validForBusType = false;
                    }
                }
            } else if (storageBus.isEssentia()) {
                // For essentia buses, need ItemDummyAspect or essentia container
                ItemStack essentiaRep = ThaumicEnergisticsIntegration.tryConvertEssentiaContainerToAspect(stack);
                // Can't use this item on essentia bus
                if (essentiaRep.isEmpty()) {
                    invalidItemCount++;
                    validForBusType = false;
                } else {
                    stackToSend = essentiaRep;
                }
            }

            if (!validForBusType) continue;

            // Find first empty slot in this storage bus
            List<ItemStack> partition = storageBus.getPartition();
            int availableSlots = storageBus.getAvailableConfigSlots();
            int targetSlot = -1;

            for (int i = 0; i < availableSlots; i++) {
                if (i >= partition.size() || partition.get(i).isEmpty()) {
                    targetSlot = i;
                    break;
                }
            }

            if (targetSlot < 0) {
                noSlotCount++;
                continue;
            }

            CellTerminalNetwork.INSTANCE.sendToServer(
                new PacketStorageBusPartitionAction(
                    busId,
                    PacketStorageBusPartitionAction.Action.ADD_ITEM,
                    targetSlot,
                    stackToSend
                )
            );
            successCount++;
        }

        if (successCount == 0 && Minecraft.getMinecraft().player != null) {
            // Show appropriate error message based on what failed
            if (invalidItemCount > 0 && noSlotCount == 0) {
                MessageHelper.error("cellterminal.storage_bus.invalid_item");
            } else if (noSlotCount > 0 && invalidItemCount == 0) {
                MessageHelper.error("cellterminal.storage_bus.partition_full");
            } else {
                // Mixed: some were invalid, some were full
                MessageHelper.error("cellterminal.storage_bus.partition_full");
            }
        }

        return true;
    }

    @Override
    public boolean requiresServerPolling() {
        // Storage bus tabs require server polling
        return true;
    }

    @Override
    public List<Object> getLines(TabContext context) {
        return context.getStorageBusPartitionLines();
    }

    @Override
    public int getLineCount(TabContext context) {
        return context.getStorageBusPartitionLines().size();
    }
}
