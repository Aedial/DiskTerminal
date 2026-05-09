/**
 * Server-side data handlers for the Cell Terminal container.
 * <p>
 * These handlers are responsible for scanning the ME network, serializing cell/bus/subnet
 * data into NBT compounds, and processing client-initiated actions (eject, insert, partition,
 * network tool execution, etc.). They are invoked exclusively from packet handlers on the
 * server thread.
 * <p>
 * <b>Data handlers (server → client):</b>
 * <ul>
 *   <li>{@link com.cellterminal.container.handler.CellDataHandler}: Gathers cell NBT data
 *       (bytes, types, partition, contents, upgrades) via storage scanner registries.</li>
 *   <li>{@link com.cellterminal.container.handler.StorageBusDataHandler}: Gathers storage bus
 *       NBT data (partition, I/O mode, priority) via storage bus scanner registries.</li>
 *   <li>{@link com.cellterminal.container.handler.SubnetDataHandler}: Gathers subnet list data
 *       for the subnet overview tab.</li>
 * </ul>
 * <p>
 * <b>Action handlers (client → server):</b>
 * <ul>
 *   <li>{@link com.cellterminal.container.handler.CellActionHandler}: Eject, insert, extract
 *       upgrade, pickup cell operations.</li>
 *   <li>{@link com.cellterminal.container.handler.NetworkToolActionHandler}: Batch operations
 *       (mass partition, attribute unique distribution) across cells and storage buses.</li>
 *   <li>{@link com.cellterminal.container.handler.TempCellActionHandler}: Temporary cell area
 *       operations (add, remove, partition preview).</li>
 * </ul>
 *
 * @see com.cellterminal.container.ContainerCellTerminalBase
 * @see com.cellterminal.network
 */
package com.cellterminal.container.handler;
