# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

- Keep a Changelog: https://keepachangelog.com/en/1.1.0/
- Semantic Versioning: https://semver.org/spec/v2.0.0.html


## [1.4.0-rc1] - 2026-02-18
### Added
- Add subnet overview screen, showing all connected subnets and allowing to load them in the main terminal view. This allows better management of multiple subnets, instead of having a different terminal for each subnet and no way to see the overall structure of the network.


## [1.3.0] - 2026-02-16
### Added
- Add shift rigft-click handling for upgrade cards to Storage Buses, like Storage Cells, to insert them directly without dragging.
- Add individual config options to disable the integration with other mods (CrazyAE, ECOAEExtension, WUT) for better compatibility with different versions of those mods.

### Fixed
- Fix "Attribute Uniques" Network Tool not having proper failsafes, resulting in potential item loss if space was insufficient for the redistribution.
- Fix "Attribute Uniques" not handling Long overflows when calculating the distribution, which could silently cause item loss if a cell had a very large amount of items.
- Fix shift right-click on custom upgrades not working for cells.
- Properly exclude Compacting Cells from Partitioning and Attribute Unique tools, as they should not change partition or content. They expose virtual items that do not actually exist in the cell, which can cause issues with the tools if they are included.


## [1.3.0-rc2] - 2026-02-02
### Added
- Add exclusion of IICompactingCells from Partition Storage Cells from Content tool, as they should only have 1 partition, yet expose multiple item types

### Fixed
- Fix non-standard upgrade cards (not from base AE2) not being properly accepted for insertion into cells/storage buses
- Fix Attribute Unique Tool not properly refreshing the network after execution, requiring manual cell re-insertion to see changes


## [1.3.0-rc1] - 2026-02-02
### Added
- Add Network Tools as a 6th tab:
  - Unique Content Distributor tool to partition unique each cell with a unique content type (from filtered cells) and move the existing content accordingly
  - Mass Partition All Cells tool to set all filtered cells' partitions to match their current contents
  - Mass Partition Storage Bus tool to set the filter of a Storage Bus to match its current inventory contents
  - All tools come with heavy warnings and eager confirmation dialogs to avoid accidental usage


## [1.2.1] - 2026-01-30
### Added
- Add a button to decide the max number of slots to show per cell/storage bus in inventory (8, 32, 64, or all)
- Add [+]/[-] buttons to all storage entries to expand/collapse them, with persistent state until restart
- Add persistent scroll position per tab until restart

### Technical
- Start refactoring integration into separate Scanners for better modularity and future extensibility


## [1.2.0] - 2026-01-29
### Added
- Add CrazyAE's Drives support
- Add ECOAEExtension's E-Storage Drives support (largely untested)


## [1.1.0] - 2026-01-27
### Added
- Add integration with AE2 Wireless Universal Terminal mod:
  - Register Cell Terminal as a Wireless Universal Terminal mode
  - Add Cell Terminal mode switcher button in the GUI when AE2WUT is present
  - Support for crafting recipes using the Wireless Universal Terminal with Cell Terminal mode


## [1.0.0] - 2026-01-25
### Added
- Add recipes for Cell Terminal and Wireless Cell Terminal
- Add textures for Cell Terminal and Wireless Cell Terminal items
- Add models for Cell Terminal part and Wireless Cell Terminal item


## [0.5.5] - 2026-01-24
### Fixed
- Fix double-click highlighting for Storage Cells sometimes missfiring


## [0.5.4] - 2026-01-24
### Fixed
- Fix Void Cells not being properly handled


## [0.5.3] - 2026-01-24
### Added
- Add server config option to disable core features:
  - Each individual tab
  - Storage Bus Inventory polling (enable and frequency)
  - Cell Ejection/Insertion/Swapping
  - Partition/Priority Editing
  - Upgrade Insertion/Extraction

- Add client config option to tweak visuals:
  - Distance limit for block highlighting
  - Duration of block highlighting when double-clicking entries

### Fixed
- Fix shift-clicking Upgrade Cards not working for Storage Buses tabs
- Fix Small style GUI sticking to the bottom (instead of centering)


## [0.5.2] - 2026-01-23
### Fixed
- Fix Upgrade Cards not being properly consumed when inserted fast enough
- Fix Upgrade Cards only being extractable in reverse order
- Fix tab 4/5 not updating until tab switch, when opening the GUI with a storage bus already selected
- Fix item filter considering item count when checking for existing items in Storage Bus slots
- Fix double-click partition/inventory slots triggering device highlighting
- Fix search failing to match items after a gap on Storage Bus tabs, due to an early exit
- Fix highlighting Storage Bus in-world not giving proper user feedback
- Fix Set Contents to Filter button not working if the Storage Bus already has a filter configured

### Changed
- Change Upgrade Cards to show their physical slot position


## [0.5.1] - 2026-01-22
### Added
- Add localization for new search syntax errors
- Add double-click to expand search bar as a modal
- Add upgrades controls tooltip and insertion/extraction


## [0.5.0] - 2026-01-21
### Added
- Add filter buttons for visibility control:
  - Item Cells/Storage Buses
  - Fluid Cells/Storage Buses
  - Essentia Cells/Storage Buses (only when Thaumic Energistics is loaded)
  - Has Contents (cells/buses with items)
  - Has Partition/Filter (cells/buses with configured filters)
  - Each filter cycles through Show All → Show Only → Hide states
  - Filter states are separate for cell tabs (1-3) and storage bus tabs (4-5)
- Add advanced search syntax (prefix with "?" to use):
  - `$name` - Match against inventory/partition content's name/id (string matching)
  - `$priority` - Match against storage priority (numeric comparison)
  - `$partition` - Match against partition slot count (numeric comparison)
  - `$items` - Match against stored item type count (numeric comparison)
  - Operators: `= != < > <= >= ~` for comparisons, `& |` for AND/OR, `( )` for grouping
  - Examples: `?$priority>0`, `? $items=0 & $partition>0`, `? $name~iron | $name~gold`
- Add search help button ("?") next to the search field, showing syntax help on hover


## [0.4.2] - 2026-01-20
### Added
- Add better user feedback on user messages (errors, success)

### Fixed
- Fix fluid parition detection failing in some cases due to fluid amount differences


## [0.4.1] - 2026-01-19
### Fixed
- Fix Essentia partitioning not recognizing NBT differences, causing issues when partitioning multiple essentia types


## [0.4.0] - 2026-01-18
### Added
- Add priority field to set ME Chest/Drive priority directly from the Terminal GUI
- Add Inventory tab (4th tab) for Storage Buses, showing all items the bus sees (mirroring the behavior of Tab 2 for drives/chests)
- Add Partition tab (5th tab) for Storage Buses, showing the partition slots of the bus (mirroring the behavior of Tab 3 for drives/chests)

### Changed
- Double-clicking a storage entry to highlight in-world now also sends a green chat message with the block name and coordinates


## [0.3.2] - 2026-01-14
### Added
- Add quick partition keybinds (defaulting to unbound) for partitioning cells with hovered items:
  - Quick Partition (Auto Type): Automatically infers cell type (with warning about potential misattribution)
  - Quick Partition (Item Cell): Partition into the first item cell without partition
  - Quick Partition (Fluid Cell): Partition into the first fluid cell without partition
  - Quick Partition (Essentia Cell): Partition into the first essentia cell without partition
- Add controls help widget for guiding the player


## [0.3.1] - 2026-01-13
### Fixed
- Fix shift-clicking cells in tab 2/3 views putting them into hand instead of directly in inventory

### Changed
- Slightly move the components for the item filter


## [0.3.0] - 2026-01-13
### Added
- Add button to toggle between tall and compact GUI styles, with persistent setting
- Add item filter for inventory/partition, with button to toggle filter input
- Add support for Thaumic Energistics Essentia Cells

### Fixed
- Fix JEI drag-and-drop not working for enchanted books
- Fix cells not being extractable if they contained any item in inventory view (as it would toggle partition status instead of ejecting)
- Fix the network not refreshing after partition changes, requiring inserting/ejecting any cell in the drive to update


## [0.2.0] - 2026-01-12
### Added
- Add tabbed main GUI with three views using item icons:
  - Terminal tab (Interface Terminal icon): Original list view with the cells
  - Inventory tab: Grid view of cells as interactive slots with content preview
  - Partition tab: Grid view of cells as interactive slots with partition slots
- Persist selected tab across sessions using client config
- Add localization for a lot of hardcoded strings in the GUI

### Fixed
- Fix items already in partition not being properly toggled when clicking in the inventory view

### Changed
- Extend the formatting of item counts to handle large numbers (K, M, B, T, etc.)
- Change default categories sorting from "distance to terminal" to "distance to origin (0,0,0)" for better wireless terminal consistency
- Harmonize the terminology to use "cell" instead of "disk" throughout the GUI and codebase, as AE2 refers to storage units as "cells"
- Harmonize the terminology to use "wireless" instead of "portable" for the wireless terminal item, to match the naming


## [0.1.1] - 2026-01-08
### Added
- Add a separator line between different storage categories (drives and chests) in the cells list
- Add semi-transparent overlay to cell entries for better readability
- Add shift-click handling for cells in player inventory, to insert them into the first available drive slot
- Add click handling on a storage category while holding a cell to insert it into this category's first available drive slot
- Double-click on an entry/category to highlight the block it represents in-world, for 15 seconds
- Sort cells list by distance to terminal (same dimension prioritized)
- Add small symbol (P) for items in inventory view that are currently in the partition

### Fixed
- Fix the darkening of modal windows
- Fix fluid cells not using the proper drag-and-drop (item cells's behavior was used instead)

### Changed
- Adjust the collapse/expand to be on the [+]/[-] button only, to allow other click interactions on the entry


## [0.1.0] - 2026-01-07
### Added
- Cell Terminal part that can be placed on ME cables to view and manage storage cells
- Wireless Cell Terminal item for wireless access to cell management
- Scrollable GUI displaying all ME Drives and ME Chests in the network
- Expandable/collapsible storage entries showing drive location and custom name
- Cell entries showing cell type, storage usage bar, and byte/type counts
- Network scanning for all active storage containers (drives and chests)
- Real-time cell information including used/total bytes and stored item types
- Eject button (E) to remove cells from drives directly to player inventory
- Inventory preview button (I) showing cell contents with partition highlighting
- Partition editor button (P) for managing cell filter configuration
- Double-click items in inventory view to toggle partition status
- "Set All to Partition" button to partition all current cell contents
- Click-to-remove items from partition slots
- JEI ghost ingredient support for dragging items into partition slots
- Wireless terminal linking via Security Terminal (same as AE2 wireless terminals)
- Power consumption and range checking for wireless terminal
- Baubles support for wearing wireless terminal as a trinket