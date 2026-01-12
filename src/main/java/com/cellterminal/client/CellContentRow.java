package com.cellterminal.client;


/**
 * Represents a row of cell contents or partitions for display in Tab 2/3.
 * Each CellContentRow shows up to 8 items from a cell, starting at a given index.
 */
public class CellContentRow {

    private final CellInfo cell;
    private final int startIndex;
    private final boolean isFirstRow;

    /**
     * @param cell The cell this row belongs to
     * @param startIndex The starting index of items to display in this row (0, 8, 16, etc)
     * @param isFirstRow Whether this is the first row for this cell (shows cell icon)
     */
    public CellContentRow(CellInfo cell, int startIndex, boolean isFirstRow) {
        this.cell = cell;
        this.startIndex = startIndex;
        this.isFirstRow = isFirstRow;
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
}
