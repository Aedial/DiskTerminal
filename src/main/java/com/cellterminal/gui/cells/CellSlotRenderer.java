package com.cellterminal.gui.cells;

import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import appeng.util.ReadableNumberConverter;

import com.cellterminal.client.CellInfo;
import com.cellterminal.gui.FluidStackUtil;
import com.cellterminal.gui.GuiConstants;


/**
 * Handles rendering of cell slots, content items, and partition slots.
 *
 * This class provides low-level drawing operations for cell-related GUI elements.
 * It does not handle hover detection or click events - those are handled by
 * the tab renderers and click handlers.
 */
public class CellSlotRenderer {

    private final FontRenderer fontRenderer;
    private final RenderItem itemRender;

    public CellSlotRenderer(FontRenderer fontRenderer, RenderItem itemRender) {
        this.fontRenderer = fontRenderer;
        this.itemRender = itemRender;
    }

    /**
     * Draw a standard slot background with 3D borders.
     */
    public void drawSlotBackground(int x, int y) {
        drawSlotBackground(x, y, GuiConstants.MINI_SLOT_SIZE, GuiConstants.MINI_SLOT_SIZE);
    }

    /**
     * Draw a slot background with custom dimensions.
     */
    public void drawSlotBackground(int x, int y, int width, int height) {
        Gui.drawRect(x, y, x + width, y + height, GuiConstants.COLOR_SLOT_BACKGROUND);
        Gui.drawRect(x, y, x + width - 1, y + 1, GuiConstants.COLOR_SLOT_BORDER_DARK);
        Gui.drawRect(x, y, x + 1, y + height - 1, GuiConstants.COLOR_SLOT_BORDER_DARK);
        Gui.drawRect(x + 1, y + height - 1, x + width, y + height, GuiConstants.COLOR_SLOT_BORDER_LIGHT);
        Gui.drawRect(x + width - 1, y + 1, x + width, y + height - 1, GuiConstants.COLOR_SLOT_BORDER_LIGHT);
    }

    /**
     * Draw a partition slot background with amber tint.
     */
    public void drawPartitionSlotBackground(int x, int y) {
        drawSlotBackground(x, y);
        int inner = GuiConstants.MINI_SLOT_SIZE - 1;
        Gui.drawRect(x + 1, y + 1, x + inner, y + inner, GuiConstants.COLOR_PARTITION_SLOT_TINT);
    }

    /**
     * Draw a hover highlight over a slot.
     */
    public void drawSlotHoverHighlight(int x, int y) {
        int inner = GuiConstants.MINI_SLOT_SIZE - 1;
        Gui.drawRect(x + 1, y + 1, x + inner, y + inner, GuiConstants.COLOR_HOVER_HIGHLIGHT);
    }

    /**
     * Render an item stack at the given position.
     */
    public void renderItemStack(ItemStack stack, int x, int y) {
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
    public void renderSmallItemStack(ItemStack stack, int x, int y) {
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
     * Draw item count in the slot (bottom-right, small text).
     */
    public void drawItemCount(long count, int slotX, int slotY) {
        String countStr = formatItemCount(count);
        if (countStr.isEmpty()) return;

        int countWidth = fontRenderer.getStringWidth(countStr);
        int textX = slotX + GuiConstants.MINI_SLOT_SIZE - 1;
        int textY = slotY + GuiConstants.MINI_SLOT_SIZE - 5;

        GlStateManager.disableDepth();
        GlStateManager.pushMatrix();
        GlStateManager.scale(0.5f, 0.5f, 0.5f);
        fontRenderer.drawStringWithShadow(countStr, textX * 2 - countWidth, textY * 2, 0xFFFFFF);
        GlStateManager.popMatrix();
        GlStateManager.enableDepth();
    }

    /**
     * Draw partition indicator ("P") in the top-left corner.
     */
    public void drawPartitionIndicator(int slotX, int slotY) {
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.pushMatrix();
        GlStateManager.scale(0.5f, 0.5f, 0.5f);
        fontRenderer.drawStringWithShadow("P", (slotX + 1) * 2, (slotY + 1) * 2, GuiConstants.COLOR_PARTITION_INDICATOR);
        GlStateManager.popMatrix();
        GlStateManager.enableDepth();
    }

    /**
     * Draw cell upgrade icons to the left of the cell slot.
     *
     * @param cell The cell info
     * @param x The x position to start drawing
     * @param y The y position of the row
     * @return The width consumed by upgrade icons
     */
    public int drawCellUpgradeIcons(CellInfo cell, int x, int y) {
        List<ItemStack> upgrades = cell.getUpgrades();
        if (upgrades.isEmpty()) return 0;

        int iconX = x;
        for (ItemStack upgrade : upgrades) {
            renderSmallItemStack(upgrade, iconX, y);
            iconX += 9; // 8px icon + 1px spacing
        }

        return iconX - x;
    }

    /**
     * Draw a button with 3D effect.
     */
    public void drawButton(int x, int y, int size, String label, boolean hovered) {
        int btnColor = hovered ? GuiConstants.COLOR_BUTTON_HOVER : GuiConstants.COLOR_BUTTON_NORMAL;
        Gui.drawRect(x, y, x + size, y + size, btnColor);
        Gui.drawRect(x, y, x + size, y + 1, GuiConstants.COLOR_BUTTON_HIGHLIGHT);
        Gui.drawRect(x, y, x + 1, y + size, GuiConstants.COLOR_BUTTON_HIGHLIGHT);
        Gui.drawRect(x, y + size - 1, x + size, y + size, GuiConstants.COLOR_BUTTON_SHADOW);
        Gui.drawRect(x + size - 1, y, x + size, y + size, GuiConstants.COLOR_BUTTON_SHADOW);
        fontRenderer.drawString(label, x + 4, y + 3, GuiConstants.COLOR_TEXT_NORMAL);
    }

    /**
     * Draw a small action button (partition-all, clear, etc.).
     */
    public void drawSmallButton(int x, int y, boolean hovered, int fillColor) {
        int size = GuiConstants.SMALL_BUTTON_SIZE;
        int btnColor = hovered ? GuiConstants.COLOR_BUTTON_HOVER : GuiConstants.COLOR_BUTTON_NORMAL;

        // Button background
        Gui.drawRect(x, y, x + size, y + size, btnColor);

        // 3D borders
        Gui.drawRect(x, y, x + size, y + 1, GuiConstants.COLOR_BUTTON_HIGHLIGHT);
        Gui.drawRect(x, y, x + 1, y + size, GuiConstants.COLOR_BUTTON_HIGHLIGHT);
        Gui.drawRect(x, y + size - 1, x + size, y + size, GuiConstants.COLOR_BUTTON_SHADOW);
        Gui.drawRect(x + size - 1, y, x + size, y + size, GuiConstants.COLOR_BUTTON_SHADOW);

        // Colored fill
        if (fillColor != 0) Gui.drawRect(x + 1, y + 1, x + size - 1, y + size - 1, fillColor);
    }

    /**
     * Format item count for display.
     */
    public String formatItemCount(long count) {
        if (count < 1000) return String.valueOf(count);

        return ReadableNumberConverter.INSTANCE.toSlimReadableForm(count);
    }

    /**
     * Check if an item is in the partition list.
     * Uses fluid-aware comparison for fluid items (compares by fluid type only).
     */
    public boolean isInPartition(ItemStack stack, List<ItemStack> partition) {
        return FluidStackUtil.isInPartition(stack, partition);
    }

    /**
     * Get the usage bar color based on percentage.
     */
    public int getUsageColor(float percent) {
        if (percent > 0.9f) return GuiConstants.COLOR_USAGE_HIGH;
        if (percent > 0.75f) return GuiConstants.COLOR_USAGE_MEDIUM;

        return GuiConstants.COLOR_USAGE_LOW;
    }
}
