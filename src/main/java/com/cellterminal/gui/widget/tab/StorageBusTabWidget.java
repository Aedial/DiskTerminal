package com.cellterminal.gui.widget.tab;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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

import com.cellterminal.client.KeyBindings;
import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.client.StorageBusContentRow;
import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.client.TabStateManager;
import com.cellterminal.config.CellTerminalServerConfig;
import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.handler.JeiGhostHandler;
import com.cellterminal.gui.handler.TerminalDataManager;
import com.cellterminal.gui.handler.QuickPartitionHandler;
import com.cellterminal.gui.overlay.MessageHelper;
import com.cellterminal.gui.widget.CardsDisplay;
import com.cellterminal.gui.widget.DoubleClickTracker;
import com.cellterminal.gui.widget.IWidget;
import com.cellterminal.gui.widget.button.ButtonType;
import com.cellterminal.gui.widget.button.SmallButton;
import com.cellterminal.gui.widget.header.StorageBusHeader;
import com.cellterminal.gui.widget.line.ContinuationLine;
import com.cellterminal.gui.widget.line.SlotsLine;
import com.cellterminal.integration.ThaumicEnergisticsIntegration;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketExtractUpgrade;
import com.cellterminal.network.PacketStorageBusIOMode;
import com.cellterminal.network.PacketStorageBusPartitionAction;
import com.cellterminal.network.PacketUpgradeStorageBus;


/**
 * Tab widget for Storage Bus Inventory (Tab 4) and Storage Bus Partition (Tab 5) tabs.
 * <p>
 * Both tabs display storage bus groups with expandable content rows. Each row is either:
 * <ul>
 *   <li>{@link StorageBusInfo} → {@link StorageBusHeader} (name, location, IO mode, expand)</li>
 *   <li>{@link StorageBusContentRow} (first row) → {@link SlotsLine} with tree button</li>
 *   <li>{@link StorageBusContentRow} (continuation) → {@link ContinuationLine}</li>
 * </ul>
 *
 * <h3>Slot mode differences</h3>
 * <ul>
 *   <li><b>Inventory tab:</b> Shows bus contents with counts. Tree button is
 *       DO_PARTITION (adds item to partition). Content slots show "P" indicator
 *       for items that are in the partition.</li>
 *   <li><b>Partition tab:</b> Shows bus partitions with amber tint. Tree button is
 *       CLEAR_PARTITION (removes partition entry). Supports JEI ghost drops.</li>
 * </ul>
 *
 * Storage bus tabs use 9 slots per row at a narrower X offset (no inline cell slot).
 */
public class StorageBusTabWidget extends AbstractTabWidget {

    /** Slots per row for storage buses: 9 */
    private static final int SLOTS_PER_ROW = GuiConstants.STORAGE_BUS_SLOTS_PER_ROW;

    /** X offset for content/partition slots */
    private static final int SLOTS_X_OFFSET = GuiConstants.CELL_INDENT + 4;

    private final SlotsLine.SlotMode slotMode;
    private final ButtonType treeButtonType;
    private final boolean isPartitionMode;

    /**
     * @param slotMode CONTENT for Storage Bus Inventory tab, PARTITION for Storage Bus Partition tab
     */
    public StorageBusTabWidget(SlotsLine.SlotMode slotMode,
                                FontRenderer fontRenderer, RenderItem itemRender) {
        super(fontRenderer, itemRender);
        this.slotMode = slotMode;
        this.isPartitionMode = (slotMode == SlotsLine.SlotMode.PARTITION);
        this.treeButtonType = isPartitionMode ? ButtonType.CLEAR_PARTITION : ButtonType.DO_PARTITION;
    }

    // ---- Tab controller methods ----

    @Override
    public List<Object> getLines(TerminalDataManager dataManager) {
        return isPartitionMode ? dataManager.getStorageBusPartitionLines() : dataManager.getStorageBusInventoryLines();
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
            lines.add(I18n.format("gui.cellterminal.controls.storage_bus_add_key",
                KeyBindings.ADD_TO_STORAGE_BUS.getDisplayName()));
            lines.add(I18n.format("gui.cellterminal.controls.storage_bus_capacity"));
            lines.add("");
            lines.add(I18n.format("gui.cellterminal.controls.jei_drag"));
            lines.add(I18n.format("gui.cellterminal.controls.click_to_remove"));
        } else {
            lines.add(I18n.format("gui.cellterminal.controls.filter_indicator"));
            lines.add(I18n.format("gui.cellterminal.controls.click_to_remove"));
        }

        lines.add(I18n.format("gui.cellterminal.controls.double_click_storage"));
        lines.add(I18n.format("gui.cellterminal.right_click_rename"));

        return lines;
    }

    @Override
    public ItemStack getTabIcon() {
        // Returns the base storage bus icon; composite overlay is handled by TabRenderingHandler
        return AEApi.instance().definitions().parts().storageBus()
            .maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    public String getTabTooltip() {
        if (isPartitionMode) {
            return I18n.format("gui.cellterminal.tab.storage_bus_partition.tooltip");
        }

        return I18n.format("gui.cellterminal.tab.storage_bus_inventory.tooltip");
    }

    @Override
    public boolean handleTabKeyTyped(int keyCode) {
        if (!isPartitionMode) return false;
        if (!KeyBindings.ADD_TO_STORAGE_BUS.isActiveAndMatches(keyCode)) return false;

        return handleAddToStorageBusKeybind(
            guiContext.getSelectedStorageBusIds(),
            guiContext.getSlotUnderMouse(),
            guiContext.getDataManager().getStorageBusMap());
    }

    // ---- Upgrade support ----

    @Override
    public boolean handleUpgradeClick(Object hoveredData, ItemStack heldStack, boolean isShiftClick) {
        if (isShiftClick) {
            // Shift-click: server finds first bus that can accept
            guiContext.sendPacket(new PacketUpgradeStorageBus(0, true));
            return true;
        }

        StorageBusInfo bus = null;
        if (hoveredData instanceof StorageBusInfo) bus = (StorageBusInfo) hoveredData;
        else if (hoveredData instanceof StorageBusContentRow) bus = ((StorageBusContentRow) hoveredData).getStorageBus();

        if (bus != null) {
            guiContext.sendPacket(new PacketUpgradeStorageBus(bus.getId(), false));
            return true;
        }

        return false;
    }

    @Override
    public boolean handleShiftUpgradeClick(ItemStack heldStack) {
        // Shift-click: server finds first bus that can accept
        guiContext.sendPacket(new PacketUpgradeStorageBus(0, true));
        return true;
    }

    @Override
    public boolean handleInventorySlotShiftClick(ItemStack upgradeStack, int sourceSlotIndex) {
        // Shift-click from player inventory: find first visible bus that can accept
        List<Object> lines = getLines(guiContext.getDataManager());
        Set<Long> checkedBusIds = new HashSet<>();

        for (Object line : lines) {
            StorageBusInfo bus = null;

            if (line instanceof StorageBusInfo) {
                bus = (StorageBusInfo) line;
            } else if (line instanceof StorageBusContentRow) {
                bus = ((StorageBusContentRow) line).getStorageBus();
            }

            if (bus == null) continue;
            if (!checkedBusIds.add(bus.getId())) continue;
            if (!bus.canAcceptUpgrade(upgradeStack)) continue;

            guiContext.sendPacket(new PacketUpgradeStorageBus(bus.getId(), true, sourceSlotIndex));

            return true;
        }

        return false;
    }

    // ---- JEI ghost targets ----

    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        if (!isPartitionMode) return Collections.emptyList();

        List<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();
        for (Map.Entry<IWidget, Object> entry : getWidgetDataMap().entrySet()) {
            IWidget widget = entry.getKey();
            Object data = entry.getValue();
            if (!(widget instanceof SlotsLine)) continue;

            SlotsLine slotsLine = (SlotsLine) widget;
            List<SlotsLine.PartitionSlotTarget> slotTargets = slotsLine.getPartitionTargets();
            if (slotTargets.isEmpty()) continue;
            if (!(data instanceof StorageBusContentRow)) continue;

            StorageBusInfo bus = ((StorageBusContentRow) data).getStorageBus();
            for (SlotsLine.PartitionSlotTarget slot : slotTargets) {
                targets.add(new IGhostIngredientHandler.Target<Object>() {
                    @Override
                    public Rectangle getArea() {
                        return new Rectangle(slot.absX, slot.absY, slot.width, slot.height);
                    }

                    @Override
                    public void accept(Object ing) {
                        ItemStack stack = JeiGhostHandler.convertJeiIngredientForStorageBus(
                            ing, bus.isFluid(), bus.isEssentia());
                        if (!stack.isEmpty()) {
                            guiContext.sendPacket(new PacketStorageBusPartitionAction(
                                bus.getId(),
                                PacketStorageBusPartitionAction.Action.ADD_ITEM,
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
        if (lineData instanceof StorageBusInfo) {
            return createBusHeader((StorageBusInfo) lineData, y);
        }

        if (lineData instanceof StorageBusContentRow) {
            return createContentLine((StorageBusContentRow) lineData, y);
        }

        return null;
    }

    @Override
    protected boolean isContentLine(List<?> allLines, int index) {
        if (index < 0 || index >= allLines.size()) return false;

        return allLines.get(index) instanceof StorageBusContentRow;
    }

    // ---- Storage bus header creation ----

    private StorageBusHeader createBusHeader(StorageBusInfo bus, int y) {
        StorageBusHeader header = new StorageBusHeader(y, fontRenderer, itemRender);
        header.setIconSupplier(bus::getConnectedInventoryIcon);
        header.setNameSupplier(bus::getLocalizedName);
        header.setHasCustomNameSupplier(bus::hasCustomName);
        // Use TabStateManager for expand/collapse state (persists across rebuilds)
        TabStateManager.TabType tabType = isPartitionMode
            ? TabStateManager.TabType.STORAGE_BUS_PARTITION
            : TabStateManager.TabType.STORAGE_BUS_INVENTORY;
        header.setExpandedSupplier(() ->
            TabStateManager.getInstance().isBusExpanded(tabType, bus.getId()));
        header.setLocationSupplier(bus::getLocationString);
        header.setAccessModeSupplier(bus::getAccessRestriction);
        header.setSupportsIOModeSupplier(bus::supportsIOMode);

        // Upgrade cards
        CardsDisplay cards = createBusCards(bus, y);
        if (cards != null) header.setCardsDisplay(cards);

        // Rename info: header handles right-click directly via InlineRenameManager
        header.setRenameInfo(bus, GuiConstants.GUI_INDENT + 20 - 2, 0, GuiConstants.BUTTON_IO_MODE_X - 4);
        header.setOnNameDoubleClick(() -> guiContext.highlightInWorld(
            bus.getPos(), bus.getDimension(), bus.getLocalizedName()),
            DoubleClickTracker.storageBusTargetId(bus.getId()));
        header.setOnExpandToggle(() -> {
            TabStateManager.getInstance().toggleBusExpanded(tabType, bus.getId());
            guiContext.rebuildAndUpdateScrollbar();
        });
        header.setOnIOModeClick(() ->
            guiContext.sendPacket(new PacketStorageBusIOMode(bus.getId())));

        // Header selection for quick-add (partition tab only)
        if (isPartitionMode) {
            header.setOnHeaderClick(() -> {
                long busId = bus.getId();
                Set<Long> selected = guiContext.getSelectedStorageBusIds();
                if (selected.contains(busId)) {
                    selected.remove(busId);
                } else {
                    selected.add(busId);
                }
            });
            header.setSelectedSupplier(() -> guiContext.getSelectedStorageBusIds().contains(bus.getId()));
        }

        // Priority field: header registers its own field with the singleton during draw
        header.setPrioritizable(bus);
        header.setGuiOffsets(guiLeft, guiTop);

        return header;
    }

    // ---- Content line creation ----

    private IWidget createContentLine(StorageBusContentRow row, int y) {
        StorageBusInfo bus = row.getStorageBus();

        if (row.isFirstRow()) {
            return createFirstRow(bus, row.getStartIndex(), y);
        }

        return createContinuationRow(bus, row.getStartIndex(), y);
    }

    /**
     * First content row: SlotsLine with tree junction button.
     */
    private SlotsLine createFirstRow(StorageBusInfo bus, int startIndex, int y) {
        SlotsLine line = new SlotsLine(
            y, SLOTS_PER_ROW, SLOTS_X_OFFSET, slotMode,
            startIndex, fontRenderer, itemRender
        );

        configureSlotData(line, bus);

        // Tree junction button
        SmallButton treeBtn = new SmallButton(0, 0, treeButtonType, () -> {
            if (isPartitionMode) {
                guiContext.sendPacket(new PacketStorageBusPartitionAction(
                    bus.getId(), PacketStorageBusPartitionAction.Action.CLEAR_ALL));
            } else {
                guiContext.sendPacket(new PacketStorageBusPartitionAction(
                    bus.getId(), PacketStorageBusPartitionAction.Action.SET_ALL_FROM_CONTENTS));
            }
        });
        line.setTreeButton(treeBtn);
        line.setTreeButtonXOffset(-3);  // 2px to the right vs cells for tighter storage bus layout

        line.setGuiOffsets(guiLeft, guiTop);

        // Selection highlight (partition mode only)
        if (isPartitionMode) {
            line.setSelectedSupplier(() -> guiContext.getSelectedStorageBusIds().contains(bus.getId()));
        }

        return line;
    }

    /**
     * Continuation row (not the first row for this bus).
     */
    private ContinuationLine createContinuationRow(StorageBusInfo bus, int startIndex, int y) {
        ContinuationLine line = new ContinuationLine(
            y, SLOTS_PER_ROW, SLOTS_X_OFFSET, slotMode,
            startIndex, fontRenderer, itemRender
        );

        configureSlotData(line, bus);
        line.setGuiOffsets(guiLeft, guiTop);

        // Selection highlight (partition mode only)
        if (isPartitionMode) {
            line.setSelectedSupplier(() -> guiContext.getSelectedStorageBusIds().contains(bus.getId()));
        }

        return line;
    }

    /**
     * Configure content/partition data suppliers on a SlotsLine.
     */
    private void configureSlotData(SlotsLine line, StorageBusInfo bus) {
        if (slotMode == SlotsLine.SlotMode.CONTENT) {
            line.setItemsSupplier(bus::getContents);
            line.setPartitionSupplier(bus::getPartition);
            line.setCountProvider(() -> bus::getContentCount);
        } else {
            line.setItemsSupplier(bus::getPartition);
            line.setMaxSlots(GuiConstants.MAX_STORAGE_BUS_PARTITION_SLOTS);
        }

        line.setSlotClickCallback((slotIndex, mouseButton) -> {
            if (mouseButton != 0) return;

            if (isPartitionMode) {
                ItemStack heldStack = guiContext.getHeldStack();
                List<ItemStack> partition = bus.getPartition();
                boolean slotOccupied = slotIndex < partition.size() && !partition.get(slotIndex).isEmpty();

                if (!heldStack.isEmpty()) {
                    guiContext.sendPacket(new PacketStorageBusPartitionAction(
                        bus.getId(), PacketStorageBusPartitionAction.Action.ADD_ITEM,
                        slotIndex, heldStack));
                } else if (slotOccupied) {
                    guiContext.sendPacket(new PacketStorageBusPartitionAction(
                        bus.getId(), PacketStorageBusPartitionAction.Action.REMOVE_ITEM, slotIndex));
                }
            } else {
                // Content mode: toggle partition for content item
                List<ItemStack> contents = bus.getContents();
                if (slotIndex < contents.size() && !contents.get(slotIndex).isEmpty()) {
                    guiContext.sendPacket(new PacketStorageBusPartitionAction(
                        bus.getId(), PacketStorageBusPartitionAction.Action.TOGGLE_ITEM,
                        contents.get(slotIndex)));
                }
            }
        });
    }

    // ---- Keybind handling ----

    /**
     * Handle the add-to-storage-bus keybind.
     * Adds the hovered item to the partition of all selected storage buses.
     * Converts items for fluid/essentia buses, finds empty slots.
     *
     * @param selectedBusIds The set of selected storage bus IDs
     * @param hoveredSlot The slot the mouse is over (or null)
     * @param storageBusMap Map of storage bus IDs to info
     * @return true if the keybind was handled
     */
    private static boolean handleAddToStorageBusKeybind(Set<Long> selectedBusIds,
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

    // ---- Cards helper ----

    private CardsDisplay createBusCards(StorageBusInfo bus, int rowY) {
        List<CardsDisplay.CardEntry> entries = buildCardEntries(bus);
        if (entries.isEmpty()) return null;

        // Position cards at left margin (x=3)
        CardsDisplay cards = new CardsDisplay(GuiConstants.CARDS_X, rowY, () -> entries, itemRender);

        cards.setClickCallback(slotIndex -> handleBusCardClick(bus, slotIndex));

        return cards;
    }

    private List<CardsDisplay.CardEntry> buildCardEntries(StorageBusInfo bus) {
        List<CardsDisplay.CardEntry> entries = new ArrayList<>();
        int slotCount = bus.getUpgradeSlotCount();

        // Build a slot-indexed array so empty slots are visually present
        ItemStack[] slotStacks = new ItemStack[slotCount];
        java.util.Arrays.fill(slotStacks, ItemStack.EMPTY);
        for (int i = 0; i < bus.getUpgrades().size(); i++) {
            int slotIdx = bus.getUpgradeSlotIndex(i);
            if (slotIdx >= 0 && slotIdx < slotCount) {
                slotStacks[slotIdx] = bus.getUpgrades().get(i);
            }
        }

        for (int i = 0; i < slotCount; i++) {
            entries.add(new CardsDisplay.CardEntry(slotStacks[i], i));
        }

        return entries;
    }

    // ---- Upgrade card click handling ----

    private void handleBusCardClick(StorageBusInfo bus, int upgradeSlotIndex) {
        if (CellTerminalServerConfig.isInitialized()
                && !CellTerminalServerConfig.getInstance().isUpgradeExtractEnabled()) {
            guiContext.showError("cellterminal.error.upgrade_extract_disabled");
            return;
        }

        boolean toInventory = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        guiContext.sendPacket(PacketExtractUpgrade.forStorageBus(
            bus.getId(), upgradeSlotIndex, toInventory));
    }
}
