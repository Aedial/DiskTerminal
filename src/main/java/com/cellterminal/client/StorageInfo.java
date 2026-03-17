package com.cellterminal.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import com.cellterminal.gui.rename.Renameable;
import com.cellterminal.gui.rename.RenameTargetType;


/**
 * Client-side data holder for drive/chest storage information received from server.
 * <p>
 * <b>NBT Data Map</b> (written by {@link com.cellterminal.container.handler.CellDataHandler#createStorageData}):
 * <pre>
 * StorageInfo                          Size (bytes)
 * ─────────────────────────────────────────────────
 * "id"              long                  8
 * "pos"             long (BlockPos)       8
 * "dim"             int                   4
 * "name"            String                ~N  (storage device name or lang key)
 * "priority"        int                   4   (if IPriorityHost)
 * "supportsPriority" boolean              1   (from scanner)
 * "blockItem"       NBTTagCompound        ~B  (optional; block item representation)
 * "slotCount"       int                   4
 * "cells"           NBTTagList            S * C  (C = number of occupied cell slots)
 *   └─ each entry: see {@link CellInfo} data map
 * ─────────────────────────────────────────────────
 * Total ≈ 29 + N + B + S * C
 *   where S = size of one CellInfo compound (see CellInfo),
 *         C = number of populated cell slots,
 *         N = name string length,
 *         B = block item NBT size (0 if absent)
 * </pre>
 */
public class StorageInfo implements Renameable, Prioritizable {

    private final long id;
    private final BlockPos pos;
    private final int dimension;
    private final String name;
    private final ItemStack blockItem;
    private final int slotCount;
    private final int priority;
    private final boolean supportsPriorityFlag;
    private final List<CellInfo> cells = new ArrayList<>();

    public StorageInfo(NBTTagCompound nbt) {
        this.id = nbt.getLong("id");
        this.pos = BlockPos.fromLong(nbt.getLong("pos"));
        this.dimension = nbt.getInteger("dim");
        this.name = nbt.getString("name");
        this.blockItem = nbt.hasKey("blockItem") ? new ItemStack(nbt.getCompoundTag("blockItem")) : ItemStack.EMPTY;
        this.slotCount = nbt.getInteger("slotCount");
        this.priority = nbt.getInteger("priority");
        this.supportsPriorityFlag = nbt.getBoolean("supportsPriority");

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

    /**
     * Check if this storage supports priority editing.
     */
    public boolean supportsPriority() {
        return supportsPriorityFlag;
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
        // Storage name is always set (either custom or translation key)
        // We consider it "custom" if it doesn't start with tile./item. (translation key)
        if (name != null && !name.startsWith("tile.") && !name.startsWith("item.")) return name;

        return null;
    }

    @Override
    public boolean hasCustomName() {
        return name != null && !name.startsWith("tile.") && !name.startsWith("item.");
    }

    @Override
    public void setCustomName(String name) {
        // Client-side optimistic update is not applicable for StorageInfo's final fields.
        // The server will send a full refresh after rename.
    }

    @Override
    public RenameTargetType getRenameTargetType() {
        return RenameTargetType.STORAGE;
    }

    @Override
    public long getRenameId() {
        return id;
    }
}
