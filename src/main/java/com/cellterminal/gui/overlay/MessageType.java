package com.cellterminal.gui.overlay;


/**
 * Types of messages that can be displayed in the overlay.
 * Each type has an associated color for visual distinction.
 */
public enum MessageType {
    SUCCESS(0x55FF55),  // Green
    ERROR(0xFF5555),    // Red
    WARNING(0xFFFF55);  // Yellow

    private final int color;

    MessageType(int color) {
        this.color = color;
    }

    /**
     * Gets the RGB color for this message type (without alpha).
     */
    public int getColor() {
        return color;
    }

    /**
     * Gets the color with full opacity for text rendering.
     */
    public int getTextColor() {
        return 0xFF000000 | color;
    }
}
