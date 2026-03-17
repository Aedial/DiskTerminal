/**
 * Cell Terminal — an Applied Energistics 2 addon that provides a unified GUI for
 * inspecting and managing all storage devices, storage buses, and subnets in an
 * ME network.
 * <p>
 * <b>Architecture overview:</b>
 * <ul>
 *   <li>{@code client/} — Client-side data holders ({@code *Info} classes), filters,
 *       enums, keybindings, tooltip handlers, and search parsing. The {@code *Info}
 *       classes are the canonical client-side representations of server data.</li>
 *   <li>{@code container/} — Server-side containers and the {@code handler/}
 *       sub-package which handles all NBT serialization and action execution.</li>
 *   <li>{@code gui/} — All GUI rendering: base class, popups, modals, and the
 *       {@code widget/} hierarchy (tabs, headers, lines, buttons).</li>
 *   <li>{@code network/} — Network packets for client↔server communication.
 *       All data is transmitted as NBT via these packets.</li>
 *   <li>{@code integration/} — Mod integrations and the scanner registries that
 *       allow pluggable grid-scanning for different device types.</li>
 *   <li>{@code config/} — Server and client configuration with GUI support.</li>
 *   <li>{@code items/} — Custom items (wired and wireless cell terminals).</li>
 *   <li>{@code part/} — ME cable part for the wired terminal.</li>
 *   <li>{@code proxy/} — Client/server proxy classes for startup logic.</li>
 *   <li>{@code util/} — General-purpose utilities (math, key wrappers, etc.).</li>
 * </ul>
 * <p>
 * <b>Data flow:</b>
 * <ol>
 *   <li>Server scans the ME grid via scanner registries (integration layer).</li>
 *   <li>Data handlers serialize results to NBT ({@code CellDataHandler},
 *       {@code StorageBusDataHandler}, {@code SubnetDataHandler}).</li>
 *   <li>NBT is sent to clients via {@code PacketCellTerminalUpdate}.</li>
 *   <li>Clients deserialize NBT into Info objects ({@code StorageInfo},
 *       {@code CellInfo}, {@code StorageBusInfo}, {@code SubnetInfo}).</li>
 *   <li>GUI renders Info objects via the widget tree ({@code GuiCellTerminalBase}).</li>
 * </ol>
 *
 * @see com.cellterminal.gui.GuiCellTerminalBase
 * @see com.cellterminal.client.StorageInfo
 * @see com.cellterminal.client.CellInfo
 * @see com.cellterminal.client.StorageBusInfo
 * @see com.cellterminal.client.SubnetInfo
 */
package com.cellterminal;
