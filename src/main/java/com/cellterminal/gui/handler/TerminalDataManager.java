package com.cellterminal.gui.handler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import com.cellterminal.client.CellContentRow;
import com.cellterminal.client.CellInfo;
import com.cellterminal.client.EmptySlotInfo;
import com.cellterminal.client.StorageInfo;


/**
 * Manages storage data and line building for Cell Terminal GUI.
 */
public class TerminalDataManager {

    private static final int SLOTS_PER_ROW = 8;
    private static final int MAX_PARTITION_SLOTS = 63;

    private final Map<Long, StorageInfo> storageMap = new LinkedHashMap<>();
    private final List<Object> lines = new ArrayList<>();
    private final List<Object> inventoryLines = new ArrayList<>();
    private final List<Object> partitionLines = new ArrayList<>();

    private BlockPos terminalPos = BlockPos.ORIGIN;
    private int terminalDimension = 0;

    public Map<Long, StorageInfo> getStorageMap() {
        return storageMap;
    }

    public List<Object> getLines() {
        return lines;
    }

    public List<Object> getInventoryLines() {
        return inventoryLines;
    }

    public List<Object> getPartitionLines() {
        return partitionLines;
    }

    public BlockPos getTerminalPos() {
        return terminalPos;
    }

    public int getTerminalDimension() {
        return terminalDimension;
    }

    public void processUpdate(NBTTagCompound data) {
        if (data.hasKey("terminalPos")) {
            this.terminalPos = BlockPos.fromLong(data.getLong("terminalPos"));
            this.terminalDimension = data.getInteger("terminalDim");
        }

        if (!data.hasKey("storages")) return;

        this.storageMap.clear();
        NBTTagList storageList = data.getTagList("storages", Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < storageList.tagCount(); i++) {
            NBTTagCompound storageNbt = storageList.getCompoundTagAt(i);
            StorageInfo storage = new StorageInfo(storageNbt);
            this.storageMap.put(storage.getId(), storage);
        }

        rebuildLines();
    }

    public void rebuildLines() {
        this.lines.clear();
        this.inventoryLines.clear();
        this.partitionLines.clear();

        List<StorageInfo> sortedStorages = new ArrayList<>(this.storageMap.values());
        sortedStorages.sort(createStorageComparator());

        for (StorageInfo storage : sortedStorages) {
            this.lines.add(storage);
            this.inventoryLines.add(storage);
            this.partitionLines.add(storage);

            for (int slot = 0; slot < storage.getSlotCount(); slot++) {
                CellInfo cell = storage.getCellAtSlot(slot);

                if (cell != null) {
                    cell.setParentStorageId(storage.getId());

                    if (storage.isExpanded()) {
                        this.lines.add(cell);
                    }

                    int contentCount = cell.getContents().size();
                    int contentRows = Math.max(1, (contentCount + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW);
                    for (int row = 0; row < contentRows; row++) {
                        this.inventoryLines.add(new CellContentRow(cell, row * SLOTS_PER_ROW, row == 0));
                    }

                    int highestSlot = getHighestNonEmptyPartitionSlot(cell);
                    int partitionRows = Math.max(1, (highestSlot + SLOTS_PER_ROW) / SLOTS_PER_ROW);
                    for (int row = 0; row < partitionRows; row++) {
                        this.partitionLines.add(new CellContentRow(cell, row * SLOTS_PER_ROW, row == 0));
                    }
                } else {
                    EmptySlotInfo emptySlot = new EmptySlotInfo(storage.getId(), slot);
                    this.inventoryLines.add(emptySlot);
                    this.partitionLines.add(emptySlot);
                }
            }
        }
    }

    private int getHighestNonEmptyPartitionSlot(CellInfo cell) {
        List<ItemStack> partition = cell.getPartition();
        int highest = -1;

        for (int i = 0; i < partition.size(); i++) {
            if (!partition.get(i).isEmpty()) {
                highest = i;
            }
        }

        if (highest >= 0) {
            int currentRows = (highest / SLOTS_PER_ROW) + 1;
            int lastSlotInLastRow = (currentRows * SLOTS_PER_ROW) - 1;

            if (highest == lastSlotInLastRow && highest < MAX_PARTITION_SLOTS - 1) {
                highest = Math.min(highest + SLOTS_PER_ROW, MAX_PARTITION_SLOTS - 1);
            }
        }

        return highest;
    }

    private Comparator<StorageInfo> createStorageComparator() {
        return (a, b) -> {
            boolean aInDim = a.getDimension() == terminalDimension;
            boolean bInDim = b.getDimension() == terminalDimension;

            if (aInDim != bInDim) return aInDim ? -1 : 1;

            if (a.getDimension() != b.getDimension()) return Integer.compare(a.getDimension(), b.getDimension());

            double distA = terminalPos.distanceSq(a.getPos());
            double distB = terminalPos.distanceSq(b.getPos());

            return Double.compare(distA, distB);
        };
    }

    public int getLineCount(int currentTab) {
        switch (currentTab) {
            case 1:
                return inventoryLines.size();
            case 2:
                return partitionLines.size();
            default:
                return lines.size();
        }
    }
}
