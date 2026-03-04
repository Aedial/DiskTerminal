package com.cellterminal.gui.tab;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import net.minecraftforge.fluids.FluidStack;

import appeng.fluids.items.FluidDummyItem;

import com.cellterminal.client.CellInfo;
import com.cellterminal.client.KeyBindings;
import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.client.TempCellInfo;
import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.handler.QuickPartitionHandler;
import com.cellterminal.gui.overlay.MessageHelper;
import com.cellterminal.integration.ThaumicEnergisticsIntegration;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketTempCellPartitionAction;


/**
 * Tab controller for the Temp Area tab (Tab 3).
 * This tab provides a temporary staging area for cells where users can:
 * - Place cells from their inventory
 * - View cell contents (like Inventory tab)
 * - Edit cell partitions (like Partition tab)
 * - Send cells to the first available slot in the network
 * <p>
 * Uses selection-based partitioning like StorageBusPartitionTabController:
 * select cell(s) by clicking header, then press keybind to add hovered item.
 */
public class TempAreaTabController implements ITabController {

    @Override
    public int getTabIndex() {
        return GuiConstants.TAB_TEMP_AREA;
    }

    @Override
    public SearchFilterMode getEffectiveSearchMode(SearchFilterMode userSelectedMode) {
        // Temp area respects the user's selected search mode
        return userSelectedMode;
    }

    @Override
    public boolean showSearchModeButton() {
        // Show search mode button since we support both inventory and partition views
        return true;
    }

    @Override
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();

        lines.add(I18n.format("gui.cellterminal.controls.temp_area.drag_cell"));
        lines.add(I18n.format("gui.cellterminal.controls.temp_area.send_cell"));

        lines.add("");

        // Use ADD_TO_STORAGE_BUS keybind like storage bus partition tab
        lines.add(I18n.format("gui.cellterminal.controls.temp_area.add_key",
            KeyBindings.ADD_TO_STORAGE_BUS.getDisplayName()));

        lines.add("");

        lines.add(I18n.format("gui.cellterminal.controls.jei_drag"));
        lines.add(I18n.format("gui.cellterminal.controls.click_to_remove"));

        return lines;
    }

    @Override
    public boolean handleClick(TabClickContext context) {
        // Click handling for temp area is done in GuiCellTerminalBase
        // - Click on cell slot to insert/extract cell
        // - Click on Send button to send cell to network
        // - Click on partition slots to edit partition
        return false;
    }

    @Override
    public boolean handleKeyTyped(int keyCode, TabContext context) {
        return false;
    }

    @Override
    public boolean requiresServerPolling() {
        // Temp area doesn't need server polling - data is managed locally
        return false;
    }

    @Override
    public List<Object> getLines(TabContext context) {
        return context.getTempAreaLines();
    }

    @Override
    public int getLineCount(TabContext context) {
        return context.getTempAreaLines().size();
    }

    /**
     * Handle the add-to-temp-cell keybind (same key as ADD_TO_STORAGE_BUS).
     * Adds the hovered item to all selected temp cells' partitions.
     * Matches storage bus behavior: converts items for fluid/essentia cells, finds empty slots.
     *
     * @param selectedTempCellSlots Set of selected temp cell slot indexes
     * @param hoveredSlot The slot the mouse is over (or null)
     * @param tempAreaLines List of temp area line objects
     * @return true if the keybind was handled
     */
    public static boolean handleAddToTempCellKeybind(Set<Integer> selectedTempCellSlots,
                                                      Slot hoveredSlot,
                                                      List<Object> tempAreaLines) {
        if (selectedTempCellSlots.isEmpty()) {
            if (Minecraft.getMinecraft().player != null) {
                MessageHelper.warning("gui.cellterminal.temp_area.no_selection");
            }

            return true;
        }

        // Get the item to add
        ItemStack stack = ItemStack.EMPTY;
        if (hoveredSlot != null && hoveredSlot.getHasStack()) stack = hoveredSlot.getStack();

        // Try JEI/bookmark if no inventory item
        if (stack.isEmpty()) {
            QuickPartitionHandler.HoveredIngredient jeiItem = QuickPartitionHandler.getHoveredIngredient();
            if (jeiItem != null && !jeiItem.stack.isEmpty()) stack = jeiItem.stack;
        }

        if (stack.isEmpty()) {
            if (Minecraft.getMinecraft().player != null) {
                MessageHelper.warning("gui.cellterminal.temp_area.no_item");
            }

            return true;
        }

        // Add to all selected temp cells
        int successCount = 0;
        int invalidItemCount = 0;
        int noSlotCount = 0;

        for (Integer tempSlotIndex : selectedTempCellSlots) {
            // Find the TempCellInfo for this slot
            TempCellInfo tempCell = findTempCellBySlot(tempAreaLines, tempSlotIndex);
            if (tempCell == null || tempCell.getCellInfo() == null) continue;

            CellInfo cellInfo = tempCell.getCellInfo();

            // Convert the item for non-item cell types first to check validity
            ItemStack stackToSend = stack;
            boolean validForCellType = true;

            if (cellInfo.isFluid()) {
                // For fluid cells, need FluidDummyItem or fluid container
                if (!(stack.getItem() instanceof FluidDummyItem)) {
                    FluidStack fluid = net.minecraftforge.fluids.FluidUtil.getFluidContained(stack);
                    // Can't use this item on fluid cell
                    if (fluid == null) {
                        invalidItemCount++;
                        validForCellType = false;
                    }
                }
            } else if (cellInfo.isEssentia()) {
                // For essentia cells, need ItemDummyAspect or essentia container
                ItemStack essentiaRep = ThaumicEnergisticsIntegration.tryConvertEssentiaContainerToAspect(stack);
                // Can't use this item on essentia cell
                if (essentiaRep.isEmpty()) {
                    invalidItemCount++;
                    validForCellType = false;
                } else {
                    stackToSend = essentiaRep;
                }
            }

            if (!validForCellType) continue;

            // Find first empty slot in this cell's partition
            // For cells, use totalTypes as the maximum config slots (always 63 for standard cells)
            List<ItemStack> partition = cellInfo.getPartition();
            int availableSlots = (int) cellInfo.getTotalTypes();
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

            // Send packet to add item to this temp cell's partition at specific slot
            CellTerminalNetwork.INSTANCE.sendToServer(
                new PacketTempCellPartitionAction(
                    tempSlotIndex,
                    PacketTempCellPartitionAction.Action.ADD_ITEM,
                    targetSlot,
                    stackToSend
                )
            );
            successCount++;
        }

        if (successCount == 0 && Minecraft.getMinecraft().player != null) {
            // Show appropriate error message based on what failed
            if (invalidItemCount > 0 && noSlotCount == 0) {
                MessageHelper.error("gui.cellterminal.temp_area.invalid_item");
            } else if (noSlotCount > 0 && invalidItemCount == 0) {
                MessageHelper.error("gui.cellterminal.temp_area.partition_full");
            } else {
                // Mixed or other failure
                MessageHelper.error("gui.cellterminal.temp_area.add_failed");
            }
        }

        return true;
    }

    /**
     * Find TempCellInfo for a given slot index in the temp area lines.
     */
    private static TempCellInfo findTempCellBySlot(List<Object> lines, int slotIndex) {
        for (Object line : lines) {
            if (line instanceof TempCellInfo) {
                TempCellInfo tempCell = (TempCellInfo) line;
                if (tempCell.getTempSlotIndex() == slotIndex) return tempCell;
            }
        }

        return null;
    }
}
