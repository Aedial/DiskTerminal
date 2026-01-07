# Disk Terminal

An AE2-UEL addon providing a terminal block to access and manage storage cells directly, in a centralized manner.

## Features
- Provides both a multipart and wireless terminal.
- Supports all AE2 storage cell types.
- Easy insertion/removal of cells.
- Contains a category per block and a row per cell:
  - Categories show the block and its location.
  - Row shows the cell icon:
    - Cell capacity (used/total).
    - Direct modification of partition with JEI integration (dragging) and cell-content-to-partition support (hover to preview, click to edit).
    - Overview of stored items with quantity (hover to preview, click to view). Click on an item to add it to partition.
- Sorting with various criteria:
  - Empty/Non-empty cells.
  - Used space.
  - Number of types.
  - Number of partitioned items.
  - Search filter for items across all cells.

## Building
Run:
```
./gradlew -q build
```
Resulting jar will be under `build/libs/`.

## License
This project is licensed under the MIT License - see the LICENSE file for details.
