package com.cellterminal.gui.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;


/**
 * A container that manages a list of child widgets.
 *
 * Provides ordered iteration for drawing (first added = drawn first = background)
 * and reverse iteration for click handling (last added = drawn on top = gets first click).
 *
 * Events propagate to children in the appropriate order and stop at the first
 * handler that returns true (indicating the event was consumed).
 */
public class WidgetContainer extends AbstractWidget {

    protected final List<IWidget> children = new ArrayList<>();

    public WidgetContainer(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    /**
     * Add a child widget at the end of the list (drawn on top, gets clicks first).
     */
    public void addChild(IWidget child) {
        children.add(child);
    }

    /**
     * Remove a child widget.
     */
    public void removeChild(IWidget child) {
        children.remove(child);
    }

    /**
     * Remove all child widgets.
     */
    public void clearChildren() {
        children.clear();
    }

    /**
     * Get an unmodifiable view of the children.
     */
    public List<IWidget> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        // Draw children in order (first = background, last = foreground)
        for (IWidget child : children) {
            if (child.isVisible()) child.draw(mouseX, mouseY);
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible) return false;

        // Process clicks in reverse order (foreground widgets get priority)
        for (int i = children.size() - 1; i >= 0; i--) {
            IWidget child = children.get(i);

            if (child.isVisible() && child.isHovered(mouseX, mouseY)) {
                if (child.handleClick(mouseX, mouseY, button)) return true;
            }
        }

        return false;
    }

    @Override
    public boolean handleKey(char typedChar, int keyCode) {
        if (!visible) return false;

        // Propagate to children in reverse order (foreground widgets get priority)
        for (int i = children.size() - 1; i >= 0; i--) {
            IWidget child = children.get(i);

            if (child.isVisible() && child.handleKey(typedChar, keyCode)) return true;
        }

        return false;
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible) return Collections.emptyList();

        // Check children in reverse order (foreground first)
        for (int i = children.size() - 1; i >= 0; i--) {
            IWidget child = children.get(i);

            if (!child.isVisible() || !child.isHovered(mouseX, mouseY)) continue;

            List<String> tooltip = child.getTooltip(mouseX, mouseY);
            // Item tooltips (cell slots, content slots) are handled separately via
            // getHoveredItemStack(), so text tooltips don't conflict with item tooltips.
            if (!tooltip.isEmpty()) return tooltip;
        }

        return Collections.emptyList();
    }

    @Override
    public ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        if (!visible) return ItemStack.EMPTY;

        // Check children in reverse order (foreground first)
        for (int i = children.size() - 1; i >= 0; i--) {
            IWidget child = children.get(i);

            if (!child.isVisible() || !child.isHovered(mouseX, mouseY)) continue;

            ItemStack stack = child.getHoveredItemStack(mouseX, mouseY);
            if (!stack.isEmpty()) return stack;
        }

        return ItemStack.EMPTY;
    }
}
