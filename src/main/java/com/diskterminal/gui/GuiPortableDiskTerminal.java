package com.diskterminal.gui;

import net.minecraft.entity.player.InventoryPlayer;

import com.diskterminal.container.ContainerPortableDiskTerminal;


/**
 * GUI for the Portable Disk Terminal.
 * Same functionality as GuiDiskTerminal but for the portable version.
 */
public class GuiPortableDiskTerminal extends GuiDiskTerminalBase {

    public GuiPortableDiskTerminal(InventoryPlayer playerInventory, int slot, boolean isBauble) {
        super(new ContainerPortableDiskTerminal(playerInventory, slot, isBauble));
    }

    @Override
    protected String getGuiTitle() {
        return "Wireless Disk Terminal";
    }
}
