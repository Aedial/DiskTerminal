# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

- Keep a Changelog: https://keepachangelog.com/en/1.1.0/
- Semantic Versioning: https://semver.org/spec/v2.0.0.html


## [0.1.1] - 2026-01-08
### Added
- Add a separator line between different storage categories (drives and chests) in the disks list
- Add semi-transparent overlay to disk entries for better readability
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
- Disk Terminal part that can be placed on ME cables to view and manage storage disks
- Wireless Disk Terminal item for wireless access to disk management
- Scrollable GUI displaying all ME Drives and ME Chests in the network
- Expandable/collapsible storage entries showing drive location and custom name
- Disk entries showing cell type, storage usage bar, and byte/type counts
- Network scanning for all active storage containers (drives and chests)
- Real-time disk information including used/total bytes and stored item types
- Eject button (E) to remove disks from drives directly to player inventory
- Inventory preview button (I) showing disk contents with partition highlighting
- Partition editor button (P) for managing cell filter configuration
- Double-click items in inventory view to toggle partition status
- "Set All to Partition" button to partition all current disk contents
- Click-to-remove items from partition slots
- JEI ghost ingredient support for dragging items into partition slots
- Wireless terminal linking via Security Terminal (same as AE2 wireless terminals)
- Power consumption and range checking for wireless terminal
- Baubles support for wearing wireless terminal as a trinket