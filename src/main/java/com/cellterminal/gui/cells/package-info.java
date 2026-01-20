/**
 * Cell Terminal - Cell Rendering Module
 *
 * This package contains all cell-specific GUI rendering and interaction logic.
 * It handles the display and behavior of ME storage cells (items, fluids, essentia)
 * in the Inventory and Partition tabs.
 *
 * Key Classes:
 * - {@link com.cellterminal.gui.cells.CellRenderer} - Core rendering for cell slots and contents
 * - {@link com.cellterminal.gui.cells.CellHoverState} - Hover tracking for cells
 * - {@link com.cellterminal.gui.cells.CellSlotRenderer} - Slot background and item rendering
 * - {@link com.cellterminal.gui.cells.CellTreeRenderer} - Tree line rendering connecting cells to storage
 *
 * This module should NOT contain any storage bus-related logic.
 * Storage bus rendering is handled in {@link com.cellterminal.gui.storagebus}.
 *
 * @see com.cellterminal.gui.render.InventoryTabRenderer
 * @see com.cellterminal.gui.render.PartitionTabRenderer
 */
package com.cellterminal.gui.cells;
