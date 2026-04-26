/**
 * Widget framework for the Cell Terminal GUI.
 * <p>
 * Implements a hierarchical widget tree where each tab contains a scrollable list of
 * headers and content lines. The widget system handles event propagation (clicks, keys,
 * hover), visibility, and rendering order.
 * <p>
 * <b>Widget hierarchy:</b>
 * <pre>
 *   {@link com.cellterminal.gui.widget.IWidget}
 *     └─ {@link com.cellterminal.gui.widget.AbstractWidget}
 *          ├─ {@link com.cellterminal.gui.widget.tab.AbstractTabWidget}      ← Tab containers (see tab/)
 *          │    ├─ {@link com.cellterminal.gui.widget.header.AbstractHeader} ← Content headers (see header/)
 *          │    └─ {@link com.cellterminal.gui.widget.line.AbstractLine}     ← Content rows (see line/)
 *          ├─ {@link com.cellterminal.gui.widget.CardsDisplay}              ← Upgrade card grid
 *          ├─ {@link com.cellterminal.gui.widget.NetworkToolRowWidget}      ← Network tool action row
 *          └─ {@link com.cellterminal.gui.widget.button.SmallButton}        ← Inline action buttons (see button/)
 * </pre>
 * <p>
 * <b>Core classes:</b>
 * <ul>
 *   <li>{@link com.cellterminal.gui.widget.IWidget}: Base interface defining draw, click,
 *       key, hover, and tooltip methods.</li>
 *   <li>{@link com.cellterminal.gui.widget.AbstractWidget}: Common base with position, size,
 *       visibility, hover detection, and rendering utilities.</li>
 *   <li>{@link com.cellterminal.gui.widget.WidgetContainer}: Ordered child list with event
 *       propagation to children in appropriate order.</li>
 *   <li>{@link com.cellterminal.gui.widget.DoubleClickTracker}: Utility for detecting
 *       double-clicks (used by headers for in-world position highlighting).</li>
 * </ul>
 *
 * @see com.cellterminal.gui.widget.tab
 * @see com.cellterminal.gui.widget.header
 * @see com.cellterminal.gui.widget.line
 * @see com.cellterminal.gui.widget.button
 */
package com.cellterminal.gui.widget;
