/**
 * Group header widgets for the Cell Terminal GUI.
 * <p>
 * Headers appear at the top of each collapsible group in the tab content area. They display
 * the storage device/bus/subnet name (right-click to rename, double-click to highlight in-world),
 * an icon, and group-specific controls like priority or I/O mode.
 * Tree connectors link headers to their child lines.
 * <p>
 * <b>Header implementations:</b>
 * <ul>
 *   <li>{@link com.cellterminal.gui.widget.header.AbstractHeader}: Base class with icon,
 *       renameable name field, and tree connector rendering.</li>
 *   <li>{@link com.cellterminal.gui.widget.header.StorageHeader}: ME Drive/Chest header
 *       showing priority, total byte usage, and upgrade summary.</li>
 *   <li>{@link com.cellterminal.gui.widget.header.StorageBusHeader}: Storage bus header
 *       with I/O mode indicator and partition filter status.</li>
 *   <li>{@link com.cellterminal.gui.widget.header.SubnetHeader}: Subnet header with
 *       visibility toggle and connection count.</li>
 *   <li>{@link com.cellterminal.gui.widget.header.TempAreaHeader}: Temporary cell area
 *       header with occupied slot count.</li>
 * </ul>
 *
 * @see com.cellterminal.gui.widget.line
 * @see com.cellterminal.gui.rename
 */
package com.cellterminal.gui.widget.header;
