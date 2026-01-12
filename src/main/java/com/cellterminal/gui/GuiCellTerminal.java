package com.cellterminal.gui;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;

import com.cellterminal.container.ContainerCellTerminal;
import com.cellterminal.part.PartCellTerminal;


/**
 * GUI for the Cell Terminal.
 * Displays all storage drives/chests in the network with their cells,
 * allowing players to view and manage cell partitions.
 */
public class GuiCellTerminal extends GuiCellTerminalBase {

    public GuiCellTerminal(InventoryPlayer playerInventory, PartCellTerminal part) {
        super(new ContainerCellTerminal(playerInventory, part));
    }

    @Override
    protected String getGuiTitle() {
        return I18n.format("gui.cellterminal.cell_terminal.title");
    }
}
