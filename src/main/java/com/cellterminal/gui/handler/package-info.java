/**
 * GUI-side data management and event handling for the Cell Terminal.
 * <p>
 * These classes bridge the raw NBT data received from the server with the widget tree
 * that renders it. They manage tab state, aggregate cell/bus/subnet data into display-ready
 * structures, and coordinate rendering across tabs.
 * <p>
 * <b>Key classes:</b>
 * <ul>
 *   <li>{@link com.cellterminal.gui.handler.TerminalDataManager}: Central data aggregator
 *       that applies chunked per-channel payloads (see {@code com.cellterminal.network.chunked})
 *       and turns them into {@code StorageInfo}, {@code CellInfo}, {@code StorageBusInfo}, and
 *       {@code SubnetInfo} lists. Also applies client-side search/type filters and computes
 *       storage totals.</li>
 *   <li>{@link com.cellterminal.gui.handler.TabManager}: Manages which tab is active, creates
 *       tab widget instances, and handles tab switching.</li>
 *   <li>{@link com.cellterminal.gui.handler.TabRenderingHandler}: Coordinates per-frame
 *       rendering of the active tab, including scroll management and clipping.</li>
 *   <li>{@link com.cellterminal.gui.handler.TooltipHandler}: Collects and renders tooltips
 *       from top-level hovered widgets (not from widgets tree, which handles its own tooltips).</li>
 *   <li>{@link com.cellterminal.gui.handler.JeiGhostHandler}: Handles JEI ghost ingredient
 *       drops for adding items to cell partitions via drag-and-drop.</li>
 *   <li>{@link com.cellterminal.gui.handler.QuickPartitionHandler}: Batch partition setup
 *       from JEI recipe views.</li>
 * </ul>
 *
 * @see com.cellterminal.gui.GuiCellTerminalBase
 * @see com.cellterminal.gui.widget.tab
 */
package com.cellterminal.gui.handler;
