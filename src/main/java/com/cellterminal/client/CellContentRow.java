package com.cellterminal.client;


/**
 * Represents a row of cell contents or partitions for display in Tab 2/3/Temp Area.
 * Each CellContentRow shows up to 8 items from a cell, starting at a given index.
 */
public class CellContentRow {

    private final CellInfo cell;
    private final int startIndex;
    private final boolean isFirstRow;
    private final boolean isPartitionRow;

    /**
     * @param cell The cell this row belongs to
     * @param startIndex The starting index of items to display in this row (0, 8, 16, etc)
     * @param isFirstRow Whether this is the first row for this cell (shows cell icon)
     */
    public CellContentRow(CellInfo cell, int startIndex, boolean isFirstRow) {
        this(cell, startIndex, isFirstRow, false);
    }

    /**
     * @param cell The cell this row belongs to
     * @param startIndex The starting index of items to display in this row (0, 8, 16, etc)
     * @param isFirstRow Whether this is the first row for this cell (shows cell icon)
     * @param isPartitionRow Whether this row displays partition data (vs inventory data)
     */
    public CellContentRow(CellInfo cell, int startIndex, boolean isFirstRow, boolean isPartitionRow) {
        this.cell = cell;
        this.startIndex = startIndex;
        this.isFirstRow = isFirstRow;
        this.isPartitionRow = isPartitionRow;
    }

    public CellInfo getCell() {
        return cell;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public boolean isFirstRow() {
        return isFirstRow;
    }

    /**
     * Whether this row displays partition data (vs inventory contents).
     * Used in Temp Area tab to differentiate between inventory and partition sections.
     */
    public boolean isPartitionRow() {
        return isPartitionRow;
    }
}
