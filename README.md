# Cell Terminal

An AE2-UEL addon providing a centralized terminal to manage storage cells directly.

## Features

### Cell Terminal
A terminal block and wireless variant for managing storage cells across the ME network.

- **Terminal & Wireless Access**: Place the Cell Terminal part on ME cables, or use the Wireless Cell Terminal for remote access. As usual, you will need to use the Security Station to link the wireless terminal to your network.
- **Multi-Cell Support**: Works with all AE2 storage cell types (items, fluids, essentia).
- **Network Overview**: Displays all ME Drives and ME Chests in your network, organized by location.
- **Cell Management**:
  - View cell capacity (used/total bytes and types)
  - Eject cells directly to inventory
  - Preview stored items with quantities
  - Edit partitions with JEI drag-and-drop support
- **Multiple Views**:
  - **Terminal**: Compact list view with expandable entries
  - **Inventory**: Grid view of cells showing stored items
  - **Partition**: Grid view for quick partition editing
- **Search & Filter**: Find items across all cells with inventory/partition search modes

### Compacting Storage Cells
Storage cells that automatically expose compressed and decompressed forms of items to the ME network, similar to Storage Drawers' Compacting Drawer.

#### How It Works
1. **Partition Required**: Compacting cells require a partition to be set before they can accept items.
2. **Compression Chain**: When partitioned with an item (e.g., Iron Ingot), the cell automatically detects the compression chain:
   - Higher tier: Iron Block (compressed form)
   - Main tier: Iron Ingot (the partitioned item)
   - Lower tier: Iron Nugget (decompressed form)
3. **Virtual Conversion**: Items are stored in a unified pool and can be extracted in any compression tier:
   - Insert 81 Iron Nuggets → Extract 81 Nuggets, 9 Iron Ingots, or 1 Iron Block
   - Insert 1 Iron Block → Extract 9 Iron Ingots, 81 Iron Nuggets, or 1 Iron Block
   - All conversions are lossless and instant
4. **Single Item Type**: Each compacting cell stores only one item type (with its compression variants).
5. **Storage Counting**: Storage capacity is measured in main tier (partitioned item) units. All items are internally converted to base units for proper accounting.

#### Available Tiers
- **1k Compacting Storage Cell** (1,024 bytes)
- **4k Compacting Storage Cell** (4,096 bytes)
- **16k Compacting Storage Cell** (16,384 bytes)
- **64k Compacting Storage Cell** (65,536 bytes)

With NAE2 installed:
- **256k Compacting Storage Cell** (262,144 bytes)
- **1M Compacting Storage Cell** (1,048,576 bytes)
- **4M Compacting Storage Cell** (4,194,304 bytes)
- **16M Compacting Storage Cell** (16,777,216 bytes)

#### Partition Protection
- If a compacting cell contains items, the partition cannot be changed.
- Attempts to change the partition via Cell Workbench are automatically reverted.
- Empty the cell first before changing what item type it stores.

#### Void Overflow Upgrade
- Only works with Compacting Storage Cells (no support in AE2 or NAE2).
- Install a Void Overflow Card in the cell's upgrade slots.
- When the cell is full, excess items are voided instead of rejected.
- Useful for automated systems where overflow should be destroyed.

### Additional normal Storage Cells
Storage cells with larger capacities:
- **64M Normal Storage Cell** (67,108,864 bytes)
- **256M Normal Storage Cell** (268,435,456 bytes)
- **1G Normal Storage Cell** (1,073,741,824 bytes)
- **2G Normal Storage Cell** (2,147,483,648 bytes)

### High-Density Storage Cells
For even larger capacities:
- **1k High-Density Storage Cell** (1024 * 2,147,483,648 bytes)
- **4k High-Density Storage Cell** (4096 * 2,147,483,648 bytes)
- **16k High-Density Storage Cell** (16384 * 2,147,483,648 bytes)
- **64k High-Density Storage Cell** (65536 * 2,147,483,648 bytes)
- **256k High-Density Storage Cell** (262144 * 2,147,483,648 bytes)
- **1M High-Density Storage Cell** (1,048,576 * 2,147,483,648 bytes)
- **4M High-Density Storage Cell** (4,194,304 * 2,147,483,648 bytes)
- **16M High-Density Storage Cell** (16,777,216 * 2,147,483,648 bytes)
- **64M High-Density Storage Cell** (67,108,864 * 2,147,483,648 bytes)
- **256M High-Density Storage Cell** (268,435,456 * 2,147,483,648 bytes)
- **1G High-Density Storage Cell** (1,073,741,824 * 2,147,483,648 bytes)

Also comes with High-Density Compacting Storage Cell variants.


## Building
Run:
```
./gradlew -q build
```
Resulting jar will be under `build/libs/`.

## License
This project is licensed under the MIT License - see the LICENSE file for details.
