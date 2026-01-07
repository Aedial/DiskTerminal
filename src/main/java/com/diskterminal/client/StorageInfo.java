package com.diskterminal.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;


/**
 * Client-side data holder for drive/chest storage information received from server.
 */
public class StorageInfo {

    private final long id;
    private final BlockPos pos;
    private final int dimension;
    private final String name;
    private final ItemStack blockItem;
    private final List<DiskInfo> disks = new ArrayList<>();
    private boolean expanded = true;

    public StorageInfo(NBTTagCompound nbt) {
        this.id = nbt.getLong("id");
        this.pos = BlockPos.fromLong(nbt.getLong("pos"));
        this.dimension = nbt.getInteger("dim");
        this.name = nbt.getString("name");
        this.blockItem = nbt.hasKey("blockItem") ? new ItemStack(nbt.getCompoundTag("blockItem")) : ItemStack.EMPTY;

        if (nbt.hasKey("disks")) {
            NBTTagList diskList = nbt.getTagList("disks", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < diskList.tagCount(); i++) {
                DiskInfo disk = new DiskInfo(diskList.getCompoundTagAt(i));
                disk.setParentStorageId(this.id);
                this.disks.add(disk);
            }
        }
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

    public String getName() {
        return name;
    }

    public ItemStack getBlockItem() {
        return blockItem;
    }

    public List<DiskInfo> getDisks() {
        return disks;
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

    public String getLocationString() {
        return String.format("[%d, %d, %d] (DIM %d)", pos.getX(), pos.getY(), pos.getZ(), dimension);
    }

    public int getTotalDiskCount() {
        return disks.size();
    }

    public long getTotalUsedBytes() {
        long total = 0;
        for (DiskInfo disk : disks) total += disk.getUsedBytes();

        return total;
    }

    public long getTotalMaxBytes() {
        long total = 0;
        for (DiskInfo disk : disks) total += disk.getTotalBytes();

        return total;
    }
}
