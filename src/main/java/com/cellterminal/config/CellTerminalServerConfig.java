package com.cellterminal.config;

import java.io.File;
import java.util.Set;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;


/**
 * Server-side configuration for Cell Terminal features.
 * Controls tab availability, storage bus polling, cell operations, and other server-enforced settings.
 *
 * These settings affect all players connecting to the server.
 * On single-player, they are still enforced as if it were a server.
 */
public class CellTerminalServerConfig {

    private static final String CONFIG_FILE = "cellterminal_server.cfg";

    // Category names
    private static final String CATEGORY_TABS = "tabs";
    private static final String CATEGORY_POLLING = "polling";
    private static final String CATEGORY_CELL_OPERATIONS = "cell_operations";
    private static final String CATEGORY_MISC = "misc";

    private static CellTerminalServerConfig instance;

    private final Configuration config;

    // Tab settings
    private final Property tabTerminalEnabledProperty;
    private final Property tabInventoryEnabledProperty;
    private final Property tabPartitionEnabledProperty;
    private final Property tabStorageBusInventoryEnabledProperty;
    private final Property tabStorageBusPartitionEnabledProperty;

    private boolean tabTerminalEnabled = true;
    private boolean tabInventoryEnabled = true;
    private boolean tabPartitionEnabled = true;
    private boolean tabStorageBusInventoryEnabled = true;
    private boolean tabStorageBusPartitionEnabled = true;

    // Polling settings
    private final Property storageBusPollingEnabledProperty;
    private final Property pollingIntervalProperty;

    private boolean storageBusPollingEnabled = false;  // Disabled by default for performance
    private int pollingInterval = 20;  // Ticks (20 = 1 second)

    // Cell operation settings
    private final Property cellEjectEnabledProperty;
    private final Property cellInsertEnabledProperty;
    private final Property cellSwapEnabledProperty;

    private boolean cellEjectEnabled = true;
    private boolean cellInsertEnabled = true;
    private boolean cellSwapEnabled = true;

    // Misc settings
    private final Property partitionEditEnabledProperty;
    private final Property priorityEditEnabledProperty;
    private final Property upgradeInsertEnabledProperty;
    private final Property upgradeExtractEnabledProperty;

    private boolean partitionEditEnabled = true;
    private boolean priorityEditEnabled = true;
    private boolean upgradeInsertEnabled = true;
    private boolean upgradeExtractEnabled = true;

    private CellTerminalServerConfig(File configDir) {
        File configFile = new File(configDir, CONFIG_FILE);
        this.config = new Configuration(configFile);

        // Tab settings
        config.setCategoryComment(CATEGORY_TABS,
            "Enable or disable individual tabs in the Cell Terminal GUI.\n" +
            "Disabled tabs will appear grayed out and show a message in the tooltip.");
        config.setCategoryLanguageKey(CATEGORY_TABS, "config.cellterminal.config.server.tabs");

        this.tabTerminalEnabledProperty = config.get(CATEGORY_TABS, "terminalTabEnabled", true,
            "Enable the Terminal tab (overview of all cells)");
        this.tabTerminalEnabledProperty.setLanguageKey("config.cellterminal.config.server.tabs.terminal");
        this.tabTerminalEnabled = this.tabTerminalEnabledProperty.getBoolean();

        this.tabInventoryEnabledProperty = config.get(CATEGORY_TABS, "inventoryTabEnabled", true,
            "Enable the Inventory tab (view cell contents)");
        this.tabInventoryEnabledProperty.setLanguageKey("config.cellterminal.config.server.tabs.inventory");
        this.tabInventoryEnabled = this.tabInventoryEnabledProperty.getBoolean();

        this.tabPartitionEnabledProperty = config.get(CATEGORY_TABS, "partitionTabEnabled", true,
            "Enable the Partition tab (edit cell filters)");
        this.tabPartitionEnabledProperty.setLanguageKey("config.cellterminal.config.server.tabs.partition");
        this.tabPartitionEnabled = this.tabPartitionEnabledProperty.getBoolean();

        this.tabStorageBusInventoryEnabledProperty = config.get(CATEGORY_TABS, "storageBusInventoryTabEnabled", true,
            "Enable the Storage Bus Inventory tab (view storage bus contents)");
        this.tabStorageBusInventoryEnabledProperty.setLanguageKey("config.cellterminal.config.server.tabs.storage_bus_inventory");
        this.tabStorageBusInventoryEnabled = this.tabStorageBusInventoryEnabledProperty.getBoolean();

        this.tabStorageBusPartitionEnabledProperty = config.get(CATEGORY_TABS, "storageBusPartitionTabEnabled", true,
            "Enable the Storage Bus Partition tab (edit storage bus filters)");
        this.tabStorageBusPartitionEnabledProperty.setLanguageKey("config.cellterminal.config.server.tabs.storage_bus_partition");
        this.tabStorageBusPartitionEnabled = this.tabStorageBusPartitionEnabledProperty.getBoolean();

        // Polling settings
        config.setCategoryComment(CATEGORY_POLLING,
            "Storage bus polling settings.\n" +
            "WARNING: Storage bus polling can be expensive on large networks!\n" +
            "It requires iterating through all storage buses and their inventories.\n" +
            "Consider keeping polling disabled and reopening the terminal to refresh.");
        config.setCategoryLanguageKey(CATEGORY_POLLING, "config.cellterminal.config.server.polling");

        this.storageBusPollingEnabledProperty = config.get(CATEGORY_POLLING, "storageBusPollingEnabled", false,
            "Enable automatic polling of storage bus data while on storage bus tabs.\n" +
            "WARNING: This can impact server performance on large networks!\n" +
            "When disabled, storage bus data is only fetched once per terminal session.\n" +
            "Reopen the terminal to manually refresh.");
        this.storageBusPollingEnabledProperty.setLanguageKey("config.cellterminal.config.server.polling.enabled");
        this.storageBusPollingEnabled = this.storageBusPollingEnabledProperty.getBoolean();

        this.pollingIntervalProperty = config.get(CATEGORY_POLLING, "pollingInterval", 20,
            "How often to poll for storage bus updates, in ticks (20 ticks = 1 second).\n" +
            "Higher values reduce server load but make data less responsive.\n" +
            "Only applies when storage bus polling is enabled.", 1, 1200);
        this.pollingIntervalProperty.setLanguageKey("config.cellterminal.config.server.polling.interval");
        this.pollingInterval = this.pollingIntervalProperty.getInt();

        // Cell operation settings
        config.setCategoryComment(CATEGORY_CELL_OPERATIONS,
            "Cell operation permissions.\n" +
            "These settings control whether cells can be inserted/ejected from drives using the Cell Terminal GUI.\n" +
            "Disabling these forces players to manage cells directly at the drives/chests instead.");
        config.setCategoryLanguageKey(CATEGORY_CELL_OPERATIONS, "config.cellterminal.config.server.cell_ops");

        this.cellEjectEnabledProperty = config.get(CATEGORY_CELL_OPERATIONS, "cellEjectEnabled", true,
            "Allow ejecting/picking up cells from drives/chests through the Cell Terminal.\n" +
            "Affects the eject button and clicking cells in Inventory/Partition tabs.");
        this.cellEjectEnabledProperty.setLanguageKey("config.cellterminal.config.server.cell_ops.eject");
        this.cellEjectEnabled = this.cellEjectEnabledProperty.getBoolean();

        this.cellInsertEnabledProperty = config.get(CATEGORY_CELL_OPERATIONS, "cellInsertEnabled", true,
            "Allow inserting cells into drives/chests through the Cell Terminal.\n" +
            "When disabled, clicking empty cell slots with a cell in hand will do nothing.");
        this.cellInsertEnabledProperty.setLanguageKey("config.cellterminal.config.server.cell_ops.insert");
        this.cellInsertEnabled = this.cellInsertEnabledProperty.getBoolean();

        this.cellSwapEnabledProperty = config.get(CATEGORY_CELL_OPERATIONS, "cellSwapEnabled", true,
            "Allow swapping cells between drives/chests through the Cell Terminal.\n" +
            "When disabled, clicking a cell slot with a cell in hand will not swap.\n" +
            "Requires both eject and insert to be enabled to function.");
        this.cellSwapEnabledProperty.setLanguageKey("config.cellterminal.config.server.cell_ops.swap");
        this.cellSwapEnabled = this.cellSwapEnabledProperty.getBoolean();

        // Misc settings
        config.setCategoryComment(CATEGORY_MISC,
            "Miscellaneous settings for various Cell Terminal features.");
        config.setCategoryLanguageKey(CATEGORY_MISC, "config.cellterminal.config.server.misc");

        this.partitionEditEnabledProperty = config.get(CATEGORY_MISC, "partitionEditEnabled", true,
            "Allow editing cell partitions through the Cell Terminal.\n" +
            "When disabled, all partition modification features are blocked.");
        this.partitionEditEnabledProperty.setLanguageKey("config.cellterminal.config.server.misc.partition_edit");
        this.partitionEditEnabled = this.partitionEditEnabledProperty.getBoolean();

        this.priorityEditEnabledProperty = config.get(CATEGORY_MISC, "priorityEditEnabled", true,
            "Allow editing drive/storage bus priorities through the Cell Terminal.\n" +
            "When disabled, the priority field will be read-only.");
        this.priorityEditEnabledProperty.setLanguageKey("config.cellterminal.config.server.misc.priority_edit");
        this.priorityEditEnabled = this.priorityEditEnabledProperty.getBoolean();

        this.upgradeInsertEnabledProperty = config.get(CATEGORY_MISC, "upgradeInsertEnabled", true,
            "Allow inserting upgrades into cells and storage buses through the Cell Terminal.\n" +
            "When disabled, clicking entries while holding upgrades will do nothing.");
        this.upgradeInsertEnabledProperty.setLanguageKey("config.cellterminal.config.server.misc.upgrade_insert");
        this.upgradeInsertEnabled = this.upgradeInsertEnabledProperty.getBoolean();

        this.upgradeExtractEnabledProperty = config.get(CATEGORY_MISC, "upgradeExtractEnabled", true,
            "Allow extracting upgrades from cells and storage buses through the Cell Terminal.\n" +
            "When disabled, clicking upgrade icons will do nothing.");
        this.upgradeExtractEnabledProperty.setLanguageKey("config.cellterminal.config.server.misc.upgrade_extract");
        this.upgradeExtractEnabled = this.upgradeExtractEnabledProperty.getBoolean();

        if (config.hasChanged()) config.save();
    }

    public static void init(File configDir) {
        if (instance == null) instance = new CellTerminalServerConfig(configDir);
    }

    public static CellTerminalServerConfig getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CellTerminalServerConfig not initialized! Call init() during preInit.");
        }

        return instance;
    }

    /**
     * Check if the instance has been initialized.
     * Useful for client-side checks where server config may not be available yet.
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    /**
     * Sync values from the config file after GUI changes.
     * Call this after the config GUI closes or when ConfigChangedEvent fires.
     */
    public void syncFromConfig() {
        // Reload all values from their properties
        this.tabTerminalEnabled = this.tabTerminalEnabledProperty.getBoolean();
        this.tabInventoryEnabled = this.tabInventoryEnabledProperty.getBoolean();
        this.tabPartitionEnabled = this.tabPartitionEnabledProperty.getBoolean();
        this.tabStorageBusInventoryEnabled = this.tabStorageBusInventoryEnabledProperty.getBoolean();
        this.tabStorageBusPartitionEnabled = this.tabStorageBusPartitionEnabledProperty.getBoolean();

        this.storageBusPollingEnabled = this.storageBusPollingEnabledProperty.getBoolean();
        this.pollingInterval = this.pollingIntervalProperty.getInt();

        this.cellEjectEnabled = this.cellEjectEnabledProperty.getBoolean();
        this.cellInsertEnabled = this.cellInsertEnabledProperty.getBoolean();
        this.cellSwapEnabled = this.cellSwapEnabledProperty.getBoolean();

        this.partitionEditEnabled = this.partitionEditEnabledProperty.getBoolean();
        this.priorityEditEnabled = this.priorityEditEnabledProperty.getBoolean();
        this.upgradeInsertEnabled = this.upgradeInsertEnabledProperty.getBoolean();
        this.upgradeExtractEnabled = this.upgradeExtractEnabledProperty.getBoolean();

        if (config.hasChanged()) config.save();
    }

    /**
     * Save the config file.
     */
    public void save() {
        if (config.hasChanged()) config.save();
    }

    /**
     * Get the underlying Configuration object.
     * Useful for ConfigChangedEvent handling.
     */
    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Get the path to the config file for display in the config GUI.
     */
    public String getConfigFilePath() {
        return config.getConfigFile().getAbsolutePath();
    }

    /**
     * Get all category names for the config GUI.
     */
    public Set<String> getCategoryNames() {
        return config.getCategoryNames();
    }

    /**
     * Get a category by name for the config GUI.
     */
    public ConfigCategory getCategory(String name) {
        return config.getCategory(name);
    }

    // Tab getters

    public boolean isTabTerminalEnabled() {
        return tabTerminalEnabled;
    }

    public boolean isTabInventoryEnabled() {
        return tabInventoryEnabled;
    }

    public boolean isTabPartitionEnabled() {
        return tabPartitionEnabled;
    }

    public boolean isTabStorageBusInventoryEnabled() {
        return tabStorageBusInventoryEnabled;
    }

    public boolean isTabStorageBusPartitionEnabled() {
        return tabStorageBusPartitionEnabled;
    }

    /**
     * Check if a specific tab is enabled by its index.
     * @param tabIndex The tab index (0-4)
     * @return true if the tab is enabled
     */
    public boolean isTabEnabled(int tabIndex) {
        switch (tabIndex) {
            case 0:
                return tabTerminalEnabled;
            case 1:
                return tabInventoryEnabled;
            case 2:
                return tabPartitionEnabled;
            case 3:
                return tabStorageBusInventoryEnabled;
            case 4:
                return tabStorageBusPartitionEnabled;
            default:
                return false;
        }
    }

    // Polling getters

    public boolean isStorageBusPollingEnabled() {
        return storageBusPollingEnabled;
    }

    public int getPollingInterval() {
        return pollingInterval;
    }

    // Cell operation getters

    public boolean isCellEjectEnabled() {
        return cellEjectEnabled;
    }

    public boolean isCellInsertEnabled() {
        return cellInsertEnabled;
    }

    public boolean isCellSwapEnabled() {
        // Swap requires both eject and insert
        return cellSwapEnabled && cellEjectEnabled && cellInsertEnabled;
    }

    // Misc getters

    public boolean isPartitionEditEnabled() {
        return partitionEditEnabled;
    }

    public boolean isPriorityEditEnabled() {
        return priorityEditEnabled;
    }

    public boolean isUpgradeInsertEnabled() {
        return upgradeInsertEnabled;
    }

    public boolean isUpgradeExtractEnabled() {
        return upgradeExtractEnabled;
    }
}
