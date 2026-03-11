package com.cellterminal.gui.widget;

import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;


/**
 * Base interface for all widgets in the Cell Terminal GUI.
 * <p>
 * A widget is a self-contained visual component that handles its own:
 * - Rendering (draw)
 * - Click handling (handleClick)
 * - Keyboard handling (handleKey)
 * - Hover detection (isHovered)
 * - Tooltip provision (getTooltip)
 * <p>
 * Widgets do not need to know about sibling widgets. They only communicate
 * upward through return values (e.g., "I handled this click") or callbacks
 * provided at construction time.
 */
public interface IWidget {

    /**
     * Draw this widget.
     *
     * @param mouseX Mouse X relative to the GUI
     * @param mouseY Mouse Y relative to the GUI
     */
    void draw(int mouseX, int mouseY);

    /**
     * Handle a mouse click.
     *
     * @param mouseX Mouse X relative to the GUI
     * @param mouseY Mouse Y relative to the GUI
     * @param button Mouse button (0=left, 1=right, 2=middle)
     * @return true if the click was handled and should not propagate
     */
    boolean handleClick(int mouseX, int mouseY, int button);

    /**
     * Handle a key press.
     *
     * @param typedChar The typed character
     * @param keyCode The key code
     * @return true if the key was handled and should not propagate
     */
    default boolean handleKey(char typedChar, int keyCode) {
        return false;
    }

    /**
     * Check if the mouse is over this widget.
     *
     * @param mouseX Mouse X relative to the GUI
     * @param mouseY Mouse Y relative to the GUI
     * @return true if the mouse is over this widget
     */
    boolean isHovered(int mouseX, int mouseY);

    /**
     * Get tooltip lines to display when this widget is hovered.
     * Returns empty list if no tooltip should be shown.
     *
     * @param mouseX Mouse X relative to the GUI
     * @param mouseY Mouse Y relative to the GUI
     * @return List of tooltip lines (can be empty, never null)
     */
    default List<String> getTooltip(int mouseX, int mouseY) {
        return Collections.emptyList();
    }

    /**
     * Get the ItemStack under the mouse cursor, if any.
     * Used by the parent GUI to render item tooltips (which require full GUI context
     * with drawHoveringText). This is separate from {@link #getTooltip} which provides
     * custom text tooltips (button hints, etc.).
     *
     * @param mouseX Mouse X relative to the GUI
     * @param mouseY Mouse Y relative to the GUI
     * @return The hovered ItemStack, or ItemStack.EMPTY if none
     */
    default ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        return ItemStack.EMPTY;
    }

    /**
     * Get the X position of this widget relative to GUI.
     */
    int getX();

    /**
     * Get the Y position of this widget relative to GUI.
     */
    int getY();

    /**
     * Get the width of this widget.
     */
    int getWidth();

    /**
     * Get the height of this widget.
     */
    int getHeight();

    /**
     * Whether this widget is visible and should be drawn.
     */
    default boolean isVisible() {
        return true;
    }
}
