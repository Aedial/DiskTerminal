package com.cellterminal.gui;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.List;

import com.cellterminal.container.ContainerWirelessCellTerminal;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.helpers.WirelessTerminalGuiObject;

import com.cellterminal.config.CellTerminalClientConfig;
import com.cellterminal.integration.AE2WUTIntegration;
import com.cellterminal.integration.WUTModeSwitcher;


/**
 * GUI for the Wireless Cell Terminal.
 * Same functionality as GuiCellTerminal but for the wireless version.
 */
public class GuiWirelessCellTerminal extends GuiCellTerminalBase {

    private final WirelessTerminalGuiObject wirelessTerminalGuiObject;
    private WUTModeSwitcher modeSwitcher;

    public GuiWirelessCellTerminal(InventoryPlayer playerInventory, WirelessTerminalGuiObject wth) {
        super(new ContainerWirelessCellTerminal(playerInventory, wth));
        this.wirelessTerminalGuiObject = wth;
    }

    @Override
    protected String getGuiTitle() {
        return I18n.format("gui.cellterminal.wireless_cell_terminal.title");
    }

    @Override
    public void initGui() {
        // Clear old mode switcher buttons before super.initGui() clears buttonList
        if (modeSwitcher != null) {
            modeSwitcher = null;
        }

        super.initGui();

        // Initialize WUT mode switcher if available
        if (AE2WUTIntegration.isModLoaded()) {
            this.modeSwitcher = new WUTModeSwitcher(wirelessTerminalGuiObject);
            boolean isSmallStyle = CellTerminalClientConfig.getInstance().getTerminalStyle() == CellTerminalClientConfig.TerminalStyle.SMALL;
            this.nextButtonId = modeSwitcher.initButtons(this.buttonList, this.nextButtonId, this.guiLeft, this.guiTop, this.ySize, this.height, isSmallStyle);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        // Check if WUT mode switcher handles this button
        if (modeSwitcher != null && modeSwitcher.handleButtonClick(button)) return;

        super.actionPerformed(button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw WUT mode switcher tooltips
        if (modeSwitcher != null) modeSwitcher.drawTooltips(mouseX, mouseY, this);
    }

    /**
     * Get the WirelessTerminalGuiObject for WUT integration.
     */
    public WirelessTerminalGuiObject getWirelessTerminalGuiObject() {
        return this.wirelessTerminalGuiObject;
    }

    /**
     * Get the container as the WUT-specific type.
     */
    public ContainerWirelessCellTerminal getWUTContainer() {
        return (ContainerWirelessCellTerminal) this.inventorySlots;
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> areas = super.getJEIExclusionArea();

        // Add WUT mode switcher exclusion area if present
        if (modeSwitcher != null) {
            Rectangle switcherArea = modeSwitcher.getExclusionArea();
            if (switcherArea.width > 0) areas.add(switcherArea);
        }

        return areas;
    }
}
