# Cell Terminal

An AE2 addon providing a centralized terminal to manage storage cells and storage buses directly.

## Features

### Cell Terminal
A terminal and wireless terminal for managing storage cells and storage buses across the ME network.

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
  - **Network Tools**: Various tools for managing and optimizing the whole network
- **Search & Filter**: Find items across all cells/Storage Buses with inventory/partition search modes
- **In-World Highlighting**: Double-click any storage entry to highlight its block in-world and show coordinates in chat
- **Priority Management**: Set ME Chest/Drive/Storage Bus priority directly from the GUI
- **Quick Partition Keybinds**: Configurable keybinds for quick partitioning with the hovered item

### Subnets overview
Clicking on the left arrow, top left of the terminal, will open a subnet overview, showing all connected subnets and their contents. You can then rename subnets, favorite them to show them at the top of the list, or load a subnet into the terminal to manage its contents. The last loaded subnet will be remembered and automatically loaded the next time you open the terminal (until world reload).

### Network Tools
A set of tools to manage and optimize your ME network storage. All tools can be matched against all available filtering options (search text, advanced search, filter buttons).
- **Attribute Unique Tool**: Redistributes items/fluids/essentia from matching cells to ensure each cell contains 1 unique type. May use free, unpartitioned cells on the network if needed.
- **Partition Storage Cells from Content**: Automatically partitions storage cells that match the filter, based on their current contents.
- **Partition Storage Buses from Content**: Automatically configures Storage Bus filters that match the filter, based on their current accessible contents.


## Building
Run:
```
./gradlew -q build
```
First build may take some time. Resulting jar will be under `build/libs/`.

## License
This project is licensed under the MIT License - see the LICENSE file for details.
