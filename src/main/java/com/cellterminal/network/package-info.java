/**
 * Network packet definitions for client↔server communication.
 * <p>
 * All communication between the Cell Terminal GUI (client) and its container (server) is
 * done via Forge's {@code SimpleNetworkWrapper} packets. Each packet implements
 * {@code IMessage} with a corresponding {@code IMessageHandler}.
 * <p>
 * <b>Central dispatcher:</b>
 * <ul>
 *   <li>{@link com.cellterminal.network.CellTerminalNetwork} — Singleton network handler
 *       registering all packets with discriminator IDs.</li>
 * </ul>
 * <p>
 * <b>Server → Client packets:</b>
 * <ul>
 *   <li>{@link com.cellterminal.network.PacketCellTerminalUpdate} — Compressed NBT containing
 *       full GUI state (cells, buses, subnets). This is the main data channel.</li>
 *   <li>{@link com.cellterminal.network.PacketHighlightBlockClient} — Block highlight overlay
 *       coordinates for rendering storage device outlines.</li>
 *   <li>{@link com.cellterminal.network.PacketPlayerFeedback} — Colored feedback message for
 *       overlay rendering.</li>
 *   <li>{@link com.cellterminal.network.PacketSubnetListUpdate} — Updated visible subnet
 *       list.</li>
 * </ul>
 * <p>
 * <b>Client → Server packets:</b>
 * <ul>
 *   <li>{@link com.cellterminal.network.PacketEjectCell} — Eject cell to player inventory.</li>
 *   <li>{@link com.cellterminal.network.PacketExtractUpgrade} — Extract an upgrade card.</li>
 *   <li>{@link com.cellterminal.network.PacketHighlightBlock} — Request block highlight.</li>
 *   <li>{@link com.cellterminal.network.PacketInsertCell} — Insert held cell into network.</li>
 *   <li>{@link com.cellterminal.network.PacketNetworkToolAction} — Execute batch network tool
 *       operation.</li>
 *   <li>{@link com.cellterminal.network.PacketOpenWirelessTerminal} — Open wireless terminal
 *       GUI.</li>
 *   <li>{@link com.cellterminal.network.PacketPartitionAction} — Modify cell partition.</li>
 *   <li>{@link com.cellterminal.network.PacketPickupCell} — Move cell to cursor from temp
 *       inventory.</li>
 *   <li>{@link com.cellterminal.network.PacketRenameAction} — Rename storage/cell/bus/subnet.</li>
 *   <li>{@link com.cellterminal.network.PacketSetPriority} — Change priority value.</li>
 *   <li>{@link com.cellterminal.network.PacketSlotLimitChange} — Change cell slot limit.</li>
 *   <li>{@link com.cellterminal.network.PacketStorageBusIOMode} — Toggle storage bus I/O
 *       mode.</li>
 *   <li>{@link com.cellterminal.network.PacketStorageBusPartitionAction} — Modify storage bus
 *       partition.</li>
 *   <li>{@link com.cellterminal.network.PacketSubnetAction} — Modify subnet (rename, etc.).</li>
 *   <li>{@link com.cellterminal.network.PacketSubnetListRequest} — Request subnet list.</li>
 *   <li>{@link com.cellterminal.network.PacketSwitchNetwork} — Switch to different
 *       network/subnet.</li>
 *   <li>{@link com.cellterminal.network.PacketTabChange} — Notify tab change for polling
 *       optimization.</li>
 *   <li>{@link com.cellterminal.network.PacketTempCellAction} — Temporary Area cell
 *       operations.</li>
 *   <li>{@link com.cellterminal.network.PacketTempCellPartitionAction} — Temporary Area cell partition
 *       modification.</li>
 *   <li>{@link com.cellterminal.network.PacketUpgradeCell} — Insert an Upgrade card into a cell.</li>
 *   <li>{@link com.cellterminal.network.PacketUpgradeStorageBus} — Insert an Upgrade card into a storage bus.</li>
 * </ul>
 *
 * @see com.cellterminal.container.ContainerCellTerminalBase
 * @see com.cellterminal.gui.handler.TerminalDataManager
 */
package com.cellterminal.network;
