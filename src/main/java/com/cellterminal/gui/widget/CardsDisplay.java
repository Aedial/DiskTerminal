package com.cellterminal.gui.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;


/**
 * Widget that displays cell upgrade cards (small 8x8 icons).
 * <p>
 * Cards are drawn as half-size item icons in a 2-column grid layout.
 * Columns are placed left-to-right, rows top-to-bottom.
 * Supports hover tracking for tooltip display and click-to-extract behavior.
 * <p>
 * Usage: created by line or header widgets, positioned relative to the cell icon.
 * The cards data comes from a supplier so the widget always reflects current state.
 */
public class CardsDisplay extends AbstractWidget {

    /** Width of each card icon (8px half-size item) */
    private static final int CARD_ICON_SIZE = 8;

    /** Spacing between card icons (icon + 1px gap) */
    private static final int CARD_STRIDE = 9;

    /** Number of columns in the card grid */
    private static final int COLUMNS = 2;

    private final Supplier<List<CardEntry>> cardsSupplier;
    private final RenderItem itemRender;

    // Tracked hover state
    private int hoveredCardIndex = -1;
    private ItemStack hoveredCardStack = ItemStack.EMPTY;

    /**
     * A single card entry with its item and slot position.
     */
    public static class CardEntry {
        public final ItemStack stack;
        public final int slotIndex;

        public CardEntry(ItemStack stack, int slotIndex) {
            this.stack = stack;
            this.slotIndex = slotIndex;
        }
    }

    /**
     * Callback for card click events (upgrade extraction).
     */
    @FunctionalInterface
    public interface CardClickCallback {
        /**
         * @param slotIndex The upgrade slot index that was clicked
         */
        void onCardClicked(int slotIndex);
    }

    private CardClickCallback clickCallback;

    /**
     * @param x X position (relative to GUI)
     * @param y Y position (relative to GUI, aligned with top of cell icon)
     * @param cardsSupplier Supplier for the card entries to display
     * @param itemRender Item renderer for icons
     */
    public CardsDisplay(int x, int y, Supplier<List<CardEntry>> cardsSupplier, RenderItem itemRender) {
        super(x, y, 0, CARD_ICON_SIZE);
        this.cardsSupplier = cardsSupplier;
        this.itemRender = itemRender;
    }

    public void setClickCallback(CardClickCallback callback) {
        this.clickCallback = callback;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        List<CardEntry> cards = cardsSupplier.get();
        if (cards.isEmpty()) return;

        hoveredCardIndex = -1;
        hoveredCardStack = ItemStack.EMPTY;

        // Calculate grid dimensions
        int rows = (cards.size() + COLUMNS - 1) / COLUMNS;
        this.width = COLUMNS * CARD_STRIDE;
        this.height = rows * CARD_STRIDE;

        // Render each card in a 2-column grid (column-first, then row)
        for (int i = 0; i < cards.size(); i++) {
            CardEntry entry = cards.get(i);
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int iconX = x + col * CARD_STRIDE;
            int iconY = y + row * CARD_STRIDE;

            if (!entry.stack.isEmpty()) {
                renderSmallItemStack(entry.stack, iconX, iconY);

                // Check hover (using 8x8 icon bounds) - only for non-empty cards
                if (mouseX >= iconX && mouseX < iconX + CARD_ICON_SIZE
                    && mouseY >= iconY && mouseY < iconY + CARD_ICON_SIZE) {
                    hoveredCardIndex = entry.slotIndex;
                    hoveredCardStack = entry.stack;
                }
            }
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible || button != 0 || hoveredCardIndex < 0) return false;
        if (clickCallback == null) return false;

        clickCallback.onCardClicked(hoveredCardIndex);

        return true;
    }

    @Override
    public boolean isHovered(int mouseX, int mouseY) {
        if (!visible) return false;

        List<CardEntry> cards = cardsSupplier.get();
        if (cards.isEmpty()) return false;

        // Check each individual card's bounds in the 2-column grid
        // Only non-empty cards are hoverable (empty slots are just visual placeholders)
        for (int i = 0; i < cards.size(); i++) {
            CardEntry entry = cards.get(i);
            if (entry.stack.isEmpty()) continue;

            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int iconX = x + col * CARD_STRIDE;
            int iconY = y + row * CARD_STRIDE;
            if (mouseX >= iconX && mouseX < iconX + CARD_ICON_SIZE
                && mouseY >= iconY && mouseY < iconY + CARD_ICON_SIZE) {
                return true;
            }
        }

        return false;
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (hoveredCardIndex < 0 || hoveredCardStack.isEmpty()) return Collections.emptyList();

        // Return the item's display name with extraction hint
        List<String> lines = new ArrayList<>();
        lines.add("§6" + hoveredCardStack.getDisplayName());
        lines.add("");
        lines.add("§b" + I18n.format("gui.cellterminal.upgrade.click_extract"));
        lines.add("§b" + I18n.format("gui.cellterminal.upgrade.shift_click_inventory"));

        return lines;
    }

    private void renderSmallItemStack(ItemStack stack, int renderX, int renderY) {
        if (stack.isEmpty()) return;

        GlStateManager.pushMatrix();
        GlStateManager.translate(renderX, renderY, 0);
        GlStateManager.scale(0.5f, 0.5f, 1.0f);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemIntoGUI(stack, 0, 0);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();

        GlStateManager.popMatrix();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
}
