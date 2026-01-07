package com.diskterminal.gui;

import net.minecraft.entity.player.InventoryPlayer;

import com.diskterminal.container.ContainerDiskTerminal;
import com.diskterminal.part.PartDiskTerminal;


/**
 * GUI for the Disk Terminal.
 * Displays all storage drives/chests in the network with their disks,
 * allowing players to view and manage disk partitions.
 */
public class GuiDiskTerminal extends GuiDiskTerminalBase {

    public GuiDiskTerminal(InventoryPlayer playerInventory, PartDiskTerminal part) {
        super(new ContainerDiskTerminal(playerInventory, part));
    }

    @Override
    protected String getGuiTitle() {
        return "Disk Terminal";
    }
}
