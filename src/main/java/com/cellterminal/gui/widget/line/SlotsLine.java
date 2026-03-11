package com.cellterminal.gui.widget.line;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.util.ReadableNumberConverter;

import com.cellterminal.gui.ComparisonUtils;
import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.widget.AbstractWidget;


/**
 * A line widget that renders a grid of content or partition slots.
 * <p>
 * Supports two modes:
 * - **Content mode**: Shows item contents with count labels and partition indicators ("P").
 *   Click toggles the item into/out of partition.
 * - **Partition mode**: Shows partition entries with amber tint.
 *   Click sets/clears individual partition slots. Supports JEI ghost ingredient drop.
 * <p>
 * Configuration:
 * - {@code slotsPerRow}: Number of slots per row (8 for cells, 9 for storage buses)
 * - {@code slotsXOffset}: X offset from GUI left for the first slot
 * - {@code startIndex}: Index into the data list for the first slot in this row
 *
 * @see CellSlotsLine
 * @see ContinuationLine
 */
public class SlotsLine extends AbstractLine {

    /**
     * Determines the behavior and visual style of the slot grid.
     */
    public enum SlotMode {
        /** Show item contents with counts and partition indicators */
        CONTENT,
        /** Show partition entries with amber tint, supports drag-and-drop */
        PARTITION
    }

    /**
     * Callback for slot interactions (click on content or partition slot).
     */
    @FunctionalInterface
    public interface SlotClickCallback {
        /**
         * @param slotIndex The absolute index into the data list
         * @param mouseButton Mouse button (0=left, 1=right)
         */
        void onSlotClicked(int slotIndex, int mouseButton);
    }

    /**
     * Tracks a visible partition slot for JEI ghost ingredient integration.
     */
    public static class PartitionSlotTarget {
        public final int absoluteIndex;
        public final int absX;
        public final int absY;
        public final int width;
        public final int height;

        public PartitionSlotTarget(int absoluteIndex, int absX, int absY, int width, int height) {
            this.absoluteIndex = absoluteIndex;
            this.absX = absX;
            this.absY = absY;
            this.width = width;
            this.height = height;
        }
    }

    private static final int SIZE = GuiConstants.MINI_SLOT_SIZE;
    private static final ResourceLocation TEXTURE =
        new ResourceLocation("cellterminal", "textures/guis/atlas.png");

    protected final int slotsPerRow;
    protected final int slotsXOffset;
    protected final SlotMode mode;
    protected final FontRenderer fontRenderer;
    protected final RenderItem itemRender;

    /** Supplier for the items to display (content or partition list) */
    protected Supplier<List<ItemStack>> itemsSupplier;

    /** Supplier for the partition list (used in content mode for the "P" indicator) */
    protected Supplier<List<ItemStack>> partitionSupplier;

    /** Supplier for item counts (used in content mode, index-aligned with items) */
    protected Supplier<ContentCountProvider> countProvider;

    /** Starting index into the data list for this row */
    protected int startIndex;

    /** Maximum number of slots allowed (e.g., MAX_CELL_PARTITION_SLOTS) */
    protected int maxSlots = Integer.MAX_VALUE;

    /** Absolute GUI position offsets for JEI target registration */
    protected int guiLeft;
    protected int guiTop;

    // Hover tracking (computed during draw, consumed by tooltip/click)
    protected int hoveredSlotIndex = -1;
    protected ItemStack hoveredStack = ItemStack.EMPTY;
    protected int hoveredAbsX;
    protected int hoveredAbsY;

    // JEI targets accumulated during draw
    protected final List<PartitionSlotTarget> partitionTargets = new ArrayList<>();

    protected SlotClickCallback slotClickCallback;

    /** Supplier for the selection state (selected lines get a highlight overlay) */
    protected Supplier<Boolean> selectedSupplier;

    /** Whether to draw a horizontal separator line at the top of this row */
    protected boolean drawTopSeparator = false;

    /**
     * @param y Y position relative to GUI
     * @param slotsPerRow Number of slots per row (8 for cells, 9 for buses)
     * @param slotsXOffset X offset from GUI left where slots start
     * @param mode Content or Partition mode
     * @param startIndex Index into data list for first slot
     * @param fontRenderer Font renderer
     * @param itemRender Item renderer
     */
    public SlotsLine(int y, int slotsPerRow, int slotsXOffset, SlotMode mode,
                     int startIndex, FontRenderer fontRenderer, RenderItem itemRender) {
        super(0, y, GuiConstants.CONTENT_RIGHT_EDGE);
        this.slotsPerRow = slotsPerRow;
        this.slotsXOffset = slotsXOffset;
        this.mode = mode;
        this.startIndex = startIndex;
        this.fontRenderer = fontRenderer;
        this.itemRender = itemRender;
    }

    public void setItemsSupplier(Supplier<List<ItemStack>> supplier) {
        this.itemsSupplier = supplier;
    }

    public void setPartitionSupplier(Supplier<List<ItemStack>> supplier) {
        this.partitionSupplier = supplier;
    }

    public void setCountProvider(Supplier<ContentCountProvider> provider) {
        this.countProvider = provider;
    }

    public void setSlotClickCallback(SlotClickCallback callback) {
        this.slotClickCallback = callback;
    }

    /**
     * Set the selection state supplier. When selected, the line gets a
     * selection highlight overlay (for batch keybind operations like quick-add).
     */
    public void setSelectedSupplier(Supplier<Boolean> supplier) {
        this.selectedSupplier = supplier;
    }

    public void setMaxSlots(int maxSlots) {
        this.maxSlots = maxSlots;
    }

    /**
     * Set whether to draw a horizontal separator line at the top of this row.
     * Used for the first partition row in temp area to visually separate from content rows.
     */
    public void setDrawTopSeparator(boolean draw) {
        this.drawTopSeparator = draw;
    }

    public void setGuiOffsets(int guiLeft, int guiTop) {
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
    }

    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    public int getStartIndex() {
        return startIndex;
    }

    /**
     * Get the hovered slot index (absolute in data), or -1 if none.
     */
    public int getHoveredSlotIndex() {
        return hoveredSlotIndex;
    }

    /**
     * Get the hovered item stack, or EMPTY.
     */
    public ItemStack getHoveredStack() {
        return hoveredStack;
    }

    /**
     * Get the absolute screen position of the hovered slot for tooltip display.
     */
    public int getHoveredAbsX() {
        return hoveredAbsX;
    }

    public int getHoveredAbsY() {
        return hoveredAbsY;
    }

    /**
     * Get the JEI partition slot targets accumulated during the last draw.
     * Only populated in PARTITION mode.
     */
    public List<PartitionSlotTarget> getPartitionTargets() {
        return Collections.unmodifiableList(partitionTargets);
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        // Draw horizontal separator at top if requested (before selection background)
        if (drawTopSeparator) {
            Gui.drawRect(GuiConstants.GUI_INDENT, y - 1, GuiConstants.CONTENT_RIGHT_EDGE, y,
                GuiConstants.COLOR_SEPARATOR);
        }

        // Draw selection background first (below everything else)
        boolean isSelected = selectedSupplier != null && selectedSupplier.get();
        if (isSelected) {
            Gui.drawRect(GuiConstants.GUI_INDENT, y, GuiConstants.CONTENT_RIGHT_EDGE,
                y + GuiConstants.ROW_HEIGHT, GuiConstants.COLOR_SELECTION);
        }

        // Draw tree lines first (background layer)
        drawTreeLines(mouseX, mouseY);

        // Reset hover state
        hoveredSlotIndex = -1;
        hoveredStack = ItemStack.EMPTY;
        partitionTargets.clear();

        // Draw slot grid
        if (mode == SlotMode.CONTENT) {
            drawContentSlots(mouseX, mouseY);
        } else {
            drawPartitionSlots(mouseX, mouseY);
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        // Let tree button handle click first
        if (super.handleClick(mouseX, mouseY, button)) return true;

        if (!visible || hoveredSlotIndex < 0) return false;
        if (slotClickCallback == null) return false;

        slotClickCallback.onSlotClicked(hoveredSlotIndex, button);

        return true;
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        // Check tree button tooltip first
        List<String> buttonTooltip = super.getTooltip(mouseX, mouseY);
        if (!buttonTooltip.isEmpty()) return buttonTooltip;

        // Slot tooltip is handled by the parent tab/GUI since it requires
        // rendering an item tooltip (which needs the full GUI context)
        return Collections.emptyList();
    }

    @Override
    public ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) return ItemStack.EMPTY;

        return hoveredStack;
    }

    // ---- Content slot rendering ----

    protected void drawContentSlots(int mouseX, int mouseY) {
        List<ItemStack> items = itemsSupplier != null ? itemsSupplier.get() : Collections.emptyList();
        List<ItemStack> partition = partitionSupplier != null ? partitionSupplier.get() : Collections.emptyList();
        ContentCountProvider counts = countProvider != null ? countProvider.get() : null;

        for (int x = slotsXOffset; x < slotsXOffset + (slotsPerRow * SIZE); x += SIZE) {
            drawSlotBackground(x, y);
        }

        int slots = Integer.min(startIndex + slotsPerRow, items.size()) - startIndex;
        for (int i = 0; i < slots; i++) {
            int absIndex = startIndex + i;
            int slotX = slotsXOffset + (i * SIZE);

            ItemStack stack = items.get(absIndex);
            if (stack.isEmpty()) continue;

            renderItemStack(stack, slotX, y);

            // Draw partition indicator "P" if item is in partition
            if (ComparisonUtils.isInPartition(stack, partition)) {
                drawPartitionIndicator(slotX, y);
            }

            // Draw item count
            if (counts != null) {
                long count = counts.getCount(absIndex);
                drawItemCount(count, slotX, y);
            }

            // Check hover
            if (mouseX >= slotX && mouseX < slotX + SIZE && mouseY >= y && mouseY < y + SIZE) {
                drawSlotHoverHighlight(slotX, y);
                hoveredSlotIndex = absIndex;
                hoveredStack = stack;
                hoveredAbsX = guiLeft + mouseX;
                hoveredAbsY = guiTop + mouseY;
            }
        }
    }

    // ---- Partition slot rendering ----

    protected void drawPartitionSlots(int mouseX, int mouseY) {
        List<ItemStack> partition = itemsSupplier != null ? itemsSupplier.get() : Collections.emptyList();

        for (int x = slotsXOffset; x < slotsXOffset + (slotsPerRow * SIZE); x += SIZE) {
            drawPartitionSlotBackground(x, y);
        }

        for (int i = 0; i < slotsPerRow; i++) {
            int absIndex = startIndex + i;
            if (absIndex >= maxSlots) break;

            int slotX = slotsXOffset + (i * SIZE);

            // Register JEI ghost target
            partitionTargets.add(new PartitionSlotTarget(
                absIndex, guiLeft + slotX, guiTop + y, SIZE, SIZE));

            // Draw partition item if present
            ItemStack partItem = absIndex < partition.size() ? partition.get(absIndex) : ItemStack.EMPTY;
            if (!partItem.isEmpty()) {
                renderItemStack(partItem, slotX, y);
            }

            // Check hover
            if (mouseX >= slotX && mouseX < slotX + SIZE && mouseY >= y && mouseY < y + SIZE) {
                drawSlotHoverHighlight(slotX, y);
                hoveredSlotIndex = absIndex;

                if (!partItem.isEmpty()) {
                    hoveredStack = partItem;
                    hoveredAbsX = guiLeft + mouseX;
                    hoveredAbsY = guiTop + mouseY;
                }
            }
        }
    }

    // ---- Drawing helpers ----

    protected void drawSlotBackground(int slotX, int slotY) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();

        int texX = GuiConstants.MINI_SLOT_X;
        int texY = GuiConstants.MINI_SLOT_Y;
        Gui.drawScaledCustomSizeModalRect(
            slotX, slotY, texX, texY, SIZE, SIZE, SIZE, SIZE,
            GuiConstants.ATLAS_WIDTH, GuiConstants.ATLAS_HEIGHT);
    }

    protected void drawPartitionSlotBackground(int slotX, int slotY) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();

        // Partition variant uses the right half of the texture (x uv 16-31)
        int texX = GuiConstants.MINI_SLOT_X + SIZE;
        int texY = GuiConstants.MINI_SLOT_Y;
        Gui.drawScaledCustomSizeModalRect(
            slotX, slotY, texX, texY, SIZE, SIZE, SIZE, SIZE,
            GuiConstants.ATLAS_WIDTH, GuiConstants.ATLAS_HEIGHT);
    }

    protected void drawSlotHoverHighlight(int slotX, int slotY) {
        Gui.drawRect(slotX + 1, slotY + 1, slotX + SIZE - 1, slotY + SIZE - 1, GuiConstants.COLOR_HOVER_HIGHLIGHT);
    }

    protected void renderItemStack(ItemStack stack, int renderX, int renderY) {
        AbstractWidget.renderItemStack(itemRender, stack, renderX, renderY);
    }

    protected void drawItemCount(long count, int slotX, int slotY) {
        String countStr = formatItemCount(count);
        if (countStr.isEmpty()) return;

        int countWidth = fontRenderer.getStringWidth(countStr);
        int textX = slotX + SIZE - 1;
        int textY = slotY + SIZE - 5;

        GlStateManager.disableDepth();
        GlStateManager.pushMatrix();
        GlStateManager.scale(0.5f, 0.5f, 0.5f);
        fontRenderer.drawStringWithShadow(countStr, textX * 2 - countWidth, textY * 2, 0xFFFFFF);
        GlStateManager.popMatrix();
        GlStateManager.enableDepth();
    }

    protected void drawPartitionIndicator(int slotX, int slotY) {
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.pushMatrix();
        GlStateManager.scale(0.5f, 0.5f, 0.5f);
        fontRenderer.drawStringWithShadow("P", (slotX + 1) * 2, (slotY + 1) * 2, GuiConstants.COLOR_PARTITION_INDICATOR);
        GlStateManager.popMatrix();
        GlStateManager.enableDepth();
    }

    private String formatItemCount(long count) {
        if (count < 1000) return String.valueOf(count);

        return ReadableNumberConverter.INSTANCE.toWideReadableForm(count);
    }

    /**
     * Provider interface for getting item counts by index.
     * Separates count access from the data model to support
     * different backends (CellInfo, StorageBusInfo, etc.).
     */
    @FunctionalInterface
    public interface ContentCountProvider {
        long getCount(int index);
    }
}
