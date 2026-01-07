package com.diskterminal.gui;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

import appeng.core.AEConfig;
import appeng.util.ReadableNumberConverter;

import com.diskterminal.client.DiskInfo;
import com.diskterminal.client.StorageInfo;


/**
 * Popup overlay for viewing disk inventory contents.
 * Read-only view with double-click to toggle partition status.
 */
public class PopupDiskInventory extends Gui {

    private static final int SLOTS_PER_ROW = 9;
    private static final int MAX_ROWS = 7;
    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 8;
    private static final int HEADER_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 14;
    private static final int FOOTER_HEIGHT = 12;

    private final GuiScreen parent;
    private final DiskInfo disk;
    private final long storageId;
    private final int diskSlot;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int slotOffsetX;

    // Button for set/unset all partition
    private int partitionButtonX;
    private int partitionButtonY;
    private int partitionButtonWidth;
    private boolean partitionAllHovered = false;

    // Hovered item for tooltip
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private int hoveredX = 0;
    private int hoveredY = 0;

    public PopupDiskInventory(GuiScreen parent, DiskInfo disk, int mouseX, int mouseY) {
        this.parent = parent;
        this.disk = disk;
        this.storageId = disk.getParentStorageId();
        this.diskSlot = disk.getSlot();

        int contentRows = Math.min(MAX_ROWS, (disk.getContents().size() + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW);
        if (contentRows == 0) contentRows = 1;

        // Calculate width based on title or slots, whichever is wider
        Minecraft mc = Minecraft.getMinecraft();
        String title = disk.getDisplayName() + " - Contents";
        int titleWidth = mc.fontRenderer.getStringWidth(title) + PADDING * 2;
        int slotsWidth = SLOTS_PER_ROW * SLOT_SIZE + PADDING * 2;
        this.width = Math.max(titleWidth, slotsWidth);
        this.height = HEADER_HEIGHT + BUTTON_HEIGHT + 4 + contentRows * SLOT_SIZE + FOOTER_HEIGHT;

        // Calculate slot area offset to center slots within modal
        int slotAreaWidth = SLOTS_PER_ROW * SLOT_SIZE;
        this.slotOffsetX = (this.width - slotAreaWidth) / 2;

        // Center on screen using scaled resolution
        ScaledResolution sr = new ScaledResolution(mc);
        this.x = (sr.getScaledWidth() - this.width) / 2;
        this.y = (sr.getScaledHeight() - this.height) / 2;

        this.partitionButtonX = this.x + PADDING;
        this.partitionButtonY = this.y + HEADER_HEIGHT;
        this.partitionButtonWidth = this.width - PADDING * 2;
    }

    public void draw(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRenderer;

        // Reset hovered state
        hoveredStack = ItemStack.EMPTY;

        // Reset GL state to known good state before drawing
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Draw popup background (similar to vanilla container style)
        drawRect(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF000000);
        drawGradientRect(x, y, x + width, y + height, 0xFFC6C6C6, 0xFFC6C6C6);

        // Draw border highlights
        drawRect(x, y, x + width, y + 1, 0xFFFFFFFF);
        drawRect(x, y, x + 1, y + height, 0xFFFFFFFF);
        drawRect(x, y + height - 1, x + width, y + height, 0xFF555555);
        drawRect(x + width - 1, y, x + width, y + height, 0xFF555555);

        // Draw title
        String title = disk.getDisplayName() + " - Contents";
        fr.drawString(title, x + PADDING, y + 6, 0x404040);

        // Draw partition all button
        partitionAllHovered = mouseX >= partitionButtonX && mouseX < partitionButtonX + partitionButtonWidth
            && mouseY >= partitionButtonY && mouseY < partitionButtonY + BUTTON_HEIGHT;

        int buttonColor = partitionAllHovered ? 0xFF707070 : 0xFF8B8B8B;
        drawRect(partitionButtonX, partitionButtonY, partitionButtonX + partitionButtonWidth, partitionButtonY + BUTTON_HEIGHT, buttonColor);
        drawRect(partitionButtonX, partitionButtonY, partitionButtonX + partitionButtonWidth, partitionButtonY + 1, 0xFFFFFFFF);
        drawRect(partitionButtonX, partitionButtonY, partitionButtonX + 1, partitionButtonY + BUTTON_HEIGHT, 0xFFFFFFFF);
        drawRect(partitionButtonX, partitionButtonY + BUTTON_HEIGHT - 1, partitionButtonX + partitionButtonWidth, partitionButtonY + BUTTON_HEIGHT, 0xFF555555);
        drawRect(partitionButtonX + partitionButtonWidth - 1, partitionButtonY, partitionButtonX + partitionButtonWidth, partitionButtonY + BUTTON_HEIGHT, 0xFF555555);

        // FIXME: localize
        String buttonText = "Set All to Partition";
        int textWidth = fr.getStringWidth(buttonText);
        fr.drawString(buttonText, partitionButtonX + (partitionButtonWidth - textWidth) / 2, partitionButtonY + 3, 0x404040);

        // Draw item slots
        int slotStartY = y + HEADER_HEIGHT + BUTTON_HEIGHT + 4;
        List<ItemStack> contents = disk.getContents();
        List<ItemStack> partition = getCurrentPartition();

        for (int i = 0; i < contents.size() && i < MAX_ROWS * SLOTS_PER_ROW; i++) {
            int slotX = x + slotOffsetX + (i % SLOTS_PER_ROW) * SLOT_SIZE;
            int slotY = slotStartY + (i / SLOTS_PER_ROW) * SLOT_SIZE;

            ItemStack stack = contents.get(i);

            // Check if item is in partition
            boolean inPartition = isInPartition(stack, partition);

            // Draw slot background
            int slotBgColor = 0xFF8B8B8B;
            boolean slotHovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE - 1
                && mouseY >= slotY && mouseY < slotY + SLOT_SIZE - 1;

            drawRect(slotX, slotY, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, slotBgColor);

            // Draw slot border (3D effect)
            drawRect(slotX, slotY, slotX + SLOT_SIZE - 1, slotY + 1, 0xFF373737);
            drawRect(slotX, slotY, slotX + 1, slotY + SLOT_SIZE - 1, 0xFF373737);
            drawRect(slotX, slotY + SLOT_SIZE - 2, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, 0xFFFFFFFF);
            drawRect(slotX + SLOT_SIZE - 2, slotY, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, 0xFFFFFFFF);

            // Draw item
            if (!stack.isEmpty()) {
                GlStateManager.enableDepth();
                RenderHelper.enableGUIStandardItemLighting();
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack, slotX, slotY);
                RenderHelper.disableStandardItemLighting();

                // Draw count like AE2 terminal
                String countStr = formatItemCount(stack.getCount());
                GlStateManager.disableLighting();
                GlStateManager.disableDepth();
                int countWidth = fr.getStringWidth(countStr);

                GlStateManager.pushMatrix();
                GlStateManager.scale(0.5f, 0.5f, 0.5f);
                fr.drawStringWithShadow(countStr, (slotX + 17 - countWidth) * 2, (slotY + 14) * 2, 0xFFFFFF);
                GlStateManager.popMatrix();

                // Draw partition indicator in top-left corner if in partition
                if (inPartition) {
                    // TODO: localize
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(0.5f, 0.5f, 0.5f);
                    fr.drawStringWithShadow("P", (slotX + 1) * 2, (slotY + 1) * 2, 0xFF55FF55);
                    GlStateManager.popMatrix();
                }

                // Track hovered item for tooltip
                if (slotHovered) {
                    hoveredStack = stack;
                    hoveredX = mouseX;
                    hoveredY = mouseY;
                }
            }
        }

        // Draw empty message if no contents
        if (contents.isEmpty()) {
            String empty = "Empty";
            int emptyWidth = fr.getStringWidth(empty);
            fr.drawString(empty, x + (width - emptyWidth) / 2, slotStartY + 4, 0x606060);
        }

        // Reset state for subsequent rendering
        GlStateManager.enableDepth();
        GlStateManager.disableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Draw tooltip for hovered item. Must be called after draw() in a separate pass.
     */
    public void drawTooltip(int mouseX, int mouseY) {
        if (!hoveredStack.isEmpty() && parent instanceof GuiScreen) {
            ((GuiScreen) parent).drawHoveringText(
                parent.getItemToolTip(hoveredStack),
                hoveredX,
                hoveredY
            );
        }
    }

    private String formatItemCount(long count) {
        if (count <= 0) return "";
        if (count < 1000) return String.valueOf(count);
        if (AEConfig.instance().isUseColoredCraftingStatus()) {
            return ReadableNumberConverter.INSTANCE.toSlimReadableForm(count);
        }

        return ReadableNumberConverter.INSTANCE.toWideReadableForm(count);
    }

    private boolean isInPartition(ItemStack stack, List<ItemStack> partition) {
        if (stack.isEmpty()) return false;

        for (ItemStack partItem : partition) {
            if (ItemStack.areItemsEqual(stack, partItem)) return true;
        }

        return false;
    }

    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (!isInsidePopup(mouseX, mouseY)) return false;

        // Check partition all button click
        if (mouseX >= partitionButtonX && mouseX < partitionButtonX + partitionButtonWidth
                && mouseY >= partitionButtonY && mouseY < partitionButtonY + BUTTON_HEIGHT) {
            if (parent instanceof GuiDiskTerminalBase) ((GuiDiskTerminalBase) parent).onPartitionAllClicked(disk);

            return true;
        }

        // Check slot click for partition add
        int slotStartY = y + HEADER_HEIGHT + BUTTON_HEIGHT + 4;
        int relX = mouseX - x - slotOffsetX;
        int relY = mouseY - slotStartY;

        if (relX >= 0 && relX < SLOTS_PER_ROW * SLOT_SIZE && relY >= 0) {
            int slotCol = relX / SLOT_SIZE;
            int slotRow = relY / SLOT_SIZE;
            int slotIndex = slotRow * SLOTS_PER_ROW + slotCol;

            if (slotIndex < disk.getContents().size()) {
                ItemStack clickedStack = disk.getContents().get(slotIndex);

                // FIXME: partition cannot be toggled on/off if it is in partition already
                if (!clickedStack.isEmpty() && parent instanceof GuiDiskTerminalBase) {
                    // Only add to partition if not already in partition
                    if (!isInPartition(clickedStack, disk.getPartition())) {
                        ((GuiDiskTerminalBase) parent).onTogglePartitionItem(disk, clickedStack);
                    }
                }

                return true;
            }
        }

        return true;
    }

    public boolean isInsidePopup(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /**
     * Get current partition list from parent's storageMap.
     * This ensures we show up-to-date partition status after server updates.
     */
    private List<ItemStack> getCurrentPartition() {
        if (!(parent instanceof GuiDiskTerminalBase)) return disk.getPartition();

        GuiDiskTerminalBase gui = (GuiDiskTerminalBase) parent;
        StorageInfo storage = gui.storageMap.get(storageId);

        if (storage == null) return disk.getPartition();

        for (DiskInfo d : storage.getDisks()) {
            if (d.getSlot() == diskSlot) return d.getPartition();
        }

        return disk.getPartition();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
