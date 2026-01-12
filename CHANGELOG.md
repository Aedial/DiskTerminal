# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

- Keep a Changelog: https://keepachangelog.com/en/1.1.0/
- Semantic Versioning: https://semver.org/spec/v2.0.0.html


## [0.3.0] - 2026-01-13
### Added
- Add button to toggle between tall and compact GUI styles, with persistent setting

### Fixed
- Fix JEI drag-and-drop not working for enchanted books
- Fix cells not being extractable if they contained any item in inventory view (as it would toggle partition status instead of ejecting)


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