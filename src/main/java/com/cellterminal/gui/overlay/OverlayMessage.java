package com.cellterminal.gui.overlay;


/**
 * Represents a message to be displayed in the overlay.
 * Contains the translated message text, its type, and timing information for fading.
 */
public class OverlayMessage {

    private final String text;
    private final MessageType type;
    private final long createdAt;

    /**
     * Duration in milliseconds before the message starts fading.
     */
    public static final long DISPLAY_DURATION_MS = 3000;

    /**
     * Duration in milliseconds for the fade-out effect.
     */
    public static final long FADE_DURATION_MS = 1000;

    /**
     * Total duration before the message is fully hidden.
     */
    public static final long TOTAL_DURATION_MS = DISPLAY_DURATION_MS + FADE_DURATION_MS;

    public OverlayMessage(String text, MessageType type) {
        this.text = text;
        this.type = type;
        this.createdAt = System.currentTimeMillis();
    }

    public String getText() {
        return text;
    }

    public MessageType getType() {
        return type;
    }

    /**
     * Returns the alpha value (0.0 to 1.0) based on the current fade state.
     * Returns 1.0 during display period, then fades to 0.0.
     */
    public float getAlpha() {
        long elapsed = System.currentTimeMillis() - createdAt;
        if (elapsed < DISPLAY_DURATION_MS) return 1.0f;

        long fadeElapsed = elapsed - DISPLAY_DURATION_MS;
        if (fadeElapsed >= FADE_DURATION_MS) return 0.0f;

        return 1.0f - (fadeElapsed / (float) FADE_DURATION_MS);
    }

    /**
     * Returns true if the message has fully faded and can be removed.
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - createdAt >= TOTAL_DURATION_MS;
    }
}
