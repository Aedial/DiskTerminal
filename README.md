# Cell Terminal

An AE2-UEL addon providing a centralized terminal to manage storage cells directly.

## Features

### Cell Terminal
A terminal block and wireless variant for managing storage cells across the ME network.

- **Terminal & Wireless Access**: Place the Cell Terminal part on ME cables, or use the Wireless Cell Terminal for remote access. As usual, you will need to use the Security Station to link the wireless terminal to your network.
- **Multi-Cell Support**: Works with all AE2 storage cell types (items, fluids, essentia).
- **Network Overview**: Displays all ME Drives, ME Chests, and Storage Buses in your network, organized by location.
- **Cell Management (Cell Terminal View)**:
  - View cell capacity (used/total bytes and types)
  - Eject cells directly to inventory
  - Preview stored items with quantities
  - Edit partitions with JEI drag-and-drop support
- **Multiple Views**:
  - **Cell Terminal**: Compact list view of cells with expandable entries
  - **Cell Inventory**: Grid view of cells showing stored items
  - **Cell Partition**: Grid view for quick partition editing
  - **Storage Bus Inventory**: View contents accessible through Storage Buses
  - **Storage Bus Partition**: Edit Storage Bus filter configurations
- **Search & Filter**: Find items across all cells/Storage Buses with inventory/partition search modes
- **In-World Highlighting**: Double-click any storage entry to highlight its block in-world and show coordinates in chat
- **Priority Management**: Set ME Chest/Drive/Storage Bus priority directly from the GUI
- **Quick Partition Keybinds**: Configurable keybinds for quick partitioning with the hovered item


## Building
Run:
```
./gradlew -q build
```
Resulting jar will be under `build/libs/`.

## License
This project is licensed under the MIT License - see the LICENSE file for details.
