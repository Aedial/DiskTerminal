package com.cellterminal.gui.widget.header;

import java.util.function.Supplier;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderItem;

import com.cellterminal.gui.GuiConstants;


/**
 * Storage header widget for the Cell Terminal (tabs 0-2).
 * <p>
 * Extends the base header with:
 * <ul>
 *   <li>Location string (dimension + coordinates, rendered below the name)</li>
 *   <li>Expand/collapse button ("[+]"/"[-]" at the right side)</li>
 *   <li>Priority field positioning (managed externally by PriorityFieldManager)</li>
 * </ul>
 *
 * The priority field is NOT rendered by this widget. The parent tab uses
 * {@link #getY()} to position the field via PriorityFieldManager. The header
 * only reserves visual space (by limiting name/location text width).
 *
 * @see AbstractHeader
 * @see StorageBusHeader
 */
public class StorageHeader extends AbstractHeader {

    /** Supplier for the location string (e.g., "(x, y, z, dim)") */
    protected Supplier<String> locationSupplier;

    /** Supplier for the expand/collapse state */
    protected Supplier<Boolean> expandedSupplier;

    /** Callback when the expand/collapse button is clicked */
    protected Runnable onExpandToggle;

    // Hover state
    protected boolean expandHovered = false;

    public StorageHeader(int y, FontRenderer fontRenderer, RenderItem itemRender) {
        super(y, fontRenderer, itemRender);
    }

    // ---- Configuration ----

    public void setLocationSupplier(Supplier<String> supplier) {
        this.locationSupplier = supplier;
    }

    public void setExpandedSupplier(Supplier<Boolean> supplier) {
        this.expandedSupplier = supplier;
    }

    public void setOnExpandToggle(Runnable callback) {
        this.onExpandToggle = callback;
    }

    /**
     * Whether the expand/collapse button is currently hovered.
     */
    public boolean isExpandHovered() {
        return expandHovered;
    }

    // ---- Rendering ----

    @Override
    protected int drawHeaderContent(int mouseX, int mouseY) {
        expandHovered = false;

        // Draw location text
        drawLocation();

        // Draw expand/collapse indicator
        drawExpandIcon(mouseX, mouseY);

        // Return the hover right bound (up to expand area, excluding priority field)
        return GuiConstants.EXPAND_ICON_X;
    }

    /**
     * Draw the location string below the name.
     * Location strings extend to the right edge (wider than the name which stops at IO mode area).
     */
    protected void drawLocation() {
        String location = locationSupplier != null ? locationSupplier.get() : "";
        if (location.isEmpty()) return;

        String displayLocation = trimTextToWidth(location, GuiConstants.HEADER_LOCATION_MAX_WIDTH);
        fontRenderer.drawString(displayLocation, GuiConstants.HEADER_NAME_X, y + 9,
            GuiConstants.COLOR_TEXT_SECONDARY);
    }

    /**
     * Draw the expand/collapse indicator ("[+]" or "[-]").
     */
    protected void drawExpandIcon(int mouseX, int mouseY) {
        boolean expanded = expandedSupplier != null && expandedSupplier.get();
        String expandIcon = expanded ? "[-]" : "[+]";
        fontRenderer.drawString(expandIcon, GuiConstants.EXPAND_ICON_X, y + 1,
            GuiConstants.COLOR_TEXT_PLACEHOLDER);

        // Check expand icon hover (wider area for easy clicking)
        expandHovered = mouseX >= GuiConstants.EXPAND_ICON_X - 2
            && mouseX < GuiConstants.CONTENT_RIGHT_EDGE
            && mouseY >= y && mouseY < y + GuiConstants.ROW_HEIGHT;
    }

    // ---- Click handling ----

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible) return false;

        // Expand/collapse click - left click only, check at click time not cached hover
        if (button == 0) {
            boolean isExpandArea = mouseX >= GuiConstants.EXPAND_ICON_X - 2
                && mouseX < GuiConstants.CONTENT_RIGHT_EDGE
                && mouseY >= y && mouseY < y + GuiConstants.ROW_HEIGHT;
            if (isExpandArea && onExpandToggle != null) {
                onExpandToggle.run();
                return true;
            }
        }

        // Name click (right-click), cards click, and header selection (from base)
        return super.handleClick(mouseX, mouseY, button);
    }
}
