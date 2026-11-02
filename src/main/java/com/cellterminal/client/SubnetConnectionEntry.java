package com.cellterminal.client;

import com.cellterminal.gui.rename.Renameable;
import com.cellterminal.gui.rename.RenameTargetType;


/**
 * Represents a single connection point as a header-level entry in the subnet overview.
 * <p>
 * Each connection between the main network and a subnet gets its own header row,
 * showing the direction arrow (→/←), icon, connection name, position, and Load button.
 * <p>
 * The flattened display list for subnets is:
 * <pre>
 *   SubnetInfo (main network only, no connections)
 *   SubnetConnectionEntry → connection header (with arrow, per-connection)
 *     SubnetConnectionRow (isPartitionRow=false) → SlotsLine (content)
 *     SubnetConnectionRow (isPartitionRow=true) → SlotsLine (partition/filter)
 * </pre>
 *
 * @see SubnetInfo
 * @see SubnetInfo.ConnectionPoint
 * @see SubnetConnectionRow
 */
public class SubnetConnectionEntry implements Renameable {

    private final SubnetInfo subnet;
    private final SubnetInfo.ConnectionPoint connection;
    private final int connectionIndex;

    /**
     * @param subnet The subnet this connection belongs to
     * @param connection The specific connection point
     * @param connectionIndex Index of this connection in the subnet's connection list
     */
    public SubnetConnectionEntry(SubnetInfo subnet, SubnetInfo.ConnectionPoint connection, int connectionIndex) {
        this.subnet = subnet;
        this.connection = connection;
        this.connectionIndex = connectionIndex;
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

    // ---- Renameable implementation ----

    @Override
    public boolean isRenameable() {
        return true;
    }

    @Override
    public String getCustomName() {
        return connection.getCustomName();
    }

    @Override
    public boolean hasCustomName() {
        return connection.hasCustomName();
    }

    @Override
    public void setCustomName(String name) {
        connection.setCustomName(name);
    }

    @Override
    public RenameTargetType getRenameTargetType() {
        return RenameTargetType.SUBNET;
    }

    @Override
    public long getRenameId() {
        return subnet.getId();
    }

    @Override
    public int getRenameSecondaryId() {
        return connectionIndex;
    }
}
