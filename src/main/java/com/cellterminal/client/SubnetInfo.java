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

import com.cellterminal.gui.rename.Renameable;
import com.cellterminal.gui.rename.RenameTargetType;


/**
 * Client-side data holder for subnet connection information received from server.
 * <p>
 * <b>NBT Data Map</b> (written by {@link com.cellterminal.integration.subnet.AbstractSubnetScanner}):
 * <pre>
 * SubnetInfo                            Size (bytes)
 * ─────────────────────────────────────────────────
 * "id"              long                  8
 * "dim"             int                   4
 * "primaryPos"      long (BlockPos)       8
 * "posX"            int                   4   (for client-side name generation)
 * "posY"            int                   4   (")
 * "posZ"            int                   4   (")
 * "customName"      String                ~N  (optional; user-set subnet name)
 * "favorite"        boolean               1   (optional; only if true)
 * "hasSecurity"     boolean               1
 * "accessible"      boolean               1
 * "hasPower"        boolean               1
 * "inventory"       NBTTagList            S_inv * T_inv  (subnet ME storage contents)
 *   └─ each entry: ItemStack NBT + "Cnt" long
 * "connections"     NBTTagList            S_conn * C  (C = connection count)
 *   └─ each ConnectionPoint entry:
 *        "pos"          long (BlockPos)       8
 *        "dim"          int                   4
 *        "side"         int (EnumFacing)      4
 *        "outbound"     boolean               1
 *        "localIcon"    NBTTagCompound        ~I  (optional; block icon on main net)
 *        "remoteIcon"   NBTTagCompound        ~I  (optional; block icon on subnet)
 *        "content"      NBTTagList            ~S  (optional; flowing items)
 *        "filter"       NBTTagList            ~P  (optional; partition/filter items)
 *        "maxPartitionSlots" int              4   (optional; default 63)
 * ─────────────────────────────────────────────────
 * Total ≈ 36 + N + S_inv * T_inv + S_conn * C
 *   where S_inv  = average size of one inventory entry (~50-200 bytes),
 *         T_inv  = number of unique item types in subnet ME storage,
 *         S_conn = average size of one connection entry (~50-300 bytes),
 *         C      = number of connection points to this subnet,
 *         N      = custom name length (0 if absent)
 * </pre>
 * <p>
 * A subnet is a SEPARATE ME grid that connects to the main network through the
 * ME Passthrough mechanism (IStorageMonitorableAccessor capability). Each connection
 * is one-way:
 * <p>
 * - Outbound: Storage Bus on main network → Interface on subnet
 *   (Main network reads from / writes to subnet's storage)
 * <p>
 * - Inbound: Interface on main network ← Storage Bus on subnet
 *   (Subnet reads from / writes to main network's storage)
 * <p>
 * Both directions can exist simultaneously for bidirectional item flow.
 * Multiple connections to the same subnet are grouped together and sorted by position.
 * <p>
 * Note: A subnet does NOT require a controller - any cable segment with up to 8 channels
 * can function as a subnet if powered via Energy Acceptor or Quartz Fiber.
 * <p>
 * Note: P2P Tunnels do NOT create subnets - they teleport channels within the same grid.
 */
public class SubnetInfo implements Renameable {

    /**
     * Represents a single connection point between main network and subnet.
     * Multiple connections can exist to the same subnet.
     */
    public static class ConnectionPoint {

        private final BlockPos pos;           // Position on main network
        private final int dimension;          // Dimension of the connection (can differ from subnet primary dim via Quantum Bridge)
        private final EnumFacing side;        // Side of the part
        private final boolean isOutbound;     // true = Storage Bus on main, false = Interface on main
        private final ItemStack localIcon;    // Icon of the block on main network
        private final ItemStack remoteIcon;   // Icon of the block on subnet

        // Content items (items flowing through the connection). Empty list if no content key in data.
        private final List<ItemStack> content;
        private final boolean hasContentKey;  // Whether the backend sent a "content" key at all

        // Partition items (storage bus filter config). Empty list if no partition key in data.
        private final List<ItemStack> partition;
        private final boolean hasPartitionKey; // Whether the backend sent a "filter" key at all
        private final int maxPartitionSlots;   // Maximum number of partition slots (e.g. 63 for storage bus)

        public ConnectionPoint(NBTTagCompound nbt) {
            this.pos = BlockPos.fromLong(nbt.getLong("pos"));
            this.dimension = nbt.getInteger("dim");
            this.side = EnumFacing.byIndex(nbt.getInteger("side"));
            this.isOutbound = nbt.getBoolean("outbound");
            this.localIcon = nbt.hasKey("localIcon") ? new ItemStack(nbt.getCompoundTag("localIcon")) : ItemStack.EMPTY;
            this.remoteIcon = nbt.hasKey("remoteIcon") ? new ItemStack(nbt.getCompoundTag("remoteIcon")) : ItemStack.EMPTY;

            // Content items (future: backend will send "content" key with subnet inventory)
            this.hasContentKey = nbt.hasKey("content");
            this.content = new ArrayList<>();
            if (hasContentKey) {
                NBTTagList contentList = nbt.getTagList("content", Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < contentList.tagCount(); i++) {
                    content.add(new ItemStack(contentList.getCompoundTagAt(i)));
                }
            }

            // Partition items (storage bus filter config, with empty slots preserved for position-aware editing)
            this.hasPartitionKey = nbt.hasKey("filter");
            this.partition = new ArrayList<>();
            if (hasPartitionKey) {
                NBTTagList filterList = nbt.getTagList("filter", Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < filterList.tagCount(); i++) {
                    partition.add(new ItemStack(filterList.getCompoundTagAt(i)));
                }
            }
            this.maxPartitionSlots = nbt.hasKey("maxPartitionSlots") ? nbt.getInteger("maxPartitionSlots") : 63;
        }

        public BlockPos getPos() {
            return pos;
        }

        public EnumFacing getSide() {
            return side;
        }

        /**
         * Dimension of this connection point.
         * May differ from the subnet's primary dimension when Quantum Bridges are involved.
         */
        public int getDimension() {
            return dimension;
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
         * Content items flowing through this connection.
         * Empty if backend doesn't send content data yet.
         */
        public List<ItemStack> getContent() {
            return content;
        }

        /**
         * Whether the backend sent a "content" key at all.
         * When true, content rows should be shown (even if the list is empty).
         */
        public boolean hasContentKey() {
            return hasContentKey;
        }

        /**
         * Partition items (storage bus filter configuration).
         * Includes empty slots to preserve slot positions for editing.
         */
        public List<ItemStack> getPartition() {
            return partition;
        }

        /**
         * Whether the backend sent a "filter" key at all.
         * When true, partition rows should be shown (even if the list is empty).
         */
        public boolean hasPartitionKey() {
            return hasPartitionKey;
        }

        /**
         * Maximum number of partition slots (e.g. 63 for an AE2 storage bus).
         */
        public int getMaxPartitionSlots() {
            return maxPartitionSlots;
        }

        /**
         * Check if this connection has a non-empty filter/partition configured.
         */
        public boolean hasFilter() {
            for (ItemStack stack : partition) {
                if (!stack.isEmpty()) return true;
            }

            return false;
        }
    }

    private final long id;                    // Unique identifier for this subnet (based on grid hash or primary position)
    private final int dimension;              // Primary dimension (from primary node, used for sorting)
    private final BlockPos primaryPos;        // Primary position for sorting/highlighting (first interface position)
    private final String defaultName;         // Auto-generated name (e.g., "Subnet @ X, Y, Z")
    private String customName;                // User-defined name
    private boolean isFavorite;
    private final boolean hasSecurity;        // Whether the subnet has a security station
    private final boolean isAccessible;       // Whether current player can access the subnet
    private final boolean hasPower;           // Whether the subnet has power

    // All connection points between main network and this subnet
    private final List<ConnectionPoint> connections = new ArrayList<>();

    // Subnet inventory: all items/fluids stored in the subnet's ME storage
    private final List<ItemStack> inventory = new ArrayList<>();
    private final List<Long> inventoryCounts = new ArrayList<>();

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

        // Load subnet inventory (items/fluids in the subnet's ME storage)
        if (nbt.hasKey("inventory")) {
            NBTTagList invList = nbt.getTagList("inventory", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < invList.tagCount(); i++) {
                NBTTagCompound stackNbt = invList.getCompoundTagAt(i);
                ItemStack stack = new ItemStack(stackNbt);
                long count = stackNbt.hasKey("Cnt") ? stackNbt.getLong("Cnt") : stack.getCount();
                inventory.add(stack);
                inventoryCounts.add(count);
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
     * Get the subnet's inventory items (all items/fluids stored in the subnet's ME storage).
     */
    public List<ItemStack> getInventory() {
        return inventory;
    }

    /**
     * Get the count for an inventory item at the given index.
     */
    public long getInventoryCount(int index) {
        if (index < 0 || index >= inventoryCounts.size()) return 0;

        return inventoryCounts.get(index);
    }

    /**
     * Whether this subnet has any inventory data.
     */
    public boolean hasInventory() {
        return !inventory.isEmpty();
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
            for (ItemStack stack : cp.getPartition()) {
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
     * Build content and partition rows for a single connection, following the same layout
     * as Temp Area: content rows first, then partition rows.
     * <p>
     * Content rows are only emitted if the backend sent a "content" key (even if empty).
     * Partition rows are only emitted if the backend sent a "filter" key (even if empty).
     * When present, at least one row is always shown (even for empty data).
     * <p>
     * Partition display stops at the last non-empty slot, but adds one more row when
     * the last column is occupied (so the user can add more items).
     *
     * @param slotsPerRow Number of slots per row (typically 9)
     * @return List of connection rows for this connection
     */
    public static List<SubnetConnectionRow> buildConnectionContentRows(
            SubnetInfo subnet, ConnectionPoint conn, int connIdx, int slotsPerRow) {
        List<SubnetConnectionRow> rows = new ArrayList<>();

        // For outbound connections (Storage Bus on main → Interface on subnet),
        // show the subnet's entire ME storage as "content" so the user can see
        // what's available and use "Partition All" to set filters.
        if (conn.isOutbound() && subnet.hasInventory()) {
            int contentCount = subnet.getInventory().size();
            int contentRows = Math.max(1, (contentCount + slotsPerRow - 1) / slotsPerRow);
            for (int row = 0; row < contentRows; row++) {
                rows.add(new SubnetConnectionRow(subnet, conn, connIdx,
                    row * slotsPerRow, row == 0, false, true));
            }

        // For inbound connections, use per-connection content (if backend sends it)
        } else if (conn.hasContentKey()) {
            int contentCount = conn.getContent().size();
            int contentRows = Math.max(1, (contentCount + slotsPerRow - 1) / slotsPerRow);
            for (int row = 0; row < contentRows; row++) {
                rows.add(new SubnetConnectionRow(subnet, conn, connIdx,
                    row * slotsPerRow, row == 0, false, false));
            }
        }

        // Partition rows (storage bus filter config)
        if (conn.hasPartitionKey()) {
            int highestSlot = getHighestNonEmptySlot(conn.getPartition());
            int partitionRows = Math.max(1, (highestSlot + slotsPerRow) / slotsPerRow);

            // If the last visible column is occupied and there's room for more,
            // add one more row so the user can expand
            if (highestSlot >= 0 && (highestSlot + 1) % slotsPerRow == 0
                    && (highestSlot + 1) < conn.getMaxPartitionSlots()) {
                partitionRows++;
            }

            for (int row = 0; row < partitionRows; row++) {
                rows.add(new SubnetConnectionRow(subnet, conn, connIdx,
                    row * slotsPerRow, row == 0, true, false));
            }
        }

        return rows;
    }

    /**
     * Find the highest non-empty slot index in a list, or -1 if all empty.
     */
    private static int getHighestNonEmptySlot(List<ItemStack> items) {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (!items.get(i).isEmpty()) return i;
        }

        return -1;
    }

    // ---- Renameable implementation ----

    @Override
    public boolean isRenameable() {
        return !isMainNetwork;
    }

    @Override
    public RenameTargetType getRenameTargetType() {
        return RenameTargetType.SUBNET;
    }

    @Override
    public long getRenameId() {
        return id;
    }
}
