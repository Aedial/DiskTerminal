package com.cellterminal.gui.tab;

import java.util.HashMap;
import java.util.Map;


/**
 * Registry for tab controllers.
 * Provides access to tab controllers by index.
 */
public class TabControllerRegistry {

    private static final Map<Integer, ITabController> controllers = new HashMap<>();

    static {
        registerController(new TerminalTabController());
        registerController(new InventoryTabController());
        registerController(new PartitionTabController());
        registerController(new StorageBusInventoryTabController());
        registerController(new StorageBusPartitionTabController());
    }

    private static void registerController(ITabController controller) {
        controllers.put(controller.getTabIndex(), controller);
    }

    /**
     * Get the controller for a specific tab index.
     * @param tabIndex The tab index
     * @return The controller, or null if not found
     */
    public static ITabController getController(int tabIndex) {
        return controllers.get(tabIndex);
    }

    /**
     * Get the total number of tabs.
     * @return The number of registered tabs
     */
    public static int getTabCount() {
        return controllers.size();
    }

    /**
     * Check if a tab requires server polling.
     * @param tabIndex The tab index
     * @return true if the tab requires polling
     */
    public static boolean requiresPolling(int tabIndex) {
        ITabController controller = controllers.get(tabIndex);

        return controller != null && controller.requiresServerPolling();
    }
}
