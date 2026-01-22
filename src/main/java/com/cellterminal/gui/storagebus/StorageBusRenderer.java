package com.cellterminal.gui.storagebus;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import com.cellterminal.client.StorageBusContentRow;
import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.render.RenderContext;


/**
 * Main renderer for storage bus-related GUI elements.
 *
 * This class coordinates the rendering of storage buses in the Storage Bus
 * Inventory and Partition tabs. It delegates to specialized sub-renderers
 * for slots and content items.
 *
 * Usage:
 * <pre>
 * StorageBusRenderer renderer = new StorageBusRenderer(fontRenderer, itemRender);
 * // For inventory tab:
 * renderer.drawStorageBusInventoryLine(storageBus, startIndex, y, ...);
 * // For partition tab:
 * renderer.drawStorageBusPartitionLine(storageBus, startIndex, y, ...);
 * </pre>
 */
public class StorageBusRenderer {

    // IO Mode dot colors
    private static final int COLOR_BLUE_EXTRACT = 0xFF4466FF;
    private static final int COLOR_ORANGE_INSERT = 0xFFFF9933;
    private static final int COLOR_GREY_NONE = 0xFF555555;

    private final FontRenderer fontRenderer;
    private final StorageBusSlotRenderer slotRenderer;

    public StorageBusRenderer(FontRenderer fontRenderer, RenderItem itemRender) {
        this.fontRenderer = fontRenderer;
        this.slotRenderer = new StorageBusSlotRenderer(fontRenderer, itemRender);
    }

    /**
     * Get the slot renderer for custom slot drawing.
     */
    public StorageBusSlotRenderer getSlotRenderer() {
        return slotRenderer;
    }

    // ========================================
    // HEADER RENDERING
    // ========================================

    /**
     * Draw a storage bus header row.
     *
     * @param storageBus The storage bus info
     * @param y Y position
     * @param lines All lines in the list
     * @param lineIndex Current line index
     * @param mouseX Relative mouse X
     * @param mouseY Relative mouse Y
     * @param absMouseX Absolute mouse X
     * @param absMouseY Absolute mouse Y
     * @param ctx Render context
     */
    public void drawStorageBusHeader(StorageBusInfo storageBus, int y,
                                      List<Object> lines, int lineIndex,
                                      int mouseX, int mouseY, int absMouseX, int absMouseY,
                                      RenderContext ctx) {

        // Track this storage bus for priority field rendering
        ctx.visibleStorageBuses.add(new RenderContext.VisibleStorageBusEntry(storageBus, y));

        // Draw vertical tree line connecting to content rows below
        boolean hasContentFollowing = lineIndex + 1 < lines.size()
            && lines.get(lineIndex + 1) instanceof StorageBusContentRow;

        if (hasContentFollowing) {
            int lineX = GuiConstants.GUI_INDENT + 7;
            Gui.drawRect(lineX, y + GuiConstants.ROW_HEIGHT - 1, lineX + 1, y + GuiConstants.ROW_HEIGHT, GuiConstants.COLOR_TREE_LINE);
        }

        // Draw connected inventory icon
        ItemStack connectedIcon = storageBus.getConnectedInventoryIcon();
        if (!connectedIcon.isEmpty()) slotRenderer.renderItemStack(connectedIcon, GuiConstants.GUI_INDENT, y);

        // Draw name and location
        String name = storageBus.getLocalizedName();
        if (name.length() > 18) name = name.substring(0, 16) + "...";
        fontRenderer.drawString(name, GuiConstants.GUI_INDENT + 20, y + 1, GuiConstants.COLOR_TEXT_NORMAL);

        String location = storageBus.getLocationString();
        if (location.length() > 30) location = location.substring(0, 28) + "...";
        fontRenderer.drawString(location, GuiConstants.GUI_INDENT + 20, y + 9, GuiConstants.COLOR_TEXT_SECONDARY);

        // Draw IO Mode button
        int buttonX = GuiConstants.BUTTON_IO_MODE_X;
        int buttonY = y;
        int buttonSize = GuiConstants.SMALL_BUTTON_SIZE;

        boolean ioModeHovered = mouseX >= buttonX && mouseX < buttonX + buttonSize
            && mouseY >= buttonY && mouseY < buttonY + buttonSize;

        drawIOModeDotButton(buttonX, buttonY, buttonSize, storageBus.getAccessRestriction(), ioModeHovered);

        if (ioModeHovered) ctx.hoveredIOModeButtonStorageBus = storageBus;

        // Header hover detection
        int headerRightBound = buttonX;
        boolean headerHovered = mouseX >= GuiConstants.GUI_INDENT && mouseX < headerRightBound
            && mouseY >= y && mouseY < y + GuiConstants.ROW_HEIGHT;

        if (headerHovered) {
            Gui.drawRect(GuiConstants.GUI_INDENT, y, headerRightBound, y + GuiConstants.ROW_HEIGHT, GuiConstants.COLOR_STORAGE_HEADER_HOVER);
            ctx.hoveredStorageBus = storageBus;
            ctx.hoveredContentStack = ItemStack.EMPTY;
            ctx.hoveredContentX = absMouseX;
            ctx.hoveredContentY = absMouseY;
        }

        // Draw upgrade icons
        drawUpgradeIcons(storageBus, 3, y - 1, ctx);
    }

    /**
     * Draw IO mode button with colored dot.
     */
    private void drawIOModeDotButton(int x, int y, int size, int accessMode, boolean hovered) {
        drawIOModeDotButton(x, y, size, accessMode, hovered, true);
    }

    /**
     * Draw IO mode button with colored dot.
     *
     * @param supportsIOMode Whether the storage bus supports IO mode (essentia buses don't)
     */
    private void drawIOModeDotButton(int x, int y, int size, int accessMode, boolean hovered, boolean supportsIOMode) {
        int btnColor;
        if (supportsIOMode) {
            btnColor = hovered ? GuiConstants.COLOR_BUTTON_HOVER : GuiConstants.COLOR_BUTTON_NORMAL;
        } else {
            btnColor = hovered ? 0xFF606060 : 0xFF707070;
        }

        Gui.drawRect(x, y, x + size, y + size, btnColor);
        Gui.drawRect(x, y, x + size, y + 1, GuiConstants.COLOR_BUTTON_SHADOW);
        Gui.drawRect(x, y, x + 1, y + size, GuiConstants.COLOR_BUTTON_SHADOW);
        Gui.drawRect(x, y + size - 1, x + size, y + size, GuiConstants.COLOR_BUTTON_SHADOW);
        Gui.drawRect(x + size - 1, y, x + size, y + size, GuiConstants.COLOR_BUTTON_SHADOW);

        drawIOModeDot(x + 1, y + 1, size - 2, accessMode);
    }

    /**
     * Draw a storage bus header row for the Partition tab.
     * Includes selection highlighting support.
     *
     * @param storageBus The storage bus info
     * @param y Y position
     * @param lines All lines in the list
     * @param lineIndex Current line index
     * @param mouseX Relative mouse X
     * @param mouseY Relative mouse Y
     * @param absMouseX Absolute mouse X
     * @param absMouseY Absolute mouse Y
     * @param guiLeft GUI left position
     * @param guiTop GUI top position
     * @param ctx Render context
     */
    public void drawStorageBusPartitionHeader(StorageBusInfo storageBus, int y,
                                               List<Object> lines, int lineIndex,
                                               int mouseX, int mouseY, int absMouseX, int absMouseY,
                                               int guiLeft, int guiTop, RenderContext ctx) {

        // Track this storage bus for priority field rendering
        ctx.visibleStorageBuses.add(new RenderContext.VisibleStorageBusEntry(storageBus, y));

        // Check if this storage bus is selected
        boolean isSelected = ctx.selectedStorageBusIds != null
            && ctx.selectedStorageBusIds.contains(storageBus.getId());

        // Draw selection background
        if (isSelected) {
            Gui.drawRect(GuiConstants.GUI_INDENT, y, GuiConstants.CONTENT_RIGHT_EDGE, y + GuiConstants.ROW_HEIGHT, GuiConstants.COLOR_SELECTION);
        }

        // Draw vertical tree line connecting to content rows below
        boolean hasContentFollowing = lineIndex + 1 < lines.size() && lines.get(lineIndex + 1) instanceof StorageBusContentRow;

        if (hasContentFollowing) {
            int lineX = GuiConstants.GUI_INDENT + 7;
            Gui.drawRect(lineX, y + GuiConstants.ROW_HEIGHT - 1, lineX + 1, y + GuiConstants.ROW_HEIGHT, GuiConstants.COLOR_TREE_LINE);
        }

        // Draw connected inventory icon
        ItemStack connectedIcon = storageBus.getConnectedInventoryIcon();
        if (!connectedIcon.isEmpty()) slotRenderer.renderItemStack(connectedIcon, GuiConstants.GUI_INDENT, y);

        // Draw name and location
        String name = storageBus.getLocalizedName();
        if (name.length() > 18) name = name.substring(0, 16) + "...";
        int nameColor = isSelected ? 0x204080 : GuiConstants.COLOR_TEXT_NORMAL;
        fontRenderer.drawString(name, GuiConstants.GUI_INDENT + 20, y + 1, nameColor);

        String location = storageBus.getLocationString();
        if (location.length() > 30) location = location.substring(0, 28) + "...";
        fontRenderer.drawString(location, GuiConstants.GUI_INDENT + 20, y + 9, GuiConstants.COLOR_TEXT_SECONDARY);

        // Draw IO Mode button
        int buttonX = GuiConstants.BUTTON_IO_MODE_X;
        int buttonY = y;
        int buttonSize = GuiConstants.SMALL_BUTTON_SIZE;

        boolean ioModeHovered = mouseX >= buttonX && mouseX < buttonX + buttonSize
            && mouseY >= buttonY && mouseY < buttonY + buttonSize;

        drawIOModeDotButton(buttonX, buttonY, buttonSize, storageBus.getAccessRestriction(),
                            ioModeHovered, storageBus.supportsIOMode());

        if (ioModeHovered) ctx.hoveredIOModeButtonStorageBus = storageBus;

        // Header hover detection
        int headerRightBound = buttonX;
        boolean headerHovered = mouseX >= GuiConstants.GUI_INDENT && mouseX < headerRightBound
            && mouseY >= y && mouseY < y + GuiConstants.ROW_HEIGHT;

        if (headerHovered) {
            Gui.drawRect(GuiConstants.GUI_INDENT, y, headerRightBound, y + GuiConstants.ROW_HEIGHT, GuiConstants.COLOR_STORAGE_HEADER_HOVER);
            ctx.hoveredStorageBus = storageBus;
            ctx.hoveredContentStack = ItemStack.EMPTY;
            ctx.hoveredContentX = absMouseX;
            ctx.hoveredContentY = absMouseY;
        }

        // Draw upgrade icons
        drawUpgradeIcons(storageBus, 3, y - 1, ctx);
    }

    /**
     * Draw upgrade icons in 2 columns with hover tracking.
     */
    private void drawUpgradeIcons(StorageBusInfo storageBus, int x, int y, RenderContext ctx) {
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
                slotRenderer.renderSmallItemStack(upgrade, iconX, iconY);

                // Track upgrade icon position for tooltip and click handling
                if (ctx != null) {
                    ctx.upgradeIconTargets.add(new RenderContext.UpgradeIconTarget(
                        storageBus, upgrade, i, ctx.guiLeft + iconX, ctx.guiTop + iconY));
                }
            }
        }
    }

    // ========================================
    // INVENTORY LINE RENDERING
    // ========================================

    /**
     * Draw a storage bus inventory line (content items).
     */
    public void drawStorageBusInventoryLine(StorageBusInfo storageBus, int startIndex,
                                             int y, int mouseX, int mouseY,
                                             int absMouseX, int absMouseY,
                                             boolean isFirstInGroup, boolean isLastInGroup,
                                             boolean isFirstVisibleRow, boolean isLastVisibleRow,
                                             boolean hasContentAbove, boolean hasContentBelow,
                                             RenderContext ctx) {

        int lineX = GuiConstants.GUI_INDENT + 7;
        int visibleTop = GuiConstants.CONTENT_START_Y;
        int visibleBottom = GuiConstants.CONTENT_START_Y + ctx.rowsVisible * GuiConstants.ROW_HEIGHT;

        // Draw tree lines
        drawTreeLines(lineX, y, isFirstInGroup, isLastInGroup, visibleTop, visibleBottom,
                      isFirstVisibleRow, isLastVisibleRow, hasContentAbove, hasContentBelow);

        // Draw partition-all button for first row
        if (isFirstInGroup) drawPartitionAllButton(storageBus, lineX, y, mouseX, mouseY, ctx);

        // Draw content slots
        drawInventoryContentSlots(storageBus, startIndex, y, mouseX, mouseY, absMouseX, absMouseY, ctx);
    }

    private void drawTreeLines(int lineX, int y, boolean isFirstInGroup, boolean isLastInGroup,
                                int visibleTop, int visibleBottom,
                                boolean isFirstVisibleRow, boolean isLastVisibleRow,
                                boolean hasContentAbove, boolean hasContentBelow) {

        int lineTop = isFirstVisibleRow && hasContentAbove ? visibleTop : y - 4;
        int lineBottom = isLastInGroup ? y + 9 : (isLastVisibleRow && hasContentBelow ? visibleBottom : y + GuiConstants.ROW_HEIGHT);

        if (lineTop < visibleTop) lineTop = visibleTop;

        // Vertical line
        Gui.drawRect(lineX, lineTop, lineX + 1, lineBottom, GuiConstants.COLOR_TREE_LINE);

        // Horizontal branch
        Gui.drawRect(lineX, y + 8, lineX + 10, y + 9, GuiConstants.COLOR_TREE_LINE);
    }

    private void drawPartitionAllButton(StorageBusInfo storageBus, int lineX, int y, int mouseX, int mouseY, RenderContext ctx) {
        int buttonX = lineX - 3;
        int buttonY = y + 4;
        int buttonSize = GuiConstants.SMALL_BUTTON_SIZE;

        boolean hovered = mouseX >= buttonX && mouseX < buttonX + buttonSize
            && mouseY >= buttonY && mouseY < buttonY + buttonSize;

        // Draw background
        Gui.drawRect(buttonX - 1, buttonY - 1, buttonX + buttonSize + 1, buttonY + buttonSize + 1, GuiConstants.COLOR_SLOT_BACKGROUND);

        // Draw button with green fill
        slotRenderer.drawSmallButton(buttonX, buttonY, hovered, GuiConstants.COLOR_BUTTON_GREEN);

        if (hovered) ctx.hoveredPartitionAllButtonStorageBus = storageBus;
    }

    private void drawInventoryContentSlots(StorageBusInfo storageBus, int startIndex, int y,
                                            int mouseX, int mouseY, int absMouseX, int absMouseY,
                                            RenderContext ctx) {

        List<ItemStack> contents = storageBus.getContents();
        List<ItemStack> partition = storageBus.getPartition();
        int slotStartX = GuiConstants.CELL_INDENT + 4;

        for (int i = 0; i < GuiConstants.STORAGE_BUS_SLOTS_PER_ROW; i++) {
            int contentIndex = startIndex + i;
            int slotX = slotStartX + (i * GuiConstants.MINI_SLOT_SIZE);

            slotRenderer.drawSlotBackground(slotX, y);

            if (contentIndex >= contents.size()) continue;

            ItemStack stack = contents.get(contentIndex);
            if (stack.isEmpty()) continue;

            slotRenderer.renderItemStack(stack, slotX, y);

            // Draw partition indicator
            if (slotRenderer.isInPartition(stack, partition)) slotRenderer.drawPartitionIndicator(slotX, y);

            // Draw item count
            slotRenderer.drawItemCount(storageBus.getContentCount(contentIndex), slotX, y);

            // Check hover
            if (mouseX >= slotX && mouseX < slotX + GuiConstants.MINI_SLOT_SIZE
                && mouseY >= y && mouseY < y + GuiConstants.MINI_SLOT_SIZE) {
                slotRenderer.drawSlotHoverHighlight(slotX, y);
                ctx.hoveredContentStack = stack;
                ctx.hoveredContentX = absMouseX;
                ctx.hoveredContentY = absMouseY;
                ctx.hoveredStorageBusContentSlot = contentIndex;
                ctx.hoveredStorageBus = storageBus;
            }
        }
    }

    // ========================================
    // PARTITION LINE RENDERING
    // ========================================

    /**
     * Draw a storage bus partition line (partition slots).
     * Overload without selection support (selection is handled at tab level).
     */
    public void drawStorageBusPartitionLine(StorageBusInfo storageBus, int startIndex,
                                             int y, int mouseX, int mouseY,
                                             int absMouseX, int absMouseY,
                                             boolean isFirstInGroup, boolean isLastInGroup,
                                             int visibleTop, int visibleBottom,
                                             boolean isFirstVisibleRow, boolean isLastVisibleRow,
                                             boolean hasContentAbove, boolean hasContentBelow,
                                             int guiLeft, int guiTop,
                                             RenderContext ctx) {

        int lineX = GuiConstants.GUI_INDENT + 7;

        // Draw tree lines
        drawTreeLines(lineX, y, isFirstInGroup, isLastInGroup, visibleTop, visibleBottom,
                      isFirstVisibleRow, isLastVisibleRow, hasContentAbove, hasContentBelow);

        // Draw clear button for first row
        if (isFirstInGroup) drawClearButton(storageBus, lineX, y, mouseX, mouseY, ctx);

        // Draw partition slots
        drawPartitionSlots(storageBus, startIndex, y, mouseX, mouseY, absMouseX, absMouseY, guiLeft, guiTop, ctx);
    }

    /**
     * Draw a storage bus partition line (partition slots) with selection support.
     */
    public void drawStorageBusPartitionLineWithSelection(StorageBusInfo storageBus, int startIndex,
                                             int y, int mouseX, int mouseY,
                                             int absMouseX, int absMouseY,
                                             boolean isFirstInGroup, boolean isLastInGroup,
                                             boolean isFirstVisibleRow, boolean isLastVisibleRow,
                                             boolean hasContentAbove, boolean hasContentBelow,
                                             boolean isSelected, int guiLeft, int guiTop,
                                             RenderContext ctx) {

        int lineX = GuiConstants.GUI_INDENT + 7;
        int visibleTop = GuiConstants.CONTENT_START_Y;
        int visibleBottom = GuiConstants.CONTENT_START_Y + ctx.rowsVisible * GuiConstants.ROW_HEIGHT;

        // Draw tree lines
        drawTreeLines(lineX, y, isFirstInGroup, isLastInGroup, visibleTop, visibleBottom,
                      isFirstVisibleRow, isLastVisibleRow, hasContentAbove, hasContentBelow);

        // Draw clear button for first row
        if (isFirstInGroup) drawClearButton(storageBus, lineX, y, mouseX, mouseY, ctx);

        // Draw selection highlight
        if (isSelected) {
            Gui.drawRect(GuiConstants.GUI_INDENT - 2, y - 1, GuiConstants.CONTENT_RIGHT_EDGE, y + GuiConstants.ROW_HEIGHT - 1, GuiConstants.COLOR_SELECTION_HIGHLIGHT);
        }

        // Draw partition slots
        drawPartitionSlots(storageBus, startIndex, y, mouseX, mouseY, absMouseX, absMouseY, guiLeft, guiTop, ctx);
    }

    private void drawClearButton(StorageBusInfo storageBus, int lineX, int y, int mouseX, int mouseY, RenderContext ctx) {
        int buttonX = lineX - 3;
        int buttonY = y + 4;
        int buttonSize = GuiConstants.SMALL_BUTTON_SIZE;

        boolean hovered = mouseX >= buttonX && mouseX < buttonX + buttonSize
            && mouseY >= buttonY && mouseY < buttonY + buttonSize;

        // Draw background
        Gui.drawRect(buttonX - 1, buttonY - 1, buttonX + buttonSize + 1, buttonY + buttonSize + 1, GuiConstants.COLOR_SLOT_BACKGROUND);

        // Draw button with red fill
        slotRenderer.drawSmallButton(buttonX, buttonY, hovered, GuiConstants.COLOR_BUTTON_RED);

        if (hovered) ctx.hoveredClearButtonStorageBus = storageBus;
    }

    private void drawPartitionSlots(StorageBusInfo storageBus, int startIndex, int y,
                                     int mouseX, int mouseY, int absMouseX, int absMouseY,
                                     int guiLeft, int guiTop, RenderContext ctx) {

        List<ItemStack> partition = storageBus.getPartition();
        int maxSlots = storageBus.getAvailableConfigSlots();
        int slotStartX = GuiConstants.CELL_INDENT + 4;

        for (int i = 0; i < GuiConstants.STORAGE_BUS_SLOTS_PER_ROW; i++) {
            int partitionIndex = startIndex + i;
            if (partitionIndex >= maxSlots) break;

            int slotX = slotStartX + (i * GuiConstants.MINI_SLOT_SIZE);

            // Draw partition slot with amber tint
            slotRenderer.drawPartitionSlotBackground(slotX, y);

            // Register JEI ghost target
            ctx.storageBusPartitionSlotTargets.add(new RenderContext.StorageBusPartitionSlotTarget(
                storageBus, partitionIndex, guiLeft + slotX, guiTop + y,
                GuiConstants.MINI_SLOT_SIZE, GuiConstants.MINI_SLOT_SIZE));

            // Draw partition item if present
            ItemStack partItem = partitionIndex < partition.size() ? partition.get(partitionIndex) : ItemStack.EMPTY;
            if (!partItem.isEmpty()) slotRenderer.renderItemStack(partItem, slotX, y);

            // Check hover
            if (mouseX >= slotX && mouseX < slotX + GuiConstants.MINI_SLOT_SIZE
                && mouseY >= y && mouseY < y + GuiConstants.MINI_SLOT_SIZE) {
                slotRenderer.drawSlotHoverHighlight(slotX, y);
                ctx.hoveredStorageBusPartitionSlot = partitionIndex;
                ctx.hoveredStorageBus = storageBus;

                if (!partItem.isEmpty()) {
                    ctx.hoveredContentStack = partItem;
                    ctx.hoveredContentX = absMouseX;
                    ctx.hoveredContentY = absMouseY;
                }
            }
        }
    }

    // ========================================
    // IO MODE RENDERING
    // ========================================

    /**
     * Draw a colored dot indicating IO mode.
     *
     * @param x Left edge of dot area
     * @param y Top edge of dot area
     * @param size Size of dot area
     * @param accessMode 0=NO_ACCESS, 1=READ, 2=WRITE, 3=READ_WRITE
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
            drawDiagonalSplit(x, y, size, color1, color2);
        } else {
            Gui.drawRect(x, y, x + size, y + size, color1);
        }
    }

    private void drawDiagonalSplit(int x, int y, int size, int color1, int color2) {
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int color = (px + py < size) ? color1 : color2;
                Gui.drawRect(x + px, y + py, x + px + 1, y + py + 1, color);
            }
        }
    }
}
