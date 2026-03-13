package com.cellterminal.gui.rename;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import org.lwjgl.input.Keyboard;

import com.cellterminal.gui.GuiConstants;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketRenameAction;
import com.cellterminal.network.PacketSubnetAction;


/**
 * Singleton manager for inline rename editing in the Cell Terminal GUI.
 * <p>
 * Handles the full lifecycle of rename sessions: start, draw, key events, click-outside,
 * and sending rename packets directly via {@link CellTerminalNetwork}.
 * Individual widgets (headers, lines) trigger rename by calling {@link #startEditing} directly
 * when they receive a right-click on a renameable name area. The manager globally tracks
 * the single active rename session and draws the text field overlay.
 *
 * <h3>Usage flow</h3>
 * <ol>
 *   <li>A header/line widget detects right-click on a name → calls {@link #startEditing}</li>
 *   <li>GUI calls {@link #handleKey} in keyTyped (consumes all input while editing)</li>
 *   <li>GUI calls {@link #handleClickOutside} at the top of mouseClicked (saves on click-outside)</li>
 *   <li>GUI calls {@link #drawRenameField} after widget drawing (overlay on top)</li>
 * </ol>
 */
public class InlineRenameManager {

    private static final InlineRenameManager INSTANCE = new InlineRenameManager();

    public static InlineRenameManager getInstance() {
        return INSTANCE;
    }

    // The renameable target currently being edited (null if not editing)
    private Renameable editingTarget = null;
    private String editingText = "";
    private int editingCursorPos = 0;
    private int editingY = 0;
    private int editingX = GuiConstants.GUI_INDENT + 20;
    private int editingRightEdge = GuiConstants.CONTENT_RIGHT_EDGE - 4;

    // TODO: max length should probably be determined by available space rather than a fixed count
    // Maximum length for the rename text
    private static final int MAX_NAME_LENGTH = 50;

    // Layout constants for the text field
    private static final int TEXT_FIELD_HEIGHT = 9;

    private InlineRenameManager() {}

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
     * If already editing the same target, does nothing (avoids flicker on re-click).
     * If editing a different target, confirms the current rename first.
     *
     * @param target The renameable target
     * @param y The Y position where the edit field should appear
     * @param x The X position where the text field starts
     * @param rightEdge The right edge of the text field
     */
    public void startEditing(Renameable target, int y, int x, int rightEdge) {
        if (target == null || !target.isRenameable()) return;

        // Already editing this exact target, do nothing
        if (isEditing() && isSameTarget(editingTarget, target)) return;

        // Editing a different target, confirm the previous one first
        if (isEditing()) confirmEditing();

        this.editingTarget = target;
        this.editingText = target.hasCustomName() ? target.getCustomName() : "";
        this.editingCursorPos = editingText.length();
        this.editingY = y;
        this.editingX = x;
        this.editingRightEdge = rightEdge;
    }

    /**
     * Confirm the current editing session: send the rename packet and update the local name.
     */
    private void confirmEditing() {
        if (editingTarget == null) return;

        Renameable target = editingTarget;
        String newName = editingText.trim();
        clearState();

        switch (target.getRenameTargetType()) {
            case STORAGE:
                CellTerminalNetwork.INSTANCE.sendToServer(
                    PacketRenameAction.renameStorage(target.getRenameId(), newName));
                break;
            case CELL:
                CellTerminalNetwork.INSTANCE.sendToServer(
                    PacketRenameAction.renameCell(target.getRenameId(), target.getRenameSecondaryId(), newName));
                break;
            case STORAGE_BUS:
                CellTerminalNetwork.INSTANCE.sendToServer(
                    PacketRenameAction.renameStorageBus(target.getRenameId(), newName));
                break;
            case SUBNET:
                CellTerminalNetwork.INSTANCE.sendToServer(
                    PacketSubnetAction.rename(target.getRenameId(), newName));
                break;
            default:
                break;
        }

        // Update local name immediately for responsiveness
        target.setCustomName(newName.isEmpty() ? null : newName);
    }

    /**
     * Cancel editing without saving.
     */
    public void cancelEditing() {
        clearState();
    }

    private void clearState() {
        editingTarget = null;
        editingText = "";
        editingCursorPos = 0;
    }

    /**
     * Handle keyboard input for rename editing.
     * Handles all keys internally including Esc (cancel) and Enter (confirm).
     *
     * @param typedChar The character typed
     * @param keyCode The key code
     * @return true if the input was consumed (editing is active)
     */
    public boolean handleKey(char typedChar, int keyCode) {
        if (editingTarget == null) return false;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            cancelEditing();
            return true;
        }

        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            confirmEditing();
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

        // Consume all input while editing (don't let keys leak to other handlers)
        return true;
    }

    /**
     * Handle a click-outside event. Call at the top of mouseClicked, before any
     * other click processing. If editing is active and the click is outside the
     * rename field, confirms the current session.
     * <p>
     * Does NOT consume the click, letting it propagate so it may start a new rename
     * or interact with other GUI elements.
     */
    public void handleClickOutside(int mouseX, int mouseY) {
        if (!isEditing()) return;

        // Click inside the rename field: keep editing
        int fieldWidth = editingRightEdge - editingX;
        int fieldY = editingY + 1;
        if (mouseX >= editingX && mouseX < editingX + fieldWidth
            && mouseY >= fieldY && mouseY < fieldY + TEXT_FIELD_HEIGHT) {
            return;
        }

        // Click outside: confirm and save
        confirmEditing();
    }

    /**
     * Draw the rename text field overlay if editing.
     * Call after drawing all widget content so the field appears on top.
     */
    public void drawRenameField(FontRenderer fontRenderer) {
        if (editingTarget == null) return;

        int x = editingX;
        int y = editingY + 1;  // Vertically aligned with text (y + 1 is where text draws)
        int width = editingRightEdge - editingX;
        int height = TEXT_FIELD_HEIGHT;

        // Draw background with dark border
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
     * Check if two renameable targets refer to the same entity.
     */
    private static boolean isSameTarget(Renameable a, Renameable b) {
        if (a == b) return true;
        if (a == null || b == null) return false;

        return a.getRenameTargetType() == b.getRenameTargetType()
            && a.getRenameId() == b.getRenameId()
            && a.getRenameSecondaryId() == b.getRenameSecondaryId();
    }
}
