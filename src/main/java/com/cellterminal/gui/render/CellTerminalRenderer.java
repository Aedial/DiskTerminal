package com.cellterminal.gui.render;

import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import appeng.util.ReadableNumberConverter;

import com.cellterminal.client.CellContentRow;
import com.cellterminal.client.CellInfo;
import com.cellterminal.client.EmptySlotInfo;
import com.cellterminal.client.StorageInfo;


/**
 * Base renderer class for Cell Terminal tabs.
 * Provides common drawing utilities shared across all tab renderers.
 */
public abstract class CellTerminalRenderer {

    protected static final int ROW_HEIGHT = 18;
    protected static final int GUI_INDENT = 22;
    protected static final int CELL_INDENT = GUI_INDENT + 12;
    protected static final int SLOTS_PER_ROW = 8;
    protected static final int SLOTS_PER_ROW_BUS = 9;

    protected final FontRenderer fontRenderer;
    protected final RenderItem itemRender;

    public CellTerminalRenderer(FontRenderer fontRenderer, RenderItem itemRender) {
        this.fontRenderer = fontRenderer;
        this.itemRender = itemRender;
    }

    /**
     * Draw a standard slot background.
     */
    protected void drawSlotBackground(int x, int y) {
        Gui.drawRect(x, y, x + 16, y + 16, 0xFF8B8B8B);
        Gui.drawRect(x, y, x + 15, y + 1, 0xFF373737);
        Gui.drawRect(x, y, x + 1, y + 15, 0xFF373737);
        Gui.drawRect(x + 1, y + 15, x + 16, y + 16, 0xFFFFFFFF);
        Gui.drawRect(x + 15, y + 1, x + 16, y + 15, 0xFFFFFFFF);
    }

    /**
     * Draw a button with 3D effect.
     */
    protected void drawButton(int x, int y, int size, String label, boolean hovered) {
        int btnColor = hovered ? 0xFF707070 : 0xFF8B8B8B;
        Gui.drawRect(x, y, x + size, y + size, btnColor);
        Gui.drawRect(x, y, x + size, y + 1, 0xFFFFFFFF);
        Gui.drawRect(x, y, x + 1, y + size, 0xFFFFFFFF);
        Gui.drawRect(x, y + size - 1, x + size, y + size, 0xFF555555);
        Gui.drawRect(x + size - 1, y, x + size, y + size, 0xFF555555);
        fontRenderer.drawString(label, x + 4, y + 3, 0x404040);
    }

    /**
     * Format item count for display.
     */
    protected String formatItemCount(long count) {
        if (count <= 1) return "";
        if (count < 1000) return String.valueOf(count);

        return ReadableNumberConverter.INSTANCE.toSlimReadableForm(count);
    }

    /**
     * Get the usage bar color based on percentage.
     */
    protected int getUsageColor(float percent) {
        if (percent > 0.9f) return 0xFFFF3333;
        if (percent > 0.75f) return 0xFFFFAA00;

        return 0xFF33FF33;
    }

    /**
     * Get the number of formatting code characters (§X pairs) in a string.
     */
    protected int getDecorationLength(String name) {
        int decorLength = 0;

        for (int i = 0; i < name.length() - 1; i++) {
            if (name.charAt(i) == '§') {
                decorLength += 2;
                i++;
            }
        }

        return decorLength;
    }

    /**
     * Render an item stack at the given position.
     */
    protected void renderItemStack(ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Render a small (8x8) item icon at the given position.
     */
    protected void renderSmallItemStack(ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
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

    /**
     * Draw upgrade icons for a cell.
     * Icons are drawn at the left side of the cell entry, using small 8x8 icons.
     * @param cell The cell info
     * @param x The x position to start drawing (left edge)
     * @param y The y position of the row
     * @return The width consumed by upgrade icons
     */
    protected int drawCellUpgradeIcons(CellInfo cell, int x, int y) {
        List<ItemStack> upgrades = cell.getUpgrades();
        if (upgrades.isEmpty()) return 0;

        int iconX = x;
        int iconY = y; // Align with top of cell icon

        for (ItemStack upgrade : upgrades) {
            renderSmallItemStack(upgrade, iconX, iconY);
            iconX += 9; // 8px icon + 1px spacing
        }

        return iconX - x;
    }

    /**
     * Check if the line at the given index is the first in its storage group.
     */
    protected boolean isFirstInStorageGroup(List<Object> lines, int index) {
        if (index <= 0) return true;

        return lines.get(index - 1) instanceof StorageInfo;
    }

    /**
     * Check if the line at the given index is the last in its storage group.
     * For multi-row cells, this returns true for ALL rows of the last cell.
     */
    protected boolean isLastInStorageGroup(List<Object> lines, int index) {
        if (index >= lines.size() - 1) return true;

        // Look ahead to find if there are any more cells after all rows of current cell
        for (int i = index + 1; i < lines.size(); i++) {
            Object line = lines.get(i);

                // Hit the next storage, so current cell is last in group
            if (line instanceof StorageInfo) return true;

            if (line instanceof CellContentRow) {
                CellContentRow row = (CellContentRow) line;
                // If this is a first row, it's a different cell - current is last
                if (row.isFirstRow()) return false;
                // Otherwise it's a continuation row of the same cell, keep looking
            } else if (line instanceof EmptySlotInfo) {
                // Empty slot is a different entry - current is not last
                return false;
            }
        }

        // Reached end of list
        return true;
    }

    /**
     * Draw tree lines connecting cells to their storage parent.
     * @param lineX The x position of the vertical line
     * @param y The y position of this row
     * @param isFirstRow Whether this is the first row for this cell
     * @param isFirstInGroup Whether this is the first cell in the storage group
     * @param isLastInGroup Whether this is the last cell in the storage group
     * @param visibleTop The top y of the visible area
     * @param visibleBottom The bottom y of the visible area
     * @param isFirstVisibleRow Whether this is the first visible row
     * @param isLastVisibleRow Whether this is the last visible row
     * @param hasContentAbove Whether there's content above that's scrolled out
     * @param hasContentBelow Whether there's content below that's scrolled out
     * @param allBranches Whether to draw a horizontal branch for every row (not just first)
     */
    protected void drawTreeLines(int lineX, int y, boolean isFirstRow, boolean isFirstInGroup,
            boolean isLastInGroup, int visibleTop, int visibleBottom,
            boolean isFirstVisibleRow, boolean isLastVisibleRow,
            boolean hasContentAbove, boolean hasContentBelow,
            boolean allBranches) {

        int lineTop;
        if (isFirstRow) {
            if (isFirstInGroup) {
                // First row in group (right after header) - extend up to connect with header's segment
                lineTop = y - 3;
            } else if (isFirstVisibleRow && hasContentAbove) {
                // First visible row with content above scrolled out -
                // clamp to visibleTop to avoid leaking above GUI
                lineTop = visibleTop;
            } else {
                // Connect to row above but don't extend too high (avoid overlapping buttons/icons)
                lineTop = y - 4;
            }
        } else {
            // Connect to row above with minimal overlap
            lineTop = isFirstVisibleRow && hasContentAbove ? visibleTop : y - 4;
        }

        // Clamp lineTop to never go above visibleTop to prevent leak above GUI
        if (lineTop < visibleTop) lineTop = visibleTop;

        int lineBottom;
        if (isLastInGroup) {
            lineBottom = y + 9;
        } else if (isLastVisibleRow && hasContentBelow) {
            lineBottom = visibleBottom;
        } else {
            lineBottom = y + ROW_HEIGHT;
        }

        // Vertical line
        Gui.drawRect(lineX, lineTop, lineX + 1, lineBottom, 0xFF808080);

        // Horizontal branch (only on first row unless all branches are enabled)
        if (allBranches) {
            Gui.drawRect(lineX, y + 8, lineX + 10, y + 9, 0xFF808080);
        } else if (isFirstRow) {
            Gui.drawRect(lineX, y + 8, lineX + 10, y + 9, 0xFF808080);
        }
    }

    /**
     * Check if an item is in the partition list.
     */
    protected boolean isInPartition(ItemStack stack, List<ItemStack> partition) {
        if (stack.isEmpty()) return false;

        for (ItemStack partItem : partition) {
            if (ItemStack.areItemsEqual(stack, partItem)) return true;
        }

        return false;
    }
}
