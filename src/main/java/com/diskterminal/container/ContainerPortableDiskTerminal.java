package com.diskterminal.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.features.ILocatable;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.core.AEConfig;
import appeng.core.localization.PlayerMessages;
import appeng.util.Platform;

import baubles.api.BaublesApi;

import com.diskterminal.items.ItemPortableDiskTerminal;


/**
 * Container for the Portable Disk Terminal GUI.
 * Similar to ContainerDiskTerminal but works with wireless connection.
 */
public class ContainerPortableDiskTerminal extends ContainerDiskTerminalBase {

    private final int slot;
    private final boolean isBauble;
    private final ItemStack terminalStack;

    private int ticks = 0;

    public ContainerPortableDiskTerminal(InventoryPlayer ip, int slot, boolean isBauble) {
        super(ip, null);
        this.slot = slot;
        this.isBauble = isBauble;

        if (isBauble) {
            this.terminalStack = getBaubleStack(ip.player, slot);
        } else {
            this.terminalStack = ip.getStackInSlot(slot);
            this.lockPlayerInventorySlot(slot);
        }

        if (Platform.isServer() && !terminalStack.isEmpty()) {
            IWirelessTermHandler handler = AEApi.instance().registries().wireless().getWirelessTerminalHandler(terminalStack);

            if (handler != null) {
                String encKey = handler.getEncryptionKey(terminalStack);

                try {
                    long parsedKey = Long.parseLong(encKey);
                    ILocatable obj = AEApi.instance().registries().locatable().getLocatableBy(parsedKey);

                    if (obj instanceof IActionHost) {
                        IGridNode n = ((IActionHost) obj).getActionableNode();
                        if (n != null) {
                            this.grid = n.getGrid();
                        }
                    }
                } catch (NumberFormatException e) {
                    // Invalid key
                }
            }
        }

        this.bindPlayerInventory(ip, 0, 0);
    }

    @Optional.Method(modid = "baubles")
    private ItemStack getBaubleStack(EntityPlayer player, int baubleSlot) {
        return BaublesApi.getBaublesHandler(player).getStackInSlot(baubleSlot);
    }

    @Override
    protected boolean canSendUpdates() {
        // Check terminal is still valid
        ItemStack currentStack = isBauble ? getBaubleStack(getPlayerInv().player, slot)
                                          : getPlayerInv().getStackInSlot(slot);

        if (currentStack.isEmpty() || !(currentStack.getItem() instanceof ItemPortableDiskTerminal)) {
            this.setValidContainer(false);

            return false;
        }

        // Drain power periodically (every 20 ticks = 1 second)
        this.ticks++;
        if (this.ticks >= 20) {
            ItemPortableDiskTerminal terminal = (ItemPortableDiskTerminal) currentStack.getItem();
            double powerDrain = AEConfig.instance().wireless_getDrainRate(0);
            double extracted = terminal.extractAEPower(currentStack, powerDrain, Actionable.MODULATE);

            if (extracted < powerDrain * 0.9) {
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
}
