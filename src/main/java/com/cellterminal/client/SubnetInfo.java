package com.cellterminal.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;


/**
 * Client-side data holder for subnet connection information received from server.
 * 
 * A subnet is a SEPARATE ME grid that connects to the main network through the
 * ME Passthrough mechanism (IStorageMonitorableAccessor capability). Each connection
 * is one-way:
 * 
 * - Outbound: Storage Bus on main network → Interface on subnet
 *   (Main network reads from / writes to subnet's storage)
 * 
 * - Inbound: Interface on main network ← Storage Bus on subnet
 *   (Subnet reads from / writes to main network's storage)
 * 
 * Both directions can exist simultaneously for bidirectional item flow.
 * Multiple connections to the same subnet are grouped together and sorted by position.
 * 
 * Note: A subnet does NOT require a controller - any cable segment with up to 8 channels
 * can function as a subnet if powered via Energy Acceptor or Quartz Fiber.
 * 
 * Note: P2P Tunnels do NOT create subnets - they teleport channels within the same grid.
 */
public class SubnetInfo {

    /**
     * Represents a single connection point between main network and subnet.
     * Multiple connections can exist to the same subnet.
     */
    public static class ConnectionPoint {

        private final BlockPos pos;           // Position on main network
        private final EnumFacing side;        // Side of the part
        private final boolean isOutbound;     // true = Storage Bus on main, false = Interface on main
        private final ItemStack localIcon;    // Icon of the block on main network
        private final ItemStack remoteIcon;   // Icon of the block on subnet
        private final List<ItemStack> filter; // Filter configuration (for Storage Bus connections)

        public ConnectionPoint(NBTTagCompound nbt) {
            this.pos = BlockPos.fromLong(nbt.getLong("pos"));
            this.side = EnumFacing.byIndex(nbt.getInteger("side"));
            this.isOutbound = nbt.getBoolean("outbound");
            this.localIcon = nbt.hasKey("localIcon") ? new ItemStack(nbt.getCompoundTag("localIcon")) : ItemStack.EMPTY;
            this.remoteIcon = nbt.hasKey("remoteIcon") ? new ItemStack(nbt.getCompoundTag("remoteIcon")) : ItemStack.EMPTY;
            this.filter = new ArrayList<>();

            if (nbt.hasKey("filter")) {
                NBTTagList filterList = nbt.getTagList("filter", Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < filterList.tagCount(); i++) {
                    filter.add(new ItemStack(filterList.getCompoundTagAt(i)));
                }
            }
        }

        public BlockPos getPos() {
            return pos;
        }

        public EnumFacing getSide() {
            return side;
        }

        /**
         * True if this is an outbound connection (Storage Bus on main → Interface on subnet).
         * False if this is an inbound connection (Interface on main ← Storage Bus on subnet).
         */
        public boolean isOutbound() {
            return isOutbound;
        }

        /**
         * Icon of the block on the main network (Storage Bus for outbound, Interface for inbound).
         */
        public ItemStack getLocalIcon() {
            return localIcon;
        }

        /**
         * Icon of the block on the subnet (Interface for outbound, Storage Bus for inbound).
         */
        public ItemStack getRemoteIcon() {
            return remoteIcon;
        }

        /**
         * Filter configuration (for Storage Bus connections only).
         */
        public List<ItemStack> getFilter() {
            return filter;
        }

        /**
         * Check if this connection has a non-empty filter configured.
         */
        public boolean hasFilter() {
            for (ItemStack stack : filter) {
                if (!stack.isEmpty()) return true;
            }

            return false;
        }
    }

    private final long id;                    // Unique identifier for this subnet (based on grid hash or primary position)
    private final int dimension;
    private final BlockPos primaryPos;        // Primary position for sorting/highlighting (first interface position)
    private final String defaultName;         // Auto-generated name (e.g., "Subnet @ X, Y, Z")
    private String customName;                // User-defined name
    private boolean isFavorite;
    private final boolean hasSecurity;        // Whether the subnet has a security station
    private final boolean isAccessible;       // Whether current player can access the subnet
    private final boolean hasPower;           // Whether the subnet has power

    // All connection points between main network and this subnet
    private final List<ConnectionPoint> connections = new ArrayList<>();

    // Whether this represents the main network (ID = 0)
    private final boolean isMainNetwork;

    /**
     * Create a SubnetInfo representing the main network.
     * This is always displayed at the top of the subnet list.
     */
    public static SubnetInfo createMainNetwork() {
        return new SubnetInfo(0, BlockPos.ORIGIN, I18n.format("cellterminal.subnet.main_network"), true, true, true, true);
    }

    /**
     * Private constructor for factory methods.
     */
    private SubnetInfo(long id, BlockPos primaryPos, String defaultName, boolean isFavorite,
                       boolean hasSecurity, boolean isAccessible, boolean hasPower) {
        this.id = id;
        this.dimension = 0;
        this.primaryPos = primaryPos;
        this.defaultName = defaultName;
        this.customName = null;
        this.isFavorite = isFavorite;
        this.hasSecurity = hasSecurity;
        this.isAccessible = isAccessible;
        this.hasPower = hasPower;
        this.isMainNetwork = (id == 0);
    }

    public SubnetInfo(NBTTagCompound nbt) {
        this.id = nbt.getLong("id");
        this.dimension = nbt.getInteger("dim");
        this.primaryPos = BlockPos.fromLong(nbt.getLong("primaryPos"));

        // Generate localized default name from position coordinates
        int posX = nbt.hasKey("posX") ? nbt.getInteger("posX") : primaryPos.getX();
        int posY = nbt.hasKey("posY") ? nbt.getInteger("posY") : primaryPos.getY();
        int posZ = nbt.hasKey("posZ") ? nbt.getInteger("posZ") : primaryPos.getZ();
        this.defaultName = I18n.format("gui.cellterminal.subnet.default_name", posX, posY, posZ);

        this.customName = nbt.hasKey("customName") ? nbt.getString("customName") : null;
        this.isFavorite = nbt.getBoolean("favorite");
        this.hasSecurity = nbt.getBoolean("hasSecurity");
        this.isAccessible = nbt.getBoolean("accessible");
        this.hasPower = nbt.getBoolean("hasPower");
        this.isMainNetwork = false;

        // Load connection points
        if (nbt.hasKey("connections")) {
            NBTTagList connList = nbt.getTagList("connections", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < connList.tagCount(); i++) {
                connections.add(new ConnectionPoint(connList.getCompoundTagAt(i)));
            }
        }
    }

    public long getId() {
        return id;
    }

    /**
     * Check if this represents the main network.
     */
    public boolean isMainNetwork() {
        return isMainNetwork;
    }

    public int getDimension() {
        return dimension;
    }

    /**
     * Get the primary position for this subnet (first interface position).
     * Used for sorting and highlighting.
     */
    public BlockPos getPrimaryPos() {
        return primaryPos;
    }

    /**
     * Get the display name for this subnet.
     * Returns custom name if set, otherwise the default name.
     */
    public String getDisplayName() {
        if (customName != null && !customName.isEmpty()) return customName;

        return defaultName;
    }

    public String getDefaultName() {
        return defaultName;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String name) {
        this.customName = name;
    }

    public boolean hasCustomName() {
        return customName != null && !customName.isEmpty();
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        this.isFavorite = favorite;
    }

    /**
     * Whether the subnet has a security station.
     */
    public boolean hasSecurity() {
        return hasSecurity;
    }

    /**
     * Whether the current player can access this subnet.
     * If hasSecurity is true but isAccessible is false, the player
     * lacks permission to view this subnet's contents.
     */
    public boolean isAccessible() {
        return isAccessible;
    }

    /**
     * Whether the subnet has power.
     */
    public boolean hasPower() {
        return hasPower;
    }

    /**
     * Get all connection points between main network and this subnet.
     */
    public List<ConnectionPoint> getConnections() {
        return connections;
    }

    /**
     * Get the number of outbound connections (Storage Bus on main → Interface on subnet).
     */
    public int getOutboundCount() {
        int count = 0;
        for (ConnectionPoint cp : connections) {
            if (cp.isOutbound()) count++;
        }

        return count;
    }

    /**
     * Get the number of inbound connections (Interface on main ← Storage Bus on subnet).
     */
    public int getInboundCount() {
        int count = 0;
        for (ConnectionPoint cp : connections) {
            if (!cp.isOutbound()) count++;
        }

        return count;
    }

    /**
     * Get all unique filter items across all connections.
     * Returns at most maxItems items for display.
     */
    public List<ItemStack> getAllFilterItems(int maxItems) {
        List<ItemStack> items = new ArrayList<>();

        for (ConnectionPoint cp : connections) {
            for (ItemStack stack : cp.getFilter()) {
                if (stack.isEmpty()) continue;
                if (items.size() >= maxItems) return items;

                // Check if already have this item type
                boolean found = false;
                for (ItemStack existing : items) {
                    if (ItemStack.areItemsEqual(existing, stack) && ItemStack.areItemStackTagsEqual(existing, stack)) {
                        found = true;
                        break;
                    }
                }

                if (!found) items.add(stack.copy());
            }
        }

        return items;
    }

    /**
     * Check if any connection has a filter configured.
     */
    public boolean hasAnyFilter() {
        for (ConnectionPoint cp : connections) {
            if (cp.hasFilter()) return true;
        }

        return false;
    }

    /**
     * Create NBT data for this subnet info (for saving custom name and favorite).
     */
    public NBTTagCompound writeActionNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong("id", id);
        if (customName != null) nbt.setString("customName", customName);
        nbt.setBoolean("favorite", isFavorite);

        return nbt;
    }

    /**
     * Build a list of connection rows for displaying filters under this subnet's header.
     * Each connection with filters gets one or more rows (9 items per row).
     * Connections without filters still get one row to show the connection info.
     *
     * @param maxFilterItemsPerRow Maximum filter items shown per row (typically 9)
     * @return List of connection rows for this subnet
     */
    public List<SubnetConnectionRow> buildConnectionRows(int maxFilterItemsPerRow) {
        List<SubnetConnectionRow> rows = new ArrayList<>();

        for (int connIdx = 0; connIdx < connections.size(); connIdx++) {
            ConnectionPoint conn = connections.get(connIdx);
            int filterCount = conn.getFilter().size();

            if (filterCount == 0) {
                // Connection with no filter - show one row with connection info only
                rows.add(new SubnetConnectionRow(this, conn, connIdx, 0, true));
            } else {
                // Connection with filters - create rows for each batch of items
                for (int startIdx = 0; startIdx < filterCount; startIdx += maxFilterItemsPerRow) {
                    boolean isFirst = (startIdx == 0);
                    rows.add(new SubnetConnectionRow(this, conn, connIdx, startIdx, isFirst));
                }
            }
        }

        return rows;
    }
}
