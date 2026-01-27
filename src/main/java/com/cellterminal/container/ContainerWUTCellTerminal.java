package com.cellterminal.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;

import appeng.api.AEApi;
import appeng.api.features.ILocatable;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.core.AEConfig;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.WirelessTerminalGuiObject;

import appeng.util.Platform;

import baubles.api.BaublesApi;

import com.cellterminal.CellTerminal;
import com.circulation.ae2wut.item.ItemWirelessUniversalTerminal;


/**
 * Container for Cell Terminal when opened via AE2WUT's Wireless Universal Terminal.
 * Uses WirelessTerminalGuiObject instead of direct slot access.
 */
@Optional.Interface(iface = "appeng.helpers.WirelessTerminalGuiObject", modid = "ae2wut")
public class ContainerWUTCellTerminal extends ContainerCellTerminalBase {

    private final WirelessTerminalGuiObject wirelessTerminalGuiObject;
    private final int slot;
    private final boolean isBauble;

    private int ticks = 0;

    public ContainerWUTCellTerminal(InventoryPlayer ip, WirelessTerminalGuiObject wth) {
        super(ip, null);

        this.wirelessTerminalGuiObject = wth;
        this.slot = wth.getInventorySlot();
        this.isBauble = wth.isBaubleSlot();

        // Get grid by looking up the encryption key (same pattern as ContainerWirelessCellTerminal)
        // Only do this server-side where the grid/locatable registry is available
        ItemStack terminalStack = getTerminalStack();

        if (Platform.isServer() && !terminalStack.isEmpty()) {
            IWirelessTermHandler handler = AEApi.instance().registries().wireless().getWirelessTerminalHandler(terminalStack);
            if (handler != null) {
                String encKey = handler.getEncryptionKey(terminalStack);

                try {
                    long parsedKey = Long.parseLong(encKey);
                    ILocatable obj = AEApi.instance().registries().locatable().getLocatableBy(parsedKey);

                    if (obj instanceof IActionHost) {
                        IGridNode n = ((IActionHost) obj).getActionableNode();
                        if (n != null) this.grid = n.getGrid();
                    }
                } catch (NumberFormatException e) {
                    CellTerminal.LOGGER.error("ContainerWUTCellTerminal - invalid encKey: {}", encKey);
                }
            }
        }

        // Lock the slot if it's in the main inventory
        if (!isBauble && slot >= 0 && slot < ip.mainInventory.size()) this.lockPlayerInventorySlot(slot);

        this.bindPlayerInventory(ip, 0, 0);
    }

    /**
     * Get the WirelessTerminalGuiObject for this container.
     * Used by the GUI to access WUT functionality.
     */
    public WirelessTerminalGuiObject getWirelessTerminalGuiObject() {
        return this.wirelessTerminalGuiObject;
    }

    private ItemStack getTerminalStack() {
        if (isBauble) return getBaubleStack(getPlayerInv().player, slot);

        return getPlayerInv().getStackInSlot(slot);
    }

    @Optional.Method(modid = "baubles")
    private ItemStack getBaubleStack(EntityPlayer player, int baubleSlot) {
        return BaublesApi.getBaublesHandler(player).getStackInSlot(baubleSlot);
    }

    @Override
    protected boolean canSendUpdates() {
        ItemStack currentStack = getTerminalStack();

        if (currentStack.isEmpty() || !(currentStack.getItem() instanceof ItemWirelessUniversalTerminal)) {
            this.setValidContainer(false);

            return false;
        }

        // Drain power periodically (every 20 ticks = 1 second)
        this.ticks++;
        if (this.ticks >= 20) {
            IWirelessTermHandler handler = AEApi.instance().registries().wireless().getWirelessTerminalHandler(currentStack);
            if (handler == null) {
                this.setValidContainer(false);

                return false;
            }

            double powerDrain = AEConfig.instance().wireless_getDrainRate(0);
            boolean powerUsed = handler.usePower(getPlayerInv().player, powerDrain, currentStack);

            if (!powerUsed) {
                if (this.isValidContainer()) {
                    getPlayerInv().player.sendMessage(PlayerMessages.DeviceNotPowered.get());
                }
                this.setValidContainer(false);

                return false;
            }

            this.ticks = 0;
        }

        // Check range (simplified - just check grid is still valid)
        if (this.grid == null) {
            if (this.isValidContainer()) {
                getPlayerInv().player.sendMessage(PlayerMessages.StationCanNotBeLocated.get());
            }
            this.setValidContainer(false);

            return false;
        }

        return true;
    }

    @Override
    public IActionHost getActionHost() {
        return this.wirelessTerminalGuiObject;
    }
}
