package com.cellterminal.gui;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;

import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.client.StorageInfo;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketSetPriority;


/**
 * Manages priority text fields for visible storage entries (drives/chests) and storage buses.
 * Each storage/bus gets its own text field positioned at the far right of its header line.
 */
public class PriorityFieldManager {

    // Field dimensions - sized for 7 digits with caret (-999999 to 9999999)
    private static final int FIELD_WIDTH = 35;
    private static final int FIELD_HEIGHT = 6;

    // Position offset from right edge of content area (leave room for [+]/[-] button)
    private static final int RIGHT_MARGIN = 15;

    private final FontRenderer fontRenderer;
    private final Map<Long, PriorityField> fields = new HashMap<>();
    private final Map<Long, StorageBusPriorityField> storageBusFields = new HashMap<>();
    private PriorityField focusedField = null;
    private StorageBusPriorityField focusedStorageBusField = null;

    public PriorityFieldManager(FontRenderer fontRenderer) {
        this.fontRenderer = fontRenderer;
    }

    /**
     * Get or create a priority field for the given storage.
     * Also updates the storage reference if the field already exists.
     */
    public PriorityField getOrCreateField(StorageInfo storage, int guiLeft, int guiTop) {
        PriorityField field = fields.get(storage.getId());

        if (field == null) {
            field = new PriorityField(storage, fontRenderer);
            fields.put(storage.getId(), field);
        } else {
            // Update storage reference in case it was refreshed from server
            field.updateStorage(storage);
        }

        return field;
    }

    /**
     * Get or create a priority field for the given storage bus.
     * Also updates the storage bus reference if the field already exists.
     */
    public StorageBusPriorityField getOrCreateStorageBusField(StorageBusInfo storageBus, int guiLeft, int guiTop) {
        StorageBusPriorityField field = storageBusFields.get(storageBus.getId());

        if (field == null) {
            field = new StorageBusPriorityField(storageBus, fontRenderer);
            storageBusFields.put(storageBus.getId(), field);
        } else {
            // Update storage bus reference in case it was refreshed from server
            field.updateStorageBus(storageBus);
        }

        return field;
    }

    /**
     * Update the position of a field for the current render frame.
     * @param field The field to update
     * @param y The GUI-relative Y position of the storage line
     * @param guiLeft The GUI's absolute left position
     * @param guiTop The GUI's absolute top position
     */
    public void updateFieldPosition(PriorityField field, int y, int guiLeft, int guiTop) {
        // Position at far right of the storage line
        // Store absolute position for click handling
        int fieldX = guiLeft + 180 - FIELD_WIDTH - RIGHT_MARGIN;
        int fieldY = guiTop + y + 1;
        field.updatePosition(fieldX, fieldY);
    }

    /**
     * Update the position of a storage bus priority field for the current render frame.
     */
    public void updateStorageBusFieldPosition(StorageBusPriorityField field, int y, int guiLeft, int guiTop) {
        int fieldX = guiLeft + 180 - FIELD_WIDTH - RIGHT_MARGIN;
        int fieldY = guiTop + y + 1;
        field.updatePosition(fieldX, fieldY);
    }

    /**
     * Draw all visible priority fields.
     * Must be called from absolute coordinate context (like drawScreen).
     */
    public void drawFields(int mouseX, int mouseY) {
        for (PriorityField field : fields.values()) {
            if (field.isVisible()) field.draw();
        }

        for (StorageBusPriorityField field : storageBusFields.values()) {
            if (field.isVisible()) field.draw();
        }
    }

    /**
     * Draw fields when in GUI-relative context (after glTranslate by guiLeft/guiTop).
     * We need to undo the translation to draw at absolute positions.
     */
    public void drawFieldsRelative(int guiLeft, int guiTop) {
        // Pop out of GUI-relative coordinates to draw at absolute positions
        GlStateManager.pushMatrix();
        GlStateManager.translate(-guiLeft, -guiTop, 0);

        for (PriorityField field : fields.values()) {
            if (field.isVisible()) field.draw();
        }

        for (StorageBusPriorityField field : storageBusFields.values()) {
            if (field.isVisible()) field.draw();
        }

        GlStateManager.popMatrix();
    }

    /**
     * Mark all fields as not visible at the start of a render cycle.
     */
    public void resetVisibility() {
        for (PriorityField field : fields.values()) field.setVisible(false);
        for (StorageBusPriorityField field : storageBusFields.values()) field.setVisible(false);
    }

    /**
     * Clean up fields for storages that no longer exist.
     */
    public void cleanupStaleFields(Map<Long, StorageInfo> currentStorages) {
        fields.keySet().removeIf(id -> !currentStorages.containsKey(id));
    }

    /**
     * Clean up fields for storage buses that no longer exist.
     */
    public void cleanupStaleStorageBusFields(Map<Long, StorageBusInfo> currentStorageBuses) {
        storageBusFields.keySet().removeIf(id -> !currentStorageBuses.containsKey(id));
    }

    /**
     * Handle a mouse click.
     * @return true if a field was clicked and handled the event
     */
    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        // Check storage bus fields
        for (StorageBusPriorityField field : storageBusFields.values()) {
            if (!field.isVisible()) continue;

            if (field.isMouseOver(mouseX, mouseY)) {
                // Unfocus any other focused field
                if (focusedField != null) {
                    focusedField.onFocusLost();
                    focusedField = null;
                }

                if (focusedStorageBusField != null && focusedStorageBusField != field) {
                    focusedStorageBusField.onFocusLost();
                }

                focusedStorageBusField = field;
                field.mouseClicked(mouseX, mouseY, mouseButton);

                return true;
            }
        }

        // Check storage fields
        for (PriorityField field : fields.values()) {
            if (!field.isVisible()) continue;

            if (field.isMouseOver(mouseX, mouseY)) {
                // Unfocus any other focused field
                if (focusedStorageBusField != null) {
                    focusedStorageBusField.onFocusLost();
                    focusedStorageBusField = null;
                }

                if (focusedField != null && focusedField != field) focusedField.onFocusLost();

                focusedField = field;
                field.mouseClicked(mouseX, mouseY, mouseButton);

                return true;
            }
        }

        // Clicking outside any field - unfocus current
        if (focusedField != null) {
            focusedField.onFocusLost();
            focusedField = null;
        }

        if (focusedStorageBusField != null) {
            focusedStorageBusField.onFocusLost();
            focusedStorageBusField = null;
        }

        return false;
    }

    /**
     * Handle a key typed event.
     * @return true if the event was consumed
     */
    public boolean handleKeyTyped(char typedChar, int keyCode) {
        if (focusedField != null) {
            boolean consumed = focusedField.keyTyped(typedChar, keyCode);

            // If the field was unfocused (e.g., by Escape), clear the reference
            if (!focusedField.isFocused()) focusedField = null;

            return consumed;
        }

        if (focusedStorageBusField != null) {
            boolean consumed = focusedStorageBusField.keyTyped(typedChar, keyCode);

            // If the field was unfocused (e.g., by Escape), clear the reference
            if (!focusedStorageBusField.isFocused()) focusedStorageBusField = null;

            return consumed;
        }

        return false;
    }

    /**
     * Check if any field is focused.
     */
    public boolean hasFocusedField() {
        return (focusedField != null && focusedField.isFocused())
            || (focusedStorageBusField != null && focusedStorageBusField.isFocused());
    }

    /**
     * Unfocus all fields.
     */
    public void unfocusAll() {
        if (focusedField != null) {
            focusedField.onFocusLost();
            focusedField = null;
        }

        if (focusedStorageBusField != null) {
            focusedStorageBusField.onFocusLost();
            focusedStorageBusField = null;
        }
    }

    /**
     * Check if mouse is over any visible priority field (storage or storage bus).
     * @return true if mouse is over a priority field
     */
    public boolean isMouseOverField(int mouseX, int mouseY) {
        for (PriorityField field : fields.values()) {
            if (!field.isVisible()) continue;
            if (field.isMouseOver(mouseX, mouseY)) return true;
        }

        for (StorageBusPriorityField field : storageBusFields.values()) {
            if (!field.isVisible()) continue;
            if (field.isMouseOver(mouseX, mouseY)) return true;
        }

        return false;
    }

    /**
     * A single priority field for a storage.
     */
    public static class PriorityField {

        private static final float TEXT_SCALE = 0.65f;

        private StorageInfo storage;
        private final GuiTextField textField;
        private final FontRenderer fontRenderer;
        private boolean visible = false;
        private int lastKnownPriority;

        public PriorityField(StorageInfo storage, FontRenderer fontRenderer) {
            this.storage = storage;
            this.fontRenderer = fontRenderer;
            this.lastKnownPriority = storage.getPriority();
            this.textField = new GuiTextField(0, fontRenderer, 0, 0, FIELD_WIDTH, FIELD_HEIGHT);
            this.textField.setMaxStringLength(8);
            this.textField.setEnableBackgroundDrawing(false); // We'll draw our own background
            this.textField.setText(String.valueOf(storage.getPriority()));
        }

        /**
         * Update the storage reference. Called when storage data is refreshed from server.
         */
        public void updateStorage(StorageInfo newStorage) {
            this.storage = newStorage;
        }

        public void updatePosition(int x, int y) {
            this.textField.x = x;
            this.textField.y = y;
            this.visible = true;

            // Update text if storage priority changed externally
            if (storage.getPriority() != lastKnownPriority && !textField.isFocused()) {
                lastKnownPriority = storage.getPriority();
                textField.setText(String.valueOf(lastKnownPriority));
            }
        }

        public void draw() {
            int x = textField.x;
            int y = textField.y;

            // Draw background
            net.minecraft.client.gui.Gui.drawRect(x - 1, y - 1, x + FIELD_WIDTH + 1, y + FIELD_HEIGHT + 1, 0xFF373737);
            net.minecraft.client.gui.Gui.drawRect(x, y, x + FIELD_WIDTH, y + FIELD_HEIGHT, textField.isFocused() ? 0xFF000000 : 0xFF1E1E1E);

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
                net.minecraft.client.gui.Gui.drawRect(x + 2 + cursorX, y + 1, x + 3 + cursorX, y + FIELD_HEIGHT - 1, 0xFFD0D0D0);
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
                textField.setText(String.valueOf(storage.getPriority()));
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

                if (newPriority != storage.getPriority()) {
                    CellTerminalNetwork.INSTANCE.sendToServer(new PacketSetPriority(storage.getId(), newPriority));
                    lastKnownPriority = newPriority;
                }
            } catch (NumberFormatException e) {
                // Invalid input - revert to current value
                textField.setText(String.valueOf(storage.getPriority()));
            }
        }

        public StorageInfo getStorage() {
            return storage;
        }
    }

    /**
     * A single priority field for a storage bus.
     */
    public static class StorageBusPriorityField {

        private static final float TEXT_SCALE = 0.65f;

        private StorageBusInfo storageBus;
        private final GuiTextField textField;
        private final FontRenderer fontRenderer;
        private boolean visible = false;
        private int lastKnownPriority;

        public StorageBusPriorityField(StorageBusInfo storageBus, FontRenderer fontRenderer) {
            this.storageBus = storageBus;
            this.fontRenderer = fontRenderer;
            this.lastKnownPriority = storageBus.getPriority();
            this.textField = new GuiTextField(0, fontRenderer, 0, 0, FIELD_WIDTH, FIELD_HEIGHT);
            this.textField.setMaxStringLength(8);
            this.textField.setText(String.valueOf(storageBus.getPriority()));
        }

        /**
         * Update the storage bus reference. Called when storage bus data is refreshed from server.
         */
        public void updateStorageBus(StorageBusInfo newStorageBus) {
            this.storageBus = newStorageBus;
        }

        public void updatePosition(int x, int y) {
            this.textField.x = x;
            this.textField.y = y;
            this.visible = true;

            // Update text if storage bus priority changed externally
            if (storageBus.getPriority() != lastKnownPriority && !textField.isFocused()) {
                lastKnownPriority = storageBus.getPriority();
                textField.setText(String.valueOf(lastKnownPriority));
            }
        }

        public void draw() {
            int x = textField.x;
            int y = textField.y;

            // Draw background (same style as PriorityField)
            net.minecraft.client.gui.Gui.drawRect(x - 1, y - 1, x + FIELD_WIDTH + 1, y + FIELD_HEIGHT + 1, 0xFF373737);
            net.minecraft.client.gui.Gui.drawRect(x, y, x + FIELD_WIDTH, y + FIELD_HEIGHT, textField.isFocused() ? 0xFF000000 : 0xFF1E1E1E);

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
                textField.setText(String.valueOf(storageBus.getPriority()));
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

                if (newPriority != storageBus.getPriority()) {
                    CellTerminalNetwork.INSTANCE.sendToServer(new PacketSetPriority(storageBus.getId(), newPriority));
                    lastKnownPriority = newPriority;
                }
            } catch (NumberFormatException e) {
                // Invalid input - revert to current value
                textField.setText(String.valueOf(storageBus.getPriority()));
            }
        }

        public StorageBusInfo getStorageBus() {
            return storageBus;
        }
    }
}
