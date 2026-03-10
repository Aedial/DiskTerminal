package com.cellterminal.gui.widget.button;

import net.minecraft.client.resources.I18n;


/**
 * Defines the types of small buttons (8x8) used in the Cell Terminal GUI.
 *
 * Each type maps to a column in the {@code textures/guis/atlas.png} sprite sheet
 * (5x2 grid of 8x8 icons). The hover state is the row (0=normal, 1=hovered).
 *
 * @see SmallButton
 */
public enum ButtonType {

    /** Green button: partition cell contents from current inventory */
    DO_PARTITION(0, "gui.cellterminal.button.do_partition"),

    /** Red button: clear all partition entries */
    CLEAR_PARTITION(1, "gui.cellterminal.button.clear_partition"),

    /** IO mode: read-only */
    READ_ONLY(2, "gui.cellterminal.button.read_only"),

    /** IO mode: write-only */
    WRITE_ONLY(3, "gui.cellterminal.button.write_only"),

    /** IO mode: read-write (bidirectional) */
    READ_WRITE(4, "gui.cellterminal.button.read_write");

    /** X offset in the texture atlas (column index * 8) */
    private final int textureX;

    /** Localization key for the tooltip */
    private final String tooltipKey;

    ButtonType(int column, String tooltipKey) {
        this.textureX = column * 8;
        this.tooltipKey = tooltipKey;
    }

    /**
     * Get the X offset in the sprite sheet for this button type.
     */
    public int getTextureX() {
        return textureX;
    }

    /**
     * Get the localized tooltip text for this button type.
     */
    public String getTooltip() {
        return I18n.format(tooltipKey);
    }

    /**
     * Get the tooltip localization key.
     */
    public String getTooltipKey() {
        return tooltipKey;
    }
}
