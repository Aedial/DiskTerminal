/**
 * Content line widgets for the Cell Terminal GUI.
 * <p>
 * Lines represent individual content rows within a tab, appearing below their parent header.
 * They render cell information, slot grids, or structural tree connectors. Lines inherit
 * tree-connector rendering from their base class.
 * <p>
 * <b>Line implementations:</b>
 * <ul>
 *   <li>{@link com.cellterminal.gui.widget.line.AbstractLine} — Base class with tree connector
 *       rendering, tree line button support, and cut point tracking.</li>
 *   <li>{@link com.cellterminal.gui.widget.line.TerminalLine} — Cell row displaying icon,
 *       renameable name, byte/type usage bars, upgrade cards, and action buttons (eject,
 *       inventory, partition).</li>
 *   <li>{@link com.cellterminal.gui.widget.line.CellSlotsLine} — Grid of cell slots showing
 *       cell inventory items from drives or temporary area.</li>
 *   <li>{@link com.cellterminal.gui.widget.line.SlotsLine} — Generic slot grid for displaying
 *       item/fluid stacks with customizable rendering and hover behavior.</li>
 *   <li>{@link com.cellterminal.gui.widget.line.ContinuationLine} — SlotLine variant for continuing
 *       a multi-line slots grid with seamless tree connectors.</li>
 * </ul>
 *
 * @see com.cellterminal.gui.widget.header
 * @see com.cellterminal.gui.widget.button
 */
package com.cellterminal.gui.widget.line;
