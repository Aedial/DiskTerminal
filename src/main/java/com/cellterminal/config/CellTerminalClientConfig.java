package com.cellterminal.config;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.HashMap;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.cellterminal.client.CellFilter;
import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.client.SlotLimit;
import com.cellterminal.client.SubnetVisibility;


/**
 * Client-side configuration for Cell Terminal GUI preferences.
 * Uses Forge's Configuration system for proper config file management.
 */
public class CellTerminalClientConfig {

    private static final String CONFIG_FILE = "cellterminal_client.cfg";
    private static final String CATEGORY_SETTINGS = "settings";
    private static final String CATEGORY_GUI = "gui";
    private static final String CATEGORY_FILTERS = "filters";

    private static CellTerminalClientConfig instance;

    private final Configuration config;

    // Settings properties (shown in config GUI)
    private final Property maxHighlightDistanceProperty;
    private final Property highlightDurationProperty;
    private final Property arrowScalePercentProperty;
    private final Property textScalePercentProperty;
    private final Property adaptiveTextScaleProperty;
    private final Property adaptiveTextScaleMinPercentProperty;
    private final Property adaptiveTextScaleMaxPercentProperty;
    private int maxHighlightDistance = -1;  // -1 = unlimited
    private int highlightDuration = 15;  // seconds
    private int arrowScalePercent = 100;  // Arrow size scale percentage
    private int textScalePercent = 100;   // Arrow text scale percentage
    private boolean adaptiveTextScale = true;  // Scale text based on arrow distance from camera
    private int adaptiveTextScaleMinPercent = 100;  // Minimum adaptive text scale
    private int adaptiveTextScaleMaxPercent = 200;  // Maximum adaptive text scale

    // GUI state properties (hidden from config GUI - persistent state)
    private final Property selectedTabProperty;
    private final Property terminalStyleProperty;
    private final Property searchFilterProperty;
    private final Property searchModeProperty;
    private final Property cellSlotLimitProperty;
    private final Property busSlotLimitProperty;
    private final Property lastViewedNetworkIdProperty;
    private final Property subnetVisibilityProperty;
    private int selectedTab = 0;
    private TerminalStyle terminalStyle = TerminalStyle.SMALL;
    private String searchFilter = "";
    private SearchFilterMode searchMode = SearchFilterMode.MIXED;
    private SlotLimit cellSlotLimit = SlotLimit.UNLIMITED;
    private SlotLimit busSlotLimit = SlotLimit.UNLIMITED;
    private long lastViewedNetworkId = 0;  // 0 = main network
    private SubnetVisibility subnetVisibility = SubnetVisibility.DONT_SHOW;

    // Filter states - separate maps for cells (tabs 0-2) and storage buses (tabs 3-4)
    private final Map<CellFilter, CellFilter.State> cellFilterStates = new EnumMap<>(CellFilter.class);
    private final Map<CellFilter, CellFilter.State> busFilterStates = new EnumMap<>(CellFilter.class);
    private final Map<String, Property> filterProperties = new HashMap<>();

    private CellTerminalClientConfig() {
        File configFile = new File(Minecraft.getMinecraft().gameDir, "config/" + CONFIG_FILE);
        this.config = new Configuration(configFile);

        // Settings category (shown in config GUI)
        config.setCategoryComment(CATEGORY_SETTINGS,
            "User-configurable settings for Cell Terminal rendering and behavior.");
        config.setCategoryLanguageKey(CATEGORY_SETTINGS, "config.cellterminal.config.client.settings");

        this.maxHighlightDistanceProperty = config.get(CATEGORY_SETTINGS, "maxHighlightDistance", -1,
            "Maximum distance (in blocks) from the player for block highlighting.\n" +
            "Set to -1 for unlimited. Blocks beyond this distance will not be highlighted.", -1, 10000);
        this.maxHighlightDistanceProperty.setLanguageKey("config.cellterminal.config.client.settings.max_highlight_distance");
        this.maxHighlightDistance = this.maxHighlightDistanceProperty.getInt();

        this.highlightDurationProperty = config.get(CATEGORY_SETTINGS, "highlightDuration", 15,
            "Duration (in seconds) for block highlighting when double-clicking entries.\n" +
            "Higher values keep highlights visible longer.", 1, 3600);
        this.highlightDurationProperty.setLanguageKey("config.cellterminal.config.client.settings.highlight_duration");
        this.highlightDuration = this.highlightDurationProperty.getInt();

        this.arrowScalePercentProperty = config.get(CATEGORY_SETTINGS, "arrowScalePercent", 100,
            "Scale percentage for directional arrows pointing to distant highlights.\n" +
            "Larger values make arrows bigger, smaller values make them smaller.", 10, 1000);
        this.arrowScalePercentProperty.setLanguageKey("config.cellterminal.config.client.settings.arrow_scale");
        this.arrowScalePercent = this.arrowScalePercentProperty.getInt();

        this.textScalePercentProperty = config.get(CATEGORY_SETTINGS, "textScalePercent", 100,
            "Scale percentage for distance text on directional arrows.", 10, 1000);
        this.textScalePercentProperty.setLanguageKey("config.cellterminal.config.client.settings.text_scale");
        this.textScalePercent = this.textScalePercentProperty.getInt();

        this.adaptiveTextScaleProperty = config.get(CATEGORY_SETTINGS, "adaptiveTextScale", true,
            "Whether to automatically scale arrow text based on arrow's distance from camera.\n" +
            "Arrows at the edge of view will have larger text for better readability.");
        this.adaptiveTextScaleProperty.setLanguageKey("config.cellterminal.config.client.settings.adaptive_text_scale");
        this.adaptiveTextScale = this.adaptiveTextScaleProperty.getBoolean();

        this.adaptiveTextScaleMinPercentProperty = config.get(CATEGORY_SETTINGS, "adaptiveTextScaleMinPercent", 100,
            "Minimum text scale multiplier (in percent) when using adaptive text scaling.", 10, 1000);
        this.adaptiveTextScaleMinPercentProperty.setLanguageKey("config.cellterminal.config.client.settings.adaptive_text_scale_min");
        this.adaptiveTextScaleMinPercent = this.adaptiveTextScaleMinPercentProperty.getInt();

        this.adaptiveTextScaleMaxPercentProperty = config.get(CATEGORY_SETTINGS, "adaptiveTextScaleMaxPercent", 200,
            "Maximum text scale multiplier (in percent) when using adaptive text scaling.", 10, 1000);
        this.adaptiveTextScaleMaxPercentProperty.setLanguageKey("config.cellterminal.config.client.settings.adaptive_text_scale_max");
        this.adaptiveTextScaleMaxPercent = this.adaptiveTextScaleMaxPercentProperty.getInt();

        // GUI state category (hidden from config GUI - persistent state)
        this.selectedTabProperty = config.get(CATEGORY_GUI, "selectedTab", 0,
            "The currently selected tab in the Cell Terminal GUI (0=Terminal, 1=Inventory, 2=Partition)");
        this.selectedTabProperty.setLanguageKey("config.cellterminal.gui.selectedTab");
        this.selectedTab = this.selectedTabProperty.getInt();

        this.terminalStyleProperty = config.get(CATEGORY_GUI, "terminalStyle", TerminalStyle.SMALL.name(),
            "Terminal size style: SMALL (fixed 8 rows), TALL (expands to fill screen)");
        this.terminalStyleProperty.setLanguageKey("config.cellterminal.gui.terminalStyle");
        this.terminalStyle = TerminalStyle.fromName(this.terminalStyleProperty.getString());

        this.searchFilterProperty = config.get(CATEGORY_GUI, "searchFilter", "",
            "The last search filter text used in the Cell Terminal");
        this.searchFilterProperty.setLanguageKey("config.cellterminal.gui.searchFilter");
        this.searchFilter = this.searchFilterProperty.getString();

        this.searchModeProperty = config.get(CATEGORY_GUI, "searchMode", SearchFilterMode.MIXED.name(),
            "Search filter mode: INVENTORY (search contents), PARTITION (search filters), MIXED (search both)");
        this.searchModeProperty.setLanguageKey("config.cellterminal.gui.searchMode");
        this.searchMode = SearchFilterMode.fromName(this.searchModeProperty.getString());

        this.cellSlotLimitProperty = config.get(CATEGORY_GUI, "cellSlotLimit", SlotLimit.UNLIMITED.name(),
            "Slot limit for cell content display: LIMIT_8, LIMIT_32, LIMIT_64, or UNLIMITED");
        this.cellSlotLimitProperty.setLanguageKey("config.cellterminal.gui.cellSlotLimit");
        this.cellSlotLimit = SlotLimit.fromName(this.cellSlotLimitProperty.getString());

        this.busSlotLimitProperty = config.get(CATEGORY_GUI, "busSlotLimit", SlotLimit.UNLIMITED.name(),
            "Slot limit for storage bus content display: LIMIT_8, LIMIT_32, LIMIT_64, or UNLIMITED");
        this.busSlotLimitProperty.setLanguageKey("config.cellterminal.gui.busSlotLimit");
        this.busSlotLimit = SlotLimit.fromName(this.busSlotLimitProperty.getString());

        this.lastViewedNetworkIdProperty = config.get(CATEGORY_GUI, "lastViewedNetworkId", "0",
            "The last viewed network ID (0 = main network, other = subnet ID). Stored as string to support long values.");
        this.lastViewedNetworkIdProperty.setLanguageKey("config.cellterminal.gui.lastViewedNetworkId");
        try {
            this.lastViewedNetworkId = Long.parseLong(this.lastViewedNetworkIdProperty.getString());
        } catch (NumberFormatException e) {
            this.lastViewedNetworkId = 0;
        }

        this.subnetVisibilityProperty = config.get(CATEGORY_GUI, "subnetVisibility", SubnetVisibility.DONT_SHOW.name(),
            "Subnet content visibility in each tab: DONT_SHOW, SHOW_FAVORITES, or SHOW_ALL");
        this.subnetVisibilityProperty.setLanguageKey("config.cellterminal.gui.subnetVisibility");
        this.subnetVisibility = SubnetVisibility.fromName(this.subnetVisibilityProperty.getString());

        // Load filter states for cells and storage buses separately
        config.setCategoryLanguageKey(CATEGORY_FILTERS, "config.cellterminal.filters");
        for (CellFilter filter : CellFilter.values()) {
            // Cell filter states (tabs 0-2)
            String cellKey = filter.getConfigKey(false);
            Property cellProp = config.get(CATEGORY_FILTERS, cellKey, CellFilter.State.SHOW_ALL.name(),
                "Filter state for " + filter.name() + " (cells): SHOW_ALL, SHOW_ONLY, or HIDE");
            cellProp.setLanguageKey("config.cellterminal.filter." + cellKey);
            filterProperties.put(cellKey, cellProp);
            cellFilterStates.put(filter, CellFilter.State.fromName(cellProp.getString()));

            // Storage bus filter states (tabs 3-4)
            String busKey = filter.getConfigKey(true);
            Property busProp = config.get(CATEGORY_FILTERS, busKey, CellFilter.State.SHOW_ALL.name(),
                "Filter state for " + filter.name() + " (storage buses): SHOW_ALL, SHOW_ONLY, or HIDE");
            busProp.setLanguageKey("config.cellterminal.filter." + busKey);
            filterProperties.put(busKey, busProp);
            busFilterStates.put(filter, CellFilter.State.fromName(busProp.getString()));
        }

        config.setCategoryLanguageKey(CATEGORY_GUI, "config.cellterminal.gui");

        if (config.hasChanged()) config.save();
    }

    public static CellTerminalClientConfig getInstance() {
        if (instance == null) instance = new CellTerminalClientConfig();

        return instance;
    }

    public int getSelectedTab() {
        return selectedTab;
    }

    public void setSelectedTab(int tab) {
        if (this.selectedTab == tab) return;

        this.selectedTab = tab;
        this.selectedTabProperty.set(tab);
        config.save();
    }

    public TerminalStyle getTerminalStyle() {
        return terminalStyle;
    }

    public void setTerminalStyle(TerminalStyle style) {
        if (this.terminalStyle == style) return;

        this.terminalStyle = style;
        this.terminalStyleProperty.set(style.name());
        config.save();
    }

    /**
     * Cycle to the next terminal style.
     * @return The new terminal style after cycling
     */
    public TerminalStyle cycleTerminalStyle() {
        TerminalStyle next = terminalStyle.next();
        setTerminalStyle(next);

        return next;
    }

    public String getSearchFilter() {
        return searchFilter;
    }

    public void setSearchFilter(String filter) {
        if (this.searchFilter.equals(filter)) return;

        this.searchFilter = filter;
        this.searchFilterProperty.set(filter);
        config.save();
    }

    public SearchFilterMode getSearchMode() {
        return searchMode;
    }

    public void setSearchMode(SearchFilterMode mode) {
        if (this.searchMode == mode) return;

        this.searchMode = mode;
        this.searchModeProperty.set(mode.name());
        config.save();
    }

    /**
     * Cycle to the next search mode.
     * @return The new search mode after cycling
     */
    public SearchFilterMode cycleSearchMode() {
        SearchFilterMode next = searchMode.next();
        setSearchMode(next);

        return next;
    }

    /**
     * Get the slot limit for cells (tabs 0-2).
     */
    public SlotLimit getCellSlotLimit() {
        return cellSlotLimit;
    }

    /**
     * Set the slot limit for cells (tabs 0-2).
     */
    public void setCellSlotLimit(SlotLimit limit) {
        if (this.cellSlotLimit == limit) return;

        this.cellSlotLimit = limit;
        this.cellSlotLimitProperty.set(limit.name());
        config.save();
    }

    /**
     * Cycle to the next cell slot limit.
     * @return The new slot limit after cycling
     */
    public SlotLimit cycleCellSlotLimit() {
        SlotLimit next = cellSlotLimit.next();
        setCellSlotLimit(next);

        return next;
    }

    /**
     * Get the slot limit for storage buses (tabs 3-4).
     */
    public SlotLimit getBusSlotLimit() {
        return busSlotLimit;
    }

    /**
     * Set the slot limit for storage buses (tabs 3-4).
     */
    public void setBusSlotLimit(SlotLimit limit) {
        if (this.busSlotLimit == limit) return;

        this.busSlotLimit = limit;
        this.busSlotLimitProperty.set(limit.name());
        config.save();
    }

    /**
     * Cycle to the next storage bus slot limit.
     * @return The new slot limit after cycling
     */
    public SlotLimit cycleBusSlotLimit() {
        SlotLimit next = busSlotLimit.next();
        setBusSlotLimit(next);

        return next;
    }

    /**
     * Get the last viewed network ID (0 = main network).
     */
    public long getLastViewedNetworkId() {
        return lastViewedNetworkId;
    }

    /**
     * Set the last viewed network ID. Use 0 for main network.
     */
    public void setLastViewedNetworkId(long networkId) {
        if (this.lastViewedNetworkId == networkId) return;

        this.lastViewedNetworkId = networkId;
        this.lastViewedNetworkIdProperty.set(String.valueOf(networkId));
        config.save();
    }

    /**
     * Get the subnet visibility mode for tabs.
     */
    public SubnetVisibility getSubnetVisibility() {
        return subnetVisibility;
    }

    /**
     * Set the subnet visibility mode for tabs.
     */
    public void setSubnetVisibility(SubnetVisibility visibility) {
        if (this.subnetVisibility == visibility) return;

        this.subnetVisibility = visibility;
        this.subnetVisibilityProperty.set(visibility.name());
        config.save();
    }

    /**
     * Cycle to the next subnet visibility mode.
     * @return The new visibility mode after cycling
     */
    public SubnetVisibility cycleSubnetVisibility() {
        SubnetVisibility next = subnetVisibility.next();
        setSubnetVisibility(next);

        return next;
    }

    /**
     * Get the appropriate slot limit for the current tab.
     * @param forStorageBus true for storage bus tabs (3-4), false for cell tabs (0-2)
     */
    public SlotLimit getSlotLimit(boolean forStorageBus) {
        return forStorageBus ? busSlotLimit : cellSlotLimit;
    }

    /**
     * Get the filter state for a specific filter.
     * @param filter The filter type
     * @param forStorageBus true for storage bus tabs (3-4), false for cell tabs (0-2)
     */
    public CellFilter.State getFilterState(CellFilter filter, boolean forStorageBus) {
        Map<CellFilter, CellFilter.State> states = forStorageBus ? busFilterStates : cellFilterStates;

        return states.getOrDefault(filter, CellFilter.State.SHOW_ALL);
    }

    /**
     * Set the filter state for a specific filter.
     * @param filter The filter type
     * @param state The new state
     * @param forStorageBus true for storage bus tabs (3-4), false for cell tabs (0-2)
     */
    public void setFilterState(CellFilter filter, CellFilter.State state, boolean forStorageBus) {
        Map<CellFilter, CellFilter.State> states = forStorageBus ? busFilterStates : cellFilterStates;
        if (states.get(filter) == state) return;

        states.put(filter, state);
        String key = filter.getConfigKey(forStorageBus);
        Property prop = filterProperties.get(key);
        if (prop != null) {
            prop.set(state.name());
            config.save();
        }
    }

    /**
     * Get all filter states as a map.
     * @param forStorageBus true for storage bus tabs (3-4), false for cell tabs (0-2)
     */
    public Map<CellFilter, CellFilter.State> getAllFilterStates(boolean forStorageBus) {
        return new EnumMap<>(forStorageBus ? busFilterStates : cellFilterStates);
    }

    public int getMaxHighlightDistance() {
        return maxHighlightDistance;
    }

    /**
     * Get the highlight duration in milliseconds.
     */
    public long getHighlightDurationMs() {
        return highlightDuration * 1000L;
    }

    /**
     * Get the arrow scale factor (1.0 = 100%).
     */
    public float getArrowScale() {
        return arrowScalePercent / 100.0f;
    }

    /**
     * Get the text scale factor (1.0 = 100%).
     */
    public float getTextScale() {
        return textScalePercent / 100.0f;
    }

    /**
     * Check if adaptive text scaling is enabled.
     */
    public boolean isAdaptiveTextScale() {
        return adaptiveTextScale;
    }

    /**
     * Get the minimum adaptive text scale factor.
     */
    public float getAdaptiveTextScaleMin() {
        return adaptiveTextScaleMinPercent / 100.0f;
    }

    /**
     * Get the maximum adaptive text scale factor.
     */
    public float getAdaptiveTextScaleMax() {
        return adaptiveTextScaleMaxPercent / 100.0f;
    }

    /**
     * Get the underlying Configuration object.
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
     * Get the settings category name (shown in config GUI).
     */
    public String getSettingsCategoryName() {
        return CATEGORY_SETTINGS;
    }

    /**
     * Sync values from the config file after GUI changes.
     */
    public void syncFromConfig() {
        this.maxHighlightDistance = this.maxHighlightDistanceProperty.getInt();
        this.highlightDuration = this.highlightDurationProperty.getInt();
        this.arrowScalePercent = this.arrowScalePercentProperty.getInt();
        this.textScalePercent = this.textScalePercentProperty.getInt();
        this.adaptiveTextScale = this.adaptiveTextScaleProperty.getBoolean();
        this.adaptiveTextScaleMinPercent = this.adaptiveTextScaleMinPercentProperty.getInt();
        this.adaptiveTextScaleMaxPercent = this.adaptiveTextScaleMaxPercentProperty.getInt();

        if (config.hasChanged()) config.save();
    }

    /**
     * Terminal size styles.
     */
    public enum TerminalStyle {
        SMALL,  // Fixed 8 rows (default)
        TALL;   // Expands to fill available screen space

        public TerminalStyle next() {
            TerminalStyle[] values = values();

            return values[(ordinal() + 1) % values.length];
        }

        public static TerminalStyle fromName(String name) {
            try {
                return valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                return SMALL;
            }
        }
    }
}
