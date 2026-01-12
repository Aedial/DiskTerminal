package com.cellterminal.container;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.util.Platform;

import com.cellterminal.part.PartCellTerminal;


/**
 * Container for the Cell Terminal GUI.
 * Scans the ME network for all drives and chests with their storage cells,
 * then sends the information to the client for display.
 */
public class ContainerCellTerminal extends ContainerCellTerminalBase {

    public ContainerCellTerminal(InventoryPlayer ip, PartCellTerminal part) {
        super(ip, part);

        if (Platform.isServer()) this.grid = part.getActionableNode().getGrid();

        this.bindPlayerInventory(ip, 0, 0);
    }
}
