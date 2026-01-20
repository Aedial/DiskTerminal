package com.cellterminal.gui.tab;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.gui.render.RenderContext;


/**
 * Interface for tab controllers that manage individual tabs in the Cell Terminal GUI.
 * Each tab can define its own behavior for:
 * - Help text content
 * - Search filter mode
 * - Tooltip handling
 * - Click handling
 * - Keyboard handling
 */
public interface ITabController {

    /**
     * Get the tab index this controller manages.
     * @return The tab index (0-4)
     */
    int getTabIndex();

    /**
     * Get the search filter mode that should be used when this tab is active.
     * @param userSelectedMode The mode the user has selected (for tabs that respect user choice)
     * @return The effective search filter mode for this tab
     */
    SearchFilterMode getEffectiveSearchMode(SearchFilterMode userSelectedMode);

    /**
     * Check if the search mode button should be visible when this tab is active.
     * @return true if the search mode button should be shown
     */
    boolean showSearchModeButton();

    /**
     * Get the help lines to display for this tab.
     * These are displayed in the controls help widget.
     * @return List of localized help text lines (can include empty strings for spacing)
     */
    List<String> getHelpLines();

    /**
     * Handle a click on this tab's content area.
     * @param context The click context containing all relevant state
     * @return true if the click was handled and should not propagate
     */
    boolean handleClick(TabClickContext context);

    /**
     * Handle a key press when this tab is active.
     * @param keyCode The key code that was pressed
     * @param context The tab context containing relevant state
     * @return true if the key was handled and should not propagate
     */
    boolean handleKeyTyped(int keyCode, TabContext context);

    /**
     * Called when this tab becomes active.
     * @param context The tab context
     */
    default void onTabActivated(TabContext context) {}

    /**
     * Called when this tab becomes inactive (another tab is selected).
     * @param context The tab context
     */
    default void onTabDeactivated(TabContext context) {}

    /**
     * Get whether this tab requires server-side polling for data updates.
     * @return true if the server should poll for data while this tab is active
     */
    default boolean requiresServerPolling() {
        return false;
    }

    /**
     * Get the lines to display for this tab from the data manager.
     * @param context The tab context
     * @return The list of line objects for rendering
     */
    List<Object> getLines(TabContext context);

    /**
     * Get the total line count for scrollbar calculation.
     * @param context The tab context
     * @return The number of lines
     */
    int getLineCount(TabContext context);
}
