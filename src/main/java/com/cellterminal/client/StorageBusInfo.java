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

import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;

import com.cellterminal.gui.rename.Renameable;
import com.cellterminal.gui.rename.RenameTargetType;


/**
 * Client-side data holder for storage bus information received from server.
 * Similar to CellInfo but for storage buses which connect to external inventories.
 */
public class StorageBusInfo implements Renameable {

    /**
     * Base number of config slots without any capacity upgrades.
     */
    public static final int BASE_CONFIG_SLOTS = 18;

    /**
     * Additional config slots per capacity upgrade.
     */
    public static final int SLOTS_PER_CAPACITY_UPGRADE = 9;

    /**
     * Maximum number of config slots (with 5 capacity upgrades).
     */
    public static final int MAX_CONFIG_SLOTS = 63;

    /**
     * Maximum capacity upgrades a storage bus can have.
     */
    public static final int MAX_CAPACITY_UPGRADES = 5;

    /**
     * Calculate the number of available config slots for a given capacity upgrade count.
     * FIXME: This should be provided by the scanner implementation instead of being hardcoded here.
     */
    public static int calculateAvailableSlots(int capacityUpgrades) {
        return Math.min(BASE_CONFIG_SLOTS + SLOTS_PER_CAPACITY_UPGRADE * capacityUpgrades, MAX_CONFIG_SLOTS);
    }

    private long parentStorageId;
    private final long id;
    private final BlockPos pos;
    private final int dimension;
    private final EnumFacing side;
    private final int priority;
    private final int baseConfigSlots;
    private final int slotsPerUpgrade;
    private final int maxConfigSlots;
    private final boolean isItem;      // True for item storage buses
    private final boolean isFluid;     // True for fluid storage buses
    private final boolean isEssentia;  // True for Thaumic Energistics essentia storage buses
    private final int accessRestriction;  // 0=NO_ACCESS, 1=READ, 2=WRITE, 3=READ_WRITE
    private final String customName;      // Storage bus custom name (takes priority over connectedName)
    private final String connectedName;
    private final ItemStack connectedIcon;
    private final List<ItemStack> partition = new ArrayList<>();
    private final List<ItemStack> contents = new ArrayList<>();
    private final List<Long> contentCounts = new ArrayList<>();
    private final List<ItemStack> upgrades = new ArrayList<>();
    private final List<Integer> upgradeSlotIndices = new ArrayList<>();
    private boolean expanded = true;
    private final boolean supportsPriorityFlag;
    private final boolean supportsIOModeFlag;

    public StorageBusInfo(NBTTagCompound nbt) {
        this.id = nbt.getLong("id");
        this.pos = BlockPos.fromLong(nbt.getLong("pos"));
        this.dimension = nbt.getInteger("dim");
        this.side = EnumFacing.byIndex(nbt.getInteger("side"));
        this.priority = nbt.getInteger("priority");
        this.isFluid = nbt.getBoolean("isFluid");
        this.isEssentia = nbt.getBoolean("isEssentia");
        // Prefer explicit isItem flag; fallback to legacy inference if missing
        this.isItem = nbt.hasKey("isItem") ? nbt.getBoolean("isItem") : (!this.isFluid && !this.isEssentia);
        this.accessRestriction = nbt.hasKey("access") ? nbt.getInteger("access") : 3;  // Default READ_WRITE

        // Per-implementation slot parameters (optional; default to AE2 values)
        this.baseConfigSlots = nbt.hasKey("baseConfigSlots") ? nbt.getInteger("baseConfigSlots") : BASE_CONFIG_SLOTS;
        this.slotsPerUpgrade = nbt.hasKey("slotsPerUpgrade") ? nbt.getInteger("slotsPerUpgrade") : SLOTS_PER_CAPACITY_UPGRADE;
        this.maxConfigSlots = nbt.hasKey("maxConfigSlots") ? nbt.getInteger("maxConfigSlots") : MAX_CONFIG_SLOTS;

        // Capability flags provided by scanners
        this.supportsPriorityFlag = nbt.getBoolean("supportsPriority");
        this.supportsIOModeFlag = nbt.getBoolean("supportsIOMode");

        // Storage bus custom name (takes priority over connected block name)
        this.customName = nbt.hasKey("customName") ? nbt.getString("customName") : null;

        // Connected inventory info
        this.connectedName = nbt.hasKey("connectedName") ? nbt.getString("connectedName") : null;
        this.connectedIcon = nbt.hasKey("connectedIcon") ? new ItemStack(nbt.getCompoundTag("connectedIcon")) : ItemStack.EMPTY;

        // Parse upgrade items for display
        if (nbt.hasKey("upgrades")) {
            NBTTagList upgradeList = nbt.getTagList("upgrades", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < upgradeList.tagCount(); i++) {
                NBTTagCompound upgradeNbt = upgradeList.getCompoundTagAt(i);
                ItemStack upgrade = new ItemStack(upgradeNbt);
                if (!upgrade.isEmpty()) {
                    this.upgrades.add(upgrade);
                    // Read actual slot index, fallback to iteration index for backwards compatibility
                    int slotIndex = upgradeNbt.hasKey("slot") ? upgradeNbt.getInteger("slot") : i;
                    this.upgradeSlotIndices.add(slotIndex);
                }
            }
        }

        // Parse partition (config inventory)
        if (nbt.hasKey("partition")) {
            NBTTagList partList = nbt.getTagList("partition", Constants.NBT.TAG_COMPOUND);

            // Determine max slot to size the list properly
            int maxSlot = 0;
            for (int i = 0; i < partList.tagCount(); i++) {
                NBTTagCompound partNbt = partList.getCompoundTagAt(i);
                int slot = partNbt.hasKey("slot") ? partNbt.getInteger("slot") : i;
                if (slot >= maxSlot) maxSlot = slot + 1;
            }

            // Pre-fill partition list with empty stacks
            for (int i = 0; i < maxSlot; i++) this.partition.add(ItemStack.EMPTY);

            // Place items at their correct slot positions
            for (int i = 0; i < partList.tagCount(); i++) {
                NBTTagCompound partNbt = partList.getCompoundTagAt(i);
                int slot = partNbt.hasKey("slot") ? partNbt.getInteger("slot") : i;

                // Check if item data is present (id key indicates an item)
                if (partNbt.hasKey("id")) this.partition.set(slot, new ItemStack(partNbt));
            }
        }

        // Parse contents (inventory preview)
        if (nbt.hasKey("contents")) {
            NBTTagList contentList = nbt.getTagList("contents", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < contentList.tagCount(); i++) {
                NBTTagCompound stackNbt = contentList.getCompoundTagAt(i);
                ItemStack stack = new ItemStack(stackNbt);

                long count;
                if (stackNbt.hasKey("Cnt")) {
                    count = stackNbt.getLong("Cnt");
                } else {
                    count = stack.getCount();
                }

                this.contents.add(stack);
                this.contentCounts.add(count);
            }
        }
    }

    public void setParentStorageId(long parentStorageId) {
        this.parentStorageId = parentStorageId;
    }

    public long getParentStorageId() {
        return parentStorageId;
    }

    public long getId() {
        return id;
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getDimension() {
        return dimension;
    }

    public EnumFacing getSide() {
        return side;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * Get the number of available config slots based on capacity upgrades.
     * Formula: 18 + 9 * capacityUpgrades, capped at 63.
     * Essentia buses always have 63 slots (they don't use capacity upgrades).
     */
    public int getAvailableConfigSlots() {
        int raw = baseConfigSlots + slotsPerUpgrade * Math.max(0, getInstalledUpgrades(Upgrades.CAPACITY));

        return raw > maxConfigSlots ? maxConfigSlots : raw;
    }

    public boolean hasInverter() {
        return getInstalledUpgrades(Upgrades.INVERTER) > 0;
    }

    public boolean hasSticky() {
        return getInstalledUpgrades(Upgrades.STICKY) > 0;
    }

    public boolean hasFuzzy() {
        return getInstalledUpgrades(Upgrades.FUZZY) > 0;
    }

    public boolean isFluid() {
        return isFluid;
    }

    public boolean isEssentia() {
        return isEssentia;
    }

    public boolean isItem() {
        return isItem;
    }

    /**
     * Check if this storage bus supports IO mode toggling.
     */
    public boolean supportsIOMode() {
        return supportsIOModeFlag;
    }

    /**
     * Check if this storage bus supports priority editing.
     */
    public boolean supportsPriority() {
        return supportsPriorityFlag;
    }

    /**
     * Get the access restriction mode.
     * @return 0=NO_ACCESS, 1=READ, 2=WRITE, 3=READ_WRITE
     */
    public int getAccessRestriction() {
        return accessRestriction;
    }

    /**
     * Get the localized display name for the current IO mode (for tooltips).
     */
    public String getIOModeDisplayName() {
        switch (accessRestriction) {
            case 0: return I18n.format("gui.cellterminal.storagebus.iomode.none");
            case 1: return I18n.format("gui.cellterminal.storagebus.iomode.read");
            case 2: return I18n.format("gui.cellterminal.storagebus.iomode.write");
            case 3: return I18n.format("gui.cellterminal.storagebus.iomode.readwrite");
            default: return "?";
        }
    }

    public List<ItemStack> getPartition() {
        return partition;
    }

    public List<ItemStack> getContents() {
        return contents;
    }

    public long getContentCount(int index) {
        if (index < 0 || index >= contentCounts.size()) return 0;

        return contentCounts.get(index);
    }

    public List<ItemStack> getUpgrades() {
        return upgrades;
    }

    /**
     * Get the actual slot index for an upgrade in the upgrade inventory.
     * @param index The index in the upgrades list (0 to upgrades.size()-1)
     * @return The actual slot index in the upgrade inventory
     */
    public int getUpgradeSlotIndex(int index) {
        if (index < 0 || index >= upgradeSlotIndices.size()) return index;

        return upgradeSlotIndices.get(index);
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public void toggleExpanded() {
        this.expanded = !this.expanded;
    }

    public String getDisplayName() {
        return I18n.format("gui.cellterminal.storage_bus.name");
    }

    /**
     * Get localized name for display.
     * Priority: custom name > connected inventory name > "Air".
     */
    public String getLocalizedName() {
        // Custom name takes highest priority
        if (customName != null && !customName.isEmpty()) return customName;

        // Fall back to connected inventory name
        if (connectedName != null && !connectedName.isEmpty()) return connectedName;

        return I18n.format("gui.cellterminal.storage_bus.air");
    }

    /**
     * Check if this storage bus has a connected inventory.
     */
    public boolean hasConnectedInventory() {
        return connectedName != null && !connectedName.isEmpty();
    }

    /**
     * Get the icon of the connected inventory.
     */
    public ItemStack getConnectedInventoryIcon() {
        return connectedIcon;
    }

    public String getLocationString() {
        return I18n.format("gui.cellterminal.location_format",
            pos.getX(), pos.getY(), pos.getZ(), dimension);
    }

    /**
     * Check if this storage bus has any partition configured.
     */
    public boolean hasPartition() {
        for (ItemStack stack : partition) {
            if (!stack.isEmpty()) return true;
        }

        return false;
    }

    /**
     * Get count of non-empty partition slots.
     */
    public int getPartitionCount() {
        int count = 0;
        for (ItemStack stack : partition) {
            if (!stack.isEmpty()) count++;
        }

        return count;
    }

    /**
     * Get count of unique items in the connected inventory.
     */
    public int getContentTypeCount() {
        return contents.size();
    }

    /**
     * Get total item count in the connected inventory.
     */
    public long getTotalItemCount() {
        long total = 0;
        for (Long count : contentCounts) total += count;

        return total;
    }

    /**
     * Check if this storage bus has space for more upgrades.
     * Storage buses have 5 upgrade slots.
     */
    public boolean hasUpgradeSpace() {
        return upgrades.size() < 5;
    }

    /**
     * Get the current installed count of a specific upgrade type.
     * @param upgradeType The upgrade type to count
     * @return The number currently installed
     */
    public int getInstalledUpgrades(Upgrades upgradeType) {
        if (upgradeType == null) return 0;

        int count = 0;
        for (ItemStack upgrade : upgrades) {
            if (upgrade.getItem() instanceof IUpgradeModule) {
                Upgrades type = ((IUpgradeModule) upgrade.getItem()).getType(upgrade);
                if (type == upgradeType) count++;
            }
        }

        return count;
    }

    /**
     * Check if this storage bus can potentially accept the given upgrade item.
     * This is a client-side heuristic only - actual validation happens server-side.
     * Checks if item is an upgrade module and if there's space in the upgrade inventory.
     * @param upgradeStack The upgrade item to check
     * @return true if the upgrade might be insertable
     */
    public boolean canAcceptUpgrade(ItemStack upgradeStack) {
        if (upgradeStack.isEmpty()) return false;
        if (!(upgradeStack.getItem() instanceof IUpgradeModule)) return false;

        return hasUpgradeSpace();
    }

    // ========================================
    // Renameable implementation
    // ========================================

    @Override
    public boolean isRenameable() {
        return true;
    }

    @Override
    public String getCustomName() {
        return customName;
    }

    @Override
    public boolean hasCustomName() {
        return customName != null && !customName.isEmpty();
    }

    @Override
    public void setCustomName(String name) {
        // Client-side optimistic update not supported; server sends refresh.
    }

    @Override
    public RenameTargetType getRenameTargetType() {
        return RenameTargetType.STORAGE_BUS;
    }

    @Override
    public long getRenameId() {
        return id;
    }
}
