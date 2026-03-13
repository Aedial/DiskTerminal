package com.cellterminal.client;

import java.util.List;

import net.minecraft.item.ItemStack;


/**
 * Represents a row of connection content or partition items for display in the subnet overview.
 * Each SubnetConnectionRow shows up to 9 items from a connection point, starting at a given index.
 * <p>
 * This is analogous to {@link CellContentRow} but for subnet connections. Like CellContentRow,
 * each row has an {@code isPartitionRow} flag to distinguish content rows (read-only, items
 * flowing through the connection) from partition rows (editable, storage bus filter config).
 * <p>
 * The flattened display under a {@link SubnetConnectionEntry} header is:
 * <pre>
 *   SubnetConnectionRow (isPartitionRow=false) → content rows (subnet's whole storage)
 *   SubnetConnectionRow (isPartitionRow=true)  → partition rows (storage bus filter config)
 * </pre>
 *
 * @see SubnetConnectionEntry
 * @see SubnetInfo.ConnectionPoint
 */
public class SubnetConnectionRow {

    private final SubnetInfo subnet;
    private final SubnetInfo.ConnectionPoint connection;
    private final int connectionIndex;
    private final int startIndex;
    private final boolean isFirstRow;
    private final boolean isPartitionRow;
    private final boolean usesSubnetInventory;

    /**
     * @param subnet The subnet this row belongs to
     * @param connection The connection point this row displays items for
     * @param connectionIndex Index of this connection in subnet's connection list
     * @param startIndex The starting index of items to display (0, 9, 18, etc.)
     * @param isFirstRow Whether this is the first row for this content/partition section
     * @param isPartitionRow Whether this row displays partition data (vs content data)
     * @param usesSubnetInventory Whether content comes from the subnet's ME storage inventory
     */
    public SubnetConnectionRow(SubnetInfo subnet, SubnetInfo.ConnectionPoint connection,
                                int connectionIndex, int startIndex, boolean isFirstRow,
                                boolean isPartitionRow, boolean usesSubnetInventory) {
        this.subnet = subnet;
        this.connection = connection;
        this.connectionIndex = connectionIndex;
        this.startIndex = startIndex;
        this.isFirstRow = isFirstRow;
        this.isPartitionRow = isPartitionRow;
        this.usesSubnetInventory = usesSubnetInventory;
    }

    public SubnetInfo getSubnet() {
        return subnet;
    }

    public SubnetInfo.ConnectionPoint getConnection() {
        return connection;
    }

    public int getConnectionIndex() {
        return connectionIndex;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public boolean isFirstRow() {
        return isFirstRow;
    }

    /**
     * Whether this row displays partition data (storage bus filter config).
     * False means this row displays content data (items flowing through connection).
     */
    public boolean isPartitionRow() {
        return isPartitionRow;
    }

    /**
     * Whether this row's content comes from the subnet's aggregated ME storage inventory
     * rather than from the per-connection content.
     * Only meaningful for content rows (isPartitionRow=false) on outbound connections.
     */
    public boolean usesSubnetInventory() {
        return usesSubnetInventory;
    }

    /**
     * Get the items for this row's data source.
     * <ul>
     *   <li>Partition rows → connection partition</li>
     *   <li>Content rows with subnet inventory → subnet's ME storage</li>
     *   <li>Content rows without subnet inventory → connection's content</li>
     * </ul>
     */
    public List<ItemStack> getItems() {
        if (isPartitionRow) return connection.getPartition();
        if (usesSubnetInventory) return subnet.getInventory();

        return connection.getContent();
    }

    /**
     * Get the total number of items (in the relevant list, content or partition).
     */
    public int getTotalItemCount() {
        return getItems().size();
    }

    /**
     * Get the max number of slots available for partition editing.
     * Content has no meaningful max (backend decides).
     */
    public int getMaxSlots() {
        return isPartitionRow ? connection.getMaxPartitionSlots() : Integer.MAX_VALUE;
    }
}
