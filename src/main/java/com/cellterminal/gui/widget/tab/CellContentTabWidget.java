package com.cellterminal.gui.widget.tab;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;

import mezz.jei.api.gui.IGhostIngredientHandler;

import com.cellterminal.client.CellContentRow;
import com.cellterminal.client.CellInfo;
import com.cellterminal.client.EmptySlotInfo;
import com.cellterminal.client.KeyBindings;
import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.client.StorageInfo;
import com.cellterminal.client.TabStateManager;
import com.cellterminal.config.CellTerminalServerConfig;
import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.handler.JeiGhostHandler;
import com.cellterminal.gui.handler.QuickPartitionHandler;
import com.cellterminal.gui.handler.TerminalDataManager;
import com.cellterminal.gui.rename.Renameable;
import com.cellterminal.gui.widget.CardsDisplay;
import com.cellterminal.gui.widget.IWidget;
import com.cellterminal.gui.widget.button.ButtonType;
import com.cellterminal.gui.widget.button.SmallButton;
import com.cellterminal.gui.widget.header.StorageHeader;
import com.cellterminal.gui.widget.line.CellSlotsLine;
import com.cellterminal.gui.widget.line.ContinuationLine;
import com.cellterminal.gui.widget.line.SlotsLine;
import com.cellterminal.integration.ThaumicEnergisticsIntegration;
import com.cellterminal.network.PacketExtractUpgrade;
import com.cellterminal.network.PacketInsertCell;
import com.cellterminal.network.PacketPartitionAction;
import com.cellterminal.network.PacketPickupCell;


/**
 * Tab widget for the Inventory (Tab 1) and Partition (Tab 2) tabs.
 *
 * Both tabs display the same structure (StorageInfo headers + cell content rows)
 * but differ in the slot mode (CONTENT vs PARTITION) and tree button type:
 * <ul>
 *   <li><b>Inventory tab:</b> Shows cell contents with counts. Tree button is
 *       DO_PARTITION (adds item to partition). Content slots show "P" indicator
 *       for items that are in the partition.</li>
 *   <li><b>Partition tab:</b> Shows cell partitions with amber tint. Tree button is
 *       CLEAR_PARTITION (removes partition entry). Supports JEI ghost drops.</li>
 * </ul>
 *
 * Each row in the line list is one of:
 * <ul>
 *   <li>{@link StorageInfo} → {@link StorageHeader}</li>
 *   <li>{@link CellContentRow} (first row) → {@link CellSlotsLine} (with cell slot + cards)</li>
 *   <li>{@link CellContentRow} (continuation) → {@link ContinuationLine}</li>
 *   <li>{@link EmptySlotInfo} → {@link CellSlotsLine} (empty cell slot placeholder)</li>
 * </ul>
 */
public class CellContentTabWidget extends AbstractTabWidget {

    /** Slots per row for cell content: 8 */
    private static final int SLOTS_PER_ROW = GuiConstants.CELL_SLOTS_PER_ROW;

    /**
     * X offset where content/partition slots begin.
     * After cell slot (16px) + cards area + gap.
     */
    private static final int SLOTS_X_OFFSET = GuiConstants.CELL_INDENT + 20;

    private final SlotsLine.SlotMode slotMode;
    private final ButtonType treeButtonType;
    private final boolean isPartitionMode;

    /**
     * @param slotMode CONTENT for Inventory tab, PARTITION for Partition tab
     */
    public CellContentTabWidget(SlotsLine.SlotMode slotMode,
                                 FontRenderer fontRenderer, RenderItem itemRender) {
        super(fontRenderer, itemRender);
        this.slotMode = slotMode;
        this.isPartitionMode = (slotMode == SlotsLine.SlotMode.PARTITION);
        this.treeButtonType = isPartitionMode ? ButtonType.CLEAR_PARTITION : ButtonType.DO_PARTITION;
    }

    // ---- Tab controller methods ----

    @Override
    public List<Object> getLines(TerminalDataManager dataManager) {
        return isPartitionMode ? dataManager.getPartitionLines() : dataManager.getInventoryLines();
    }

    @Override
    public SearchFilterMode getEffectiveSearchMode(SearchFilterMode userSelectedMode) {
        return isPartitionMode ? SearchFilterMode.PARTITION : SearchFilterMode.INVENTORY;
    }

    @Override
    public boolean showSearchModeButton() {
        return false;
    }

    @Override
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();

        if (isPartitionMode) {
            lines.add(I18n.format("gui.cellterminal.controls.keybind_targets"));
            lines.add("");

            String notSet = I18n.format("gui.cellterminal.controls.key_not_set");

            String autoKey = KeyBindings.QUICK_PARTITION_AUTO.isBound()
                ? KeyBindings.QUICK_PARTITION_AUTO.getDisplayName() : notSet;
            lines.add(I18n.format("gui.cellterminal.controls.key_auto", autoKey));
            if (!autoKey.equals(notSet)) {
                lines.add(I18n.format("gui.cellterminal.controls.auto_warning"));
            }
            lines.add("");

            String itemKey = KeyBindings.QUICK_PARTITION_ITEM.isBound()
                ? KeyBindings.QUICK_PARTITION_ITEM.getDisplayName() : notSet;
            lines.add(I18n.format("gui.cellterminal.controls.key_item", itemKey));

            String fluidKey = KeyBindings.QUICK_PARTITION_FLUID.isBound()
                ? KeyBindings.QUICK_PARTITION_FLUID.getDisplayName() : notSet;
            lines.add(I18n.format("gui.cellterminal.controls.key_fluid", fluidKey));

            String essentiaKey = KeyBindings.QUICK_PARTITION_ESSENTIA.isBound()
                ? KeyBindings.QUICK_PARTITION_ESSENTIA.getDisplayName() : notSet;
            lines.add(I18n.format("gui.cellterminal.controls.key_essentia", essentiaKey));

            if (!essentiaKey.equals(notSet) && !ThaumicEnergisticsIntegration.isModLoaded()) {
                lines.add(I18n.format("gui.cellterminal.controls.essentia_warning"));
            }

            lines.add("");
            lines.add(I18n.format("gui.cellterminal.controls.jei_drag"));
            lines.add(I18n.format("gui.cellterminal.controls.click_to_remove"));
        } else {
            lines.add(I18n.format("gui.cellterminal.controls.partition_indicator"));
            lines.add(I18n.format("gui.cellterminal.controls.click_partition_toggle"));
        }

        lines.add(I18n.format("gui.cellterminal.controls.double_click_storage"));
        lines.add(I18n.format("gui.cellterminal.right_click_rename"));

        return lines;
    }

    @Override
    public ItemStack getTabIcon() {
        if (isPartitionMode) {
            return AEApi.instance().definitions().blocks().cellWorkbench()
                .maybeStack(1).orElse(ItemStack.EMPTY);
        }

        return AEApi.instance().definitions().blocks().chest()
            .maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    public String getTabTooltip() {
        if (isPartitionMode) {
            return I18n.format("gui.cellterminal.tab.partition.tooltip");
        }

        return I18n.format("gui.cellterminal.tab.inventory.tooltip");
    }

    @Override
    public boolean handleTabKeyTyped(int keyCode) {
        if (!isPartitionMode) return false;

        QuickPartitionHandler.PartitionType type = null;

        if (KeyBindings.QUICK_PARTITION_AUTO.isActiveAndMatches(keyCode)) {
            type = QuickPartitionHandler.PartitionType.AUTO;
        } else if (KeyBindings.QUICK_PARTITION_ITEM.isActiveAndMatches(keyCode)) {
            type = QuickPartitionHandler.PartitionType.ITEM;
        } else if (KeyBindings.QUICK_PARTITION_FLUID.isActiveAndMatches(keyCode)) {
            type = QuickPartitionHandler.PartitionType.FLUID;
        } else if (KeyBindings.QUICK_PARTITION_ESSENTIA.isActiveAndMatches(keyCode)) {
            type = QuickPartitionHandler.PartitionType.ESSENTIA;
        }

        if (type == null) return false;

        QuickPartitionHandler.QuickPartitionResult result = QuickPartitionHandler.attemptQuickPartition(
            type, getLines(guiContext.getDataManager()), guiContext.getDataManager().getStorageMap());

        if (result.success) {
            guiContext.showSuccess(result.message);
            if (result.scrollToLine >= 0) guiContext.scrollToLine(result.scrollToLine);
        } else {
            guiContext.showError(result.message);
        }

        return true;
    }

    // ---- Rename support ----

    @Override
    protected Renameable resolveRenameable(Object data, int relMouseX) {
        if (data instanceof StorageInfo && relMouseX < GuiConstants.BUTTON_PARTITION_X) {
            return (StorageInfo) data;
        }

        // On both inventory and partition tabs, cells are renameable (no right-side button exclusion)
        if (data instanceof CellInfo) return (CellInfo) data;

        return null;
    }

    // ---- JEI ghost targets ----

    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        if (!isPartitionMode) return Collections.emptyList();

        List<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();
        for (java.util.Map.Entry<IWidget, Object> entry : getWidgetDataMap().entrySet()) {
            IWidget widget = entry.getKey();
            Object data = entry.getValue();
            if (!(widget instanceof SlotsLine)) continue;

            SlotsLine slotsLine = (SlotsLine) widget;
            List<SlotsLine.PartitionSlotTarget> slotTargets = slotsLine.getPartitionTargets();
            if (slotTargets.isEmpty()) continue;
            if (!(data instanceof CellContentRow)) continue;

            CellInfo cell = ((CellContentRow) data).getCell();
            for (SlotsLine.PartitionSlotTarget slot : slotTargets) {
                targets.add(new IGhostIngredientHandler.Target<Object>() {
                    @Override
                    public Rectangle getArea() {
                        return new Rectangle(slot.absX, slot.absY, slot.width, slot.height);
                    }

                    @Override
                    public void accept(Object ing) {
                        ItemStack stack = JeiGhostHandler.convertJeiIngredientToItemStack(
                            ing, cell.isFluid(), cell.isEssentia());
                        if (!stack.isEmpty()) {
                            guiContext.sendPacket(new PacketPartitionAction(
                                cell.getParentStorageId(), cell.getSlot(),
                                PacketPartitionAction.Action.ADD_ITEM, slot.absoluteIndex, stack));
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
        if (lineData instanceof StorageInfo) {
            return createStorageHeader((StorageInfo) lineData, y);
        }

        if (lineData instanceof CellContentRow) {
            return createCellContentLine((CellContentRow) lineData, y);
        }

        if (lineData instanceof EmptySlotInfo) {
            return createEmptySlotLine((EmptySlotInfo) lineData, y);
        }

        return null;
    }

    @Override
    protected boolean isContentLine(List<?> allLines, int index) {
        if (index < 0 || index >= allLines.size()) return false;

        Object line = allLines.get(index);

        return line instanceof CellContentRow || line instanceof EmptySlotInfo;
    }

    // ---- Storage header creation ----

    private StorageHeader createStorageHeader(StorageInfo storage, int y) {
        StorageHeader header = new StorageHeader(y, fontRenderer, itemRender);
        header.setIconSupplier(storage::getBlockItem);
        header.setNameSupplier(storage::getName);
        header.setHasCustomNameSupplier(storage::hasCustomName);
        header.setLocationSupplier(storage::getLocationString);

        // Use TabStateManager for expand/collapse state (persists across rebuilds)
        // Determine tab type based on slot mode (INVENTORY or PARTITION)
        TabStateManager.TabType tabType = isPartitionMode
            ? TabStateManager.TabType.PARTITION
            : TabStateManager.TabType.INVENTORY;
        header.setExpandedSupplier(() ->
            TabStateManager.getInstance().isExpanded(tabType, storage.getId()));

        header.setOnNameClick(() -> guiContext.startInlineRename(storage,
            y, getRenameFieldX(storage), getRenameFieldRightEdge(storage)));
        header.setOnNameDoubleClick(() -> guiContext.highlightInWorld(
            storage.getPos(), storage.getDimension(), storage.getName()));
        header.setOnExpandToggle(() -> {
            TabStateManager.getInstance().toggleExpanded(tabType, storage.getId());
            guiContext.rebuildAndUpdateScrollbar();
        });

        return header;
    }

    // ---- Cell content line creation ----

    private IWidget createCellContentLine(CellContentRow row, int y) {
        CellInfo cell = row.getCell();

        if (row.isFirstRow()) {
            return createFirstRow(cell, row.getStartIndex(), y);
        }

        return createContinuationRow(cell, row.getStartIndex(), y);
    }

    /**
     * First row of a cell: cell slot + upgrade cards + content/partition slots + tree button.
     */
    private CellSlotsLine createFirstRow(CellInfo cell, int startIndex, int y) {
        CellSlotsLine line = new CellSlotsLine(
            y, SLOTS_PER_ROW, SLOTS_X_OFFSET, slotMode,
            startIndex, fontRenderer, itemRender
        );

        // Cell slot configuration
        line.setCellItemSupplier(cell::getCellItem);
        line.setCellFilledSupplier(() -> !cell.getCellItem().isEmpty());

        // Cell slot click (insert/extract cell)
        line.setCellSlotCallback(button -> {
            if (button != 0) return;

            ItemStack heldStack = guiContext.getHeldStack();
            if (cell.getCellItem().isEmpty() && !heldStack.isEmpty()) {
                // Insert held cell into this slot
                guiContext.sendPacket(new PacketInsertCell(
                    cell.getParentStorageId(), cell.getSlot()));
            } else if (!cell.getCellItem().isEmpty()) {
                // Pick up cell: shift = to inventory, normal click = to cursor (swap/pickup)
                boolean toInventory = guiContext.isShiftDown();
                guiContext.sendPacket(new PacketPickupCell(
                    cell.getParentStorageId(), cell.getSlot(), toInventory));
            }
        });

        // Configure content/partition data suppliers
        configureSlotData(line, cell);

        // Upgrade cards
        CardsDisplay cards = createCellCardsDisplay(cell, y,
            (c, upgradeSlot) -> handleCardClick(c, upgradeSlot));
        if (cards != null) line.setCardsDisplay(cards);

        // Tree junction button (DoPartition or ClearPartition)
        SmallButton treeBtn = new SmallButton(0, 0, treeButtonType, () -> {
            if (isPartitionMode) {
                guiContext.sendPacket(new PacketPartitionAction(
                    cell.getParentStorageId(), cell.getSlot(),
                    PacketPartitionAction.Action.CLEAR_ALL));
            } else {
                guiContext.sendPacket(new PacketPartitionAction(
                    cell.getParentStorageId(), cell.getSlot(),
                    PacketPartitionAction.Action.SET_ALL_FROM_CONTENTS));
            }
        });
        line.setTreeButton(treeBtn);

        // GUI offsets for JEI targets
        line.setGuiOffsets(guiLeft, guiTop);

        return line;
    }

    /**
     * Continuation row (not the first row for a cell): just content/partition slots.
     */
    private ContinuationLine createContinuationRow(CellInfo cell, int startIndex, int y) {
        ContinuationLine line = new ContinuationLine(
            y, SLOTS_PER_ROW, SLOTS_X_OFFSET, slotMode,
            startIndex, fontRenderer, itemRender
        );

        configureSlotData(line, cell);
        line.setGuiOffsets(guiLeft, guiTop);

        return line;
    }

    /**
     * Configure the content/partition data suppliers on a SlotsLine.
     */
    private void configureSlotData(SlotsLine line, CellInfo cell) {
        if (slotMode == SlotsLine.SlotMode.CONTENT) {
            line.setItemsSupplier(cell::getContents);
            line.setPartitionSupplier(cell::getPartition);
            line.setCountProvider(() -> cell::getContentCount);
        } else {
            // Partition mode: partition list is the items source
            line.setItemsSupplier(cell::getPartition);
            line.setMaxSlots(GuiConstants.MAX_CELL_PARTITION_SLOTS);
        }

        line.setSlotClickCallback((slotIndex, mouseButton) -> {
            if (mouseButton != 0) return;

            if (isPartitionMode) {
                ItemStack heldStack = guiContext.getHeldStack();
                List<ItemStack> partition = cell.getPartition();
                boolean slotOccupied = slotIndex < partition.size() && !partition.get(slotIndex).isEmpty();

                if (!heldStack.isEmpty()) {
                    guiContext.sendPacket(new PacketPartitionAction(
                        cell.getParentStorageId(), cell.getSlot(),
                        PacketPartitionAction.Action.ADD_ITEM, slotIndex, heldStack));
                } else if (slotOccupied) {
                    guiContext.sendPacket(new PacketPartitionAction(
                        cell.getParentStorageId(), cell.getSlot(),
                        PacketPartitionAction.Action.REMOVE_ITEM, slotIndex));
                }
            } else {
                // Content mode: toggle partition for content item
                List<ItemStack> contents = cell.getContents();
                if (slotIndex < contents.size() && !contents.get(slotIndex).isEmpty()) {
                    guiContext.sendPacket(new PacketPartitionAction(
                        cell.getParentStorageId(), cell.getSlot(),
                        PacketPartitionAction.Action.TOGGLE_ITEM, contents.get(slotIndex)));
                }
            }
        });
    }

    // ---- Empty slot line creation ----

    /**
     * Empty cell slot: shows an empty cell slot placeholder (no content slots).
     */
    private CellSlotsLine createEmptySlotLine(EmptySlotInfo emptySlot, int y) {
        CellSlotsLine line = new CellSlotsLine(
            y, SLOTS_PER_ROW, SLOTS_X_OFFSET, slotMode,
            0, fontRenderer, itemRender
        );

        // Empty cell slot (no item, not filled)
        line.setCellItemSupplier(() -> ItemStack.EMPTY);
        line.setCellFilledSupplier(() -> false);

        // Cell slot click (insert cell into empty slot)
        line.setCellSlotCallback(button -> {
            if (button != 0) return;

            ItemStack heldStack = guiContext.getHeldStack();
            if (!heldStack.isEmpty()) {
                guiContext.sendPacket(new PacketInsertCell(
                    emptySlot.getParentStorageId(), emptySlot.getSlot()));
            }
        });

        line.setGuiOffsets(guiLeft, guiTop);

        return line;
    }

    // ---- Upgrade card click handling ----

    private void handleCardClick(CellInfo cell, int upgradeSlotIndex) {
        if (CellTerminalServerConfig.isInitialized()
                && !CellTerminalServerConfig.getInstance().isUpgradeExtractEnabled()) {
            guiContext.showError("cellterminal.error.upgrade_extract_disabled");
            return;
        }

        boolean toInventory = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        guiContext.sendPacket(PacketExtractUpgrade.forCell(
            cell.getParentStorageId(), cell.getSlot(), upgradeSlotIndex, toInventory));
    }
}
