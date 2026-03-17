/**
 * Small inline action buttons for the Cell Terminal widget tree and storage buses.
 * This class exists mainly to consolidate 5 buttons as one. Other buttons are usually
 * derived from {@link com.cellterminal.gui.buttons.GuiAtlasButton}.
 * Honestly, this package may be refactored into a GuiAtlasButton subclass at some point.
 * <p>
 * <b>Classes:</b>
 * <ul>
 *   <li>{@link com.cellterminal.gui.widget.button.ButtonType} — Enum identifying button
 *       function (DO_PARTITION, READ_ONLY, etc) and mapping to texture coordinates.</li>
 *   <li>{@link com.cellterminal.gui.widget.button.SmallButton} — Fixed-size textured button
 *       with hover highlight and click callback.</li>
 *   <li>{@link com.cellterminal.gui.widget.button.SmallSwitchingButton} — Toggle variant
 *       cycling through multiple states on click (e.g., I/O mode).</li>
 * </ul>
 *
 * @see com.cellterminal.gui.widget.line.TerminalLine
 */
package com.cellterminal.gui.widget.button;
