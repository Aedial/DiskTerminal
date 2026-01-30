package com.cellterminal.gui;


/**
 * Central location for all GUI layout constants used in the Cell Terminal.
 *
 * This file allows easy customization of visual appearance without
 * diving into the main rendering or behavior classes.
 *
 * Constants are organized by category:
 * - General layout (dimensions, padding)
 * - Tab configuration
 * - Row/slot configuration
 * - Colors
 * - Timing (click detection)
 */
public final class GuiConstants {

    private GuiConstants() {}

    // ========================================
    // GENERAL LAYOUT
    // ========================================

    /** Base GUI width */
    public static final int GUI_WIDTH = 208;

    /** Left margin for content area */
    public static final int GUI_INDENT = 22;

    /** Additional indent for cell slots (relative to GUI_INDENT) */
    public static final int CELL_INDENT = GUI_INDENT + 12;

    /** Right edge of content area */
    public static final int CONTENT_RIGHT_EDGE = 180;

    /** Right edge of hoverable area */
    public static final int HOVER_RIGHT_EDGE = 185;

    /** Left edge of hoverable area */
    public static final int HOVER_LEFT_EDGE = 4;

    /** Inner padding for panels and popups */
    public static final int PADDING = 8;

    /** Y position where content starts (below header) */
    public static final int CONTENT_START_Y = 18;

    // ========================================
    // ROW AND SCROLLING
    // ========================================

    /** Height of each row in the scrollable area */
    public static final int ROW_HEIGHT = 18;

    /** Minimum number of visible rows */
    public static final int MIN_ROWS = 6;

    /** Default number of visible rows (small mode) */
    public static final int DEFAULT_ROWS = 8;

    /** Magic height number for tall mode calculation (header + footer heights) */
    public static final int MAGIC_HEIGHT_NUMBER = 18 + 98;

    // ========================================
    // TAB CONFIGURATION
    // ========================================

    /** Number of tabs in the terminal */
    public static final int TAB_COUNT = 5;

    /** Tab indices */
    public static final int TAB_TERMINAL = 0;
    public static final int TAB_INVENTORY = 1;
    public static final int TAB_PARTITION = 2;
    public static final int TAB_STORAGE_BUS_INVENTORY = 3;
    public static final int TAB_STORAGE_BUS_PARTITION = 4;

    /** Width of each tab */
    public static final int TAB_WIDTH = 22;

    /** Height of each tab */
    public static final int TAB_HEIGHT = 22;

    /** Y offset for tabs (above the GUI) */
    public static final int TAB_Y_OFFSET = -22;

    // ========================================
    // SLOT CONFIGURATION
    // ========================================

    /** Size of a standard slot (16x16 item + 2px border) */
    public static final int SLOT_SIZE = 18;

    /** Size of a mini slot for cell contents (16x16) */
    public static final int MINI_SLOT_SIZE = 16;

    /** Number of content slots per row for cells */
    public static final int CELL_SLOTS_PER_ROW = 8;

    /** Number of content slots per row for storage buses */
    public static final int STORAGE_BUS_SLOTS_PER_ROW = 9;

    /** Maximum partition slots for cells */
    public static final int MAX_CELL_PARTITION_SLOTS = 63;

    /** Maximum partition slots for storage buses */
    public static final int MAX_STORAGE_BUS_PARTITION_SLOTS = 63;

    // ========================================
    // BUTTON CONFIGURATION
    // ========================================

    /** Standard button size */
    public static final int BUTTON_SIZE = 14;

    /** Small button size (e.g., partition-all, clear) */
    public static final int SMALL_BUTTON_SIZE = 8;

    /** X position of eject button in terminal tab */
    public static final int BUTTON_EJECT_X = 135;

    /** X position of inventory button in terminal tab */
    public static final int BUTTON_INVENTORY_X = 150;

    /** X position of partition button in terminal tab */
    public static final int BUTTON_PARTITION_X = 165;

    /** X position of IO mode button in storage bus headers (before priority field) */
    public static final int BUTTON_IO_MODE_X = 115;

    // ========================================
    // CONTROLS HELP WIDGET
    // ========================================

    /** Right margin between controls help panel and GUI */
    public static final int CONTROLS_HELP_RIGHT_MARGIN = 4;

    /** Left margin between screen edge and controls help panel */
    public static final int CONTROLS_HELP_LEFT_MARGIN = 4;

    /** Inner padding for controls help panel */
    public static final int CONTROLS_HELP_PADDING = 4;

    /** Height per line in controls help panel */
    public static final int CONTROLS_HELP_LINE_HEIGHT = 10;

    // ========================================
    // POPUP CONFIGURATION
    // ========================================

    /** Slots per row in popup views */
    public static final int POPUP_SLOTS_PER_ROW = 9;

    /** Maximum rows in popup views */
    public static final int POPUP_MAX_ROWS = 7;

    /** Popup header height */
    public static final int POPUP_HEADER_HEIGHT = 20;

    /** Popup button height */
    public static final int POPUP_BUTTON_HEIGHT = 14;

    /** Popup footer height */
    public static final int POPUP_FOOTER_HEIGHT = 12;

    // ========================================
    // COLORS
    // ========================================

    /** Background color for slots */
    public static final int COLOR_SLOT_BACKGROUND = 0xFF8B8B8B;

    /** Dark border for slots (top-left) */
    public static final int COLOR_SLOT_BORDER_DARK = 0xFF373737;

    /** Light border for slots (bottom-right) */
    public static final int COLOR_SLOT_BORDER_LIGHT = 0xFFFFFFFF;

    /** Hover highlight overlay */
    public static final int COLOR_HOVER_HIGHLIGHT = 0x80FFFFFF;

    /** Row hover highlight */
    public static final int COLOR_ROW_HOVER = 0x50CCCCCC;

    /** Storage header hover */
    public static final int COLOR_STORAGE_HEADER_HOVER = 0x30FFFFFF;

    /** Separator line color */
    public static final int COLOR_SEPARATOR = 0xFF606060;

    /** Tree line color */
    public static final int COLOR_TREE_LINE = 0xFF808080;

    /** Button color (normal) */
    public static final int COLOR_BUTTON_NORMAL = 0xFF8B8B8B;

    /** Button color (hovered) */
    public static final int COLOR_BUTTON_HOVER = 0xFF707070;

    /** Button highlight (top-left) */
    public static final int COLOR_BUTTON_HIGHLIGHT = 0xFFFFFFFF;

    /** Button shadow (bottom-right) */
    public static final int COLOR_BUTTON_SHADOW = 0xFF555555;

    /** Tab background (selected) */
    public static final int COLOR_TAB_SELECTED = 0xFFC6C6C6;

    /** Tab background (hovered) */
    public static final int COLOR_TAB_HOVER = 0xFFA0A0A0;

    /** Tab background (normal) */
    public static final int COLOR_TAB_NORMAL = 0xFF8B8B8B;

    /** Partition indicator color (green) */
    public static final int COLOR_PARTITION_INDICATOR = 0xFF55FF55;

    /** Partition slot tint color (amber) */
    public static final int COLOR_PARTITION_SLOT_TINT = 0x30FFAA00;

    /** Selection highlight color (for multi-select) */
    public static final int COLOR_SELECTION_HIGHLIGHT = 0x5055FF55;

    /** Selection background color (light blue) */
    public static final int COLOR_SELECTION = 0x405599DD;

    /** Green button (partition all) */
    public static final int COLOR_BUTTON_GREEN = 0xFF33CC33;

    /** Red button (clear) */
    public static final int COLOR_BUTTON_RED = 0xFFCC3333;

    /** Usage bar colors */
    public static final int COLOR_USAGE_LOW = 0xFF33FF33;
    public static final int COLOR_USAGE_MEDIUM = 0xFFFFAA00;
    public static final int COLOR_USAGE_HIGH = 0xFFFF3333;

    // ========================================
    // TIMING
    // ========================================

    /** Double-click detection time in milliseconds */
    public static final long DOUBLE_CLICK_TIME_MS = 400;

    /** Storage bus polling interval in ticks */
    public static final int STORAGE_BUS_POLL_INTERVAL_TICKS = 20;

    // ========================================
    // TEXT COLORS
    // ========================================

    /** Normal text color */
    public static final int COLOR_TEXT_NORMAL = 0x404040;

    /** Secondary text color (locations, hints) */
    public static final int COLOR_TEXT_SECONDARY = 0x808080;

    /** Placeholder/empty text color */
    public static final int COLOR_TEXT_PLACEHOLDER = 0x606060;
}
