/**
 * Cable part implementation for the Cell Terminal.
 * <p>
 * Contains the part that attaches to ME cables, providing the wired Cell Terminal
 * functionality. The part manages power/channel state, temporary cell inventory,
 * and GUI container opening.
 * <p>
 * <b>Classes:</b>
 * <ul>
 *   <li>{@link com.cellterminal.part.PartCellTerminal} — Cable-attachable part implementing
 *       the Cell Terminal display. Stores a temporary cell inventory for pre-partitioning
 *       and manages the part's AE2 power and channel requirements.</li>
 * </ul>
 *
 * @see com.cellterminal.items.ItemCellTerminal
 * @see com.cellterminal.container.ContainerCellTerminal
 */
package com.cellterminal.part;
