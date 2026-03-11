package com.cellterminal.gui;


/**
 * Central location for all GUI layout constants used in the Cell Terminal.
 * <p>
 * This file allows easy customization of visual appearance without
 * diving into the main rendering or behavior classes.
 * <p>
 * Constants are organized by category:
 * <ul>
 *   <li>General layout (dimensions, padding)</li>
 *   <li>Tab configuration</li>
 *   <li>Row/slot configuration</li>
 *   <li>Colors</li>
 *   <li>Timing (click detection)</li>
 * </ul>
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

    /** Tab indices */
    // FIXME: these should probably be derived from tab widget registration
    public static final int TAB_TERMINAL = 0;
    public static final int TAB_INVENTORY = 1;
    public static final int TAB_PARTITION = 2;
    public static final int TAB_TEMP_AREA = 3;
    public static final int TAB_STORAGE_BUS_INVENTORY = 4;
    public static final int TAB_STORAGE_BUS_PARTITION = 5;
    public static final int TAB_NETWORK_TOOLS = 6;

    public static final int LAST_TAB = TAB_NETWORK_TOOLS;

    /** Width of each tab */
    public static final int TAB_WIDTH = 22;

    /** Height of each tab */
    public static final int TAB_HEIGHT = 22;

    /** Y offset for tabs (above the GUI) */
    public static final int TAB_Y_OFFSET = -22;

    // ========================================
    // ATLAS POSITIONS
    // ========================================

    /** Texture atlas width (for buttons, slots, etc.) */
    public static final int ATLAS_WIDTH = 64;

    /** Texture atlas height (for buttons, slots, etc.) */
    public static final int ATLAS_HEIGHT = 128;

    /** Small button size (e.g., partition-all, clear) */
    public static final int SMALL_BUTTON_SIZE = 8;

    /** Tab 1 button size */
    public static final int TAB1_BUTTON_SIZE = 12;

    /** Subnet button size */
    public static final int SUBNET_BUTTON_SIZE = 12;

    /** Search Mode button size */
    public static final int SEARCH_MODE_BUTTON_SIZE = 10;

    /** Size of a standard slot (16x16 item + 2px border) */
    public static final int SLOT_SIZE = 18;

    /** Size of a mini slot for cell contents (16x16) */
    public static final int MINI_SLOT_SIZE = 16;

    /** Size of the run button for Network Tools */
    public static final int NETWORK_TOOL_RUN_BUTTON_SIZE = 16;

    /** Size of the side buttons for the terminal (settings) */
    public static final int TERMINAL_SIDE_BUTTON_SIZE = 16;

    /** Size of the ? tooltip pseudo-button for Search Bar and Network Tools */
    public static final int TOOLTIP_BUTTON_SIZE = 10;

    // 5x2 * 8x8 for small buttons

    /** Small Buttons: Texture X positions in the atlas */
    public static final int SMALL_BUTTON_X = 0;

    /** Small Buttons: Texture Y positions in the atlas */
    public static final int SMALL_BUTTON_Y = 0;

    // 3x2 * 12x12 for terminal tab buttons
    // + 2x2 * 12x12 for subnet buttons

    /** Tab 1 Buttons: Texture X positions in the atlas */
    public static final int TAB1_BUTTON_X = 0;

    /** Tab 1 Buttons: Texture Y positions in the atlas */
    public static final int TAB1_BUTTON_Y = SMALL_BUTTON_Y + 2 * SMALL_BUTTON_SIZE;

    /** Subnet Buttons: Texture X positions in the atlas */
    public static final int SUBNET_BUTTON_X = 3 * TAB1_BUTTON_SIZE;

    /** Subnet Buttons: Texture Y positions in the atlas */
    public static final int SUBNET_BUTTON_Y = TAB1_BUTTON_Y;

    // 3x2 * 10x10 for search mode buttons

    /** Search Mode Buttons: Texture X positions in the atlas */
    public static final int SEARCH_MODE_BUTTON_X = 0;

    /** Search Mode Buttons: Texture Y positions in the atlas */
    public static final int SEARCH_MODE_BUTTON_Y = TAB1_BUTTON_Y + 2 * TAB1_BUTTON_SIZE;

    // 1x3 * 16x16 for network tool buttons
    // + 1x2 * 16x16 for terminal style buttons
    // + 1x2 * 16x16 for red terminal buttons
    // + 1x2 * 16x16 for green terminal buttons

    /** Network Tool Run Button: Texture X position */
    public static final int NETWORK_TOOL_RUN_BUTTON_X = 0;

    /** Network Tool Run Button: Texture Y position */
    public static final int NETWORK_TOOL_RUN_BUTTON_Y = SEARCH_MODE_BUTTON_Y + 2 * SEARCH_MODE_BUTTON_SIZE;

    /** Terminal Style Button: Texture X position */
    public static final int TERMINAL_STYLE_BUTTON_X = NETWORK_TOOL_RUN_BUTTON_X + NETWORK_TOOL_RUN_BUTTON_SIZE;

    /** Terminal Style Button: Texture Y position */
    public static final int TERMINAL_STYLE_BUTTON_Y = NETWORK_TOOL_RUN_BUTTON_Y;

    /** Terminal Red Button: Texture X position */
    public static final int TERMINAL_RED_BUTTON_X = TERMINAL_STYLE_BUTTON_X + TERMINAL_SIDE_BUTTON_SIZE;

    /** Terminal Red Button: Texture Y position */
    public static final int TERMINAL_RED_BUTTON_Y = TERMINAL_STYLE_BUTTON_Y;

    /** Terminal Green Button: Texture X position */
    public static final int TERMINAL_GREEN_BUTTON_X = TERMINAL_RED_BUTTON_X + TERMINAL_SIDE_BUTTON_SIZE;

    /** Terminal Green Button: Texture Y position */
    public static final int TERMINAL_GREEN_BUTTON_Y = TERMINAL_STYLE_BUTTON_Y;

    // 2x1 * 16x16 for mini slots

    /** Mini slot background: Texture X position */
    public static final int MINI_SLOT_X = NETWORK_TOOL_RUN_BUTTON_X + NETWORK_TOOL_RUN_BUTTON_SIZE;

    /** Mini slot background: Texture Y position */
    public static final int MINI_SLOT_Y = NETWORK_TOOL_RUN_BUTTON_Y + 2 * NETWORK_TOOL_RUN_BUTTON_SIZE;

    // 1x2 * 10x10 for tooltip buttons
    // + 2x1 * 18x18 for slot backgrounds

    /** Tooltip Button: Texture X position */
    public static final int TOOLTIP_BUTTON_X = 0;

    /** Tooltip Button: Texture Y position */
    public static final int TOOLTIP_BUTTON_Y = NETWORK_TOOL_RUN_BUTTON_Y + 3 * NETWORK_TOOL_RUN_BUTTON_SIZE;

    /** Slot background: Texture X position */
    public static final int SLOT_BACKGROUND_X = TOOLTIP_BUTTON_X + TOOLTIP_BUTTON_SIZE;

    /** Slot background: Texture Y position */
    public static final int SLOT_BACKGROUND_Y = TOOLTIP_BUTTON_Y;

    // ========================================
    // SLOT CONFIGURATION
    // ========================================

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
    public static final int BUTTON_SIZE = 16;

    /** X position of eject button in terminal tab */
    public static final int BUTTON_EJECT_X = 135;

    /** X position of inventory button in terminal tab */
    public static final int BUTTON_INVENTORY_X = 150;

    /** X position of partition button in terminal tab */
    public static final int BUTTON_PARTITION_X = 165;

    /** X position of IO mode button in storage bus headers (before priority field) */
    public static final int BUTTON_IO_MODE_X = 120;

    /** X position for upgrade card icons (left margin area) */
    public static final int CARDS_X = 3;

    /** Size of the small button background texture (button SIZE + 1px padding each side) */
    public static final int SMALL_BUTTON_BG_SIZE = SMALL_BUTTON_SIZE + 2;

    // ========================================
    // TERMINAL TAB CELL LAYOUT
    // ========================================

    /** X offset from CELL_INDENT to the name/bar/buttons area */
    public static final int CELL_NAME_X_OFFSET = 18;

    /** Usage bar width in pixels */
    public static final int USAGE_BAR_WIDTH = BUTTON_EJECT_X - CELL_INDENT - CELL_NAME_X_OFFSET - 4;

    /** Usage bar height in pixels */
    public static final int USAGE_BAR_HEIGHT = 4;

    // ========================================
    // HEADER LAYOUT
    // ========================================

    /** X position of header name/location text (icon + gap) */
    public static final int HEADER_NAME_X = GUI_INDENT + 20;

    /** Maximum pixel width for header name text (before IO mode / priority area) */
    public static final int HEADER_NAME_MAX_WIDTH = BUTTON_IO_MODE_X - HEADER_NAME_X - 4;

    /** Maximum pixel width for location text (extends to right edge unlike name) */
    public static final int HEADER_LOCATION_MAX_WIDTH = CONTENT_RIGHT_EDGE - HEADER_NAME_X;

    /** X position of expand/collapse indicator text "[+]"/"[-]" */
    public static final int EXPAND_ICON_X = 167;

    /** Y offset for the tree connector at the bottom of a header */
    public static final int HEADER_CONNECTOR_Y_OFFSET = ROW_HEIGHT - 3;

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

    /** Selection highlight color (for multi-select) */
    public static final int COLOR_SELECTION_HIGHLIGHT = 0x5055FF55;

    /** Selection background color (light blue) */
    public static final int COLOR_SELECTION = 0x405599DD;

    /** Selected header name color (dark blue) */
    public static final int COLOR_NAME_SELECTED = 0x204080;

    /** Custom display name color (green, used for renamed cells/storages) */
    public static final int COLOR_CUSTOM_NAME = 0xFF2E7D32;

    /** Usage bar background color */
    public static final int COLOR_USAGE_BAR_BACKGROUND = 0xFF555555;

    /** Usage bar colors */
    public static final int COLOR_USAGE_LOW = 0xFF33FF33;
    public static final int COLOR_USAGE_MEDIUM = 0xFFFFAA00;
    public static final int COLOR_USAGE_HIGH = 0xFFFF3333;

    // ========================================
    // TIMING
    // ========================================

    /** Double-click detection time in milliseconds */
    public static final long DOUBLE_CLICK_TIME_MS = 400;

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
