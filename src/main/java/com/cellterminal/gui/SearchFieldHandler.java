package com.cellterminal.gui;

import appeng.client.gui.widgets.MEGuiTextField;


/**
 * Handles search field click interactions (right-click clear, double-click modal).
 * <p>
 * Encapsulates the click logic that was previously spread across GuiCellTerminalBase's
 * mouseClicked and initSearchField methods. The GUI creates this handler alongside the
 * search field and modal, then delegates search field clicks to it.
 */
public class SearchFieldHandler {

    private static final long DOUBLE_CLICK_THRESHOLD = GuiConstants.DOUBLE_CLICK_TIME_MS;

    private final MEGuiTextField searchField;
    private final GuiModalSearchBar modalSearchBar;
    private long lastClickTime = 0;

    public SearchFieldHandler(MEGuiTextField searchField, GuiModalSearchBar modalSearchBar) {
        this.searchField = searchField;
        this.modalSearchBar = modalSearchBar;
    }

    /**
     * Handle a mouse click on the search field area.
     *
     * @return true if the click was consumed and should not propagate
     */
    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (!searchField.isMouseIn(mouseX, mouseY)) {
            // Clicking outside search field, let the default handler manage focus
            searchField.mouseClicked(mouseX, mouseY, mouseButton);
            return false;
        }

        // Right-click: clear and focus
        if (mouseButton == 1) {
            searchField.setText("");
            searchField.setFocused(true);
            return true;
        }

        // Left-click: check double-click to open modal
        if (mouseButton == 0) {
            long currentTime = System.currentTimeMillis();

            if (currentTime - lastClickTime < DOUBLE_CLICK_THRESHOLD) {
                if (modalSearchBar != null) modalSearchBar.open(searchField.y);
                lastClickTime = 0;
                return true;
            }

            lastClickTime = currentTime;
        }

        // Pass through to default handling (focus, cursor positioning)
        searchField.mouseClicked(mouseX, mouseY, mouseButton);
        return false;
    }
}
