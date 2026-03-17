package com.cellterminal.gui.widget.tab;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.AEApi;
import appeng.fluids.items.FluidDummyItem;

import mezz.jei.api.gui.IGhostIngredientHandler;

import com.cellterminal.client.CellContentRow;
import com.cellterminal.client.CellInfo;
import com.cellterminal.client.KeyBindings;
import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.client.TempCellInfo;
import com.cellterminal.config.CellTerminalServerConfig;
import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.handler.JeiGhostHandler;
import com.cellterminal.gui.handler.TerminalDataManager;
import com.cellterminal.gui.handler.QuickPartitionHandler;
import com.cellterminal.gui.overlay.MessageHelper;
import com.cellterminal.gui.widget.CardsDisplay;
import com.cellterminal.gui.widget.IWidget;
import com.cellterminal.gui.widget.button.ButtonType;
import com.cellterminal.gui.widget.button.SmallButton;
import com.cellterminal.gui.widget.header.AbstractHeader;
import com.cellterminal.gui.widget.header.TempAreaHeader;
import com.cellterminal.gui.widget.line.AbstractLine;
import com.cellterminal.gui.widget.line.ContinuationLine;
import com.cellterminal.gui.widget.line.SlotsLine;
import com.cellterminal.integration.ThaumicEnergisticsIntegration;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketExtractUpgrade;
import com.cellterminal.network.PacketTempCellAction;
import com.cellterminal.network.PacketTempCellPartitionAction;


/**
 * Tab widget for the Temp Area tab (Tab 3).
 * <p>
 * The temp area shows temporary cell storage slots. Each slot has a header
 * (with a cell drop zone and Send button) followed by interleaved content
 * and partition rows for the inserted cell.
 *
 * <h3>Line list structure</h3>
 * <pre>
 * TempCellInfo → TempAreaHeader (cell slot + Send button)
 * ├─ CellContentRow (content, first) → SlotsLine + DO_PARTITION button
 * ├─ CellContentRow (content, continuation) → ContinuationLine
 * ├─ CellContentRow (partition, first) → SlotsLine + CLEAR_PARTITION button
 * └─ CellContentRow (partition, continuation) → ContinuationLine
 * [next TempCellInfo...]
 * </pre>
 *
 * The temp area uses 9 slots per row (same as storage bus tabs) at a narrower
 * X offset since there is no inline cell slot in the content rows.
 */
public class TempAreaTabWidget extends AbstractTabWidget {

    /** Slots per row for temp area: 9 (matches storage bus layout) */
    private static final int SLOTS_PER_ROW = GuiConstants.STORAGE_BUS_SLOTS_PER_ROW;

    /** X offset for content/partition slots (no inline cell slot) */
    private static final int SLOTS_X_OFFSET = GuiConstants.CELL_INDENT + 4;

    public TempAreaTabWidget(FontRenderer fontRenderer, RenderItem itemRender) {
        super(fontRenderer, itemRender);
    }

    // ---- Tab controller methods ----

    @Override
    public List<Object> getLines(TerminalDataManager dataManager) {
        return dataManager.getTempAreaLines();
    }

    @Override
    public SearchFilterMode getEffectiveSearchMode(SearchFilterMode userSelectedMode) {
        // Temp area respects the user's selected search mode
        return userSelectedMode;
    }

    @Override
    public boolean showSearchModeButton() {
        return true;
    }

    @Override
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();

        lines.add(I18n.format("gui.cellterminal.controls.temp_area.drag_cell"));
        lines.add(I18n.format("gui.cellterminal.controls.temp_area.send_cell"));
        lines.add("");
        lines.add(I18n.format("gui.cellterminal.controls.temp_area.add_key",
            KeyBindings.ADD_TO_STORAGE_BUS.getDisplayName()));
        lines.add("");
        lines.add(I18n.format("gui.cellterminal.controls.jei_drag"));
        lines.add(I18n.format("gui.cellterminal.controls.click_to_remove"));

        return lines;
    }

    @Override
    public ItemStack getTabIcon() {
        return AEApi.instance().definitions().items().cell64k()
            .maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    public String getTabTooltip() {
        return I18n.format("gui.cellterminal.tab.temp_area.tooltip");
    }

    @Override
    public boolean handleTabKeyTyped(int keyCode) {
        if (!KeyBindings.ADD_TO_STORAGE_BUS.isActiveAndMatches(keyCode)) return false;

        return handleAddToTempCellKeybind(
            guiContext.getSelectedTempCellSlots(),
            guiContext.getSlotUnderMouse(),
            getLines(guiContext.getDataManager()));
    }

    // ---- JEI ghost targets ----

    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        List<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();

        for (Map.Entry<IWidget, Object> entry : getWidgetDataMap().entrySet()) {
            IWidget widget = entry.getKey();
            Object data = entry.getValue();
            if (!(widget instanceof SlotsLine)) continue;

            SlotsLine slotsLine = (SlotsLine) widget;
            List<SlotsLine.PartitionSlotTarget> slotTargets = slotsLine.getPartitionTargets();
            if (slotTargets.isEmpty()) continue;
            if (!(data instanceof CellContentRow)) continue;

            CellInfo cell = ((CellContentRow) data).getCell();
            int tempSlotIndex = findTempSlotIndexForCell(cell);
            if (tempSlotIndex < 0) continue;

            for (SlotsLine.PartitionSlotTarget slot : slotTargets) {
                targets.add(new IGhostIngredientHandler.Target<Object>() {
                    @Override
                    public Rectangle getArea() {
                        return new Rectangle(slot.absX, slot.absY, slot.width, slot.height);
                    }

                    @Override
                    public void accept(Object ing) {
                        ItemStack stack = JeiGhostHandler.convertJeiIngredientToItemStack(
                            ing, cell.getStorageType());
                        if (!stack.isEmpty()) {
                            guiContext.sendPacket(new PacketTempCellPartitionAction(
                                tempSlotIndex,
                                PacketTempCellPartitionAction.Action.ADD_ITEM,
                                slot.absoluteIndex, stack));
                        }
                    }
                });
            }
        }

        return targets;
    }

    // ---- Row building ----

    @Override
    protected IWidget createRowWidget(Object lineData, int y, List<?> allLines, int lineIndex) {
        if (lineData instanceof TempCellInfo) {
            return createTempAreaHeader((TempCellInfo) lineData, y);
        }

        if (lineData instanceof CellContentRow) {
            return createContentLine((CellContentRow) lineData, y);
        }

        return null;
    }

    @Override
    protected boolean isContentLine(List<?> allLines, int index) {
        if (index < 0 || index >= allLines.size()) return false;

        return allLines.get(index) instanceof CellContentRow;
    }

    /**
     * Check if a line at the given index is a partition row (vs content row).
     */
    private boolean isPartitionRow(List<?> allLines, int index) {
        if (index < 0 || index >= allLines.size()) return false;

        Object line = allLines.get(index);
        if (line instanceof CellContentRow) return ((CellContentRow) line).isPartitionRow();

        return false;
    }

    /**
     * Check if there's a non-partition content row below the given index.
     * Used to determine whether to draw the header connector.
     */
    private boolean hasNonPartitionContentBelow(List<?> allLines, int headerIndex) {
        int nextIndex = headerIndex + 1;
        if (nextIndex >= allLines.size()) return false;

        Object next = allLines.get(nextIndex);
        if (next instanceof CellContentRow) {
            return !((CellContentRow) next).isPartitionRow();
        }

        return false;
    }

    @Override
    protected void propagateTreeLines(List<?> allLines, int scrollOffset) {
        // Track the "cut Y" from the previous row - separate for content and partition sections
        int lastContentCutY = GuiConstants.CONTENT_START_Y;
        int lastPartitionCutY = GuiConstants.CONTENT_START_Y;
        boolean hasContentAbove = scrollOffset > 0 && isContentLine(allLines, scrollOffset - 1);

        for (int i = 0; i < visibleRows.size(); i++) {
            IWidget widget = visibleRows.get(i);
            int lineIndex = scrollOffset + i;

            if (widget instanceof AbstractHeader) {
                AbstractHeader header = (AbstractHeader) widget;
                // Only draw connector if non-partition content rows follow below
                // The vertical line connects header to content, NOT to partition
                boolean hasContentBelow = hasNonPartitionContentBelow(allLines, lineIndex);
                header.setDrawConnector(hasContentBelow);
                lastContentCutY = header.getConnectorY();
                // Reset partition cut Y for each new header (new temp cell)
                lastPartitionCutY = GuiConstants.CONTENT_START_Y;

            } else if (widget instanceof AbstractLine) {
                AbstractLine line = (AbstractLine) widget;
                boolean currentIsPartition = isPartitionRow(allLines, lineIndex);
                boolean prevIsPartition = lineIndex > 0 && isPartitionRow(allLines, lineIndex - 1);

                if (currentIsPartition) {
                    if (!prevIsPartition) {
                        // First partition row after content: draw horizontal line and button, but NO vertical line
                        // Set lineAboveCutY to row's own junction so vertical line has zero length
                        line.setTreeLineParams(true, line.getY() + 5);
                    } else {
                        // Continuation partition row: draw tree line connecting to previous partition
                        line.setTreeLineParams(true, lastPartitionCutY);
                    }
                } else if (i == 0 && hasContentAbove) {
                    // First visible row with content above
                    line.setTreeLineParams(true, GuiConstants.CONTENT_START_Y);
                } else {
                    line.setTreeLineParams(true, lastContentCutY);
                }

                lastPartitionCutY = line.getTreeLineCutY();
            }
        }

        // Draw a bottom continuation line if there is more content of the SAME TYPE
        // below the visible window. Don't draw between content→partition transitions,
        // as they are separate tree branches.
        int lastVisibleIndex = scrollOffset + visibleRows.size() - 1;
        boolean nextIsContent = lastVisibleIndex + 1 < allLines.size()
            && isContentLine(allLines, lastVisibleIndex + 1);

        if (nextIsContent) {
            boolean lastIsPartition = isPartitionRow(allLines, lastVisibleIndex);
            boolean nextIsPartition = isPartitionRow(allLines, lastVisibleIndex + 1);

            // Only continue the line if both rows are the same type (both content or both partition)
            if (lastIsPartition == nextIsPartition) {
                bottomContinuationFromY = lastIsPartition ? lastPartitionCutY : lastContentCutY;
            } else {
                bottomContinuationFromY = -1;
            }
        } else {
            bottomContinuationFromY = -1;
        }
    }

    // ---- TempAreaHeader creation ----

    private TempAreaHeader createTempAreaHeader(TempCellInfo tempCell, int y) {
        TempAreaHeader header = new TempAreaHeader(y, fontRenderer, itemRender);
        header.setIconSupplier(tempCell::getCellStack);
        header.setHasCellSupplier(tempCell::hasCell);

        // Name comes from the cell info when present
        CellInfo cellInfo = tempCell.getCellInfo();
        if (cellInfo != null) {
            header.setNameSupplier(cellInfo::getDisplayName);
            header.setHasCustomNameSupplier(cellInfo::hasCustomName);

            // Upgrade cards
            CardsDisplay cards = createCellCardsDisplay(cellInfo, y, this::handleCardClick);
            if (cards != null) header.setCardsDisplay(cards);
        }

        // Cell slot click (insert/extract/swap cell)
        header.setCellSlotCallback(button -> {
            if (button != 0) return;

            ItemStack heldStack = guiContext.getHeldStack();
            if (tempCell.isEmpty() && !heldStack.isEmpty()) {
                // Empty slot + holding cell = insert
                guiContext.sendPacket(new PacketTempCellAction(
                    PacketTempCellAction.Action.INSERT, tempCell.getTempSlotIndex()));
            } else if (!tempCell.isEmpty() && heldStack.isEmpty()) {
                // Occupied slot + empty hand = extract
                boolean toInventory = guiContext.isShiftDown();
                guiContext.sendPacket(new PacketTempCellAction(
                    PacketTempCellAction.Action.EXTRACT, tempCell.getTempSlotIndex(), toInventory));
            } else if (!tempCell.isEmpty() && !heldStack.isEmpty()) {
                // Occupied slot + holding cell = swap
                guiContext.sendPacket(new PacketTempCellAction(
                    PacketTempCellAction.Action.SWAP, tempCell.getTempSlotIndex()));
            }
        });

        // Send button
        header.setOnSendClick(() ->
            guiContext.sendPacket(new PacketTempCellAction(
                PacketTempCellAction.Action.SEND, tempCell.getTempSlotIndex())));

        // Name click (rename): header handles right-click directly via InlineRenameManager
        // yOffset = 4 so field background (editingY + 1) aligns with name at y + 5
        if (cellInfo != null) {
            header.setRenameInfo(cellInfo, GuiConstants.HEADER_NAME_X - 2, 4,
                TempAreaHeader.SEND_BUTTON_X - 4);
        }

        // Header selection for quick-add
        Set<Integer> selectedSlots = guiContext.getSelectedTempCellSlots();
        header.setOnHeaderClick(() -> {
            if (!tempCell.hasCell()) return;

            int slotIndex = tempCell.getTempSlotIndex();
            if (selectedSlots.contains(slotIndex)) {
                selectedSlots.remove(slotIndex);
            } else {
                // Validate same type as existing selection
                if (!selectedSlots.isEmpty()) {
                    TempCellInfo existingTempCell = findExistingSelectedTempCell(selectedSlots);
                    if (existingTempCell != null && existingTempCell.getCellInfo() != null) {
                        CellInfo existingCell = existingTempCell.getCellInfo();
                        CellInfo newCell = tempCell.getCellInfo();
                        if (newCell != null) {
                            boolean sameType = newCell.getStorageType() == existingCell.getStorageType();
                            if (!sameType) {
                                guiContext.showError("gui.cellterminal.temp_area.mixed_cell_selection");
                                return;
                            }
                        }
                    }
                }

                selectedSlots.add(slotIndex);
            }
        });
        header.setSelectedSupplier(() ->
            selectedSlots.contains(tempCell.getTempSlotIndex()));

        return header;
    }

    // ---- Content line creation ----

    private IWidget createContentLine(CellContentRow row, int y) {
        CellInfo cell = row.getCell();
        boolean isPartition = row.isPartitionRow();
        SlotsLine.SlotMode mode = isPartition ? SlotsLine.SlotMode.PARTITION : SlotsLine.SlotMode.CONTENT;

        if (row.isFirstRow()) {
            return createFirstContentRow(cell, row.getStartIndex(), mode, isPartition, y);
        }

        return createContinuationRow(cell, row.getStartIndex(), mode, y);
    }

    /**
     * First content or partition row: SlotsLine with tree junction button.
     * Content first row gets DO_PARTITION, partition first row gets CLEAR_PARTITION.
     */
    private SlotsLine createFirstContentRow(CellInfo cell, int startIndex,
                                             SlotsLine.SlotMode mode, boolean isPartition, int y) {
        SlotsLine line = new SlotsLine(
            y, SLOTS_PER_ROW, SLOTS_X_OFFSET, mode,
            startIndex, fontRenderer, itemRender
        );

        int tempSlotIndex = findTempSlotIndexForCell(cell);

        configureSlotData(line, cell, mode, tempSlotIndex);

        // Tree junction button
        ButtonType buttonType = isPartition ? ButtonType.CLEAR_PARTITION : ButtonType.DO_PARTITION;
        SmallButton treeBtn = new SmallButton(0, 0, buttonType, () -> {
            if (tempSlotIndex < 0) return;

            if (isPartition) {
                guiContext.sendPacket(new PacketTempCellPartitionAction(
                    tempSlotIndex, PacketTempCellPartitionAction.Action.CLEAR_ALL));
            } else {
                guiContext.sendPacket(new PacketTempCellPartitionAction(
                    tempSlotIndex, PacketTempCellPartitionAction.Action.SET_ALL_FROM_CONTENTS));
            }
        });
        line.setTreeButton(treeBtn);

        line.setGuiOffsets(guiLeft, guiTop);

        // Selection highlight
        if (tempSlotIndex >= 0) {
            Set<Integer> selectedSlots = guiContext.getSelectedTempCellSlots();
            line.setSelectedSupplier(() -> selectedSlots.contains(tempSlotIndex));
        }

        return line;
    }

    /**
     * Continuation row (not the first for this content/partition section).
     */
    private ContinuationLine createContinuationRow(CellInfo cell, int startIndex,
                                                    SlotsLine.SlotMode mode, int y) {
        ContinuationLine line = new ContinuationLine(
            y, SLOTS_PER_ROW, SLOTS_X_OFFSET, mode,
            startIndex, fontRenderer, itemRender
        );

        int tempSlotIndex = findTempSlotIndexForCell(cell);
        configureSlotData(line, cell, mode, tempSlotIndex);
        line.setGuiOffsets(guiLeft, guiTop);

        // Selection highlight
        if (tempSlotIndex >= 0) {
            Set<Integer> selectedSlots = guiContext.getSelectedTempCellSlots();
            line.setSelectedSupplier(() -> selectedSlots.contains(tempSlotIndex));
        }

        return line;
    }

    /**
     * Configure content/partition data suppliers on a SlotsLine.
     */
    private void configureSlotData(SlotsLine line, CellInfo cell, SlotsLine.SlotMode mode, int tempSlotIndex) {
        if (mode == SlotsLine.SlotMode.CONTENT) {
            line.setItemsSupplier(cell::getContents);
            line.setPartitionSupplier(cell::getPartition);
            line.setCountProvider(() -> cell::getContentCount);
        } else {
            line.setItemsSupplier(cell::getPartition);
            line.setMaxSlots((int) cell.getTotalTypes());
        }

        line.setSlotClickCallback((slotIndex, mouseButton) -> {
            if (mouseButton != 0 || tempSlotIndex < 0) return;

            if (mode == SlotsLine.SlotMode.CONTENT) {
                // Content slot: toggle partition for that item
                List<ItemStack> contents = cell.getContents();
                if (slotIndex < contents.size() && !contents.get(slotIndex).isEmpty()) {
                    guiContext.sendPacket(new PacketTempCellPartitionAction(
                        tempSlotIndex, PacketTempCellPartitionAction.Action.TOGGLE_ITEM,
                        contents.get(slotIndex)));
                }
            } else {
                // Partition slot: add/remove item from partition
                ItemStack heldStack = guiContext.getHeldStack();
                List<ItemStack> partition = cell.getPartition();
                boolean slotOccupied = slotIndex < partition.size() && !partition.get(slotIndex).isEmpty();

                if (!heldStack.isEmpty()) {
                    guiContext.sendPacket(new PacketTempCellPartitionAction(
                        tempSlotIndex, PacketTempCellPartitionAction.Action.ADD_ITEM,
                        slotIndex, heldStack));
                } else if (slotOccupied) {
                    guiContext.sendPacket(new PacketTempCellPartitionAction(
                        tempSlotIndex, PacketTempCellPartitionAction.Action.REMOVE_ITEM, slotIndex));
                }
            }
        });
    }

    // ---- Upgrade card click handling ----

    private void handleCardClick(CellInfo cell, int upgradeSlotIndex) {
        if (CellTerminalServerConfig.isInitialized()
                && !CellTerminalServerConfig.getInstance().isUpgradeExtractEnabled()) {
            guiContext.showError("cellterminal.error.upgrade_extract_disabled");
            return;
        }

        int tempSlotIndex = findTempSlotIndexForCell(cell);
        if (tempSlotIndex < 0) return;

        boolean toInventory = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        guiContext.sendPacket(PacketExtractUpgrade.forTempCell(tempSlotIndex, upgradeSlotIndex, toInventory));
    }

    // ---- Keybind handling ----

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
    private static boolean handleAddToTempCellKeybind(Set<Integer> selectedTempCellSlots,
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

    // ---- Upgrade support ----

    @Override
    public boolean handleUpgradeClick(Object hoveredData, ItemStack heldStack, boolean isShiftClick) {
        // Handle TempCellInfo (header) - specific temp cell slot
        if (hoveredData instanceof TempCellInfo) {
            TempCellInfo tempCell = (TempCellInfo) hoveredData;
            CellInfo cellInfo = tempCell.getCellInfo();
            if (cellInfo == null || !cellInfo.canAcceptUpgrade(heldStack)) return false;

            guiContext.sendPacket(new PacketTempCellAction(
                PacketTempCellAction.Action.UPGRADE, tempCell.getTempSlotIndex()));

            return true;
        }

        // Handle CellContentRow (content/partition row) - find parent temp cell
        if (hoveredData instanceof CellContentRow) {
            CellInfo cell = ((CellContentRow) hoveredData).getCell();
            if (cell == null || !cell.canAcceptUpgrade(heldStack)) return false;

            int tempSlotIndex = findTempSlotIndexForCell(cell);
            if (tempSlotIndex < 0) return false;

            guiContext.sendPacket(new PacketTempCellAction(
                PacketTempCellAction.Action.UPGRADE, tempSlotIndex));

            return true;
        }

        return false;
    }

    @Override
    public boolean handleShiftUpgradeClick(ItemStack heldStack) {
        // Shift-click with upgrade: server finds first temp cell that can accept
        guiContext.sendPacket(new PacketTempCellAction(
            PacketTempCellAction.Action.UPGRADE, -1, true));

        return true;
    }

    @Override
    public boolean handleInventorySlotShiftClick(ItemStack upgradeStack, int sourceSlotIndex) {
        // Delegate entirely to the server: send tempSlotIndex=-1 so the server
        // iterates all temp cells and finds the first one that can actually accept
        // the upgrade, avoiding client-side mispredictions.
        guiContext.sendPacket(new PacketTempCellAction(
            PacketTempCellAction.Action.UPGRADE, -1, sourceSlotIndex));

        return true;
    }

    // ---- Helpers ----

    /**
     * Find the temp slot index for a given cell by searching through temp area lines.
     */
    private int findTempSlotIndexForCell(CellInfo cell) {
        for (Object line : getLines(guiContext.getDataManager())) {
            if (line instanceof TempCellInfo) {
                TempCellInfo tempCell = (TempCellInfo) line;
                if (tempCell.getCellInfo() == cell) return tempCell.getTempSlotIndex();
            }
        }

        return -1;
    }

    /**
     * Find an existing selected temp cell from the selected temp cell slots set.
     */
    private TempCellInfo findExistingSelectedTempCell(Set<Integer> selectedSlots) {
        for (Integer slotIndex : selectedSlots) {
            for (Object line : getLines(guiContext.getDataManager())) {
                if (line instanceof TempCellInfo) {
                    TempCellInfo tempCell = (TempCellInfo) line;
                    if (tempCell.getTempSlotIndex() == slotIndex) return tempCell;
                }
            }
        }

        return null;
    }
}
