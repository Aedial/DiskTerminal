# Cell Terminal

An AE2-UEL addon providing a terminal block to access and manage storage cells directly, in a centralized manner.

## Features
- Provides both a multipart and wireless terminal.
- Supports all AE2 storage cell types (items, fluids, essentia).
- Easy insertion/removal of cells.
- Contains a category per block and a row per cell:
  - Categories show the block and its location.
  - Row shows the cell icon:
    - Cell capacity (used/total).
    - Direct modification of partition with JEI integration (dragging) and cell-content-to-partition support (hover to preview, click to edit).
    - Overview of stored items with quantity (hover to preview, click to view). Click on an item to add it to partition.
- Multiple GUI views:
  - List view: Compact view of everything, with inventory and partition popups.
  - Inventory view: Grid view of cells as interactive slots with content preview. Faster view and reordering/patitioning of cells according to content.
  - Partition view: Grid view of cells as interactive slots with partition slots. Faster view and partitioning of cells.
- Sorting with various criteria:
  - Empty/Non-empty cells (TODO).
  - Used space (TODO).
  - Number of types (TODO).
  - Number of partitioned items (TODO).
  - Search filter for items across all cells.

## Building
Run:
```
./gradlew -q build
```
Resulting jar will be under `build/libs/`.

## License
This project is licensed under the MIT License - see the LICENSE file for details.
