package com.cellterminal.gui.widget.header;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import com.cellterminal.gui.GuiConstants;


/**
 * Temp area header widget for the Cell Terminal (tab 3).
 * <p>
 * Unlike other headers, the temp area header has:
 * <ul>
 *   <li>An interactive cell slot (insert/extract cells by clicking/shift-clicking)</li>
 *   <li>A "Send" button (transfers cell to selected network)</li>
 *   <li>No expand/collapse button (content visibility depends on cell presence)</li>
 *   <li>No priority field or location string</li>
 * </ul>
 *
 * The icon position at {@link GuiConstants#GUI_INDENT} becomes a cell slot with
 * background and hover highlight, supporting cell insertion and extraction.
 * <p>
 * The name is vertically centered (y+5) instead of top-aligned (y+1) since
 * there is no location text below.
 *
 * @see AbstractHeader
 * @see StorageHeader
 */
public class TempAreaHeader extends AbstractHeader {

    // Send button layout
    public static final int SEND_BUTTON_X = 150;
    private static final int SEND_BUTTON_Y_OFFSET = 2;
    private static final int SEND_BUTTON_WIDTH = 28;
    private static final int SEND_BUTTON_HEIGHT = 12;

    // Send button colors
    private static final int COLOR_SEND_BUTTON = 0xFF5599CC;
    private static final int COLOR_SEND_BUTTON_HOVER = 0xFF77BBEE;

    // Name width limit (before send button area, leaves 4px gap)
    // HEADER_NAME_X = GUI_INDENT + 20 = 42, so max name ends at 42 + 104 = 146
    private static final int MAX_NAME_WIDTH = SEND_BUTTON_X - GuiConstants.HEADER_NAME_X - 4;

    private static final int SIZE = GuiConstants.MINI_SLOT_SIZE;

    /** Supplier for whether a cell is inserted in this slot */
    private Supplier<Boolean> hasCellSupplier;

    /** Callback when the cell slot is clicked (insert/extract) */
    private CellSlotClickCallback cellSlotCallback;

    /** Callback when the send button is clicked */
    private Runnable onSendClick;

    // Hover state
    private boolean cellSlotHovered = false;
    private boolean sendButtonHovered = false;

    /**
     * Callback for cell slot interactions.
     */
    @FunctionalInterface
    public interface CellSlotClickCallback {
        void onCellSlotClicked(int mouseButton);
    }

    public TempAreaHeader(int y, FontRenderer fontRenderer, RenderItem itemRender) {
        super(y, fontRenderer, itemRender);
        this.nameMaxWidth = MAX_NAME_WIDTH;
    }

    // ---- Configuration ----

    public void setHasCellSupplier(Supplier<Boolean> supplier) {
        this.hasCellSupplier = supplier;
    }

    public void setCellSlotCallback(CellSlotClickCallback callback) {
        this.cellSlotCallback = callback;
    }

    public void setOnSendClick(Runnable callback) {
        this.onSendClick = callback;
    }

    // ---- Rendering ----

    @Override
    protected int drawHeaderContent(int mouseX, int mouseY) {
        cellSlotHovered = false;
        sendButtonHovered = false;

        // Draw upgrade cards
        if (cardsDisplay != null) cardsDisplay.draw(mouseX, mouseY);

        // Draw send button if a cell is present
        boolean hasCell = hasCellSupplier != null && hasCellSupplier.get();
        if (hasCell) drawSendButton(mouseX, mouseY);

        // Return hover right bound (the entire row before send button)
        return hasCell ? SEND_BUTTON_X : GuiConstants.CONTENT_RIGHT_EDGE;
    }

    /**
     * Override icon drawing to render an interactive cell slot instead.
     * The cell slot has a background, hover highlight, and supports click interactions.
     */
    @Override
    protected void drawIcon() {
        int slotX = GuiConstants.GUI_INDENT;

        // Draw slot background
        drawSlotBackground(slotX, y);

        // Draw cell item if present
        ItemStack icon = iconSupplier != null ? iconSupplier.get() : ItemStack.EMPTY;
        if (!icon.isEmpty()) renderItemStack(icon, slotX, y);
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        nameHovered = false;
        headerHovered = false;

        // Draw horizontal separator line at the top of the header (between cells)
        Gui.drawRect(GuiConstants.GUI_INDENT, y - 1, GuiConstants.CONTENT_RIGHT_EDGE, y,
            GuiConstants.COLOR_SEPARATOR);

        // Draw selection background (below everything else)
        boolean isSelected = selectedSupplier != null && selectedSupplier.get();
        if (isSelected) {
            Gui.drawRect(GuiConstants.GUI_INDENT, y, GuiConstants.CONTENT_RIGHT_EDGE,
                y + GuiConstants.ROW_HEIGHT, GuiConstants.COLOR_SELECTION);
        }

        // Subclass-specific drawing
        int hoverRightBound = drawHeaderContent(mouseX, mouseY);

        // Header hover highlight
        headerHovered = mouseX >= GuiConstants.GUI_INDENT && mouseX < hoverRightBound
            && mouseY >= y && mouseY < y + GuiConstants.ROW_HEIGHT;
        if (headerHovered) {
            Gui.drawRect(GuiConstants.GUI_INDENT, y, hoverRightBound, y + GuiConstants.ROW_HEIGHT,
                GuiConstants.COLOR_STORAGE_HEADER_HOVER);
        }

        // Draw cell slot (instead of plain icon)
        drawIcon();

        // Check cell slot hover
        checkCellSlotHover(mouseX, mouseY);

        // Draw name (vertically centered since no location text)
        drawTempName(mouseX, mouseY);

        // Draw tree connector if content follows
        if (drawConnector) {
            Gui.drawRect(TREE_LINE_X, y + GuiConstants.HEADER_CONNECTOR_Y_OFFSET,
                TREE_LINE_X + 1, y + GuiConstants.ROW_HEIGHT,
                GuiConstants.COLOR_TREE_LINE);
        }
    }

    /**
     * Check if the cell slot is hovered and draw highlight.
     */
    private void checkCellSlotHover(int mouseX, int mouseY) {
        int slotX = GuiConstants.GUI_INDENT;
        if (mouseX >= slotX && mouseX < slotX + SIZE && mouseY >= y && mouseY < y + SIZE) {
            cellSlotHovered = true;
            drawSlotHoverHighlight(slotX, y);
        }
    }

    /**
     * Draw the temp area name with vertical centering (y+5 instead of y+1).
     */
    private void drawTempName(int mouseX, int mouseY) {
        boolean hasCell = hasCellSupplier != null && hasCellSupplier.get();
        String name;

        if (hasCell) {
            name = nameSupplier != null ? nameSupplier.get() : "";
        } else {
            name = I18n.format("gui.cellterminal.temp_area.drop_cell");
        }

        if (name.isEmpty()) return;

        String displayName = trimTextToWidth(name, nameMaxWidth);

        boolean isSelected = selectedSupplier != null && selectedSupplier.get();
        boolean hasCustomName = hasCustomNameSupplier != null && hasCustomNameSupplier.get();
        int nameColor;
        if (isSelected) {
            nameColor = GuiConstants.COLOR_NAME_SELECTED;
        } else if (!hasCell) {
            nameColor = GuiConstants.COLOR_TEXT_PLACEHOLDER;
        } else if (hasCustomName) {
            nameColor = GuiConstants.COLOR_CUSTOM_NAME;
        } else {
            nameColor = GuiConstants.COLOR_TEXT_NORMAL;
        }

        fontRenderer.drawString(displayName, GuiConstants.HEADER_NAME_X, y + 5, nameColor);

        // Name hover only makes sense when a cell is present (for rename interaction)
        if (!hasCell) return;

        // Use full name area width for easier click targeting (not just actual text width)
        if (mouseX >= GuiConstants.HEADER_NAME_X && mouseX < GuiConstants.HEADER_NAME_X + nameMaxWidth
            && mouseY >= y + 5 && mouseY < y + 14) {
            nameHovered = true;
        }
    }

    /**
     * Draw the "Send" button with 3D border effect.
     */
    private void drawSendButton(int mouseX, int mouseY) {
        int btnX = SEND_BUTTON_X;
        int btnY = y + SEND_BUTTON_Y_OFFSET;

        sendButtonHovered = mouseX >= btnX && mouseX < btnX + SEND_BUTTON_WIDTH
            && mouseY >= btnY && mouseY < btnY + SEND_BUTTON_HEIGHT;

        int btnColor = sendButtonHovered ? COLOR_SEND_BUTTON_HOVER : COLOR_SEND_BUTTON;
        Gui.drawRect(btnX, btnY, btnX + SEND_BUTTON_WIDTH, btnY + SEND_BUTTON_HEIGHT, btnColor);

        // 3D border effect
        Gui.drawRect(btnX, btnY, btnX + SEND_BUTTON_WIDTH, btnY + 1,
            GuiConstants.COLOR_BUTTON_HIGHLIGHT);
        Gui.drawRect(btnX, btnY, btnX + 1, btnY + SEND_BUTTON_HEIGHT,
            GuiConstants.COLOR_BUTTON_HIGHLIGHT);
        Gui.drawRect(btnX, btnY + SEND_BUTTON_HEIGHT - 1, btnX + SEND_BUTTON_WIDTH, btnY + SEND_BUTTON_HEIGHT,
            GuiConstants.COLOR_BUTTON_SHADOW);
        Gui.drawRect(btnX + SEND_BUTTON_WIDTH - 1, btnY, btnX + SEND_BUTTON_WIDTH, btnY + SEND_BUTTON_HEIGHT,
            GuiConstants.COLOR_BUTTON_SHADOW);

        // Button text (centered)
        String sendText = I18n.format("gui.cellterminal.temp_area.send");
        int textX = btnX + (SEND_BUTTON_WIDTH - fontRenderer.getStringWidth(sendText)) / 2;
        fontRenderer.drawString(sendText, textX, btnY + 2, 0x000000);
    }

    // ---- Click handling ----

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible) return false;

        // Send button click (left-click only)
        if (button == 0 && sendButtonHovered && onSendClick != null) {
            onSendClick.run();
            return true;
        }

        // Cell slot click (supports left and right click)
        if (cellSlotHovered && cellSlotCallback != null) {
            cellSlotCallback.onCellSlotClicked(button);
            return true;
        }

        // Name click for rename, cards click, and header selection for quick-add (from base)
        return super.handleClick(mouseX, mouseY, button);
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) return Collections.emptyList();

        // Send button tooltip
        if (sendButtonHovered) {
            return Collections.singletonList(I18n.format("gui.cellterminal.temp_area.send.tooltip"));
        }

        // Cards tooltip (from base)
        return super.getTooltip(mouseX, mouseY);
    }

    @Override
    public ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) return ItemStack.EMPTY;

        // Cell slot hover provides the cell item tooltip
        if (cellSlotHovered) {
            ItemStack icon = iconSupplier != null ? iconSupplier.get() : ItemStack.EMPTY;
            if (!icon.isEmpty()) return icon;
        }

        return ItemStack.EMPTY;
    }

    // ---- Slot rendering helpers ----

    private void drawSlotBackground(int slotX, int slotY) {
        // Mini slot: left half of slot (y uv 0-15)
        int texX = GuiConstants.MINI_SLOT_X;
        int texY = GuiConstants.MINI_SLOT_Y;
        GuiConstants.drawAtlasSprite(slotX, slotY, texX, texY, SIZE, SIZE);
    }

    private void drawSlotHoverHighlight(int slotX, int slotY) {
        Gui.drawRect(slotX, slotY, slotX + SIZE, slotY + SIZE, GuiConstants.COLOR_HOVER_HIGHLIGHT);
    }
}
