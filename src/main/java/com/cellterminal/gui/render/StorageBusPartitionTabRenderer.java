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
 * Renderer for the Storage Bus Partition tab (Tab 5).
 * Displays storage buses with a header row followed by partition rows in a tree structure.
 * Partition slots have an orange/amber tint and support JEI ghost ingredient drag-and-drop.
 */
public class StorageBusPartitionTabRenderer extends CellTerminalRenderer {

    public StorageBusPartitionTabRenderer(FontRenderer fontRenderer, RenderItem itemRender) {
        super(fontRenderer, itemRender);
    }

    /**
     * Draw the storage bus partition tab content.
     */
    public void draw(List<Object> partitionLines, int currentScroll, int rowsVisible,
                     int relMouseX, int relMouseY, int absMouseX, int absMouseY,
                     int guiLeft, int guiTop, RenderContext ctx) {
        int y = 18;
        int totalLines = partitionLines.size();

        for (int i = 0; i < rowsVisible && currentScroll + i < totalLines; i++) {
            Object line = partitionLines.get(currentScroll + i);
            int lineIndex = currentScroll + i;

            boolean isHovered = relMouseX >= 4 && relMouseX < 185
                && relMouseY >= y && relMouseY < y + ROW_HEIGHT;

            if (line instanceof StorageBusInfo) {
                // Header row
                StorageBusInfo storageBus = (StorageBusInfo) line;

                if (isHovered) ctx.hoveredLineIndex = lineIndex;

                // Draw separator line above header (except for first)
                if (lineIndex > 0) Gui.drawRect(GUI_INDENT, y - 1, 180, y, 0xFF606060);

                drawStorageBusHeader(storageBus, y, partitionLines, lineIndex,
                    relMouseX, relMouseY, absMouseX, absMouseY, guiLeft, guiTop, ctx);
            } else if (line instanceof StorageBusContentRow) {
                // Content row
                StorageBusContentRow row = (StorageBusContentRow) line;

                // Check if this bus is selected for background extension
                boolean isSelected = ctx.selectedStorageBusIds != null
                    && ctx.selectedStorageBusIds.contains(row.getStorageBus().getId());

                if (isSelected) {
                    Gui.drawRect(GUI_INDENT, y, 180, y + ROW_HEIGHT, 0x405599DD);  // Light blue selection
                }

                if (isHovered) {
                    ctx.hoveredLineIndex = lineIndex;
                    Gui.drawRect(GUI_INDENT, y - 1, 180, y + ROW_HEIGHT - 1, 0x50CCCCCC);
                }

                boolean isFirstInGroup = isFirstContentRowOfStorageBus(partitionLines, lineIndex);
                boolean isLastInGroup = isLastContentRowOfStorageBus(partitionLines, lineIndex);
                boolean isFirstVisibleRow = (i == 0);
                boolean isLastVisibleRow = (i == rowsVisible - 1) || (currentScroll + i == totalLines - 1);
                boolean hasContentAbove = hasContentRowAbove(partitionLines, lineIndex);
                boolean hasContentBelow = hasContentRowBelow(partitionLines, lineIndex);

                drawStorageBusPartitionLine(row.getStorageBus(), row.getStartIndex(),
                    y, relMouseX, relMouseY, absMouseX, absMouseY,
                    isFirstInGroup, isLastInGroup,
                    isFirstVisibleRow, isLastVisibleRow, hasContentAbove, hasContentBelow,
                    guiLeft, guiTop, ctx);
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
                                       int guiLeft, int guiTop, RenderContext ctx) {
        // Track this storage bus for priority field rendering
        ctx.visibleStorageBuses.add(new RenderContext.VisibleStorageBusEntry(storageBus, y));

        // Check if this storage bus is selected
        boolean isSelected = ctx.selectedStorageBusIds != null
            && ctx.selectedStorageBusIds.contains(storageBus.getId());

        // Draw selection background (covers header) - light blue
        if (isSelected) Gui.drawRect(GUI_INDENT, y, 180, y + ROW_HEIGHT, 0x405599DD);

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
        int nameColor = isSelected ? 0x204080 : 0x404040;  // Blue-ish when selected
        fontRenderer.drawString(name, GUI_INDENT + 20, y + 1, nameColor);

        String location = storageBus.getLocationString();
        if (location.length() > 30) location = location.substring(0, 28) + "...";
        fontRenderer.drawString(location, GUI_INDENT + 20, y + 9, 0x808080);

        // Draw IO Mode button on the right side of header
        int buttonX = 172;
        int headerRightBound = buttonX;  // Reserve space for IO mode button

        int buttonY = y;
        int buttonSize = 8;

        boolean ioModeHovered = mouseX >= buttonX && mouseX < buttonX + buttonSize
            && mouseY >= buttonY && mouseY < buttonY + buttonSize;

        // Draw background for button
        // Gui.drawRect(buttonX - 1, buttonY - 1, buttonX + buttonSize + 1, buttonY + buttonSize + 1, 0xFF8B8B8B);

        // Draw small button (grayed out if not supported)
        int btnColor;
        if (storageBus.supportsIOMode()) {
            btnColor = ioModeHovered ? 0xFF707070 : 0xFF8B8B8B;
        } else {
            // Essentia bus - grayed out appearance
            btnColor = ioModeHovered ? 0xFF606060 : 0xFF707070;
        }

        Gui.drawRect(buttonX, buttonY, buttonX + buttonSize, buttonY + buttonSize, btnColor);
        Gui.drawRect(buttonX, buttonY, buttonX + buttonSize, buttonY + 1, 0xFF555555);
        Gui.drawRect(buttonX, buttonY, buttonX + 1, buttonY + buttonSize, 0xFF555555);
        Gui.drawRect(buttonX, buttonY + buttonSize - 1, buttonX + buttonSize, buttonY + buttonSize, 0xFF555555);
        Gui.drawRect(buttonX + buttonSize - 1, buttonY, buttonX + buttonSize, buttonY + buttonSize, 0xFF555555);

        // Draw colored dot based on access mode
        drawIOModeDot(buttonX + 1, buttonY + 1, 6, storageBus.getAccessRestriction());

        if (ioModeHovered) ctx.hoveredIOModeButtonStorageBus = storageBus;

        // Header itself is hoverable for selection (excluding IO mode button area)
        boolean headerHovered = mouseX >= GUI_INDENT && mouseX < headerRightBound
            && mouseY >= y && mouseY < y + ROW_HEIGHT;

        if (headerHovered) {
            // Draw hover highlight
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
     * Draw a storage bus partition line (partition items).
     * All 9 slots are grouped together without a separate side indicator.
     */
    private void drawStorageBusPartitionLine(StorageBusInfo storageBus, int startIndex,
            int y, int mouseX, int mouseY, int absMouseX, int absMouseY,
            boolean isFirstInGroup, boolean isLastInGroup,
            boolean isFirstVisibleRow, boolean isLastVisibleRow,
            boolean hasContentAbove, boolean hasContentBelow,
            int guiLeft, int guiTop, RenderContext ctx) {

        int lineX = GUI_INDENT + 7;
        int visibleTop = 18;
        int visibleBottom = 18 + ctx.rowsVisible * ROW_HEIGHT;

        // Get available config slots based on capacity upgrades
        int availableSlots = storageBus.getAvailableConfigSlots();

        // Draw tree lines - connect all rows, not just the first
        drawTreeLines(lineX, y, isFirstInGroup, isFirstInGroup, isLastInGroup,
            visibleTop, visibleBottom, isFirstVisibleRow, isLastVisibleRow,
            hasContentAbove, hasContentBelow, true);

        // Draw clear-partition button on tree line for first row (red dot)
        if (isFirstInGroup) {
            // Draw clear-partition button on tree line (red dot)
            int buttonX = lineX - 3;
            int buttonY = y + 4;
            int buttonSize = 8;

            boolean clearHovered = mouseX >= buttonX && mouseX < buttonX + buttonSize
                && mouseY >= buttonY && mouseY < buttonY + buttonSize;

            // Draw background that covers tree line area first
            Gui.drawRect(buttonX - 1, buttonY - 1, buttonX + buttonSize + 1, buttonY + buttonSize + 1, 0xFF8B8B8B);

            // Draw small button with red dot
            int btnColor = clearHovered ? 0xFF707070 : 0xFF8B8B8B;
            Gui.drawRect(buttonX, buttonY, buttonX + buttonSize, buttonY + buttonSize, btnColor);
            Gui.drawRect(buttonX, buttonY, buttonX + buttonSize, buttonY + 1, 0xFFFFFFFF);
            Gui.drawRect(buttonX, buttonY, buttonX + 1, buttonY + buttonSize, 0xFFFFFFFF);
            Gui.drawRect(buttonX, buttonY + buttonSize - 1, buttonX + buttonSize, buttonY + buttonSize, 0xFF555555);
            Gui.drawRect(buttonX + buttonSize - 1, buttonY, buttonX + buttonSize, buttonY + buttonSize, 0xFF555555);

            // Draw red dot inside button
            Gui.drawRect(buttonX + 1, buttonY + 1, buttonX + buttonSize - 1, buttonY + buttonSize - 1, 0xFFCC3333);

            if (clearHovered) ctx.hoveredClearButtonStorageBus = storageBus;
        }

        // Draw partition item slots - all 9 slots grouped together
        // Starting at CELL_INDENT, no separate side indicator slot
        List<ItemStack> partitions = storageBus.getPartition();
        int slotStartX = CELL_INDENT;

        for (int i = 0; i < SLOTS_PER_ROW_BUS; i++) {
            int partitionIndex = startIndex + i;

            // Stop if we've reached the available config slots for this storage bus
            if (partitionIndex >= availableSlots) break;

            int slotX = slotStartX + (i * 16) + 4;
            int slotY = y;

            // Draw partition slot background (orange/amber tint)
            drawPartitionSlotBackground(slotX, slotY);

            // Track slot position for JEI ghost ingredients
            int absSlotX = guiLeft + slotX;
            int absSlotY = guiTop + slotY;
            ctx.storageBusPartitionSlotTargets.add(new RenderContext.StorageBusPartitionSlotTarget(
                storageBus, partitionIndex, absSlotX, absSlotY, 16, 16));

            boolean slotHovered = mouseX >= slotX && mouseX < slotX + 16
                    && mouseY >= slotY && mouseY < slotY + 16;

            if (partitionIndex < partitions.size() && !partitions.get(partitionIndex).isEmpty()) {
                ItemStack stack = partitions.get(partitionIndex);
                renderItemStack(stack, slotX, slotY);

                if (slotHovered) {
                    Gui.drawRect(slotX + 1, slotY + 1, slotX + 15, slotY + 15, 0x80FFFFFF);
                    ctx.hoveredContentStack = stack;
                    ctx.hoveredContentX = absMouseX;
                    ctx.hoveredContentY = absMouseY;
                    ctx.hoveredStorageBusPartitionSlot = partitionIndex;
                    ctx.hoveredStorageBus = storageBus;
                }
            } else if (slotHovered) {
                // Empty slot hover
                Gui.drawRect(slotX + 1, slotY + 1, slotX + 15, slotY + 15, 0x40FFFFFF);
                ctx.hoveredStorageBusPartitionSlot = partitionIndex;
                ctx.hoveredStorageBus = storageBus;
            }
        }
    }

    /**
     * Draw a partition slot background with orange/amber tint.
     */
    private void drawPartitionSlotBackground(int x, int y) {
        Gui.drawRect(x, y, x + 16, y + 16, 0xFF9B8B7B);
        Gui.drawRect(x, y, x + 15, y + 1, 0xFF473737);
        Gui.drawRect(x, y, x + 1, y + 15, 0xFF473737);
        Gui.drawRect(x + 1, y + 15, x + 16, y + 16, 0xFFFFEEDD);
        Gui.drawRect(x + 15, y + 1, x + 16, y + 15, 0xFFFFEEDD);
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
