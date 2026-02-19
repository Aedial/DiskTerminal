package com.cellterminal.client;


/**
 * Enum representing how subnet content should be displayed in each tab.
 * This controls whether subnet storage information is shown alongside main network content.
 */
public enum SubnetVisibility {

    /**
     * Don't show subnet content - only show main network storage.
     */
    DONT_SHOW("dont_show", "cellterminal.subnet_visibility.dont_show"),

    /**
     * Show only favorited subnets' content alongside main network.
     */
    SHOW_FAVORITES("favorites", "cellterminal.subnet_visibility.favorites"),

    /**
     * Show all subnet content alongside main network.
     */
    SHOW_ALL("all", "cellterminal.subnet_visibility.all");

    private final String configName;
    private final String translationKey;

    SubnetVisibility(String configName, String translationKey) {
        this.configName = configName;
        this.translationKey = translationKey;
    }

    /**
     * Get the name used for config storage.
     */
    public String getConfigName() {
        return configName;
    }

    /**
     * Get the translation key for this visibility mode.
     */
    public String getTranslationKey() {
        return translationKey;
    }

    /**
     * Cycle to the next visibility mode.
     */
    public SubnetVisibility next() {
        SubnetVisibility[] values = values();

        return values[(ordinal() + 1) % values.length];
    }

    /**
     * Get SubnetVisibility from config name.
     */
    public static SubnetVisibility fromConfigName(String name) {
        for (SubnetVisibility vis : values()) {
            if (vis.configName.equalsIgnoreCase(name)) return vis;
        }

        return DONT_SHOW;
    }

    /**
     * Get SubnetVisibility from enum name.
     */
    public static SubnetVisibility fromName(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DONT_SHOW;
        }
    }
}
