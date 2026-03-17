/**
 * Main GUI package for the Cell Terminal.
 * <p>
 * Contains the top-level GUI classes, popup overlays, and shared GUI utilities. The GUI
 * architecture is built on AE2's {@code AEBaseGui} with a custom widget tree system for
 * managing tabs, headers, content lines, and interactive elements.
 * <p>
 * <b>GUI classes:</b>
 * <ul>
 *   <li>{@link com.cellterminal.gui.GuiCellTerminalBase} — Abstract base GUI handling core
 *       layout, tab bar, search field, filter buttons, data synchronization, and mouse/keyboard
 *       event dispatch. This is the central class that ties the entire GUI together.</li>
 *   <li>{@link com.cellterminal.gui.GuiCellTerminal} — Concrete wired terminal GUI.</li>
 *   <li>{@link com.cellterminal.gui.GuiWirelessCellTerminal} — Wireless variant with WUT mode
 *       switching integration.</li>
 *   <li>{@link com.cellterminal.gui.GuiHandler} — Routes {@code IGuiHandler} open requests to
 *       the correct container/GUI combination.</li>
 * </ul>
 * <p>
 * <b>Popup overlays (modals):</b>
 * <ul>
 *   <li>{@link com.cellterminal.gui.PopupCellInventory} — Read-only cell inventory viewer with
 *       double-click-to-partition support.</li>
 *   <li>{@link com.cellterminal.gui.PopupCellPartition} — 63-slot partition editor with JEI
 *       ghost drag-and-drop.</li>
 *   <li>{@link com.cellterminal.gui.GuiModalSearchBar} — Full-width search bar overlay with
 *       enlarged text field.</li>
 * </ul>
 * <p>
 * <b>Shared utilities:</b>
 * <ul>
 *   <li>{@link com.cellterminal.gui.GuiConstants} — Central location for layout constants,
 *       colors, timing values, and texture atlas binding helpers.</li>
 *   <li>{@link com.cellterminal.gui.ComparisonUtils} — Item/fluid stack comparison for
 *       partition operations.</li>
 *   <li>{@link com.cellterminal.gui.SearchFieldHandler} — Single/double/right-click search
 *       field logic.</li>
 *   <li>{@link com.cellterminal.gui.PriorityFieldManager} — Singleton managing inline priority
 *       text fields that persist across frame rebuilds.</li>
 * </ul>
 *
 * @see com.cellterminal.gui.widget
 * @see com.cellterminal.gui.handler
 * @see com.cellterminal.gui.buttons
 */
package com.cellterminal.gui;
