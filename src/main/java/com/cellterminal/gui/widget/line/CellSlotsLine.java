package com.cellterminal.gui.widget.line;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.widget.CardsDisplay;


/**
 * A slot line with an additional cell slot on the left.
 * <p>
 * This is used in Tab 2 (Inventory) and Tab 3 (Partition) where each cell
 * occupies one or more rows. The first row shows:
 * - Cell slot (the cell item itself, clickable for insert/extract)
 * - Upgrade card icons (at the left of the cell slot, only if cell is filled)
 * - Content/partition slots (to the right of cell slot)
 * - Tree junction button (DoPartition or ClearPartition)
 * <p>
 * If the cell slot is empty (no cell inserted), the content/partition slots
 * are not rendered - only the empty cell slot is shown.
 * <p>
 * Continuation rows for multi-row cells are handled by {@link ContinuationLine}.
 *
 * @see SlotsLine
 * @see ContinuationLine
 */
public class CellSlotsLine extends SlotsLine {

    /** Supplier for the cell item stack in the cell slot */
    private Supplier<ItemStack> cellItemSupplier;

    /** Whether the cell slot is filled (controls slot visibility) */
    private Supplier<Boolean> cellFilledSupplier;

    /** Cards display widget for upgrade icons */
    private CardsDisplay cardsDisplay;

    /** Callback when a cell is inserted/extracted from the cell slot */
    private CellSlotClickCallback cellSlotCallback;

    // Cell slot hover tracking
    private boolean cellSlotHovered = false;

    /**
     * Callback for cell slot interactions.
     */
    @FunctionalInterface
    public interface CellSlotClickCallback {
        /**
         * Called when the cell slot is clicked (insert/extract cell).
         * @param mouseButton Mouse button (0=left, 1=right)
         */
        void onCellSlotClicked(int mouseButton);
    }

    /**
     * @param y Y position relative to GUI
     * @param slotsPerRow Number of slots per row (8 for cells)
     * @param slotsXOffset X offset where content/partition slots start
     * @param mode Content or Partition
     * @param startIndex Starting data index
     * @param fontRenderer Font renderer
     * @param itemRender Item renderer
     */
    public CellSlotsLine(int y, int slotsPerRow, int slotsXOffset, SlotMode mode,
                          int startIndex, FontRenderer fontRenderer, RenderItem itemRender) {
        super(y, slotsPerRow, slotsXOffset, mode, startIndex, fontRenderer, itemRender);
    }

    public void setCellItemSupplier(Supplier<ItemStack> supplier) {
        this.cellItemSupplier = supplier;
    }

    public void setCellFilledSupplier(Supplier<Boolean> supplier) {
        this.cellFilledSupplier = supplier;
    }

    public void setCardsDisplay(CardsDisplay cards) {
        this.cardsDisplay = cards;
    }

    public void setCellSlotCallback(CellSlotClickCallback callback) {
        this.cellSlotCallback = callback;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        // Draw selection background first (below everything else)
        boolean isSelected = selectedSupplier != null && selectedSupplier.get();
        if (isSelected) {
            net.minecraft.client.gui.Gui.drawRect(GuiConstants.GUI_INDENT, y, GuiConstants.CONTENT_RIGHT_EDGE,
                y + GuiConstants.ROW_HEIGHT, GuiConstants.COLOR_SELECTION);
        }

        // Draw tree lines (background)
        drawTreeLines(mouseX, mouseY);

        cellSlotHovered = false;

        // Always draw the cell slot
        drawCellSlot(mouseX, mouseY);

        // Draw upgrade cards next to the cell slot
        // Position cards to the left of the cell slot (same y, x based on card layout)
        if (cardsDisplay != null) cardsDisplay.draw(mouseX, mouseY);

        // Only draw content/partition slots if cell is filled
        boolean cellFilled = cellFilledSupplier != null && cellFilledSupplier.get();
        if (!cellFilled) return;

        // Reset slot hover state and draw slots
        hoveredSlotIndex = -1;
        hoveredStack = ItemStack.EMPTY;
        partitionTargets.clear();

        if (mode == SlotMode.CONTENT) {
            drawContentSlots(mouseX, mouseY);
        } else {
            drawPartitionSlots(mouseX, mouseY);
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        // Tree button first
        if (treeButton != null && treeButton.isVisible()
            && treeButton.isHovered(mouseX, mouseY)) {
            return treeButton.handleClick(mouseX, mouseY, button);
        }

        // Cards click
        if (cardsDisplay != null && cardsDisplay.isHovered(mouseX, mouseY)) {
            return cardsDisplay.handleClick(mouseX, mouseY, button);
        }

        // Cell slot click
        if (cellSlotHovered && cellSlotCallback != null) {
            cellSlotCallback.onCellSlotClicked(button);
            return true;
        }

        // Content/partition slot click
        if (hoveredSlotIndex >= 0 && slotClickCallback != null) {
            slotClickCallback.onSlotClicked(hoveredSlotIndex, button);
            return true;
        }

        return false;
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) return Collections.emptyList();

        // Tree button tooltip
        if (treeButton != null && treeButton.isHovered(mouseX, mouseY)) {
            return treeButton.getTooltip(mouseX, mouseY);
        }

        // Cards tooltip
        if (cardsDisplay != null && cardsDisplay.isHovered(mouseX, mouseY)) {
            return cardsDisplay.getTooltip(mouseX, mouseY);
        }

        // Item tooltips handled separately via getHoveredItemStack()
        return Collections.emptyList();
    }

    @Override
    public ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) return ItemStack.EMPTY;

        // Cell slot hover takes priority (it's visually to the left)
        if (cellSlotHovered) {
            ItemStack cellItem = cellItemSupplier != null ? cellItemSupplier.get() : ItemStack.EMPTY;
            if (!cellItem.isEmpty()) return cellItem;
        }

        // Content/partition slot hover
        return hoveredStack;
    }

    // ---- Private helpers ----

    private void drawCellSlot(int mouseX, int mouseY) {
        int cellX = GuiConstants.CELL_INDENT;
        drawSlotBackground(cellX, y);

        ItemStack cellItem = cellItemSupplier != null ? cellItemSupplier.get() : ItemStack.EMPTY;
        if (!cellItem.isEmpty()) renderItemStack(cellItem, cellX, y);

        // Check hover
        if (mouseX >= cellX && mouseX < cellX + GuiConstants.MINI_SLOT_SIZE
            && mouseY >= y && mouseY < y + GuiConstants.MINI_SLOT_SIZE) {
            drawSlotHoverHighlight(cellX, y);
            cellSlotHovered = true;
        }
    }
}
