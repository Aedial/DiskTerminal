package com.cellterminal.gui.widget.line;

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

import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.widget.AbstractWidget;
import com.cellterminal.gui.widget.CardsDisplay;


/**
 * Terminal tab (Tab 1) line widget.
 *
 * Each line represents a single cell in the terminal overview. Shows:
 * - Tree line connector to parent storage
 * - Upgrade card icons (left of cell)
 * - Cell item icon
 * - Cell name (clickable to rename)
 * - Byte usage bar
 * - Action buttons: Eject, Inventory, Partition (textured from atlas.png)
 *
 * Unlike other line types, terminal lines are single-row-per-cell and don't
 * have content/partition slot grids.
 */
public class TerminalLine extends AbstractLine {

    /**
     * Button hover type constants for click handling.
     */
    public static final int HOVER_NONE = 0;
    public static final int HOVER_INVENTORY = 1;
    public static final int HOVER_PARTITION = 2;
    public static final int HOVER_EJECT = 3;

    private static final int SIZE = GuiConstants.TAB1_BUTTON_SIZE;
    private static final ResourceLocation TAB1_BUTTONS_TEXTURE =
        new ResourceLocation("cellterminal", "textures/guis/atlas.png");

    // Texture column indices for each button type
    private static final int TAB1_COL_EJECT = 0;
    private static final int TAB1_COL_INVENTORY = 1;
    private static final int TAB1_COL_PARTITION = 2;

    // Max pixel width for cell name (from name start to Eject button, with gap)
    private static final int CELL_NAME_MAX_PIXEL_WIDTH =
        GuiConstants.BUTTON_EJECT_X - (GuiConstants.CELL_INDENT + GuiConstants.CELL_NAME_X_OFFSET) - 4;

    /**
     * Callback for terminal line button actions.
     */
    public interface TerminalLineCallback {
        void onEjectClicked();
        void onInventoryClicked();
        void onPartitionClicked();
        /** Called on right-click for rename */
        void onNameClicked();
        /** Called on double-click for highlight in world */
        void onNameDoubleClicked();
    }

    private final FontRenderer fontRenderer;
    private final RenderItem itemRender;

    /** Supplier for the cell item (the cell itself) */
    private Supplier<ItemStack> cellItemSupplier;

    /** Supplier for the cell display name */
    private Supplier<String> cellNameSupplier;

    /** Supplier for whether the cell has a custom name */
    private Supplier<Boolean> hasCustomNameSupplier;

    /** Supplier for byte usage percentage (0.0 - 1.0) */
    private Supplier<Float> byteUsageSupplier;

    /** Cards display widget */
    private CardsDisplay cardsDisplay;

    /** Callback for button actions */
    private TerminalLineCallback callback;

    // Hover tracking (computed during draw)
    private int hoveredButton = HOVER_NONE;
    private boolean nameHovered = false;

    // Double-click tracking for highlight
    private long lastNameClickTime = 0;

    /**
     * @param y Y position relative to GUI
     * @param fontRenderer Font renderer
     * @param itemRender Item renderer
     */
    public TerminalLine(int y, FontRenderer fontRenderer, RenderItem itemRender) {
        super(0, y, GuiConstants.CONTENT_RIGHT_EDGE);
        this.fontRenderer = fontRenderer;
        this.itemRender = itemRender;
    }

    public void setCellItemSupplier(Supplier<ItemStack> supplier) {
        this.cellItemSupplier = supplier;
    }

    public void setCellNameSupplier(Supplier<String> supplier) {
        this.cellNameSupplier = supplier;
    }

    public void setHasCustomNameSupplier(Supplier<Boolean> supplier) {
        this.hasCustomNameSupplier = supplier;
    }

    public void setByteUsageSupplier(Supplier<Float> supplier) {
        this.byteUsageSupplier = supplier;
    }

    public void setCardsDisplay(CardsDisplay cards) {
        this.cardsDisplay = cards;
    }

    public void setCallback(TerminalLineCallback callback) {
        this.callback = callback;
    }

    /**
     * Get the currently hovered button type.
     */
    public int getHoveredButton() {
        return hoveredButton;
    }

    /**
     * Whether the cell name is currently hovered (for rename).
     */
    public boolean isNameHovered() {
        return nameHovered;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        hoveredButton = HOVER_NONE;
        nameHovered = false;

        // Draw tree lines
        drawTreeLines(mouseX, mouseY);

        // Draw upgrade cards to the left of the cell icon
        if (cardsDisplay != null) cardsDisplay.draw(mouseX, mouseY);

        // Draw cell icon
        ItemStack cellItem = cellItemSupplier != null ? cellItemSupplier.get() : ItemStack.EMPTY;
        if (!cellItem.isEmpty()) AbstractWidget.renderItemStack(itemRender, cellItem, GuiConstants.CELL_INDENT, y);

        // Draw cell name
        drawCellName(mouseX, mouseY);

        // Draw usage bar
        drawUsageBar();

        // Draw action buttons
        drawActionButtons(mouseX, mouseY);
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible) return false;

        // Cards click (left-click only)
        if (button == 0 && cardsDisplay != null && cardsDisplay.isHovered(mouseX, mouseY)) {
            return cardsDisplay.handleClick(mouseX, mouseY, button);
        }

        if (callback == null) return false;

        // Left-click only for action buttons
        if (button == 0) {
            switch (hoveredButton) {
                case HOVER_EJECT:
                    callback.onEjectClicked();
                    return true;
                case HOVER_INVENTORY:
                    callback.onInventoryClicked();
                    return true;
                case HOVER_PARTITION:
                    callback.onPartitionClicked();
                    return true;
                default:
                    break;
            }
        }

        // Name rename - RIGHT-click only
        if (button == 1 && nameHovered) {
            callback.onNameClicked();
            return true;
        }

        // Name double-click for highlight in world (left-click)
        if (button == 0 && nameHovered && callback != null) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastNameClickTime < GuiConstants.DOUBLE_CLICK_TIME_MS) {
                callback.onNameDoubleClicked();
                lastNameClickTime = 0;  // Reset to avoid triple-click triggering
                return true;
            }
            lastNameClickTime = currentTime;
        }

        return false;
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) return Collections.emptyList();

        // Cards tooltip
        if (cardsDisplay != null && cardsDisplay.isHovered(mouseX, mouseY)) {
            return cardsDisplay.getTooltip(mouseX, mouseY);
        }

        return Collections.emptyList();
    }

    @Override
    public ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) return ItemStack.EMPTY;

        // Check if hovering the cell icon
        int cellX = GuiConstants.CELL_INDENT;
        if (mouseX >= cellX && mouseX < cellX + GuiConstants.MINI_SLOT_SIZE
            && mouseY >= y && mouseY < y + GuiConstants.MINI_SLOT_SIZE) {
            ItemStack cellItem = cellItemSupplier != null ? cellItemSupplier.get() : ItemStack.EMPTY;
            if (!cellItem.isEmpty()) return cellItem;
        }

        return ItemStack.EMPTY;
    }

    // ---- Drawing helpers ----

    private void drawCellName(int mouseX, int mouseY) {
        String name = cellNameSupplier != null ? cellNameSupplier.get() : "";
        if (name.isEmpty()) return;

        // Truncate to fit before the Eject button
        name = AbstractWidget.trimTextToWidth(fontRenderer, name, CELL_NAME_MAX_PIXEL_WIDTH);

        boolean hasCustomName = hasCustomNameSupplier != null && hasCustomNameSupplier.get();
        int nameColor = hasCustomName ? GuiConstants.COLOR_CUSTOM_NAME : GuiConstants.COLOR_TEXT_NORMAL;

        int nameX = GuiConstants.CELL_INDENT + GuiConstants.CELL_NAME_X_OFFSET;
        int nameY = y + 1;

        fontRenderer.drawString(name, nameX, nameY, nameColor);

        // Check name hover for rename interaction
        int nameWidth = fontRenderer.getStringWidth(name);
        if (mouseX >= nameX && mouseX < nameX + nameWidth
            && mouseY >= nameY && mouseY < nameY + 9) {
            nameHovered = true;
        }
    }

    private void drawUsageBar() {
        float usage = byteUsageSupplier != null ? byteUsageSupplier.get() : 0f;

        int barX = GuiConstants.CELL_INDENT + GuiConstants.CELL_NAME_X_OFFSET;
        int barY = y + 10;

        // Background
        Gui.drawRect(barX, barY, barX + GuiConstants.USAGE_BAR_WIDTH, barY + GuiConstants.USAGE_BAR_HEIGHT,
            GuiConstants.COLOR_USAGE_BAR_BACKGROUND);

        // Fill
        int filledWidth = (int) (GuiConstants.USAGE_BAR_WIDTH * usage);
        if (filledWidth > 0) {
            int fillColor = getUsageColor(usage);
            Gui.drawRect(barX, barY, barX + filledWidth, barY + GuiConstants.USAGE_BAR_HEIGHT, fillColor);
        }
    }

    private void drawActionButtons(int mouseX, int mouseY) {
        boolean ejectHovered = isButtonHovered(mouseX, mouseY, GuiConstants.BUTTON_EJECT_X);
        boolean invHovered = isButtonHovered(mouseX, mouseY, GuiConstants.BUTTON_INVENTORY_X);
        boolean partHovered = isButtonHovered(mouseX, mouseY, GuiConstants.BUTTON_PARTITION_X);

        drawTexturedButton(GuiConstants.BUTTON_EJECT_X, y + 1, TAB1_COL_EJECT, ejectHovered);
        drawTexturedButton(GuiConstants.BUTTON_INVENTORY_X, y + 1, TAB1_COL_INVENTORY, invHovered);
        drawTexturedButton(GuiConstants.BUTTON_PARTITION_X, y + 1, TAB1_COL_PARTITION, partHovered);

        if (ejectHovered) {
            hoveredButton = HOVER_EJECT;
        } else if (invHovered) {
            hoveredButton = HOVER_INVENTORY;
        } else if (partHovered) {
            hoveredButton = HOVER_PARTITION;
        }
    }

    private boolean isButtonHovered(int mouseX, int mouseY, int buttonX) {
        return mouseX >= buttonX && mouseX < buttonX + SIZE
            && mouseY >= y + 1 && mouseY < y + 1 + SIZE;
    }

    /**
     * Draw a textured button from atlas.png.
     */
    private void drawTexturedButton(int drawX, int drawY, int column, boolean hovered) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(TAB1_BUTTONS_TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();

        int texX = GuiConstants.TAB1_BUTTON_X + column * SIZE;
        int texY = GuiConstants.TAB1_BUTTON_Y + (hovered ? SIZE : 0);
        Gui.drawScaledCustomSizeModalRect(
            drawX, drawY, texX, texY, SIZE, SIZE, SIZE, SIZE,
            GuiConstants.ATLAS_WIDTH, GuiConstants.ATLAS_HEIGHT);
    }

    private int getUsageColor(float percent) {
        if (percent > 0.9f) return GuiConstants.COLOR_USAGE_HIGH;
        if (percent > 0.75f) return GuiConstants.COLOR_USAGE_MEDIUM;

        return GuiConstants.COLOR_USAGE_LOW;
    }
}
