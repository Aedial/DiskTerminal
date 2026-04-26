/**
 * Network tools for batch operations on cells and storage buses.
 * <p>
 * Network tools provide batch operations such as mass-partitioning cells or distributing
 * items by unique attributes across multiple cells. Each tool follows a preview-then-execute
 * pattern with a confirmation modal.
 * <p>
 * <b>Framework:</b>
 * <ul>
 *   <li>{@link com.cellterminal.gui.networktools.INetworkTool}: Interface defining tool
 *       metadata, target selection, preview generation, and execution.</li>
 *   <li>{@link com.cellterminal.gui.networktools.NetworkToolRegistry}: Singleton registry
 *       managing available tools.</li>
 *   <li>{@link com.cellterminal.gui.networktools.GuiToolConfirmationModal}: Modal overlay
 *       confirming tool execution with preview of affected cells/buses.</li>
 *   <li>{@link com.cellterminal.gui.networktools.NetworkToolFilterUtils}: Shared utility
 *       methods for applying type and state filters when selecting tool targets.</li>
 * </ul>
 * <p>
 * <b>Tool implementations:</b>
 * <ul>
 *   <li>{@link com.cellterminal.gui.networktools.MassPartitionCellTool}: Partitions multiple
 *       cells at once based on their current contents.</li>
 *   <li>{@link com.cellterminal.gui.networktools.MassPartitionBusTool}: Partitions multiple
 *       storage buses based on connected inventory contents.</li>
 *   <li>{@link com.cellterminal.gui.networktools.AttributeUniqueTool}: Distributes items
 *       across cells ensuring each cell contains items with unique attributes.</li>
 * </ul>
 *
 * @see com.cellterminal.gui.widget.tab.NetworkToolsTabWidget
 * @see com.cellterminal.container.handler.NetworkToolActionHandler
 */
package com.cellterminal.gui.networktools;
