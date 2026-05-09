/**
 * Client-side data model and state management for the Cell Terminal GUI.
 * <p>
 * <b>Data holder classes (deserialized from server NBT):</b>
 * <ul>
 *   <li>{@link com.cellterminal.client.StorageInfo}: ME Drive / ME Chest metadata
 *       (position, dimension, priority, block icon) and a list of {@code CellInfo}
 *       objects for each slot.</li>
 *   <li>{@link com.cellterminal.client.CellInfo}: Individual cell data: storage type,
 *       byte/type usage, partition config, contents, upgrades, and cell item.</li>
 *   <li>{@link com.cellterminal.client.StorageBusInfo}: Storage bus data: storage type,
 *       connected inventory, partition config, contents, upgrades, access mode, and
 *       priority.</li>
 *   <li>{@link com.cellterminal.client.SubnetInfo}: Subnet connection data: connection
 *       points (outbound/inbound), subnet inventory, metadata, and security state.</li>
 *   <li>{@link com.cellterminal.client.TempCellInfo}: Wrapper for cells in the temporary
 *       storage area.</li>
 *   <li>{@link com.cellterminal.client.EmptySlotInfo}: Represents empty cell slots.</li>
 * </ul>
 * <p>
 * <b>Enums and filters:</b>
 * <ul>
 *   <li>{@link com.cellterminal.client.StorageType}: Storage channel type enum
 *       (ITEM, FLUID, ESSENTIA, GAS). Used across cells, buses, and subnets.</li>
 *   <li>{@link com.cellterminal.client.CellFilter}: Visibility filter types with
 *       tri-state (SHOW_ALL, SHOW_ONLY, HIDE).</li>
 *   <li>{@link com.cellterminal.client.SearchFilterMode}: Search target mode
 *       (INVENTORY, PARTITION, MIXED).</li>
 *   <li>{@link com.cellterminal.client.SlotLimit}: Content display limit settings.</li>
 * </ul>
 * <p>
 * <b>Row data for rendering:</b>
 * <ul>
 *   <li>{@link com.cellterminal.client.CellContentRow}: A single row of cell content.</li>
 *   <li>{@link com.cellterminal.client.StorageBusContentRow}: A single row of bus content.</li>
 *   <li>{@link com.cellterminal.client.SubnetConnectionRow}: A row for subnet connections.</li>
 * </ul>
 * <p>
 * <b>Other:</b>
 * <ul>
 *   <li>{@link com.cellterminal.client.Prioritizable}: Interface for objects with priority.</li>
 *   <li>{@link com.cellterminal.client.AdvancedSearchParser}: Parses advanced search syntax.</li>
 *   <li>{@link com.cellterminal.client.KeyBindings} / {@link com.cellterminal.client.KeyInputHandler}: Keybinding support.</li>
 *   <li>{@link com.cellterminal.client.TabStateManager}: Active tab tracking.</li>
 * </ul>
 */
package com.cellterminal.client;
