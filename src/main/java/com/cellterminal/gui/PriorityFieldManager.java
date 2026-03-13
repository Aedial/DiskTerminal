package com.cellterminal.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;

import com.cellterminal.client.Prioritizable;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketSetPriority;


/**
 * Singleton manager for inline priority text fields in the Cell Terminal GUI.
 * <p>
 * Each visible storage header (drive/chest or storage bus) that supports priority
 * editing gets an {@link InlinePriorityField} registered here. Fields persist across
 * frame rebuilds (keyed by {@link Prioritizable#getId()}) so that focus state,
 * cursor position, and in-progress edits survive widget recreation.
 * <p>
 * Headers call {@link #registerField} during their draw pass to create/position
 * their own field. The GUI then calls {@link #drawFieldsRelative} to render all
 * visible fields, and delegates click/key events via {@link #handleClick} and
 * {@link #handleKeyTyped}.
 */
public class PriorityFieldManager {

    private static final PriorityFieldManager INSTANCE = new PriorityFieldManager();

    public static PriorityFieldManager getInstance() {
        return INSTANCE;
    }

    // Field dimensions - sized for 7 digits with caret (-999999 to 9999999)
    public static final int FIELD_WIDTH = 35;
    private static final int FIELD_HEIGHT = 6;

    // Position offset from right edge of content area (leave room for [+]/[-] button)
    public static final int RIGHT_MARGIN = 15;

    // Field registry (persists across frame rebuilds)
    private final Map<Long, InlinePriorityField> fields = new HashMap<>();
    private InlinePriorityField focusedField = null;

    private PriorityFieldManager() {}

    /**
     * Register (or update) a priority field for the given target.
     * Called by header widgets during their draw pass to create the field if it
     * doesn't exist yet, update the data reference, and position it for this frame.
     *
     * @param target The prioritizable data object (storage or storage bus)
     * @param y The GUI-relative Y position of the header row
     * @param guiLeft The GUI's absolute left position
     * @param guiTop The GUI's absolute top position
     * @param fontRenderer Font renderer for creating new fields
     */
    public void registerField(Prioritizable target, int y, int guiLeft, int guiTop, FontRenderer fontRenderer) {
        InlinePriorityField field = fields.get(target.getId());

        if (field == null) {
            field = new InlinePriorityField(target, fontRenderer);
            fields.put(target.getId(), field);
        } else {
            field.updateTarget(target);
        }

        int fieldX = guiLeft + GuiConstants.CONTENT_RIGHT_EDGE - FIELD_WIDTH - RIGHT_MARGIN;
        int fieldY = guiTop + y + 1;
        field.updatePosition(fieldX, fieldY);
    }

    /**
     * Draw fields when in GUI-relative context (after glTranslate by guiLeft/guiTop).
     * Undoes the translation to draw at the absolute positions stored in each field.
     */
    public void drawFieldsRelative(int guiLeft, int guiTop) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(-guiLeft, -guiTop, 0);

        for (InlinePriorityField field : fields.values()) {
            if (field.isVisible()) field.draw();
        }

        GlStateManager.popMatrix();
    }

    /**
     * Mark all fields as not visible at the start of a render cycle.
     * Fields are marked visible again when their header calls {@link #registerField}.
     */
    public void resetVisibility() {
        for (InlinePriorityField field : fields.values()) field.setVisible(false);
    }

    /**
     * Remove fields whose IDs are no longer present in the active data set.
     *
     * @param activeIds The set of all currently valid storage/bus IDs
     */
    public void cleanupStaleFields(Set<Long> activeIds) {
        fields.keySet().removeIf(id -> !activeIds.contains(id));
    }

    /**
     * Handle a mouse click.
     * @return true if a field was clicked and handled the event
     */
    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        for (InlinePriorityField field : fields.values()) {
            if (!field.isVisible()) continue;

            if (field.isMouseOver(mouseX, mouseY)) {
                if (focusedField != null && focusedField != field) focusedField.onFocusLost();

                focusedField = field;
                field.mouseClicked(mouseX, mouseY, mouseButton);

                return true;
            }
        }

        // Clicking outside any field = unfocus current
        if (focusedField != null) {
            focusedField.onFocusLost();
            focusedField = null;
        }

        return false;
    }

    /**
     * Handle a key typed event.
     * @return true if the event was consumed
     */
    public boolean handleKeyTyped(char typedChar, int keyCode) {
        if (focusedField == null) return false;

        boolean consumed = focusedField.keyTyped(typedChar, keyCode);

        // If the field was unfocused (e.g., by Escape or Enter), clear the reference
        if (!focusedField.isFocused()) focusedField = null;

        return consumed;
    }

    /**
     * Unfocus all fields (submits pending edits).
     */
    public void unfocusAll() {
        if (focusedField != null) {
            focusedField.onFocusLost();
            focusedField = null;
        }
    }

    /**
     * Check if mouse is over any visible priority field.
     * @return true if mouse is over a priority field
     */
    public boolean isMouseOverField(int mouseX, int mouseY) {
        for (InlinePriorityField field : fields.values()) {
            if (!field.isVisible()) continue;
            if (field.isMouseOver(mouseX, mouseY)) return true;
        }

        return false;
    }

    /**
     * A single inline priority text field for any {@link Prioritizable} target.
     * Handles rendering, keyboard input (digit filtering), and submitting
     * priority changes via {@link PacketSetPriority}.
     */
    public static class InlinePriorityField {

        private static final float TEXT_SCALE = 0.65f;

        private Prioritizable target;
        private final GuiTextField textField;
        private final FontRenderer fontRenderer;
        private boolean visible = false;
        private int lastKnownPriority;

        public InlinePriorityField(Prioritizable target, FontRenderer fontRenderer) {
            this.target = target;
            this.fontRenderer = fontRenderer;
            this.lastKnownPriority = target.getPriority();
            this.textField = new GuiTextField(0, fontRenderer, 0, 0, FIELD_WIDTH, FIELD_HEIGHT);
            this.textField.setMaxStringLength(8);
            this.textField.setEnableBackgroundDrawing(false);
            this.textField.setText(String.valueOf(target.getPriority()));
        }

        /**
         * Update the data reference. Called when data is refreshed from server.
         */
        public void updateTarget(Prioritizable newTarget) {
            this.target = newTarget;
        }

        public void updatePosition(int x, int y) {
            this.textField.x = x;
            this.textField.y = y;
            this.visible = true;

            // Sync text if priority changed externally (and field is not being edited)
            if (target.getPriority() != lastKnownPriority && !textField.isFocused()) {
                lastKnownPriority = target.getPriority();
                textField.setText(String.valueOf(lastKnownPriority));
            }
        }

        public void draw() {
            int x = textField.x;
            int y = textField.y;

            // Draw background
            Gui.drawRect(x - 1, y - 1, x + FIELD_WIDTH + 1, y + FIELD_HEIGHT + 1, 0xFF373737);
            Gui.drawRect(x, y, x + FIELD_WIDTH, y + FIELD_HEIGHT, textField.isFocused() ? 0xFF000000 : 0xFF1E1E1E);

            // Draw text with scaling
            String text = textField.getText();
            if (!text.isEmpty()) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(x + 2, y + 1, 0);
                GlStateManager.scale(TEXT_SCALE, TEXT_SCALE, 1.0f);
                fontRenderer.drawString(text, 0, 0, 0xE0E0E0);
                GlStateManager.popMatrix();
            }

            // Draw cursor if focused
            if (textField.isFocused()) {
                int cursorPos = textField.getCursorPosition();
                String beforeCursor = text.substring(0, Math.min(cursorPos, text.length()));
                int cursorX = (int) (fontRenderer.getStringWidth(beforeCursor) * TEXT_SCALE);
                Gui.drawRect(x + 2 + cursorX, y + 1, x + 3 + cursorX, y + FIELD_HEIGHT - 1, 0xFFD0D0D0);
            }
        }

        public boolean isVisible() {
            return visible;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
        }

        public boolean isMouseOver(int mouseX, int mouseY) {
            return mouseX >= textField.x && mouseX < textField.x + FIELD_WIDTH
                && mouseY >= textField.y && mouseY < textField.y + FIELD_HEIGHT;
        }

        public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
            textField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        public boolean keyTyped(char typedChar, int keyCode) {
            // Enter submits
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                submitPriority();
                textField.setFocused(false);

                return true;
            }

            // Escape cancels
            if (keyCode == Keyboard.KEY_ESCAPE) {
                textField.setText(String.valueOf(target.getPriority()));
                textField.setFocused(false);

                return true;
            }

            // Filter to only allow numeric input and minus sign
            if (Character.isDigit(typedChar) || typedChar == '-'
                    || keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE
                    || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT
                    || keyCode == Keyboard.KEY_HOME || keyCode == Keyboard.KEY_END) {
                return textField.textboxKeyTyped(typedChar, keyCode);
            }

            return false;
        }

        public boolean isFocused() {
            return textField.isFocused();
        }

        public void onFocusLost() {
            if (textField.isFocused()) {
                submitPriority();
                textField.setFocused(false);
            }
        }

        private void submitPriority() {
            try {
                int newPriority = Integer.parseInt(textField.getText().trim());

                if (newPriority != target.getPriority()) {
                    CellTerminalNetwork.INSTANCE.sendToServer(new PacketSetPriority(target.getId(), newPriority));
                    lastKnownPriority = newPriority;
                }
            } catch (NumberFormatException e) {
                // Invalid input, revert to current value
                textField.setText(String.valueOf(target.getPriority()));
            }
        }
    }
}
