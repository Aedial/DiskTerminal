package com.cellterminal.gui.storagebus;

import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import appeng.api.config.AccessRestriction;
import appeng.util.ReadableNumberConverter;

import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.gui.ComparisonUtils;
import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.render.RenderContext;


/**
 * Handles rendering of storage bus slots, content items, and partition slots.
 *
 * This class provides low-level drawing operations for storage bus-related GUI elements.
 * It extends cell slot rendering with storage bus-specific features like IO mode indicators.
 */
public class StorageBusSlotRenderer {

    private final FontRenderer fontRenderer;
    private final RenderItem itemRender;

    public StorageBusSlotRenderer(FontRenderer fontRenderer, RenderItem itemRender) {
        this.fontRenderer = fontRenderer;
        this.itemRender = itemRender;
    }

    /**
     * Draw a standard slot background with 3D borders.
     */
    public void drawSlotBackground(int x, int y) {
        int size = GuiConstants.MINI_SLOT_SIZE;
        Gui.drawRect(x, y, x + size, y + size, GuiConstants.COLOR_SLOT_BACKGROUND);
        Gui.drawRect(x, y, x + size - 1, y + 1, GuiConstants.COLOR_SLOT_BORDER_DARK);
        Gui.drawRect(x, y, x + 1, y + size - 1, GuiConstants.COLOR_SLOT_BORDER_DARK);
        Gui.drawRect(x + 1, y + size - 1, x + size, y + size, GuiConstants.COLOR_SLOT_BORDER_LIGHT);
        Gui.drawRect(x + size - 1, y + 1, x + size, y + size - 1, GuiConstants.COLOR_SLOT_BORDER_LIGHT);
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
     * Draw storage bus upgrade icons on the header row.
     *
     * @param storageBus The storage bus info
     * @param x X position to start
     * @param y Y position of the row
     * @return Width consumed by upgrade icons
     */
    public int drawUpgradeIcons(StorageBusInfo storageBus, int x, int y) {
        return drawUpgradeIcons(storageBus, x, y, null, 0, 0);
    }

    /**
     * Draw storage bus upgrade icons on the header row, with hover tracking.
     *
     * @param storageBus The storage bus info
     * @param x X position to start (relative to GUI)
     * @param y Y position of the row (relative to GUI)
     * @param ctx Optional render context for tracking upgrade icon positions
     * @param guiLeft GUI left offset for absolute position calculation
     * @param guiTop GUI top offset for absolute position calculation
     * @return Width consumed by upgrade icons
     */
    public int drawUpgradeIcons(StorageBusInfo storageBus, int x, int y, RenderContext ctx, int guiLeft, int guiTop) {
        List<ItemStack> upgrades = storageBus.getUpgrades();
        if (upgrades.isEmpty()) return 0;

        int iconX = x;

        for (int i = 0; i < upgrades.size(); i++) {
            ItemStack upgrade = upgrades.get(i);
            renderSmallItemStack(upgrade, iconX, y);

            // Track upgrade icon position for tooltip and click handling
            // Use actual slot index from the upgrade inventory, not the iteration index
            if (ctx != null) {
                int actualSlotIndex = storageBus.getUpgradeSlotIndex(i);
                ctx.upgradeIconTargets.add(new RenderContext.UpgradeIconTarget(
                    storageBus, upgrade, actualSlotIndex, guiLeft + iconX, guiTop + y));
            }

            iconX += 9; // 8px icon + 1px spacing
        }

        return iconX - x;
    }

    /**
     * Draw a small action button (IO mode, clear, partition-all).
     */
    public void drawSmallButton(int x, int y, boolean hovered, int fillColor) {
        int size = GuiConstants.SMALL_BUTTON_SIZE;
        int btnColor = hovered ? GuiConstants.COLOR_BUTTON_HOVER : GuiConstants.COLOR_BUTTON_NORMAL;

        Gui.drawRect(x, y, x + size, y + size, btnColor);
        Gui.drawRect(x, y, x + size, y + 1, GuiConstants.COLOR_BUTTON_HIGHLIGHT);
        Gui.drawRect(x, y, x + 1, y + size, GuiConstants.COLOR_BUTTON_HIGHLIGHT);
        Gui.drawRect(x, y + size - 1, x + size, y + size, GuiConstants.COLOR_BUTTON_SHADOW);
        Gui.drawRect(x + size - 1, y, x + size, y + size, GuiConstants.COLOR_BUTTON_SHADOW);

        if (fillColor != 0) Gui.drawRect(x + 1, y + 1, x + size - 1, y + size - 1, fillColor);
    }

    /**
     * Draw IO mode colored dot inside a button area.
     *
     * @param x Left edge of dot area
     * @param y Top edge of dot area
     * @param size Size of dot area
     * @param accessRestriction Current access mode
     */
    public void drawIOModeDot(int x, int y, int size, AccessRestriction accessRestriction) {
        int color;

        switch (accessRestriction) {
            case READ:
                color = 0xFF55FF55;  // Green for read-only
                break;
            case WRITE:
                color = 0xFFFF5555;  // Red for write-only
                break;
            case READ_WRITE:
            default:
                color = 0xFF5555FF;  // Blue for read-write
                break;
        }

        Gui.drawRect(x, y, x + size, y + size, color);
    }

    /**
     * Format item count for display.
     */
    public String formatItemCount(long count) {
        if (count < 1000) return String.valueOf(count);

        return ReadableNumberConverter.INSTANCE.toWideReadableForm(count);
    }

    /**
     * Check if an item is in the partition list.
     * Uses fluid-aware comparison for fluid items (compares by fluid type only).
     */
    public boolean isInPartition(ItemStack stack, List<ItemStack> partition) {
        return ComparisonUtils.isInPartition(stack, partition);
    }
}
