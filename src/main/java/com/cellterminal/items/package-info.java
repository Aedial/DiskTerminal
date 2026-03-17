/**
 * Item definitions for the Cell Terminal mod.
 * <p>
 * Contains the physical items that players craft and use to interact with the Cell Terminal.
 * <p>
 * <b>Classes:</b>
 * <ul>
 *   <li>{@link com.cellterminal.items.ItemCellTerminal} — Placeable item that creates a
 *       {@link com.cellterminal.part.PartCellTerminal} when attached to an ME cable.
 *       Implements {@code IPartItem}.</li>
 *   <li>{@link com.cellterminal.items.ItemWirelessCellTerminal} — Battery-powered wireless
 *       terminal extending {@code AEBasePoweredItem}. Implements {@code IWirelessTermHandler}
 *       for portable access to the Cell Terminal GUI.</li>
 * </ul>
 *
 * @see com.cellterminal.part.PartCellTerminal
 */
package com.cellterminal.items;
