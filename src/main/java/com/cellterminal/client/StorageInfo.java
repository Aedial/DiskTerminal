package com.cellterminal.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.I18n;
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
    private final int slotCount;
    private final int priority;
    private final List<CellInfo> cells = new ArrayList<>();
    private boolean expanded = true;

    public StorageInfo(NBTTagCompound nbt) {
        this.id = nbt.getLong("id");
        this.pos = BlockPos.fromLong(nbt.getLong("pos"));
        this.dimension = nbt.getInteger("dim");
        this.name = nbt.getString("name");
        this.blockItem = nbt.hasKey("blockItem") ? new ItemStack(nbt.getCompoundTag("blockItem")) : ItemStack.EMPTY;
        this.slotCount = nbt.getInteger("slotCount");
        this.priority = nbt.getInteger("priority");

        if (nbt.hasKey("cells")) {
            NBTTagList cellList = nbt.getTagList("cells", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < cellList.tagCount(); i++) {
                CellInfo cell = new CellInfo(cellList.getCompoundTagAt(i));
                cell.setParentStorageId(this.id);
                this.cells.add(cell);
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
        // Translate lang keys (names starting with "tile." or "item.")
        if (name.startsWith("tile.") || name.startsWith("item.")) {
            return I18n.format(name);
        }

        return name;
    }

    public ItemStack getBlockItem() {
        return blockItem;
    }

    public int getSlotCount() {
        return slotCount;
    }

    public int getPriority() {
        return priority;
    }

    public List<CellInfo> getCells() {
        return cells;
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
        return I18n.format("gui.cellterminal.location_format", pos.getX(), pos.getY(), pos.getZ(), dimension);
    }

    public int getTotalCellCount() {
        return cells.size();
    }

    public long getTotalUsedBytes() {
        long total = 0;
        for (CellInfo cell : cells) total += cell.getUsedBytes();

        return total;
    }

    public long getTotalMaxBytes() {
        long total = 0;
        for (CellInfo cell : cells) total += cell.getTotalBytes();

        return total;
    }

    /**
     * Get the cell at a specific slot index.
     * @param slotIndex The slot index (0-based)
     * @return The CellInfo at that slot, or null if the slot is empty
     */
    public CellInfo getCellAtSlot(int slotIndex) {
        for (CellInfo cell : cells) {
            if (cell.getSlot() == slotIndex) return cell;
        }

        return null;
    }
}
