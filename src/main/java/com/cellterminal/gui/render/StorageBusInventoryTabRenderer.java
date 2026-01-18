package com.cellterminal.gui.render;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import com.cellterminal.client.StorageBusContentRow;
import com.cellterminal.client.StorageBusInfo;


/**
 * Renderer for the Storage Bus Inventory tab (Tab 4).
 * Displays storage buses with a header row followed by content rows in a tree structure.
 * Content items show "P" indicator if they're in the storage bus's partition.
 */
public class StorageBusInventoryTabRenderer extends CellTerminalRenderer {

    public StorageBusInventoryTabRenderer(FontRenderer fontRenderer, RenderItem itemRender) {
        super(fontRenderer, itemRender);
    }

    /**
     * Draw the storage bus inventory tab content.
     */
    public void draw(List<Object> inventoryLines, int currentScroll, int rowsVisible,
                     int relMouseX, int relMouseY, int absMouseX, int absMouseY,
                     RenderContext ctx) {
        int y = 18;
        int totalLines = inventoryLines.size();

        for (int i = 0; i < rowsVisible && currentScroll + i < totalLines; i++) {
            Object line = inventoryLines.get(currentScroll + i);
            int lineIndex = currentScroll + i;

            boolean isHovered = relMouseX >= 4 && relMouseX < 185
                && relMouseY >= y && relMouseY < y + ROW_HEIGHT;

            if (line instanceof StorageBusInfo) {
                // Header row
                StorageBusInfo storageBus = (StorageBusInfo) line;

                if (isHovered) ctx.hoveredLineIndex = lineIndex;

                // Draw separator line above header (except for first)
                if (lineIndex > 0) Gui.drawRect(GUI_INDENT, y - 1, 180, y, 0xFF606060);

                drawStorageBusHeader(storageBus, y, inventoryLines, lineIndex,
                    relMouseX, relMouseY, absMouseX, absMouseY, ctx);
            } else if (line instanceof StorageBusContentRow) {
                // Content row
                StorageBusContentRow row = (StorageBusContentRow) line;

                if (isHovered) {
                    ctx.hoveredLineIndex = lineIndex;
                    Gui.drawRect(GUI_INDENT, y - 1, 180, y + ROW_HEIGHT - 1, 0x50CCCCCC);
                }

                boolean isFirstInGroup = isFirstContentRowOfStorageBus(inventoryLines, lineIndex);
                boolean isLastInGroup = isLastContentRowOfStorageBus(inventoryLines, lineIndex);
                boolean isFirstVisibleRow = (i == 0);
                boolean isLastVisibleRow = (i == rowsVisible - 1) || (currentScroll + i == totalLines - 1);
                boolean hasContentAbove = hasContentRowAbove(inventoryLines, lineIndex);
                boolean hasContentBelow = hasContentRowBelow(inventoryLines, lineIndex);

                drawStorageBusInventoryLine(row.getStorageBus(), row.getStartIndex(),
                    y, relMouseX, relMouseY, absMouseX, absMouseY,
                    isFirstInGroup, isLastInGroup,
                    isFirstVisibleRow, isLastVisibleRow, hasContentAbove, hasContentBelow,
                    ctx);
            }

            y += ROW_HEIGHT;
        }
    }

    /**
     * Draw a storage bus header row.
     */
    private void drawStorageBusHeader(StorageBusInfo storageBus, int y,
                                       List<Object> lines, int lineIndex,
                                       int mouseX, int mouseY, int absMouseX, int absMouseY,
                                       RenderContext ctx) {
        // Track this storage bus for priority field rendering
        ctx.visibleStorageBuses.add(new RenderContext.VisibleStorageBusEntry(storageBus, y));

        // Draw vertical tree line connecting to content rows below
        boolean hasContentFollowing = lineIndex + 1 < lines.size()
            && lines.get(lineIndex + 1) instanceof StorageBusContentRow;

        if (hasContentFollowing) {
            int lineX = GUI_INDENT + 7;
            Gui.drawRect(lineX, y + ROW_HEIGHT - 1, lineX + 1, y + ROW_HEIGHT, 0xFF808080);
        }

        // Draw connected inventory icon (not the storage bus icon)
        // Only show icon if there's a connected inventory
        ItemStack connectedIcon = storageBus.getConnectedInventoryIcon();
        if (!connectedIcon.isEmpty()) renderItemStack(connectedIcon, GUI_INDENT, y);

        // Draw name and location (shortened to fit)
        String name = storageBus.getLocalizedName();
        if (name.length() > 18) name = name.substring(0, 16) + "...";
        fontRenderer.drawString(name, GUI_INDENT + 20, y + 1, 0x404040);

        String location = storageBus.getLocationString();
        if (location.length() > 30) location = location.substring(0, 28) + "...";
        fontRenderer.drawString(location, GUI_INDENT + 20, y + 9, 0x808080);

        // Draw IO Mode button on the right side of header
        // Show for all bus types, but essentia buses will show a "no effect" tooltip
        int buttonX = 172;
        int headerRightBound = buttonX;  // Always reserve space for IO mode button

        int buttonY = y;
        int buttonSize = 8;

        boolean ioModeHovered = mouseX >= buttonX && mouseX < buttonX + buttonSize
            && mouseY >= buttonY && mouseY < buttonY + buttonSize;

        int btnColor = ioModeHovered ? 0xFF707070 : 0xFF8B8B8B;
        Gui.drawRect(buttonX, buttonY, buttonX + buttonSize, buttonY + buttonSize, btnColor);
        Gui.drawRect(buttonX, buttonY, buttonX + buttonSize, buttonY + 1, 0xFF555555);
        Gui.drawRect(buttonX, buttonY, buttonX + 1, buttonY + buttonSize, 0xFF555555);
        Gui.drawRect(buttonX, buttonY + buttonSize - 1, buttonX + buttonSize, buttonY + buttonSize, 0xFF555555);
        Gui.drawRect(buttonX + buttonSize - 1, buttonY, buttonX + buttonSize, buttonY + buttonSize, 0xFF555555);

        // Draw colored dot based on access mode
        drawIOModeDot(buttonX + 1, buttonY + 1, 6, storageBus.getAccessRestriction());

        if (ioModeHovered) ctx.hoveredIOModeButtonStorageBus = storageBus;

        // Header itself is hoverable for double-click highlight (excluding button area)
        boolean headerHovered = mouseX >= GUI_INDENT && mouseX < headerRightBound
            && mouseY >= y && mouseY < y + ROW_HEIGHT;

        if (headerHovered) {
            Gui.drawRect(GUI_INDENT, y, headerRightBound, y + ROW_HEIGHT, 0x30FFFFFF);
            ctx.hoveredStorageBus = storageBus;
            ctx.hoveredContentStack = ItemStack.EMPTY;
            ctx.hoveredContentX = absMouseX;
            ctx.hoveredContentY = absMouseY;
        }

        // Draw upgrade icons on header row (aligned with header, not first content row)
        drawStorageBusUpgradeIcons(storageBus, 3, y - 1);
    }

    /**
     * Draw a storage bus content line (inventory items).
     * All slots grouped together without a separate side indicator.
     */
    private void drawStorageBusInventoryLine(StorageBusInfo storageBus, int startIndex,
            int y, int mouseX, int mouseY, int absMouseX, int absMouseY,
            boolean isFirstInGroup, boolean isLastInGroup,
            boolean isFirstVisibleRow, boolean isLastVisibleRow,
            boolean hasContentAbove, boolean hasContentBelow,
            RenderContext ctx) {

        int lineX = GUI_INDENT + 7;
        int visibleTop = 18;
        int visibleBottom = 18 + ctx.rowsVisible * ROW_HEIGHT;

        // Draw tree lines - connect all rows
        drawTreeLines(lineX, y, isFirstInGroup, isFirstInGroup, isLastInGroup,
            visibleTop, visibleBottom, isFirstVisibleRow, isLastVisibleRow,
            hasContentAbove, hasContentBelow, true);

        // Draw partition-all button on tree line for first row (green dot)
        if (isFirstInGroup) {
            // Draw partition-all button on tree line (green dot)
            int buttonX = lineX - 3;
            int buttonY = y + 4;
            int buttonSize = 8;

            boolean partitionAllHovered = mouseX >= buttonX && mouseX < buttonX + buttonSize
                && mouseY >= buttonY && mouseY < buttonY + buttonSize;

            // Draw background that covers tree line area first
            Gui.drawRect(buttonX - 1, buttonY - 1, buttonX + buttonSize + 1, buttonY + buttonSize + 1, 0xFF8B8B8B);

            // Draw small button with green dot
            int btnColor = partitionAllHovered ? 0xFF707070 : 0xFF8B8B8B;
            Gui.drawRect(buttonX, buttonY, buttonX + buttonSize, buttonY + buttonSize, btnColor);
            Gui.drawRect(buttonX, buttonY, buttonX + buttonSize, buttonY + 1, 0xFFFFFFFF);
            Gui.drawRect(buttonX, buttonY, buttonX + 1, buttonY + buttonSize, 0xFFFFFFFF);
            Gui.drawRect(buttonX, buttonY + buttonSize - 1, buttonX + buttonSize, buttonY + buttonSize, 0xFF555555);
            Gui.drawRect(buttonX + buttonSize - 1, buttonY, buttonX + buttonSize, buttonY + buttonSize, 0xFF555555);

            // Draw green dot inside button
            Gui.drawRect(buttonX + 1, buttonY + 1, buttonX + buttonSize - 1, buttonY + buttonSize - 1, 0xFF33CC33);

            if (partitionAllHovered) ctx.hoveredPartitionAllButtonStorageBus = storageBus;
        }

        // Draw content item slots - all slots grouped together
        List<ItemStack> contents = storageBus.getContents();
        List<ItemStack> partition = storageBus.getPartition();
        int slotStartX = CELL_INDENT;

        for (int i = 0; i < SLOTS_PER_ROW_BUS; i++) {
            int contentIndex = startIndex + i;
            int slotX = slotStartX + (i * 16) + 4;
            int slotY = y;

            // Draw mini slot background
            drawSlotBackground(slotX, slotY);

            if (contentIndex < contents.size() && !contents.get(contentIndex).isEmpty()) {
                ItemStack stack = contents.get(contentIndex);
                renderItemStack(stack, slotX, slotY);

                // Draw "P" indicator if this item is in partition
                if (isInPartition(stack, partition)) {
                    GlStateManager.disableLighting();
                    GlStateManager.disableDepth();
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(0.5f, 0.5f, 0.5f);
                    fontRenderer.drawStringWithShadow("P", (slotX + 1) * 2, (slotY + 1) * 2, 0xFF55FF55);
                    GlStateManager.popMatrix();
                    GlStateManager.enableDepth();
                }

                // Draw item count
                String countStr = formatItemCount(storageBus.getContentCount(contentIndex));
                int countWidth = fontRenderer.getStringWidth(countStr);
                GlStateManager.disableDepth();
                GlStateManager.pushMatrix();
                GlStateManager.scale(0.5f, 0.5f, 0.5f);
                fontRenderer.drawStringWithShadow(countStr, (slotX + 15) * 2 - countWidth, (slotY + 11) * 2, 0xFFFFFF);
                GlStateManager.popMatrix();
                GlStateManager.enableDepth();

                // Check hover for tooltip
                if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                    Gui.drawRect(slotX + 1, slotY + 1, slotX + 15, slotY + 15, 0x80FFFFFF);
                    ctx.hoveredContentStack = stack;
                    ctx.hoveredContentX = absMouseX;
                    ctx.hoveredContentY = absMouseY;
                    ctx.hoveredStorageBusContentSlot = contentIndex;
                    ctx.hoveredStorageBus = storageBus;
                }
            }
        }
    }

    /**
     * Draw upgrade icons for a storage bus.
     * Icons are drawn in 2 columns starting from left, similar to cell upgrades.
     * @param storageBus The storage bus info
     * @param x The x position to start drawing (left edge)
     * @param y The y position of the row
     */
    private void drawStorageBusUpgradeIcons(StorageBusInfo storageBus, int x, int y) {
        List<ItemStack> upgrades = storageBus.getUpgrades();
        if (upgrades.isEmpty()) return;

        int iconSize = 8;
        int spacing = 1;
        int cols = 2;

        for (int i = 0; i < Math.min(upgrades.size(), 5); i++) {
            ItemStack upgrade = upgrades.get(i);
            if (!upgrade.isEmpty()) {
                int col = i % cols;
                int row = i / cols;
                int iconX = x + col * (iconSize + spacing);
                int iconY = y + 1 + row * (iconSize + spacing);
                renderSmallItemStack(upgrade, iconX, iconY);
            }
        }
    }

    /**
     * Check if the given line is the first content row of a storage bus.
     */
    private boolean isFirstContentRowOfStorageBus(List<Object> lines, int lineIndex) {
        if (!(lines.get(lineIndex) instanceof StorageBusContentRow)) return false;
        if (lineIndex == 0) return true;

        Object prev = lines.get(lineIndex - 1);

        return prev instanceof StorageBusInfo;
    }

    /**
     * Check if the given line is the last content row of a storage bus.
     */
    private boolean isLastContentRowOfStorageBus(List<Object> lines, int lineIndex) {
        if (!(lines.get(lineIndex) instanceof StorageBusContentRow)) return false;
        if (lineIndex >= lines.size() - 1) return true;

        Object next = lines.get(lineIndex + 1);

        return next instanceof StorageBusInfo;
    }

    /**
     * Check if there's a content row above this line within the same storage bus.
     */
    private boolean hasContentRowAbove(List<Object> lines, int lineIndex) {
        if (lineIndex == 0) return false;

        Object prev = lines.get(lineIndex - 1);

        return prev instanceof StorageBusContentRow;
    }

    /**
     * Check if there's a content row below this line within the same storage bus.
     */
    private boolean hasContentRowBelow(List<Object> lines, int lineIndex) {
        if (lineIndex >= lines.size() - 1) return false;

        Object next = lines.get(lineIndex + 1);

        return next instanceof StorageBusContentRow;
    }

    // IO Mode dot colors
    private static final int COLOR_BLUE_EXTRACT = 0xFF4466FF;  // Blue for extract/read only
    private static final int COLOR_ORANGE_INSERT = 0xFFFF9933; // Orange for insert/write only
    private static final int COLOR_GREY_NONE = 0xFF555555;     // Grey for no access

    /**
     * Draw a colored dot indicating IO mode.
     * 0 = NO_ACCESS (grey)
     * 1 = READ (blue, extract only)
     * 2 = WRITE (orange, insert only)
     * 3 = READ_WRITE (mixed diagonal)
     */
    private void drawIOModeDot(int x, int y, int size, int accessMode) {
        int color1, color2;

        switch (accessMode) {
            case 1: // READ - extract only
                color1 = COLOR_BLUE_EXTRACT;
                color2 = COLOR_BLUE_EXTRACT;
                break;
            case 2: // WRITE - insert only
                color1 = COLOR_ORANGE_INSERT;
                color2 = COLOR_ORANGE_INSERT;
                break;
            case 3: // READ_WRITE - both
                color1 = COLOR_BLUE_EXTRACT;
                color2 = COLOR_ORANGE_INSERT;
                break;
            default: // NO_ACCESS
                color1 = COLOR_GREY_NONE;
                color2 = COLOR_GREY_NONE;
                break;
        }

        // For mixed mode, draw diagonal split
        if (accessMode == 3) {
            for (int py = 0; py < size; py++) {
                for (int px = 0; px < size; px++) {
                    // Diagonal split: top-left is blue, bottom-right is orange
                    int color = (px + py < size) ? color1 : color2;
                    Gui.drawRect(x + px, y + py, x + px + 1, y + py + 1, color);
                }
            }
        } else {
            // Solid color
            Gui.drawRect(x, y, x + size, y + size, color1);
        }
    }
}
