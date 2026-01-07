package com.diskterminal.container;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.util.Platform;

import com.diskterminal.part.PartDiskTerminal;


/**
 * Container for the Disk Terminal GUI.
 * Scans the ME network for all drives and chests with their storage disks,
 * then sends the information to the client for display.
 */
public class ContainerDiskTerminal extends ContainerDiskTerminalBase {

    public ContainerDiskTerminal(InventoryPlayer ip, PartDiskTerminal part) {
        super(ip, part);

        if (Platform.isServer()) this.grid = part.getActionableNode().getGrid();

        this.bindPlayerInventory(ip, 0, 0);
    }
}
