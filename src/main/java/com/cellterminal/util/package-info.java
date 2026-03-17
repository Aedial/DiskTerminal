/**
 * Shared utility classes for the Cell Terminal.
 * <p>
 * Contains general-purpose helpers used across both client and server sides.
 * <p>
 * <b>Classes:</b>
 * <ul>
 *   <li>{@link com.cellterminal.util.AE2OldVersionSupport} — Compatibility shim for older
 *       AE2 versions; handles Baubles API fallback via reflection.</li>
 *   <li>{@link com.cellterminal.util.BigStackTracker} — Aggregates {@code IAEStack} counts
 *       using {@code BigInteger} to support totals exceeding {@code Long.MAX_VALUE}.
 *       AE2 supports Long per cell, but the Cell Terminal can combine counts from many cells,
 *       so this is necessary to avoid overflow issues.</li>
 *   <li>{@link com.cellterminal.util.FluidStackKey} — Immutable, hashable wrapper for
 *       {@code FluidStack} comparison by fluid type and NBT (ignoring amount).</li>
 *   <li>{@link com.cellterminal.util.ItemStackKey} — Immutable, hashable wrapper for
 *       {@code ItemStack} comparison by item, metadata, and NBT (ignoring count).
 *       Preferred when comparing items in a network or storage context, as the hash is cached.</li>
 *   <li>{@link com.cellterminal.util.PlayerMessageHelper} — Server-safe helper that sends
 *       colored feedback messages to players via {@code PacketPlayerFeedback}.</li>
 *   <li>{@link com.cellterminal.util.SafeMath} — Arithmetic utilities with overflow detection
 *       for combining counts from multiple cells.</li>
 * </ul>
 *
 * @see com.cellterminal.gui.overlay.MessageHelper
 */
package com.cellterminal.util;
