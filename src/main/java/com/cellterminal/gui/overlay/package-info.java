/**
 * Client-side overlay message rendering system.
 * <p>
 * Displays temporary feedback messages (success, error, warning) as colored text overlays
 * on the screen. Messages are sent from the server via {@code PacketPlayerFeedback} and
 * rendered with a fade-out animation.
 * <p>
 * This is a better alternative to chat messages/toasts for transient feedback,
 * as the messages are more visible and less likely to be missed during focused GUI interaction.
 * Chat messages are still written for persistence, but the overlay provides immediate visual feedback.
 * <p>
 * Server-side code should use {@link com.cellterminal.util.PlayerMessageHelper} to send messages,
 * which are routed to MessageHelper through packets without direct client references.
 * <p>
 * <b>Classes:</b>
 * <ul>
 *   <li>{@link com.cellterminal.gui.overlay.MessageHelper}: Client-side entry point that
 *       receives messages from packets and queues them for rendering.</li>
 *   <li>{@link com.cellterminal.gui.overlay.MessageType}: Enum for message severity/color
 *       (SUCCESS=green, ERROR=red, WARNING=yellow).</li>
 *   <li>{@link com.cellterminal.gui.overlay.OverlayMessage}: Data class holding message text,
 *       type, creation time, and fade timing.</li>
 *   <li>{@link com.cellterminal.gui.overlay.OverlayMessageRenderer}: Renders the active
 *       message list on screen each frame.</li>
 * </ul>
 *
 * @see com.cellterminal.util.PlayerMessageHelper
 * @see com.cellterminal.network
 */
package com.cellterminal.gui.overlay;
