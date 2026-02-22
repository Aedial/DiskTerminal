package com.cellterminal.gui.subnet;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import com.cellterminal.client.SubnetConnectionRow;
import com.cellterminal.client.SubnetInfo;
import com.cellterminal.gui.GuiConstants;


/**
 * Renderer for the subnet overview mode.
 * Displays a list of connected subnets in the same style as Storage Bus Partition tab (Tab 5):
 * - Header row: Icon, name, location, [Load] button
 * - Connection rows: Filter items displayed under each connection with tree lines
 *
 * The renderer takes a flattened list where SubnetInfo objects are headers
 * and SubnetConnectionRow objects are connection/filter detail rows.
 */
public class SubnetOverviewRenderer {

    // Layout constants - matching StorageBusRenderer style
    private static final int ROW_HEIGHT = 18;
    private static final int ICON_X = GuiConstants.GUI_INDENT;
    private static final int STAR_X = 6;  // Left sidebar, same as upgrade icons
    private static final int STAR_WIDTH = 18;
    private static final int NAME_X = GuiConstants.GUI_INDENT + 18;  // After icon
    private static final int LOAD_BUTTON_WIDTH = 28;
    private static final int LOAD_BUTTON_MARGIN = 4;
    private static final int LOAD_BUTTON_Y_OFFSET = 0;

    // Connection row layout (filter items under header)
    private static final int TREE_LINE_X = GuiConstants.GUI_INDENT + 7;
    private static final int FILTER_SLOTS_X = GuiConstants.CELL_INDENT + 4;
    private static final int FILTER_SLOTS_PER_ROW = 9;
    private static final int MINI_SLOT_SIZE = 16;

    // Colors
    private static final int COLOR_NAME_NORMAL = GuiConstants.COLOR_TEXT_NORMAL;
    private static final int COLOR_NAME_CUSTOM = 0xFF2E7D32;   // Dark green for custom names
    private static final int COLOR_NAME_INACCESSIBLE = 0xFF909090;  // Medium gray
    private static final int COLOR_OUTBOUND = 0xFF2E7D32;      // Dark green arrow (out)
    private static final int COLOR_INBOUND = 0xFF1565C0;       // Dark blue arrow (in)
    private static final int COLOR_TREE_LINE = GuiConstants.COLOR_TREE_LINE;
    private static final int COLOR_MAIN_NETWORK = 0xFF00838F;  // Dark cyan for main network
    private static final int COLOR_FAVORITE_ON = 0xFFCC9900;   // Darker amber for better contrast with C6C6C6
    private static final int COLOR_FAVORITE_OFF = 0xFF505050;  // Dark gray for better contrast

    private final FontRenderer fontRenderer;
    private final RenderItem itemRender;

    // Hover tracking
    private int hoveredLineIndex = -1;
    private HoverZone hoveredZone = HoverZone.NONE;
    private SubnetInfo hoveredSubnet = null;
    private SubnetConnectionRow hoveredConnectionRow = null;
    private int hoveredFilterSlot = -1;
    private ItemStack hoveredFilterStack = ItemStack.EMPTY;

    // Rename editing state
    private SubnetInfo editingSubnet = null;  // The subnet currently being renamed
    private String editingText = "";           // Current text in the rename field
    private int editingCursorPos = 0;          // Cursor position for text editing
    private int editingY = 0;                  // Y position of the editing field

    public enum HoverZone {
        NONE,
        STAR,
        NAME,
        LOAD_BUTTON,
        FILTER_SLOT,
        ENTRY   // General entry hover (for double-click highlight)
    }

    public SubnetOverviewRenderer(FontRenderer fontRenderer, RenderItem itemRender) {
        this.fontRenderer = fontRenderer;
        this.itemRender = itemRender;
    }

    /**
     * Draw the subnet overview content using a flattened line list.
     *
     * @param lines Mixed list of SubnetInfo (headers) and SubnetConnectionRow (filter rows)
     * @param currentScroll Current scroll position
     * @param rowsVisible Number of visible rows
     * @param relMouseX Mouse X relative to GUI
     * @param relMouseY Mouse Y relative to GUI
     * @param guiLeft Left edge of GUI
     * @param guiTop Top edge of GUI
     * @return The hovered line index, or -1 if none
     */
    public int draw(List<Object> lines, int currentScroll, int rowsVisible,
                    int relMouseX, int relMouseY, int guiLeft, int guiTop) {
        // Reset hover state
        this.hoveredLineIndex = -1;
        this.hoveredZone = HoverZone.NONE;
        this.hoveredSubnet = null;
        this.hoveredConnectionRow = null;
        this.hoveredFilterSlot = -1;
        this.hoveredFilterStack = ItemStack.EMPTY;

        int y = GuiConstants.CONTENT_START_Y;
        int totalLines = lines.size();
        int visibleTop = GuiConstants.CONTENT_START_Y;
        int visibleBottom = GuiConstants.CONTENT_START_Y + rowsVisible * ROW_HEIGHT;

        // Draw each visible line
        for (int i = 0; i < rowsVisible && currentScroll + i < totalLines; i++) {
            Object line = lines.get(currentScroll + i);
            int lineIndex = currentScroll + i;

            boolean isHovered = relMouseX >= GuiConstants.HOVER_LEFT_EDGE
                && relMouseX < GuiConstants.HOVER_RIGHT_EDGE
                && relMouseY >= y && relMouseY < y + ROW_HEIGHT;

            if (line instanceof SubnetInfo) {
                SubnetInfo subnet = (SubnetInfo) line;

                if (isHovered) {
                    this.hoveredLineIndex = lineIndex;
                    this.hoveredSubnet = subnet;
                    this.hoveredZone = determineHeaderHoverZone(relMouseX, subnet);
                    Gui.drawRect(GuiConstants.HOVER_LEFT_EDGE, y - 1, GuiConstants.CONTENT_RIGHT_EDGE, y + ROW_HEIGHT - 1, GuiConstants.COLOR_ROW_HOVER);
                }

                // Draw separator line above header (except for first)
                if (lineIndex > 0) {
                    Gui.drawRect(GuiConstants.GUI_INDENT, y - 1, GuiConstants.CONTENT_RIGHT_EDGE, y, GuiConstants.COLOR_SEPARATOR);
                }

                // Draw tree line down to connection rows if this subnet has connections
                boolean hasConnectionRowsBelow = hasConnectionRowBelow(lines, lineIndex);
                if (hasConnectionRowsBelow) {
                    Gui.drawRect(TREE_LINE_X, y + ROW_HEIGHT - 1, TREE_LINE_X + 1, y + ROW_HEIGHT, COLOR_TREE_LINE);
                }

                drawSubnetHeader(subnet, y, relMouseX, relMouseY, isHovered, guiLeft, guiTop);

            } else if (line instanceof SubnetConnectionRow) {
                SubnetConnectionRow row = (SubnetConnectionRow) line;

                if (isHovered) {
                    this.hoveredLineIndex = lineIndex;
                    this.hoveredConnectionRow = row;
                    this.hoveredSubnet = row.getSubnet();
                    // Check if hovering a specific filter slot
                    int slot = getHoveredFilterSlot(relMouseX, relMouseY, y);
                    if (slot >= 0 && slot < row.getFilterCountInRow()) {
                        this.hoveredZone = HoverZone.FILTER_SLOT;
                        this.hoveredFilterSlot = slot;
                        this.hoveredFilterStack = row.getFilterAt(slot);
                    } else {
                        this.hoveredZone = HoverZone.ENTRY;
                    }
                    Gui.drawRect(GuiConstants.HOVER_LEFT_EDGE, y - 1, GuiConstants.CONTENT_RIGHT_EDGE, y + ROW_HEIGHT - 1, GuiConstants.COLOR_ROW_HOVER);
                }

                // Build line context for tree drawing
                LineContext ctx = buildLineContext(lines, lineIndex, i, rowsVisible, totalLines);
                drawConnectionRow(row, y, relMouseX, relMouseY, isHovered, ctx, visibleTop, visibleBottom, guiLeft, guiTop);
            }

            y += ROW_HEIGHT;
        }

        // Draw "no subnets" message if empty
        if (totalLines == 0) {
            String noSubnets = I18n.format("cellterminal.subnet.none");
            int textWidth = fontRenderer.getStringWidth(noSubnets);
            fontRenderer.drawString(noSubnets, (GuiConstants.CONTENT_RIGHT_EDGE - GuiConstants.HOVER_LEFT_EDGE - textWidth) / 2 + GuiConstants.HOVER_LEFT_EDGE, GuiConstants.CONTENT_START_Y + 20, 0xFF606060);
        }

        return hoveredLineIndex;
    }

    /**
     * Trim text to fit within a maximum width, adding ellipsis if truncated.
     */
    private String trimTextToWidth(String text, int maxWidth) {
        if (fontRenderer.getStringWidth(text) <= maxWidth) return text;

        while (fontRenderer.getStringWidth(text + "...") > maxWidth && text.length() > 1) {
            text = text.substring(0, text.length() - 1);
        }

        return text + "...";
    }

    // ========================================
    // HEADER RENDERING (matches StorageBusRenderer.drawStorageBusPartitionHeader style)
    // ========================================

    private void drawSubnetHeader(SubnetInfo subnet, int y, int relMouseX, int relMouseY,
                                   boolean isHovered, int guiLeft, int guiTop) {
        boolean isMainNetwork = subnet.isMainNetwork();
        boolean canLoad = subnet.isAccessible() && subnet.hasPower();

        // Calculate button position from right edge
        String loadText = I18n.format("cellterminal.subnet.load");
        int loadTextWidth = fontRenderer.getStringWidth(loadText);
        int loadButtonX = GuiConstants.CONTENT_RIGHT_EDGE - LOAD_BUTTON_MARGIN - loadTextWidth;
        int nameMaxWidth = loadButtonX - NAME_X - 4;

        // Draw favorite star on left sidebar (same position as upgrade icons)
        boolean starHovered = isHovered && hoveredZone == HoverZone.STAR;
        int starColor = subnet.isFavorite() ? COLOR_FAVORITE_ON : COLOR_FAVORITE_OFF;
        if (starHovered) starColor = subnet.isFavorite() ? 0xFFDDB000 : 0xFF707070;
        GlStateManager.pushMatrix();
        GlStateManager.scale(2.0F, 2.0F, 1.0F);  // Scale up for better visibility
        fontRenderer.drawString("★", STAR_X / 2, y / 2, starColor);
        GlStateManager.popMatrix();

        // Draw subnet icon (connection icon for subnets, house for main network)
        if (isMainNetwork) {
            GlStateManager.pushMatrix();
            GlStateManager.scale(2.0F, 2.0F, 1.0F);
            fontRenderer.drawString("⌂", (ICON_X + 5) / 2, (y - 1) / 2, COLOR_MAIN_NETWORK);
            GlStateManager.popMatrix();
        } else {
            ItemStack icon = subnet.getConnections().isEmpty() ? ItemStack.EMPTY
                : subnet.getConnections().get(0).getLocalIcon();
            if (!icon.isEmpty()) renderItemStack(icon, ICON_X, y);
        }

        // Draw name (first line)
        int nameColor = COLOR_NAME_NORMAL;
        if (isMainNetwork) {
            nameColor = COLOR_MAIN_NETWORK;
        } else if (!canLoad) {
            nameColor = COLOR_NAME_INACCESSIBLE;
        } else if (subnet.hasCustomName()) {
            nameColor = COLOR_NAME_CUSTOM;
        }

        String name = subnet.getDisplayName();
        if (fontRenderer.getStringWidth(name) > nameMaxWidth) {
            while (fontRenderer.getStringWidth(name + "...") > nameMaxWidth && name.length() > 3) {
                name = name.substring(0, name.length() - 1);
            }
            name = name + "...";
        }

        // Main network has no location line, so center the name vertically
        // Row height is 18, font height is ~9, so centered position is y + (18-9)/2 = y + 4.5 ~ y + 5
        int nameY = isMainNetwork ? (y + 5) : (y + 1);
        fontRenderer.drawString(name, NAME_X, nameY, nameColor);

        // Draw location (second line) - skip for main network
        // Position text can extend to the full content width since it's on a separate line from the Load button
        if (!isMainNetwork) {
            int locationMaxWidth = GuiConstants.CONTENT_RIGHT_EDGE - NAME_X - 4;
            String location = I18n.format("cellterminal.subnet.pos",
                subnet.getPrimaryPos().getX(),
                subnet.getPrimaryPos().getY(),
                subnet.getPrimaryPos().getZ());
            if (fontRenderer.getStringWidth(location) > locationMaxWidth) {
                location = trimTextToWidth(location, locationMaxWidth);
            }
            fontRenderer.drawString(location, NAME_X, y + 9, GuiConstants.COLOR_TEXT_SECONDARY);
        }

        // Draw Load button
        boolean loadButtonHovered = isHovered && hoveredZone == HoverZone.LOAD_BUTTON;
        drawLoadButton(loadButtonX, y + LOAD_BUTTON_Y_OFFSET, loadButtonHovered, canLoad);
    }

    /**
     * Draw a small "Load" button styled like AE2 buttons.
     */
    private void drawLoadButton(int x, int y, boolean isHovered, boolean isEnabled) {
        int buttonHeight = 10;

        String text = I18n.format("cellterminal.subnet.load");
        int textWidth = fontRenderer.getStringWidth(text);
        int buttonWidth = textWidth + LOAD_BUTTON_MARGIN;

        // Background
        int bgColor;
        if (!isEnabled) {
            bgColor = 0xFF808080;  // Gray for disabled
        } else if (isHovered) {
            bgColor = 0xFF4A90D9;  // Light blue hover
        } else {
            bgColor = 0xFF3B7DC9;  // Blue
        }
        Gui.drawRect(x, y, x + buttonWidth, y + buttonHeight, bgColor);

        // Border (3D effect)
        int highlightColor = isEnabled ? 0xFF6BA5E7 : 0xFFA0A0A0;
        int shadowColor = isEnabled ? 0xFF2A5B8A : 0xFF606060;
        Gui.drawRect(x, y, x + buttonWidth, y + 1, highlightColor);  // top
        Gui.drawRect(x, y, x + 1, y + buttonHeight, highlightColor);  // left
        Gui.drawRect(x, y + buttonHeight - 1, x + buttonWidth, y + buttonHeight, shadowColor);  // bottom
        Gui.drawRect(x + buttonWidth - 1, y, x + buttonWidth, y + buttonHeight, shadowColor);  // right

        // Text
        int textX = x + LOAD_BUTTON_MARGIN / 2;
        int textY = y + 1;
        int textColor = isEnabled ? 0xFFFFFFFF : 0xFFC0C0C0;
        fontRenderer.drawString(text, textX, textY, textColor);
    }

    // ========================================
    // CONNECTION ROW RENDERING
    // ========================================

    private void drawConnectionRow(SubnetConnectionRow row, int y, int relMouseX, int relMouseY,
                                    boolean isHovered, LineContext ctx, int visibleTop, int visibleBottom,
                                    int guiLeft, int guiTop) {
        SubnetInfo.ConnectionPoint conn = row.getConnection();

        // Draw tree lines
        drawTreeLines(y, ctx, visibleTop, visibleBottom);

        // Draw connection direction indicator on first row
        if (row.isFirstRowForConnection()) drawConnectionIndicator(conn, y);

        // Draw filter slots
        drawFilterSlots(row, y, relMouseX, relMouseY, isHovered, guiLeft, guiTop);
    }

    private void drawTreeLines(int y, LineContext ctx, int visibleTop, int visibleBottom) {
        int lineX = TREE_LINE_X;

        // Vertical line - extends based on position in group
        int lineTop = ctx.isFirstVisibleRow && ctx.hasContentAbove ? visibleTop : y - 4;
        int lineBottom = ctx.isLastInGroup ? y + 9 : (ctx.isLastVisibleRow && ctx.hasContentBelow ? visibleBottom : y + ROW_HEIGHT);

        if (lineTop < visibleTop) lineTop = visibleTop;

        // Vertical line
        Gui.drawRect(lineX, lineTop, lineX + 1, lineBottom, COLOR_TREE_LINE);

        // Horizontal branch
        Gui.drawRect(lineX, y + 8, lineX + 12, y + 9, COLOR_TREE_LINE);
    }

    private void drawConnectionIndicator(SubnetInfo.ConnectionPoint conn, int y) {
        // Draw direction arrow only
        int arrowX = TREE_LINE_X + 2;
        String arrow = conn.isOutbound() ? "→" : "←";
        int arrowColor = conn.isOutbound() ? COLOR_OUTBOUND : COLOR_INBOUND;
        fontRenderer.drawString(arrow, arrowX, y + 4, arrowColor);
    }

    private void drawFilterSlots(SubnetConnectionRow row, int y, int relMouseX, int relMouseY,
                                  boolean isHovered, int guiLeft, int guiTop) {
        int filterCount = row.getFilterCountInRow();

        // If no filters, show "No filter" text with background to avoid tree line overlap
        if (row.getTotalFilterCount() == 0) {
            String noFilterText = I18n.format("cellterminal.subnet.no_filter");
            int textWidth = fontRenderer.getStringWidth(noFilterText);
            // Draw background to separate from tree line
            Gui.drawRect(FILTER_SLOTS_X - 2, y + 2, FILTER_SLOTS_X + textWidth + 2, y + 14, GuiConstants.COLOR_SLOT_BACKGROUND);
            fontRenderer.drawString(noFilterText, FILTER_SLOTS_X, y + 4, 0xFFE0E0E0);
            return;
        }

        // Draw filter item slots (same style as storage bus partition slots)
        for (int i = 0; i < FILTER_SLOTS_PER_ROW; i++) {
            int slotX = FILTER_SLOTS_X + (i * MINI_SLOT_SIZE);

            // Only draw slots for available filter items
            if (i >= filterCount) break;

            // Draw slot background
            drawSlotBackground(slotX, y);

            ItemStack filter = row.getFilterAt(i);
            if (!filter.isEmpty()) {
                GlStateManager.pushMatrix();
                GlStateManager.enableDepth();
                renderItemStack(filter, slotX, y);
                GlStateManager.popMatrix();
            }

            // Hover highlight
            if (isHovered && hoveredZone == HoverZone.FILTER_SLOT && hoveredFilterSlot == i) {
                drawSlotHoverHighlight(slotX, y);
            }
        }
    }

    private void drawSlotBackground(int x, int y) {
        int size = MINI_SLOT_SIZE;
        Gui.drawRect(x, y, x + size, y + size, GuiConstants.COLOR_SLOT_BACKGROUND);
        Gui.drawRect(x, y, x + size - 1, y + 1, GuiConstants.COLOR_SLOT_BORDER_DARK);
        Gui.drawRect(x, y, x + 1, y + size - 1, GuiConstants.COLOR_SLOT_BORDER_DARK);
        Gui.drawRect(x + 1, y + size - 1, x + size, y + size, GuiConstants.COLOR_SLOT_BORDER_LIGHT);
        Gui.drawRect(x + size - 1, y + 1, x + size, y + size - 1, GuiConstants.COLOR_SLOT_BORDER_LIGHT);
    }

    private void drawSlotHoverHighlight(int x, int y) {
        int inner = MINI_SLOT_SIZE - 1;
        Gui.drawRect(x + 1, y + 1, x + inner, y + inner, GuiConstants.COLOR_HOVER_HIGHLIGHT);
    }

    private void renderItemStack(ItemStack stack, int x, int y) {
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

    // ========================================
    // HOVER ZONE DETECTION
    // ========================================

    private HoverZone determineHeaderHoverZone(int relMouseX, SubnetInfo subnet) {
        int loadButtonX = GuiConstants.CONTENT_RIGHT_EDGE - LOAD_BUTTON_MARGIN - LOAD_BUTTON_WIDTH;

        if (relMouseX >= loadButtonX && relMouseX < loadButtonX + LOAD_BUTTON_WIDTH) return HoverZone.LOAD_BUTTON;
        if (relMouseX >= STAR_X && relMouseX < STAR_X + STAR_WIDTH) return HoverZone.STAR;
        if (relMouseX >= NAME_X && relMouseX < loadButtonX) return HoverZone.NAME;

        return HoverZone.ENTRY;
    }

    /**
     * Get the currently hovered entry index.
     * @return Index of hovered subnet, or -1 if none
     */
    private int getHoveredFilterSlot(int relMouseX, int relMouseY, int rowY) {
        if (relMouseX < FILTER_SLOTS_X) return -1;

        int slotIndex = (relMouseX - FILTER_SLOTS_X) / MINI_SLOT_SIZE;
        if (slotIndex < 0 || slotIndex >= FILTER_SLOTS_PER_ROW) return -1;

        // Check Y is within slot bounds
        if (relMouseY < rowY || relMouseY >= rowY + MINI_SLOT_SIZE) return -1;

        return slotIndex;
    }

    // ========================================
    // LINE CONTEXT HELPERS
    // ========================================

    private LineContext buildLineContext(List<Object> lines, int lineIndex, int visibleIndex,
                                          int rowsVisible, int totalLines) {
        LineContext ctx = new LineContext();
        ctx.isFirstInGroup = isFirstConnectionRowOfSubnet(lines, lineIndex);
        ctx.isLastInGroup = isLastConnectionRowOfSubnet(lines, lineIndex);
        ctx.isFirstVisibleRow = (visibleIndex == 0);
        ctx.isLastVisibleRow = (visibleIndex == rowsVisible - 1) || (lineIndex == totalLines - 1);
        ctx.hasContentAbove = hasConnectionRowAbove(lines, lineIndex);
        ctx.hasContentBelow = hasConnectionRowBelow(lines, lineIndex);

        return ctx;
    }

    private boolean isFirstConnectionRowOfSubnet(List<Object> lines, int lineIndex) {
        if (!(lines.get(lineIndex) instanceof SubnetConnectionRow)) return false;
        if (lineIndex == 0) return true;

        return lines.get(lineIndex - 1) instanceof SubnetInfo;
    }

    private boolean isLastConnectionRowOfSubnet(List<Object> lines, int lineIndex) {
        if (!(lines.get(lineIndex) instanceof SubnetConnectionRow)) return false;
        if (lineIndex >= lines.size() - 1) return true;

        return lines.get(lineIndex + 1) instanceof SubnetInfo;
    }

    private boolean hasConnectionRowAbove(List<Object> lines, int lineIndex) {
        if (lineIndex == 0) return false;

        return lines.get(lineIndex - 1) instanceof SubnetConnectionRow;
    }

    private boolean hasConnectionRowBelow(List<Object> lines, int lineIndex) {
        if (lineIndex >= lines.size() - 1) return false;

        return lines.get(lineIndex + 1) instanceof SubnetConnectionRow;
    }

    private static class LineContext {
        boolean isFirstInGroup;
        boolean isLastInGroup;
        boolean isFirstVisibleRow;
        boolean isLastVisibleRow;
        boolean hasContentAbove;
        boolean hasContentBelow;
    }

    // ========================================
    // PUBLIC ACCESSORS
    // ========================================

    /**
     * Get the currently hovered line index.
     */
    public int getHoveredLineIndex() {
        return hoveredLineIndex;
    }

    /**
     * Get the currently hovered entry index (for backward compatibility).
     * @deprecated Use getHoveredLineIndex() instead
     */
    @Deprecated
    public int getHoveredEntryIndex() {
        return hoveredLineIndex;
    }

    /**
     * Get the currently hovered zone.
     */
    public HoverZone getHoveredZone() {
        return hoveredZone;
    }

    /**
     * Get the currently hovered subnet (from header or connection row).
     */
    public SubnetInfo getHoveredSubnet() {
        return hoveredSubnet;
    }

    /**
     * Get the currently hovered connection row (if hovering a filter row).
     */
    public SubnetConnectionRow getHoveredConnectionRow() {
        return hoveredConnectionRow;
    }

    /**
     * Get the hovered filter slot index (-1 if not hovering a slot).
     */
    public int getHoveredFilterSlot() {
        return hoveredFilterSlot;
    }

    /**
     * Get the hovered filter item stack.
     */
    public ItemStack getHoveredFilterStack() {
        return hoveredFilterStack;
    }

    /**
     * Get the Y position for a subnet row.
     * @param subnet The subnet to find
     * @param lines The list of subnet lines
     * @param currentScroll The current scroll position
     * @return The Y position, or -1 if not visible
     */
    public int getRowYForSubnet(SubnetInfo subnet, List<Object> lines, int currentScroll) {
        if (subnet == null) return -1;

        for (int i = 0; i < lines.size(); i++) {
            Object line = lines.get(i);
            if (line instanceof SubnetInfo && ((SubnetInfo) line).getId() == subnet.getId()) {
                int visualIndex = i - currentScroll;
                if (visualIndex < 0) return -1; // Not visible (above scroll)

                return GuiConstants.CONTENT_START_Y + visualIndex * ROW_HEIGHT;
            }
        }

        return -1;
    }

    // ========================================
    // RENAME EDITING
    // ========================================

    /**
     * Check if currently editing a subnet name.
     */
    public boolean isEditing() {
        return editingSubnet != null;
    }

    /**
     * Get the subnet being edited.
     */
    public SubnetInfo getEditingSubnet() {
        return editingSubnet;
    }

    /**
     * Start editing a subnet's name.
     * @param subnet The subnet to rename
     * @param y The Y position where the edit field should appear
     */
    public void startEditing(SubnetInfo subnet, int y) {
        if (subnet == null || subnet.isMainNetwork()) return;

        this.editingSubnet = subnet;
        this.editingText = subnet.hasCustomName() ? subnet.getCustomName() : "";
        this.editingCursorPos = editingText.length();
        this.editingY = y;
    }

    /**
     * Stop editing and return the edited text.
     * @return The edited text, or null if not editing
     */
    public String stopEditing() {
        if (editingSubnet == null) return null;

        String result = editingText.trim();
        editingSubnet = null;
        editingText = "";
        editingCursorPos = 0;

        return result;
    }

    /**
     * Cancel editing without saving.
     */
    public void cancelEditing() {
        editingSubnet = null;
        editingText = "";
        editingCursorPos = 0;
    }

    /**
     * Get the current editing text.
     */
    public String getEditingText() {
        return editingText;
    }

    /**
     * Handle keyboard input for rename editing.
     * @param typedChar The character typed
     * @param keyCode The key code
     * @return true if the input was handled
     */
    public boolean handleKeyTyped(char typedChar, int keyCode) {
        if (editingSubnet == null) return false;

        // Enter - confirm
        if (keyCode == Keyboard.KEY_RETURN) return false;  // Return false to let GUI handle confirmation

        if (keyCode == Keyboard.KEY_ESCAPE) {
            cancelEditing();
            return true;
        }

        if (keyCode == Keyboard.KEY_BACK && editingCursorPos > 0) {
            editingText = editingText.substring(0, editingCursorPos - 1)
                + editingText.substring(editingCursorPos);
            editingCursorPos--;
            return true;
        }

        if (keyCode == Keyboard.KEY_DELETE && editingCursorPos < editingText.length()) {
            editingText = editingText.substring(0, editingCursorPos)
                + editingText.substring(editingCursorPos + 1);
            return true;
        }

        if (keyCode == Keyboard.KEY_LEFT && editingCursorPos > 0) {
            editingCursorPos--;
            return true;
        }

        if (keyCode == Keyboard.KEY_RIGHT && editingCursorPos < editingText.length()) {
            editingCursorPos++;
            return true;
        }

        if (keyCode == Keyboard.KEY_HOME) {
            editingCursorPos = 0;
            return true;
        }

        if (keyCode == Keyboard.KEY_END) {
            editingCursorPos = editingText.length();
            return true;
        }

        // Printable characters
        if (typedChar >= 32 && typedChar < 127 && editingText.length() < 50) {
            editingText = editingText.substring(0, editingCursorPos)
                + typedChar
                + editingText.substring(editingCursorPos);
            editingCursorPos++;
            return true;
        }

        return false;
    }

    /**
     * Draw the rename text field if editing.
     * Call this after drawing the subnet list.
     */
    public void drawRenameField() {
        if (editingSubnet == null) return;

        int loadButtonX = GuiConstants.CONTENT_RIGHT_EDGE - LOAD_BUTTON_MARGIN - LOAD_BUTTON_WIDTH;
        // Offset by -2 so the text inside the field (at x + 2) aligns with the original name position
        int x = NAME_X - 2;
        int y = editingY + 1;  // Vertically aligned with text (y + 1 is where text draws)
        int width = loadButtonX - x - 4;
        int height = 9;  // Reduced height to align with text

        // Draw background (E0E0E0 to match terminal background, with dark border)
        Gui.drawRect(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF373737);
        Gui.drawRect(x, y, x + width, y + height, 0xFFE0E0E0);

        // Draw text
        String displayText = editingText;
        int textWidth = fontRenderer.getStringWidth(displayText);

        // Scroll text if too long
        int visibleWidth = width - 4;
        int textX = x + 2;
        if (textWidth > visibleWidth) {
            int cursorX = fontRenderer.getStringWidth(displayText.substring(0, editingCursorPos));
            int scrollOffset = Math.max(0, cursorX - visibleWidth + 10);
            textX -= scrollOffset;
        }

        fontRenderer.drawString(displayText, textX, y, 0xFF000000);

        // Draw cursor (blinking)
        long time = System.currentTimeMillis();
        if ((time / 500) % 2 == 0) {
            int cursorX = x + 2 + fontRenderer.getStringWidth(displayText.substring(0, editingCursorPos));
            Gui.drawRect(cursorX, y, cursorX + 1, y + height - 1, 0xFF000000);
        }
    }

    /**
     * Get tooltip lines for the currently hovered element.
     * @param line The line object being hovered (SubnetInfo or SubnetConnectionRow)
     * @return List of tooltip lines
     */
    public List<String> getTooltip(Object line) {
        List<String> tooltipLines = new ArrayList<>();

        if (line == null) return tooltipLines;

        // Delegate based on line type
        if (line instanceof SubnetInfo) {
            return getSubnetTooltip((SubnetInfo) line);
        } else if (line instanceof SubnetConnectionRow) {
            return getConnectionRowTooltip((SubnetConnectionRow) line);
        }

        return tooltipLines;
    }

    private List<String> getSubnetTooltip(SubnetInfo subnet) {
        List<String> lines = new ArrayList<>();

        // Main network tooltip
        if (subnet.isMainNetwork()) {
            lines.add(I18n.format("cellterminal.subnet.main_network"));
            lines.add("§e" + I18n.format("cellterminal.subnet.click_load_main"));

            return lines;
        }

        switch (hoveredZone) {
            case STAR:
                lines.add(I18n.format("cellterminal.subnet.controls.star"));
                break;

            case NAME:
                // Show general entry info on name (more useful than just rename hint)
                lines.add(subnet.getDisplayName());
                lines.add("§7" + I18n.format("cellterminal.subnet.pos",
                    subnet.getPrimaryPos().getX(),
                    subnet.getPrimaryPos().getY(),
                    subnet.getPrimaryPos().getZ()));
                lines.add("§7" + I18n.format("cellterminal.subnet.dim", subnet.getDimension()));
                if (subnet.getOutboundCount() > 0) {
                    lines.add("§a" + I18n.format("cellterminal.subnet.outbound", subnet.getOutboundCount()));
                }
                if (subnet.getInboundCount() > 0) {
                    lines.add("§9" + I18n.format("cellterminal.subnet.inbound", subnet.getInboundCount()));
                }
                lines.add("");
                lines.add("§e" + I18n.format("cellterminal.subnet.right_click_rename"));
                break;

            case LOAD_BUTTON:
                if (!subnet.hasPower()) {
                    lines.add("§c" + I18n.format("cellterminal.subnet.no_power"));
                } else if (!subnet.isAccessible()) {
                    lines.add("§6" + I18n.format("cellterminal.subnet.no_access"));
                } else {
                    lines.add(I18n.format("cellterminal.subnet.load.tooltip"));
                }
                break;

            case ENTRY:
                lines.add(subnet.getDisplayName());
                lines.add("§7" + I18n.format("cellterminal.subnet.pos",
                    subnet.getPrimaryPos().getX(),
                    subnet.getPrimaryPos().getY(),
                    subnet.getPrimaryPos().getZ()));
                lines.add("§7" + I18n.format("cellterminal.subnet.dim", subnet.getDimension()));

                // Show connection counts
                if (subnet.getOutboundCount() > 0) {
                    lines.add("§a" + I18n.format("cellterminal.subnet.outbound", subnet.getOutboundCount()));
                }
                if (subnet.getInboundCount() > 0) {
                    lines.add("§9" + I18n.format("cellterminal.subnet.inbound", subnet.getInboundCount()));
                }

                lines.add("");
                lines.add("§e" + I18n.format("cellterminal.subnet.double_click_highlight"));
                if (!subnet.hasPower()) {
                    lines.add("§c" + I18n.format("cellterminal.subnet.no_power"));
                }
                if (subnet.hasSecurity() && !subnet.isAccessible()) {
                    lines.add("§6" + I18n.format("cellterminal.subnet.no_access"));
                }
                break;

            default:
                break;
        }

        return lines;
    }

    private List<String> getConnectionRowTooltip(SubnetConnectionRow row) {
        List<String> lines = new ArrayList<>();

        if (hoveredZone == HoverZone.FILTER_SLOT && !hoveredFilterStack.isEmpty()) return lines;

        // General connection row tooltip
        SubnetInfo.ConnectionPoint conn = row.getConnection();
        String direction = conn.isOutbound()
            ? I18n.format("cellterminal.subnet.direction.outbound")
            : I18n.format("cellterminal.subnet.direction.inbound");
        lines.add(direction);

        // Show connection position
        lines.add("§7" + I18n.format("cellterminal.subnet.connection.pos",
            conn.getPos().getX(), conn.getPos().getY(), conn.getPos().getZ()));

        // Show filter count
        int nonEmptyCount = 0;
        for (ItemStack stack : conn.getFilter()) {
            if (!stack.isEmpty()) nonEmptyCount++;
        }

        if (nonEmptyCount > 0) {
            lines.add("§7" + I18n.format("cellterminal.subnet.filter_count", nonEmptyCount));
        } else {
            lines.add("§7" + I18n.format("cellterminal.subnet.no_filter"));
        }

        return lines;
    }
}
