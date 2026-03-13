package com.cellterminal.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import appeng.api.implementations.items.IUpgradeModule;

import com.cellterminal.gui.rename.Renameable;
import com.cellterminal.gui.rename.RenameTargetType;


/**
 * Client-side data holder for cell information received from server.
 */
public class CellInfo implements Renameable {

    private long parentStorageId;
    private final int slot;
    private final int status;
    private final boolean isItem;
    private final boolean isFluid;
    private final boolean isEssentia;
    private final ItemStack cellItem;
    private final long usedBytes;
    private final long totalBytes;
    private final long usedTypes;
    private final long totalTypes;
    private final long storedItemCount;
    private final List<ItemStack> partition = new ArrayList<>();
    private final List<ItemStack> contents = new ArrayList<>();
    private final List<Long> contentCounts = new ArrayList<>();

    // Upgrade tracking
    private final List<ItemStack> upgrades = new ArrayList<>();
    private final List<Integer> upgradeSlotIndices = new ArrayList<>();
    private final int upgradeSlotCount;

    public CellInfo(NBTTagCompound nbt) {
        this.slot = nbt.getInteger("slot");
        this.status = nbt.getInteger("status");
        this.isFluid = nbt.getBoolean("isFluid");
        this.isEssentia = nbt.getBoolean("isEssentia");
        // Prefer explicit isItem flag; fallback to legacy inference if missing
        this.isItem = nbt.hasKey("isItem") ? nbt.getBoolean("isItem") : (!this.isFluid && !this.isEssentia);
        this.cellItem = nbt.hasKey("cellItem") ? new ItemStack(nbt.getCompoundTag("cellItem")) : ItemStack.EMPTY;
        this.usedBytes = nbt.getLong("usedBytes");
        this.totalBytes = nbt.getLong("totalBytes");
        this.usedTypes = nbt.getLong("usedTypes");
        this.totalTypes = nbt.getLong("totalTypes");
        this.storedItemCount = nbt.getLong("storedItemCount");

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

        // Upgrade slot count from server; fallback to 2 when not provided
        this.upgradeSlotCount = nbt.hasKey("upgradeSlotCount") ? nbt.getInteger("upgradeSlotCount") : 2;

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
            for (int i = 0; i < maxSlot; i++) {
                this.partition.add(ItemStack.EMPTY);
            }

            // Place items at their correct slot positions
            for (int i = 0; i < partList.tagCount(); i++) {
                NBTTagCompound partNbt = partList.getCompoundTagAt(i);
                int slot = partNbt.hasKey("slot") ? partNbt.getInteger("slot") : i;

                // Check if item data is present (id key indicates an item)
                if (partNbt.hasKey("id")) {
                    this.partition.set(slot, new ItemStack(partNbt));
                }
            }
        }

        if (nbt.hasKey("contents")) {
            NBTTagList contentList = nbt.getTagList("contents", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < contentList.tagCount(); i++) {
                NBTTagCompound stackNbt = contentList.getCompoundTagAt(i);
                ItemStack stack = new ItemStack(stackNbt);

                // Read the actual count from AE2's "Cnt" key, or fluidAmount/essentiaAmount for special cells
                long count;
                if (stackNbt.hasKey("fluidAmount")) {
                    // For fluid stacks, count is stored in mB
                    count = stackNbt.getLong("fluidAmount");
                } else if (stackNbt.hasKey("essentiaAmount")) {
                    // For essentia stacks, count is stored by our integration
                    count = stackNbt.getLong("essentiaAmount");
                } else if (stackNbt.hasKey("Cnt")) {
                    // For item stacks, AE2 stores count as "Cnt"
                    count = stackNbt.getLong("Cnt");
                } else {
                    // Fallback to ItemStack count
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

    public int getSlot() {
        return slot;
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

    public ItemStack getCellItem() {
        return cellItem;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public long getTotalTypes() {
        return totalTypes;
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

    public float getByteUsagePercent() {
        if (totalBytes == 0) return 0;

        return (float) usedBytes / totalBytes;
    }

    public String getDisplayName() {
        if (!cellItem.isEmpty()) return cellItem.getDisplayName();

        return I18n.format("gui.cellterminal.cell_empty");
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

    public int getUpgradeSlotCount() {
        return upgradeSlotCount;
    }

    public boolean hasUpgradeSpace() {
        return upgrades.size() < getUpgradeSlotCount();
    }

    /**
     * Check if this cell can potentially accept the given upgrade item.
     * This is a client-side heuristic only - actual validation happens server-side.
     * Checks if item is an upgrade module and if there's space in the upgrade inventory.
     * @param upgradeStack The upgrade item to check
     * @return true if the upgrade might be insertable
     */
    public boolean canAcceptUpgrade(ItemStack upgradeStack) {
        if (upgradeStack.isEmpty()) return false;
        if (!(upgradeStack.getItem() instanceof IUpgradeModule)) return false;

        // Distinguish real upgrades from storage components that also implement IUpgradeModule
        // Real upgrades (speed card, capacity card, etc.) return a non-null Upgrades type
        if (((IUpgradeModule) upgradeStack.getItem()).getType(upgradeStack) == null) return false;

        return hasUpgradeSpace();
    }

    // ========================================
    // Renameable implementation
    // ========================================

    @Override
    public boolean isRenameable() {
        return !cellItem.isEmpty();
    }

    @Override
    public String getCustomName() {
        if (cellItem.isEmpty()) return null;
        if (cellItem.hasDisplayName()) return cellItem.getDisplayName();

        return null;
    }

    @Override
    public boolean hasCustomName() {
        return !cellItem.isEmpty() && cellItem.hasDisplayName();
    }

    @Override
    public void setCustomName(String name) {
        // Client-side optimistic update for the ItemStack display name
        if (cellItem.isEmpty()) return;

        if (name == null || name.isEmpty()) {
            cellItem.clearCustomName();
        } else {
            cellItem.setStackDisplayName(name);
        }
    }

    @Override
    public RenameTargetType getRenameTargetType() {
        return RenameTargetType.CELL;
    }

    @Override
    public long getRenameId() {
        return parentStorageId;
    }

    @Override
    public int getRenameSecondaryId() {
        return slot;
    }
}
