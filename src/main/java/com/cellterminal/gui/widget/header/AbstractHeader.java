package com.cellterminal.gui.widget.header;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.widget.AbstractWidget;
import com.cellterminal.gui.widget.CardsDisplay;
import com.cellterminal.gui.widget.DoubleClickTracker;


/**
 * Base class for all header widgets in the Cell Terminal GUI.
 * <p>
 * A header represents the top row of a storage group (drive, chest, storage bus,
 * or temp area cell slot). It shows:
 * <ul>
 *   <li>Icon (block/item) at {@link GuiConstants#GUI_INDENT}</li>
 *   <li>Name (clickable for rename) at {@link GuiConstants#HEADER_NAME_X}</li>
 *   <li>Header hover highlight when the mouse is over the header area</li>
 *   <li>Tree connector at the bottom for linking to content rows below</li>
 * </ul>
 *
 * Subclasses add location text, expand/collapse, priority field positioning,
 * IO mode buttons, cell slots, etc.
 *
 * <h3>Tree connector</h3>
 * The header draws a 1px vertical connector at the bottom of the row when
 * content rows follow below. The first content line uses
 * {@link #getConnectorY()} as its {@code lineAboveCutY}.
 *
 * <h3>Priority field</h3>
 * Priority fields are managed externally by PriorityFieldManager. The header
 * does not own or render the field; it only reserves visual space for it.
 *
 * @see StorageHeader
 * @see StorageBusHeader
 * @see TempAreaHeader
 */
public abstract class AbstractHeader extends AbstractWidget {

    /** X position of the tree connector line (same as AbstractLine.TREE_LINE_X) */
    protected static final int TREE_LINE_X = GuiConstants.GUI_INDENT + 7;

    protected final FontRenderer fontRenderer;
    protected final RenderItem itemRender;

    /** Supplier for the icon ItemStack (block icon for storages, cell for temp area) */
    protected Supplier<ItemStack> iconSupplier;

    /** Supplier for the display name */
    protected Supplier<String> nameSupplier;

    /** Supplier for whether the name is a custom (user-set) name */
    protected Supplier<Boolean> hasCustomNameSupplier;

    /** Whether to draw the tree connector at the bottom (content follows below) */
    protected boolean drawConnector = false;

    /** Maximum pixel width for name text before truncation */
    protected int nameMaxWidth = GuiConstants.HEADER_NAME_MAX_WIDTH;

    /** Callback when the name area is clicked (for rename) */
    protected Runnable onNameClick;

    /** Callback when the name area is double-clicked (for highlight in world) */
    protected Runnable onNameDoubleClick;

    /** Target ID for double-click tracking (stored in DoubleClickTracker for persistence across rebuilds) */
    protected long doubleClickTargetId = -1;

    /** Callback when the header row is clicked (for area selection / quick add) */
    protected Runnable onHeaderClick;

    /** Supplier for the selection state (selected headers get a highlight overlay) */
    protected Supplier<Boolean> selectedSupplier;

    /** Cards display widget for upgrade icons (optional, used by StorageBus and TempArea headers) */
    protected CardsDisplay cardsDisplay;

    // Hover state (computed during draw)
    protected boolean nameHovered = false;
    protected boolean headerHovered = false;

    protected AbstractHeader(int y, FontRenderer fontRenderer, RenderItem itemRender) {
        super(0, y, GuiConstants.CONTENT_RIGHT_EDGE, GuiConstants.ROW_HEIGHT);
        this.fontRenderer = fontRenderer;
        this.itemRender = itemRender;
    }

    // ---- Configuration ----

    public void setIconSupplier(Supplier<ItemStack> supplier) {
        this.iconSupplier = supplier;
    }

    public void setNameSupplier(Supplier<String> supplier) {
        this.nameSupplier = supplier;
    }

    public void setHasCustomNameSupplier(Supplier<Boolean> supplier) {
        this.hasCustomNameSupplier = supplier;
    }

    public void setDrawConnector(boolean drawConnector) {
        this.drawConnector = drawConnector;
    }

    public void setNameMaxWidth(int maxWidth) {
        this.nameMaxWidth = maxWidth;
    }

    public void setOnNameClick(Runnable callback) {
        this.onNameClick = callback;
    }

    /**
     * Set the callback and target ID for double-clicking the name area (for highlight in world).
     * <p>
     * The target ID is used by {@link DoubleClickTracker} to track clicks across widget
     * rebuilds. Since widgets are recreated every frame, storing the last click time on
     * the widget instance doesn't work - we need centralized tracking keyed by target ID.
     *
     * @param callback The action to perform on double-click
     * @param targetId Unique identifier for this target (use DoubleClickTracker.storageTargetId() etc.)
     */
    public void setOnNameDoubleClick(Runnable callback, long targetId) {
        this.onNameDoubleClick = callback;
        this.doubleClickTargetId = targetId;
    }

    /**
     * Set the callback for double-clicking the name area (for highlight in world).
     * @deprecated Use {@link #setOnNameDoubleClick(Runnable, long)} instead for proper tracking
     */
    @Deprecated
    public void setOnNameDoubleClick(Runnable callback) {
        this.onNameDoubleClick = callback;
    }

    /**
     * Set the callback for clicking the header row area (for quick-add selection toggle).
     * This fires when the header row is clicked but no specific interactive element was hit.
     */
    public void setOnHeaderClick(Runnable callback) {
        this.onHeaderClick = callback;
    }

    /**
     * Set the selection state supplier. When selected, the header row gets a
     * selection highlight overlay (for batch keybind operations like quick-add).
     */
    public void setSelectedSupplier(Supplier<Boolean> supplier) {
        this.selectedSupplier = supplier;
    }

    /**
     * Set the cards display widget for upgrade icons.
     * Used by StorageBus and TempArea headers.
     */
    public void setCardsDisplay(CardsDisplay cards) {
        this.cardsDisplay = cards;
    }

    // ---- Accessors ----

    /**
     * Whether the header area is currently hovered (for click targeting by parent).
     */
    public boolean isHeaderHovered() {
        return headerHovered;
    }

    /**
     * Whether the name text is currently hovered (for rename interaction).
     */
    public boolean isNameHovered() {
        return nameHovered;
    }

    /**
     * Get the Y position of the tree connector at the bottom of this header.
     * The first content line below should use this as its {@code lineAboveCutY}.
     */
    public int getConnectorY() {
        return y + GuiConstants.HEADER_CONNECTOR_Y_OFFSET;
    }

    // ---- Rendering ----

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        nameHovered = false;
        headerHovered = false;

        // Draw horizontal separator line at the top of the header
        Gui.drawRect(GuiConstants.GUI_INDENT, y - 1, GuiConstants.CONTENT_RIGHT_EDGE, y,
            GuiConstants.COLOR_SEPARATOR);

        // Draw selection background (below everything else)
        boolean isSelected = selectedSupplier != null && selectedSupplier.get();
        if (isSelected) {
            Gui.drawRect(GuiConstants.GUI_INDENT, y, GuiConstants.CONTENT_RIGHT_EDGE,
                y + GuiConstants.ROW_HEIGHT, GuiConstants.COLOR_SELECTION);
        }

        // Subclass-specific drawing (may set right bound for hover area)
        int hoverRightBound = drawHeaderContent(mouseX, mouseY);

        // Header hover highlight
        headerHovered = mouseX >= GuiConstants.GUI_INDENT && mouseX < hoverRightBound
            && mouseY >= y && mouseY < y + GuiConstants.ROW_HEIGHT;
        if (headerHovered) {
            Gui.drawRect(GuiConstants.GUI_INDENT, y, hoverRightBound, y + GuiConstants.ROW_HEIGHT,
                GuiConstants.COLOR_STORAGE_HEADER_HOVER);
        }

        // Draw icon
        drawIcon();

        // Draw name
        drawName(mouseX, mouseY);

        // Draw tree connector if content follows
        if (drawConnector) {
            Gui.drawRect(TREE_LINE_X, y + GuiConstants.HEADER_CONNECTOR_Y_OFFSET,
                TREE_LINE_X + 1, y + GuiConstants.ROW_HEIGHT,
                GuiConstants.COLOR_TREE_LINE);
        }
    }

    /**
     * Draw subclass-specific header content (location, expand/collapse, IO mode, etc.).
     * Called before the base icon/name/connector drawing, so subclass elements
     * are drawn first (as background) and the base draws on top.
     *
     * @return The right X bound of the hoverable header area
     */
    protected abstract int drawHeaderContent(int mouseX, int mouseY);

    /**
     * Draw the icon at the left side of the header.
     * Can be overridden by subclasses (e.g., TempAreaHeader draws a cell slot instead).
     */
    protected void drawIcon() {
        ItemStack icon = iconSupplier != null ? iconSupplier.get() : ItemStack.EMPTY;
        if (!icon.isEmpty()) renderItemStack(icon, GuiConstants.GUI_INDENT, y);
    }

    /**
     * Draw the name text, handling truncation and hover detection.
     * When selected, the name is drawn in a different color (blue) to indicate selection state.
     */
    protected void drawName(int mouseX, int mouseY) {
        String name = nameSupplier != null ? nameSupplier.get() : "";
        if (name.isEmpty()) return;

        String displayName = trimTextToWidth(fontRenderer, name, nameMaxWidth);

        boolean isSelected = selectedSupplier != null && selectedSupplier.get();
        int nameColor;
        if (isSelected) {
            nameColor = GuiConstants.COLOR_NAME_SELECTED;
        } else if (hasCustomNameSupplier != null && hasCustomNameSupplier.get()) {
            nameColor = GuiConstants.COLOR_CUSTOM_NAME;
        } else {
            nameColor = GuiConstants.COLOR_TEXT_NORMAL;
        }

        fontRenderer.drawString(displayName, GuiConstants.HEADER_NAME_X, y + 1, nameColor);

        // Check name hover for rename interaction
        int nameWidth = fontRenderer.getStringWidth(displayName);
        if (mouseX >= GuiConstants.HEADER_NAME_X && mouseX < GuiConstants.HEADER_NAME_X + nameWidth
            && mouseY >= y + 1 && mouseY < y + 10) {
            nameHovered = true;
        }
    }

    // ---- Click handling ----

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible) return false;

        // Name click for rename (right-click only)
        if (button == 1 && nameHovered && onNameClick != null) {
            onNameClick.run();
            return true;
        }

        // Only left-click for remaining actions
        if (button != 0) return false;

        // Cards click takes priority over header double-click
        if (cardsDisplay != null && cardsDisplay.isHovered(mouseX, mouseY)) {
            return cardsDisplay.handleClick(mouseX, mouseY, button);
        }

        // Header double-click for highlight in world (full header area, not just name)
        // Uses centralized DoubleClickTracker since widgets are recreated every frame
        if (headerHovered && onNameDoubleClick != null && doubleClickTargetId != -1) {
            if (DoubleClickTracker.isDoubleClick(doubleClickTargetId)) {
                onNameDoubleClick.run();
                return true;
            }
            // Don't return - let it fall through in case something else needs to handle it
        }

        // Header row click for area selection / quick add (fallthrough: nothing specific was hit)
        if (headerHovered && onHeaderClick != null) {
            onHeaderClick.run();
            return true;
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

        // Check if hovering the icon
        int iconX = GuiConstants.GUI_INDENT;
        if (mouseX >= iconX && mouseX < iconX + GuiConstants.MINI_SLOT_SIZE
            && mouseY >= y && mouseY < y + GuiConstants.MINI_SLOT_SIZE) {
            ItemStack icon = iconSupplier != null ? iconSupplier.get() : ItemStack.EMPTY;
            if (!icon.isEmpty()) return icon;
        }

        return ItemStack.EMPTY;
    }

    // ---- Utilities ----

    /**
     * Delegate to the shared static utility in AbstractWidget.
     * Kept as an instance method for convenience in subclasses.
     */
    protected String trimTextToWidth(String text, int maxWidth) {
        return AbstractWidget.trimTextToWidth(fontRenderer, text, maxWidth);
    }

    protected void renderItemStack(ItemStack stack, int renderX, int renderY) {
        AbstractWidget.renderItemStack(itemRender, stack, renderX, renderY);
    }
}
