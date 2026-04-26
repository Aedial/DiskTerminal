/**
 * Inline rename support for the Cell Terminal GUI.
 * <p>
 * Provides the infrastructure for right-click-to-rename on storage devices, cells,
 * storage buses, and subnets. The rename system uses a persistent text field manager
 * that survives frame rebuilds.
 * <p>
 * <b>Classes:</b>
 * <ul>
 *   <li>{@link com.cellterminal.gui.rename.Renameable}: Interface for objects that can be
 *       renamed (implemented by {@code SubnetInfo}, {@code StorageInfo}, {@code CellInfo},
 *       {@code StorageBusInfo}).</li>
 *   <li>{@link com.cellterminal.gui.rename.InlineRenameManager}: Singleton managing the
 *       active rename text field, persisting across frame rebuilds.</li>
 *   <li>{@link com.cellterminal.gui.rename.RenameTargetType}: Enum identifying the rename
 *       target type (STORAGE, CELL, STORAGE_BUS, SUBNET) for server-side packet routing.</li>
 * </ul>
 *
 * @see com.cellterminal.gui.widget.header.AbstractHeader
 * @see com.cellterminal.network
 */
package com.cellterminal.gui.rename;
