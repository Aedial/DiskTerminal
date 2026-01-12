package com.cellterminal.gui;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;

import com.cellterminal.container.ContainerWirelessCellTerminal;


/**
 * GUI for the Wireless Cell Terminal.
 * Same functionality as GuiCellTerminal but for the wireless version.
 */
public class GuiWirelessCellTerminal extends GuiCellTerminalBase {

    public GuiWirelessCellTerminal(InventoryPlayer playerInventory, int slot, boolean isBauble) {
        super(new ContainerWirelessCellTerminal(playerInventory, slot, isBauble));
    }

    @Override
    protected String getGuiTitle() {
        return I18n.format("gui.cellterminal.wireless_cell_terminal.title");
    }
}
