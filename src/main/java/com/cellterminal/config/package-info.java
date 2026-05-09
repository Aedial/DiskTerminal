/**
 * Configuration system for the Cell Terminal.
 * <p>
 * Manages both server-enforced and client-local settings using Forge's {@code Configuration}
 * system. Server config controls feature availability and polling behavior; client config
 * stores GUI preferences and persistent state.
 * <p>
 * <b>Classes:</b>
 * <ul>
 *   <li>{@link com.cellterminal.config.CellTerminalServerConfig}: Server-enforced settings
 *       (tab availability, cell operations, storage bus polling intervals).</li>
 *   <li>{@link com.cellterminal.config.CellTerminalClientConfig}: Client GUI preferences
 *       (terminal style, search mode, filter state, favorites).</li>
 *   <li>{@link com.cellterminal.config.CellTerminalConfigGui}: In-game config GUI using
 *       Forge's config element system.</li>
 *   <li>{@link com.cellterminal.config.CellTerminalConfigGuiFactory}: Factory registered
 *       via {@code @Mod} for in-game config screen access.</li>
 * </ul>
 */
package com.cellterminal.config;
