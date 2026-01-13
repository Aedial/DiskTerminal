package com.cellterminal.client;

import java.io.File;

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
