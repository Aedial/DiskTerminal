package com.cellterminal.gui.widget.button;


/**
 * A small button that cycles through multiple {@link ButtonType}s on each click.
 *
 * The type is advanced to the next value in the provided array before the
 * onClick callback is invoked. This allows the callback to read the new type
 * and act accordingly (e.g., switch IO mode).
 *
 * @see SmallButton
 * @see ButtonType
 */
public class SmallSwitchingButton extends SmallButton {

    private final ButtonType[] types;
    private int currentIndex;

    /**
     * @param x X position relative to GUI
     * @param y Y position relative to GUI
     * @param types The button types to cycle through (at least 2)
     * @param initialIndex The starting index in the types array
     * @param onClick Callback invoked after the type switches (new type is already set)
     */
    public SmallSwitchingButton(int x, int y, ButtonType[] types, int initialIndex, Runnable onClick) {
        super(x, y, types[initialIndex], onClick);
        this.types = types;
        this.currentIndex = initialIndex;
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible || button != 0) return false;
        if (!isHovered(mouseX, mouseY)) return false;

        // Advance to next type
        currentIndex = (currentIndex + 1) % types.length;
        this.type = types[currentIndex];

        // Delegate to parent which invokes the callback
        return super.handleClick(mouseX, mouseY, button);
    }

    /**
     * Get the current type index (0-based position in the types array).
     */
    public int getCurrentIndex() {
        return currentIndex;
    }

    /**
     * Set the current type by index.
     */
    public void setCurrentIndex(int index) {
        this.currentIndex = index % types.length;
        this.type = types[currentIndex];
    }
}
