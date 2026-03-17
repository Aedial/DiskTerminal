/**
 * Tab widgets for the Cell Terminal GUI.
 * <p>
 * Each tab is a scrollable container managing its own list of headers and content lines.
 * Tabs are created by {@link com.cellterminal.gui.handler.TabManager} and rendered by
 * {@link com.cellterminal.gui.handler.TabRenderingHandler}.
 * <p>
 * <b>Tab implementations:</b>
 * <ul>
 *   <li>{@link com.cellterminal.gui.widget.tab.AbstractTabWidget} — Base class managing
 *       scrollable content, click propagation, visibility culling, and tree connector
 *       cut points.</li>
 *   <li>{@link com.cellterminal.gui.widget.tab.SubnetOverviewTabWidget} — <b>Tab -1:</b> Subnet
 *       overview. Displays visible subnets with rename and navigation to sub-networks.</li>
 *   <li>{@link com.cellterminal.gui.widget.tab.TerminalTabWidget} — <b>Tab 1:</b> Cells view.
 *       Displays all cells per drive/chest with eject, inventory, partition, and cell rename
 *       controls.</li>
 *   <li>{@link com.cellterminal.gui.widget.tab.CellContentTabWidget} — <b>Tab 2/3:</b> Cell
 *       Contents/Partitions. Searchable/filterable grid showing aggregated cell contents/partitions
 *       with stack totals.</li>
 *   <li>{@link com.cellterminal.gui.widget.tab.TempAreaTabWidget} — <b>Tab 4:</b> Temporary
 *       area. Pre-partition workspace for preparing cells before inserting them into the
 *       network.</li>
 *   <li>{@link com.cellterminal.gui.widget.tab.StorageBusTabWidget} — <b>Tab 5/6:</b> Storage
 *       buses Contents/Partitions. Displays storage buses with contents/partitions, I/O mode toggle, and
 *       priority controls.</li>
 *   <li>{@link com.cellterminal.gui.widget.tab.NetworkToolsTabWidget} — <b>Tab 7:</b> Network
 *       tools. Batch operations (mass partition, attribute unique) with preview and
 *       confirmation modal.</li>
 * </ul>
 * <p>
 * <b>Supporting class:</b>
 * <ul>
 *   <li>{@link com.cellterminal.gui.widget.tab.GuiContext} — Shared data object passed to all
 *       tabs, providing references to the parent GUI, data manager, and event handlers.</li>
 * </ul>
 *
 * @see com.cellterminal.gui.handler.TabManager
 * @see com.cellterminal.gui.widget.header
 * @see com.cellterminal.gui.widget.line
 */
package com.cellterminal.gui.widget.tab;
