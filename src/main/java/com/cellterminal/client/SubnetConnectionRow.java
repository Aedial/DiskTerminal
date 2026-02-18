package com.cellterminal.client;

import net.minecraft.item.ItemStack;


/**
 * Represents a row of connection filter items for display in the subnet overview.
 * Each SubnetConnectionRow shows up to 9 filter items from a connection point, starting at a given index.
 *
 * This is analogous to {@link StorageBusContentRow} but for subnet connections.
 */
public class SubnetConnectionRow {

    private final SubnetInfo subnet;
    private final SubnetInfo.ConnectionPoint connection;
    private final int connectionIndex;  // Index of this connection in the subnet's connection list
    private final int filterStartIndex; // Starting index of filter items to display (0, 9, 18, etc.)
    private final boolean isFirstRowForConnection;

    /**
     * @param subnet The subnet this row belongs to
     * @param connection The connection point this row displays filters for
     * @param connectionIndex Index of this connection in subnet's connection list
     * @param filterStartIndex The starting index of filter items to display (0, 9, 18, etc.)
     * @param isFirstRowForConnection Whether this is the first row for this connection
     */
    public SubnetConnectionRow(SubnetInfo subnet, SubnetInfo.ConnectionPoint connection,
                                int connectionIndex, int filterStartIndex, boolean isFirstRowForConnection) {
        this.subnet = subnet;
        this.connection = connection;
        this.connectionIndex = connectionIndex;
        this.filterStartIndex = filterStartIndex;
        this.isFirstRowForConnection = isFirstRowForConnection;
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

    public int getFilterStartIndex() {
        return filterStartIndex;
    }

    public boolean isFirstRowForConnection() {
        return isFirstRowForConnection;
    }

    /**
     * Get the filter item at the given slot index (0-8 for this row).
     * @param slotIndex Slot index within this row (0-8)
     * @return The filter item, or EMPTY if none
     */
    public ItemStack getFilterAt(int slotIndex) {
        int actualIndex = filterStartIndex + slotIndex;

        if (actualIndex < connection.getFilter().size()) {
            return connection.getFilter().get(actualIndex);
        }

        return ItemStack.EMPTY;
    }

    /**
     * Get the total number of filter items in this connection.
     */
    public int getTotalFilterCount() {
        return connection.getFilter().size();
    }

    /**
     * Get the number of filter items displayed in this row (up to 9).
     */
    public int getFilterCountInRow() {
        int remaining = connection.getFilter().size() - filterStartIndex;

        return Math.min(remaining, 9);
    }
}
