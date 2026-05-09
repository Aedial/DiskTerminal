/**
 * Forge proxy classes for sided initialization.
 * <p>
 * Follows the standard Forge proxy pattern where common logic is shared and
 * client/server-specific initialization is handled by the appropriate proxy.
 * <p>
 * <b>Classes:</b>
 * <ul>
 *   <li>{@link com.cellterminal.proxy.CommonProxy}: Base proxy handling config loading,
 *       item registration, GUI handler registration, and FML lifecycle events.</li>
 *   <li>{@link com.cellterminal.proxy.ClientProxy}: Client-side proxy registering
 *       keybindings, event handlers (block highlight, overlay), and model loading.</li>
 *   <li>{@link com.cellterminal.proxy.ServerProxy}: Server-side proxy (extends CommonProxy,
 *       currently empty).</li>
 * </ul>
 */
package com.cellterminal.proxy;
