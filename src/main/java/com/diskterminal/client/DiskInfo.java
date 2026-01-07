package com.diskterminal.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;


/**
 * Client-side data holder for disk information received from server.
 */
public class DiskInfo {

    private long parentStorageId;
    private final int slot;
    private final int status;
    private final ItemStack cellItem;
    private final long usedBytes;
    private final long totalBytes;
    private final long usedTypes;
    private final long totalTypes;
    private final long storedItemCount;
    private final List<ItemStack> partition = new ArrayList<>();
    private final List<ItemStack> contents = new ArrayList<>();

    public DiskInfo(NBTTagCompound nbt) {
        this.slot = nbt.getInteger("slot");
        this.status = nbt.getInteger("status");
        this.cellItem = nbt.hasKey("cellItem") ? new ItemStack(nbt.getCompoundTag("cellItem")) : ItemStack.EMPTY;
        this.usedBytes = nbt.getLong("usedBytes");
        this.totalBytes = nbt.getLong("totalBytes");
        this.usedTypes = nbt.getLong("usedTypes");
        this.totalTypes = nbt.getLong("totalTypes");
        this.storedItemCount = nbt.getLong("storedItemCount");

        if (nbt.hasKey("partition")) {
            NBTTagList partList = nbt.getTagList("partition", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < partList.tagCount(); i++) {
                this.partition.add(new ItemStack(partList.getCompoundTagAt(i)));
            }
        }

        if (nbt.hasKey("contents")) {
            NBTTagList contentList = nbt.getTagList("contents", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < contentList.tagCount(); i++) {
                NBTTagCompound stackNbt = contentList.getCompoundTagAt(i);
                ItemStack stack = new ItemStack(stackNbt);

                // For fluid stacks, the count is stored in fluidAmount (in mB)
                // Convert to bucket count for display (1000mB = 1 bucket)
                if (stackNbt.hasKey("fluidAmount")) {
                    long fluidMb = stackNbt.getLong("fluidAmount");
                    // Store as count in buckets (rounded up to show at least 1)
                    stack.setCount((int) Math.max(1, fluidMb / 1000));
                }

                this.contents.add(stack);
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

    public int getStatus() {
        return status;
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

    public long getUsedTypes() {
        return usedTypes;
    }

    public long getTotalTypes() {
        return totalTypes;
    }

    public long getStoredItemCount() {
        return storedItemCount;
    }

    public List<ItemStack> getPartition() {
        return partition;
    }

    public List<ItemStack> getContents() {
        return contents;
    }

    public float getByteUsagePercent() {
        if (totalBytes == 0) return 0;

        return (float) usedBytes / totalBytes;
    }

    public float getTypeUsagePercent() {
        if (totalTypes == 0) return 0;

        return (float) usedTypes / totalTypes;
    }

    public String getDisplayName() {
        if (!cellItem.isEmpty()) return cellItem.getDisplayName();

        return "Unknown Disk";
    }
}
