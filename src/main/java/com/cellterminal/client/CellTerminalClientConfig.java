package com.cellterminal.client;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.HashMap;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;


/**
 * Client-side configuration for Cell Terminal GUI preferences.
 * Uses Forge's Configuration system for proper config file management.
 */
public class CellTerminalClientConfig {

    private static final String CONFIG_FILE = "cellterminal_client.cfg";
    private static final String CATEGORY_GUI = "gui";
    private static final String CATEGORY_FILTERS = "filters";

    private static CellTerminalClientConfig instance;

    private final Configuration config;
    private final Property selectedTabProperty;
    private final Property terminalStyleProperty;
    private final Property searchFilterProperty;
    private final Property searchModeProperty;
    private int selectedTab = 0;
    private TerminalStyle terminalStyle = TerminalStyle.SMALL;
    private String searchFilter = "";
    private SearchFilterMode searchMode = SearchFilterMode.MIXED;

    // Filter states - separate maps for cells (tabs 0-2) and storage buses (tabs 3-4)
    private final Map<CellFilter, CellFilter.State> cellFilterStates = new EnumMap<>(CellFilter.class);
    private final Map<CellFilter, CellFilter.State> busFilterStates = new EnumMap<>(CellFilter.class);
    private final Map<String, Property> filterProperties = new HashMap<>();

    private CellTerminalClientConfig() {
        File configFile = new File(Minecraft.getMinecraft().gameDir, "config/" + CONFIG_FILE);
        this.config = new Configuration(configFile);

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
