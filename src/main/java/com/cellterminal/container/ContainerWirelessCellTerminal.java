package com.cellterminal.container;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import com.cellterminal.integration.AE2WUTIntegration;
import com.cellterminal.items.ItemWirelessCellTerminal;
import com.cellterminal.util.AE2OldVersionSupport;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;

import appeng.api.AEApi;
import appeng.api.features.IWirelessTermHandler;
import appeng.core.AEConfig;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.WirelessTerminalGuiObject;

import baubles.api.BaublesApi;


/**
 * Container for the Wireless Cell Terminal GUI.
 * Similar to ContainerCellTerminal but works with wireless connection.
 */
@Optional.Interface(iface = "appeng.helpers.WirelessTerminalGuiObject", modid = "ae2wut")
public class ContainerWirelessCellTerminal extends ContainerCellTerminalBase {

    private final WirelessTerminalGuiObject wirelessTerminalGuiObject;
    private final int slot;
    private final boolean isBauble;

    private double powerMultiplier = 0.5;

    private int ticks = 0;

    public ContainerWirelessCellTerminal(InventoryPlayer ip, WirelessTerminalGuiObject wth) {
        super(ip, wth);

        this.wirelessTerminalGuiObject = wth;
        this.slot = wth.getInventorySlot();
        this.isBauble = AE2OldVersionSupport.wirelessTerminalGuiObjectIsBauble(wth, ip);

        // Lock the slot if it's in the main inventory
        if (!isBauble && slot >= 0 && slot < ip.mainInventory.size()) this.lockPlayerInventorySlot(slot);

        this.bindPlayerInventory(ip, 0, 0);
    }

    @Optional.Method(modid = "baubles")
    private ItemStack getBaubleStack(EntityPlayer player, int baubleSlot) {
        return BaublesApi.getBaublesHandler(player).getStackInSlot(baubleSlot);
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



    @Override
    protected boolean canSendUpdates() {
        ItemStack currentStack = getTerminalStack();

        if (currentStack.isEmpty() || !(currentStack.getItem() instanceof ItemWirelessCellTerminal || AE2WUTIntegration.isWirelessUniversalTerminal(currentStack))) {
            this.setValidContainer(false);

            return false;
        }

        // Drain power periodically (every 20 ticks = 1 second)
        this.ticks++;
        if (this.ticks >= 20) {
            double powerDrain = this.getPowerMultiplier() * this.ticks;
            double powerUsed = this.wirelessTerminalGuiObject.extractAEPower(powerDrain, Actionable.MODULATE, PowerMultiplier.CONFIG);

            if (powerDrain != powerUsed) {
                if (this.isValidContainer()) {
                    getPlayerInv().player.sendMessage(PlayerMessages.DeviceNotPowered.get());
                }
                this.setValidContainer(false);

                return false;
            }
            this.ticks = 0;
        }

        if (!this.wirelessTerminalGuiObject.rangeCheck()) {
            if (this.isValidContainer()) {
                this.getPlayerInv().player.sendMessage(PlayerMessages.OutOfRange.get());
            }

            this.setValidContainer(false);
        } else {
            this.setPowerMultiplier(AEConfig.instance().wireless_getDrainRate(this.wirelessTerminalGuiObject.getRange()));
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


    public double getPowerMultiplier() {
        return powerMultiplier;
    }

    public void setPowerMultiplier(double powerMultiplier) {
        this.powerMultiplier = powerMultiplier;
    }
}
