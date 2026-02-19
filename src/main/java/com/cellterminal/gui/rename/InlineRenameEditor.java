package com.cellterminal.gui.rename;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import org.lwjgl.input.Keyboard;

import com.cellterminal.gui.GuiConstants;


/**
 * Shared inline text editing component for renaming entries in the Cell Terminal GUI.
 * Provides a text field overlay that appears on right-click, with keyboard navigation,
 * Enter to confirm, and Escape to cancel.
 *
 * Used by all tabs to rename drives/ME chests, cells, storage buses, and subnets.
 */
public class InlineRenameEditor {

    // The renameable target currently being edited (null if not editing)
    private Renameable editingTarget = null;
    private String editingText = "";
    private int editingCursorPos = 0;
    private int editingY = 0;
    private int editingX = GuiConstants.GUI_INDENT + 20;  // X position of the text field (varies by target)
    private int editingRightEdge = GuiConstants.CONTENT_RIGHT_EDGE - 4;  // Right edge of the text field (varies by target)

    // TODO: max length should probably be determined by available space rather than a fixed count
    // Maximum length for the rename text
    private static final int MAX_NAME_LENGTH = 50;

    // Layout constants for the text field
    private static final int TEXT_FIELD_HEIGHT = 9;  // Reduced height to align with text

    /**
     * Check if currently editing a name.
     */
    public boolean isEditing() {
        return editingTarget != null;
    }

    /**
     * Get the target being edited.
     */
    public Renameable getEditingTarget() {
        return editingTarget;
    }

    /**
     * Start editing a target's name.
     * @param target The renameable target
     * @param y The Y position where the edit field should appear
     * @param x The X position where the text field starts (after the icon)
     * @param rightEdge The right edge of the text field (before any buttons)
     */
    public void startEditing(Renameable target, int y, int x, int rightEdge) {
        if (target == null || !target.isRenameable()) return;

        this.editingTarget = target;
        this.editingText = target.hasCustomName() ? target.getCustomName() : "";
        this.editingCursorPos = editingText.length();
        this.editingY = y;
        this.editingX = x;
        this.editingRightEdge = rightEdge;
    }

    /**
     * Stop editing and return the edited text.
     * @return The edited text, or null if not editing
     */
    public String stopEditing() {
        if (editingTarget == null) return null;

        String result = editingText.trim();
        editingTarget = null;
        editingText = "";
        editingCursorPos = 0;

        return result;
    }

    /**
     * Cancel editing without saving.
     */
    public void cancelEditing() {
        editingTarget = null;
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
     * @return true if the input was handled (return false for Enter so GUI handles confirmation)
     */
    public boolean handleKeyTyped(char typedChar, int keyCode) {
        if (editingTarget == null) return false;

        // Enter - return false to let GUI handle confirmation
        if (keyCode == Keyboard.KEY_RETURN) return false;

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
        if (typedChar >= 32 && typedChar < 127 && editingText.length() < MAX_NAME_LENGTH) {
            editingText = editingText.substring(0, editingCursorPos)
                + typedChar
                + editingText.substring(editingCursorPos);
            editingCursorPos++;
            return true;
        }

        return false;
    }

    /**
     * Draw the rename text field overlay if editing.
     * Call this after drawing the main content.
     * @param fontRenderer The font renderer
     */
    public void drawRenameField(FontRenderer fontRenderer) {
        if (editingTarget == null) return;

        int x = editingX;
        int y = editingY + 1;  // Vertically aligned with text (y + 1 is where text draws)
        int width = editingRightEdge - editingX;
        int height = TEXT_FIELD_HEIGHT;

        // Draw background (E0E0E0 to match terminal background, with dark border)
        Gui.drawRect(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF373737);
        Gui.drawRect(x, y, x + width, y + height, 0xFFE0E0E0);

        // Draw text with scrolling if too long
        String displayText = editingText;
        int textWidth = fontRenderer.getStringWidth(displayText);
        int visibleWidth = width - 4;
        int textX = x + 2;

        if (textWidth > visibleWidth) {
            int cursorX = fontRenderer.getStringWidth(displayText.substring(0, editingCursorPos));
            int scrollOffset = Math.max(0, cursorX - visibleWidth + 10);
            textX -= scrollOffset;
        }

        fontRenderer.drawString(displayText, textX, y, 0xFF000000);

        // Draw blinking cursor
        long time = System.currentTimeMillis();
        if ((time / 500) % 2 == 0) {
            int cursorX = x + 2 + fontRenderer.getStringWidth(displayText.substring(0, editingCursorPos));
            Gui.drawRect(cursorX, y, cursorX + 1, y + height - 1, 0xFF000000);
        }
    }

    /**
     * Get the Y position of the rename field.
     */
    public int getEditingY() {
        return editingY;
    }
}
