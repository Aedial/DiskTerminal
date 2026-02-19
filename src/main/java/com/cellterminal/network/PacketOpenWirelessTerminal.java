package com.cellterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import appeng.api.AEApi;
import appeng.api.features.ILocatable;
import appeng.api.features.IWirelessTermHandler;
import appeng.core.localization.PlayerMessages;

import baubles.api.BaublesApi;

import com.cellterminal.ItemRegistry;
import com.cellterminal.gui.GuiHandler;
import com.cellterminal.integration.AE2WUTIntegration;


/**
 * Packet sent from client to server when the player presses the "Open Wireless Cell Terminal" keybind.
 * Searches the player's inventory (and baubles if available) for a Wireless Cell Terminal
 * or a Wireless Universal Terminal with Cell Terminal mode, and opens the GUI.
 */
public class PacketOpenWirelessTerminal implements IMessage {

    public PacketOpenWirelessTerminal() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        // No data needed
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // No data needed
    }

    public static class Handler implements IMessageHandler<PacketOpenWirelessTerminal, IMessage> {

        @Override
        public IMessage onMessage(PacketOpenWirelessTerminal message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> tryOpenTerminal(player));

            return null;
        }

        private void tryOpenTerminal(EntityPlayerMP player) {
            // First, check main inventory for our Wireless Cell Terminal
            NonNullList<ItemStack> mainInventory = player.inventory.mainInventory;
            for (int i = 0; i < mainInventory.size(); i++) {
                ItemStack is = mainInventory.get(i);
                if (is.getItem() == ItemRegistry.WIRELESS_CELL_TERMINAL) {
                    if (tryOpenWirelessCellTerminal(is, player, i, false)) return;
                }
            }

            // Check off-hand
            ItemStack offHand = player.inventory.offHandInventory.get(0);
            if (offHand.getItem() == ItemRegistry.WIRELESS_CELL_TERMINAL) {
                if (tryOpenWirelessCellTerminal(offHand, player, 40, false)) return;
            }

            // Check for Wireless Universal Terminal in main inventory (if AE2WUT is loaded)
            if (AE2WUTIntegration.isModLoaded()) {
                for (int i = 0; i < mainInventory.size(); i++) {
                    ItemStack is = mainInventory.get(i);
                    if (tryOpenWUT(is, player, i, false)) return;
                }

                // Check off-hand for WUT
                if (tryOpenWUT(offHand, player, 40, false)) return;
            }

            // Check baubles slots
            if (Loader.isModLoaded("baubles")) {
                if (tryOpenFromBaubles(player)) return;
            }

            // No terminal found
            player.sendMessage(PlayerMessages.DeviceNotLinked.get());
        }

        private boolean tryOpenWirelessCellTerminal(ItemStack is, EntityPlayerMP player, int slot, boolean isBauble) {
            IWirelessTermHandler handler = AEApi.instance().registries().wireless().getWirelessTerminalHandler(is);
            if (handler == null) return false;

            String encKey = handler.getEncryptionKey(is);
            if (encKey == null || encKey.isEmpty()) {
                player.sendMessage(PlayerMessages.DeviceNotLinked.get());
                return true;  // Found the item but it's not linked
            }

            try {
                long parsedKey = Long.parseLong(encKey);
                ILocatable securityStation = AEApi.instance().registries().locatable().getLocatableBy(parsedKey);
                if (securityStation == null) {
                    player.sendMessage(PlayerMessages.StationCanNotBeLocated.get());
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(PlayerMessages.DeviceNotLinked.get());
                return true;
            }

            if (!handler.hasPower(player, 0.5, is)) {
                player.sendMessage(PlayerMessages.DeviceNotPowered.get());
                return true;
            }

            GuiHandler.openWirelessCellTerminalGui(player, slot, isBauble);
            return true;
        }

        @Optional.Method(modid = "ae2wut")
        private boolean tryOpenWUT(ItemStack is, EntityPlayerMP player, int slot, boolean isBauble) {
            return AE2WUTIntegration.tryOpenWUTCellTerminal(is, player, slot, isBauble);
        }

        @Optional.Method(modid = "baubles")
        private boolean tryOpenFromBaubles(EntityPlayerMP player) {
            for (int i = 0; i < BaublesApi.getBaublesHandler(player).getSlots(); i++) {
                ItemStack is = BaublesApi.getBaublesHandler(player).getStackInSlot(i);
                if (is.isEmpty()) continue;

                if (is.getItem() == ItemRegistry.WIRELESS_CELL_TERMINAL) {
                    if (tryOpenWirelessCellTerminal(is, player, i, true)) return true;
                }

                if (AE2WUTIntegration.isModLoaded() && tryOpenWUT(is, player, i, true)) return true;
            }

            return false;
        }
    }
}
