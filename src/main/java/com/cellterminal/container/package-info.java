/**
 * Server-side container classes for the Cell Terminal.
 * <p>
 * Containers handle server-side inventory management, grid scanning, and data synchronization
 * with the client GUI. Each container variant binds to a specific opening context (wired terminal,
 * wireless terminal, etc.) but shares common logic via the base class.
 * <p>
 * <b>Key classes:</b>
 * <ul>
 *   <li>{@link com.cellterminal.container.ContainerCellTerminalBase} — Base container that
 *       orchestrates periodic grid scanning, NBT serialization via data handlers, and
 *       packet dispatch to clients. Manages tracker maps for cells, buses, and subnets.</li>
 *   <li>{@link com.cellterminal.container.ContainerCellTerminal} — Wired terminal container.</li>
 *   <li>{@link com.cellterminal.container.ContainerWirelessCellTerminal} — Wireless terminal
 *       container with range/power checks.</li>
 *   <li>{@link com.cellterminal.container.WirelessTempCellInventory} — Temporary cell storage
 *       for wireless terminals.</li>
 * </ul>
 * <p>
 * See the {@code handler/} sub-package for NBT data generation and action handling.
 *
 * @see com.cellterminal.container.handler
 */
package com.cellterminal.container;
